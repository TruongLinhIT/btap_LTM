import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.rmi.Naming;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public class BankClientUI extends JFrame {
    private CardLayout cardLayout;
    private JPanel mainPanel;

    // RMI & Session
    private IBankService bankService;
    private String currentUsername = null;
    private Account currentAccount = null;
    private static final int DEFAULT_RMI_PORT = 1099;
    private Timer autoRefreshTimer;
    private final AtomicBoolean isRefreshing = new AtomicBoolean(false);
    private final Map<String, Account> lastProfiles = new HashMap<>();

    // Login Components
    private JTextField txtHost;
    private JTextField txtUsername;
    private JPasswordField txtPassword;
    private JLabel lblLoginStatus;
    private JButton btnLogin;

    // Dashboard Components
    private JLabel lblWelcome;
    private JLabel lblServerBadge;
    private JLabel lblBalance;
    private JLabel lblProfileName;
    private JLabel lblProfileEmail;
    private JLabel lblProfilePhone;
    private JTextField txtToUser;
    private JTextField txtAmount;
    private JTextArea txtLogs;
    private JButton btnTransfer;
    private JButton btnRefresh;
    private JButton btnEditProfile;
    private JButton btnLogout;
    private DefaultTableModel usersTableModel;
    private JTable tblUsers;

    public BankClientUI() {
        setTitle("RMI Banking Application - Dual Server Sync");
        setSize(860, 700);
        setMinimumSize(new Dimension(780, 620));
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

        // Timer tự động đồng bộ thông tin user & số dư mỗi 2.5 giây
        autoRefreshTimer = new Timer(2500, e -> refreshDashboardData(false));

        // Xử lý tắt ứng dụng -> Tự động đăng xuất mở khóa tài khoản
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                handleExit();
            }
        });

        cardLayout = new CardLayout();
        mainPanel = new JPanel(cardLayout);

        // 1. Tạo màn hình Đăng nhập
        mainPanel.add(createLoginPanel(), "LOGIN");

        // 2. Tạo màn hình Dashboard giao dịch
        mainPanel.add(createDashboardPanel(), "DASHBOARD");

        add(mainPanel);
        cardLayout.show(mainPanel, "LOGIN");
    }

    // =========================================================================
    // HÀM TẠO NÚT BẤM (BUTTON) TÙY BIẾN ĐẸP MẮT, KHÔNG BỊ LỖI CHỮ TRẮNG TRÊN NỀN TRẮNG
    // =========================================================================
    private JButton createCustomButton(String text, Color bgColor, Color hoverColor, Color fgColor, int radius) {
        JButton btn = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (!isEnabled()) {
                    g2.setColor(new Color(200, 205, 210));
                } else if (getModel().isPressed()) {
                    g2.setColor(bgColor.darker());
                } else if (getModel().isRollover()) {
                    g2.setColor(hoverColor);
                } else {
                    g2.setColor(bgColor);
                }
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), radius, radius);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        btn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btn.setForeground(fgColor);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setOpaque(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return btn;
    }

    // =========================================================================
    // 1. MÀN HÌNH ĐĂNG NHẬP
    // =========================================================================
    private JPanel createLoginPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(new Color(245, 247, 250));

        // Header
        JPanel headerPanel = new JPanel(new GridLayout(2, 1, 0, 5));
        headerPanel.setBackground(new Color(24, 119, 242));
        headerPanel.setBorder(new EmptyBorder(25, 20, 25, 20));

        JLabel title = new JLabel("HỆ THỐNG NGÂN HÀNG ĐIỆN TỬ (RMI)", SwingConstants.CENTER);
        title.setFont(new Font("Segoe UI", Font.BOLD, 22));
        title.setForeground(Color.WHITE);

        // JLabel subtitle = new JLabel("Mô hình 2 Server A - B Đồng Bộ Hai Chiều", SwingConstants.CENTER);
        // subtitle.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        // subtitle.setForeground(new Color(220, 235, 255));

        headerPanel.add(title);
        // headerPanel.add(subtitle);
        panel.add(headerPanel, BorderLayout.NORTH);

        // Form đăng nhập
        JPanel formWrapper = new JPanel(new GridBagLayout());
        formWrapper.setOpaque(false);

        JPanel card = new JPanel(new GridBagLayout());
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 224, 230), 1),
                new EmptyBorder(30, 40, 30, 40)
        ));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(10, 8, 10, 8);

        // Host IP Server
        gbc.gridx = 0; gbc.gridy = 0;
        JLabel lblHost = new JLabel("Host IP Server:");
        lblHost.setFont(new Font("Segoe UI", Font.BOLD, 13));
        card.add(lblHost, gbc);

        txtHost = new JTextField("localhost", 18);
        txtHost.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        txtHost.setToolTipText("Nhập IP của máy chạy Server (hoặc localhost nếu cùng máy)");
        gbc.gridx = 1; gbc.gridy = 0;
        card.add(txtHost, gbc);

        // Tên tài khoản
        gbc.gridx = 0; gbc.gridy = 1;
        JLabel lblUser = new JLabel("Tên tài khoản:");
        lblUser.setFont(new Font("Segoe UI", Font.BOLD, 13));
        card.add(lblUser, gbc);

        txtUsername = new JTextField("user1", 18);
        txtUsername.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        gbc.gridx = 1; gbc.gridy = 1;
        card.add(txtUsername, gbc);

        // Mật khẩu
        gbc.gridx = 0; gbc.gridy = 2;
        JLabel lblPass = new JLabel("Mật khẩu:");
        lblPass.setFont(new Font("Segoe UI", Font.BOLD, 13));
        card.add(lblPass, gbc);

        txtPassword = new JPasswordField("123", 18);
        txtPassword.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        gbc.gridx = 1; gbc.gridy = 2;
        card.add(txtPassword, gbc);

        // Trạng thái / Báo lỗi
        lblLoginStatus = new JLabel(" ", SwingConstants.CENTER);
        lblLoginStatus.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblLoginStatus.setForeground(new Color(220, 53, 69));
        gbc.gridx = 0; gbc.gridy = 3;
        gbc.gridwidth = 2;
        card.add(lblLoginStatus, gbc);

        // Nút Đăng nhập (Nền xanh đậm, chữ trắng)
        btnLogin = createCustomButton("ĐĂNG NHẬP VÀO HỆ THỐNG", 
                new Color(24, 119, 242), new Color(13, 100, 215), Color.WHITE, 8);
        btnLogin.setPreferredSize(new Dimension(300, 42));
        btnLogin.addActionListener(e -> executeLogin());

        // Nhấn Enter ở ô mật khẩu cũng tự đăng nhập
        txtPassword.addActionListener(e -> executeLogin());

        gbc.gridx = 0; gbc.gridy = 4;
        gbc.gridwidth = 2;
        gbc.insets = new Insets(15, 8, 8, 8);
        card.add(btnLogin, gbc);

        // Gợi ý
        JLabel hint = new JLabel("<html><center style='color:#6c757d; font-size:11px;'>"
                + "Gợi ý tài khoản mẫu: <b>user1</b> / pass <b>123</b> hoặc <b>user2</b> / pass <b>123</b>"
                + "</center></html>", SwingConstants.CENTER);
        gbc.gridy = 5;
        gbc.insets = new Insets(5, 8, 5, 8);
        card.add(hint, gbc);

        formWrapper.add(card);
        panel.add(formWrapper, BorderLayout.CENTER);

        return panel;
    }

    // =========================================================================
    // 2. MÀN HÌNH DASHBOARD GIAO DỊCH
    // =========================================================================
    private JPanel createDashboardPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBackground(new Color(245, 247, 250));
        panel.setBorder(new EmptyBorder(15, 20, 15, 20));

        // Top Bar: Xin chào + Server Badge + Sửa thông tin + Đăng xuất
        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setBackground(Color.WHITE);
        topBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 224, 230), 1),
                new EmptyBorder(12, 18, 12, 18)
        ));

        JPanel userBox = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        userBox.setOpaque(false);

        lblWelcome = new JLabel("Xin chào, user1");
        lblWelcome.setFont(new Font("Segoe UI", Font.BOLD, 17));

        lblServerBadge = new JLabel("  Server: localhost  ");
        lblServerBadge.setFont(new Font("Segoe UI", Font.BOLD, 11));
        lblServerBadge.setForeground(new Color(15, 81, 50));
        lblServerBadge.setBackground(new Color(212, 237, 218));
        lblServerBadge.setOpaque(true);
        lblServerBadge.setBorder(BorderFactory.createLineBorder(new Color(195, 230, 203), 1));

        userBox.add(lblWelcome);
        userBox.add(lblServerBadge);

        JPanel rightBox = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        rightBox.setOpaque(false);

        btnEditProfile = createCustomButton("Sửa thông tin",
                new Color(24, 119, 242), new Color(13, 100, 215), Color.WHITE, 6);
        btnEditProfile.setPreferredSize(new Dimension(125, 34));
        btnEditProfile.addActionListener(e -> showEditProfileDialog());

        // Nút Đăng xuất: NỀN ĐỎ NỔI BẬT, CHỮ TRẮNG RÕ RÀNG
        btnLogout = createCustomButton("Đăng xuất", 
                new Color(220, 53, 69), new Color(195, 30, 45), Color.WHITE, 6);
        btnLogout.setPreferredSize(new Dimension(105, 34));
        btnLogout.addActionListener(e -> executeLogout());

        rightBox.add(btnEditProfile);
        rightBox.add(btnLogout);

        topBar.add(userBox, BorderLayout.WEST);
        topBar.add(rightBox, BorderLayout.EAST);
        panel.add(topBar, BorderLayout.NORTH);

        // Center Wrapper: Hàng trên (Thông tin/Số dư + Chuyển tiền) & Hàng dưới (Danh sách User đồng bộ)
        JPanel centerWrapper = new JPanel(new BorderLayout(0, 10));
        centerWrapper.setOpaque(false);

        // Center top: Số dư + Chuyển tiền
        JPanel centerPanel = new JPanel(new GridLayout(1, 2, 15, 0));
        centerPanel.setOpaque(false);

        // Cột 1: Thông tin tài khoản & Số dư
        JPanel balanceCard = new JPanel(new BorderLayout(10, 10));
        balanceCard.setBackground(Color.WHITE);
        balanceCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 224, 230), 1),
                new EmptyBorder(15, 20, 15, 20)
        ));

        JLabel lblBalTitle = new JLabel("THÔNG TIN CÁ NHÂN & SỐ DƯ");
        lblBalTitle.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lblBalTitle.setForeground(new Color(108, 117, 125));

        JPanel profileInfoBox = new JPanel(new GridLayout(4, 1, 0, 4));
        profileInfoBox.setOpaque(false);

        lblProfileName = new JLabel("Họ tên: -");
        lblProfileName.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        lblProfileEmail = new JLabel("Email: -");
        lblProfileEmail.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        lblProfilePhone = new JLabel("SĐT: -");
        lblProfilePhone.setFont(new Font("Segoe UI", Font.PLAIN, 13));

        lblBalance = new JLabel("0 VNĐ", SwingConstants.CENTER);
        lblBalance.setFont(new Font("Segoe UI", Font.BOLD, 24));
        lblBalance.setForeground(new Color(40, 167, 69));

        profileInfoBox.add(lblProfileName);
        profileInfoBox.add(lblProfileEmail);
        profileInfoBox.add(lblProfilePhone);
        profileInfoBox.add(lblBalance);

        // Nút Làm mới dữ liệu (Nền xám nhạt hiện đại, chữ đậm màu tối)
        btnRefresh = createCustomButton("Làm mới dữ liệu", 
                new Color(235, 238, 242), new Color(220, 225, 230), new Color(33, 37, 41), 6);
        btnRefresh.setPreferredSize(new Dimension(200, 34));
        btnRefresh.addActionListener(e -> refreshDashboardData(true));

        balanceCard.add(lblBalTitle, BorderLayout.NORTH);
        balanceCard.add(profileInfoBox, BorderLayout.CENTER);
        balanceCard.add(btnRefresh, BorderLayout.SOUTH);
        centerPanel.add(balanceCard);

        // Cột 2: Form chuyển khoản
        JPanel transferCard = new JPanel(new GridBagLayout());
        transferCard.setBackground(Color.WHITE);
        transferCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 224, 230), 1),
                new EmptyBorder(15, 20, 15, 20)
        ));

        GridBagConstraints tGbc = new GridBagConstraints();
        tGbc.fill = GridBagConstraints.HORIZONTAL;
        tGbc.insets = new Insets(6, 4, 6, 4);

        JLabel lblTransTitle = new JLabel("CHUYỂN KHOẢN LIÊN SERVER");
        lblTransTitle.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lblTransTitle.setForeground(new Color(24, 119, 242));
        tGbc.gridx = 0; tGbc.gridy = 0;
        tGbc.gridwidth = 2;
        transferCard.add(lblTransTitle, tGbc);

        tGbc.gridwidth = 1;
        tGbc.gridx = 0; tGbc.gridy = 1;
        JLabel lblRecv = new JLabel("Người nhận:");
        lblRecv.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        transferCard.add(lblRecv, tGbc);

        txtToUser = new JTextField();
        txtToUser.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        tGbc.gridx = 1; tGbc.gridy = 1;
        transferCard.add(txtToUser, tGbc);

        tGbc.gridx = 0; tGbc.gridy = 2;
        JLabel lblMoney = new JLabel("Số tiền (VNĐ):");
        lblMoney.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        transferCard.add(lblMoney, tGbc);

        txtAmount = new JTextField();
        txtAmount.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        tGbc.gridx = 1; tGbc.gridy = 2;
        transferCard.add(txtAmount, tGbc);

        // Nút Chuyển tiền: NỀN XANH DƯƠNG ĐẬM, CHỮ TRẮNG RÕ RÀNG
        btnTransfer = createCustomButton("GỬI TIỀN NGAY", 
                new Color(24, 119, 242), new Color(13, 100, 215), Color.WHITE, 6);
        btnTransfer.setPreferredSize(new Dimension(240, 38));
        btnTransfer.addActionListener(e -> executeTransfer());

        tGbc.gridx = 0; tGbc.gridy = 3;
        tGbc.gridwidth = 2;
        tGbc.insets = new Insets(12, 4, 4, 4);
        transferCard.add(btnTransfer, tGbc);

        centerPanel.add(transferCard);
        centerWrapper.add(centerPanel, BorderLayout.NORTH);

        // Center bottom: Bảng thông tin khách hàng đồng bộ theo thời gian thực
        JPanel usersCard = new JPanel(new BorderLayout(5, 6));
        usersCard.setBackground(Color.WHITE);
        usersCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 224, 230), 1),
                new EmptyBorder(10, 15, 10, 15)
        ));

        JPanel usersHeader = new JPanel(new BorderLayout());
        usersHeader.setOpaque(false);
        JLabel lblUsersTitle = new JLabel("DANH BẠ KHÁCH HÀNG (TỰ ĐỘNG ĐỒNG BỘ GIỮA CÁC USER & SERVER)");
        lblUsersTitle.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblUsersTitle.setForeground(new Color(108, 117, 125));
        JLabel lblUsersHint = new JLabel("Click vào dòng để chọn người nhận");
        lblUsersHint.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        lblUsersHint.setForeground(new Color(130, 138, 145));
        usersHeader.add(lblUsersTitle, BorderLayout.WEST);
        usersHeader.add(lblUsersHint, BorderLayout.EAST);

        String[] cols = {"Tài khoản", "Họ và tên", "Email", "Số điện thoại", "Trạng thái"};
        usersTableModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        tblUsers = new JTable(usersTableModel);
        tblUsers.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        tblUsers.setRowHeight(24);
        tblUsers.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        tblUsers.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tblUsers.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int row = tblUsers.getSelectedRow();
                if (row >= 0) {
                    String selectedUser = String.valueOf(usersTableModel.getValueAt(row, 0));
                    if (!selectedUser.equals(currentUsername)) {
                        txtToUser.setText(selectedUser);
                    }
                }
            }
        });

        JScrollPane scrollUsers = new JScrollPane(tblUsers);
        scrollUsers.setPreferredSize(new Dimension(600, 125));

        usersCard.add(usersHeader, BorderLayout.NORTH);
        usersCard.add(scrollUsers, BorderLayout.CENTER);
        centerWrapper.add(usersCard, BorderLayout.CENTER);

        panel.add(centerWrapper, BorderLayout.CENTER);

        // Bottom: Hoạt động / Nhật ký (Activity Logs)
        JPanel logCard = new JPanel(new BorderLayout(5, 5));
        logCard.setBackground(Color.WHITE);
        logCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 224, 230), 1),
                new EmptyBorder(10, 15, 10, 15)
        ));
        logCard.setPreferredSize(new Dimension(600, 130));

        JLabel lblLogTitle = new JLabel("Nhật ký giao dịch & Đồng bộ RMI:");
        lblLogTitle.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblLogTitle.setForeground(new Color(108, 117, 125));

        txtLogs = new JTextArea();
        txtLogs.setEditable(false);
        txtLogs.setFont(new Font("Consolas", Font.PLAIN, 12));
        txtLogs.setBackground(new Color(248, 249, 250));
        JScrollPane scrollLogs = new JScrollPane(txtLogs);

        logCard.add(lblLogTitle, BorderLayout.NORTH);
        logCard.add(scrollLogs, BorderLayout.CENTER);
        panel.add(logCard, BorderLayout.SOUTH);

        return panel;
    }

    // =========================================================================
    // XỬ LÝ NGHIỆP VỤ (LOGIN, LOGOUT, CHUYỂN TIỀN, SỬA THÔNG TIN & ĐỒNG BỘ)
    // =========================================================================

    private void executeLogin() {
        String host = txtHost.getText().trim();
        if (host.isEmpty()) host = "localhost";

        String username = txtUsername.getText().trim();
        String password = new String(txtPassword.getPassword());

        if (username.isEmpty() || password.isEmpty()) {
            lblLoginStatus.setText("Vui lòng điền đủ Tên tài khoản và Mật khẩu!");
            return;
        }

        lblLoginStatus.setText("⏳ Đang kết nối tới " + host + ":" + DEFAULT_RMI_PORT + "...");
        lblLoginStatus.setForeground(new Color(24, 119, 242));
        btnLogin.setEnabled(false);

        final String finalHost = host;
        new Thread(() -> {
            try {
                String rmiUrl = "rmi://" + finalHost + ":" + DEFAULT_RMI_PORT + "/BankService";
                IBankService service = (IBankService) Naming.lookup(rmiUrl);

                String result = service.login(username, password);

                SwingUtilities.invokeLater(() -> {
                    btnLogin.setEnabled(true);
                    if ("OK".equals(result)) {
                        bankService = service;
                        currentUsername = username;
                        currentAccount = null;
                        lastProfiles.clear();

                        lblWelcome.setText("Xin chào, " + username);
                        lblServerBadge.setText("  Server: " + finalHost + "  ");
                        txtLogs.setText("");
                        addLog("Đăng nhập thành công vào Server (" + finalHost + ")");

                        refreshDashboardData(false);
                        autoRefreshTimer.start();
                        cardLayout.show(mainPanel, "DASHBOARD");
                        lblLoginStatus.setText(" ");
                    } else if ("DANG_DANG_NHAP_NOI_KHAC".equals(result)) {
                        lblLoginStatus.setForeground(new Color(220, 53, 69));
                        lblLoginStatus.setText("Tài khoản đang được đăng nhập ở nơi khác!");
                        JOptionPane.showMessageDialog(this,
                                "⚠️ CẢNH BÁO TỪ CHỐI ĐĂNG NHẬP:\n\n"
                                        + "Tài khoản '" + username + "' hiện ĐANG ĐƯỢC ĐĂNG NHẬP ở một nơi khác\n"
                                        + "(trên hệ thống Server A hoặc Server B)!\n\n"
                                        + "👉 Vui lòng đăng xuất ở thiết bị kia trước khi đăng nhập lại.",
                                "Tài khoản đang đăng nhập", JOptionPane.WARNING_MESSAGE);
                    } else {
                        lblLoginStatus.setForeground(new Color(220, 53, 69));
                        lblLoginStatus.setText("Sai tên tài khoản hoặc mật khẩu!");
                    }
                });

            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    btnLogin.setEnabled(true);
                    lblLoginStatus.setForeground(new Color(220, 53, 69));
                    lblLoginStatus.setText("Lỗi kết nối RMI: " + ex.getMessage());
                    JOptionPane.showMessageDialog(this,
                            "Không thể kết nối tới Server tại " + finalHost + ":" + DEFAULT_RMI_PORT + "!\n"
                                    + "Hãy đảm bảo file ServerMain đã được chạy trên máy đó.",
                            "Lỗi kết nối Server", JOptionPane.ERROR_MESSAGE);
                });
            }
        }).start();
    }

    private void refreshDashboardData(boolean showManualLog) {
        if (bankService == null || currentUsername == null) return;
        if (!isRefreshing.compareAndSet(false, true)) return;

        final IBankService serviceSnapshot = bankService;
        final String usernameSnapshot = currentUsername;

        new Thread(() -> {
            try {
                List<Account> profiles = serviceSnapshot.getAllUserProfiles();
                SwingUtilities.invokeLater(() -> {
                    try {
                        if (currentUsername == null || !currentUsername.equals(usernameSnapshot)) return;

                        boolean hadPrevious = !lastProfiles.isEmpty();
                        NumberFormat nf = NumberFormat.getInstance(new Locale("vi", "VN"));

                        int selectedRow = tblUsers.getSelectedRow();
                        String selectedUser = (selectedRow >= 0 && selectedRow < usersTableModel.getRowCount())
                                ? String.valueOf(usersTableModel.getValueAt(selectedRow, 0))
                                : null;

                        usersTableModel.setRowCount(0);
                        int rowToSelect = -1;

                        for (int i = 0; i < profiles.size(); i++) {
                            Account acc = profiles.get(i);
                            Account prev = lastProfiles.get(acc.getUsername());

                            if (hadPrevious && prev != null) {
                                boolean infoChanged = !Objects.equals(prev.getFullName(), acc.getFullName())
                                        || !Objects.equals(prev.getEmail(), acc.getEmail())
                                        || !Objects.equals(prev.getPhone(), acc.getPhone());
                                if (infoChanged) {
                                    addLog("[Đồng bộ] Thông tin user '" + acc.getUsername() + "' đã cập nhật -> "
                                            + acc.getFullName() + " | " + acc.getEmail() + " | " + acc.getPhone());
                                }
                                if (acc.getUsername().equals(currentUsername)
                                        && Double.compare(prev.getBalance(), acc.getBalance()) != 0) {
                                    addLog("[Đồng bộ] Số dư tài khoản cập nhật: " + nf.format(acc.getBalance()) + " VNĐ");
                                }
                            }

                            if (acc.getUsername().equals(currentUsername)) {
                                currentAccount = acc;
                                String displayName = acc.getFullName().isEmpty()
                                        ? acc.getUsername()
                                        : acc.getFullName() + " (" + acc.getUsername() + ")";
                                lblWelcome.setText("Xin chào, " + displayName);
                                lblProfileName.setText("Họ tên: " + (acc.getFullName().isEmpty() ? "(Chưa cập nhật)" : acc.getFullName()));
                                lblProfileEmail.setText("Email: " + (acc.getEmail().isEmpty() ? "(Chưa cập nhật)" : acc.getEmail()));
                                lblProfilePhone.setText("SĐT: " + (acc.getPhone().isEmpty() ? "(Chưa cập nhật)" : acc.getPhone()));
                                lblBalance.setText(nf.format(acc.getBalance()) + " VNĐ");
                            }

                            usersTableModel.addRow(new Object[]{
                                    acc.getUsername(),
                                    acc.getFullName().isEmpty() ? "-" : acc.getFullName(),
                                    acc.getEmail().isEmpty() ? "-" : acc.getEmail(),
                                    acc.getPhone().isEmpty() ? "-" : acc.getPhone(),
                                    acc.isLocked() ? "Đang online" : "Offline"
                            });

                            if (acc.getUsername().equals(selectedUser)) {
                                rowToSelect = i;
                            }
                        }

                        if (rowToSelect >= 0) {
                            tblUsers.setRowSelectionInterval(rowToSelect, rowToSelect);
                        }

                        lastProfiles.clear();
                        for (Account acc : profiles) {
                            lastProfiles.put(acc.getUsername(), acc);
                        }

                        if (showManualLog) {
                            addLog("Đã làm mới dữ liệu & đồng bộ danh bạ khách hàng.");
                        }
                    } finally {
                        isRefreshing.set(false);
                    }
                });
            } catch (Exception ex) {
                isRefreshing.set(false);
                ex.printStackTrace();
            }
        }).start();
    }

    private void showEditProfileDialog() {
        if (bankService == null || currentUsername == null) return;

        JDialog dialog = new JDialog(this, "Chỉnh sửa thông tin tài khoản (" + currentUsername + ")", true);
        dialog.setSize(430, 340);
        dialog.setLocationRelativeTo(this);
        dialog.setResizable(false);

        JPanel content = new JPanel(new BorderLayout(10, 12));
        content.setBorder(new EmptyBorder(16, 20, 16, 20));
        content.setBackground(Color.WHITE);

        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(6, 5, 6, 5);

        JTextField txtUserReadOnly = new JTextField(currentUsername, 20);
        txtUserReadOnly.setEnabled(false);

        JTextField txtFullName = new JTextField(currentAccount != null ? currentAccount.getFullName() : "", 20);
        JTextField txtEmail = new JTextField(currentAccount != null ? currentAccount.getEmail() : "", 20);
        JTextField txtPhone = new JTextField(currentAccount != null ? currentAccount.getPhone() : "", 20);
        JPasswordField txtNewPass = new JPasswordField(20);

        String[] labels = {"Tài khoản:", "Họ và tên (*):", "Email:", "Số điện thoại:", "Mật khẩu mới:"};
        JComponent[] fields = {txtUserReadOnly, txtFullName, txtEmail, txtPhone, txtNewPass};

        for (int i = 0; i < labels.length; i++) {
            gbc.gridx = 0; gbc.gridy = i; gbc.weightx = 0;
            JLabel lbl = new JLabel(labels[i]);
            lbl.setFont(new Font("Segoe UI", Font.BOLD, 12));
            form.add(lbl, gbc);

            gbc.gridx = 1; gbc.gridy = i; gbc.weightx = 1;
            fields[i].setFont(new Font("Segoe UI", Font.PLAIN, 13));
            form.add(fields[i], gbc);
        }

        gbc.gridx = 1; gbc.gridy = labels.length;
        JLabel lblNote = new JLabel("Để trống mật khẩu mới nếu không muốn đổi.");
        lblNote.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        lblNote.setForeground(new Color(108, 117, 125));
        form.add(lblNote, gbc);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        btnPanel.setOpaque(false);

        JButton btnCancel = createCustomButton("Hủy",
                new Color(235, 238, 242), new Color(220, 225, 230), new Color(33, 37, 41), 6);
        btnCancel.setPreferredSize(new Dimension(85, 34));
        btnCancel.addActionListener(e -> dialog.dispose());

        JButton btnSave = createCustomButton("Lưu & Đồng bộ",
                new Color(24, 119, 242), new Color(13, 100, 215), Color.WHITE, 6);
        btnSave.setPreferredSize(new Dimension(130, 34));
        btnSave.addActionListener(e -> {
            String fullName = txtFullName.getText().trim();
            String email = txtEmail.getText().trim();
            String phone = txtPhone.getText().trim();
            String newPass = new String(txtNewPass.getPassword()).trim();

            btnSave.setEnabled(false);
            new Thread(() -> {
                try {
                    String res = bankService.updateUserInfo(currentUsername, fullName, email, phone, newPass);
                    SwingUtilities.invokeLater(() -> {
                        btnSave.setEnabled(true);
                        if ("OK".equals(res)) {
                            dialog.dispose();
                            refreshDashboardData(false);
                            JOptionPane.showMessageDialog(this,
                                    "✅ Cập nhật thông tin thành công!\n"
                                            + "Dữ liệu đã được đồng bộ sang cả 2 Server và các User.",
                                    "Thành công", JOptionPane.INFORMATION_MESSAGE);
                        } else {
                            JOptionPane.showMessageDialog(dialog, res, "Dữ liệu không hợp lệ", JOptionPane.WARNING_MESSAGE);
                        }
                    });
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> {
                        btnSave.setEnabled(true);
                        JOptionPane.showMessageDialog(dialog, "Lỗi RMI: " + ex.getMessage(), "Lỗi hệ thống", JOptionPane.ERROR_MESSAGE);
                    });
                }
            }).start();
        });

        btnPanel.add(btnCancel);
        btnPanel.add(btnSave);

        content.add(form, BorderLayout.CENTER);
        content.add(btnPanel, BorderLayout.SOUTH);
        dialog.setContentPane(content);
        dialog.setVisible(true);
    }

    private void executeTransfer() {
        String toUser = txtToUser.getText().trim();
        String amountStr = txtAmount.getText().trim();

        if (toUser.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Vui lòng nhập tài khoản người nhận!", "Thông báo", JOptionPane.WARNING_MESSAGE);
            return;
        }

        double amount;
        try {
            amount = Double.parseDouble(amountStr);
            if (amount <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Số tiền chuyển phải là số hợp lệ và lớn hơn 0!", "Lỗi nhập liệu", JOptionPane.ERROR_MESSAGE);
            return;
        }

        btnTransfer.setEnabled(false);

        new Thread(() -> {
            try {
                String result = bankService.transfer(currentUsername, toUser, amount);
                SwingUtilities.invokeLater(() -> {
                    btnTransfer.setEnabled(true);
                    if ("OK".equals(result)) {
                        NumberFormat nf = NumberFormat.getInstance(new Locale("vi", "VN"));
                        String formattedAmt = nf.format(amount) + " VNĐ";
                        JOptionPane.showMessageDialog(this,
                                "✅ Chuyển tiền thành công!\n\n"
                                        + "Đã chuyển " + formattedAmt + " tới tài khoản '" + toUser + "'.\n"
                                        + "Số dư đã được đồng bộ tự động sang cả 2 Server.",
                                "Giao dịch thành công", JOptionPane.INFORMATION_MESSAGE);

                        addLog("Chuyển " + formattedAmt + " tới '" + toUser + "' thành công (Đồng bộ Dual-Server).");
                        txtToUser.setText("");
                        txtAmount.setText("");
                        refreshDashboardData(false);
                    } else {
                        JOptionPane.showMessageDialog(this, result, "Giao dịch thất bại", JOptionPane.WARNING_MESSAGE);
                        addLog("Chuyển tiền thất bại: " + result);
                    }
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    btnTransfer.setEnabled(true);
                    JOptionPane.showMessageDialog(this, "Lỗi RMI: " + ex.getMessage(), "Lỗi hệ thống", JOptionPane.ERROR_MESSAGE);
                });
            }
        }).start();
    }

    private void executeLogout() {
        autoRefreshTimer.stop();
        if (bankService != null && currentUsername != null) {
            try {
                bankService.logout(currentUsername);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
        currentUsername = null;
        currentAccount = null;
        bankService = null;
        lastProfiles.clear();
        cardLayout.show(mainPanel, "LOGIN");
    }

    private void handleExit() {
        autoRefreshTimer.stop();
        if (bankService != null && currentUsername != null) {
            try {
                bankService.logout(currentUsername);
            } catch (Exception ignored) {}
        }
        System.exit(0);
    }

    private void addLog(String message) {
        String time = new SimpleDateFormat("HH:mm:ss").format(new Date());
        txtLogs.append("[" + time + "] " + message + "\n");
        txtLogs.setCaretPosition(txtLogs.getDocument().getLength());
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        SwingUtilities.invokeLater(() -> {
            BankClientUI app = new BankClientUI();
            app.setVisible(true);
        });
    }
}
