CREATE TABLE appointments (
  appointment_id CHAR(36) PRIMARY KEY,
  elder_id CHAR(36) NOT NULL,
  clinic_name VARCHAR(255) NOT NULL,
  clinic_address_enc TEXT NULL,
  clinic_lat DECIMAL(9,6) NULL,
  clinic_lon DECIMAL(9,6) NULL,
  appointment_at TIMESTAMP(6) NOT NULL,
  department VARCHAR(100) NULL,
  purpose VARCHAR(100) NULL,
  location VARCHAR(255) NULL,
  preparations TEXT NULL,
  needs_guardian BOOLEAN NOT NULL DEFAULT FALSE,
  source VARCHAR(20) NOT NULL,
  status VARCHAR(20) NOT NULL,
  extraction_confidence DECIMAL(4,3) NULL,
  confirmed_at TIMESTAMP(6) NULL,
  created_at TIMESTAMP(6) NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL,
  CONSTRAINT fk_appointment_elder FOREIGN KEY (elder_id) REFERENCES elder_profiles(elder_id),
  INDEX idx_appointment_elder_time (elder_id, appointment_at, status)
);

CREATE TABLE companion_assessments (
  assessment_id CHAR(36) PRIMARY KEY,
  appointment_id CHAR(36) NOT NULL,
  ruleset_version VARCHAR(50) NOT NULL,
  recommended BOOLEAN NOT NULL,
  reason_codes VARCHAR(255) NULL,
  assessed_at TIMESTAMP(6) NOT NULL,
  CONSTRAINT fk_assessment_appointment FOREIGN KEY (appointment_id) REFERENCES appointments(appointment_id)
);

CREATE TABLE companion_requests (
  request_id CHAR(36) PRIMARY KEY,
  appointment_id CHAR(36) NOT NULL,
  provider_code VARCHAR(100) NULL,
  booking_method VARCHAR(30) NOT NULL,
  status VARCHAR(20) NOT NULL,
  requested_at TIMESTAMP(6) NOT NULL,
  CONSTRAINT fk_companion_request_appointment FOREIGN KEY (appointment_id) REFERENCES appointments(appointment_id),
  INDEX idx_companion_request_appointment (appointment_id, status)
);
