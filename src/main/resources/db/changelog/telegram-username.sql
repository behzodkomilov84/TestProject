--liquibase formatted sql

-- Telegram'ning o'zining @username'i (raqamli telegram_id emas) —
-- foydalanuvchi so'rovi, 2026-09-06: "qaysi telegram accountga
-- bog'langan, shu yerga qo'sh" — profilda odam TANIY oladigan ko'rinishda
-- ko'rsatish uchun (masalan "@behzodkomilov8484").

--changeset behzod:112
ALTER TABLE users ADD COLUMN telegram_username VARCHAR(64) NULL;
