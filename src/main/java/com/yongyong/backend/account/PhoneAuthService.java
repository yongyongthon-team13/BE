package com.yongyong.backend.account;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PhoneAuthService {
    private static final int CODE_LIFETIME_SECONDS = 300;
    private static final int ACCESS_TOKEN_LIFETIME_SECONDS = 3600;
    private static final int REFRESH_TOKEN_LIFETIME_DAYS = 30;
    private final JdbcTemplate db;
    private final AccountService accounts;
    private final SmsSender sms;
    private final JwtEncoder encoder;
    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    public PhoneAuthService(JdbcTemplate db, AccountService accounts, SmsSender sms,
                            JwtEncoder encoder, SecurityConfig security) {
        this.db = db;
        this.accounts = accounts;
        this.sms = sms;
        this.encoder = encoder;
        // Team app can start before deployment secrets are configured; account APIs stay closed.
        this.key = security.configured() ? security.secretKey() : null;
    }

    public record LoginResult(String accessToken, String tokenType, Instant expiresAt,
                              String refreshToken, Instant refreshExpiresAt,
                              String userId, String role) {}
    private record Challenge(String codeHash, Instant expiresAt, int attempts, Instant sentAt,
                             LocalDate sentDay, int dailyCount, Instant consumedAt) {}
    private record Session(String sessionId, String userId, String subject, String role,
                           Instant expiresAt) {}

    @Transactional
    public void requestCode(String input) {
        requireConfigured();
        String phone = normalize(input);
        if (!sms.available())
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "SMS provider is not configured");
        String phoneHash = digest("phone:" + phone);
        Instant now = Instant.now();
        LocalDate today = now.atZone(ZoneOffset.UTC).toLocalDate();
        List<Challenge> existing = challenge(phoneHash);
        int dailyCount = 1;
        if (!existing.isEmpty()) {
            Challenge previous = existing.get(0);
            if (previous.sentAt().plusSeconds(60).isAfter(now))
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Wait before requesting another code");
            dailyCount = previous.sentDay().equals(today) ? previous.dailyCount() + 1 : 1;
            if (dailyCount > 10)
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Daily code limit reached");
        }
        String code = "%06d".formatted(random.nextInt(1_000_000));
        String codeHash = digest("code:" + phone + ":" + code);
        if (existing.isEmpty()) {
            db.update("INSERT INTO phone_verifications(phone_hash, code_hash, expires_at, sent_at, sent_day, daily_count) " +
                            "VALUES (?, ?, ?, ?, ?, ?)", phoneHash, codeHash,
                    Timestamp.from(now.plusSeconds(CODE_LIFETIME_SECONDS)), Timestamp.from(now),
                    java.sql.Date.valueOf(today), dailyCount);
        } else {
            db.update("UPDATE phone_verifications SET code_hash = ?, expires_at = ?, attempts = 0, " +
                            "sent_at = ?, sent_day = ?, daily_count = ?, consumed_at = NULL WHERE phone_hash = ?",
                    codeHash, Timestamp.from(now.plusSeconds(CODE_LIFETIME_SECONDS)), Timestamp.from(now),
                    java.sql.Date.valueOf(today), dailyCount, phoneHash);
        }
        // The code is sent only to the verified phone; it is never logged or returned by HTTP.
        sms.send(phone, "[용용톤] 인증번호 " + code + " (5분 유효)");
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public LoginResult login(String input, String code, String role) {
        requireConfigured();
        String phone = normalize(input);
        if (!code.matches("[0-9]{6}")) invalidCode();
        String phoneHash = digest("phone:" + phone);
        List<Challenge> existing = challenge(phoneHash);
        if (existing.isEmpty()) invalidCode();
        Challenge challenge = existing.get(0);
        Instant now = Instant.now();
        if (challenge.consumedAt() != null || !challenge.expiresAt().isAfter(now) || challenge.attempts() >= 5)
            invalidCode();
        db.update("UPDATE phone_verifications SET attempts = attempts + 1 WHERE phone_hash = ?", phoneHash);
        String suppliedHash = digest("code:" + phone + ":" + code);
        if (!MessageDigest.isEqual(challenge.codeHash().getBytes(StandardCharsets.US_ASCII),
                                   suppliedHash.getBytes(StandardCharsets.US_ASCII))) invalidCode();

        String sessionId = UUID.randomUUID().toString();
        Jwt jwt = issueAccessToken(phoneHash, now, sessionId);
        AccountService.User user = accounts.login(jwt, role);
        db.update("UPDATE phone_verifications SET consumed_at = ? WHERE phone_hash = ?",
                Timestamp.from(now), phoneHash);
        String refreshToken = newRefreshToken();
        Instant refreshExpiresAt = now.plus(REFRESH_TOKEN_LIFETIME_DAYS, ChronoUnit.DAYS);
        db.update("INSERT INTO auth_sessions(session_id, user_id, refresh_token_hash, expires_at) " +
                "VALUES (?, ?, ?, ?)", sessionId, user.userId(),
                digest("refresh:" + refreshToken), Timestamp.from(refreshExpiresAt));
        return new LoginResult(jwt.getTokenValue(), "Bearer", jwt.getExpiresAt(), refreshToken,
                refreshExpiresAt, user.userId(), user.role());
    }

    @Transactional
    public LoginResult refresh(String refreshToken) {
        requireConfigured();
        String tokenHash = refreshHash(refreshToken);
        List<Session> sessions = db.query("""
                SELECT s.session_id, s.user_id, i.subject, u.role, s.expires_at
                FROM auth_sessions s
                JOIN app_users u ON u.user_id = s.user_id AND u.deleted_at IS NULL
                JOIN user_identities i ON i.user_id = s.user_id AND i.issuer = ?
                WHERE s.refresh_token_hash = ? AND s.revoked_at IS NULL
                FOR UPDATE
                """, (rs, n) -> new Session(rs.getString(1), rs.getString(2), rs.getString(3),
                rs.getString(4), rs.getTimestamp(5).toInstant()), SecurityConfig.ISSUER, tokenHash);
        if (sessions.isEmpty()) invalidRefreshToken();
        Session session = sessions.get(0);
        Instant now = Instant.now();
        if (!session.expiresAt().isAfter(now)) invalidRefreshToken();
        String replacement = newRefreshToken();
        Instant refreshedExpiry = now.plus(REFRESH_TOKEN_LIFETIME_DAYS, ChronoUnit.DAYS);
        db.update("UPDATE auth_sessions SET refresh_token_hash = ?, last_used_at = ?, expires_at = ? " +
                        "WHERE session_id = ?", digest("refresh:" + replacement), Timestamp.from(now),
                Timestamp.from(refreshedExpiry), session.sessionId());
        Jwt jwt = issueAccessToken(session.subject(), now, session.sessionId());
        return new LoginResult(jwt.getTokenValue(), "Bearer", jwt.getExpiresAt(), replacement,
                refreshedExpiry, session.userId(), session.role());
    }

    @Transactional
    public void logout(String refreshToken) {
        requireConfigured();
        String tokenHash = refreshHash(refreshToken);
        db.update("UPDATE auth_sessions SET revoked_at = CURRENT_TIMESTAMP(6) " +
                "WHERE refresh_token_hash = ? AND revoked_at IS NULL", tokenHash);
    }

    private Jwt issueAccessToken(String subject, Instant now, String sessionId) {
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer(SecurityConfig.ISSUER)
                .subject(subject).audience(List.of(SecurityConfig.AUDIENCE))
                .issuedAt(now).expiresAt(now.plusSeconds(ACCESS_TOKEN_LIFETIME_SECONDS))
                .claim("sid", sessionId).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims));
    }

    private String newRefreshToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String refreshHash(String refreshToken) {
        if (refreshToken == null || !refreshToken.matches("[A-Za-z0-9_-]{43}")) invalidRefreshToken();
        return digest("refresh:" + refreshToken);
    }

    private static void invalidRefreshToken() {
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired refresh token");
    }

    private List<Challenge> challenge(String phoneHash) {
        return db.query("SELECT code_hash, expires_at, attempts, sent_at, sent_day, daily_count, consumed_at " +
                        "FROM phone_verifications WHERE phone_hash = ? FOR UPDATE",
                (rs, n) -> new Challenge(rs.getString(1), rs.getTimestamp(2).toInstant(), rs.getInt(3),
                        rs.getTimestamp(4).toInstant(), rs.getDate(5).toLocalDate(), rs.getInt(6),
                        rs.getTimestamp(7) == null ? null : rs.getTimestamp(7).toInstant()), phoneHash);
    }

    private String digest(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Could not calculate authentication digest", e);
        }
    }

    private void requireConfigured() {
        if (key == null)
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Phone login is not configured");
    }

    private static String normalize(String input) {
        if (input == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Phone number is required");
        String stripped = input.replaceAll("[ -]", "");
        if (stripped.matches("010[0-9]{8}")) return "+82" + stripped.substring(1);
        if (stripped.matches("\\+8210[0-9]{8}")) return stripped;
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use a Korean 010 mobile number");
    }

    private static void invalidCode() {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired verification code");
    }
}
