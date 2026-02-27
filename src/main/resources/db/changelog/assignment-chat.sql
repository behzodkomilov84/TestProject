--liquibase formatted sql

--changeset behzod:23
CREATE TABLE assignment_chat (
                                 id BIGINT AUTO_INCREMENT PRIMARY KEY,

                                 assignment_id BIGINT NOT NULL,
                                 sender_id BIGINT NOT NULL,

                                 message_text VARCHAR(2000) NOT NULL,

                                 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                 deleted BOOLEAN NOT NULL DEFAULT FALSE,

                                 CONSTRAINT fk_chat_assignment
                                     FOREIGN KEY (assignment_id)
                                         REFERENCES assignments(id)
                                         ON DELETE CASCADE,

                                 CONSTRAINT fk_chat_sender
                                     FOREIGN KEY (sender_id)
                                         REFERENCES users(id)
                                         ON DELETE CASCADE
);

--changeset behzod:24
CREATE INDEX idx_chat_assignment
    ON assignment_chat(assignment_id);

--changeset behzod:25
CREATE INDEX idx_chat_created_at
    ON assignment_chat(created_at);
