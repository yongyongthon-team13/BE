package com.yongyong.backend.transport;

import com.yongyong.backend.account.AccountService;
import com.yongyong.backend.account.DataCipher;
import com.yongyong.backend.transport.TransportRequestDtos.CreateRequest;
import com.yongyong.backend.transport.TransportRequestDtos.PickupLocation;
import com.yongyong.backend.transport.TransportRequestDtos.TransportRequest;
import com.yongyong.backend.transport.TransportRequestDtos.Type;
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

    public TransportRequestService(JdbcTemplate db, AccountService accounts,
                                   DataCipher cipher, ObjectMapper mapper) {
        this.db = db; this.accounts = accounts;
        this.cipher = cipher; this.mapper = mapper;
    }

    @Transactional
    public TransportRequest create(Jwt jwt, UUID appointmentId, CreateRequest request, String idempotencyKey) {
        authorizeAppointment(jwt, appointmentId);
        var user = accounts.current(jwt);
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            List<TransportRequest> prior = db.query("SELECT request_id, type, status, requested_at, updated_at FROM transport_requests WHERE requested_by_user_id = ? AND appointment_id = ? AND idempotency_key = ?",
                    (rs, n) -> response(rs.getString(1), rs.getString(2), rs.getString(3), rs.getTimestamp(4), rs.getTimestamp(5)),
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
            return db.queryForObject("SELECT request_id, type, status, requested_at, updated_at FROM transport_requests WHERE requested_by_user_id = ? AND appointment_id = ? AND idempotency_key = ?",
                    (rs, n) -> response(rs.getString(1), rs.getString(2), rs.getString(3), rs.getTimestamp(4), rs.getTimestamp(5)),
                    user.userId(), appointmentId.toString(), idempotencyKey);
        }
        return new TransportRequest(requestId, request.type(), "PENDING", now, now,
                "외부 접수 전 요청 대기 중입니다.");
    }

    @Transactional(readOnly = true)
    public List<TransportRequest> list(Jwt jwt, UUID appointmentId) {
        authorizeAppointment(jwt, appointmentId);
        return db.query("SELECT request_id, type, status, requested_at, updated_at FROM transport_requests WHERE appointment_id = ? ORDER BY requested_at DESC",
                (rs, n) -> response(rs.getString(1), rs.getString(2), rs.getString(3), rs.getTimestamp(4), rs.getTimestamp(5)),
                appointmentId.toString());
    }

    private String encode(PickupLocation location) {
        if (location == null) return null;
        try { return mapper.writeValueAsString(location); }
        catch (JacksonException exception) { throw new IllegalArgumentException("Invalid pickup location"); }
    }
    private void authorizeAppointment(Jwt jwt, UUID appointmentId) {
        List<String> elderIds = db.query("SELECT elder_id FROM appointments WHERE appointment_id = ?",
                (rs, n) -> rs.getString(1), appointmentId.toString());
        if (elderIds.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Appointment not found");
        accounts.profile(jwt, elderIds.get(0));
    }
    private String encrypt(String value) {
        try { return cipher.encrypt(value); }
        catch (IllegalStateException exception) { throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "APP_DATA_KEY_REQUIRED"); }
    }
    private static String normalizeKey(String key) { return key == null || key.isBlank() ? null : key; }
    private static Instant instant(java.sql.Timestamp value) { return value == null ? null : value.toInstant(); }
    private static java.sql.Timestamp ts(Instant value) { return java.sql.Timestamp.from(value); }
    private static TransportRequest response(String id, String type, String status,
                                             java.sql.Timestamp created, java.sql.Timestamp updated) {
        Type parsed = Type.valueOf(type);
        String description = "PENDING".equals(status) ? "외부 접수 전 요청 대기 중입니다." : status;
        return new TransportRequest(id, parsed, status, instant(created), instant(updated), description);
    }
}
