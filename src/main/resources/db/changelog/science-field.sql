--liquibase formatted sql

--changeset behzod:89
-- Fan (Science) — endi UI'da "Bo'lim" deb ataladi (foydalanuvchi so'rovi,
-- 2026-09-04: TEST BOSHQARUVI ierarxiyasi Kurs bilan bir xil terminologiyaga
-- keltirildi — Fan->Bo'lim, Bo'lim(TopicSection)->Mavzu, Mavzu(Topic)->Dars).
-- Shu bilan bir qatorda Fan ham endi Yo'nalish (CourseField, Kurslar bilan
-- UMUMIY jadval) ostiga guruhlanishi mumkin — course_chapters.chapter_id
-- bilan bir xil andoza (ixtiyoriy, ON DELETE SET NULL).
ALTER TABLE science
    ADD COLUMN field_id BIGINT NULL,
    ADD CONSTRAINT fk_science_field FOREIGN KEY (field_id)
        REFERENCES course_fields (id)
        ON DELETE SET NULL;

--changeset behzod:90
-- Bir martalik ma'lumot to'ldirish — mavjud "Bakteriologiya" va "Kimyo"
-- fanlarini, xuddi shu nomli Kurslar (course-fields.sql) bilan BIR XIL
-- Yo'nalishga bog'laymiz (foydalanuvchi bilan kelishilgan, 2026-09-04).
-- "Ona tili" hozircha Yo'nalishsiz qoladi (keyinroq qo'lda tayinlanadi).
UPDATE science SET field_id = (SELECT id FROM course_fields WHERE name = 'Sanitariya epidemiologiya xizmati') WHERE name = 'Bakteriologiya';
UPDATE science SET field_id = (SELECT id FROM course_fields WHERE name = 'O''rta ta''lim') WHERE name = 'Kimyo';
