CREATE TABLE test_session_questions
(
    id BIGINT AUTO_INCREMENT PRIMARY KEY,

    test_session_id BIGINT NOT NULL,
    question_id BIGINT NOT NULL,
    selected_answer_id BIGINT NULL,

    is_correct BOOLEAN NOT NULL,

    CONSTRAINT fk_tsq_session
        FOREIGN KEY (test_session_id)
            REFERENCES test_sessions(id)
            ON DELETE CASCADE,

    CONSTRAINT fk_tsq_question
        FOREIGN KEY (question_id)
            REFERENCES questions(id)
            ON DELETE CASCADE,

    CONSTRAINT fk_tsq_selected_answer
        FOREIGN KEY (selected_answer_id)
            REFERENCES answers(id)
            ON DELETE SET NULL
);

