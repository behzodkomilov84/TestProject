--liquibase formatted sql
--changeset behzod:118
-- "/users" sahifasida "Oxirgi tashrif vaqti" ustuni (foydalanuvchi
-- so'rovi, 2026-09-09). NULL bo'lishi mumkin — bu ustun qo'shilishidan
-- OLDIN hech kim kuzatilmagan, shuning uchun sahifada "—" ko'rsatiladi.
-- OnlineUserTracker orqali (throttled — ko'pi bilan har 60 soniyada bir
-- marta) avtomatik yangilanadi.
ALTER TABLE users ADD COLUMN last_seen_at DATETIME NULL;
