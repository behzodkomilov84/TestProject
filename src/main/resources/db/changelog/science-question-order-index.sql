--liquibase formatted sql

--changeset behzod:81
-- Fanlarni A-Z/Z-A saralash va qo'lda tartiblash (⬆⬇) imkoniyati uchun —
-- TopicSection/Topic/CourseSection'da allaqachon mavjud bo'lgan
-- "order_index" konvensiyasi bilan bir xil.
ALTER TABLE science
    ADD COLUMN order_index INT NULL;

--changeset behzod:82
-- order_index'ni hozirgi (id bo'yicha) tartibdan avtomatik to'ldirish —
-- barcha muhitda xavfsiz, faqat hosila ma'lumot (topics.order_index'ning
-- ilgari qilingan backfill'i bilan bir xil texnika).
SET @rn_science := 0;
UPDATE science s
    JOIN (
        SELECT id, (@rn_science := @rn_science + 1) AS rn
        FROM science
        ORDER BY id
    ) ranked ON ranked.id = s.id
SET s.order_index = ranked.rn;

--changeset behzod:83
-- Savollarni A-Z/Z-A saralash va qo'lda tartiblash (⬆⬇) imkoniyati uchun.
ALTER TABLE questions
    ADD COLUMN order_index INT NULL;

--changeset behzod:84
SET @rn_question := 0;
UPDATE questions q
    JOIN (
        SELECT id, (@rn_question := @rn_question + 1) AS rn
        FROM questions
        ORDER BY topic_id, id
    ) ranked ON ranked.id = q.id
SET q.order_index = ranked.rn;
