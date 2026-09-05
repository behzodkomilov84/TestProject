--liquibase formatted sql

--changeset behzod:92
-- Kurs (Course) kartochkalarini Yo'nalish ichida qo'lda tartiblash (⬆⬇)
-- imkoniyati uchun — science/topics/course_chapters'da allaqachon mavjud
-- bo'lgan "order_index" konvensiyasi bilan bir xil (foydalanuvchi so'rovi,
-- 2026-09-05: "bo'limlarni o'rnini almashtirish funksiyasini qo'shish
-- kerak" — coursesCatalog sahifasidagi kurs kartalari haqida).
ALTER TABLE courses
    ADD COLUMN order_index INT NULL;

--changeset behzod:93
-- order_index'ni hozirgi holatdan (har bir Yo'nalish ICHIDA alohida,
-- created_at bo'yicha) avtomatik to'ldirish — science-question-order-index.sql
-- bilan bir xil texnika (MySQL 5.7 bilan ham mos, oyna funksiyasisiz),
-- faqat bu yerda guruh (field_id) o'zgarganda hisoblagich 1'dan qayta
-- boshlanadi ("<=>" — NULL-xavfsiz solishtirish, "Yo'nalishsiz kurslar"
-- guruhi uchun).
SET @rn_course := 0, @prev_field := -1;
UPDATE courses c
    JOIN (
        SELECT id,
               @rn_course := IF(@prev_field <=> field_id, @rn_course + 1, 1) AS rn,
               @prev_field := field_id AS field_id
        FROM courses
        ORDER BY field_id IS NULL, field_id, created_at
    ) ranked ON ranked.id = c.id
SET c.order_index = ranked.rn;
