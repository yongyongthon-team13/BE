ALTER TABLE app_users
    ADD COLUMN phone_number_enc TEXT NULL;

CREATE TABLE guardian_profiles (
    guardian_user_id CHAR(36) PRIMARY KEY,
    display_name_enc TEXT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_guardian_profile_user FOREIGN KEY (guardian_user_id) REFERENCES app_users(user_id)
);
