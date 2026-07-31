--liquibase formatted sql

--changeset behzod:36
ALTER TABLE users ADD COLUMN failed_attempts INT NOT NULL DEFAULT 0;

--changeset behzod:37
ALTER TABLE users ADD COLUMN locked_until DATETIME NULL;
