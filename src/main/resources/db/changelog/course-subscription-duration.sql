--liquibase formatted sql

--changeset behzod:51
ALTER TABLE course_subscriptions ADD COLUMN start_date DATETIME NULL;

--changeset behzod:52
ALTER TABLE course_subscriptions ADD COLUMN end_date DATETIME NULL;
