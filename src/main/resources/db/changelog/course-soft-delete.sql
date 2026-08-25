--liquibase formatted sql

--changeset behzod:75
-- Kursni "O'chirilganlar savati"ga o'tkazish uchun — bosilganda kurs
-- DARHOL butunlay o'chirilmaydi (bo'limlar/mavzular/obunalar HAM
-- saqlanib qoladi), faqat shu ustun bilan belgilanadi. Shu bilan
-- kursning barcha bog'liq ma'lumotlari (course_sections, course_chapters,
-- course_subscriptions, course_section_progress) TEGILMAY qoladi — birror
-- vaqt ichida ("O'chirilganlar" sahifasidan) bir tugma bilan qaytadan
-- tiklash mumkin (CourseService.restoreCourse). NULL — o'chirilmagan
-- (odatiy holat).
ALTER TABLE courses
    ADD COLUMN deleted_at DATETIME NULL;
