-- ==========================================
--  NetChat — Database Schema
-- ==========================================

CREATE DATABASE IF NOT EXISTS ltm_db
    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE ltm_db;

-- Bảng người dùng
CREATE TABLE IF NOT EXISTS users (
    id         INT AUTO_INCREMENT PRIMARY KEY,
    username   VARCHAR(64)  NOT NULL UNIQUE,
    password   VARCHAR(255) NOT NULL,
    created_at DATETIME     DEFAULT NOW()
);

-- Bảng tin nhắn
CREATE TABLE IF NOT EXISTS messages (
    id         INT AUTO_INCREMENT PRIMARY KEY,
    sender     VARCHAR(64)  NOT NULL,
    receiver   VARCHAR(64)  NOT NULL,   -- 'GROUP' hoặc username
    content    TEXT         NOT NULL,
    created_at DATETIME     DEFAULT NOW()
);

-- Tài khoản mẫu để test (mật khẩu = 123456)
INSERT IGNORE INTO users (username, password) VALUES
    ('alice',   '123456'),
    ('bob',     '123456'),
    ('charlie', '123456');
