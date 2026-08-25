--liquibase formatted sql

--changeset behzod:79
-- Fanni (Science) "O'chirilganlar savati"ga o'tkazish uchun — Course.
-- deletedAt bilan bir xil g'oya: o'chirilganda DARHOL butunlay o'chmaydi
-- (Bo'lim/mavzu/savollari saqlanib qoladi), "♻️ Tiklash" bilan bir
-- zumda qaytadi.
ALTER TABLE science
    ADD COLUMN deleted_at DATETIME NULL;

--changeset behzod:80
-- Bo'limni (TopicSection) "O'chirilganlar savati"ga o'tkazish uchun.
ALTER TABLE topic_sections
    ADD COLUMN deleted_at DATETIME NULL;
