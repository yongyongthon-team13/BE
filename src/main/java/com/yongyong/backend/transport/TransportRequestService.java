package com.yongyong.backend.transport;

import com.yongyong.backend.account.AccountService;
import com.yongyong.backend.account.DataCipher;
import com.yongyong.backend.transport.TransportRequestDtos.CreateRequest;
import com.yongyong.backend.transport.TransportRequestDtos.PickupLocation;
import com.yongyong.backend.transport.TransportRequestDtos.TransportRequest;
import com.yongyong.backend.transport.TransportRequestDtos.Type;
import com.yongyong.backend.transport.TransportRequestDtos.GuardianResponse;
import com.yongyong.backend.transport.TransportRequestDtos.RespondRequest;
import com.yongyong.backend.push.PushOutboxService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class TransportRequestService {
    private final JdbcTemplate db;
    private final AccountService accounts;
    private final DataCipher cipher;
    private final ObjectMapper mapper;
    private final PushOutboxService pushOutbox;

    public TransportRequestService(JdbcTemplate db, AccountService accounts,
                                   DataCipher cipher, ObjectMapper mapper, PushOutboxService pushOutbox) {
        this.db = db; this.accounts = accounts;
        this.cipher = cipher; this.mapper = mapper; this.pushOutbox = pushOutbox;
    }

    @Transactional
    public TransportRequest create(Jwt jwt, UUID appointmentId, CreateRequest request, String idempotencyKey) {
        authorizeAppointment(jwt, appointmentId);
        var user = accounts.current(jwt);
        if (request.type() == Type.GUARDIAN_ASSISTANCE && !"ELDER".equals(user.role()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the elder can request guardian assistance");
        String elderId = appointmentElder(appointmentId);
        if (request.type() == Type.GUARDIAN_ASSISTANCE && !sharingConsented(elderId))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Caregiver sharing consent is required");
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            List<TransportRequest> prior = db.query("SELECT request_id, type, status, guardian_response, requested_at, updated_at FROM transport_requests WHERE requested_by_user_id = ? AND appointment_id = ? AND idempotency_key = ?",
                    (rs, n) -> response(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getTimestamp(5), rs.getTimestamp(6)),
                    user.userId(), appointmentId.toString(), idempotencyKey);
            if (!prior.isEmpty()) return prior.get(0);
        }
        String pickupJson = encode(request.pickupLocation());
        String encryptedPickup = pickupJson == null ? null : encrypt(pickupJson);
        String encryptedNote = request.note() == null ? null : encrypt(request.note());
        Instant now = Instant.now();
        String requestId = UUID.randomUUID().toString();
        try {
            db.update("""
                    INSERT INTO transport_requests(request_id, appointment_id, requested_by_user_id, type,
                      pickup_data_enc, note_enc, status, idempotency_key, requested_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, 'PENDING', ?, ?, ?)
                    """, requestId, appointmentId.toString(), user.userId(), request.type().name(),
                    encryptedPickup, encryptedNote, normalizeKey(idempotencyKey), ts(now), ts(now));
        } catch (org.springframework.dao.DuplicateKeyException exception) {
            if (idempotencyKey == null || idempotencyKey.isBlank()) throw exception;
            return db.queryForObject("SELECT request_id, type, status, guardian_response, requested_at, updated_at FROM transport_requests WHERE requested_by_user_id = ? AND appointment_id = ? AND idempotency_key = ?",
                    (rs, n) -> response(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getTimestamp(5), rs.getTimestamp(6)),
                    user.userId(), appointmentId.toString(), idempotencyKey);
        }
        if (request.type() == Type.GUARDIAN_ASSISTANCE) {
            List<String> guardians = db.query("SELECT guardian_user_id FROM guardian_links WHERE elder_id = ? AND status = 'ACTIVE'",
                    (rs, n) -> rs.getString(1), elderId);
            if (guardians.isEmpty()) throw new ResponseStatusException(HttpStatus.CONFLICT, "No active guardian is linked");
            String data = "{\"type\":\"GUARDIAN_ASSISTANCE_REQUEST\",\"appointmentId\":\"" + appointmentId
                    + "\",\"requestId\":\"" + requestId + "\"}";
            for (String guardianId : guardians) pushOutbox.enqueue(guardianId, "GUARDIAN_ASSISTANCE_REQUEST",
                    "보호자 도움이 필요합니다", "어르신이 이동 도움을 요청했습니다.", data);
        }
        return new TransportRequest(requestId, request.type(), "PENDING", null, now, now,
                request.type() == Type.GUARDIAN_ASSISTANCE ? "보호자 응답 대기 중입니다." : "외부 접수 전 요청 대기 중입니다.");
    }

    @Transactional(readOnly = true)
    public List<TransportRequest> list(Jwt jwt, UUID appointmentId) {
        authorizeAppointment(jwt, appointmentId);
        return db.query("SELECT request_id, type, status, guardian_response, requested_at, updated_at FROM transport_requests WHERE appointment_id = ? ORDER BY requested_at DESC",
                (rs, n) -> response(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getTimestamp(5), rs.getTimestamp(6)),
                appointmentId.toString());
    }

    @Transactional(readOnly = true)
    public TransportRequest get(Jwt jwt, UUID appointmentId, UUID requestId) {
        authorizeAppointment(jwt, appointmentId);
        List<TransportRequest> rows = db.query("SELECT request_id, type, status, guardian_response, requested_at, updated_at FROM transport_requests WHERE appointment_id = ? AND request_id = ?",
                (rs, n) -> response(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getTimestamp(5), rs.getTimestamp(6)),
                appointmentId.toString(), requestId.toString());
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Transport request not found");
        return rows.get(0);
    }

    @Transactional
    public TransportRequest respond(Jwt jwt, UUID appointmentId, UUID requestId, RespondRequest request) {
        var guardian = accounts.current(jwt);
        if (!"GUARDIAN".equals(guardian.role())) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        String elderId = appointmentElder(appointmentId);
        accounts.profile(jwt, elderId); // Enforces active link and caregiver-sharing consent.
        List<String> rows = db.query("SELECT request_id FROM transport_requests WHERE request_id = ? AND appointment_id = ? AND type = 'GUARDIAN_ASSISTANCE'",
                (rs, n) -> rs.getString(1), requestId.toString(), appointmentId.toString());
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Assistance request not found");
        int changed = db.update("UPDATE transport_requests SET status = 'ACCEPTED', guardian_response = ?, updated_at = CURRENT_TIMESTAMP(6) WHERE request_id = ? AND status = 'PENDING' AND guardian_response IS NULL",
                request.response().name(), requestId.toString());
        if (changed == 0) throw new ResponseStatusException(HttpStatus.CONFLICT, "Request has already been answered");
        return db.queryForObject("SELECT request_id, type, status, guardian_response, requested_at, updated_at FROM transport_requests WHERE request_id = ?",
                (rs, n) -> response(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getTimestamp(5), rs.getTimestamp(6)), requestId.toString());
    }

    private String encode(PickupLocation location) {
        if (location == null) return null;
        try { return mapper.writeValueAsString(location); }
        catch (JacksonException exception) { throw new IllegalArgumentException("Invalid pickup location"); }
    }
    private void authorizeAppointment(Jwt jwt, UUID appointmentId) {
        accounts.profile(jwt, appointmentElder(appointmentId));
    }
    private String appointmentElder(UUID appointmentId) {
        List<String> elderIds = db.query("SELECT elder_id FROM appointments WHERE appointment_id = ?",
                (rs, n) -> rs.getString(1), appointmentId.toString());
        if (elderIds.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Appointment not found");
        return elderIds.get(0);
    }
    private boolean sharingConsented(String elderId) {
        List<Boolean> rows = db.query("SELECT granted FROM consent_records WHERE elder_id = ? AND scope = 'CAREGIVER_SHARING' ORDER BY recorded_at DESC, consent_id DESC LIMIT 1",
                (rs, n) -> rs.getBoolean(1), elderId);
        return !rows.isEmpty() && rows.get(0);
    }
    private String encrypt(String value) {
        try { return cipher.encrypt(value); }
        catch (IllegalStateException exception) { throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "APP_DATA_KEY_REQUIRED"); }
    }
    private static String normalizeKey(String key) { return key == null || key.isBlank() ? null : key; }
    private static Instant instant(java.sql.Timestamp value) { return value == null ? null : value.toInstant(); }
    private static java.sql.Timestamp ts(Instant value) { return java.sql.Timestamp.from(value); }
    private static TransportRequest response(String id, String type, String status,
                                             String guardianResponse, java.sql.Timestamp created, java.sql.Timestamp updated) {
        Type parsed = Type.valueOf(type);
        String description = "GUARDIAN_ASSISTANCE".equals(type) && "PENDING".equals(status) ? "보호자 응답 대기 중입니다."
                : ("ACCEPTED".equals(status) ? "보호자가 요청을 확인했습니다." : "외부 접수 전 요청 대기 중입니다.");
        GuardianResponse response = guardianResponse == null ? null : GuardianResponse.valueOf(guardianResponse);
        return new TransportRequest(id, parsed, status, response, instant(created), instant(updated), description);
    }
}
