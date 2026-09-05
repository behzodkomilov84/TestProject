--liquibase formatted sql

-- "Savol formasi"da rasmning tanlangan eni/bo'yini (piksel) saqlash uchun
-- (foydalanuvchi so'rovi, 2026-09-05: savol/javob rasmlarining o'lchamini
-- o'zgartirish, natija bazada saqlanib qolishi kerak). NULL — rasm
-- o'zining tabiiy o'lchamida (CSS bo'yicha) ko'rsatiladi.

--changeset behzod:96
ALTER TABLE questions ADD COLUMN image_width INT NULL;
ALTER TABLE questions ADD COLUMN image_height INT NULL;

--changeset behzod:97
ALTER TABLE answers ADD COLUMN image_width INT NULL;
ALTER TABLE answers ADD COLUMN image_height INT NULL;
