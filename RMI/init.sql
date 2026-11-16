-- ==========================================================
-- CSDL RMI BANKING
-- ==========================================================
CREATE DATABASE IF NOT EXISTS rmi_db;
USE rmi_db;

CREATE TABLE IF NOT EXISTS accounts (
    username VARCHAR(50) PRIMARY KEY,
    password VARCHAR(255) NOT NULL,
    balance DOUBLE NOT NULL DEFAULT 0,
    is_locked BOOLEAN NOT NULL DEFAULT FALSE,
    full_name VARCHAR(100) NOT NULL DEFAULT '',
    email VARCHAR(100) NOT NULL DEFAULT '',
    phone VARCHAR(20) NOT NULL DEFAULT ''
);

INSERT INTO accounts (username, password, balance, is_locked, full_name, email, phone) VALUES
('user1', '123', 5000000.0, FALSE, 'Nguyễn Văn A', 'user1@bank.vn', '0901000001'),
('user2', '123', 3000000.0, FALSE, 'Trần Thị B', 'user2@bank.vn', '0901000002'),
('user3', '123', 10000000.0, FALSE, 'Lê Văn C', 'user3@bank.vn', '0901000003')
ON DUPLICATE KEY UPDATE balance = VALUES(balance), is_locked = FALSE;
