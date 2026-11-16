import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class DBManager {
    private String host;
    private int port;
    private String dbName;
    private String dbUser;
    private String dbPass;
    private String dbUrl;

    static {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            System.err.println("❌ Không tìm thấy MySQL JDBC Driver!");
        }
    }

    public DBManager(String dbName, String dbUser, String dbPass) {
        this("localhost", 3308, dbName, dbUser, dbPass);
    }

    public DBManager(String host, int port, String dbName, String dbUser, String dbPass) {
        this.host = host;
        this.port = port;
        this.dbName = dbName;
        this.dbUser = dbUser;
        this.dbPass = dbPass;
        this.dbUrl = "jdbc:mysql://" + host + ":" + port + "/" + dbName + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(dbUrl, dbUser, dbPass);
    }

    // Kiểm tra kết nối CSDL và tạo sẵn bảng nếu chưa có
    public boolean testConnection() {
        String serverUrl = "jdbc:mysql://" + host + ":" + port + "/?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
        try (Connection serverConn = DriverManager.getConnection(serverUrl, dbUser, dbPass);
             Statement stmt = serverConn.createStatement()) {
            stmt.executeUpdate("CREATE DATABASE IF NOT EXISTS " + dbName + ";");
        } catch (SQLException e) {
            System.err.println("❌ Không thể kết nối tới MySQL tại " + host + ":" + port);
            return false;
        }

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            
            // Tạo bảng accounts gồm thông tin tài khoản & thông tin cá nhân
            String sql = "CREATE TABLE IF NOT EXISTS accounts ("
                    + "username VARCHAR(50) PRIMARY KEY, "
                    + "password VARCHAR(255) NOT NULL, "
                    + "balance DOUBLE NOT NULL DEFAULT 0, "
                    + "is_locked BOOLEAN NOT NULL DEFAULT FALSE, "
                    + "full_name VARCHAR(100) NOT NULL DEFAULT '', "
                    + "email VARCHAR(100) NOT NULL DEFAULT '', "
                    + "phone VARCHAR(20) NOT NULL DEFAULT ''"
                    + ");";
            stmt.executeUpdate(sql);

            // Đảm bảo các bảng cũ đã tồn tại được bổ sung cột mới
            ensureColumn(conn, "full_name", "VARCHAR(100) NOT NULL DEFAULT ''");
            ensureColumn(conn, "email", "VARCHAR(100) NOT NULL DEFAULT ''");
            ensureColumn(conn, "phone", "VARCHAR(20) NOT NULL DEFAULT ''");

            // Nạp dữ liệu mẫu ban đầu
            String insertSample = "INSERT INTO accounts (username, password, balance, is_locked, full_name, email, phone) VALUES "
                    + "('user1', '123', 5000000.0, FALSE, 'Nguyễn Văn A', 'user1@bank.vn', '0901000001'), "
                    + "('user2', '123', 3000000.0, FALSE, 'Trần Thị B', 'user2@bank.vn', '0901000002'), "
                    + "('user3', '123', 10000000.0, FALSE, 'Lê Văn C', 'user3@bank.vn', '0901000003') "
                    + "ON DUPLICATE KEY UPDATE username = username;";
            stmt.executeUpdate(insertSample);

            return true;
        } catch (SQLException e) {
            System.err.println("❌ Lỗi CSDL: " + e.getMessage());
            return false;
        }
    }

    private void ensureColumn(Connection conn, String colName, String colDef) throws SQLException {
        DatabaseMetaData meta = conn.getMetaData();
        try (ResultSet rs = meta.getColumns(conn.getCatalog(), null, "accounts", colName)) {
            if (!rs.next()) {
                try (Statement st = conn.createStatement()) {
                    st.executeUpdate("ALTER TABLE accounts ADD COLUMN " + colName + " " + colDef);
                }
            }
        }
    }

    private Account mapAccount(ResultSet rs) throws SQLException {
        return new Account(
                rs.getString("username"),
                rs.getString("password"),
                rs.getDouble("balance"),
                rs.getBoolean("is_locked"),
                rs.getString("full_name"),
                rs.getString("email"),
                rs.getString("phone")
        );
    }

    // Mở khóa các tài khoản khi bật Server
    public void resetAllLocks() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("UPDATE accounts SET is_locked = FALSE");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Lấy thông tin 1 tài khoản
    public Account getAccount(String username) {
        String sql = "SELECT * FROM accounts WHERE username = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapAccount(rs);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    // Lấy toàn bộ danh sách tài khoản từ CSDL
    public List<Account> getAllAccounts() {
        List<Account> list = new ArrayList<>();
        String sql = "SELECT * FROM accounts ORDER BY username ASC";
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                list.add(mapAccount(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    // Lưu hoặc cập nhật 1 tài khoản vào CSDL (dùng khi đồng bộ từ Server kia)
    public void saveOrUpdateAccount(Account acc) {
        String sql = "INSERT INTO accounts (username, password, balance, is_locked, full_name, email, phone) VALUES (?, ?, ?, ?, ?, ?, ?) "
                   + "ON DUPLICATE KEY UPDATE "
                   + "password = VALUES(password), "
                   + "balance = VALUES(balance), "
                   + "is_locked = VALUES(is_locked), "
                   + "full_name = VALUES(full_name), "
                   + "email = VALUES(email), "
                   + "phone = VALUES(phone)";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, acc.getUsername());
            ps.setString(2, acc.getPassword());
            ps.setDouble(3, acc.getBalance());
            ps.setBoolean(4, acc.isLocked());
            ps.setString(5, acc.getFullName());
            ps.setString(6, acc.getEmail());
            ps.setString(7, acc.getPhone());
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Cập nhật thông tin cá nhân của user
    public boolean updateUserInfo(String username, String fullName, String email, String phone, String password) {
        String sql = "UPDATE accounts SET full_name = ?, email = ?, phone = ?, password = ? WHERE username = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, fullName);
            ps.setString(2, email);
            ps.setString(3, phone);
            ps.setString(4, password);
            ps.setString(5, username);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    // Cập nhật trạng thái khóa tài khoản
    public boolean updateLockStatus(String username, boolean isLocked) {
        String sql = "UPDATE accounts SET is_locked = ? WHERE username = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBoolean(1, isLocked);
            ps.setString(2, username);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    // Cập nhật chuyển tiền của 2 tài khoản trong 1 Transaction
    public boolean updateTransfer(String fromUser, double newFromBalance, String toUser, double newToBalance) {
        String sql = "UPDATE accounts SET balance = ? WHERE username = ?";
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false); // Bắt đầu Transaction
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                // Trừ tiền người gửi
                ps.setDouble(1, newFromBalance);
                ps.setString(2, fromUser);
                ps.executeUpdate();

                // Cộng tiền người nhận
                ps.setDouble(1, newToBalance);
                ps.setString(2, toUser);
                ps.executeUpdate();

                conn.commit();
                return true;
            } catch (SQLException ex) {
                conn.rollback();
                ex.printStackTrace();
                return false;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
}
