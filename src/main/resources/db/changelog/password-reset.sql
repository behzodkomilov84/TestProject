--liquibase formatted sql

--changeset behzod:33
ALTER TABLE users ADD COLUMN email VARCHAR(255) NULL UNIQUE;

--changeset behzod:34
CREATE TABLE password_reset_codes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    code VARCHAR(10) NOT NULL,
    channel VARCHAR(20) NOT NULL,
    used BOOLEAN NOT NULL DEFAULT FALSE,
    expires_at DATETIME NOT NULL,
    created_at DATETIME NOT NULL,
    CONSTRAINT fk_password_reset_user FOREIGN KEY (user_id) REFERENCES users(id)
);

--changeset behzod:35
CREATE INDEX idx_password_reset_code ON password_reset_codes (code, used);
