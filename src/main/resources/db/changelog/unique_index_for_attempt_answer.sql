--liquibase formatted sql

--changeset behzod:34
ALTER TABLE attempt_answers
    ADD CONSTRAINT uk_attempt_question
        UNIQUE(attempt_id, question_id);