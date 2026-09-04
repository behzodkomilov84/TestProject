--liquibase formatted sql

--changeset behzod:91
-- Haqiqiy production bug (course-section-title-length.sql#67 bilan bir xil
-- sinf): uzun savol matni (masalan uzun klinik holat tavsifi) 255
-- belgidan oshib ketsa, "Data truncation: Data too long for column
-- 'question_text'" xatosi chiqib, ExcelService.importQuestions() ichida
-- Hibernate sessiyasi buzilib ("AssertionFailure ... null identifier"),
-- @Transactional butun importni (BARCHA qatorlarni, hatto to'g'ri
-- bo'lganlarini ham) orqaga qaytarardi — foydalanuvchiga esa hech qanday
-- tushunarli xabar ko'rinmasdi ("hech narsa o'zgarmadi").
ALTER TABLE questions
    MODIFY COLUMN question_text VARCHAR(2000) NOT NULL;
