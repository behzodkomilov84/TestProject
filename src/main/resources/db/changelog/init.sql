--liquibase formatted sql

--changeset behzod:1
create table roles
(
    id        bigint not null auto_increment,
    role_name varchar(255),
    primary key (id)
);

INSERT INTO roles (role_name) VALUES
                                  ('ROLE_OWNER'),
                                  ('ROLE_ADMIN'),
                                  ('ROLE_USER');

create table users
(
    id       bigint not null auto_increment,
    username varchar(255),
    password varchar(255),
    role_id  bigint,
#     enabled BOOLEAN NOT NULL DEFAULT TRUE,
    primary key (id),
    foreign key (role_id) references roles (id)
);

create table questions
(
    id            bigint       not null auto_increment,
    topic_id      bigint       not null,
    question_text varchar(255) not null,
    primary key (id)
);

create table answers
(
    id          bigint       not null auto_increment,
    question_id bigint       not null,
    answer_text varchar(255) not null,
    is_true     bit          not null,
    commentary  varchar(700) null,
    primary key (id),
    constraint fk_answers_question
        foreign key (question_id)
            references questions (id)
            on delete cascade
);

create table science
(
    id   bigint       not null auto_increment,
    name varchar(255) NOT NULL UNIQUE,
    primary key (id)
);

create table topics
(
    id         bigint       not null auto_increment,
    science_id bigint       not null,
    name       varchar(255) NOT NULL UNIQUE,
    primary key (id)
);