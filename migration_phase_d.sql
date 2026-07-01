-- ============================================================
-- Phase D migration: thu hoi tin nhan + gui file/anh.
-- Chay SAU schema.sql va SAU migration_phase_c.sql, theo dung thu tu:
--   schema.sql  →  migration_phase_c.sql  →  migration_phase_d.sql
-- ============================================================

-- 1. Cot 'recalled': danh dau tin nhan da bi nguoi gui thu hoi (tinh nang 8).
--    0 = binh thuong, 1 = da thu hoi (UI se hien "Tin nhan da duoc thu hoi").
ALTER TABLE messages ADD COLUMN recalled TINYINT(1) NOT NULL DEFAULT 0;

-- 2. Cot 'type': phan biet tin nhan van ban (TEXT) va tin nhan file/anh (FILE).
ALTER TABLE messages ADD COLUMN type VARCHAR(10) NOT NULL DEFAULT 'TEXT';

-- 3. Cot 'file_name': ten file goc khi type = 'FILE' (NULL cho tin nhan TEXT).
--    Noi dung file (base64) van luu trong cot 'content' co san, khong can cot moi.
ALTER TABLE messages ADD COLUMN file_name VARCHAR(255) DEFAULT NULL;
