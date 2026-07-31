--liquibase formatted sql

--changeset behzod:27
ALTER TABLE questions ADD COLUMN image_url VARCHAR(500) NULL;

--changeset behzod:28
ALTER TABLE answers ADD COLUMN image_url VARCHAR(500) NULL;
