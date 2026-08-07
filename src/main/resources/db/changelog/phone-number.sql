--liquibase formatted sql

--changeset behzod:57
ALTER TABLE users ADD COLUMN phone_number VARCHAR(20) NULL;
