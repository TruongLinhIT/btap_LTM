package database;

import java.sql.Timestamp;

/**
 * Tài khoản người dùng của Mail Server (địa chỉ: username@domain).
 */
public class UserRecord {
    public int id;
    public String username;
    public String fullName;
    public boolean active;
    public Timestamp createdAt;
    public int inboxCount;
    public int unreadCount;
}
