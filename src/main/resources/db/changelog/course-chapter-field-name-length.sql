--liquibase formatted sql

--changeset behzod:122
-- Foydalanuvchi so'rovi (2026-09-12: "Дарс номлари, мавзу номларини
-- чекловини текшир. Агар 300 тадан кам бўлса, кўратиб қўй 300+ қилиб") —
-- "course_chapters.name" (Mavzu) va "course_fields.name" (Yo'nalish)
-- 255 belgi bilan cheklangan edi (course_sections.title — "Dars" —
-- allaqachon course-section-title-length.sql#67 orqali 500ga oshirilgan
-- edi, bu yerga tegishli emas). Uzun nomlar (masalan to'liq kasb-toifa
-- tavsifi, ekran suratida ko'rilgan "Sanitariya epidemiologiya xizmati
-- (\"Xayrullo Komilov ustoz - shogird maktabi\")" kabi) "Data
-- truncation: Data too long for column 'name'" xatosiga olib kelishi
-- mumkin edi (question-text-length.sql/course-section-title-length.sql
-- bilan bir xil sinf muammo).
ALTER TABLE course_chapters
    MODIFY COLUMN name VARCHAR(500) NOT NULL;

ALTER TABLE course_fields
    MODIFY COLUMN name VARCHAR(500) NOT NULL;
