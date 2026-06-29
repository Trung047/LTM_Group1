-- ==========================================
--  LTM Group 1 — Database Schema
--  Chạy file này để tạo database từ đầu.
--  Lệnh: mysql -u root -p < Database.sql
-- ==========================================

CREATE DATABASE IF NOT EXISTS ltm_db
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE ltm_db;

-- ==========================================
--  Bảng users — tài khoản đăng nhập
-- ==========================================
CREATE TABLE IF NOT EXISTS users (
    id         INT AUTO_INCREMENT PRIMARY KEY,
    username   VARCHAR(64)  NOT NULL UNIQUE,
    password   VARCHAR(255) NOT NULL,           -- plain text (project học tập)
    created_at DATETIME     DEFAULT NOW()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ==========================================
--  Bảng rooms — danh sách phòng chat nhóm
-- ==========================================
CREATE TABLE IF NOT EXISTS rooms (
    id         INT AUTO_INCREMENT PRIMARY KEY,
    room_name  VARCHAR(100) NOT NULL UNIQUE,
    created_at DATETIME     DEFAULT NOW()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ==========================================
--  Bảng messages — lịch sử tin nhắn
--  receiver = 'GROUP'   → tin nhắn nhóm
--  receiver = username  → tin nhắn riêng
-- ==========================================
CREATE TABLE IF NOT EXISTS messages (
    id         INT AUTO_INCREMENT PRIMARY KEY,
    sender     VARCHAR(64)  NOT NULL,
    receiver   VARCHAR(64)  NOT NULL,
    content    TEXT         NOT NULL,
    created_at DATETIME     DEFAULT NOW(),

    -- Index để query lịch sử nhanh hơn
    INDEX idx_group    (receiver, created_at),
    INDEX idx_private  (sender,   receiver, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ==========================================
--  Dữ liệu mẫu
-- ==========================================

-- Tài khoản test (mật khẩu = 123)
INSERT IGNORE INTO users (username, password) VALUES
    ('admin',   '123'),
    ('kiet',    '123'),
    ('an',      '123'),
    ('alice',   '123'),
    ('bob',     '123');

-- Phòng chat mặc định
INSERT IGNORE INTO rooms (room_name) VALUES
    ('General'),
    ('Java'),
    ('Study');
