package com.yongyong.backend.push;

import com.yongyong.backend.account.DataCipher;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

@Component
public class ExpoPushDispatcher {
    private final JdbcTemplate db;
    private final DataCipher cipher;
    private final ObjectMapper mapper;
    private final RestClient client;
    private final String accessToken;

    public ExpoPushDispatcher(JdbcTemplate db, DataCipher cipher, ObjectMapper mapper,
            @Value("${app.push.expo-access-token:}") String accessToken) {
        this.db = db; this.cipher = cipher; this.mapper = mapper; this.accessToken = accessToken;
        this.client = RestClient.builder().baseUrl("https://exp.host").build();
    }

    @Scheduled(fixedDelayString = "${app.push.dispatch-ms:5000}")
    public void dispatchDue() {
        List<Outbox> pending = db.query("SELECT outbox_id, recipient_user_id, notification_type, title, body, data_json, attempts, accepted_token_ids, expo_ticket_map FROM push_outbox WHERE status = 'QUEUED' AND available_at <= CURRENT_TIMESTAMP(6) ORDER BY created_at LIMIT 50",
                (rs, n) -> new Outbox(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getString(5), rs.getString(6), rs.getInt(7), rs.getString(8), rs.getString(9)));
        for (Outbox item : pending) dispatch(item);
    }

    @Scheduled(fixedDelayString = "${app.push.receipt-check-ms:60000}")
    public void checkReceiptsDue() {
        List<ReceiptCheck> pending = db.query("SELECT outbox_id, expo_ticket_map, receipt_attempts FROM push_outbox WHERE status = 'SENT' AND receipt_due_at <= CURRENT_TIMESTAMP(6) AND receipt_checked_at IS NULL AND expo_ticket_map IS NOT NULL ORDER BY receipt_due_at LIMIT 50",
                (rs, n) -> new ReceiptCheck(rs.getString(1), rs.getString(2), rs.getInt(3)));
        for (ReceiptCheck item : pending) checkReceipt(item);
    }

    private void dispatch(Outbox item) {
        java.util.Set<String> acceptedTokenIds = item.acceptedTokenIds() == null || item.acceptedTokenIds().isBlank()
                ? new java.util.HashSet<>() : new java.util.HashSet<>(List.of(item.acceptedTokenIds().split(",")));
        Map<String, String> ticketsByToken = parseTicketMap(item.expoTicketMap());
        List<Device> devices = db.query("SELECT token_id, token_enc FROM push_tokens WHERE user_id = ? AND active = TRUE",
                (rs, n) -> new Device(rs.getString(1), cipher.decrypt(rs.getString(2))), item.userId());
        if (devices.isEmpty()) {
            finish(item.id(), acceptedTokenIds.isEmpty() ? "SKIPPED" : "SENT", null);
            return;
        }
        boolean foundNewDevice = false;
        boolean retryableFailure = false;
        for (Device device : devices) {
            if (acceptedTokenIds.contains(device.id())) continue;
            foundNewDevice = true;
            try {
                var request = client.post().uri("/--/api/v2/push/send").contentType(MediaType.APPLICATION_JSON);
                if (!accessToken.isBlank()) request.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
                var body = Map.of("to", device.token(), "title", item.title(), "body", item.body(),
                        "data", item.dataJson() == null ? Map.of("type", item.type()) : mapper.readValue(item.dataJson(), Map.class),
                        "sound", "default");
                Map<?, ?> result = request.body(body).retrieve().body(Map.class);
                TicketResult ticket = parseTicket(result);
                if (ticket.outcome() == TicketOutcome.ACCEPTED) {
                    acceptedTokenIds.add(device.id());
                    ticketsByToken.put(ticket.ticketId(), device.id());
                    String acceptedIds = String.join(",", acceptedTokenIds);
                    db.update("UPDATE push_outbox SET accepted_token_ids = ?, expo_ticket_map = ? WHERE outbox_id = ?",
                            acceptedIds, serializeTicketMap(ticketsByToken), item.id());
                } else if (ticket.outcome() == TicketOutcome.DEVICE_NOT_REGISTERED) {
                    db.update("UPDATE push_tokens SET active = FALSE, updated_at = CURRENT_TIMESTAMP(6) WHERE token_id = ?", device.id());
                } else {
                    retryableFailure = true;
                }
            } catch (RuntimeException exception) { retryableFailure = true; }
        }
        if (!retryableFailure && !acceptedTokenIds.isEmpty()) finish(item.id(), "SENT", null);
        else if (!retryableFailure) finish(item.id(), "SKIPPED", foundNewDevice ? "NO_VALID_EXPO_DEVICE" : null);
        else if (item.attempts() >= 5) finish(item.id(), acceptedTokenIds.isEmpty() ? "FAILED" : "SENT", "EXPO_SEND_FAILED");
        else db.update("UPDATE push_outbox SET attempts = attempts + 1, available_at = ? WHERE outbox_id = ?",
                Timestamp.from(Instant.now().plusSeconds(Math.min(300, 5L << item.attempts()))), item.id());
    }

    private void checkReceipt(ReceiptCheck item) {
        Map<String, String> ticketsByToken = parseTicketMap(item.ticketMap());
        try {
            var request = client.post().uri("/--/api/v2/push/getReceipts").contentType(MediaType.APPLICATION_JSON);
            if (!accessToken.isBlank()) request.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
            Map<?, ?> result = request.body(Map.of("ids", List.copyOf(ticketsByToken.keySet()))).retrieve().body(Map.class);
            Object dataValue = result == null ? null : result.get("data");
            if (!(dataValue instanceof Map<?, ?> receiptData)
                    || !receiptData.keySet().containsAll(ticketsByToken.keySet())) {
                retryReceipt(item);
                return;
            }
            boolean deviceInvalid = false;
            for (Map.Entry<String, String> ticket : ticketsByToken.entrySet()) {
                Object receiptValue = receiptData.get(ticket.getKey());
                if (receiptValue instanceof Map<?, ?> receipt && "error".equals(receipt.get("status"))) {
                    Object detailValue = receipt.get("details");
                    if (detailValue instanceof Map<?, ?> details && "DeviceNotRegistered".equals(details.get("error"))) {
                        db.update("UPDATE push_tokens SET active = FALSE, updated_at = CURRENT_TIMESTAMP(6) WHERE token_id = ?", ticket.getValue());
                        deviceInvalid = true;
                    }
                }
            }
            db.update("UPDATE push_outbox SET receipt_checked_at = CURRENT_TIMESTAMP(6), receipt_due_at = NULL, last_error = CASE WHEN ? THEN 'EXPO_RECEIPT_DEVICE_NOT_REGISTERED' ELSE last_error END WHERE outbox_id = ?",
                    deviceInvalid, item.id());
        } catch (RuntimeException exception) {
            retryReceipt(item);
        }
    }

    private void retryReceipt(ReceiptCheck item) {
        if (item.attempts() >= 5) {
            db.update("UPDATE push_outbox SET receipt_checked_at = CURRENT_TIMESTAMP(6), receipt_due_at = NULL, last_error = 'EXPO_RECEIPT_LOOKUP_FAILED' WHERE outbox_id = ?",
                    item.id());
        } else {
            db.update("UPDATE push_outbox SET receipt_attempts = receipt_attempts + 1, receipt_due_at = ? WHERE outbox_id = ?",
                    Timestamp.from(Instant.now().plus(5, ChronoUnit.MINUTES)), item.id());
        }
    }

    private void finish(String id, String status, String error) {
        Instant receiptDue = Instant.now().plus(15, ChronoUnit.MINUTES);
        db.update("UPDATE push_outbox SET status = ?, last_error = ?, sent_at = CASE WHEN ? = 'SENT' THEN CURRENT_TIMESTAMP(6) ELSE sent_at END, receipt_due_at = CASE WHEN ? = 'SENT' AND expo_ticket_map IS NOT NULL THEN ? ELSE receipt_due_at END WHERE outbox_id = ?",
                status, error, status, status, Timestamp.from(receiptDue), id);
    }
    static TicketResult parseTicket(Map<?, ?> result) {
        Object data = result == null ? null : result.get("data");
        if (!(data instanceof List<?> tickets) || tickets.size() != 1 || !(tickets.get(0) instanceof Map<?, ?> ticket))
            return new TicketResult(TicketOutcome.RETRY, null);
        Object status = ticket.get("status");
        if ("ok".equals(status) && ticket.get("id") instanceof String id && !id.isBlank())
            return new TicketResult(TicketOutcome.ACCEPTED, id);
        if (!"error".equals(status)) return new TicketResult(TicketOutcome.RETRY, null);
        Object details = ticket.get("details");
        if (details instanceof Map<?, ?> errorDetails && "DeviceNotRegistered".equals(errorDetails.get("error")))
            return new TicketResult(TicketOutcome.DEVICE_NOT_REGISTERED, null);
        return new TicketResult(TicketOutcome.RETRY, null);
    }
    enum TicketOutcome { ACCEPTED, DEVICE_NOT_REGISTERED, RETRY }
    record TicketResult(TicketOutcome outcome, String ticketId) {}
    private static Map<String, String> parseTicketMap(String serialized) {
        Map<String, String> values = new LinkedHashMap<>();
        if (serialized == null || serialized.isBlank()) return values;
        for (String pair : serialized.split(",")) {
            int separator = pair.indexOf('=');
            if (separator > 0 && separator < pair.length() - 1)
                values.put(pair.substring(0, separator), pair.substring(separator + 1));
        }
        return values;
    }
    private static String serializeTicketMap(Map<String, String> ticketsByToken) {
        return ticketsByToken.entrySet().stream().map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(java.util.stream.Collectors.joining(","));
    }
    private record Outbox(String id, String userId, String type, String title, String body, String dataJson,
                          int attempts, String acceptedTokenIds, String expoTicketMap) {}
    private record Device(String id, String token) {}
    private record ReceiptCheck(String id, String ticketMap, int attempts) {}
}
