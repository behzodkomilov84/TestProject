--liquibase formatted sql

--changeset behzod:69
-- Kursga to'g'ridan-to'g'ri (obuna so'rovi -> OWNER tasdig'i kutmasdan)
-- onlayn (Click) to'lov qilish uchun — PaymentOrder endi ADMIN-rol
-- obunasi (course_id=NULL, avvalgi xulq-atvor) YOKI muayyan kursga
-- to'lov (course_id belgilangan) bo'lishi mumkin. subscription_id'dagi
-- kabi haqiqiy FK constraint yo'q (soddalik uchun).
ALTER TABLE payment_orders
    ADD COLUMN course_id BIGINT NULL;
