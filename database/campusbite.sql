-- CampusBite database setup
-- MySQL 8.0+
-- Creates the database and tables required by the current Spring Boot project.
-- Does not create MySQL users or set application passwords.
-- Configure CANTEEN_DB_USERNAME and CANTEEN_DB_PASSWORD separately.

CREATE DATABASE IF NOT EXISTS campus_bite
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE campus_bite;

CREATE TABLE IF NOT EXISTS users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    full_name VARCHAR(100) NOT NULL,
    email VARCHAR(255) NOT NULL,
    roll_number VARCHAR(50) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_users_email (email),
    UNIQUE KEY uq_users_roll_number (roll_number)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS api_tokens (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    token_hash CHAR(64) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    revoked_at TIMESTAMP NULL DEFAULT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_api_tokens_hash (token_hash),
    KEY idx_api_tokens_user_id (user_id),
    KEY idx_api_tokens_expiry (expires_at),
    CONSTRAINT fk_api_tokens_user
        FOREIGN KEY (user_id) REFERENCES users (id)
        ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS password_reset_tokens (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    token_hash CHAR(64) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    used_at TIMESTAMP NULL DEFAULT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_password_reset_token_hash (token_hash),
    KEY idx_password_reset_user (user_id),
    KEY idx_password_reset_expiry (expires_at),
    CONSTRAINT fk_password_reset_user
        FOREIGN KEY (user_id) REFERENCES users (id)
        ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS items (
    id VARCHAR(60) NOT NULL,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(500) NULL,
    category VARCHAR(50) NOT NULL,
    price DECIMAL(10,2) NOT NULL,
    image_link VARCHAR(2048) NULL,
    available BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_items_category (category),
    KEY idx_items_available (available),
    CONSTRAINT chk_items_price CHECK (price >= 0)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS payments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    transaction_id VARCHAR(80) NOT NULL,
    user_id BIGINT NOT NULL,
    amount DECIMAL(10,2) NOT NULL,
    status ENUM('PENDING', 'PAID', 'FAILED') NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    paid_at TIMESTAMP NULL DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_payments_transaction_id (transaction_id),
    KEY idx_payments_user (user_id),
    KEY idx_payments_status (status),
    CONSTRAINT fk_payments_user
        FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT chk_payment_amount CHECK (amount > 0)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS orders (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    payment_id BIGINT NULL DEFAULT NULL,
    total_amount DECIMAL(10,2) NOT NULL,
    status ENUM('PLACED', 'PREPARING', 'READY', 'COMPLETED', 'CANCELLED')
        NOT NULL DEFAULT 'PLACED',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    transaction_id VARCHAR(80) NULL DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_orders_payment_id (payment_id),
    UNIQUE KEY uq_orders_transaction_id (transaction_id),
    KEY idx_orders_user_date (user_id, created_at),
    CONSTRAINT fk_orders_payment
        FOREIGN KEY (payment_id) REFERENCES payments (id),
    CONSTRAINT fk_orders_transaction
        FOREIGN KEY (transaction_id) REFERENCES payments (transaction_id),
    CONSTRAINT fk_orders_user
        FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS order_items (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    product_id VARCHAR(60) NOT NULL,
    product_name VARCHAR(120) NOT NULL,
    unit_price DECIMAL(10,2) NOT NULL,
    quantity INT NOT NULL,
    PRIMARY KEY (id),
    KEY idx_order_items_order (order_id),
    CONSTRAINT fk_order_items_order
        FOREIGN KEY (order_id) REFERENCES orders (id)
        ON DELETE CASCADE,
    CONSTRAINT chk_order_item_price CHECK (unit_price >= 0),
    CONSTRAINT chk_order_item_quantity CHECK (quantity > 0)
) ENGINE=InnoDB;

-- Optional: insert actual menu products through the admin panel.
-- Example:
-- INSERT INTO items (id, name, description, category, price, image_link, available)
-- VALUES ('sample-sandwich', 'Veg Sandwich', 'Fresh vegetable sandwich',
--         'Snacks', 80.00, NULL, TRUE);
