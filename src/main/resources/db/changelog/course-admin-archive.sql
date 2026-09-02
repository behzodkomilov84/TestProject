--liquibase formatted sql

--changeset behzod:85
-- ROLE_ADMIN o'z kursini "Butunlay o'chirish" desa — ROLE_OWNER'dan
-- farqli, HAQIQIY (bazadan butunlay) o'chirilmaydi: shu ADMIN va
-- katalogdan yo'qoladi, lekin ma'lumotlari saqlanib qoladi, ROLE_OWNER
-- hali ham ko'ra oladi (CourseService.permanentlyDeleteCourse) va xohlasa
-- o'z nomiga o'tkazib qayta tiklashi mumkin (reclaimArchivedCourse).
-- NULL — arxivlanmagan (odatiy holat).
ALTER TABLE courses
    ADD COLUMN archived_by_admin_id BIGINT NULL,
    ADD COLUMN archived_at DATETIME NULL,
    ADD CONSTRAINT fk_course_archived_by_admin
        FOREIGN KEY (archived_by_admin_id)
            REFERENCES users(id)
            ON DELETE SET NULL;
