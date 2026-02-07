--liquibase formatted sql

--changeset raw:init-teacher-module::1
CREATE TABLE teacher_groups
(
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    teacher_id BIGINT       NOT NULL,
    name       VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_group_teacher FOREIGN KEY (teacher_id)
        REFERENCES users (id)
        ON DELETE CASCADE
);

--changeset raw:init-teacher-module::2
CREATE TABLE group_members
(
    id        BIGINT AUTO_INCREMENT PRIMARY KEY,
    group_id  BIGINT NOT NULL,
    pupil_id  BIGINT NOT NULL,
    joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_group_member UNIQUE (group_id, pupil_id),
    CONSTRAINT fk_member_group FOREIGN KEY (group_id)
        REFERENCES teacher_groups (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_member_user FOREIGN KEY (pupil_id)
        REFERENCES users (id)
        ON DELETE CASCADE
);

--changeset raw:init-teacher-module::3
CREATE TABLE group_invites
(
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    group_id   BIGINT      NOT NULL,
    pupil_id   BIGINT      NOT NULL,
    status     VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    accepted   BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP            DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_group_invite UNIQUE (group_id, pupil_id),
    CONSTRAINT fk_invite_group FOREIGN KEY (group_id)
        REFERENCES teacher_groups (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_invite_user FOREIGN KEY (pupil_id)
        REFERENCES users (id)
        ON DELETE CASCADE
);

--changeset raw:init-teacher-module::4
CREATE TABLE question_sets
(
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    teacher_id BIGINT       NOT NULL,
    name       VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_set_teacher FOREIGN KEY (teacher_id)
        REFERENCES users (id)
        ON DELETE CASCADE
);

--changeset raw:init-teacher-module::5
CREATE TABLE question_set_items
(
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    set_id      BIGINT NOT NULL,
    question_id BIGINT NOT NULL,
    CONSTRAINT uq_set_question UNIQUE (set_id, question_id),
    CONSTRAINT fk_set_item_set FOREIGN KEY (set_id)
        REFERENCES question_sets (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_set_item_question FOREIGN KEY (question_id)
        REFERENCES questions (id)
        ON DELETE CASCADE
);

--changeset raw:init-teacher-module::6
CREATE TABLE assignments
(
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    set_id      BIGINT    NOT NULL,
    group_id    BIGINT    NULL,
    pupil_id    BIGINT    NULL,
    assigned_by BIGINT    NOT NULL,
    assigned_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    due_date    TIMESTAMP NULL,
    CONSTRAINT fk_assignment_set FOREIGN KEY (set_id)
        REFERENCES question_sets (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_assignment_group FOREIGN KEY (group_id)
        REFERENCES teacher_groups (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_assignment_pupil FOREIGN KEY (pupil_id)
        REFERENCES users (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_assignment_teacher FOREIGN KEY (assigned_by)
        REFERENCES users (id)
        ON DELETE CASCADE
);

--changeset raw:init-teacher-module::7
CREATE TABLE assignment_attempts
(
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    assignment_id   BIGINT    NOT NULL,
    pupil_id        BIGINT    NOT NULL,
    total_questions INT       NOT NULL,
    correct_answers INT       NOT NULL,
    percent         INT       NOT NULL,
    duration_sec    INT       NOT NULL,
    started_at      TIMESTAMP NULL,
    finished_at     TIMESTAMP NULL,
    CONSTRAINT fk_attempt_assignment FOREIGN KEY (assignment_id)
        REFERENCES assignments (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_attempt_user FOREIGN KEY (pupil_id)
        REFERENCES users (id)
        ON DELETE CASCADE
);

--changeset raw:init-teacher-module::8
CREATE TABLE attempt_answers
(
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    attempt_id         BIGINT  NOT NULL,
    question_id        BIGINT  NOT NULL,
    selected_answer_id BIGINT  NULL,
    is_correct         BOOLEAN NOT NULL,
    CONSTRAINT fk_answer_attempt FOREIGN KEY (attempt_id)
        REFERENCES assignment_attempts (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_answer_question FOREIGN KEY (question_id)
        REFERENCES questions (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_answer_selected FOREIGN KEY (selected_answer_id)
        REFERENCES answers (id)
        ON DELETE SET NULL
);
