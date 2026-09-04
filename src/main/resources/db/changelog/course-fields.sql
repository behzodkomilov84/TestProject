--liquibase formatted sql

--changeset behzod:86
-- "Yo'nalish" (soha) — Kurslar (endi "Bo'lim" deb ataladi) katalogini
-- kattaroq guruhga bo'lish uchun eng yuqori daraja (masalan "Sanitariya
-- epidemiologiya xizmati", "O'rta ta'lim"). Foydalanuvchi so'rovi bo'yicha
-- (2026-09-04) — turli sohalarga tegishli kurslar bitta tekis ro'yxatda
-- aralashib ketmasligi uchun. course_chapters'dan farqli — mustaqil
-- boshqariladi (OWNER/ADMIN CRUD), shu sabab o'zining alohida
-- "O'chirilganlar savati"ga ega (deleted_at).
CREATE TABLE course_fields
(
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    name        VARCHAR(255) NOT NULL,
    order_index INT          NOT NULL,
    created_by  BIGINT       NOT NULL,
    created_at  DATETIME     NOT NULL,
    deleted_at  DATETIME NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_course_field_created_by FOREIGN KEY (created_by)
        REFERENCES users (id)
);

--changeset behzod:87
-- Course -> CourseField (ixtiyoriy DB darajasida — course_chapters.chapter_id
-- bilan bir xil andoza, ON DELETE SET NULL — Yo'nalish o'chirilsa ham
-- Kurslar o'zi o'chib ketmaydi). "Majburiy" talab ILOVA darajasida
-- (CourseService.createCourse) ta'minlanadi — yangi kurs Yo'nalishsiz
-- yaratilmaydi, lekin bazada NULL bo'lish IMKONIYATI xavfsizlik uchun
-- saqlanadi (masalan Yo'nalish o'chirilganda kurs "yetim" qolib ketmasin,
-- keyin qo'lda boshqa Yo'nalishga o'tkazilsin).
ALTER TABLE courses
    ADD COLUMN field_id BIGINT NULL,
    ADD CONSTRAINT fk_course_field FOREIGN KEY (field_id)
        REFERENCES course_fields (id)
        ON DELETE SET NULL;

--changeset behzod:88
-- Bir martalik ma'lumot to'ldirish — HOZIRGI ishlab chiqarish bazasidagi
-- 2 ta mavjud kurs uchun (foydalanuvchi bilan kelishilgan aniq
-- taqsimot, 2026-09-04): "Bakteriologiya" (id=6, keyinroq "SEO va JS
-- Qo'mitasi (Sanitariya epidemiologiya xizmati)" deb qayta nomlangan) va
-- "Kimyo — abituriyentlar uchun to'liq qo'llanma" (id=2). created_by —
-- mos kursni yaratgan foydalanuvchi (qattiq user ID kiritilmaydi).
INSERT INTO course_fields (name, order_index, created_by, created_at)
SELECT 'Sanitariya epidemiologiya xizmati', 1, c.created_by, NOW()
FROM courses c WHERE c.id = 6;

INSERT INTO course_fields (name, order_index, created_by, created_at)
SELECT 'O''rta ta''lim', 2, c.created_by, NOW()
FROM courses c WHERE c.id = 2;

UPDATE courses SET field_id = (SELECT id FROM course_fields WHERE name = 'Sanitariya epidemiologiya xizmati') WHERE id = 6;
UPDATE courses SET field_id = (SELECT id FROM course_fields WHERE name = 'O''rta ta''lim') WHERE id = 2;
