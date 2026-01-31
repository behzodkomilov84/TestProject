create table test_session_questions
(
    id              bigint auto_increment primary key,
    test_session_id bigint not null,
    question_id     bigint not null,
    selected_answer_id bigint null,
    is_correct      BOOLEAN not null,

    foreign key (test_session_id) references test_sessions(id),
    foreign key (question_id) references questions(id),
    foreign key (selected_answer_id) references answers(id)
);
