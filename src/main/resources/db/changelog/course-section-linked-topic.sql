--liquibase formatted sql

--changeset behzod:63
ALTER TABLE course_sections
    ADD COLUMN linked_topic_id BIGINT NULL,
    ADD CONSTRAINT fk_course_section_linked_topic FOREIGN KEY (linked_topic_id) REFERENCES topics(id);
