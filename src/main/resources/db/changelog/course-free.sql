--liquibase formatted sql

--changeset behzod:66
-- "Bepul kurs" — obunasiz ham (site'da HAM, Telegram bot'da HAM) hammaga
-- to'liq ochiq bo'ladigan kurs. CourseService.isSubscribed() shu maydonni
-- tekshirib, free=true bo'lsa har doim "obunasi bor" deb hisoblaydi.
ALTER TABLE courses
    ADD COLUMN free BOOLEAN NOT NULL DEFAULT FALSE;
