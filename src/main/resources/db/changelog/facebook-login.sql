--liquibase formatted sql

-- "Facebook orqali kirish" — Telegram/Google'dan keyingi uchinchi
-- ijtimoiy login usuli (foydalanuvchi so'rovi, 2026-09-07: "Facebook,
-- google, telegram orqali kirish" — Telegram/Google tayyor, navbatda
-- Facebook). Facebook'ning barqaror foydalanuvchi ID'sini saqlaydi —
-- google_id bilan bir xil g'oya.

--changeset behzod:113
ALTER TABLE users ADD COLUMN facebook_id VARCHAR(64) NULL UNIQUE;
