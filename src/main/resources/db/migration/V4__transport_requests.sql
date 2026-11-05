CREATE TABLE transport_requests (
  request_id CHAR(36) PRIMARY KEY,
  appointment_id CHAR(36) NOT NULL,
  requested_by_user_id CHAR(36) NOT NULL,
  type VARCHAR(30) NOT NULL,
  pickup_data_enc TEXT NULL,
  note_enc TEXT NULL,
  status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
  idempotency_key VARCHAR(100) NULL,
  requested_at TIMESTAMP(6) NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL,
  CONSTRAINT fk_transport_request_user FOREIGN KEY (requested_by_user_id) REFERENCES app_users(user_id),
  CONSTRAINT uq_transport_request_idempotency UNIQUE (requested_by_user_id, appointment_id, idempotency_key),
  INDEX idx_transport_request_appointment (appointment_id, requested_at)
);
