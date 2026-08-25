--liquibase formatted sql

--changeset behzod:76
-- Mavzuni (Topic, TEST BOSHQARUVI) "O'chirilganlar savati"ga o'tkazish
-- uchun — Course'dagi bilan bir xil g'oya (course-soft-delete.sql):
-- o'chirilganda DARHOL butunlay o'chmaydi, savollari saqlanib qoladi,
-- "♻️ Tiklash" bilan bir zumda qaytadi.
ALTER TABLE topics
    ADD COLUMN deleted_at DATETIME NULL;

--changeset behzod:77
-- Kurs mavzusini (CourseSection — dars/lesson) "O'chirilganlar savati"ga
-- o'tkazish uchun — xuddi shu g'oya: progress yozuvlari saqlanib qoladi.
ALTER TABLE course_sections
    ADD COLUMN deleted_at DATETIME NULL;

--changeset behzod:78
-- Savolni (Question) "O'chirilganlar savati"ga o'tkazish uchun — javoblari
-- (Answer) saqlanib qoladi.
ALTER TABLE questions
    ADD COLUMN deleted_at DATETIME NULL;
