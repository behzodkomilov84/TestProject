--liquibase formatted sql

-- Click/Payme kabi to'lov shlyuzlari talab qiladigan minimal tranzaksiya
-- summasi — ilgari kodda umuman yo'q edi (hech qanday tekshiruv qilinmasdi).
-- Bitta qatorli (singleton, id=1) jadval — OWNER uni /users sahifasidan
-- .env/redeploy'siz o'zgartira oladi.
--changeset behzod:58
CREATE TABLE payment_settings (
    id BIGINT PRIMARY KEY,
    min_amount_som DECIMAL(12,2) NOT NULL DEFAULT 1000.00
);

--changeset behzod:59
INSERT INTO payment_settings (id, min_amount_som) VALUES (1, 1000.00);
