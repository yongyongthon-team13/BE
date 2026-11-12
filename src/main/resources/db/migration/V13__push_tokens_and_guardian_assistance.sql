ALTER TABLE transport_requests ADD COLUMN guardian_response VARCHAR(40) NULL;

CREATE TABLE push_tokens (
  token_id CHAR(36) PRIMARY KEY,
  user_id CHAR(36) NOT NULL,
  token_hash CHAR(64) NOT NULL UNIQUE,
  token_enc TEXT NOT NULL,
  platform VARCHAR(20) NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMP(6) NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL,
  CONSTRAINT fk_push_token_user FOREIGN KEY (user_id) REFERENCES app_users(user_id),
  INDEX idx_push_token_user_active (user_id, active)
);

CREATE TABLE push_outbox (
  outbox_id CHAR(36) PRIMARY KEY,
  recipient_user_id CHAR(36) NOT NULL,
  notification_type VARCHAR(40) NOT NULL,
  title VARCHAR(120) NOT NULL,
  body VARCHAR(240) NOT NULL,
  data_json TEXT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'QUEUED',
  attempts INT NOT NULL DEFAULT 0,
  accepted_token_ids TEXT NULL,
  expo_ticket_map TEXT NULL,
  receipt_due_at TIMESTAMP(6) NULL,
  receipt_attempts INT NOT NULL DEFAULT 0,
  receipt_checked_at TIMESTAMP(6) NULL,
  available_at TIMESTAMP(6) NOT NULL,
  sent_at TIMESTAMP(6) NULL,
  last_error VARCHAR(100) NULL,
  created_at TIMESTAMP(6) NOT NULL,
  CONSTRAINT fk_push_outbox_user FOREIGN KEY (recipient_user_id) REFERENCES app_users(user_id),
  INDEX idx_push_outbox_due (status, available_at)
);
