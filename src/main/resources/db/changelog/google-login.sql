--liquibase formatted sql

-- "Google orqali kirish" — foydalanuvchi so'rovi, 2026-09-06 (Telegram'dan
-- keyingi navbatdagi ijtimoiy login). Google'ning barqaror "sub" (subject)
-- identifikatorini saqlaydi — telegram_id bilan bir xil g'oya.

--changeset behzod:111
ALTER TABLE users ADD COLUMN google_id VARCHAR(64) NULL UNIQUE;
