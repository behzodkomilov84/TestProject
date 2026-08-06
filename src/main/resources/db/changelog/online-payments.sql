--liquibase formatted sql

--changeset behzod:53
CREATE TABLE payment_orders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    amount DECIMAL(12,2) NOT NULL,
    duration_months INT NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at DATETIME NOT NULL,
    paid_at DATETIME NULL,
    CONSTRAINT fk_payment_orders_user FOREIGN KEY (user_id) REFERENCES users(id)
);

--changeset behzod:54
CREATE TABLE payment_transactions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    provider VARCHAR(10) NOT NULL,
    provider_transaction_id VARCHAR(100) NOT NULL,
    amount DECIMAL(12,2) NOT NULL,
    state VARCHAR(10) NOT NULL,
    create_time DATETIME NULL,
    perform_time DATETIME NULL,
    cancel_time DATETIME NULL,
    cancel_reason INT NULL,
    created_at DATETIME NOT NULL,
    CONSTRAINT fk_payment_transactions_order FOREIGN KEY (order_id) REFERENCES payment_orders(id)
);

--changeset behzod:55
-- Bir xil provayderdan bir xil tranzaksiya ID ikki marta kelsa ham
-- (Payme/Click qayta so'rov yuborishi mumkin) — idempotentlik uchun.
CREATE UNIQUE INDEX idx_payment_tx_provider_id ON payment_transactions (provider, provider_transaction_id);

--changeset behzod:56
-- To'lov muvaffaqiyatli bo'lganda yaratilgan Subscription'ga havola —
-- chargeback/qaytarish sodir bo'lsa aynan shu obunani bekor qilish uchun.
ALTER TABLE payment_orders ADD COLUMN subscription_id BIGINT NULL;
