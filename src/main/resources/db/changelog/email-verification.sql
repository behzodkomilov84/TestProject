--liquibase formatted sql

-- DEFAULT TRUE — mavjud (eski) userlar login qila olishda davom etishi
-- uchun muhim: faqat yangi ro'yxatdan o'tishda (UserServiceImpl.register)
-- kod ichida explicit "false" qo'yiladi.
--changeset behzod:48
ALTER TABLE users ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT TRUE;

--changeset behzod:49
CREATE TABLE email_verification_codes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    code VARCHAR(10) NOT NULL,
    used BOOLEAN NOT NULL DEFAULT FALSE,
    expires_at DATETIME NOT NULL,
    created_at DATETIME NOT NULL,
    CONSTRAINT fk_email_verification_user FOREIGN KEY (user_id) REFERENCES users(id)
);

--changeset behzod:50
CREATE INDEX idx_email_verification_code ON email_verification_codes (code, used);
