-- =====================================================================================
-- Farmer Market Linkage System with Integrated Decision Support
-- MySQL / MariaDB (XAMPP) schema  –  database: fmls_db
--
-- NOTE: You do NOT have to run this script. The Spring Boot backend creates the database
-- and all tables automatically (spring.jpa.hibernate.ddl-auto=update) and loads the
-- simulated dataset on first start. This script documents the schema and can be imported
-- through phpMyAdmin if you prefer to create the tables yourself.
--
-- Tables follow the database design (ER diagram / dbdiagram.io):
--   farmers, buyers, admins, products, market_prices, price_predictions,
--   demand_forecasts, matches, transactions
-- plus messages (buyer–farmer communication) and system_logs (admin monitoring).
-- =====================================================================================

CREATE DATABASE IF NOT EXISTS fmls_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE fmls_db;

CREATE TABLE IF NOT EXISTS farmers (
    farmer_id      BIGINT       NOT NULL AUTO_INCREMENT,
    name           VARCHAR(100) NOT NULL,
    phone          VARCHAR(20),
    email          VARCHAR(120),
    password_hash  VARCHAR(255) NOT NULL,
    location       VARCHAR(100),
    status         ENUM('PENDING','ACTIVE','SUSPENDED','BANNED') NOT NULL,
    created_at     DATETIME(6)  NOT NULL,
    last_login_at  DATETIME(6),
    PRIMARY KEY (farmer_id),
    CONSTRAINT uk_farmers_phone UNIQUE (phone),
    CONSTRAINT uk_farmers_email UNIQUE (email)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS buyers (
    buyer_id       BIGINT       NOT NULL AUTO_INCREMENT,
    name           VARCHAR(100) NOT NULL,
    phone          VARCHAR(20),
    email          VARCHAR(120),
    password_hash  VARCHAR(255) NOT NULL,
    location       VARCHAR(100),
    preferences    VARCHAR(255),
    status         ENUM('PENDING','ACTIVE','SUSPENDED','BANNED') NOT NULL,
    created_at     DATETIME(6)  NOT NULL,
    last_login_at  DATETIME(6),
    PRIMARY KEY (buyer_id),
    CONSTRAINT uk_buyers_phone UNIQUE (phone),
    CONSTRAINT uk_buyers_email UNIQUE (email)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS admins (
    admin_id       BIGINT       NOT NULL AUTO_INCREMENT,
    name           VARCHAR(100) NOT NULL,
    phone          VARCHAR(20),
    email          VARCHAR(120),
    password_hash  VARCHAR(255) NOT NULL,
    location       VARCHAR(100),
    status         ENUM('PENDING','ACTIVE','SUSPENDED','BANNED') NOT NULL,
    created_at     DATETIME(6)  NOT NULL,
    last_login_at  DATETIME(6),
    PRIMARY KEY (admin_id),
    CONSTRAINT uk_admins_email UNIQUE (email)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS products (
    product_id        BIGINT        NOT NULL AUTO_INCREMENT,
    farmer_id         BIGINT        NOT NULL,
    crop_type         VARCHAR(50)   NOT NULL,
    quantity          DECIMAL(10,2),
    unit              VARCHAR(20),
    harvest_date      DATE,
    status            ENUM('DRAFT','AVAILABLE','SOLD','REMOVED') NOT NULL,
    price_per_unit    DECIMAL(10,2),
    recommended_price DECIMAL(10,2),
    location          VARCHAR(100),
    description       VARCHAR(500),
    created_at        DATETIME(6)   NOT NULL,
    updated_at        DATETIME(6),
    PRIMARY KEY (product_id),
    KEY idx_products_status_crop (status, crop_type),
    KEY idx_products_farmer (farmer_id),
    CONSTRAINT fk_products_farmer FOREIGN KEY (farmer_id) REFERENCES farmers (farmer_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS market_prices (
    price_id        BIGINT        NOT NULL AUTO_INCREMENT,
    crop_type       VARCHAR(50)   NOT NULL,
    market_location VARCHAR(100),
    price           DECIMAL(10,2) NOT NULL,
    recorded_date   DATE          NOT NULL,
    source          VARCHAR(50),
    PRIMARY KEY (price_id),
    KEY idx_market_crop_loc_date (crop_type, market_location, recorded_date),
    KEY idx_market_date (recorded_date)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS price_predictions (
    prediction_id     BIGINT        NOT NULL AUTO_INCREMENT,
    product_id        BIGINT        NOT NULL,
    predicted_price   DECIMAL(10,2),
    recommended_price DECIMAL(10,2),
    confidence        DECIMAL(4,3),
    confidence_label  VARCHAR(10),
    lower_bound       DECIMAL(10,2),
    upper_bound       DECIMAL(10,2),
    demand_trend      VARCHAR(10),
    based_on_records  INT,
    model_version     VARCHAR(80),
    details_json      LONGTEXT,
    created_at        DATETIME(6)   NOT NULL,
    PRIMARY KEY (prediction_id),
    CONSTRAINT fk_predictions_product FOREIGN KEY (product_id) REFERENCES products (product_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS demand_forecasts (
    forecast_id        BIGINT       NOT NULL AUTO_INCREMENT,
    crop_type          VARCHAR(50)  NOT NULL,
    forecast_period    VARCHAR(60),
    trend              ENUM('RISING','STABLE','FALLING'),
    predicted_quantity DECIMAL(12,2),
    change_pct         DECIMAL(10,2),
    model              VARCHAR(40),
    generated_at       DATETIME(6)  NOT NULL,
    PRIMARY KEY (forecast_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS matches (
    match_id    BIGINT       NOT NULL AUTO_INCREMENT,
    product_id  BIGINT       NOT NULL,
    buyer_id    BIGINT       NOT NULL,
    match_score DECIMAL(5,4),
    reasons     VARCHAR(500),
    created_at  DATETIME(6)  NOT NULL,
    PRIMARY KEY (match_id),
    CONSTRAINT fk_matches_product FOREIGN KEY (product_id) REFERENCES products (product_id),
    CONSTRAINT fk_matches_buyer FOREIGN KEY (buyer_id) REFERENCES buyers (buyer_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS transactions (
    transaction_id   BIGINT        NOT NULL AUTO_INCREMENT,
    product_id       BIGINT        NOT NULL,
    farmer_id        BIGINT        NOT NULL,
    buyer_id         BIGINT        NOT NULL,
    agreed_price     DECIMAL(10,2) NOT NULL,
    quantity         DECIMAL(10,2),
    status           ENUM('PENDING','CONFIRMED','COMPLETED','CANCELLED') NOT NULL,
    transaction_date DATETIME(6)   NOT NULL,
    updated_at       DATETIME(6),
    PRIMARY KEY (transaction_id),
    KEY idx_transactions_date (transaction_date),
    CONSTRAINT fk_transactions_product FOREIGN KEY (product_id) REFERENCES products (product_id),
    CONSTRAINT fk_transactions_farmer FOREIGN KEY (farmer_id) REFERENCES farmers (farmer_id),
    CONSTRAINT fk_transactions_buyer FOREIGN KEY (buyer_id) REFERENCES buyers (buyer_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS messages (
    message_id  BIGINT        NOT NULL AUTO_INCREMENT,
    farmer_id   BIGINT        NOT NULL,
    buyer_id    BIGINT        NOT NULL,
    product_id  BIGINT,
    sender_role ENUM('FARMER','BUYER','ADMIN') NOT NULL,
    content     VARCHAR(1000) NOT NULL,
    is_read     BIT(1)        NOT NULL,
    created_at  DATETIME(6)   NOT NULL,
    PRIMARY KEY (message_id),
    KEY idx_messages_pair (farmer_id, buyer_id),
    CONSTRAINT fk_messages_farmer FOREIGN KEY (farmer_id) REFERENCES farmers (farmer_id),
    CONSTRAINT fk_messages_buyer FOREIGN KEY (buyer_id) REFERENCES buyers (buyer_id),
    CONSTRAINT fk_messages_product FOREIGN KEY (product_id) REFERENCES products (product_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS system_logs (
    log_id     BIGINT       NOT NULL AUTO_INCREMENT,
    actor_role ENUM('FARMER','BUYER','ADMIN'),
    actor_id   BIGINT,
    actor_name VARCHAR(100),
    action     VARCHAR(60)  NOT NULL,
    details    VARCHAR(500),
    created_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (log_id),
    KEY idx_logs_created (created_at)
) ENGINE=InnoDB;
