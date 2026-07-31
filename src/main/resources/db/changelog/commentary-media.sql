--liquibase formatted sql

--changeset behzod:29
ALTER TABLE answers ADD COLUMN commentary_image_url VARCHAR(500) NULL;

--changeset behzod:30
ALTER TABLE answers ADD COLUMN commentary_video_url VARCHAR(500) NULL;
