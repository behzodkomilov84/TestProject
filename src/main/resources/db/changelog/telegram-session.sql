--liquibase formatted sql

-- Botdagi ko'p bosqichli suhbatlar (masalan profilni tahrirlash) uchun
-- holat saqlash. Xotirada (masalan static Map) emas, bazada — chunki
-- production tez-tez qayta ishga tushadi (deploy), xotiradagi holat har
-- safar yo'qolib, foydalanuvchini suhbat o'rtasida "uzib qo'yardi".
--changeset behzod:60
CREATE TABLE telegram_sessions (
    chat_id BIGINT PRIMARY KEY,
    state VARCHAR(50) NOT NULL,
    temp_data TEXT NULL,
    updated_at DATETIME NOT NULL
);
