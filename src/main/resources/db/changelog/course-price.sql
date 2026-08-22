--liquibase formatted sql

--changeset behzod:68
-- Pullik kurslar uchun ko'rsatiladigan (ma'lumot uchun) narx — obuna
-- so'rovini OWNER qo'lda tasdiqlaganda haqiqiy summa alohida kiritiladi
-- (CourseSubscription.amount), bu ustun esa katalogda/kurs sahifasida
-- "narxi qancha" ko'rsatish uchun. Bepul (free=true) kurslarda odatda NULL.
ALTER TABLE courses
    ADD COLUMN price DECIMAL(12,2) NULL;
