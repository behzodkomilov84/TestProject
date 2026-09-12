--liquibase formatted sql

--changeset behzod:123
-- Foydalanuvchi so'rovi (2026-09-12, course-chapter-field-name-length.sql'ning
-- davomi): o'sha safar faqat "course_chapters.name" va "course_fields.name"
-- 500ga oshirilgan edi, lekin BOG'LIQ uchta joy 255'da (Hibernate standart
-- qiymati) qolib ketgan ekan — HAQIQIY TOPILGAN BO'SHLIQ: dars qo'shilganda
-- (CourseService#resolveLinkedTopic) tizim AVTOMATIK ravishda xuddi shu
-- nom bilan bir Topic yaratadi/bog'laydi — 500 belgigacha dars nomi
-- "course_sections.title"da muvaffaqiyatli saqlanadi, lekin unga bog'langan
-- "topics.name" 255'dan oshsa "Data truncation" bilan muvaffaqiyatsiz
-- tugaydi (chalkash holat — foydalanuvchi Dars emas, Mavzu limitiga
-- urilganini bilmaydi). "topic_sections.name" (TEST BOSHQARUVI'dagi
-- "Bo'lim") va "science.name" (Fan) ham bir xillik uchun oshirildi.
ALTER TABLE topics
    MODIFY COLUMN name VARCHAR(500) NOT NULL;

ALTER TABLE topic_sections
    MODIFY COLUMN name VARCHAR(500) NOT NULL;

ALTER TABLE science
    MODIFY COLUMN name VARCHAR(500) NOT NULL;
