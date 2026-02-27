--liquibase formatted sql

--changeset behzod:1
CREATE TABLE roles (
                       id BIGINT NOT NULL AUTO_INCREMENT,
                       role_name VARCHAR(255),
                       PRIMARY KEY (id)
);

INSERT INTO roles (role_name)
VALUES ('ROLE_OWNER'),
       ('ROLE_ADMIN'),
       ('ROLE_USER');

--changeset behzod:2
CREATE TABLE users (
                       id BIGINT NOT NULL AUTO_INCREMENT,
                       username VARCHAR(255) NOT NULL,
                       password VARCHAR(255) NOT NULL,
                       role_id BIGINT NOT NULL,
                       group_id bigint,
                       PRIMARY KEY (id),
                       CONSTRAINT fk_user_role FOREIGN KEY (role_id)
                           REFERENCES roles(id)
);

--changeset behzod:3
CREATE TABLE teacher_groups (
                                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                teacher_id BIGINT NOT NULL,
                                name VARCHAR(255) NOT NULL,
                                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                CONSTRAINT fk_group_teacher FOREIGN KEY (teacher_id)
                                    REFERENCES users(id)
                                    ON DELETE CASCADE
);

--changeset behzod:4
CREATE TABLE group_members (
                               id BIGINT AUTO_INCREMENT PRIMARY KEY,
                               group_id BIGINT NOT NULL,
                               pupil_id BIGINT NOT NULL,
                               joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                               CONSTRAINT uq_group_member UNIQUE (group_id, pupil_id),
                               CONSTRAINT fk_member_group FOREIGN KEY (group_id)
                                   REFERENCES teacher_groups(id)
                                   ON DELETE CASCADE,
                               CONSTRAINT fk_member_user FOREIGN KEY (pupil_id)
                                   REFERENCES users(id)
                                   ON DELETE CASCADE
);

--changeset behzod:5
CREATE TABLE science (
                         id BIGINT NOT NULL AUTO_INCREMENT,
                         name VARCHAR(255) NOT NULL UNIQUE,
                         PRIMARY KEY (id)
);

--changeset behzod:6
CREATE TABLE topics (
                        id BIGINT NOT NULL AUTO_INCREMENT,
                        science_id BIGINT NOT NULL,
                        name VARCHAR(255) NOT NULL,
                        PRIMARY KEY (id),
                        UNIQUE KEY uk_science_topic (science_id, name),
                        CONSTRAINT fk_topic_science FOREIGN KEY (science_id)
                            REFERENCES science(id)
);

--changeset behzod:7
CREATE TABLE questions (
                           id BIGINT NOT NULL AUTO_INCREMENT,
                           topic_id BIGINT NOT NULL,
                           question_text VARCHAR(255) NOT NULL,
                           PRIMARY KEY (id)
);

--changeset behzod:8
CREATE TABLE answers (
                         id BIGINT NOT NULL AUTO_INCREMENT,
                         question_id BIGINT NOT NULL,
                         answer_text VARCHAR(255) NOT NULL,
                         is_true BIT NOT NULL,
                         commentary MEDIUMTEXT NULL,
                         PRIMARY KEY (id),
                         CONSTRAINT fk_answers_question FOREIGN KEY (question_id)
                             REFERENCES questions(id)
                             ON DELETE CASCADE
);

--changeset behzod:9
CREATE TABLE test_sessions (
                               id BIGINT AUTO_INCREMENT PRIMARY KEY,
                               user_id BIGINT NOT NULL,
                               total_questions INT NULL,
                               correct_answers INT NULL,
                               wrong_answers INT NULL,
                               percent INT NULL,
                               duration_sec BIGINT NULL,
                               started_at TIMESTAMP NOT NULL,
                               finished_at TIMESTAMP NULL,
                               CONSTRAINT fk_test_sessions_user FOREIGN KEY (user_id)
                                   REFERENCES users(id)
);

--changeset behzod:10
CREATE TABLE question_sets (
                               id BIGINT AUTO_INCREMENT PRIMARY KEY,
                               teacher_id BIGINT NOT NULL,
                               name VARCHAR(255) NOT NULL,
                               created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                               CONSTRAINT fk_set_teacher FOREIGN KEY (teacher_id)
                                   REFERENCES users(id)
                                   ON DELETE CASCADE
);

--changeset behzod:11
CREATE TABLE question_set_items (
                                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                    set_id BIGINT NOT NULL,
                                    question_id BIGINT NOT NULL,
                                    CONSTRAINT uq_set_question UNIQUE (set_id, question_id),
                                    CONSTRAINT fk_set_item_set FOREIGN KEY (set_id)
                                        REFERENCES question_sets(id)
                                        ON DELETE CASCADE,
                                    CONSTRAINT fk_set_item_question FOREIGN KEY (question_id)
                                        REFERENCES questions(id)
                                        ON DELETE CASCADE
);

--changeset behzod:12
CREATE TABLE assignments (
                             id BIGINT AUTO_INCREMENT PRIMARY KEY,
                             set_id BIGINT NOT NULL,
                             group_id BIGINT NULL,
                             assigned_by BIGINT NOT NULL,
                             assigned_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                             due_date TIMESTAMP NULL,
                             CONSTRAINT fk_assignment_set FOREIGN KEY (set_id)
                                 REFERENCES question_sets(id)
                                 ON DELETE CASCADE,
                             CONSTRAINT fk_assignment_group FOREIGN KEY (group_id)
                                 REFERENCES teacher_groups(id)
                                 ON DELETE CASCADE,
                             CONSTRAINT fk_assignment_teacher FOREIGN KEY (assigned_by)
                                 REFERENCES users(id)
                                 ON DELETE CASCADE
);

--changeset behzod:13
CREATE TABLE assignment_recipients (
                                       id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                       assignment_id BIGINT NOT NULL,
                                       pupil_id BIGINT NOT NULL,
                                       CONSTRAINT fk_recipient_assignment FOREIGN KEY (assignment_id)
                                           REFERENCES assignments(id)
                                           ON DELETE CASCADE,
                                       CONSTRAINT fk_recipient_user FOREIGN KEY (pupil_id)
                                           REFERENCES users(id)
                                           ON DELETE CASCADE,
                                       CONSTRAINT uq_assignment_student UNIQUE (assignment_id, pupil_id)
);

--changeset behzod:14
CREATE TABLE assignment_attempts (
                                     id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                     assignment_id BIGINT NOT NULL,
                                     pupil_id BIGINT NOT NULL,
                                     total_questions INT NOT NULL,
                                     correct_answers INT DEFAULT 0,
                                     percent INT DEFAULT 0,
                                     duration_sec INT DEFAULT 0,
                                     started_at TIMESTAMP NULL,
                                     finished_at TIMESTAMP NULL,
                                     last_sync TIMESTAMP NULL,
                                     CONSTRAINT fk_attempt_assignment FOREIGN KEY (assignment_id)
                                         REFERENCES assignments(id)
                                         ON DELETE CASCADE,
                                     CONSTRAINT fk_attempt_user FOREIGN KEY (pupil_id)
                                         REFERENCES users(id)
                                         ON DELETE CASCADE,
                                     UNIQUE KEY uq_attempt (assignment_id, pupil_id)
);

--changeset behzod:15
CREATE TABLE attempt_answers (
                                 id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                 attempt_id BIGINT NOT NULL,
                                 question_id BIGINT NOT NULL,
                                 selected_answer_id BIGINT NULL,
                                 is_correct BOOLEAN DEFAULT 0,
                                 CONSTRAINT fk_answer_attempt FOREIGN KEY (attempt_id)
                                     REFERENCES assignment_attempts(id)
                                     ON DELETE CASCADE,
                                 CONSTRAINT fk_answer_question FOREIGN KEY (question_id)
                                     REFERENCES questions(id)
                                     ON DELETE CASCADE,
                                 CONSTRAINT fk_answer_selected FOREIGN KEY (selected_answer_id)
                                     REFERENCES answers(id)
                                     ON DELETE SET NULL,
                                 UNIQUE KEY uq_answer (attempt_id, question_id)
);

--changeset behzod:16
CREATE TABLE test_session_questions (
                                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                        test_session_id BIGINT NOT NULL,
                                        question_id BIGINT NOT NULL,
                                        selected_answer_id BIGINT NULL,
                                        is_correct BOOLEAN NOT NULL,
                                        CONSTRAINT fk_tsq_session FOREIGN KEY (test_session_id)
                                            REFERENCES test_sessions(id)
                                            ON DELETE CASCADE,
                                        CONSTRAINT fk_tsq_question FOREIGN KEY (question_id)
                                            REFERENCES questions(id)
                                            ON DELETE CASCADE,
                                        CONSTRAINT fk_tsq_selected_answer FOREIGN KEY (selected_answer_id)
                                            REFERENCES answers(id)
                                            ON DELETE SET NULL
);

--changeset behzod:17
CREATE TABLE user_question_stats (
                                     user_id BIGINT,
                                     question_id BIGINT,
                                     total_attempts INT DEFAULT 0,
                                     correct_attempts INT DEFAULT 0,
                                     PRIMARY KEY (user_id, question_id)
);

--changeset behzod:18
CREATE TABLE group_invites (
                               id BIGINT AUTO_INCREMENT PRIMARY KEY,
                               group_id BIGINT NOT NULL,
                               pupil_id BIGINT NOT NULL,
                               status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
                               created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

                               CONSTRAINT uq_group_invite UNIQUE (group_id, pupil_id),

                               CONSTRAINT fk_invite_group FOREIGN KEY (group_id)
                                   REFERENCES teacher_groups (id)
                                   ON DELETE CASCADE,

                               CONSTRAINT fk_invite_user FOREIGN KEY (pupil_id)
                                   REFERENCES users (id)
                                   ON DELETE CASCADE
);

--changeset behzod:19
CREATE INDEX idx_attempt_assignment ON assignment_attempts(assignment_id);

--changeset behzod:20
CREATE INDEX idx_assignment_group ON assignments(group_id);

--changeset behzod:21
CREATE INDEX idx_recipient_assignment ON assignment_recipients(assignment_id);

--changeset behzod:22
CREATE INDEX idx_recipient_student ON assignment_recipients(pupil_id);
