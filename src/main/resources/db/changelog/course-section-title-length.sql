--liquibase formatted sql

--changeset behzod:67
-- Haqiqiy production bug: bo'lim nomi (masalan kitob bobi sarlavhasi
-- to'liq nusxalanganda) 200 belgidan oshib ketsa, "Data truncation: Data
-- too long for column 'title'" xatosi chiqib, GlobalRestExceptionHandler
-- buni umumiy "bog'liq ma'lumotlar mavjud" (FK xatosiga mo'ljallangan,
-- bu yerda noto'g'ri va chalg'ituvchi) xabar bilan qaytarardi.
ALTER TABLE course_sections
    MODIFY COLUMN title VARCHAR(500) NOT NULL;
