import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.rmi.Naming;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class BankClientUI extends JFrame {
    private CardLayout cardLayout;
    private JPanel mainPanel;

    // RMI & Session
    private IBankService bankService;
    private String currentUsername = null;
    private static final int DEFAULT_RMI_PORT = 1099;

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
    private JTextField txtToUser;
    private JTextField txtAmount;
    private JTextArea txtLogs;
    private JButton btnTransfer;
    private JButton btnRefresh;
    private JButton btnLogout;

    public BankClientUI() {
        setTitle("RMI Banking Application - Dual Server Sync");
        setSize(700, 560);
        setMinimumSize(new Dimension(650, 500));
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

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

        // Top Bar: Xin chào + Server Badge + Đăng xuất
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

        // Nút Đăng xuất: NỀN ĐỎ NỔI BẬT, CHỮ TRẮNG RÕ RÀNG
        btnLogout = createCustomButton("Đăng xuất", 
                new Color(220, 53, 69), new Color(195, 30, 45), Color.WHITE, 6);
        btnLogout.setPreferredSize(new Dimension(105, 34));
        btnLogout.addActionListener(e -> executeLogout());

        topBar.add(userBox, BorderLayout.WEST);
        topBar.add(btnLogout, BorderLayout.EAST);
        panel.add(topBar, BorderLayout.NORTH);

        // Center: Số dư + Chuyển tiền
        JPanel centerPanel = new JPanel(new GridLayout(1, 2, 15, 0));
        centerPanel.setOpaque(false);

        // Cột 1: Thông tin tài khoản & Số dư
        JPanel balanceCard = new JPanel(new BorderLayout(10, 15));
        balanceCard.setBackground(Color.WHITE);
        balanceCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 224, 230), 1),
                new EmptyBorder(20, 20, 20, 20)
        ));

        JLabel lblBalTitle = new JLabel("SỐ DƯ KHẢ DỤNG");
        lblBalTitle.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lblBalTitle.setForeground(new Color(108, 117, 125));

        lblBalance = new JLabel("0 VNĐ", SwingConstants.CENTER);
        lblBalance.setFont(new Font("Segoe UI", Font.BOLD, 26));
        lblBalance.setForeground(new Color(40, 167, 69));

        // Nút Làm mới số dư (Nền xám nhạt hiện đại, chữ đậm màu tối)
        btnRefresh = createCustomButton("Làm mới số dư", 
                new Color(235, 238, 242), new Color(220, 225, 230), new Color(33, 37, 41), 6);
        btnRefresh.setPreferredSize(new Dimension(200, 36));
        btnRefresh.addActionListener(e -> refreshBalance());

        balanceCard.add(lblBalTitle, BorderLayout.NORTH);
        balanceCard.add(lblBalance, BorderLayout.CENTER);
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
        panel.add(centerPanel, BorderLayout.CENTER);

        // Bottom: Hoạt động / Nhật ký (Activity Logs)
        JPanel logCard = new JPanel(new BorderLayout(5, 5));
        logCard.setBackground(Color.WHITE);
        logCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 224, 230), 1),
                new EmptyBorder(10, 15, 10, 15)
        ));
        logCard.setPreferredSize(new Dimension(600, 150));

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
    // XỬ LÝ NGHIỆP VỤ (LOGIN, LOGOUT, CHUYỂN TIỀN, LÀM MỚI SỐ DƯ)
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

                        lblWelcome.setText("Xin chào, " + username);
                        lblServerBadge.setText("  Server: " + finalHost + "  ");
                        txtLogs.setText("");
                        addLog("Đăng nhập thành công vào Server (" + finalHost + ")");

                        refreshBalance();
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

    private void refreshBalance() {
        if (bankService == null || currentUsername == null) return;

        new Thread(() -> {
            try {
                double bal = bankService.getBalance(currentUsername);
                SwingUtilities.invokeLater(() -> {
                    NumberFormat nf = NumberFormat.getInstance(new Locale("vi", "VN"));
                    lblBalance.setText(nf.format(bal) + " VNĐ");
                });
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }).start();
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
                        refreshBalance();
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
        if (bankService != null && currentUsername != null) {
            try {
                bankService.logout(currentUsername);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
        currentUsername = null;
        bankService = null;
        cardLayout.show(mainPanel, "LOGIN");
    }

    private void handleExit() {
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
