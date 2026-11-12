package com.yongyong.backend.account;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AccountService {
    private final JdbcTemplate db;
    private final DataCipher cipher;
    private final SecureRandom random = new SecureRandom();

    public AccountService(JdbcTemplate db, DataCipher cipher) {
        this.db = db;
        this.cipher = cipher;
    }

    public record User(String userId, String role) {}
    public record Mobility(String mobilityAid, boolean stairsDifficult, boolean busDifficult,
                           boolean longWalkDifficult, boolean walkingDiscomfort) {}
    public record MobilityUpdate(String mobilityAid, Boolean stairsDifficult, Boolean busDifficult,
                                 Boolean longWalkDifficult, Boolean walkingDiscomfort) {
        public MobilityUpdate(String mobilityAid, Boolean stairsDifficult, Boolean busDifficult,
                              Boolean longWalkDifficult) {
            this(mobilityAid, stairsDifficult, busDifficult, longWalkDifficult, null);
        }
    }
    public record Profile(String elderId, String name, Integer birthYear, String homeAddress,
                          Mobility mobility, List<HealthCondition> healthConditions) {}
    public record HealthCondition(String conditionCode, String note) {}
    public record Notification(String notificationId, String tripId, String channel, String type,
                               String status, Instant sentAt, Instant readAt) {}
    public record LinkCode(String code, Instant expiresAt) {}
    public record GuardianLink(String linkId, String elderId, String status) {}
    public record GuardianProfile(String name, String phoneNumber) {}
    public record LinkedGuardian(String linkId, String name, String phoneNumber, String relationship) {}

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public User login(Jwt jwt, String role) {
        return login(jwt, role, null);
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public User login(Jwt jwt, String role, String verifiedPhoneNumber) {
        if (!List.of("ELDER", "GUARDIAN").contains(role)) badRequest("role must be ELDER or GUARDIAN");
        String issuer = jwt.getIssuer().toString();
        String subject = jwt.getSubject();
        List<User> existing = db.query("""
                SELECT u.user_id, u.role FROM app_users u JOIN user_identities i ON i.user_id = u.user_id
                WHERE i.issuer = ? AND i.subject = ? AND u.deleted_at IS NULL
                """, (rs, n) -> new User(rs.getString(1), rs.getString(2)), issuer, subject);
        if (!existing.isEmpty()) {
            if (!existing.get(0).role().equals(role))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "This phone number already has a different role");
            saveVerifiedPhone(existing.get(0), verifiedPhoneNumber);
            return existing.get(0);
        }
        String userId = id();
        db.update("INSERT INTO app_users(user_id, role) VALUES (?, ?)", userId, role);
        db.update("INSERT INTO user_identities(identity_id, user_id, issuer, subject) VALUES (?, ?, ?, ?)",
                id(), userId, issuer, subject);
        saveVerifiedPhone(new User(userId, role), verifiedPhoneNumber);
        return new User(userId, role);
    }

    private void saveVerifiedPhone(User user, String verifiedPhoneNumber) {
        if (verifiedPhoneNumber != null)
            db.update("UPDATE app_users SET phone_number_enc = ? WHERE user_id = ?",
                    cipher.encrypt(verifiedPhoneNumber), user.userId());
    }

    public User current(Jwt jwt) {
        if (jwt == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        List<User> rows = db.query("""
                SELECT u.user_id, u.role FROM app_users u JOIN user_identities i ON i.user_id = u.user_id
                WHERE i.issuer = ? AND i.subject = ? AND u.deleted_at IS NULL
                """, (rs, n) -> new User(rs.getString(1), rs.getString(2)),
                jwt.getIssuer().toString(), jwt.getSubject());
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Call /auth/login first");
        return rows.get(0);
    }

    @Transactional
    public Profile createProfile(Jwt jwt, String name, Integer birthYear, String homeAddress,
                                 MobilityUpdate mobility) {
        User user = current(jwt);
        requireRole(user, "ELDER");
        if (!db.query("SELECT elder_id FROM elder_profiles WHERE user_id = ?", (rs, n) -> rs.getString(1),
                user.userId()).isEmpty()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Profile exists");
        String elderId = id();
        db.update("INSERT INTO elder_profiles(elder_id, user_id, display_name_enc, birth_year, home_address_enc) " +
                        "VALUES (?, ?, ?, ?, ?)", elderId, user.userId(), cipher.encrypt(name), birthYear,
                cipher.encrypt(homeAddress));
        db.update("INSERT INTO mobility_profiles(elder_id) VALUES (?)", elderId);
        updateMobility(elderId, mobility);
        return profile(jwt, elderId);
    }

    public Profile profile(Jwt jwt, String elderId) {
        User user = current(jwt);
        requireProfileAccess(user, elderId);
        List<Profile> rows = db.query("""
                SELECT elder_id, display_name_enc, birth_year, home_address_enc
                FROM elder_profiles WHERE elder_id = ?
                """, (rs, n) -> new Profile(rs.getString(1), cipher.decrypt(rs.getString(2)),
                (Integer) rs.getObject(3), cipher.decrypt(rs.getString(4)), mobility(elderId),
                owns(user, elderId) || consented(elderId, "HEALTH") ? health(elderId) : List.of()), elderId);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile not found");
        return rows.get(0);
    }

    public Profile ownProfile(Jwt jwt) {
        User user = current(jwt);
        requireRole(user, "ELDER");
        List<String> elderIds = db.query("SELECT elder_id FROM elder_profiles WHERE user_id = ?",
                (rs, n) -> rs.getString(1), user.userId());
        if (elderIds.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile not found");
        return profile(jwt, elderIds.get(0));
    }

    public List<LinkedGuardian> ownGuardians(Jwt jwt) {
        User elder = current(jwt);
        requireRole(elder, "ELDER");
        List<String> elderIds = db.query("SELECT elder_id FROM elder_profiles WHERE user_id = ?",
                (rs, n) -> rs.getString(1), elder.userId());
        if (elderIds.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile not found");
        return db.query("""
                SELECT gl.link_id, gp.display_name_enc, u.phone_number_enc, gl.relationship
                FROM guardian_links gl
                JOIN app_users u ON u.user_id = gl.guardian_user_id AND u.role = 'GUARDIAN'
                LEFT JOIN guardian_profiles gp ON gp.guardian_user_id = gl.guardian_user_id
                WHERE gl.elder_id = ? AND gl.status = 'ACTIVE' AND u.deleted_at IS NULL
                ORDER BY gl.linked_at, gl.link_id
                """, (rs, n) -> new LinkedGuardian(rs.getString(1), cipher.decrypt(rs.getString(2)),
                cipher.decrypt(rs.getString(3)), rs.getString(4)), elderIds.get(0));
    }

    @Transactional
    public GuardianProfile updateGuardianProfile(Jwt jwt, String name) {
        User guardian = current(jwt);
        requireRole(guardian, "GUARDIAN");
        String encryptedName = cipher.encrypt(name);
        db.update("""
                INSERT INTO guardian_profiles(guardian_user_id, display_name_enc)
                VALUES (?, ?)
                ON DUPLICATE KEY UPDATE display_name_enc = VALUES(display_name_enc),
                  updated_at = CURRENT_TIMESTAMP(6)
                """, guardian.userId(), encryptedName);
        return guardianProfile(guardian.userId());
    }

    public GuardianProfile guardianProfile(Jwt jwt) {
        User guardian = current(jwt);
        requireRole(guardian, "GUARDIAN");
        return guardianProfile(guardian.userId());
    }

    private GuardianProfile guardianProfile(String guardianUserId) {
        List<GuardianProfile> rows = db.query("""
                SELECT gp.display_name_enc, u.phone_number_enc
                FROM app_users u LEFT JOIN guardian_profiles gp ON gp.guardian_user_id = u.user_id
                WHERE u.user_id = ? AND u.deleted_at IS NULL
                """, (rs, n) -> new GuardianProfile(cipher.decrypt(rs.getString(1)),
                cipher.decrypt(rs.getString(2))), guardianUserId);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Guardian not found");
        return rows.get(0);
    }

    @Transactional
    public Profile updateProfile(Jwt jwt, String elderId, String name, Integer birthYear, String homeAddress,
                                 MobilityUpdate mobility) {
        requireOwner(current(jwt), elderId);
        if (name != null) db.update("UPDATE elder_profiles SET display_name_enc = ?, updated_at = CURRENT_TIMESTAMP(6) WHERE elder_id = ?",
                cipher.encrypt(name), elderId);
        if (birthYear != null) db.update("UPDATE elder_profiles SET birth_year = ?, updated_at = CURRENT_TIMESTAMP(6) WHERE elder_id = ?",
                birthYear, elderId);
        if (homeAddress != null) db.update("UPDATE elder_profiles SET home_address_enc = ?, updated_at = CURRENT_TIMESTAMP(6) WHERE elder_id = ?",
                cipher.encrypt(homeAddress), elderId);
        updateMobility(elderId, mobility);
        return profile(jwt, elderId);
    }

    private Mobility mobility(String elderId) {
        return db.queryForObject("SELECT mobility_aid, stairs_difficult, bus_difficult, long_walk_difficult, " +
                "walking_discomfort FROM mobility_profiles WHERE elder_id = ?", (rs, n) -> new Mobility(rs.getString(1),
                rs.getBoolean(2), rs.getBoolean(3), rs.getBoolean(4), rs.getBoolean(5)), elderId);
    }

    private void updateMobility(String elderId, MobilityUpdate update) {
        if (update == null) return;
        if (update.mobilityAid() != null &&
                !List.of("NONE", "CANE", "WALKER", "WHEELCHAIR", "OTHER").contains(update.mobilityAid()))
            badRequest("Unknown mobility aid");
        db.update("""
                UPDATE mobility_profiles SET mobility_aid = COALESCE(?, mobility_aid),
                  stairs_difficult = COALESCE(?, stairs_difficult),
                  bus_difficult = COALESCE(?, bus_difficult),
                  long_walk_difficult = COALESCE(?, long_walk_difficult),
                  walking_discomfort = COALESCE(?, walking_discomfort), updated_at = CURRENT_TIMESTAMP(6)
                WHERE elder_id = ?
                """, update.mobilityAid(), update.stairsDifficult(), update.busDifficult(),
                update.longWalkDifficult(), update.walkingDiscomfort(), elderId);
    }

    @Transactional
    public List<HealthCondition> replaceHealth(Jwt jwt, String elderId, List<HealthCondition> conditions) {
        requireOwner(current(jwt), elderId);
        if (!consented(elderId, "HEALTH"))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Health consent is required");
        db.update("DELETE FROM health_profiles WHERE elder_id = ?", elderId);
        for (HealthCondition condition : conditions) {
            db.update("INSERT INTO health_profiles(health_profile_id, elder_id, condition_code, note_enc) VALUES (?, ?, ?, ?)",
                    id(), elderId, condition.conditionCode(), cipher.encrypt(condition.note()));
        }
        return health(elderId);
    }

    public List<HealthCondition> health(String elderId) {
        return db.query("SELECT condition_code, note_enc FROM health_profiles WHERE elder_id = ? ORDER BY condition_code",
                (rs, n) -> new HealthCondition(rs.getString(1), cipher.decrypt(rs.getString(2))), elderId);
    }

    @Transactional
    public void recordConsent(Jwt jwt, String elderId, String scope, String policyVersion, boolean granted) {
        requireOwner(current(jwt), elderId);
        if (!List.of("HEALTH", "LOCATION", "NOTIFICATION_CALL", "CAREGIVER_SHARING",
                "AI_ROUTE_RECOMMENDATION").contains(scope))
            badRequest("Unknown consent scope");
        db.update("INSERT INTO consent_records(consent_id, elder_id, scope, policy_version, granted, withdrawn_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?)", id(), elderId, scope, policyVersion, granted,
                granted ? null : java.sql.Timestamp.from(Instant.now()));
    }

    public void requireConsent(Jwt jwt, String elderId, String scope) {
        requireProfileAccess(current(jwt), elderId);
        if (!consented(elderId, scope))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Consent is required for this operation");
    }

    @Transactional
    public LinkCode issueLinkCode(Jwt jwt, String elderId) {
        requireOwner(current(jwt), elderId);
        if (!consented(elderId, "CAREGIVER_SHARING"))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Caregiver sharing consent is required");
        byte[] bytes = new byte[18];
        random.nextBytes(bytes);
        String code = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expiresAt = Instant.now().plus(10, ChronoUnit.MINUTES);
        db.update("UPDATE guardian_link_codes SET consumed_at = CURRENT_TIMESTAMP(6) WHERE elder_id = ? AND consumed_at IS NULL", elderId);
        db.update("INSERT INTO guardian_link_codes(code_id, elder_id, code_hash, expires_at) VALUES (?, ?, ?, ?)",
                id(), elderId, sha256(code), java.sql.Timestamp.from(expiresAt));
        return new LinkCode(code, expiresAt);
    }

    @Transactional
    public GuardianLink connectGuardian(Jwt jwt, String code, String relationship) {
        User guardian = current(jwt);
        requireRole(guardian, "GUARDIAN");
        List<String[]> rows = db.query("""
                SELECT code_id, elder_id FROM guardian_link_codes
                WHERE code_hash = ? AND consumed_at IS NULL AND expires_at > CURRENT_TIMESTAMP(6) FOR UPDATE
                """, (rs, n) -> new String[]{rs.getString(1), rs.getString(2)}, sha256(code));
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired code");
        String[] found = rows.get(0);
        if (!consented(found[1], "CAREGIVER_SHARING"))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Caregiver sharing consent was withdrawn");
        db.update("UPDATE guardian_link_codes SET consumed_at = CURRENT_TIMESTAMP(6) WHERE code_id = ?", found[0]);
        List<String> existing = db.query("SELECT link_id FROM guardian_links WHERE elder_id = ? AND guardian_user_id = ?",
                (rs, n) -> rs.getString(1), found[1], guardian.userId());
        String linkId = existing.isEmpty() ? id() : existing.get(0);
        if (existing.isEmpty()) {
            db.update("INSERT INTO guardian_links(link_id, elder_id, guardian_user_id, relationship, status, linked_at) " +
                            "VALUES (?, ?, ?, ?, 'ACTIVE', CURRENT_TIMESTAMP(6))",
                    linkId, found[1], guardian.userId(), relationship);
        } else {
            db.update("UPDATE guardian_links SET relationship = ?, status = 'ACTIVE', linked_at = CURRENT_TIMESTAMP(6), " +
                    "revoked_at = NULL WHERE link_id = ?", relationship, linkId);
        }
        return new GuardianLink(linkId, found[1], "ACTIVE");
    }

    @Transactional
    public void disconnectGuardian(Jwt jwt, String linkId) {
        User user = current(jwt);
        int updated = db.update("""
                UPDATE guardian_links SET status = 'REVOKED', revoked_at = CURRENT_TIMESTAMP(6)
                WHERE link_id = ? AND (guardian_user_id = ? OR elder_id IN
                  (SELECT elder_id FROM elder_profiles WHERE user_id = ?))
                """, linkId, user.userId(), user.userId());
        if (updated == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Link not found");
    }

    public List<Notification> notifications(Jwt jwt) {
        User user = current(jwt);
        return db.query("""
                SELECT notification_id, trip_id, channel, type, status, sent_at, read_at FROM notifications
                WHERE recipient_user_id = ? ORDER BY sent_at DESC LIMIT 50
                """, (rs, n) -> new Notification(rs.getString(1), rs.getString(2), rs.getString(3),
                rs.getString(4), rs.getString(5), timestamp(rs.getTimestamp(6)), timestamp(rs.getTimestamp(7))),
                user.userId());
    }

    @Transactional
    public void markNotificationRead(Jwt jwt, String notificationId) {
        User user = current(jwt);
        int updated = db.update("UPDATE notifications SET read_at = COALESCE(read_at, CURRENT_TIMESTAMP(6)) " +
                "WHERE notification_id = ? AND recipient_user_id = ?", notificationId, user.userId());
        if (updated == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found");
    }

    private void requireProfileAccess(User user, String elderId) {
        if (owns(user, elderId)) return;
        if ("GUARDIAN".equals(user.role()) && consented(elderId, "CAREGIVER_SHARING") &&
                !db.query("SELECT link_id FROM guardian_links WHERE elder_id = ? AND guardian_user_id = ? AND status = 'ACTIVE'",
                        (rs, n) -> rs.getString(1), elderId, user.userId()).isEmpty()) return;
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile not found");
    }

    private void requireOwner(User user, String elderId) {
        if (!owns(user, elderId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile not found");
    }

    private boolean owns(User user, String elderId) {
        return !db.query("SELECT elder_id FROM elder_profiles WHERE elder_id = ? AND user_id = ?",
                (rs, n) -> rs.getString(1), elderId, user.userId()).isEmpty();
    }

    private boolean consented(String elderId, String scope) {
        List<Boolean> rows = db.query("SELECT granted FROM consent_records WHERE elder_id = ? AND scope = ? " +
                "ORDER BY recorded_at DESC, consent_id DESC LIMIT 1", (rs, n) -> rs.getBoolean(1), elderId, scope);
        return !rows.isEmpty() && rows.get(0);
    }

    private static void requireRole(User user, String role) {
        if (!role.equals(user.role())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Wrong account role");
    }

    private static void badRequest(String message) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private static String id() { return UUID.randomUUID().toString(); }
    private static Instant timestamp(java.sql.Timestamp value) { return value == null ? null : value.toInstant(); }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
