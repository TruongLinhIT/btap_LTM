-- Script khoi tao Database cho FTP Server (LTM)
-- Co the chay trong phpMyAdmin hoac MySQL Console

CREATE DATABASE IF NOT EXISTS ftp_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE ftp_db;

-- 1. Bang nguoi dung (ftp_users)
CREATE TABLE IF NOT EXISTS ftp_users (
    id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(128) NOT NULL,
    salt VARCHAR(64) NOT NULL,
    home_directory VARCHAR(255) NOT NULL,
    can_upload TINYINT(1) NOT NULL DEFAULT 1,
    can_download TINYINT(1) NOT NULL DEFAULT 1,
    can_delete TINYINT(1) NOT NULL DEFAULT 1,
    can_modify TINYINT(1) NOT NULL DEFAULT 1,
    is_active TINYINT(1) NOT NULL DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. Bang lich su hoat dong / audit log (ftp_logs)
CREATE TABLE IF NOT EXISTS ftp_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50),
    client_ip VARCHAR(64),
    command VARCHAR(50) NOT NULL,
    path VARCHAR(500),
    file_size BIGINT DEFAULT 0,
    status VARCHAR(20) NOT NULL,
    message VARCHAR(500),
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. Bang cau hinh he thong (ftp_settings)
CREATE TABLE IF NOT EXISTS ftp_settings (
    config_key VARCHAR(50) PRIMARY KEY,
    config_value VARCHAR(255) NOT NULL,
    description VARCHAR(255)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
