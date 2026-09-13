--liquibase formatted sql

--changeset behzod:124
-- Foydalanuvchi so'rovi (2026-09-13): "Тестлар базасидаги айрим тестлар 1
-- дан ортиқ мавзуларга тушиши керак бўлиб қолади" — bitta dars/test
-- materialini bir nechta Mavzuga (Topic) MUSTAQIL NUSXA sifatida
-- bog'lash kerak bo'lgan holatlar bor, lekin "topics" jadvalidagi
-- "uk_science_topic" (science_id, name) cheklovi bir xil nomli Mavzuni
-- BUTUN FAN (kurs) bo'yicha faqat BITTA marta bo'lishga majburlar edi —
-- qaysi Bo'limga (topic_sections) tegishli bo'lishidan qat'iy nazar.
-- CourseService#resolveLinkedTopic shu sabab bir xil nomli Mavzuni
-- (savollari bilan birga) YANGI Bo'limga "ko'chirib" qo'yardi — haqiqiy
-- mustaqil nusxa yaratish imkonsiz edi. Endi noyoblik BO'LIM darajasida
-- (science_id, section_id, name) — eskisidan KENGROQ (3 ustunli) cheklov
-- bo'lgani uchun, mavjud ma'lumotlarga hech qanday xavf yo'q (eski
-- cheklovga mos kelgan har qanday qator yangisiga ham mos keladi).
-- DIQQAT: DROP va ADD bitta ALTER TABLE ichida, IKKITA ALOHIDA
-- statement sifatida EMAS — "uk_science_topic" (science_id, name)
-- "fk_topic_science" FK'sining chap tomonidagi ustuni (science_id) uchun
-- kerakli indeks vazifasini ham bajaradi; alohida DROP qilinsa, MySQL
-- "Cannot drop index 'uk_science_topic': needed in a foreign key
-- constraint" xatosini beradi (HAQIQIY TOPILGAN — test kontekstida
-- ushlangan). Bitta ALTER TABLE ichida esa MySQL ikkala amalning
-- YAKUNIY natijasini birga baholaydi — yangi "uk_science_section_topic"
-- ham "science_id" bilan boshlangani uchun, FK talabini xuddi
-- eskisidek qondiraveradi.
ALTER TABLE topics
    DROP INDEX uk_science_topic,
    ADD CONSTRAINT uk_science_section_topic UNIQUE (science_id, section_id, name);
