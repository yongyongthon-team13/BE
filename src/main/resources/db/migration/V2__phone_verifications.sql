CREATE TABLE phone_verifications (
  phone_hash CHAR(64) PRIMARY KEY,
  code_hash CHAR(64) NOT NULL,
  expires_at TIMESTAMP(6) NOT NULL,
  attempts INT NOT NULL DEFAULT 0,
  sent_at TIMESTAMP(6) NOT NULL,
  sent_day DATE NOT NULL,
  daily_count INT NOT NULL DEFAULT 1,
  consumed_at TIMESTAMP(6) NULL
);
