package database;

import config.ServerConfig;

import java.io.File;
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
 * Quản lý kết nối và thao tác dữ liệu với MySQL Database.
 */
public class DatabaseManager {
    private static DatabaseManager instance;
    private final ServerConfig config;
    private final ExecutorService logExecutor = Executors.newSingleThreadExecutor();

    public static synchronized DatabaseManager getInstance() {
        if (instance == null) {
            instance = new DatabaseManager();
        }
        return instance;
    }

    private DatabaseManager() {
        this.config = ServerConfig.getInstance();
    }

    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(
                config.getDbJdbcUrl(),
                config.getDbUser(),
                config.getDbPass()
        );
    }

    /**
     * Kiểm tra kết nối tới MySQL.
     */
    public boolean testConnection() {
        try (Connection conn = getConnection()) {
            return conn.isValid(2);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Khởi tạo các bảng và dữ liệu mẫu nếu chưa có.
     */
    public void init() throws SQLException {
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            // 1. Tạo bảng người dùng
            stmt.execute("CREATE TABLE IF NOT EXISTS ftp_users (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY," +
                    "username VARCHAR(50) NOT NULL UNIQUE," +
                    "password_hash VARCHAR(128) NOT NULL," +
                    "salt VARCHAR(64) NOT NULL," +
                    "home_directory VARCHAR(255) NOT NULL," +
                    "can_upload TINYINT(1) NOT NULL DEFAULT 1," +
                    "can_download TINYINT(1) NOT NULL DEFAULT 1," +
                    "can_delete TINYINT(1) NOT NULL DEFAULT 1," +
                    "can_modify TINYINT(1) NOT NULL DEFAULT 1," +
                    "is_active TINYINT(1) NOT NULL DEFAULT 1," +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                    "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");

            // 2. Tạo bảng nhật ký hoạt động
            stmt.execute("CREATE TABLE IF NOT EXISTS ftp_logs (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                    "username VARCHAR(50)," +
                    "client_ip VARCHAR(64)," +
                    "command VARCHAR(50) NOT NULL," +
                    "path VARCHAR(500)," +
                    "file_size BIGINT DEFAULT 0," +
                    "status VARCHAR(20) NOT NULL," +
                    "message VARCHAR(500)," +
                    "timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");

            // 3. Tạo bảng cấu hình
            stmt.execute("CREATE TABLE IF NOT EXISTS ftp_settings (" +
                    "config_key VARCHAR(50) PRIMARY KEY," +
                    "config_value VARCHAR(255) NOT NULL," +
                    "description VARCHAR(255)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");

            // Kiểm tra và chèn tài khoản mẫu
            seedDefaultUsers(conn);
        }
    }

    private void seedDefaultUsers(Connection conn) {
        try (Statement s = conn.createStatement();
             ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM ftp_users")) {
            if (rs.next() && rs.getInt(1) == 0) {
                System.out.println("Cơ sở dữ liệu người dùng trống. Tiến hành tạo tài khoản mẫu...");

                UserModel admin = new UserModel("admin", "ftp_root/admin");
                admin.setCanUpload(true);
                admin.setCanDownload(true);
                admin.setCanDelete(true);
                admin.setCanModify(true);
                addUserDirect(conn, admin, "admin123");

                UserModel student = new UserModel("student", "ftp_root/student");
                student.setCanUpload(true);
                student.setCanDownload(true);
                student.setCanDelete(true);
                student.setCanModify(true);
                addUserDirect(conn, student, "123456");

                UserModel guest = new UserModel("anonymous", "ftp_root/public");
                guest.setCanUpload(false);
                guest.setCanDownload(true);
                guest.setCanDelete(false);
                guest.setCanModify(false);
                addUserDirect(conn, guest, "anonymous");

                System.out.println("Đã khởi tạo tài khoản mẫu: admin/admin123, student/123456, anonymous/anonymous");
            }
        } catch (Exception e) {
            System.err.println("Lỗi tạo người dùng mẫu: " + e.getMessage());
        }
    }

    // ---------- Băm mật khẩu (SHA-256 + Salt) ----------

    public static String generateSalt() {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        return HexFormat.of().formatHex(salt);
    }

    public static String hashPassword(String salt, String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest((salt + password).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("Lỗi băm mật khẩu", e);
        }
    }

    // ---------- Thao tác người dùng ----------

    private void addUserDirect(Connection conn, UserModel user, String rawPassword) throws SQLException {
        String salt = generateSalt();
        String hash = hashPassword(salt, rawPassword);

        String sql = "INSERT INTO ftp_users (username, password_hash, salt, home_directory, " +
                "can_upload, can_download, can_delete, can_modify, is_active) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, user.getUsername());
            ps.setString(2, hash);
            ps.setString(3, salt);
            ps.setString(4, user.getHomeDirectory());
            ps.setBoolean(5, user.isCanUpload());
            ps.setBoolean(6, user.isCanDownload());
            ps.setBoolean(7, user.isCanDelete());
            ps.setBoolean(8, user.isCanModify());
            ps.setBoolean(9, user.isActive());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    user.setId(rs.getInt(1));
                }
            }
        }

        // Tạo thư mục người dùng trên ổ đĩa nếu chưa có
        ensureDirectory(user.getHomeDirectory());
    }

    public boolean addUser(UserModel user, String rawPassword) {
        try (Connection conn = getConnection()) {
            addUserDirect(conn, user, rawPassword);
            return true;
        } catch (Exception e) {
            System.err.println("Lỗi thêm người dùng: " + e.getMessage());
            return false;
        }
    }

    public boolean updateUser(UserModel user, String newRawPasswordOrNull) {
        String sql;
        boolean changePass = newRawPasswordOrNull != null && !newRawPasswordOrNull.isBlank();
        if (changePass) {
            sql = "UPDATE ftp_users SET home_directory=?, can_upload=?, can_download=?, " +
                    "can_delete=?, can_modify=?, is_active=?, password_hash=?, salt=? WHERE id=?";
        } else {
            sql = "UPDATE ftp_users SET home_directory=?, can_upload=?, can_download=?, " +
                    "can_delete=?, can_modify=?, is_active=? WHERE id=?";
        }

        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, user.getHomeDirectory());
            ps.setBoolean(2, user.isCanUpload());
            ps.setBoolean(3, user.isCanDownload());
            ps.setBoolean(4, user.isCanDelete());
            ps.setBoolean(5, user.isCanModify());
            ps.setBoolean(6, user.isActive());

            if (changePass) {
                String salt = generateSalt();
                String hash = hashPassword(salt, newRawPasswordOrNull);
                ps.setString(7, hash);
                ps.setString(8, salt);
                ps.setInt(9, user.getId());
            } else {
                ps.setInt(7, user.getId());
            }

            int rows = ps.executeUpdate();
            ensureDirectory(user.getHomeDirectory());
            return rows > 0;
        } catch (Exception e) {
            System.err.println("Lỗi cập nhật người dùng: " + e.getMessage());
            return false;
        }
    }

    public boolean deleteUser(int id) {
        String sql = "DELETE FROM ftp_users WHERE id=?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            System.err.println("Lỗi xóa người dùng: " + e.getMessage());
            return false;
        }
    }

    public boolean setUserActive(int id, boolean active) {
        String sql = "UPDATE ftp_users SET is_active=? WHERE id=?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBoolean(1, active);
            ps.setInt(2, id);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            return false;
        }
    }

    public List<UserModel> getAllUsers() {
        List<UserModel> list = new ArrayList<>();
        String sql = "SELECT * FROM ftp_users ORDER BY id ASC";
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(mapUser(rs));
            }
        } catch (Exception e) {
            System.err.println("Lỗi lấy danh sách người dùng: " + e.getMessage());
        }
        return list;
    }

    public UserModel getUserByUsername(String username) {
        String sql = "SELECT * FROM ftp_users WHERE username = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapUser(rs);
                }
            }
        } catch (Exception e) {
            System.err.println("Lỗi tìm người dùng: " + e.getMessage());
        }
        return null;
    }

    /**
     * Xác thực thông tin đăng nhập người dùng từ MySQL.
     * Hỗ trợ cả mật khẩu băm SHA-256 + salt và kiểm tra mềm nếu người dùng nhập tay qua phpMyAdmin.
     */
    public UserModel authenticate(String username, String password) {
        if (username == null || password == null) return null;

        // Anonymous login nếu cho phép
        if (username.equalsIgnoreCase("anonymous")) {
            UserModel anon = getUserByUsername("anonymous");
            if (anon != null && anon.isActive()) {
                return anon;
            }
        }

        String sql = "SELECT * FROM ftp_users WHERE username = ? AND is_active = 1";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    UserModel user = mapUser(rs);
                    String salt = user.getSalt();
                    String hash = user.getPasswordHash();

                    // Kiểm tra băm chuẩn
                    if (salt != null && !salt.isBlank()) {
                        String expectedHash = hashPassword(salt, password);
                        if (expectedHash.equalsIgnoreCase(hash)) {
                            return user;
                        }
                    }

                    // Hỗ trợ trường hợp tạo qua phpMyAdmin bằng plaintext
                    if (password.equals(hash)) {
                        // Tự động nâng cấp lên mã hóa salt + hash
                        updateUser(user, password);
                        return user;
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Lỗi xác thực người dùng: " + e.getMessage());
        }
        return null;
    }

    private UserModel mapUser(ResultSet rs) throws SQLException {
        UserModel u = new UserModel();
        u.setId(rs.getInt("id"));
        u.setUsername(rs.getString("username"));
        u.setPasswordHash(rs.getString("password_hash"));
        u.setSalt(rs.getString("salt"));
        u.setHomeDirectory(rs.getString("home_directory"));
        u.setCanUpload(rs.getBoolean("can_upload"));
        u.setCanDownload(rs.getBoolean("can_download"));
        u.setCanDelete(rs.getBoolean("can_delete"));
        u.setCanModify(rs.getBoolean("can_modify"));
        u.setActive(rs.getBoolean("is_active"));
        u.setCreatedAt(rs.getTimestamp("created_at"));
        return u;
    }

    // ---------- Nhật ký hoạt động (Audit Logs) ----------

    /**
     * Ghi nhật ký bất đồng bộ (không làm nghẽn tiến trình truyền file FTP).
     */
    public void logAsync(String username, String clientIp, String command, String path, long fileSize, String status, String message) {
        logExecutor.submit(() -> {
            String sql = "INSERT INTO ftp_logs (username, client_ip, command, path, file_size, status, message) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?)";
            try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, username);
                ps.setString(2, clientIp);
                ps.setString(3, command);
                ps.setString(4, path);
                ps.setLong(5, fileSize);
                ps.setString(6, status);
                ps.setString(7, message);
                ps.executeUpdate();
            } catch (Exception e) {
                System.err.println("Lỗi ghi log MySQL: " + e.getMessage());
            }
        });
    }

    public List<FtpLogModel> getLogs(int limit, String searchKeyword, String commandFilter) {
        List<FtpLogModel> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT * FROM ftp_logs WHERE 1=1 ");
        List<Object> params = new ArrayList<>();

        if (searchKeyword != null && !searchKeyword.isBlank()) {
            sql.append("AND (username LIKE ? OR client_ip LIKE ? OR path LIKE ? OR message LIKE ?) ");
            String k = "%" + searchKeyword.trim() + "%";
            params.add(k);
            params.add(k);
            params.add(k);
            params.add(k);
        }

        if (commandFilter != null && !commandFilter.isBlank() && !commandFilter.equalsIgnoreCase("ALL")) {
            sql.append("AND command = ? ");
            params.add(commandFilter.trim());
        }

        sql.append("ORDER BY id DESC LIMIT ?");
        params.add(limit <= 0 ? 100 : limit);

        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    FtpLogModel m = new FtpLogModel();
                    m.setId(rs.getLong("id"));
                    m.setUsername(rs.getString("username"));
                    m.setClientIp(rs.getString("client_ip"));
                    m.setCommand(rs.getString("command"));
                    m.setPath(rs.getString("path"));
                    m.setFileSize(rs.getLong("file_size"));
                    m.setStatus(rs.getString("status"));
                    m.setMessage(rs.getString("message"));
                    m.setTimestamp(rs.getTimestamp("timestamp"));
                    list.add(m);
                }
            }
        } catch (Exception e) {
            System.err.println("Lỗi lấy danh sách log: " + e.getMessage());
        }
        return list;
    }

    public boolean clearLogs() {
        try (Connection conn = getConnection(); Statement s = conn.createStatement()) {
            s.execute("TRUNCATE TABLE ftp_logs");
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void ensureDirectory(String dirPath) {
        try {
            File f = new File(dirPath);
            if (!f.exists()) {
                f.mkdirs();
            }
        } catch (Exception ignored) {}
    }
}
