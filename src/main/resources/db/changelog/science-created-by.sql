--liquibase formatted sql

-- ADMIN cheklovi: "test boshqaruvi" (Fan/Bo'lim/Mavzu/Savol) uchun ham
-- Course'dagi kabi egalik (createdBy) qo'shiladi (foydalanuvchi so'rovi,
-- 2026-09-08: "ROLE_ADMIN o'zi yaratmagan hech qaysi joyda o'zgartirish
-- qila olmasin. Kursda, test boshqaruvida ..."). Course'dan farqli — bu
-- ustun NULL bo'lishi mumkin (courses.created_by kabi NOT NULL emas),
-- chunki mavjud fanlar hech qanday muallifsiz yaratilgan edi. Shu sabab
-- BARCHA mavjud yozuvlar shu yerda darhol egalarga tayinlanadi
-- (foydalanuvchi ANIQ ko'rsatmasi bilan, 2026-09-08): "Bakteriologiya"
-- fani Xayrullo Komilov'ga (id=3, username "xkomilov") — u aynan shu
-- fanni haqiqatda yaratgan/boshqarib kelgan; qolgan BARCHA fanlar
-- (shu jumladan o'chirilganlari ham) Behzod Komilov'ga (id=1). Bundan
-- keyin YANGI yaratiladigan fanlar o'z muallifiga avtomatik biriktiriladi
-- (ScienceService#createScience).
--changeset behzod:115
ALTER TABLE science ADD COLUMN created_by BIGINT NULL;

ALTER TABLE science
    ADD CONSTRAINT fk_science_created_by FOREIGN KEY (created_by) REFERENCES users(id);

UPDATE science SET created_by = 3 WHERE name = 'Bakteriologiya';
UPDATE science SET created_by = 1 WHERE created_by IS NULL;
