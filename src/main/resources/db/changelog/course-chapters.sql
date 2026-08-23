--liquibase formatted sql

--changeset behzod:73
-- "Bo'lim" (bob) — bitta kurs ICHIDA CourseSection'larni (mavzu/lesson)
-- guruhlash uchun (masalan "1-BOB: Kirish"). topic_sections (Fan -> Bo'lim
-- -> Mavzu, test bazasi ierarxiyasi) bilan aloqasi yo'q — kurs kontekstiga
-- xos, alohida jadval.
CREATE TABLE course_chapters
(
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    course_id   BIGINT       NOT NULL,
    name        VARCHAR(255) NOT NULL,
    order_index INT          NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_course_chapter_course_name (course_id, name),
    CONSTRAINT fk_course_chapter_course FOREIGN KEY (course_id)
        REFERENCES courses (id)
        ON DELETE CASCADE
);

--changeset behzod:74
-- CourseSection -> CourseChapter (ixtiyoriy, NULL = hali bo'limga
-- ajratilmagan — hozirgidek tekis ro'yxatda ko'rinadi).
ALTER TABLE course_sections
    ADD COLUMN chapter_id BIGINT NULL,
    ADD CONSTRAINT fk_course_section_chapter FOREIGN KEY (chapter_id)
        REFERENCES course_chapters (id)
        ON DELETE SET NULL;
