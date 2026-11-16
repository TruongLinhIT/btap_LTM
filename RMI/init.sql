-- ==========================================================
-- CSDL RMI BANKING
-- ==========================================================
CREATE DATABASE IF NOT EXISTS rmi_db;
USE rmi_db;

CREATE TABLE IF NOT EXISTS accounts (
    username VARCHAR(50) PRIMARY KEY,
    password VARCHAR(255) NOT NULL,
    balance DOUBLE NOT NULL DEFAULT 0,
    is_locked BOOLEAN NOT NULL DEFAULT FALSE
);

INSERT INTO accounts (username, password, balance, is_locked) VALUES
('user1', '123', 5000000.0, FALSE),
('user2', '123', 3000000.0, FALSE),
('user3', '123', 10000000.0, FALSE)
ON DUPLICATE KEY UPDATE balance = VALUES(balance), is_locked = FALSE;
