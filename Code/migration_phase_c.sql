-- ============================================================
-- Phase C migration: persist Rooms + Room Members to MySQL,
-- and tag each group message with the room it belongs to.
-- Run this ONCE against the existing ltm_db database.
-- ============================================================

-- 1. Bang rooms: danh sach phong chat (thay the cho Set<String> in-memory)
CREATE TABLE IF NOT EXISTS rooms (
    id         INT AUTO_INCREMENT PRIMARY KEY,
    name       VARCHAR(50) NOT NULL UNIQUE,
    creator    VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 2. Bang room_members: ai dang/da o phong nao
CREATE TABLE IF NOT EXISTS room_members (
    id         INT AUTO_INCREMENT PRIMARY KEY,
    room_name  VARCHAR(50) NOT NULL,
    username   VARCHAR(50) NOT NULL,
    joined_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uniq_room_user (room_name, username)
);

-- 3. Phong mac dinh "General" (khop voi UserManager.rooms mac dinh)
INSERT IGNORE INTO rooms (name, creator) VALUES ('General', NULL);

-- 4. Them cot room vao bang messages de tin nhan nhom gan voi dung phong.
--    Tin nhan rieng (receiver = username) se co room = NULL.
--    Neu cot da ton tai (chay lai script), lenh nay se loi - bo qua loi do duoc.
--    LUU Y: MySQL "instant ADD COLUMN" tra ve gia tri DEFAULT cho TAT CA cac dong
--    da co san khi duoc query - bao gom ca tin nhan rieng (receiver != 'GROUP'),
--    khong chi tin nhan nhom. Vi vay buoc 5b ben duoi can xoa lai room cho tin rieng.
ALTER TABLE messages ADD COLUMN room VARCHAR(50) DEFAULT 'General';

-- 5a. Tin nhan GROUP cu (truoc Phase C) deu thuoc phong "General" (giu nguyen, dung muc dich)
UPDATE messages SET room = 'General' WHERE receiver = 'GROUP' AND room IS NULL;

-- 5b. Tin nhan RIENG cu vo tinh bi gan room='General' do DEFAULT cua ALTER TABLE o buoc 4.
--     Dat lai thanh NULL de dung voi thiet ke (tin rieng khong thuoc phong nao).
UPDATE messages SET room = NULL WHERE receiver != 'GROUP';
