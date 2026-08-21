--liquibase formatted sql

--changeset behzod:64
-- Bo'lim matnini qo'lda yozish (PLAIN — xom matn, http(s) havolalar
-- avtomatik bosiladigan qilinadi) yoki fayldan import qilish (HTML —
-- .docx fayldan mammoth.js orqali formatlash saqlangan holda olingan
-- HTML) o'rtasidagi farqni belgilaydi. Eski qatorlar uchun default PLAIN —
-- ular xuddi avvalgidek ko'rsatiladi.
ALTER TABLE course_sections
    ADD COLUMN text_content_format VARCHAR(10) NOT NULL DEFAULT 'PLAIN';

--changeset behzod:65
-- .docx'dan import qilingan HTML kontent oddiy TEXT (65KB) chegarasidan
-- katta bo'lishi mumkin (formatlash teglariga sabab) — MEDIUMTEXT'ga
-- kengaytirildi (16MB gacha).
ALTER TABLE course_sections
    MODIFY COLUMN text_content MEDIUMTEXT NULL;
