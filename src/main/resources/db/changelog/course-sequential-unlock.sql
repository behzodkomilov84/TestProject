--liquibase formatted sql

-- Kurs darajasida "ketma-ket ochilish" sozlamasi (foydalanuvchi so'rovi,
-- 2026-09-07): OWNER/ADMIN kurs yaratganda/tahrirlaganda tanlaydi —
-- "Barcha darslar ochiq" (sequential_unlock=false) yoki "Har bir
-- Mavzuning ketma-ket darslari — oldingisi tugatilgach keyingisi
-- ochiladi" (sequential_unlock=true). Bu MAVJUD (avvaldan CourseService
-- #isSectionUnlockedGivenPrev'da qattiq kodlangan, sozlanmaydigan)
-- ketma-ket-ochilish mantig'iga shunchaki ON/OFF tugmasi qo'shadi —
-- default TRUE, chunki hozirgacha BARCHA kurslar aynan shu (ketma-ket)
-- tartibda ishlagan, mavjud kurslar xatti-harakati o'zgarib qolmasligi
-- uchun.
--changeset behzod:114
ALTER TABLE courses ADD COLUMN sequential_unlock BOOLEAN NOT NULL DEFAULT TRUE;
