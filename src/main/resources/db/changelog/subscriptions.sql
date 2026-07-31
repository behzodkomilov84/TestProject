--liquibase formatted sql

--changeset behzod:31
CREATE TABLE subscriptions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    amount DECIMAL(12,2) NOT NULL,
    source VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    start_date DATETIME NULL,
    end_date DATETIME NULL,
    confirmed_by BIGINT NULL,
    note VARCHAR(500) NULL,
    created_at DATETIME NOT NULL,
    CONSTRAINT fk_subscription_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_subscription_confirmed_by FOREIGN KEY (confirmed_by) REFERENCES users(id)
);

--changeset behzod:32
CREATE INDEX idx_subscription_status_end_date ON subscriptions (status, end_date);
