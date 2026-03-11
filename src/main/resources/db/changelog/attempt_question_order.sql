--liquibase formatted sql

--changeset behzod:31
CREATE TABLE attempt_question_order (
                                        id BIGINT NOT NULL AUTO_INCREMENT,
                                        attempt_id BIGINT NOT NULL,
                                        question_id BIGINT NOT NULL,
                                        position INT NOT NULL,

                                        CONSTRAINT pk_attempt_question_order
                                            PRIMARY KEY (id),

                                        CONSTRAINT fk_attempt_question_order_attempt
                                            FOREIGN KEY (attempt_id)
                                                REFERENCES assignment_attempts(id)
                                                ON DELETE CASCADE,

                                        CONSTRAINT fk_attempt_question_order_question
                                            FOREIGN KEY (question_id)
                                                REFERENCES questions(id)
                                                ON DELETE CASCADE
);

--changeset behzod:32
CREATE INDEX idx_attempt_question_order_attempt
    ON attempt_question_order(attempt_id);

--changeset behzod:33
CREATE UNIQUE INDEX uk_attempt_question_position
    ON attempt_question_order(attempt_id, position);