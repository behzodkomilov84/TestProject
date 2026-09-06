--liquibase formatted sql

-- Profilga qo'shimcha ma'lumotlar — Ism, Familiya, Ish/o'qish joyi,
-- Lavozim va profil rasmi (foydalanuvchi so'rovi, 2026-09-06: "Фамилия,
-- исм, иш ёки ўқиш жойи, лавозими киритилса яхши бўлади... Буларни
-- 'тўлдирилиши шарт майдон' қилиш керак"). Ustunlar NULL qilib
-- qo'yiladi (mavjud foydalanuvchilarda bu ma'lumot yo'q) — "shart"lik
-- FAQAT yangi ro'yxatdan o'tishda (UserServiceImpl#register) forma
-- darajasida ta'minlanadi, DB darajasida emas.

--changeset behzod:106
ALTER TABLE users ADD COLUMN first_name VARCHAR(100) NULL;

--changeset behzod:107
ALTER TABLE users ADD COLUMN last_name VARCHAR(100) NULL;

--changeset behzod:108
ALTER TABLE users ADD COLUMN workplace VARCHAR(255) NULL;

--changeset behzod:109
ALTER TABLE users ADD COLUMN job_title VARCHAR(255) NULL;

--changeset behzod:110
ALTER TABLE users ADD COLUMN avatar_url VARCHAR(500) NULL;
