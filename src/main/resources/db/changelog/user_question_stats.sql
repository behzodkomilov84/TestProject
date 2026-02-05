CREATE TABLE user_question_stats
(
    user_id          BIGINT,
    question_id      BIGINT,

    total_attempts   INT DEFAULT 0,
    correct_attempts INT DEFAULT 0,

    PRIMARY KEY (user_id, question_id)
);
