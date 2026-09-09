--liquibase formatted sql

-- "3 kunlik bepul sinov" (bonus obuna) — foydalanuvchi kurs sahifasida
-- "Obunaga so'rov yuborish" o'rniga endi to'g'ridan-to'g'ri (OWNER
-- tasdig'isiz) 3 kunlik bepul kirish oladi. is_trial=true — bitta
-- foydalanuvchi bitta kursda FAQAT BIR MARTA sinovdan foydalana olishi
-- uchun (CourseSubscriptionService#startFreeTrial shu ustunga qarab
-- tekshiradi) — foydalanuvchi so'rovi, 2026-09-09.
--changeset behzod:116
ALTER TABLE course_subscriptions ADD COLUMN is_trial BOOLEAN NOT NULL DEFAULT FALSE;
