--liquibase formatted sql
--changeset behzod:119
-- "/users" sahifasidagi "Ro'yxatdan o'tgan" ustuni migratsiyadan (117)
-- OLDIN yaratilgan hisoblarda "—" (noma'lum) ko'rsatib turardi
-- (foydalanuvchi so'rovi, 2026-09-09: "маълумоти йўқ фойдаланувчиларга
-- кечаги санани қўй"). Bir martalik backfill — haqiqiy ro'yxatdan o'tgan
-- sana noma'lum bo'lgani uchun, taxminiy qiymat sifatida "kecha"
-- (migratsiya ishga tushgan kundan bir kun oldin) qo'yiladi.
UPDATE users SET created_at = DATE_SUB(CURDATE(), INTERVAL 1 DAY) WHERE created_at IS NULL;
