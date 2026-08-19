--liquibase formatted sql

-- Botdagi havolalar ("saytda ko'ring") bosilganda foydalanuvchini avtomatik
-- login qildirish uchun — bitta martalik, qisqa muddatli (2 daqiqa) token.
-- Xuddi telegram_link_codes kabi, lekin akkaunt ULASH uchun emas, allaqachon
-- ulangan foydalanuvchini saytga (parolsiz) kiritish uchun.
--changeset behzod:61
CREATE TABLE telegram_auto_login_tokens (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    token VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    redirect_path VARCHAR(255) NULL,
    expires_at DATETIME NOT NULL,
    used BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_telegram_auto_login_tokens_user FOREIGN KEY (user_id) REFERENCES users(id)
);

--changeset behzod:62
CREATE UNIQUE INDEX idx_telegram_auto_login_token ON telegram_auto_login_tokens (token);
