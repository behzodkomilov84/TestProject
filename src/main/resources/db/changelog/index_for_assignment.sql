--liquibase formatted sql

--changeset yourname:create-index-attempt-assignment
CREATE INDEX idx_attempt_assignment ON assignment_attempts (assignment_id);

--changeset yourname:create-index-assignment-group
CREATE INDEX idx_assignment_group ON assignments (group_id);

--changeset yourname:create-index-assignment-pupil
CREATE INDEX idx_assignment_pupil ON assignments (pupil_id);