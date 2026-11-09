-- =====================================================================
-- KHOI TAO CO SO DU LIEU CHO MAIL SERVER (LTM - LAP TRINH MANG)
-- Ket noi toi container MySQL 'ltm-mysql' (Cong 3308, root / root123)
-- Hoac chay truc tiep trong phpMyAdmin: http://localhost:8085
-- =====================================================================

CREATE DATABASE IF NOT EXISTS `mail_db` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `mail_db`;

-- 1. BANG NGUOI DUNG (users)
CREATE TABLE IF NOT EXISTS `users` (
    `id` INT AUTO_INCREMENT PRIMARY KEY,
    `username` VARCHAR(50) NOT NULL UNIQUE,
    `password_hash` VARCHAR(128) NOT NULL,
    `salt` VARCHAR(64) NOT NULL,
    `full_name` VARCHAR(100) NOT NULL DEFAULT '',
    `is_active` TINYINT(1) NOT NULL DEFAULT 1,
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. BANG LUU TRU EMAIL (emails)
-- Moi nguoi dung so huu 1 ban ghi cho hop thu cua minh (INBOX, SENT, TRASH)
CREATE TABLE IF NOT EXISTS `emails` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `owner` VARCHAR(50) NOT NULL,
    `folder` VARCHAR(10) NOT NULL,
    `message_id` VARCHAR(255),
    `sender` VARCHAR(320) NOT NULL,
    `recipients` TEXT,
    `subject` VARCHAR(998),
    `body_text` MEDIUMTEXT,
    `raw_message` LONGTEXT NOT NULL,
    `size_bytes` INT NOT NULL DEFAULT 0,
    `has_attachment` TINYINT(1) NOT NULL DEFAULT 0,
    `is_read` TINYINT(1) NOT NULL DEFAULT 0,
    `source` VARCHAR(10) NOT NULL DEFAULT 'LOCAL',
    `status` VARCHAR(20) NOT NULL DEFAULT 'DELIVERED',
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_owner_folder` (`owner`, `folder`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. BANG NHAT KY HOAT DONG SOCKET & HE THONG (mail_logs)
CREATE TABLE IF NOT EXISTS `mail_logs` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `protocol` VARCHAR(10) NOT NULL,
    `client_ip` VARCHAR(64),
    `username` VARCHAR(50),
    `command` VARCHAR(100),
    `status` VARCHAR(20),
    `message` VARCHAR(1000),
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4. BANG GHI NHO CAC THU DA LAY TU GMAIL QUA POP3 (gmail_fetched)
CREATE TABLE IF NOT EXISTS `gmail_fetched` (
    `uid` VARCHAR(255) PRIMARY KEY,
    `fetched_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
