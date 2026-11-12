package com.yongyong.backend.push;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class PushOutboxService {
    private final JdbcTemplate db;
    public PushOutboxService(JdbcTemplate db) { this.db = db; }

    public void enqueue(String recipientId, String type, String title, String body, String dataJson) {
        Instant now = Instant.now();
        db.update("""
                INSERT INTO push_outbox(outbox_id, recipient_user_id, notification_type, title, body,
                  data_json, status, attempts, available_at, created_at)
                VALUES (?, ?, ?, ?, ?, ?, 'QUEUED', 0, ?, ?)
                """, UUID.randomUUID().toString(), recipientId, type, title, body, dataJson,
                Timestamp.from(now), Timestamp.from(now));
    }
}
