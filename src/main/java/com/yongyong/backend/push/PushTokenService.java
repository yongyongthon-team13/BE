package com.yongyong.backend.push;

import com.yongyong.backend.account.AccountService;
import com.yongyong.backend.account.DataCipher;
import com.yongyong.backend.push.PushTokenDtos.Register;
import com.yongyong.backend.push.PushTokenDtos.Unregister;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PushTokenService {
    private final JdbcTemplate db;
    private final AccountService accounts;
    private final DataCipher cipher;
    public PushTokenService(JdbcTemplate db, AccountService accounts, DataCipher cipher) {
        this.db = db; this.accounts = accounts; this.cipher = cipher;
    }

    @Transactional
    public void register(Jwt jwt, Register request) {
        var user = accounts.current(jwt);
        if (!"GUARDIAN".equals(user.role())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Guardian account required");
        String hash = hash(request.token());
        String encrypted;
        try { encrypted = cipher.encrypt(request.token()); }
        catch (IllegalStateException exception) { throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "APP_DATA_KEY_REQUIRED"); }
        Instant now = Instant.now();
        db.update("""
                INSERT INTO push_tokens(token_id, user_id, token_hash, token_enc, platform, active, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, TRUE, ?, ?)
                ON DUPLICATE KEY UPDATE user_id = VALUES(user_id), token_enc = VALUES(token_enc),
                  platform = VALUES(platform), active = TRUE, updated_at = VALUES(updated_at)
                """, UUID.randomUUID().toString(), user.userId(), hash, encrypted, request.platform().name(),
                java.sql.Timestamp.from(now), java.sql.Timestamp.from(now));
    }

    @Transactional
    public void unregister(Jwt jwt, Unregister request) {
        var user = accounts.current(jwt);
        if (!"GUARDIAN".equals(user.role())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Guardian account required");
        db.update("UPDATE push_tokens SET active = FALSE, updated_at = CURRENT_TIMESTAMP(6) WHERE user_id = ? AND token_hash = ?",
                user.userId(), hash(request.token()));
    }

    static String hash(String token) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception exception) { throw new IllegalStateException(exception); }
    }
}
