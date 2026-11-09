package server;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.*;
import java.util.*;

/** Lớp truy cập MySQL (JDBC). */
public class Database {
    private final String url, user, pass;

    static {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            System.err.println("❌ Không tìm thấy MySQL JDBC Driver! Hãy kiểm tra thư viện mysql-connector trong lib.");
        }
    }

    public Database(String url, String user, String pass) {
        this.url = url;
        this.user = user;
        this.pass = pass;
    }

    private Connection conn() throws SQLException {
        return DriverManager.getConnection(url, user, pass);
    }

    /** Tạo bảng nếu chưa có. */
    public void init() throws SQLException {
        try (Connection c = conn(); Statement s = c.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS users (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY," +
                    "username VARCHAR(50) NOT NULL UNIQUE," +
                    "password_hash VARCHAR(100) NOT NULL," +
                    "salt VARCHAR(40) NOT NULL," +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP) CHARACTER SET utf8mb4");
            s.execute("CREATE TABLE IF NOT EXISTS chat_groups (" +
                    "name VARCHAR(50) PRIMARY KEY," +
                    "owner VARCHAR(50) NOT NULL," +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP) CHARACTER SET utf8mb4");
            s.execute("CREATE TABLE IF NOT EXISTS group_members (" +
                    "group_name VARCHAR(50) NOT NULL," +
                    "username VARCHAR(50) NOT NULL," +
                    "PRIMARY KEY (group_name, username)) CHARACTER SET utf8mb4");
            s.execute("CREATE TABLE IF NOT EXISTS messages (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                    "sender VARCHAR(50) NOT NULL," +
                    "kind VARCHAR(10) NOT NULL," +
                    "target VARCHAR(50) NOT NULL," +
                    "media_type VARCHAR(10) NOT NULL DEFAULT 'TEXT'," +
                    "content TEXT," +
                    "file_name VARCHAR(255)," +
                    "file_path VARCHAR(500)," +
                    "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                    "INDEX idx_kind_target (kind, target)) CHARACTER SET utf8mb4");
        }
    }

    // ---------- Người dùng ----------

    private static String hash(String salt, String password) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(md.digest((salt + password).getBytes("UTF-8")));
    }

    /** @return true nếu đăng ký thành công, false nếu tên đã tồn tại. */
    public boolean register(String username, String password) throws Exception {
        byte[] s = new byte[16];
        new SecureRandom().nextBytes(s);
        String salt = HexFormat.of().formatHex(s);
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement("INSERT INTO users(username,password_hash,salt) VALUES(?,?,?)")) {
            ps.setString(1, username);
            ps.setString(2, hash(salt, password));
            ps.setString(3, salt);
            ps.executeUpdate();
            return true;
        } catch (SQLIntegrityConstraintViolationException e) {
            return false;
        }
    }

    public boolean login(String username, String password) throws Exception {
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement("SELECT password_hash, salt FROM users WHERE username=?")) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getString(1).equals(hash(rs.getString(2), password));
            }
        }
    }

    public boolean userExists(String username) throws SQLException {
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement("SELECT 1 FROM users WHERE username=?")) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public List<String> allUsers() throws SQLException {
        List<String> r = new ArrayList<>();
        try (Connection c = conn(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT username FROM users ORDER BY username")) {
            while (rs.next()) r.add(rs.getString(1));
        }
        return r;
    }

    // ---------- Nhóm (Multicast) ----------

    /** @return null nếu OK, ngược lại là thông báo lỗi. */
    public String createGroup(String name, String owner, Collection<String> members) throws SQLException {
        Set<String> all = new LinkedHashSet<>(members);
        all.add(owner);
        if (all.size() < 2) return "Nhóm cần ít nhất 2 thành viên";
        for (String m : all) if (!userExists(m)) return "Người dùng không tồn tại: " + m;
        try (Connection c = conn()) {
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO chat_groups(name,owner) VALUES(?,?)")) {
                ps.setString(1, name);
                ps.setString(2, owner);
                ps.executeUpdate();
            } catch (SQLIntegrityConstraintViolationException e) {
                return "Tên nhóm đã tồn tại";
            }
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO group_members(group_name,username) VALUES(?,?)")) {
                for (String m : all) {
                    ps.setString(1, name);
                    ps.setString(2, m);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        }
        return null;
    }

    public List<String> members(String group) throws SQLException {
        List<String> r = new ArrayList<>();
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement("SELECT username FROM group_members WHERE group_name=? ORDER BY username")) {
            ps.setString(1, group);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) r.add(rs.getString(1));
            }
        }
        return r;
    }

    /** Các nhóm mà user tham gia, kèm danh sách thành viên. */
    public Map<String, List<String>> groupsOf(String username) throws SQLException {
        Map<String, List<String>> r = new TreeMap<>();
        String sql = "SELECT group_name, username FROM group_members WHERE group_name IN " +
                "(SELECT group_name FROM group_members WHERE username=?) ORDER BY group_name, username";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) r.computeIfAbsent(rs.getString(1), k -> new ArrayList<>()).add(rs.getString(2));
            }
        }
        return r;
    }

    // ---------- Tin nhắn ----------

    public Msg saveMessage(String sender, String kind, String target, String media,
                           String content, String fileName, String filePath) throws SQLException {
        Timestamp now = new Timestamp(System.currentTimeMillis());
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO messages(sender,kind,target,media_type,content,file_name,file_path,created_at) VALUES(?,?,?,?,?,?,?,?)",
                     Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, sender);
            ps.setString(2, kind);
            ps.setString(3, target);
            ps.setString(4, media);
            ps.setString(5, content);
            ps.setString(6, fileName);
            ps.setString(7, filePath);
            ps.setTimestamp(8, now);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                rs.next();
                return new Msg(rs.getLong(1), sender, kind, target, media, content, fileName, filePath, now);
            }
        }
    }

    private static Msg map(ResultSet rs) throws SQLException {
        return new Msg(rs.getLong("id"), rs.getString("sender"), rs.getString("kind"), rs.getString("target"),
                rs.getString("media_type"), rs.getString("content"), rs.getString("file_name"),
                rs.getString("file_path"), rs.getTimestamp("created_at"));
    }

    public Msg getMessage(long id) throws SQLException {
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement("SELECT * FROM messages WHERE id=?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    /** 200 tin gần nhất của một cuộc hội thoại, cũ → mới. */
    public List<Msg> history(String me, String kind, String target) throws SQLException {
        String sql;
        Object[] args;
        switch (kind) {
            case "PRIVATE" -> {
                sql = "SELECT * FROM messages WHERE kind='PRIVATE' AND ((sender=? AND target=?) OR (sender=? AND target=?)) ORDER BY id DESC LIMIT 200";
                args = new Object[]{me, target, target, me};
            }
            case "GROUP" -> {
                sql = "SELECT * FROM messages WHERE kind='GROUP' AND target=? ORDER BY id DESC LIMIT 200";
                args = new Object[]{target};
            }
            default -> {
                sql = "SELECT * FROM messages WHERE kind='BROADCAST' ORDER BY id DESC LIMIT 200";
                args = new Object[]{};
            }
        }
        List<Msg> r = new ArrayList<>();
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) ps.setString(i + 1, (String) args[i]);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) r.add(map(rs));
            }
        }
        Collections.reverse(r);
        return r;
    }

    public boolean canAccess(String user, Msg m) throws SQLException {
        return switch (m.kind()) {
            case "PRIVATE" -> user.equals(m.sender()) || user.equals(m.target());
            case "GROUP" -> members(m.target()).contains(user);
            default -> true;
        };
    }
}
