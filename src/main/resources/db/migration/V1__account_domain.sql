CREATE TABLE app_users (
  user_id CHAR(36) PRIMARY KEY,
  role VARCHAR(20) NOT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  deleted_at TIMESTAMP(6) NULL
);

CREATE TABLE user_identities (
  identity_id CHAR(36) PRIMARY KEY,
  user_id CHAR(36) NOT NULL,
  issuer VARCHAR(255) NOT NULL,
  subject VARCHAR(255) NOT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_identity_user FOREIGN KEY (user_id) REFERENCES app_users(user_id),
  CONSTRAINT uq_identity_provider UNIQUE (issuer, subject)
);

CREATE TABLE elder_profiles (
  elder_id CHAR(36) PRIMARY KEY,
  user_id CHAR(36) NOT NULL UNIQUE,
  display_name_enc TEXT NOT NULL,
  birth_year INT NULL,
  home_address_enc TEXT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_elder_user FOREIGN KEY (user_id) REFERENCES app_users(user_id)
);

CREATE TABLE mobility_profiles (
  elder_id CHAR(36) PRIMARY KEY,
  mobility_aid VARCHAR(20) NOT NULL DEFAULT 'NONE',
  stairs_difficult BOOLEAN NOT NULL DEFAULT FALSE,
  bus_difficult BOOLEAN NOT NULL DEFAULT FALSE,
  long_walk_difficult BOOLEAN NOT NULL DEFAULT FALSE,
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_mobility_elder FOREIGN KEY (elder_id) REFERENCES elder_profiles(elder_id)
);

CREATE TABLE health_profiles (
  health_profile_id CHAR(36) PRIMARY KEY,
  elder_id CHAR(36) NOT NULL,
  condition_code VARCHAR(50) NOT NULL,
  note_enc TEXT NULL,
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_health_elder FOREIGN KEY (elder_id) REFERENCES elder_profiles(elder_id),
  CONSTRAINT uq_health_code UNIQUE (elder_id, condition_code)
);

CREATE TABLE consent_records (
  consent_id CHAR(36) PRIMARY KEY,
  elder_id CHAR(36) NOT NULL,
  scope VARCHAR(30) NOT NULL,
  policy_version VARCHAR(50) NOT NULL,
  granted BOOLEAN NOT NULL,
  recorded_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  withdrawn_at TIMESTAMP(6) NULL,
  CONSTRAINT fk_consent_elder FOREIGN KEY (elder_id) REFERENCES elder_profiles(elder_id),
  INDEX idx_consent_latest (elder_id, scope, recorded_at)
);

CREATE TABLE guardian_links (
  link_id CHAR(36) PRIMARY KEY,
  elder_id CHAR(36) NOT NULL,
  guardian_user_id CHAR(36) NOT NULL,
  relationship VARCHAR(50) NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  linked_at TIMESTAMP(6) NULL,
  revoked_at TIMESTAMP(6) NULL,
  CONSTRAINT fk_link_elder FOREIGN KEY (elder_id) REFERENCES elder_profiles(elder_id),
  CONSTRAINT fk_link_guardian FOREIGN KEY (guardian_user_id) REFERENCES app_users(user_id),
  CONSTRAINT uq_elder_guardian UNIQUE (elder_id, guardian_user_id)
);

CREATE TABLE guardian_link_codes (
  code_id CHAR(36) PRIMARY KEY,
  elder_id CHAR(36) NOT NULL,
  code_hash CHAR(64) NOT NULL UNIQUE,
  expires_at TIMESTAMP(6) NOT NULL,
  consumed_at TIMESTAMP(6) NULL,
  CONSTRAINT fk_code_elder FOREIGN KEY (elder_id) REFERENCES elder_profiles(elder_id)
);

CREATE TABLE notifications (
  notification_id CHAR(36) PRIMARY KEY,
  trip_id CHAR(36) NOT NULL,
  recipient_user_id CHAR(36) NOT NULL,
  channel VARCHAR(20) NOT NULL,
  type VARCHAR(30) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'QUEUED',
  sent_at TIMESTAMP(6) NULL,
  read_at TIMESTAMP(6) NULL,
  CONSTRAINT fk_notification_user FOREIGN KEY (recipient_user_id) REFERENCES app_users(user_id),
  INDEX idx_notification_inbox (recipient_user_id, read_at, sent_at)
);
