CREATE TABLE auth_sessions (
  session_id CHAR(36) PRIMARY KEY,
  user_id CHAR(36) NOT NULL,
  refresh_token_hash CHAR(64) NOT NULL UNIQUE,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  last_used_at TIMESTAMP(6) NULL,
  expires_at TIMESTAMP(6) NOT NULL,
  revoked_at TIMESTAMP(6) NULL,
  CONSTRAINT fk_auth_session_user FOREIGN KEY (user_id) REFERENCES app_users(user_id),
  INDEX idx_auth_session_user (user_id)
);
