package ui;

import client.FtpFileItem;
import client.SimpleFtpClient;
import config.ServerConfig;
import database.DatabaseManager;
import database.FtpLogModel;
import database.UserModel;
import server.FtpEventListener;
import server.FtpServer;
import server.FtpSession;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * Giao diện đồ họa chính của FTP Server (Tương tự FileZilla Server):
 * - Bảng điều khiển giám sát trạng thái & phiên kết nối
 * - Console nhật ký thời gian thực
 * - Quản lý tài khoản người dùng MySQL
 * - Xem & lọc lịch sử hoạt động từ MySQL
 * - Cấu hình hệ thống & kết nối Database
 * - Trình khách FTP tích hợp sẵn để kiểm tra trực tiếp
 */
public class MainServerFrame extends JFrame implements FtpEventListener {
    private final FtpServer server;
    private final ServerConfig config;
    private final DatabaseManager db;

    // Components Header
    private JLabel lblStatusBadge;
    private JLabel lblDbStatusBadge;
    private JSpinner spinPort;
    private JButton btnStartStop;

    // Tab 1: Dashboard
    private JLabel lblCardSessions;
    private JLabel lblCardUsers;
    private JLabel lblCardTransfers;
    private DefaultTableModel modelSessions;
    private JTable tableSessions;
    private long totalTransfersCount = 0;

    // Tab 2: Live Log
    private JTextArea txtLiveLog;
    private JCheckBox chkAutoScroll;

    // Tab 3: Users
    private DefaultTableModel modelUsers;
    private JTable tableUsers;

    // Tab 4: DB Logs
    private DefaultTableModel modelLogs;
    private JTable tableLogs;
    private JTextField txtSearchLog;
    private JComboBox<String> cbCommandFilter;

    // Tab 5: Settings
    private JTextField txtDbHost, txtDbPort, txtDbName, txtDbUser;
    private JPasswordField txtDbPass;
    private JTextField txtPasvMin, txtPasvMax, txtMaxClients, txtTimeout;

    // Tab 6: Built-in Client
    private JTextField txtClientHost, txtClientPort, txtClientUser;
    private JPasswordField txtClientPass;
    private JButton btnClientConnect;
    private JTextField txtClientPath;
    private DefaultTableModel modelClientFiles;
    private JTable tableClientFiles;
    private JProgressBar clientProgressBar;
    private JLabel lblClientStatus;
    private SimpleFtpClient testClient;

    public MainServerFrame() {
        super("FTP Server - Quản lý máy chủ FTP & Database MySQL (LTM)");
        this.config = ServerConfig.getInstance();
        this.db = DatabaseManager.getInstance();
        this.server = new FtpServer();
        this.server.addListener(this);

        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        initComponents();
        setSize(1080, 720);
        setMinimumSize(new Dimension(850, 550));
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        // Khởi tạo kiểm tra database và tải danh sách ban đầu
        initDatabase();
        refreshUsers();
        refreshDbLogs();
    }

    private void initDatabase() {
        new Thread(() -> {
            try {
                db.init();
                SwingUtilities.invokeLater(() -> {
                    lblDbStatusBadge.setText(" MySQL: Đã kết nối (Port " + config.getDbPort() + ") ");
                    lblDbStatusBadge.setBackground(new Color(40, 167, 69));
                    refreshUsers();
                });
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    lblDbStatusBadge.setText(" MySQL: Lỗi kết nối! ");
                    lblDbStatusBadge.setBackground(new Color(220, 53, 69));
                    JOptionPane.showMessageDialog(this,
                            "Không thể kết nối MySQL tại " + config.getDbHost() + ":" + config.getDbPort() + "\n" +
                                    "Nguyên nhân: " + e.getMessage() + "\n\n" +
                                    "Vui lòng đảm bảo Docker container 'ltm-mysql' đang chạy trên cổng 3308.",
                            "Cảnh báo Database", JOptionPane.WARNING_MESSAGE);
                });
            }
        }).start();
    }

    private void initComponents() {
        setLayout(new BorderLayout());

        // Header Bar
        add(createHeaderPanel(), BorderLayout.NORTH);

        // Tabbed Pane
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        tabbedPane.addTab("  Bảng điều khiển  ", createDashboardTab());
        tabbedPane.addTab("  Nhật ký thời gian thực  ", createLiveLogTab());
        tabbedPane.addTab("  Người dùng MySQL  ", createUsersTab());
        tabbedPane.addTab("  Lịch sử Database  ", createHistoryTab());
        tabbedPane.addTab("  Cấu hình hệ thống  ", createSettingsTab());
        tabbedPane.addTab("  Trình khách FTP test  ", createClientTab());

        add(tabbedPane, BorderLayout.CENTER);
    }

    // ==========================================
    // HEADER PANEL
    // ==========================================
    private JPanel createHeaderPanel() {
        JPanel header = new JPanel(new BorderLayout(15, 0));
        header.setBackground(new Color(33, 37, 41));
        header.setBorder(new EmptyBorder(10, 15, 10, 15));

        // Trái: Tiêu đề và biểu tượng
        JPanel titlePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        titlePanel.setOpaque(false);
        JLabel lblTitle = new JLabel("FTP SERVER MANAGER");
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 17));
        lblTitle.setForeground(Color.WHITE);
        titlePanel.add(lblTitle);

        lblStatusBadge = new JLabel(" ĐÃ DỪNG ");
        lblStatusBadge.setOpaque(true);
        lblStatusBadge.setBackground(new Color(108, 117, 125));
        lblStatusBadge.setForeground(Color.WHITE);
        lblStatusBadge.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblStatusBadge.setBorder(new EmptyBorder(3, 8, 3, 8));
        titlePanel.add(lblStatusBadge);

        lblDbStatusBadge = new JLabel(" MySQL: Đang kết nối... ");
        lblDbStatusBadge.setOpaque(true);
        lblDbStatusBadge.setBackground(new Color(255, 193, 7));
        lblDbStatusBadge.setForeground(Color.BLACK);
        lblDbStatusBadge.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblDbStatusBadge.setBorder(new EmptyBorder(3, 8, 3, 8));
        titlePanel.add(lblDbStatusBadge);

        header.add(titlePanel, BorderLayout.WEST);

        // Phải: Nút Khởi động / Dừng và cấu hình Port nhanh
        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        controlPanel.setOpaque(false);

        JLabel lblPort = new JLabel("Cổng FTP:");
        lblPort.setForeground(Color.WHITE);
        lblPort.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        controlPanel.add(lblPort);

        spinPort = new JSpinner(new SpinnerNumberModel(config.getServerPort(), 1, 65535, 1));
        spinPort.setPreferredSize(new Dimension(80, 28));
        controlPanel.add(spinPort);

        btnStartStop = new JButton("Khởi động Server");
        btnStartStop.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnStartStop.setBackground(new Color(40, 167, 69));
        btnStartStop.setForeground(Color.WHITE);
        btnStartStop.setFocusPainted(false);
        btnStartStop.setPreferredSize(new Dimension(150, 32));
        btnStartStop.addActionListener(e -> toggleServer());
        controlPanel.add(btnStartStop);

        header.add(controlPanel, BorderLayout.EAST);
        return header;
    }

    private void toggleServer() {
        if (!server.isRunning()) {
            int port = (int) spinPort.getValue();
            try {
                server.start(port);
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this,
                        "Không thể khởi động FTP Server trên cổng " + port + ":\n" + e.getMessage() +
                                "\n\n(Nếu cổng 21 yêu cầu quyền Administrator, bạn có thể đổi sang cổng 2121)",
                        "Lỗi Khởi Động", JOptionPane.ERROR_MESSAGE);
            }
        } else {
            server.stop();
        }
    }

    // ==========================================
    // TAB 1: DASHBOARD & ACTIVE SESSIONS
    // ==========================================
    private JPanel createDashboardTab() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(new EmptyBorder(15, 15, 15, 15));

        // 3 Thẻ thống kê (Metric Cards)
        JPanel cardsPanel = new JPanel(new GridLayout(1, 3, 15, 0));
        lblCardSessions = createMetricCard(cardsPanel, "Phiên kết nối hoạt động", "0", new Color(0, 123, 255));
        lblCardUsers = createMetricCard(cardsPanel, "Tổng tài khoản MySQL", "0", new Color(40, 167, 69));
        lblCardTransfers = createMetricCard(cardsPanel, "Tổng lượt truyền tệp", "0", new Color(255, 193, 7));
        panel.add(cardsPanel, BorderLayout.NORTH);

        // Bảng danh sách phiên hoạt động
        JPanel tablePanel = new JPanel(new BorderLayout(5, 5));
        tablePanel.setBorder(BorderFactory.createTitledBorder("Danh sách Client đang kết nối (Active Sessions)"));

        String[] cols = {"Session ID", "Địa chỉ Client", "Cổng", "Tài khoản", "Thư mục ảo", "Trạng thái", "Thời gian kết nối"};
        modelSessions = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int row, int col) { return false; }
        };
        tableSessions = new JTable(modelSessions);
        tableSessions.setRowHeight(24);
        tableSessions.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        tablePanel.add(new JScrollPane(tableSessions), BorderLayout.CENTER);

        // Nút thao tác dưới bảng
        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 5));
        JButton btnKick = new JButton("Ngắt kết nối Client đã chọn");
        btnKick.setBackground(new Color(220, 53, 69));
        btnKick.setForeground(Color.WHITE);
        btnKick.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnKick.addActionListener(e -> {
            int row = tableSessions.getSelectedRow();
            if (row >= 0) {
                String sessionId = (String) modelSessions.getValueAt(row, 0);
                server.disconnectClient(sessionId);
                updateActiveSessionsTable();
            } else {
                JOptionPane.showMessageDialog(this, "Vui lòng chọn một phiên cần ngắt kết nối!", "Thông báo", JOptionPane.INFORMATION_MESSAGE);
            }
        });

        JButton btnRefreshSessions = new JButton("Làm mới danh sách");
        btnRefreshSessions.addActionListener(e -> updateActiveSessionsTable());

        actionPanel.add(btnKick);
        actionPanel.add(btnRefreshSessions);
        tablePanel.add(actionPanel, BorderLayout.SOUTH);

        panel.add(tablePanel, BorderLayout.CENTER);
        return panel;
    }

    private JLabel createMetricCard(JPanel parent, String title, String val, Color color) {
        JPanel card = new JPanel(new BorderLayout(5, 5));
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 4, 0, 0, color),
                BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(new Color(220, 220, 220), 1),
                        new EmptyBorder(12, 15, 12, 15)
                )
        ));

        JLabel lblTitle = new JLabel(title);
        lblTitle.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblTitle.setForeground(new Color(108, 117, 125));

        JLabel lblVal = new JLabel(val);
        lblVal.setFont(new Font("Segoe UI", Font.BOLD, 24));
        lblVal.setForeground(new Color(33, 37, 41));

        card.add(lblTitle, BorderLayout.NORTH);
        card.add(lblVal, BorderLayout.CENTER);
        parent.add(card);
        return lblVal;
    }

    private void updateActiveSessionsTable() {
        List<FtpSession> sessions = server.getActiveSessions();
        modelSessions.setRowCount(0);
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss dd/MM");
        for (FtpSession s : sessions) {
            modelSessions.addRow(new Object[]{
                    s.getId(),
                    s.getClientIp(),
                    s.getClientPort(),
                    s.getUsername(),
                    s.getCurrentVirtualDir(),
                    s.getState(),
                    sdf.format(new Date(s.getConnectedTimeMillis()))
            });
        }
        lblCardSessions.setText(String.valueOf(sessions.size()));
    }

    // ==========================================
    // TAB 2: LIVE LOG
    // ==========================================
    private JPanel createLiveLogTab() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));

        txtLiveLog = new JTextArea();
        txtLiveLog.setFont(new Font("Consolas", Font.PLAIN, 12));
        txtLiveLog.setBackground(new Color(30, 30, 30));
        txtLiveLog.setForeground(new Color(220, 220, 220));
        txtLiveLog.setCaretColor(Color.WHITE);
        txtLiveLog.setEditable(false);

        JScrollPane scroll = new JScrollPane(txtLiveLog);
        panel.add(scroll, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 5));
        chkAutoScroll = new JCheckBox("Tự động cuộn xuống", true);

        JButton btnClearLog = new JButton("Xóa nhật ký hiển thị");
        btnClearLog.addActionListener(e -> txtLiveLog.setText(""));

        JButton btnExportLog = new JButton("Xuất ra file...");
        btnExportLog.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                try (FileWriter fw = new FileWriter(chooser.getSelectedFile())) {
                    fw.write(txtLiveLog.getText());
                    JOptionPane.showMessageDialog(this, "Đã lưu nhật ký thành công!");
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(this, "Lỗi lưu file: " + ex.getMessage());
                }
            }
        });

        bottom.add(chkAutoScroll);
        bottom.add(btnClearLog);
        bottom.add(btnExportLog);
        panel.add(bottom, BorderLayout.SOUTH);
        return panel;
    }

    private void appendLiveLog(String msg) {
        SwingUtilities.invokeLater(() -> {
            String time = new SimpleDateFormat("HH:mm:ss").format(new Date());
            txtLiveLog.append("[" + time + "] " + msg + "\n");
            if (chkAutoScroll.isSelected()) {
                txtLiveLog.setCaretPosition(txtLiveLog.getDocument().getLength());
            }
        });
    }

    // ==========================================
    // TAB 3: MYSQL USER MANAGEMENT
    // ==========================================
    private JPanel createUsersTab() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(new EmptyBorder(15, 15, 15, 15));

        String[] cols = {"ID", "Tên tài khoản", "Thư mục gốc (Home)", "Tải về", "Tải lên", "Xóa", "Sửa/Tạo", "Trạng thái", "Ngày tạo"};
        modelUsers = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int r, int c) { return false; }
        };
        tableUsers = new JTable(modelUsers);
        tableUsers.setRowHeight(24);
        tableUsers.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        panel.add(new JScrollPane(tableUsers), BorderLayout.CENTER);

        // Nút chức năng quản lý người dùng
        JPanel btnBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 5));

        JButton btnAdd = new JButton("Thêm người dùng mới");
        btnAdd.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnAdd.setBackground(new Color(40, 167, 69));
        btnAdd.setForeground(Color.WHITE);
        btnAdd.addActionListener(e -> {
            UserDialog dlg = new UserDialog(this, null);
            dlg.setVisible(true);
            if (dlg.isSaved()) refreshUsers();
        });

        JButton btnEdit = new JButton("Chỉnh sửa");
        btnEdit.addActionListener(e -> {
            int row = tableUsers.getSelectedRow();
            if (row >= 0) {
                int id = (int) modelUsers.getValueAt(row, 0);
                UserModel u = db.getAllUsers().stream().filter(x -> x.getId() == id).findFirst().orElse(null);
                if (u != null) {
                    UserDialog dlg = new UserDialog(this, u);
                    dlg.setVisible(true);
                    if (dlg.isSaved()) refreshUsers();
                }
            } else {
                JOptionPane.showMessageDialog(this, "Vui lòng chọn tài khoản cần sửa!");
            }
        });

        JButton btnToggle = new JButton("Khóa / Mở khóa");
        btnToggle.addActionListener(e -> {
            int row = tableUsers.getSelectedRow();
            if (row >= 0) {
                int id = (int) modelUsers.getValueAt(row, 0);
                String status = (String) modelUsers.getValueAt(row, 7);
                boolean currentActive = status.contains("Hoạt động");
                db.setUserActive(id, !currentActive);
                refreshUsers();
            }
        });

        JButton btnDelete = new JButton("Xóa tài khoản");
        btnDelete.setBackground(new Color(220, 53, 69));
        btnDelete.setForeground(Color.WHITE);
        btnDelete.addActionListener(e -> {
            int row = tableUsers.getSelectedRow();
            if (row >= 0) {
                String name = (String) modelUsers.getValueAt(row, 1);
                int id = (int) modelUsers.getValueAt(row, 0);
                int confirm = JOptionPane.showConfirmDialog(this,
                        "Bạn có chắc chắn muốn xóa tài khoản '" + name + "' khỏi Database?",
                        "Xác nhận xóa", JOptionPane.YES_NO_OPTION);
                if (confirm == JOptionPane.YES_OPTION) {
                    db.deleteUser(id);
                    refreshUsers();
                }
            }
        });

        JButton btnOpenFolder = new JButton("Mở thư mục gốc");
        btnOpenFolder.addActionListener(e -> {
            int row = tableUsers.getSelectedRow();
            if (row >= 0) {
                String dirPath = (String) modelUsers.getValueAt(row, 2);
                try {
                    File f = new File(dirPath);
                    if (!f.exists()) f.mkdirs();
                    Desktop.getDesktop().open(f);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, "Không thể mở thư mục: " + ex.getMessage());
                }
            }
        });

        JButton btnRefreshUsers = new JButton("Làm mới từ DB");
        btnRefreshUsers.addActionListener(e -> refreshUsers());

        btnBar.add(btnAdd);
        btnBar.add(btnEdit);
        btnBar.add(btnToggle);
        btnBar.add(btnDelete);
        btnBar.add(btnOpenFolder);
        btnBar.add(btnRefreshUsers);

        panel.add(btnBar, BorderLayout.SOUTH);
        return panel;
    }

    private void refreshUsers() {
        new Thread(() -> {
            List<UserModel> users = db.getAllUsers();
            SwingUtilities.invokeLater(() -> {
                modelUsers.setRowCount(0);
                for (UserModel u : users) {
                    modelUsers.addRow(new Object[]{
                            u.getId(),
                            u.getUsername(),
                            u.getHomeDirectory(),
                            u.isCanDownload() ? "Có" : "Không",
                            u.isCanUpload() ? "Có" : "Không",
                            u.isCanDelete() ? "Có" : "Không",
                            u.isCanModify() ? "Có" : "Không",
                            u.isActive() ? "Hoạt động" : "Bị khóa",
                            u.getCreatedAt() != null ? u.getCreatedAt().toString() : ""
                    });
                }
                lblCardUsers.setText(String.valueOf(users.size()));
            });
        }).start();
    }

    // ==========================================
    // TAB 4: DATABASE AUDIT LOGS
    // ==========================================
    private JPanel createHistoryTab() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(new EmptyBorder(15, 15, 15, 15));

        // Thanh tìm kiếm và bộ lọc
        JPanel filterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        filterPanel.add(new JLabel("Tìm kiếm từ khóa:"));
        txtSearchLog = new JTextField(15);
        filterPanel.add(txtSearchLog);

        filterPanel.add(new JLabel("Lệnh FTP:"));
        cbCommandFilter = new JComboBox<>(new String[]{"ALL", "LOGIN", "QUIT", "RETR", "STOR", "DELE", "MKD", "RMD", "RNTO", "LIST"});
        filterPanel.add(cbCommandFilter);

        JButton btnSearch = new JButton("Lọc dữ liệu");
        btnSearch.addActionListener(e -> refreshDbLogs());
        filterPanel.add(btnSearch);

        JButton btnClearLogs = new JButton("Xóa toàn bộ Log MySQL");
        btnClearLogs.addActionListener(e -> {
            int c = JOptionPane.showConfirmDialog(this, "Bạn có chắc muốn xóa sạch bảng ftp_logs?", "Cảnh báo", JOptionPane.YES_NO_OPTION);
            if (c == JOptionPane.YES_OPTION) {
                db.clearLogs();
                refreshDbLogs();
            }
        });
        filterPanel.add(btnClearLogs);

        panel.add(filterPanel, BorderLayout.NORTH);

        // Bảng dữ liệu log
        String[] cols = {"ID", "Thời gian", "Người dùng", "IP Client", "Lệnh", "Đường dẫn / Tệp", "Dung lượng (Bytes)", "Trạng thái", "Ghi chú"};
        modelLogs = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int r, int c) { return false; }
        };
        tableLogs = new JTable(modelLogs);
        tableLogs.setRowHeight(22);
        tableLogs.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));

        // Căn chỉnh màu theo trạng thái
        tableLogs.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object val, boolean sel, boolean focus, int row, int col) {
                Component c = super.getTableCellRendererComponent(t, val, sel, focus, row, col);
                String status = String.valueOf(t.getValueAt(row, 7));
                if ("SUCCESS".equals(status)) {
                    setForeground(new Color(0, 128, 0));
                } else if ("FAILED".equals(status) || "DENIED".equals(status)) {
                    setForeground(new Color(200, 0, 0));
                } else {
                    setForeground(Color.BLACK);
                }
                return c;
            }
        });

        panel.add(new JScrollPane(tableLogs), BorderLayout.CENTER);
        return panel;
    }

    private void refreshDbLogs() {
        new Thread(() -> {
            String kw = txtSearchLog != null ? txtSearchLog.getText() : "";
            String cmd = cbCommandFilter != null ? (String) cbCommandFilter.getSelectedItem() : "ALL";
            List<FtpLogModel> logs = db.getLogs(200, kw, cmd);

            SwingUtilities.invokeLater(() -> {
                modelLogs.setRowCount(0);
                for (FtpLogModel l : logs) {
                    modelLogs.addRow(new Object[]{
                            l.getId(),
                            l.getTimestamp() != null ? l.getTimestamp().toString() : "",
                            l.getUsername(),
                            l.getClientIp(),
                            l.getCommand(),
                            l.getPath(),
                            l.getFileSize(),
                            l.getStatus(),
                            l.getMessage()
                    });
                }
            });
        }).start();
    }

    // ==========================================
    // TAB 5: SETTINGS & DATABASE CONFIG
    // ==========================================
    private JPanel createSettingsTab() {
        JPanel panel = new JPanel(new BorderLayout(15, 15));
        panel.setBorder(new EmptyBorder(15, 15, 15, 15));

        JPanel form = new JPanel(new GridLayout(2, 1, 15, 15));

        // Group 1: MySQL Database Configuration
        JPanel dbGroup = new JPanel(new GridBagLayout());
        dbGroup.setBorder(BorderFactory.createTitledBorder("Cấu hình kết nối MySQL (Docker Container / phpMyAdmin)"));
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(6, 8, 6, 8);
        g.fill = GridBagConstraints.HORIZONTAL;

        g.gridx = 0; g.gridy = 0; dbGroup.add(new JLabel("MySQL Host:"), g);
        txtDbHost = new JTextField(config.getDbHost(), 15);
        g.gridx = 1; g.gridy = 0; dbGroup.add(txtDbHost, g);

        g.gridx = 2; g.gridy = 0; dbGroup.add(new JLabel("Cổng MySQL:"), g);
        txtDbPort = new JTextField(String.valueOf(config.getDbPort()), 8);
        g.gridx = 3; g.gridy = 0; dbGroup.add(txtDbPort, g);

        g.gridx = 0; g.gridy = 1; dbGroup.add(new JLabel("Tên Database:"), g);
        txtDbName = new JTextField(config.getDbName(), 15);
        g.gridx = 1; g.gridy = 1; dbGroup.add(txtDbName, g);

        g.gridx = 2; g.gridy = 1; dbGroup.add(new JLabel("Người dùng DB:"), g);
        txtDbUser = new JTextField(config.getDbUser(), 12);
        g.gridx = 3; g.gridy = 1; dbGroup.add(txtDbUser, g);

        g.gridx = 0; g.gridy = 2; dbGroup.add(new JLabel("Mật khẩu DB:"), g);
        txtDbPass = new JPasswordField(config.getDbPass(), 15);
        g.gridx = 1; g.gridy = 2; dbGroup.add(txtDbPass, g);

        JButton btnTestDb = new JButton("Kiểm tra kết nối MySQL");
        btnTestDb.addActionListener(e -> {
            applyConfigFields();
            boolean ok = db.testConnection();
            if (ok) {
                JOptionPane.showMessageDialog(this, "Kết nối MySQL thành công!\nHost: " + config.getDbHost() + ":" + config.getDbPort(), "Thành công", JOptionPane.INFORMATION_MESSAGE);
                lblDbStatusBadge.setText(" MySQL: Đã kết nối (Port " + config.getDbPort() + ") ");
                lblDbStatusBadge.setBackground(new Color(40, 167, 69));
            } else {
                JOptionPane.showMessageDialog(this, "Không thể kết nối MySQL! Kiểm tra thông số và trạng thái container.", "Thất bại", JOptionPane.ERROR_MESSAGE);
                lblDbStatusBadge.setText(" MySQL: Lỗi kết nối! ");
                lblDbStatusBadge.setBackground(new Color(220, 53, 69));
            }
        });
        g.gridx = 2; g.gridy = 2; g.gridwidth = 2; dbGroup.add(btnTestDb, g);

        form.add(dbGroup);

        // Group 2: FTP Server Parameters
        JPanel ftpGroup = new JPanel(new GridBagLayout());
        ftpGroup.setBorder(BorderFactory.createTitledBorder("Cấu hình giao thức & Tham số máy chủ"));

        g = new GridBagConstraints();
        g.insets = new Insets(6, 8, 6, 8);
        g.fill = GridBagConstraints.HORIZONTAL;

        g.gridx = 0; g.gridy = 0; ftpGroup.add(new JLabel("Cổng Passive tối thiểu (Min):"), g);
        txtPasvMin = new JTextField(String.valueOf(config.getPasvPortMin()), 8);
        g.gridx = 1; g.gridy = 0; ftpGroup.add(txtPasvMin, g);

        g.gridx = 2; g.gridy = 0; ftpGroup.add(new JLabel("Cổng Passive tối đa (Max):"), g);
        txtPasvMax = new JTextField(String.valueOf(config.getPasvPortMax()), 8);
        g.gridx = 3; g.gridy = 0; ftpGroup.add(txtPasvMax, g);

        g.gridx = 0; g.gridy = 1; ftpGroup.add(new JLabel("Số kết nối tối đa:"), g);
        txtMaxClients = new JTextField(String.valueOf(config.getMaxClients()), 8);
        g.gridx = 1; g.gridy = 1; ftpGroup.add(txtMaxClients, g);

        g.gridx = 2; g.gridy = 1; ftpGroup.add(new JLabel("Timeout (giây):"), g);
        txtTimeout = new JTextField(String.valueOf(config.getTimeoutSeconds()), 8);
        g.gridx = 3; g.gridy = 1; ftpGroup.add(txtTimeout, g);

        form.add(ftpGroup);
        panel.add(form, BorderLayout.CENTER);

        // Nút lưu
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 10));
        JButton btnSave = new JButton("Lưu cấu hình");
        btnSave.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnSave.setBackground(new Color(0, 123, 255));
        btnSave.setForeground(Color.WHITE);
        btnSave.setPreferredSize(new Dimension(140, 32));
        btnSave.addActionListener(e -> {
            applyConfigFields();
            config.save();
            JOptionPane.showMessageDialog(this, "Đã lưu cấu hình thành công!");
        });
        bottom.add(btnSave);
        panel.add(bottom, BorderLayout.SOUTH);

        return panel;
    }

    private void applyConfigFields() {
        try {
            config.setDbHost(txtDbHost.getText().trim());
            config.setDbPort(Integer.parseInt(txtDbPort.getText().trim()));
            config.setDbName(txtDbName.getText().trim());
            config.setDbUser(txtDbUser.getText().trim());
            config.setDbPass(new String(txtDbPass.getPassword()).trim());
            config.setPasvPortMin(Integer.parseInt(txtPasvMin.getText().trim()));
            config.setPasvPortMax(Integer.parseInt(txtPasvMax.getText().trim()));
            config.setMaxClients(Integer.parseInt(txtMaxClients.getText().trim()));
            config.setTimeoutSeconds(Integer.parseInt(txtTimeout.getText().trim()));
        } catch (Exception ignored) {}
    }

    // ==========================================
    // TAB 6: BUILT-IN FTP CLIENT (TƯƠNG TỰ FILEZILLA)
    // ==========================================
    private JPanel createClientTab() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));

        // Quick Connect Bar
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 5));
        bar.setBorder(BorderFactory.createTitledBorder("Kết nối nhanh (Quick Connect)"));

        bar.add(new JLabel("Host:"));
        txtClientHost = new JTextField("127.0.0.1", 9);
        bar.add(txtClientHost);

        bar.add(new JLabel("Port:"));
        txtClientPort = new JTextField(String.valueOf(config.getServerPort()), 5);
        bar.add(txtClientPort);

        bar.add(new JLabel("Tài khoản:"));
        txtClientUser = new JTextField("admin", 9);
        bar.add(txtClientUser);

        bar.add(new JLabel("Mật khẩu:"));
        txtClientPass = new JPasswordField("admin123", 9);
        bar.add(txtClientPass);

        btnClientConnect = new JButton("Kết nối");
        btnClientConnect.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnClientConnect.setBackground(new Color(0, 123, 255));
        btnClientConnect.setForeground(Color.WHITE);
        btnClientConnect.addActionListener(e -> toggleClientConnect());
        bar.add(btnClientConnect);

        panel.add(bar, BorderLayout.NORTH);

        // Remote Explorer Panel
        JPanel explorer = new JPanel(new BorderLayout(5, 5));
        explorer.setBorder(BorderFactory.createTitledBorder("Cửa sổ tệp từ xa (Remote File Explorer)"));

        // Path Navigation bar
        JPanel pathBar = new JPanel(new BorderLayout(5, 0));
        JButton btnUp = new JButton("Lên thư mục cha [..]");
        btnUp.addActionListener(e -> clientCdup());
        txtClientPath = new JTextField("/");
        txtClientPath.setEditable(false);
        pathBar.add(btnUp, BorderLayout.WEST);
        pathBar.add(txtClientPath, BorderLayout.CENTER);
        explorer.add(pathBar, BorderLayout.NORTH);

        // Table
        String[] cols = {"Loại", "Tên tệp / thư mục", "Kích thước (Bytes)", "Thời gian sửa đổi", "Quyền hạn"};
        modelClientFiles = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int r, int c) { return false; }
        };
        tableClientFiles = new JTable(modelClientFiles);
        tableClientFiles.setRowHeight(22);
        tableClientFiles.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));

        // Nhấp đúp chuột để mở thư mục
        tableClientFiles.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                if (evt.getClickCount() == 2) {
                    int row = tableClientFiles.getSelectedRow();
                    if (row >= 0) {
                        String type = (String) modelClientFiles.getValueAt(row, 0);
                        String name = (String) modelClientFiles.getValueAt(row, 1);
                        if ("[DIR]".equals(type)) {
                            clientCwd(name);
                        }
                    }
                }
            }
        });
        explorer.add(new JScrollPane(tableClientFiles), BorderLayout.CENTER);

        // Explorer Action Buttons
        JPanel actionRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 5));
        JButton btnUpload = new JButton("Tải file lên (Upload)...");
        btnUpload.addActionListener(e -> clientUpload());

        JButton btnDownload = new JButton("Tải file về (Download)...");
        btnDownload.addActionListener(e -> clientDownload());

        JButton btnNewDir = new JButton("Tạo thư mục mới");
        btnNewDir.addActionListener(e -> clientMakeDir());

        JButton btnRename = new JButton("Đổi tên");
        btnRename.addActionListener(e -> clientRename());

        JButton btnDelete = new JButton("Xóa");
        btnDelete.addActionListener(e -> clientDelete());

        JButton btnRefresh = new JButton("Làm mới danh sách");
        btnRefresh.addActionListener(e -> clientRefreshFiles());

        actionRow.add(btnUpload);
        actionRow.add(btnDownload);
        actionRow.add(btnNewDir);
        actionRow.add(btnRename);
        actionRow.add(btnDelete);
        actionRow.add(btnRefresh);

        explorer.add(actionRow, BorderLayout.SOUTH);
        panel.add(explorer, BorderLayout.CENTER);

        // Status & Progress bar
        JPanel clientFooter = new JPanel(new BorderLayout(5, 5));
        clientProgressBar = new JProgressBar(0, 100);
        clientProgressBar.setStringPainted(true);
        lblClientStatus = new JLabel("Chưa kết nối.");
        clientFooter.add(lblClientStatus, BorderLayout.WEST);
        clientFooter.add(clientProgressBar, BorderLayout.EAST);
        panel.add(clientFooter, BorderLayout.SOUTH);

        return panel;
    }

    private void toggleClientConnect() {
        if (testClient != null && testClient.isConnected()) {
            testClient.disconnect();
            testClient = null;
            btnClientConnect.setText("Kết nối");
            btnClientConnect.setBackground(new Color(0, 123, 255));
            lblClientStatus.setText("Đã ngắt kết nối.");
            modelClientFiles.setRowCount(0);
            txtClientPath.setText("/");
            return;
        }

        String host = txtClientHost.getText().trim();
        int port = Integer.parseInt(txtClientPort.getText().trim());
        String user = txtClientUser.getText().trim();
        String pass = new String(txtClientPass.getPassword());

        new Thread(() -> {
            try {
                SwingUtilities.invokeLater(() -> lblClientStatus.setText("Đang kết nối tới " + host + ":" + port + "..."));
                SimpleFtpClient client = new SimpleFtpClient();
                client.connect(host, port);
                boolean loggedIn = client.login(user, pass);
                if (!loggedIn) {
                    client.disconnect();
                    SwingUtilities.invokeLater(() -> {
                        lblClientStatus.setText("Đăng nhập thất bại: " + client.getLastResponse());
                        JOptionPane.showMessageDialog(this, "Đăng nhập thất bại! Kiểm tra username/password trong MySQL.", "Lỗi Đăng Nhập", JOptionPane.ERROR_MESSAGE);
                    });
                    return;
                }

                testClient = client;
                SwingUtilities.invokeLater(() -> {
                    btnClientConnect.setText("Ngắt kết nối");
                    btnClientConnect.setBackground(new Color(220, 53, 69));
                    lblClientStatus.setText("Đã kết nối với quyền tài khoản: " + user);
                });
                clientRefreshFiles();
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    lblClientStatus.setText("Lỗi kết nối: " + ex.getMessage());
                    JOptionPane.showMessageDialog(this, "Không thể kết nối FTP: " + ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
                });
            }
        }).start();
    }

    private void clientRefreshFiles() {
        if (testClient == null || !testClient.isConnected()) return;
        new Thread(() -> {
            try {
                String pwd = testClient.pwd();
                List<FtpFileItem> files = testClient.listFiles();
                SwingUtilities.invokeLater(() -> {
                    txtClientPath.setText(pwd);
                    modelClientFiles.setRowCount(0);
                    for (FtpFileItem f : files) {
                        modelClientFiles.addRow(new Object[]{
                                f.isDirectory() ? "[DIR]" : "[FILE]",
                                f.getName(),
                                f.isDirectory() ? "" : String.valueOf(f.getSize()),
                                f.getDateStr(),
                                f.getPermissions()
                        });
                    }
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> lblClientStatus.setText("Lỗi đọc danh sách tệp: " + ex.getMessage()));
            }
        }).start();
    }

    private void clientCwd(String dirName) {
        if (testClient == null || !testClient.isConnected()) return;
        new Thread(() -> {
            try {
                if (testClient.cwd(dirName)) {
                    clientRefreshFiles();
                }
            } catch (Exception ignored) {}
        }).start();
    }

    private void clientCdup() {
        if (testClient == null || !testClient.isConnected()) return;
        new Thread(() -> {
            try {
                if (testClient.cdup()) {
                    clientRefreshFiles();
                }
            } catch (Exception ignored) {}
        }).start();
    }

    private void clientUpload() {
        if (testClient == null || !testClient.isConnected()) {
            JOptionPane.showMessageDialog(this, "Vui lòng kết nối trước!");
            return;
        }
        JFileChooser chooser = new JFileChooser();
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File localFile = chooser.getSelectedFile();
            new Thread(() -> {
                try {
                    SwingUtilities.invokeLater(() -> {
                        lblClientStatus.setText("Đang tải lên: " + localFile.getName() + "...");
                        clientProgressBar.setValue(0);
                    });
                    testClient.uploadFile(localFile, localFile.getName(), (transferred, total) -> {
                        if (total > 0) {
                            int pct = (int) (transferred * 100 / total);
                            SwingUtilities.invokeLater(() -> clientProgressBar.setValue(pct));
                        }
                    });
                    SwingUtilities.invokeLater(() -> {
                        lblClientStatus.setText("Tải lên hoàn tất: " + localFile.getName());
                        clientProgressBar.setValue(100);
                    });
                    clientRefreshFiles();
                    refreshDbLogs();
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, "Lỗi tải lên: " + ex.getMessage()));
                }
            }).start();
        }
    }

    private void clientDownload() {
        if (testClient == null || !testClient.isConnected()) {
            JOptionPane.showMessageDialog(this, "Vui lòng kết nối trước!");
            return;
        }
        int row = tableClientFiles.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn một tệp để tải về!");
            return;
        }
        String type = (String) modelClientFiles.getValueAt(row, 0);
        if ("[DIR]".equals(type)) {
            JOptionPane.showMessageDialog(this, "Không thể tải về thư mục. Vui lòng chọn tệp tin!");
            return;
        }
        String filename = (String) modelClientFiles.getValueAt(row, 1);

        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File(filename));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File dest = chooser.getSelectedFile();
            new Thread(() -> {
                try {
                    SwingUtilities.invokeLater(() -> {
                        lblClientStatus.setText("Đang tải về: " + filename + "...");
                        clientProgressBar.setValue(0);
                    });
                    testClient.downloadFile(filename, dest, (transferred, total) -> {
                        if (total > 0) {
                            int pct = (int) (transferred * 100 / total);
                            SwingUtilities.invokeLater(() -> clientProgressBar.setValue(pct));
                        }
                    });
                    SwingUtilities.invokeLater(() -> {
                        lblClientStatus.setText("Tải về hoàn tất: " + filename);
                        clientProgressBar.setValue(100);
                        JOptionPane.showMessageDialog(this, "Đã tải về thành công tại: " + dest.getAbsolutePath());
                    });
                    refreshDbLogs();
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, "Lỗi tải về: " + ex.getMessage()));
                }
            }).start();
        }
    }

    private void clientMakeDir() {
        if (testClient == null || !testClient.isConnected()) return;
        String name = JOptionPane.showInputDialog(this, "Nhập tên thư mục mới:");
        if (name != null && !name.trim().isEmpty()) {
            new Thread(() -> {
                try {
                    testClient.mkd(name.trim());
                    clientRefreshFiles();
                    refreshDbLogs();
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, "Lỗi tạo thư mục: " + ex.getMessage()));
                }
            }).start();
        }
    }

    private void clientRename() {
        if (testClient == null || !testClient.isConnected()) return;
        int row = tableClientFiles.getSelectedRow();
        if (row < 0) return;
        String oldName = (String) modelClientFiles.getValueAt(row, 1);
        String newName = JOptionPane.showInputDialog(this, "Nhập tên mới:", oldName);
        if (newName != null && !newName.trim().isEmpty() && !newName.equals(oldName)) {
            new Thread(() -> {
                try {
                    testClient.rename(oldName, newName.trim());
                    clientRefreshFiles();
                    refreshDbLogs();
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, "Lỗi đổi tên: " + ex.getMessage()));
                }
            }).start();
        }
    }

    private void clientDelete() {
        if (testClient == null || !testClient.isConnected()) return;
        int row = tableClientFiles.getSelectedRow();
        if (row < 0) return;
        String type = (String) modelClientFiles.getValueAt(row, 0);
        String name = (String) modelClientFiles.getValueAt(row, 1);

        int confirm = JOptionPane.showConfirmDialog(this, "Bạn có chắc chắn muốn xóa " + name + "?", "Xác nhận", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            new Thread(() -> {
                try {
                    if ("[DIR]".equals(type)) {
                        testClient.rmd(name);
                    } else {
                        testClient.dele(name);
                    }
                    clientRefreshFiles();
                    refreshDbLogs();
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, "Lỗi xóa: " + ex.getMessage()));
                }
            }).start();
        }
    }

    // ==========================================
    // FTP EVENT LISTENER IMPLEMENTATION
    // ==========================================
    @Override
    public void onServerStatusChanged(boolean running, int port, String message) {
        SwingUtilities.invokeLater(() -> {
            if (running) {
                lblStatusBadge.setText(" ĐANG CHẠY (PORT " + port + ") ");
                lblStatusBadge.setBackground(new Color(40, 167, 69));
                btnStartStop.setText("Dừng Server");
                btnStartStop.setBackground(new Color(220, 53, 69));
                spinPort.setEnabled(false);
            } else {
                lblStatusBadge.setText(" ĐÃ DỪNG ");
                lblStatusBadge.setBackground(new Color(108, 117, 125));
                btnStartStop.setText("Khởi động Server");
                btnStartStop.setBackground(new Color(40, 167, 69));
                spinPort.setEnabled(true);
            }
            appendLiveLog("[SYSTEM] " + message);
        });
    }

    @Override
    public void onClientConnected(FtpSession session) {
        SwingUtilities.invokeLater(() -> {
            updateActiveSessionsTable();
            appendLiveLog("[CONNECT] Client kết nối từ: " + session.getClientIp() + ":" + session.getClientPort());
        });
    }

    @Override
    public void onClientDisconnected(FtpSession session) {
        SwingUtilities.invokeLater(() -> {
            updateActiveSessionsTable();
            appendLiveLog("[DISCONNECT] Client ngắt kết nối: " + session.getClientIp() + " (Tài khoản: " + session.getUsername() + ")");
        });
    }

    @Override
    public void onCommand(FtpSession session, String commandLine) {
        appendLiveLog("[" + session.getClientIp() + ":" + session.getClientPort() + "] < " + commandLine);
        SwingUtilities.invokeLater(this::updateActiveSessionsTable);
    }

    @Override
    public void onResponse(FtpSession session, String responseLine) {
        appendLiveLog("[" + session.getClientIp() + ":" + session.getClientPort() + "] > " + responseLine);
    }

    @Override
    public void onTransfer(FtpSession session, String action, String filename, long bytesTransferred, boolean success) {
        totalTransfersCount++;
        SwingUtilities.invokeLater(() -> {
            lblCardTransfers.setText(String.valueOf(totalTransfersCount));
            refreshDbLogs();
        });
    }
}
