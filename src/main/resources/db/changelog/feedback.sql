--liquibase formatted sql

-- "Fikr va takliflar" — sayt bo'yicha (kursga bog'liq emas, global) ochiq
-- fikr-taklif taxtasi: istalgan login qilgan foydalanuvchi fikr/taklif
-- yozishi, BOSHQA foydalanuvchilar ham javob berishi mumkin (foydalanuvchi
-- so'rovi, 2026-09-06). "course_forum_threads" bilan bir xil andoza
-- (ON DELETE CASCADE — foydalanuvchi o'chirilganda uning fikrlari ham
-- avtomatik o'chadi, UserServiceImpl#deleteUser'ga qo'shimcha o'zgartirish
-- shart emas), farqi — kursga bog'liq emas va OWNER/ADMIN moderatsiya
-- uchun "status" ustuni bor (FeedbackStatus: OPEN/IN_PROGRESS/DONE/REJECTED).

--changeset behzod:102
CREATE TABLE feedback_threads (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    author_id BIGINT NOT NULL,
    feedback_text VARCHAR(4000) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    created_at DATETIME NOT NULL,
    deleted_at DATETIME NULL,
    CONSTRAINT fk_feedback_thread_author FOREIGN KEY (author_id) REFERENCES users(id) ON DELETE CASCADE
);

--changeset behzod:103
CREATE INDEX idx_feedback_thread_created ON feedback_threads(created_at);

--changeset behzod:104
CREATE TABLE feedback_replies (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    thread_id BIGINT NOT NULL,
    author_id BIGINT NOT NULL,
    reply_text VARCHAR(4000) NOT NULL,
    created_at DATETIME NOT NULL,
    deleted_at DATETIME NULL,
    CONSTRAINT fk_feedback_reply_thread FOREIGN KEY (thread_id) REFERENCES feedback_threads(id) ON DELETE CASCADE,
    CONSTRAINT fk_feedback_reply_author FOREIGN KEY (author_id) REFERENCES users(id) ON DELETE CASCADE
);

--changeset behzod:105
CREATE INDEX idx_feedback_reply_thread ON feedback_replies(thread_id);
