--liquibase formatted sql

--changeset behzod:26
ALTER TABLE users ADD telegram_id BIGINT UNIQUE;

--changeset behzod:27
ALTER TABLE assignment_attempts ADD current_question_index INT DEFAULT 0;

--changeset behzod:28
CREATE UNIQUE INDEX idx_user_telegram_id ON users (telegram_id);

--changeset behzod:29
CREATE INDEX idx_recipient_assignment_pupil
    ON assignment_recipients (pupil_id, assignment_id);

--changeset behzod:30
CREATE TABLE telegram_link_codes (

                                     id BIGINT AUTO_INCREMENT PRIMARY KEY,

                                     user_id BIGINT NOT NULL,

                                     code VARCHAR(10) NOT NULL UNIQUE,

                                     created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

                                     used BOOLEAN DEFAULT FALSE,

                                     CONSTRAINT fk_link_user
                                         FOREIGN KEY (user_id)
                                             REFERENCES users(id)
                                             ON DELETE CASCADE
);
