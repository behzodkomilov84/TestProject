--liquibase formatted sql
--changeset behzod:117
-- "/users" sahifasida "Ro'yxatdan o'tgan sana" ustuni (foydalanuvchi
-- so'rovi, 2026-09-09). NULL bo'lishi mumkin — mavjud (eski)
-- foydalanuvchilar uchun haqiqiy ro'yxatdan o'tgan sana noma'lum, sahifada
-- "—" ko'rsatiladi; yangi ro'yxatdan o'tishlar @CreationTimestamp orqali
-- avtomatik to'ldiriladi (User.java).
ALTER TABLE users ADD COLUMN created_at DATETIME NULL;
