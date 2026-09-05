--liquibase formatted sql

--changeset behzod:94
-- Ko'p to'g'ri javobli testlar (foydalanuvchi so'rovi, 2026-09-05: "javoblarining
-- bir nechtasi to'g'ri" savollar) uchun 1-bosqich (backend fundament) —
-- talaba TANLAGAN javoblar ro'yxatini saqlash uchun yangi ustun. Eski
-- "selected_answer_id" (BITTA javob) ustuni O'CHIRILMAYDI — hozirgi
-- ma'lumotlar va eski (hali yangilanmagan) mijozlar (Telegram bot)
-- shu orqali ishlashda davom etadi; YANGI yozuvlar ikkalasiga ham
-- yoziladi (TestSessionService/AssignmentAttemptService).
-- Format: vergul bilan ajratilgan Answer.id'lar ro'yxati, masalan "12,14".
ALTER TABLE test_session_questions
    ADD COLUMN selected_answer_ids VARCHAR(255) NULL;

--changeset behzod:95
ALTER TABLE attempt_answers
    ADD COLUMN selected_answer_ids VARCHAR(255) NULL;
