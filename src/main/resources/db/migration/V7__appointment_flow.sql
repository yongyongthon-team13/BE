ALTER TABLE appointments MODIFY COLUMN clinic_name VARCHAR(255) NULL;
ALTER TABLE appointments MODIFY COLUMN appointment_at TIMESTAMP(6) NULL;
ALTER TABLE appointments ADD COLUMN clinic_size VARCHAR(10) NULL;
ALTER TABLE appointments ADD COLUMN help_requested_at TIMESTAMP(6) NULL;

ALTER TABLE companion_requests ADD COLUMN pickup_at TIMESTAMP(6) NULL;
ALTER TABLE companion_requests ADD COLUMN pickup_place VARCHAR(255) NULL;
ALTER TABLE companion_requests ADD COLUMN manager_name VARCHAR(100) NULL;

CREATE TABLE visit_reviews (
  review_id CHAR(36) PRIMARY KEY,
  appointment_id CHAR(36) NOT NULL UNIQUE,
  transport VARCHAR(20) NOT NULL,
  on_time BOOLEAN NOT NULL,
  reviewed_at TIMESTAMP(6) NOT NULL,
  CONSTRAINT fk_review_appointment FOREIGN KEY (appointment_id) REFERENCES appointments(appointment_id)
);
