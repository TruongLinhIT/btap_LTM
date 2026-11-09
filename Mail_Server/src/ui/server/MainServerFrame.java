package ui.server;

import client.MailClientService;
import config.MailConfig;
import database.DatabaseManager;
import database.MailRecord;
import database.UserRecord;
import server.MailServerManager;
import server.ServerListener;
import ui.client.LoginFrame;
import ui.client.MailClientFrame;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.List;

/**
 * Giao diện Quản trị Trung tâm của Mail Server (LTM Mail Server Manager):
 * - Giám sát trạng thái máy chủ SMTP (cổng 2525) & POP3 (cổng 1100).
 * - Tự động liên kết MySQL Database (container ltm-mysql trên cổng 3308).
 * - Nhật ký socket trực tiếp (SMTP commands: HELO, AUTH, MAIL FROM, RCPT TO, DATA; POP3: USER, PASS, STAT, LIST, RETR, DELE).
 * - Quản lý tài khoản người dùng và hòm thư trong Database.
 * - Cấu hình cổng kết nối Gmail (Relay SMTP ra ngoài & Lấy thư mới qua POP3).
 * - Khởi chạy nhanh trình khách Mail Client tương tự Gmail.
 */
public class MainServerFrame extends JFrame implements ServerListener {
    private final MailServerManager manager = MailServerManager.getInstance();
    private final MailConfig config = MailConfig.getInstance();
    private final DatabaseManager db = DatabaseManager.getInstance();

    // Header components
    private final JLabel lblStatusBadge = new JLabel("● ĐÃ DỪNG");
    private final JLabel lblDbBadge = new JLabel("DATABASE: ĐANG KIỂM TRA");
    private final JSpinner spinSmtpPort = new JSpinner(new SpinnerNumberModel(2525, 1, 65535, 1));
    private final JSpinner spinPop3Port = new JSpinner(new SpinnerNumberModel(1100, 1, 65535, 1));
    private final JButton btnStartStop = new JButton("▶ Khởi động máy chủ");
    private final JButton btnOpenClient = new JButton("✉ Mở Mail Client");
    private final JButton btnOpenPma = new JButton("🌐 phpMyAdmin (8085)");

    // Tab 1: Dashboard
    private final JLabel lblCardUsers = new JLabel("0", SwingConstants.CENTER);
    private final JLabel lblCardEmails = new JLabel("0", SwingConstants.CENTER);
    private final JLabel lblCardRelayed = new JLabel("0", SwingConstants.CENTER);
    private final JLabel lblCardFetched = new JLabel("0", SwingConstants.CENTER);
    private final DefaultTableModel modelDashEmails;
    private final JTable tableDashEmails;

    // Tab 2: Live Log
    private final JTextArea txtLiveLog = new JTextArea();
    private final JCheckBox chkAutoScroll = new JCheckBox("Tự động cuộn", true);

    // Tab 3: Users
    private final DefaultTableModel modelUsers;
    private final JTable tableUsers;

    // Tab 4: Database Emails & Logs
    private final DefaultTableModel modelDbEmails;
    private final JTable tableDbEmails;
    private final JTextField txtSearchDbEmail = new JTextField();
    private final DefaultTableModel modelDbLogs;
    private final JTable tableDbLogs;

    // Tab 5: Gmail Gateway Settings
    private final JCheckBox chkGmailEnabled = new JCheckBox("Bật chuyển tiếp (Relay) qua Gmail khi gửi tới địa chỉ ngoài");
    private final JTextField txtGmailUser = new JTextField();
    private final JPasswordField txtGmailAppPass = new JPasswordField();
    private final JTextField txtGmailSmtpHost = new JTextField();
    private final JTextField txtGmailSmtpPort = new JTextField();
    private final JTextField txtGmailPopHost = new JTextField();
    private final JTextField txtGmailPopPort = new JTextField();
    private final JCheckBox chkGmailFetch = new JCheckBox("Tự động nhận thư mới từ Gmail về Server (POP3 SSL)");
    private final JTextField txtGmailFetchInterval = new JTextField();
    private final JTextField txtGmailDefaultUser = new JTextField();

    // Tab 6: System & MySQL Settings
    private final JTextField txtDomain = new JTextField();
    private final JTextField txtDbHost = new JTextField();
    private final JTextField txtDbPort = new JTextField();
    private final JTextField txtDbName = new JTextField();
    private final JTextField txtDbUser = new JTextField();
    private final JPasswordField txtDbPass = new JPasswordField();

    public MainServerFrame() {
        super("LTM Mail Server Manager (SMTP + POP3 + MySQL + Gmail)");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1180, 760);
        setMinimumSize(new Dimension(980, 620));
        setLocationRelativeTo(null);

        // Khởi tạo bảng Dashboard
        modelDashEmails = new DefaultTableModel(new String[]{"ID", "Chủ hộp thư", "Thư mục", "Người gửi", "Người nhận", "Tiêu đề", "Nguồn", "Trạng thái", "Thời gian"}, 0);
        tableDashEmails = new JTable(modelDashEmails);

        // Khởi tạo bảng Users
        modelUsers = new DefaultTableModel(new String[]{"ID", "Tên đăng nhập", "Họ và tên", "Hộp thư đến", "Chưa đọc", "Kích hoạt", "Ngày tạo"}, 0);
        tableUsers = new JTable(modelUsers);

        // Khởi tạo bảng DB Emails
        modelDbEmails = new DefaultTableModel(new String[]{"ID", "Chủ hộp thư", "Thư mục", "Người gửi", "Người nhận", "Tiêu đề", "Kích thước", "Đính kèm", "Nguồn", "Thời gian"}, 0);
        tableDbEmails = new JTable(modelDbEmails);

        // Khởi tạo bảng DB Logs
        modelDbLogs = new DefaultTableModel(new String[]{"Thời gian", "Giao thức", "Client IP", "Người dùng", "Lệnh", "Trạng thái", "Chi tiết"}, 0);
        tableDbLogs = new JTable(modelDbLogs);

        initUi();
        setupEvents();

        manager.addListener(this);

        // Tự động kiểm tra và khởi động máy chủ
        initDatabaseAndAutoStart();
    }

    private void initUi() {
        JPanel root = new JPanel(new BorderLayout());

        // ================= HEADER =================
        JPanel pnlHeader = new JPanel(new BorderLayout(10, 0));
        pnlHeader.setBackground(new Color(0x1F, 0x29, 0x37));
        pnlHeader.setBorder(new EmptyBorder(10, 16, 10, 16));

        // Trái: Tiêu đề + Trạng thái
        JPanel pnlHeaderLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        pnlHeaderLeft.setOpaque(false);

        JLabel lblTitle = new JLabel("LTM MAIL SERVER");
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 18));
        lblTitle.setForeground(Color.WHITE);

        lblStatusBadge.setOpaque(true);
        lblStatusBadge.setBackground(new Color(0xEF, 0x44, 0x44));
        lblStatusBadge.setForeground(Color.WHITE);
        lblStatusBadge.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblStatusBadge.setBorder(new EmptyBorder(4, 10, 4, 10));

        lblDbBadge.setOpaque(true);
        lblDbBadge.setBackground(new Color(0x37, 0x41, 0x51));
        lblDbBadge.setForeground(Color.WHITE);
        lblDbBadge.setFont(new Font("Segoe UI", Font.BOLD, 11));
        lblDbBadge.setBorder(new EmptyBorder(4, 8, 4, 8));

        pnlHeaderLeft.add(lblTitle);
        pnlHeaderLeft.add(lblStatusBadge);
        pnlHeaderLeft.add(lblDbBadge);

        // Phải: Cổng + Nút bấm điều khiển
        JPanel pnlHeaderRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        pnlHeaderRight.setOpaque(false);

        JLabel lblSmtp = new JLabel("SMTP:");
        lblSmtp.setForeground(Color.LIGHT_GRAY);
        spinSmtpPort.setPreferredSize(new Dimension(65, 26));

        JLabel lblPop3 = new JLabel("POP3:");
        lblPop3.setForeground(Color.LIGHT_GRAY);
        spinPop3Port.setPreferredSize(new Dimension(65, 26));

        btnStartStop.setBackground(new Color(0x10, 0xB9, 0x81));
        btnStartStop.setForeground(Color.WHITE);
        btnStartStop.setFont(btnStartStop.getFont().deriveFont(Font.BOLD, 12f));

        btnOpenClient.setBackground(new Color(0x3B, 0x82, 0xF6));
        btnOpenClient.setForeground(Color.WHITE);
        btnOpenClient.setFont(btnOpenClient.getFont().deriveFont(Font.BOLD, 12f));

        btnOpenPma.setBackground(new Color(0x4B, 0x55, 0x63));
        btnOpenPma.setForeground(Color.WHITE);
        btnOpenPma.setFont(btnOpenPma.getFont().deriveFont(Font.PLAIN, 12f));

        pnlHeaderRight.add(lblSmtp);
        pnlHeaderRight.add(spinSmtpPort);
        pnlHeaderRight.add(lblPop3);
        pnlHeaderRight.add(spinPop3Port);
        pnlHeaderRight.add(btnStartStop);
        pnlHeaderRight.add(btnOpenClient);
        pnlHeaderRight.add(btnOpenPma);

        pnlHeader.add(pnlHeaderLeft, BorderLayout.WEST);
        pnlHeader.add(pnlHeaderRight, BorderLayout.EAST);
        root.add(pnlHeader, BorderLayout.NORTH);

        // ================= CENTER: TABBED PANE =================
        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(new Font("Segoe UI", Font.PLAIN, 13));

        tabs.addTab("📊 Tổng quan (Dashboard)", createDashboardTab());
        tabs.addTab("📡 Nhật ký Socket (Live Log)", createLiveLogTab());
        tabs.addTab("👥 Quản lý người dùng", createUsersTab());
        tabs.addTab("🗄 Thư trong Database & Logs", createDatabaseEmailsTab());
        tabs.addTab("⚙ Cấu hình Gmail Gateway", createGmailSettingsTab());
        tabs.addTab("🔧 Cấu hình MySQL & Hệ thống", createSystemSettingsTab());

        root.add(tabs, BorderLayout.CENTER);

        // Cập nhật giá trị vào các form cấu hình
        syncConfigToForm();

        setContentPane(root);
    }

    // ================== TAB 1: DASHBOARD ==================
    private JPanel createDashboardTab() {
        JPanel p = new JPanel(new BorderLayout(0, 12));
        p.setBorder(new EmptyBorder(16, 16, 16, 16));
        p.setBackground(new Color(0xF9, 0xFA, 0xFB));

        // 4 Thẻ thống kê
        JPanel pnlCards = new JPanel(new GridLayout(1, 4, 12, 0));
        pnlCards.setOpaque(false);
        pnlCards.setPreferredSize(new Dimension(0, 85));

        pnlCards.add(createCard("TÀI KHOẢN NGƯỜI DÙNG", lblCardUsers, new Color(0x3B, 0x82, 0xF6)));
        pnlCards.add(createCard("TỔNG SỐ THƯ TRONG DB", lblCardEmails, new Color(0x10, 0xB9, 0x81)));
        pnlCards.add(createCard("ĐÃ RELAY QUA GMAIL", lblCardRelayed, new Color(0xF5, 0x9E, 0x0B)));
        pnlCards.add(createCard("NHẬN TỪ GMAIL VỀ", lblCardFetched, new Color(0x8B, 0x5C, 0xF6)));

        p.add(pnlCards, BorderLayout.NORTH);

        // Bảng thư mới nhất
        JPanel pnlRecent = new JPanel(new BorderLayout(0, 8));
        pnlRecent.setOpaque(false);

        JPanel pnlRecentHeader = new JPanel(new BorderLayout());
        pnlRecentHeader.setOpaque(false);
        JLabel lblRecentTitle = new JLabel("Các bức thư lưu trữ trong Database (Gần đây nhất)");
        lblRecentTitle.setFont(new Font("Segoe UI", Font.BOLD, 14));

        JButton btnRefreshDash = new JButton("🔄 Làm mới");
        btnRefreshDash.addActionListener(e -> refreshAllData());

        pnlRecentHeader.add(lblRecentTitle, BorderLayout.WEST);
        pnlRecentHeader.add(btnRefreshDash, BorderLayout.EAST);
        pnlRecent.add(pnlRecentHeader, BorderLayout.NORTH);

        tableDashEmails.setRowHeight(26);
        pnlRecent.add(new JScrollPane(tableDashEmails), BorderLayout.CENTER);

        p.add(pnlRecent, BorderLayout.CENTER);
        return p;
    }

    private JPanel createCard(String title, JLabel lblValue, Color accent) {
        JPanel c = new JPanel(new BorderLayout(0, 6));
        c.setBackground(Color.WHITE);
        c.setBorder(new CompoundBorder(
                BorderFactory.createMatteBorder(0, 4, 0, 0, accent),
                new EmptyBorder(10, 14, 10, 14)
        ));
        JLabel lblTitle = new JLabel(title);
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 11));
        lblTitle.setForeground(new Color(0x6B, 0x72, 0x80));

        lblValue.setFont(new Font("Segoe UI", Font.BOLD, 26));
        lblValue.setForeground(new Color(0x11, 0x18, 0x27));

        c.add(lblTitle, BorderLayout.NORTH);
        c.add(lblValue, BorderLayout.CENTER);
        return c;
    }

    // ================== TAB 2: LIVE LOG ==================
    private JPanel createLiveLogTab() {
        JPanel p = new JPanel(new BorderLayout(0, 8));
        p.setBorder(new EmptyBorder(12, 16, 12, 16));
        p.setBackground(Color.WHITE);

        JPanel pnlTop = new JPanel(new BorderLayout());
        pnlTop.setOpaque(false);
        JLabel lbl = new JLabel("Nhật ký thời gian thực các lệnh SMTP & POP3 đi qua Socket Server:");
        lbl.setFont(new Font("Segoe UI", Font.BOLD, 13));

        JPanel pnlBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        pnlBtns.setOpaque(false);
        JButton btnClear = new JButton("Xóa log");
        btnClear.addActionListener(e -> txtLiveLog.setText(""));
        pnlBtns.add(chkAutoScroll);
        pnlBtns.add(btnClear);

        pnlTop.add(lbl, BorderLayout.WEST);
        pnlTop.add(pnlBtns, BorderLayout.EAST);
        p.add(pnlTop, BorderLayout.NORTH);

        txtLiveLog.setEditable(false);
        txtLiveLog.setBackground(new Color(0x1E, 0x29, 0x3B));
        txtLiveLog.setForeground(new Color(0x34, 0xD3, 0x99));
        txtLiveLog.setFont(new Font("Consolas", Font.PLAIN, 13));
        txtLiveLog.setBorder(new EmptyBorder(8, 8, 8, 8));

        p.add(new JScrollPane(txtLiveLog), BorderLayout.CENTER);
        return p;
    }

    // ================== TAB 3: USERS ==================
    private JPanel createUsersTab() {
        JPanel p = new JPanel(new BorderLayout(0, 10));
        p.setBorder(new EmptyBorder(14, 16, 14, 16));
        p.setBackground(Color.WHITE);

        JPanel pnlTop = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        pnlTop.setOpaque(false);

        JButton btnAddUser = new JButton("➕ Thêm người dùng");
        JButton btnEditPass = new JButton("🔑 Đổi mật khẩu");
        JButton btnToggleActive = new JButton("🔒 Khóa / Mở");
        JButton btnDelUser = new JButton("🗑 Xóa");
        JButton btnRefreshUsers = new JButton("🔄 Làm mới");
        JButton btnLaunchClientAsUser = new JButton("🚀 Mở Mail Client với User này");

        pnlTop.add(btnAddUser);
        pnlTop.add(btnEditPass);
        pnlTop.add(btnToggleActive);
        pnlTop.add(btnDelUser);
        pnlTop.add(btnRefreshUsers);
        pnlTop.add(Box.createHorizontalStrut(15));
        pnlTop.add(btnLaunchClientAsUser);

        p.add(pnlTop, BorderLayout.NORTH);

        tableUsers.setRowHeight(26);
        p.add(new JScrollPane(tableUsers), BorderLayout.CENTER);

        btnAddUser.addActionListener(e -> {
            UserDialog d = new UserDialog(this, null);
            d.setVisible(true);
            if (d.isSaved()) refreshUsers();
        });

        btnEditPass.addActionListener(e -> {
            String u = getSelectedUsername();
            if (u != null) {
                UserDialog d = new UserDialog(this, u);
                d.setVisible(true);
                if (d.isSaved()) refreshUsers();
            }
        });

        btnToggleActive.addActionListener(e -> toggleUserActive());
        btnDelUser.addActionListener(e -> deleteSelectedUser());
        btnRefreshUsers.addActionListener(e -> refreshUsers());
        btnLaunchClientAsUser.addActionListener(e -> launchClientForSelectedUser());

        return p;
    }

    private String getSelectedUsername() {
        int r = tableUsers.getSelectedRow();
        if (r < 0) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn một người dùng trong bảng!");
            return null;
        }
        return (String) tableUsers.getValueAt(r, 1);
    }

    private void toggleUserActive() {
        String u = getSelectedUsername();
        if (u == null) return;
        int r = tableUsers.getSelectedRow();
        boolean current = Boolean.TRUE.equals(tableUsers.getValueAt(r, 5));
        try {
            db.setActive(u, !current);
            refreshUsers();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Lỗi: " + ex.getMessage());
        }
    }

    private void deleteSelectedUser() {
        String u = getSelectedUsername();
        if (u == null) return;
        if (u.equals("admin")) {
            JOptionPane.showMessageDialog(this, "Không được xóa tài khoản quản trị viên admin!");
            return;
        }
        int ans = JOptionPane.showConfirmDialog(this, "Bạn có chắc chắn muốn xóa tài khoản " + u + " và toàn bộ thư liên quan?",
                "Xác nhận xóa", JOptionPane.YES_NO_OPTION);
        if (ans == JOptionPane.YES_OPTION) {
            try {
                db.deleteUser(u);
                refreshUsers();
                refreshDashboard();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Lỗi xóa: " + ex.getMessage());
            }
        }
    }

    private void launchClientForSelectedUser() {
        String u = getSelectedUsername();
        if (u == null) return;
        String pass = JOptionPane.showInputDialog(this, "Nhập mật khẩu của " + u + ":",
                u.equals("admin") ? "admin123" : "123456");
        if (pass == null || pass.isBlank()) return;

        int smtp = (int) spinSmtpPort.getValue();
        int pop = (int) spinPop3Port.getValue();
        MailClientService svc = new MailClientService("localhost", smtp, pop, u + "@" + config.getDomain(), pass);
        svc.setFullName(db.getFullName(u));
        new SwingWorker<Integer, Void>() {
            @Override
            protected Integer doInBackground() throws Exception {
                return svc.login();
            }

            @Override
            protected void done() {
                try {
                    get();
                    new MailClientFrame(svc).setVisible(true);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(MainServerFrame.this, "Đăng nhập thất bại: " + ex.getMessage());
                }
            }
        }.execute();
    }

    // ================== TAB 4: DATABASE EMAILS & LOGS ==================
    private JPanel createDatabaseEmailsTab() {
        JPanel p = new JPanel(new BorderLayout(0, 10));
        p.setBorder(new EmptyBorder(12, 16, 12, 16));
        p.setBackground(Color.WHITE);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        split.setDividerLocation(320);

        // Nửa trên: Bảng thư trong DB
        JPanel pnlTopEmails = new JPanel(new BorderLayout(0, 8));
        pnlTopEmails.setOpaque(false);

        JPanel pnlSearch = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        pnlSearch.setOpaque(false);
        txtSearchDbEmail.setPreferredSize(new Dimension(240, 28));
        JButton btnSearchEmail = new JButton("🔍 Tìm");
        JButton btnViewRaw = new JButton("👁 Xem Raw RFC822");
        JButton btnDeleteDbEmail = new JButton("🗑 Xóa thư");
        JButton btnRefreshDbEmail = new JButton("🔄 Làm mới");

        pnlSearch.add(new JLabel("Tìm kiếm thư trong DB:"));
        pnlSearch.add(txtSearchDbEmail);
        pnlSearch.add(btnSearchEmail);
        pnlSearch.add(btnViewRaw);
        pnlSearch.add(btnDeleteDbEmail);
        pnlSearch.add(btnRefreshDbEmail);

        pnlTopEmails.add(pnlSearch, BorderLayout.NORTH);
        tableDbEmails.setRowHeight(24);
        pnlTopEmails.add(new JScrollPane(tableDbEmails), BorderLayout.CENTER);
        split.setTopComponent(pnlTopEmails);

        // Nửa dưới: Bảng Audit Log (mail_logs)
        JPanel pnlBottomLogs = new JPanel(new BorderLayout(0, 6));
        pnlBottomLogs.setOpaque(false);
        JLabel lblLogTitle = new JLabel("Nhật ký kiểm toán lưu trong Database (bảng mail_logs):");
        lblLogTitle.setFont(new Font("Segoe UI", Font.BOLD, 12));
        pnlBottomLogs.add(lblLogTitle, BorderLayout.NORTH);

        tableDbLogs.setRowHeight(22);
        pnlBottomLogs.add(new JScrollPane(tableDbLogs), BorderLayout.CENTER);
        split.setBottomComponent(pnlBottomLogs);

        p.add(split, BorderLayout.CENTER);

        btnSearchEmail.addActionListener(e -> searchDbEmails());
        btnRefreshDbEmail.addActionListener(e -> {
            txtSearchDbEmail.setText("");
            searchDbEmails();
            refreshDbLogs();
        });
        btnViewRaw.addActionListener(e -> viewRawSelectedEmail());
        btnDeleteDbEmail.addActionListener(e -> deleteSelectedDbEmail());

        return p;
    }

    private void searchDbEmails() {
        String kw = txtSearchDbEmail.getText().trim();
        new SwingWorker<List<MailRecord>, Void>() {
            @Override
            protected List<MailRecord> doInBackground() throws Exception {
                return db.listAllEmails(kw, 200);
            }

            @Override
            protected void done() {
                try {
                    List<MailRecord> list = get();
                    modelDbEmails.setRowCount(0);
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                    for (MailRecord m : list) {
                        modelDbEmails.addRow(new Object[]{
                                m.id, m.owner, m.folder, m.sender, m.recipients, m.subject,
                                m.sizeBytes + " B", m.hasAttachment ? "Có" : "Không", m.source,
                                m.createdAt != null ? sdf.format(m.createdAt) : ""
                        });
                    }
                } catch (Exception ex) {
                    // ignored
                }
            }
        }.execute();
    }

    private void refreshDbLogs() {
        new SwingWorker<List<String[]>, Void>() {
            @Override
            protected List<String[]> doInBackground() throws Exception {
                return db.listLogs(100);
            }

            @Override
            protected void done() {
                try {
                    List<String[]> list = get();
                    modelDbLogs.setRowCount(0);
                    for (String[] row : list) modelDbLogs.addRow(row);
                } catch (Exception ignored) {}
            }
        }.execute();
    }

    private void viewRawSelectedEmail() {
        int r = tableDbEmails.getSelectedRow();
        if (r < 0) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn một bức thư!");
            return;
        }
        long id = (long) tableDbEmails.getValueAt(r, 0);
        try {
            String raw = db.getRawMessage(id);
            if (raw == null) {
                JOptionPane.showMessageDialog(this, "Không tìm thấy nội dung!");
                return;
            }
            JDialog dlg = new JDialog(this, "Nội dung thô (RFC 822) – Mail ID: " + id, false);
            dlg.setSize(750, 520);
            dlg.setLocationRelativeTo(this);
            JTextArea ta = new JTextArea(raw);
            ta.setEditable(false);
            ta.setFont(new Font("Consolas", Font.PLAIN, 12));
            ta.setCaretPosition(0);
            dlg.add(new JScrollPane(ta));
            dlg.setVisible(true);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Lỗi đọc thư: " + ex.getMessage());
        }
    }

    private void deleteSelectedDbEmail() {
        int r = tableDbEmails.getSelectedRow();
        if (r < 0) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn một bức thư cần xóa!");
            return;
        }
        long id = (long) tableDbEmails.getValueAt(r, 0);
        int ans = JOptionPane.showConfirmDialog(this, "Xóa vĩnh viễn thư ID " + id + " khỏi Database?", "Xác nhận", JOptionPane.YES_NO_OPTION);
        if (ans == JOptionPane.YES_OPTION) {
            try {
                db.deleteEmail(id);
                searchDbEmails();
                refreshDashboard();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Lỗi xóa thư: " + ex.getMessage());
            }
        }
    }

    // ================== TAB 5: GMAIL SETTINGS ==================
    private JPanel createGmailSettingsTab() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(Color.WHITE);

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(new EmptyBorder(20, 24, 20, 24));
        form.setOpaque(false);
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(6, 6, 6, 6);
        g.fill = GridBagConstraints.HORIZONTAL;

        int row = 0;
        g.gridx = 0; g.gridy = row; g.gridwidth = 2;
        chkGmailEnabled.setFont(new Font("Segoe UI", Font.BOLD, 13));
        form.add(chkGmailEnabled, g);
        row++;

        g.gridwidth = 1;
        row = addFormRow(form, g, row, "Địa chỉ Gmail của bạn:", txtGmailUser, "Ví dụ: tenban@gmail.com");
        row = addFormRow(form, g, row, "Mật khẩu ứng dụng (App Password):", txtGmailAppPass, "Mật khẩu 16 chữ số tạo trong Google Account");
        row = addFormRow(form, g, row, "Gmail SMTP Host / Port:", createPairPanel(txtGmailSmtpHost, txtGmailSmtpPort), "smtp.gmail.com : 465 (SSL)");
        row = addFormRow(form, g, row, "Gmail POP3 Host / Port:", createPairPanel(txtGmailPopHost, txtGmailPopPort), "pop.gmail.com : 995 (SSL)");

        g.gridx = 0; g.gridy = row; g.gridwidth = 2;
        chkGmailFetch.setFont(new Font("Segoe UI", Font.BOLD, 13));
        form.add(chkGmailFetch, g);
        row++;

        g.gridwidth = 1;
        row = addFormRow(form, g, row, "Chu kỳ lấy thư Gmail (giây):", txtGmailFetchInterval, "Mặc định: 60 giây");
        row = addFormRow(form, g, row, "User nhận thư mặc định:", txtGmailDefaultUser, "admin");

        // Khu vực nút bấm kiểm tra và lưu
        JPanel pnlBtns = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        pnlBtns.setOpaque(false);
        JButton btnTestGmail = new JButton("🔍 Kiểm tra kết nối Gmail ngay");
        JButton btnFetchNow = new JButton("📥 Tải thư từ Gmail về ngay");
        JButton btnSaveGmail = new JButton("💾 Lưu cấu hình Gmail");
        btnSaveGmail.setBackground(new Color(0x10, 0xB9, 0x81));
        btnSaveGmail.setForeground(Color.WHITE);
        btnSaveGmail.setFont(btnSaveGmail.getFont().deriveFont(Font.BOLD));

        pnlBtns.add(btnTestGmail);
        pnlBtns.add(btnFetchNow);
        pnlBtns.add(btnSaveGmail);

        g.gridx = 0; g.gridy = row; g.gridwidth = 2;
        form.add(pnlBtns, g);
        row++;

        // Hướng dẫn tạo App Password
        JLabel lblGuide = new JLabel("<html><div style='background:#EFF6FF;padding:12px;border:1px solid #BFDBFE;border-radius:4px;'>"
                + "<b>💡 Hướng dẫn tạo Mật khẩu ứng dụng (App Password) cho Gmail:</b><br>"
                + "1. Truy cập tài khoản Google của bạn: <font color='#1D4ED8'>myaccount.google.com/security</font><br>"
                + "2. Bật <b>Xác minh 2 bước</b> (2-Step Verification) nếu chưa bật.<br>"
                + "3. Tìm mục <b>Mật khẩu ứng dụng (App passwords)</b> và tạo một mật khẩu mới cho ứng dụng 'Mail'.<br>"
                + "4. Dán mật khẩu 16 chữ số vào ô trên và bấm <b>Lưu cấu hình Gmail</b>.<br>"
                + "5. Để nhận được thư qua POP3, vào Gmail > Cài đặt > <i>Chuyển tiếp và POP/IMAP</i> > <b>Bật POP cho tất cả thư</b>.</div></html>");
        g.gridx = 0; g.gridy = row; g.gridwidth = 2;
        form.add(lblGuide, g);

        p.add(new JScrollPane(form), BorderLayout.CENTER);

        btnSaveGmail.addActionListener(e -> saveGmailConfig());
        btnTestGmail.addActionListener(e -> testGmailConnection());
        btnFetchNow.addActionListener(e -> fetchGmailNow());

        return p;
    }

    private void saveGmailConfig() {
        config.setGmailEnabled(chkGmailEnabled.isSelected());
        config.setGmailUser(txtGmailUser.getText().trim());
        config.setGmailAppPassword(new String(txtGmailAppPass.getPassword()).trim());
        config.setGmailSmtpHost(txtGmailSmtpHost.getText().trim());
        try { config.setGmailSmtpPort(Integer.parseInt(txtGmailSmtpPort.getText().trim())); } catch (Exception ignored) {}
        config.setGmailPop3Host(txtGmailPopHost.getText().trim());
        try { config.setGmailPop3Port(Integer.parseInt(txtGmailPopPort.getText().trim())); } catch (Exception ignored) {}
        config.setGmailFetchEnabled(chkGmailFetch.isSelected());
        try { config.setGmailFetchIntervalSec(Integer.parseInt(txtGmailFetchInterval.getText().trim())); } catch (Exception ignored) {}
        config.setGmailDefaultUser(txtGmailDefaultUser.getText().trim());
        config.save();
        JOptionPane.showMessageDialog(this, "Đã lưu cấu hình Gmail thành công!");
    }

    private void testGmailConnection() {
        saveGmailConfig();
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() {
                return manager.getGmailGateway().testConnection();
            }

            @Override
            protected void done() {
                setCursor(Cursor.getDefaultCursor());
                try {
                    String res = get();
                    JOptionPane.showMessageDialog(MainServerFrame.this, res, "Kết quả kiểm tra Gmail", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(MainServerFrame.this, "Lỗi kiểm tra: " + ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void fetchGmailNow() {
        saveGmailConfig();
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        new SwingWorker<Integer, Void>() {
            @Override
            protected Integer doInBackground() throws Exception {
                return manager.getGmailGateway().fetch();
            }

            @Override
            protected void done() {
                setCursor(Cursor.getDefaultCursor());
                try {
                    int n = get();
                    JOptionPane.showMessageDialog(MainServerFrame.this, "Đã lấy " + n + " thư mới từ Gmail!", "Thông báo", JOptionPane.INFORMATION_MESSAGE);
                    refreshAllData();
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(MainServerFrame.this, "Lỗi lấy thư Gmail: " + ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    // ================== TAB 6: SYSTEM & MYSQL SETTINGS ==================
    private JPanel createSystemSettingsTab() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(Color.WHITE);

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(new EmptyBorder(20, 24, 20, 24));
        form.setOpaque(false);
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(6, 6, 6, 6);
        g.fill = GridBagConstraints.HORIZONTAL;

        int row = 0;
        row = addFormRow(form, g, row, "Tên miền (Domain Mail):", txtDomain, "Mặc định: localhost");
        row = addFormRow(form, g, row, "MySQL Host:", txtDbHost, "localhost");
        row = addFormRow(form, g, row, "MySQL Port (Container ltm-mysql):", txtDbPort, "3308");
        row = addFormRow(form, g, row, "Tên Database (Database Name):", txtDbName, "mail_db");
        row = addFormRow(form, g, row, "MySQL User:", txtDbUser, "root");
        row = addFormRow(form, g, row, "MySQL Password:", txtDbPass, "root123");

        JPanel pnlBtns = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        pnlBtns.setOpaque(false);
        JButton btnTestDb = new JButton("🔌 Kiểm tra kết nối MySQL");
        JButton btnInitDb = new JButton("⚡ Khởi tạo lại Database (Init DB)");
        JButton btnSaveSys = new JButton("💾 Lưu cấu hình hệ thống");
        btnSaveSys.setBackground(new Color(0x10, 0xB9, 0x81));
        btnSaveSys.setForeground(Color.WHITE);
        btnSaveSys.setFont(btnSaveSys.getFont().deriveFont(Font.BOLD));

        pnlBtns.add(btnTestDb);
        pnlBtns.add(btnInitDb);
        pnlBtns.add(btnSaveSys);

        g.gridx = 0; g.gridy = row; g.gridwidth = 2;
        form.add(pnlBtns, g);

        p.add(new JScrollPane(form), BorderLayout.CENTER);

        btnTestDb.addActionListener(e -> {
            saveSystemConfig();
            boolean ok = db.testConnection();
            JOptionPane.showMessageDialog(this, ok ? "Kết nối MySQL THÀNH CÔNG!" : "KHÔNG THỂ kết nối tới MySQL!\nHãy đảm bảo container ltm-mysql đang chạy trên cổng " + config.getDbPort(),
                    "Kiểm tra MySQL", ok ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.ERROR_MESSAGE);
            updateDbBadge(ok);
        });

        btnInitDb.addActionListener(e -> {
            saveSystemConfig();
            try {
                db.init();
                JOptionPane.showMessageDialog(this, "Đã khởi tạo Database mail_db thành công!");
                updateDbBadge(true);
                refreshAllData();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Lỗi khởi tạo DB: " + ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
            }
        });

        btnSaveSys.addActionListener(e -> {
            saveSystemConfig();
            JOptionPane.showMessageDialog(this, "Đã lưu cấu hình hệ thống!");
        });

        return p;
    }

    private void saveSystemConfig() {
        config.setDomain(txtDomain.getText().trim());
        config.setDbHost(txtDbHost.getText().trim());
        try { config.setDbPort(Integer.parseInt(txtDbPort.getText().trim())); } catch (Exception ignored) {}
        config.setDbName(txtDbName.getText().trim());
        config.setDbUser(txtDbUser.getText().trim());
        config.setDbPass(new String(txtDbPass.getPassword()));
        config.save();
    }

    private int addFormRow(JPanel p, GridBagConstraints g, int row, String label, JComponent comp, String hint) {
        g.gridx = 0; g.gridy = row; g.weightx = 0;
        p.add(new JLabel(label), g);
        g.gridx = 1; g.weightx = 1;
        JPanel wrap = new JPanel(new BorderLayout(8, 0));
        wrap.setOpaque(false);
        wrap.add(comp, BorderLayout.CENTER);
        if (hint != null && !hint.isBlank()) {
            JLabel h = new JLabel(hint);
            h.setForeground(Color.GRAY);
            h.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            wrap.add(h, BorderLayout.SOUTH);
        }
        p.add(wrap, g);
        return row + 1;
    }

    private JPanel createPairPanel(JTextField left, JTextField right) {
        JPanel p = new JPanel(new BorderLayout(8, 0));
        p.setOpaque(false);
        right.setPreferredSize(new Dimension(80, 26));
        p.add(left, BorderLayout.CENTER);
        p.add(right, BorderLayout.EAST);
        return p;
    }

    private void syncConfigToForm() {
        spinSmtpPort.setValue(config.getSmtpPort());
        spinPop3Port.setValue(config.getPop3Port());

        chkGmailEnabled.setSelected(config.isGmailEnabled());
        txtGmailUser.setText(config.getGmailUser());
        txtGmailAppPass.setText(config.getGmailAppPassword());
        txtGmailSmtpHost.setText(config.getGmailSmtpHost());
        txtGmailSmtpPort.setText(String.valueOf(config.getGmailSmtpPort()));
        txtGmailPopHost.setText(config.getGmailPop3Host());
        txtGmailPopPort.setText(String.valueOf(config.getGmailPop3Port()));
        chkGmailFetch.setSelected(config.isGmailFetchEnabled());
        txtGmailFetchInterval.setText(String.valueOf(config.getGmailFetchIntervalSec()));
        txtGmailDefaultUser.setText(config.getGmailDefaultUser());

        txtDomain.setText(config.getDomain());
        txtDbHost.setText(config.getDbHost());
        txtDbPort.setText(String.valueOf(config.getDbPort()));
        txtDbName.setText(config.getDbName());
        txtDbUser.setText(config.getDbUser());
        txtDbPass.setText(config.getDbPass());
    }

    private void setupEvents() {
        btnStartStop.addActionListener(e -> toggleServer());
        btnOpenClient.addActionListener(e -> new LoginFrame().setVisible(true));
        btnOpenPma.addActionListener(e -> openPhpMyAdmin());
    }

    private void toggleServer() {
        if (manager.isRunning()) {
            manager.stop();
        } else {
            int smtp = (int) spinSmtpPort.getValue();
            int pop = (int) spinPop3Port.getValue();
            config.setSmtpPort(smtp);
            config.setPop3Port(pop);
            config.save();
            try {
                manager.start();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Không thể khởi động Server:\n" + ex.getMessage(),
                        "Lỗi khởi động", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void openPhpMyAdmin() {
        try {
            Desktop.getDesktop().browse(new URI("http://localhost:8085"));
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Hãy mở trình duyệt tới: http://localhost:8085\n(" + ex.getMessage() + ")");
        }
    }

    private void initDatabaseAndAutoStart() {
        new SwingWorker<Boolean, Void>() {
            @Override
            protected Boolean doInBackground() {
                try {
                    db.init();
                    return true;
                } catch (Exception e) {
                    System.err.println("Init DB: " + e.getMessage());
                    return false;
                }
            }

            @Override
            protected void done() {
                try {
                    boolean ok = get();
                    updateDbBadge(ok);
                    if (ok) {
                        refreshAllData();
                        // Tự khởi động Server
                        try {
                            manager.start();
                        } catch (Exception ex) {
                            manager.log("SYSTEM", "Tự khởi động lỗi: " + ex.getMessage());
                        }
                    }
                } catch (Exception ignored) {}
            }
        }.execute();
    }

    private void updateDbBadge(boolean ok) {
        if (ok) {
            lblDbBadge.setText("MYSQL: ĐÃ KẾT NỐI (CỔNG " + config.getDbPort() + ")");
            lblDbBadge.setBackground(new Color(0x10, 0xB9, 0x81));
        } else {
            lblDbBadge.setText("MYSQL: MẤT KẾT NỐI (CỔNG " + config.getDbPort() + ")");
            lblDbBadge.setBackground(new Color(0xEF, 0x44, 0x44));
        }
    }

    public void refreshAllData() {
        refreshDashboard();
        refreshUsers();
        searchDbEmails();
        refreshDbLogs();
    }

    private void refreshDashboard() {
        new SwingWorker<int[], Void>() {
            @Override
            protected int[] doInBackground() {
                return db.stats();
            }

            @Override
            protected void done() {
                try {
                    int[] st = get();
                    lblCardUsers.setText(String.valueOf(st[0]));
                    lblCardEmails.setText(String.valueOf(st[1]));
                    lblCardRelayed.setText(String.valueOf(st[2]));
                    lblCardFetched.setText(String.valueOf(st[3]));
                } catch (Exception ignored) {}
            }
        }.execute();

        new SwingWorker<List<MailRecord>, Void>() {
            @Override
            protected List<MailRecord> doInBackground() throws Exception {
                return db.listAllEmails("", 30);
            }

            @Override
            protected void done() {
                try {
                    List<MailRecord> list = get();
                    modelDashEmails.setRowCount(0);
                    SimpleDateFormat sdf = new SimpleDateFormat("dd/MM HH:mm:ss");
                    for (MailRecord m : list) {
                        modelDashEmails.addRow(new Object[]{
                                m.id, m.owner, m.folder, m.sender, m.recipients, m.subject,
                                m.source, m.status, m.createdAt != null ? sdf.format(m.createdAt) : ""
                        });
                    }
                } catch (Exception ignored) {}
            }
        }.execute();
    }

    private void refreshUsers() {
        new SwingWorker<List<UserRecord>, Void>() {
            @Override
            protected List<UserRecord> doInBackground() throws Exception {
                return db.listUsers();
            }

            @Override
            protected void done() {
                try {
                    List<UserRecord> list = get();
                    modelUsers.setRowCount(0);
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
                    for (UserRecord u : list) {
                        modelUsers.addRow(new Object[]{
                                u.id, u.username, u.fullName, u.inboxCount, u.unreadCount,
                                u.active, u.createdAt != null ? sdf.format(u.createdAt) : ""
                        });
                    }
                } catch (Exception ignored) {}
            }
        }.execute();
    }

    // ================== SERVER LISTENER CALLBACKS ==================
    @Override
    public void onLog(String protocol, String message) {
        SwingUtilities.invokeLater(() -> {
            txtLiveLog.append(message + "\n");
            if (chkAutoScroll.isSelected()) {
                txtLiveLog.setCaretPosition(txtLiveLog.getDocument().getLength());
            }
        });
    }

    @Override
    public void onDataChanged() {
        SwingUtilities.invokeLater(this::refreshAllData);
    }

    @Override
    public void onStatusChanged() {
        SwingUtilities.invokeLater(() -> {
            boolean running = manager.isRunning();
            if (running) {
                lblStatusBadge.setText("● ĐANG CHẠY");
                lblStatusBadge.setBackground(new Color(0x10, 0xB9, 0x81));
                btnStartStop.setText("⏹ Dừng máy chủ");
                btnStartStop.setBackground(new Color(0xEF, 0x44, 0x44));
                spinSmtpPort.setEnabled(false);
                spinPop3Port.setEnabled(false);
            } else {
                lblStatusBadge.setText("● ĐÃ DỪNG");
                lblStatusBadge.setBackground(new Color(0xEF, 0x44, 0x44));
                btnStartStop.setText("▶ Khởi động máy chủ");
                btnStartStop.setBackground(new Color(0x10, 0xB9, 0x81));
                spinSmtpPort.setEnabled(true);
                spinPop3Port.setEnabled(true);
            }
        });
    }
}
