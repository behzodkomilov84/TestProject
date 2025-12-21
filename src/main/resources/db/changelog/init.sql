--liquibase formatted sql

--changeset behzod:1
create table answers
(
    is_true     bit,
    id          bigint not null auto_increment,
    question_id bigint not null,
    answer_text varchar(255),
    primary key (id)
);

create table science
(
    id   bigint not null auto_increment,
    name varchar(255),
    primary key (id)
);

create table questions
(
    id            bigint       not null auto_increment,
    topic_id      bigint       not null,
    question_text varchar(255) not null,
    primary key (id)
);

create table topics
(
    id         bigint not null auto_increment,
    science_id bigint not null,
    name       varchar(255),
    primary key (id)
);