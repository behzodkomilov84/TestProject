--liquibase formatted sql

--changeset behzod:70
-- "Bo'lim" — Fan (science) ichidagi mavzular guruhi (masalan Kimyo fanida
-- "I. UMUMIY KIMYO", "II. ANORGANIK KIMYO"...). CourseSection
-- (course_sections — kurs video/matn darslari) bilan aloqasi yo'q.
CREATE TABLE topic_sections
(
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    science_id  BIGINT       NOT NULL,
    name        VARCHAR(255) NOT NULL,
    order_index INT          NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_topic_section_science_name (science_id, name),
    CONSTRAINT fk_topic_section_science FOREIGN KEY (science_id)
        REFERENCES science (id)
        ON DELETE CASCADE
);

--changeset behzod:71
-- Topic -> TopicSection (ixtiyoriy, NULL = hali bo'limga ajratilmagan) +
-- aniq tartib raqami (ilgari "ORDER BY t.id" workaround ishlatilgan edi).
ALTER TABLE topics
    ADD COLUMN section_id  BIGINT NULL,
    ADD COLUMN order_index INT    NULL,
    ADD CONSTRAINT fk_topic_section FOREIGN KEY (section_id)
        REFERENCES topic_sections (id)
        ON DELETE SET NULL;

--changeset behzod:72
-- order_index'ni hozirgi (id bo'yicha) tartibdan avtomatik to'ldirish —
-- barcha muhitda (dev/CI/prod) xavfsiz, chunki faqat hosila ma'lumot.
-- DIQQAT: bu Kimyo/Ona tili/Bakteriologiya'ga Bo'lim biriktirish bilan
-- ALOQASI YO'Q — bo'lim biriktirish alohida, qo'lda ishga tushiriladigan
-- production skriptida (bu changelog'ga KIRMAYDI, chunki production'ga
-- xos aniq id'larni o'z ichiga oladi va faqat bir marta ishga tushishi
-- kerak).
SET @rn := 0;
UPDATE topics t
    JOIN (
        SELECT id, (@rn := @rn + 1) AS rn
        FROM topics
        ORDER BY science_id, id
    ) ranked ON ranked.id = t.id
SET t.order_index = ranked.rn;
