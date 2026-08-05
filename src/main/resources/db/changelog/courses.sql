--liquibase formatted sql

--changeset behzod:42
CREATE TABLE courses (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    description TEXT NULL,
    cover_image_url VARCHAR(500) NULL,
    published BOOLEAN NOT NULL DEFAULT FALSE,
    created_by BIGINT NOT NULL,
    created_at DATETIME NOT NULL,
    CONSTRAINT fk_course_created_by FOREIGN KEY (created_by) REFERENCES users(id)
);

--changeset behzod:43
CREATE TABLE course_sections (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    course_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    order_index INT NOT NULL,
    type VARCHAR(10) NOT NULL,
    text_content TEXT NULL,
    video_source_type VARCHAR(10) NULL,
    video_url VARCHAR(1000) NULL,
    video_duration_seconds INT NULL,
    created_at DATETIME NOT NULL,
    CONSTRAINT fk_course_section_course FOREIGN KEY (course_id) REFERENCES courses(id)
);

--changeset behzod:44
CREATE INDEX idx_course_section_course_order ON course_sections (course_id, order_index);

--changeset behzod:45
CREATE TABLE course_subscriptions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    course_id BIGINT NOT NULL,
    amount DECIMAL(12,2) NOT NULL,
    status VARCHAR(20) NOT NULL,
    confirmed_by BIGINT NULL,
    note VARCHAR(500) NULL,
    created_at DATETIME NOT NULL,
    CONSTRAINT fk_course_subscription_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_course_subscription_course FOREIGN KEY (course_id) REFERENCES courses(id),
    CONSTRAINT fk_course_subscription_confirmed_by FOREIGN KEY (confirmed_by) REFERENCES users(id)
);

--changeset behzod:46
CREATE INDEX idx_course_subscription_user_course ON course_subscriptions (user_id, course_id, status);

--changeset behzod:47
CREATE TABLE course_section_progress (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    section_id BIGINT NOT NULL,
    completed_at DATETIME NOT NULL,
    CONSTRAINT fk_course_progress_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_course_progress_section FOREIGN KEY (section_id) REFERENCES course_sections(id),
    CONSTRAINT uq_course_progress_user_section UNIQUE (user_id, section_id)
);
