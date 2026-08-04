--liquibase formatted sql

--changeset behzod:40
CREATE TABLE role_audit_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    target_user_id BIGINT NOT NULL,
    changed_by_id BIGINT NULL,
    role_name VARCHAR(50) NOT NULL,
    action VARCHAR(20) NOT NULL,
    source VARCHAR(20) NOT NULL,
    created_at DATETIME NOT NULL,
    CONSTRAINT fk_role_audit_target_user FOREIGN KEY (target_user_id) REFERENCES users(id),
    CONSTRAINT fk_role_audit_changed_by FOREIGN KEY (changed_by_id) REFERENCES users(id)
);

--changeset behzod:41
CREATE INDEX idx_role_audit_target_user ON role_audit_logs (target_user_id, created_at);
