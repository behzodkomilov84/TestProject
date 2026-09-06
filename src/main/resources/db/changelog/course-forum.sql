--liquibase formatted sql

-- "Forum" — kurs foydalanuvchilari kurs muallifiga savol berishi va
-- BOSHQA foydalanuvchilar ham shu savolga javob berishi mumkin bo'lgan
-- muhokama taxtasi (foydalanuvchi so'rovi, 2026-09-06). "assignment_chat"
-- (fk_chat_sender) bilan bir xil andoza — ON DELETE CASCADE, foydalanuvchi
-- o'chirilganda uning forum yozuvlari ham avtomatik o'chadi (UserServiceImpl
-- #deleteUser'ga qo'shimcha o'zgartirish shart emas).

--changeset behzod:98
CREATE TABLE course_forum_threads (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    course_id BIGINT NOT NULL,
    author_id BIGINT NOT NULL,
    question_text VARCHAR(4000) NOT NULL,
    created_at DATETIME NOT NULL,
    deleted_at DATETIME NULL,
    CONSTRAINT fk_forum_thread_course FOREIGN KEY (course_id) REFERENCES courses(id) ON DELETE CASCADE,
    CONSTRAINT fk_forum_thread_author FOREIGN KEY (author_id) REFERENCES users(id) ON DELETE CASCADE
);

--changeset behzod:99
CREATE INDEX idx_forum_thread_course ON course_forum_threads(course_id);

--changeset behzod:100
CREATE TABLE course_forum_replies (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    thread_id BIGINT NOT NULL,
    author_id BIGINT NOT NULL,
    reply_text VARCHAR(4000) NOT NULL,
    created_at DATETIME NOT NULL,
    deleted_at DATETIME NULL,
    CONSTRAINT fk_forum_reply_thread FOREIGN KEY (thread_id) REFERENCES course_forum_threads(id) ON DELETE CASCADE,
    CONSTRAINT fk_forum_reply_author FOREIGN KEY (author_id) REFERENCES users(id) ON DELETE CASCADE
);

--changeset behzod:101
CREATE INDEX idx_forum_reply_thread ON course_forum_replies(thread_id);
