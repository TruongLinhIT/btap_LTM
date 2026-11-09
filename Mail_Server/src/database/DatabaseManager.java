package database;

import common.MimeUtil;
import common.ParsedMail;
import config.MailConfig;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.*;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Lớp truy cập MySQL (container ltm-mysql) cho Mail Server.
 * Database: mail_db – các bảng: users, emails, mail_logs, gmail_fetched.
 */
public class DatabaseManager {
    private static DatabaseManager instance;
    private final MailConfig config = MailConfig.getInstance();
    private final ExecutorService logExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "db-log-writer");
        t.setDaemon(true);
        return t;
    });

    public static synchronized DatabaseManager getInstance() {
        if (instance == null) instance = new DatabaseManager();
        return instance;
    }

    private DatabaseManager() {}

    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(config.getDbJdbcUrl(), config.getDbUser(), config.getDbPass());
    }

    public boolean testConnection() {
        try (Connection c = getConnection()) {
            return c.isValid(2);
        } catch (Exception e) {
            return false;
        }
    }

    /** Tạo database mail_db (nếu chưa có), các bảng và dữ liệu mẫu. */
    public void init() throws SQLException {
        try (Connection c = DriverManager.getConnection(config.getServerJdbcUrl(), config.getDbUser(), config.getDbPass());
             Statement st = c.createStatement()) {
            st.execute("CREATE DATABASE IF NOT EXISTS `" + config.getDbName()
                    + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        }
        try (Connection c = getConnection(); Statement st = c.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS users (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY," +
                    "username VARCHAR(50) NOT NULL UNIQUE," +
                    "password_hash VARCHAR(128) NOT NULL," +
                    "salt VARCHAR(64) NOT NULL," +
                    "full_name VARCHAR(100) NOT NULL DEFAULT ''," +
                    "is_active TINYINT(1) NOT NULL DEFAULT 1," +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");
            st.execute("CREATE TABLE IF NOT EXISTS emails (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                    "owner VARCHAR(50) NOT NULL," +
                    "folder VARCHAR(10) NOT NULL," +
                    "message_id VARCHAR(255)," +
                    "sender VARCHAR(320) NOT NULL," +
                    "recipients TEXT," +
                    "subject VARCHAR(998)," +
                    "body_text MEDIUMTEXT," +
                    "raw_message LONGTEXT NOT NULL," +
                    "size_bytes INT NOT NULL DEFAULT 0," +
                    "has_attachment TINYINT(1) NOT NULL DEFAULT 0," +
                    "is_read TINYINT(1) NOT NULL DEFAULT 0," +
                    "source VARCHAR(10) NOT NULL DEFAULT 'LOCAL'," +
                    "status VARCHAR(20) NOT NULL DEFAULT 'DELIVERED'," +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                    "INDEX idx_owner_folder (owner, folder)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");
            st.execute("CREATE TABLE IF NOT EXISTS mail_logs (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                    "protocol VARCHAR(10) NOT NULL," +
                    "client_ip VARCHAR(64)," +
                    "username VARCHAR(50)," +
                    "command VARCHAR(100)," +
                    "status VARCHAR(20)," +
                    "message VARCHAR(1000)," +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");
            st.execute("CREATE TABLE IF NOT EXISTS gmail_fetched (" +
                    "uid VARCHAR(255) PRIMARY KEY," +
                    "fetched_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");
            seed(c);
        }
    }

    private void seed(Connection c) throws SQLException {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM users")) {
            if (rs.next() && rs.getInt(1) > 0) return;
        }
        addUser("admin", "admin123", "Quản trị viên");
        addUser("alice", "123456", "Alice Nguyễn");
        addUser("bob", "123456", "Bob Trần");
        String domain = config.getDomain();
        for (String u : new String[]{"admin", "alice", "bob"}) {
            String raw = MimeUtil.buildMessage("LTM Mail Server", "admin@" + domain, List.of(u + "@" + domain), null,
                    "Chào mừng bạn đến với LTM Mail!",
                    "Xin chào " + u + ",\n\nTài khoản " + u + "@" + domain + " đã sẵn sàng.\n"
                            + "- Gửi thư nội bộ: nhập địa chỉ dạng ten@" + domain + "\n"
                            + "- Gửi thư ra Gmail: nhập địa chỉ @gmail.com (server sẽ relay qua smtp.gmail.com)\n\n"
                            + "Thân mến,\nLTM Mail Server", null, null, domain);
            saveIncoming(u, "INBOX", "admin@" + domain, u + "@" + domain, raw, "LOCAL", "DELIVERED");
        }
    }

    // =====================================================================
    //  USERS
    // =====================================================================

    public synchronized boolean addUser(String username, String password, String fullName) throws SQLException {
        String salt = randomSalt();
        String sql = "INSERT INTO users(username, password_hash, salt, full_name) VALUES (?,?,?,?)";
        try (Connection c = getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username.toLowerCase());
            ps.setString(2, hash(password, salt));
            ps.setString(3, salt);
            ps.setString(4, fullName == null ? "" : fullName);
            return ps.executeUpdate() > 0;
        }
    }

    public boolean authenticate(String username, String password) {
        String sql = "SELECT password_hash, salt FROM users WHERE username=? AND is_active=1";
        try (Connection c = getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username.toLowerCase());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getString(1).equals(hash(password, rs.getString(2)));
            }
        } catch (SQLException e) {
            System.err.println("authenticate: " + e.getMessage());
            return false;
        }
    }

    public boolean userExists(String username) {
        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT 1 FROM users WHERE username=? AND is_active=1")) {
            ps.setString(1, username.toLowerCase());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            return false;
        }
    }

    public String getFullName(String username) {
        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT full_name FROM users WHERE username=?")) {
            ps.setString(1, username.toLowerCase());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : "";
            }
        } catch (SQLException e) {
            return "";
        }
    }

    public List<UserRecord> listUsers() throws SQLException {
        List<UserRecord> list = new ArrayList<>();
        String sql = "SELECT u.id, u.username, u.full_name, u.is_active, u.created_at," +
                " (SELECT COUNT(*) FROM emails e WHERE e.owner=u.username AND e.folder='INBOX')," +
                " (SELECT COUNT(*) FROM emails e WHERE e.owner=u.username AND e.folder='INBOX' AND e.is_read=0)" +
                " FROM users u ORDER BY u.id";
        try (Connection c = getConnection(); Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                UserRecord u = new UserRecord();
                u.id = rs.getInt(1);
                u.username = rs.getString(2);
                u.fullName = rs.getString(3);
                u.active = rs.getBoolean(4);
                u.createdAt = rs.getTimestamp(5);
                u.inboxCount = rs.getInt(6);
                u.unreadCount = rs.getInt(7);
                list.add(u);
            }
        }
        return list;
    }

    public void deleteUser(String username) throws SQLException {
        try (Connection c = getConnection()) {
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM emails WHERE owner=?")) {
                ps.setString(1, username);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM users WHERE username=?")) {
                ps.setString(1, username);
                ps.executeUpdate();
            }
        }
    }

    public void changePassword(String username, String newPass) throws SQLException {
        String salt = randomSalt();
        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement("UPDATE users SET password_hash=?, salt=? WHERE username=?")) {
            ps.setString(1, hash(newPass, salt));
            ps.setString(2, salt);
            ps.setString(3, username);
            ps.executeUpdate();
        }
    }

    public void setActive(String username, boolean active) throws SQLException {
        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement("UPDATE users SET is_active=? WHERE username=?")) {
            ps.setBoolean(1, active);
            ps.setString(2, username);
            ps.executeUpdate();
        }
    }

    // =====================================================================
    //  EMAILS
    // =====================================================================

    /** Lưu một thư vào hộp thư của owner. Tự phân tích subject/body từ raw để dễ xem trên phpMyAdmin. */
    public long saveIncoming(String owner, String folder, String sender, String recipients, String raw,
                             String source, String status) throws SQLException {
        ParsedMail pm = MimeUtil.parse(raw);
        String sql = "INSERT INTO emails(owner, folder, message_id, sender, recipients, subject, body_text, raw_message," +
                " size_bytes, has_attachment, is_read, source, status) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try (Connection c = getConnection(); PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, owner.toLowerCase());
            ps.setString(2, folder);
            ps.setString(3, cut(pm.getMessageId(), 255));
            ps.setString(4, cut(sender, 320));
            ps.setString(5, recipients);
            ps.setString(6, cut(pm.getSubject(), 990));
            ps.setString(7, cut(pm.getDisplayText(), 5_000_000));
            ps.setString(8, raw);
            ps.setInt(9, raw.getBytes(StandardCharsets.ISO_8859_1).length);
            ps.setBoolean(10, !pm.getAttachments().isEmpty());
            ps.setBoolean(11, folder.equals("SENT"));
            ps.setString(12, source);
            ps.setString(13, status);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                return rs.next() ? rs.getLong(1) : -1;
            }
        }
    }

    /** Danh sách thư (không gồm raw) của một hộp thư, sắp xếp cũ -> mới (dùng cho POP3). */
    public List<MailRecord> listMailbox(String owner, String folder) throws SQLException {
        List<MailRecord> list = new ArrayList<>();
        String sql = "SELECT id, size_bytes, is_read FROM emails WHERE owner=? AND folder=? ORDER BY id";
        try (Connection c = getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, owner.toLowerCase());
            ps.setString(2, folder);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    MailRecord m = new MailRecord();
                    m.id = rs.getLong(1);
                    m.sizeBytes = rs.getInt(2);
                    m.read = rs.getBoolean(3);
                    m.owner = owner;
                    m.folder = folder;
                    list.add(m);
                }
            }
        }
        return list;
    }

    public String getRawMessage(long id) throws SQLException {
        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT raw_message FROM emails WHERE id=?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    public void markRead(long id) {
        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement("UPDATE emails SET is_read=1 WHERE id=?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("markRead: " + e.getMessage());
        }
    }

    /** INBOX/SENT -> TRASH ; TRASH -> xóa vĩnh viễn. */
    public void deleteOrTrash(List<Long> ids, String folder) throws SQLException {
        if (ids.isEmpty()) return;
        String sql = folder.equals("TRASH") ? "DELETE FROM emails WHERE id=?" : "UPDATE emails SET folder='TRASH' WHERE id=?";
        try (Connection c = getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            for (Long id : ids) {
                ps.setLong(1, id);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    /** Dùng cho giao diện server: liệt kê thư mới nhất, có thể lọc theo từ khóa. */
    public List<MailRecord> listAllEmails(String keyword, int limit) throws SQLException {
        List<MailRecord> list = new ArrayList<>();
        String sql = "SELECT id, owner, folder, sender, recipients, subject, size_bytes, has_attachment, is_read, source, status, created_at" +
                " FROM emails WHERE (? = '' OR owner LIKE ? OR sender LIKE ? OR recipients LIKE ? OR subject LIKE ?)" +
                " ORDER BY id DESC LIMIT ?";
        try (Connection c = getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            String k = keyword == null ? "" : keyword.trim();
            String like = "%" + k + "%";
            ps.setString(1, k);
            for (int i = 2; i <= 5; i++) ps.setString(i, like);
            ps.setInt(6, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    MailRecord m = new MailRecord();
                    m.id = rs.getLong(1);
                    m.owner = rs.getString(2);
                    m.folder = rs.getString(3);
                    m.sender = rs.getString(4);
                    m.recipients = rs.getString(5);
                    m.subject = rs.getString(6);
                    m.sizeBytes = rs.getInt(7);
                    m.hasAttachment = rs.getBoolean(8);
                    m.read = rs.getBoolean(9);
                    m.source = rs.getString(10);
                    m.status = rs.getString(11);
                    m.createdAt = rs.getTimestamp(12);
                    list.add(m);
                }
            }
        }
        return list;
    }

    public void deleteEmail(long id) throws SQLException {
        try (Connection c = getConnection(); PreparedStatement ps = c.prepareStatement("DELETE FROM emails WHERE id=?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        }
    }

    public int[] stats() {
        int[] r = new int[4]; // users, emails, gmail-relayed, gmail-received
        try (Connection c = getConnection(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT (SELECT COUNT(*) FROM users), (SELECT COUNT(*) FROM emails)," +
                     " (SELECT COUNT(*) FROM emails WHERE status='RELAYED'), (SELECT COUNT(*) FROM emails WHERE source='GMAIL')")) {
            if (rs.next()) for (int i = 0; i < 4; i++) r[i] = rs.getInt(i + 1);
        } catch (SQLException ignored) {
        }
        return r;
    }

    // =====================================================================
    //  GMAIL FETCH (UIDL đã lấy)
    // =====================================================================

    public boolean isGmailFetched(String uid) {
        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT 1 FROM gmail_fetched WHERE uid=?")) {
            ps.setString(1, uid);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            return true;
        }
    }

    public void markGmailFetched(String uid) {
        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement("INSERT IGNORE INTO gmail_fetched(uid) VALUES (?)")) {
            ps.setString(1, uid);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("markGmailFetched: " + e.getMessage());
        }
    }

    public int countGmailFetched() {
        try (Connection c = getConnection(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM gmail_fetched")) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            return 0;
        }
    }

    // =====================================================================
    //  LOGS
    // =====================================================================

    public void logAsync(String protocol, String ip, String user, String command, String status, String message) {
        logExecutor.submit(() -> {
            String sql = "INSERT INTO mail_logs(protocol, client_ip, username, command, status, message) VALUES (?,?,?,?,?,?)";
            try (Connection c = getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, protocol);
                ps.setString(2, ip);
                ps.setString(3, user);
                ps.setString(4, cut(command, 100));
                ps.setString(5, status);
                ps.setString(6, cut(message, 1000));
                ps.executeUpdate();
            } catch (SQLException ignored) {
            }
        });
    }

    public List<String[]> listLogs(int limit) throws SQLException {
        List<String[]> list = new ArrayList<>();
        String sql = "SELECT created_at, protocol, client_ip, username, command, status, message FROM mail_logs ORDER BY id DESC LIMIT ?";
        try (Connection c = getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String[] row = new String[7];
                    for (int i = 0; i < 7; i++) row[i] = rs.getString(i + 1);
                    list.add(row);
                }
            }
        }
        return list;
    }

    // =====================================================================
    private static String cut(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }

    private static String randomSalt() {
        byte[] b = new byte[16];
        new SecureRandom().nextBytes(b);
        return HexFormat.of().formatHex(b);
    }

    public static String hash(String password, String salt) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(salt.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(md.digest(password.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
