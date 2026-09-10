--liquibase formatted sql
--changeset behzod:120
-- "Oxirgi tashrif vaqti" katakchasi bosilganda ochiladigan oynadagi
-- "Saytda jami necha soat" ko'rsatkichi uchun (foydalanuvchi so'rovi,
-- 2026-09-10). UserActivityTracker orqali (throttled — har ~60 soniyada
-- bir marta flush) to'planadi. total_seconds — foydalanuvchi "faol"
-- deb hisoblangan vaqtning yig'indisi (ketma-ket so'rovlar orasidagi
-- bo'shliq 5 daqiqadan oshsa, o'sha oraliq hisoblanmaydi — foydalanuvchi
-- "ketgan" deb qabul qilinadi).
CREATE TABLE user_activity_totals (
    user_id BIGINT NOT NULL PRIMARY KEY,
    total_seconds BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_activity_totals_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

--changeset behzod:121
-- Xuddi shu g'oya, lekin KURS kesimida (foydalanuvchi so'rovi: "Kurslar
-- kesimida qancha soatlari kursga aloqador sahifalarda ketyapti?") —
-- so'rov yo'li "/courses/{id}..." yoki "/api/courses/{id}..." bilan mos
-- kelsa, o'sha oraliq shu kursga ham qo'shiladi.
CREATE TABLE user_course_activity_totals (
    user_id BIGINT NOT NULL,
    course_id BIGINT NOT NULL,
    total_seconds BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (user_id, course_id),
    CONSTRAINT fk_course_activity_totals_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_course_activity_totals_course FOREIGN KEY (course_id) REFERENCES courses(id) ON DELETE CASCADE
);
