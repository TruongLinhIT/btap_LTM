package ui.client;

import client.MailClientService;
import client.MailSummary;
import common.Attachment;
import common.ParsedMail;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Giao diện Mail Client hoàn chỉnh theo phong cách Gmail (LTM Mail):
 * - Thanh bên trái: Nút Soạn thư lớn, Hộp thư đến, Thư đã gửi, Thùng rác.
 * - Danh sách thư: Tiêu đề in đậm nếu chưa đọc, ký hiệu tệp đính kèm 📎, ngày giờ.
 * - Khung đọc thư bên phải: Tương tự Gmail, hiển thị avatar, người gửi, đính kèm, nội dung HTML/Text.
 * - Khung giám sát giao thức POP3 & SMTP: Hiển thị socket request/response thời gian thực.
 * - Hỗ trợ gửi thư nội bộ (@localhost) và gửi thư ra Internet qua Gmail Gateway!
 */
public class MailClientFrame extends JFrame {
    private final MailClientService service;

    // Trạng thái hiện tại
    private String currentFolder = "INBOX";
    private List<MailSummary> allSummaries = new ArrayList<>();
    private List<MailSummary> displayedSummaries = new ArrayList<>();

    // Các thành phần giao diện
    private final JButton btnCompose = new JButton("＋  Soạn thư");
    private final JButton btnNavInbox = new JButton("📥  Hộp thư đến");
    private final JButton btnNavSent = new JButton("📤  Thư đã gửi");
    private final JButton btnNavTrash = new JButton("🗑  Thùng rác");
    private final JLabel lblInboxBadge = new JLabel("0");

    private final JTextField txtSearch = new JTextField();
    private final JButton btnRefresh = new JButton("🔄 Làm mới");
    private final JButton btnDelete = new JButton("🗑 Xóa");
    private final JButton btnToggleLog = new JButton("📡 Console Socket");

    private final DefaultTableModel tableModel;
    private final JTable mailTable;
    private final MailDetailPanel detailPanel = new MailDetailPanel();

    // Protocol Log Panel
    private final JPanel pnlProtocolLog = new JPanel(new BorderLayout());
    private final JTextArea txtProtocolLog = new JTextArea();
    private boolean logVisible = false;

    // Timer tự động làm mới
    private Timer autoRefreshTimer;

    public MailClientFrame(MailClientService service) {
        super("LTM Mail – " + service.getEmail() + " (Giao thức POP3 & SMTP)");
        this.service = service;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(1240, 780);
        setMinimumSize(new Dimension(960, 600));
        setLocationRelativeTo(null);

        // Thiết lập bảng danh sách thư
        String[] columnNames = {"", "Người gửi / Nhận", "Tiêu đề", "📎", "Thời gian"};
        tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        mailTable = new JTable(tableModel);

        initUi();
        setupEvents();

        // Đăng ký log theo dõi socket giao thức POP3/SMTP
        service.setTrace(msg -> SwingUtilities.invokeLater(() -> {
            txtProtocolLog.append(msg + "\n");
            txtProtocolLog.setCaretPosition(txtProtocolLog.getDocument().getLength());
        }));

        // Tải thư mục đầu tiên
        switchFolder("INBOX");

        // Tự động kiểm tra thư mới mỗi 30 giây
        autoRefreshTimer = new Timer(30000, e -> reloadFolder(false));
        autoRefreshTimer.start();
    }

    private void initUi() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(new Color(0xF6, 0xF8, 0xFC));

        // ================= 1. TOP HEADER (GMAIL STYLE) =================
        JPanel pnlHeader = new JPanel(new BorderLayout(15, 0));
        pnlHeader.setBackground(Color.WHITE);
        pnlHeader.setBorder(new CompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(0xEA, 0xEA, 0xEA)),
                new EmptyBorder(8, 16, 8, 16)
        ));

        // Logo
        JPanel pnlLogo = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        pnlLogo.setOpaque(false);
        JLabel lblLogoIcon = new JLabel("M");
        lblLogoIcon.setFont(new Font("Segoe UI", Font.BOLD, 22));
        lblLogoIcon.setForeground(new Color(0xEA, 0x43, 0x35));
        JLabel lblLogoText = new JLabel("LTM Mail");
        lblLogoText.setFont(new Font("Segoe UI", Font.BOLD, 18));
        lblLogoText.setForeground(new Color(0x5F, 0x63, 0x68));
        pnlLogo.add(lblLogoIcon);
        pnlLogo.add(lblLogoText);
        pnlHeader.add(pnlLogo, BorderLayout.WEST);

        // Ô tìm kiếm ở giữa
        JPanel pnlSearchBox = new JPanel(new BorderLayout(8, 0));
        pnlSearchBox.setBackground(new Color(0xF1, 0xF3, 0xF4));
        pnlSearchBox.setBorder(new CompoundBorder(
                new LineBorder(new Color(0xE0, 0xE0, 0xE0), 1, true),
                new EmptyBorder(6, 12, 6, 12)
        ));
        JLabel lblSearchIcon = new JLabel("🔍");
        txtSearch.setBorder(null);
        txtSearch.setOpaque(false);
        txtSearch.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        txtSearch.putClientProperty("JTextField.placeholderText", "Tìm kiếm trong thư...");
        pnlSearchBox.add(lblSearchIcon, BorderLayout.WEST);
        pnlSearchBox.add(txtSearch, BorderLayout.CENTER);
        pnlHeader.add(pnlSearchBox, BorderLayout.CENTER);

        // Khung thông tin người dùng góc phải
        JPanel pnlUser = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 2));
        pnlUser.setOpaque(false);

        JLabel lblUserAvatar = new JLabel(service.getUsername().substring(0, 1).toUpperCase());
        lblUserAvatar.setPreferredSize(new Dimension(32, 32));
        lblUserAvatar.setOpaque(true);
        lblUserAvatar.setBackground(new Color(0x1A, 0x73, 0xE8));
        lblUserAvatar.setForeground(Color.WHITE);
        lblUserAvatar.setHorizontalAlignment(SwingConstants.CENTER);
        lblUserAvatar.setFont(new Font("Segoe UI", Font.BOLD, 14));
        lblUserAvatar.setBorder(new LineBorder(new Color(0xDF, 0xE1, 0xE5), 1, true));

        String displayName = service.getFullName().isBlank() ? service.getUsername() : service.getFullName();
        JLabel lblUserInfo = new JLabel("<html><b>" + displayName + "</b><br><small style='color:gray'>"
                + service.getEmail() + "</small></html>");

        JButton btnLogout = new JButton("Đăng xuất");
        btnLogout.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        btnLogout.addActionListener(e -> logout());

        pnlUser.add(lblUserAvatar);
        pnlUser.add(lblUserInfo);
        pnlUser.add(btnLogout);
        pnlHeader.add(pnlUser, BorderLayout.EAST);

        root.add(pnlHeader, BorderLayout.NORTH);

        // ================= 2. LEFT SIDEBAR =================
        JPanel pnlSidebar = new JPanel(new BorderLayout());
        pnlSidebar.setPreferredSize(new Dimension(220, 0));
        pnlSidebar.setBackground(new Color(0xF6, 0xF8, 0xFC));
        pnlSidebar.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, new Color(0xEA, 0xEA, 0xEA)));

        JPanel pnlSidebarTop = new JPanel();
        pnlSidebarTop.setLayout(new BoxLayout(pnlSidebarTop, BoxLayout.Y_AXIS));
        pnlSidebarTop.setOpaque(false);
        pnlSidebarTop.setBorder(new EmptyBorder(16, 12, 16, 12));

        // Nút Soạn thư lớn phong cách Gmail
        btnCompose.setMaximumSize(new Dimension(196, 48));
        btnCompose.setPreferredSize(new Dimension(196, 48));
        btnCompose.setAlignmentX(Component.CENTER_ALIGNMENT);
        btnCompose.setBackground(new Color(0xC2, 0xE7, 0xFF));
        btnCompose.setForeground(new Color(0x00, 0x1D, 0x35));
        btnCompose.setFont(new Font("Segoe UI", Font.BOLD, 14));
        btnCompose.setFocusPainted(false);
        btnCompose.setBorder(new LineBorder(new Color(0xA8, 0xD4, 0xFF), 1, true));
        btnCompose.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        pnlSidebarTop.add(btnCompose);
        pnlSidebarTop.add(Box.createVerticalStrut(20));

        // Các mục thư mục bên trái
        setupNavButton(btnNavInbox);
        setupNavButton(btnNavSent);
        setupNavButton(btnNavTrash);

        // Badge unread cho Inbox
        lblInboxBadge.setOpaque(true);
        lblInboxBadge.setBackground(new Color(0xEA, 0x43, 0x35));
        lblInboxBadge.setForeground(Color.WHITE);
        lblInboxBadge.setFont(new Font("Segoe UI", Font.BOLD, 10));
        lblInboxBadge.setBorder(new EmptyBorder(2, 6, 2, 6));

        JPanel pnlInboxRow = new JPanel(new BorderLayout());
        pnlInboxRow.setOpaque(false);
        pnlInboxRow.add(btnNavInbox, BorderLayout.CENTER);
        pnlInboxRow.add(lblInboxBadge, BorderLayout.EAST);
        pnlInboxRow.setMaximumSize(new Dimension(196, 36));

        pnlSidebarTop.add(pnlInboxRow);
        pnlSidebarTop.add(Box.createVerticalStrut(4));
        pnlSidebarTop.add(wrapNav(btnNavSent));
        pnlSidebarTop.add(Box.createVerticalStrut(4));
        pnlSidebarTop.add(wrapNav(btnNavTrash));

        pnlSidebar.add(pnlSidebarTop, BorderLayout.NORTH);

        // Khu vực thông tin máy chủ & nút tạo nhanh dưới cùng Sidebar
        JPanel pnlSidebarBottom = new JPanel();
        pnlSidebarBottom.setLayout(new BoxLayout(pnlSidebarBottom, BoxLayout.Y_AXIS));
        pnlSidebarBottom.setOpaque(false);
        pnlSidebarBottom.setBorder(new EmptyBorder(8, 12, 12, 12));

        JButton btnQuickGmail = new JButton("⚡ Mẫu: Gửi Gmail");
        btnQuickGmail.setMaximumSize(new Dimension(196, 30));
        btnQuickGmail.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        btnQuickGmail.setToolTipText("Mở khung soạn thư gửi tới một địa chỉ @gmail.com");
        btnQuickGmail.addActionListener(e -> quickComposeGmail());

        JButton btnQuickLocal = new JButton("⚡ Mẫu: Gửi Localhost");
        btnQuickLocal.setMaximumSize(new Dimension(196, 30));
        btnQuickLocal.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        btnQuickLocal.setToolTipText("Mở khung soạn thư gửi nội bộ");
        btnQuickLocal.addActionListener(e -> quickComposeLocal());

        pnlSidebarBottom.add(btnQuickGmail);
        pnlSidebarBottom.add(Box.createVerticalStrut(4));
        pnlSidebarBottom.add(btnQuickLocal);
        pnlSidebarBottom.add(Box.createVerticalStrut(10));

        JLabel lblServerHost = new JLabel("<html><small style='color:gray'>Máy chủ: " + service.getHost() + "<br>Domain: "
                + service.getDomain() + "</small></html>");
        pnlSidebarBottom.add(lblServerHost);

        pnlSidebar.add(pnlSidebarBottom, BorderLayout.SOUTH);
        root.add(pnlSidebar, BorderLayout.WEST);

        // ================= 3. CENTER: TOOLBAR + SPLITPANE =================
        JPanel pnlCenter = new JPanel(new BorderLayout());
        pnlCenter.setBackground(Color.WHITE);

        // Toolbar trên danh sách thư
        JPanel pnlToolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        pnlToolbar.setBackground(Color.WHITE);
        pnlToolbar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(0xEA, 0xEA, 0xEA)));

        styleToolButton(btnRefresh);
        styleToolButton(btnDelete);
        styleToolButton(btnToggleLog);

        pnlToolbar.add(btnRefresh);
        pnlToolbar.add(btnDelete);
        pnlToolbar.add(Box.createHorizontalStrut(15));
        pnlToolbar.add(btnToggleLog);

        pnlCenter.add(pnlToolbar, BorderLayout.NORTH);

        // Cấu hình bảng hiển thị danh sách thư
        mailTable.setRowHeight(36);
        mailTable.setShowGrid(false);
        mailTable.setIntercellSpacing(new Dimension(0, 0));
        mailTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        mailTable.setSelectionBackground(new Color(0xE8, 0xF0, 0xFE));
        mailTable.setSelectionForeground(new Color(0x20, 0x21, 0x24));
        mailTable.setFont(new Font("Segoe UI", Font.PLAIN, 13));

        // Cột độ rộng
        mailTable.getColumnModel().getColumn(0).setPreferredWidth(30);
        mailTable.getColumnModel().getColumn(0).setMaxWidth(35);
        mailTable.getColumnModel().getColumn(1).setPreferredWidth(160);
        mailTable.getColumnModel().getColumn(2).setPreferredWidth(260);
        mailTable.getColumnModel().getColumn(3).setPreferredWidth(30);
        mailTable.getColumnModel().getColumn(3).setMaxWidth(35);
        mailTable.getColumnModel().getColumn(4).setPreferredWidth(90);
        mailTable.getColumnModel().getColumn(4).setMaxWidth(120);

        // Renderer tùy biến in đậm cho thư chưa đọc
        mailTable.setDefaultRenderer(Object.class, new MailTableRenderer());

        JScrollPane scrollTable = new JScrollPane(mailTable);
        scrollTable.setBorder(null);
        scrollTable.getViewport().setBackground(Color.WHITE);

        // SplitPane chia ngang: Trái là Danh sách thư, Phải là Chi tiết thư
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, scrollTable, detailPanel);
        splitPane.setDividerLocation(460);
        splitPane.setDividerSize(5);
        splitPane.setBorder(null);

        pnlCenter.add(splitPane, BorderLayout.CENTER);

        // ================= 4. PROTOCOL LOG PANEL (SOCKET CONSOLE) =================
        pnlProtocolLog.setPreferredSize(new Dimension(0, 160));
        pnlProtocolLog.setBorder(new CompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(0xCF, 0xD8, 0xDC)),
                new EmptyBorder(4, 8, 4, 8)
        ));
        pnlProtocolLog.setBackground(new Color(0x26, 0x32, 0x38));

        JPanel pnlLogHeader = new JPanel(new BorderLayout());
        pnlLogHeader.setOpaque(false);
        JLabel lblLogTitle = new JLabel("📡 Nhật ký giao thức Socket POP3 & SMTP thời gian thực");
        lblLogTitle.setForeground(new Color(0x80, 0xDE, 0xEA));
        lblLogTitle.setFont(new Font("Segoe UI", Font.BOLD, 12));

        JButton btnClearLog = new JButton("Xóa log");
        btnClearLog.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        btnClearLog.addActionListener(e -> txtProtocolLog.setText(""));

        pnlLogHeader.add(lblLogTitle, BorderLayout.WEST);
        pnlLogHeader.add(btnClearLog, BorderLayout.EAST);

        txtProtocolLog.setEditable(false);
        txtProtocolLog.setBackground(new Color(0x1E, 0x27, 0x2C));
        txtProtocolLog.setForeground(new Color(0x81, 0xC7, 0x84));
        txtProtocolLog.setFont(new Font("Consolas", Font.PLAIN, 12));

        pnlProtocolLog.add(pnlLogHeader, BorderLayout.NORTH);
        pnlProtocolLog.add(new JScrollPane(txtProtocolLog), BorderLayout.CENTER);
        pnlProtocolLog.setVisible(false);

        pnlCenter.add(pnlProtocolLog, BorderLayout.SOUTH);
        root.add(pnlCenter, BorderLayout.CENTER);

        setContentPane(root);
    }

    private void setupNavButton(JButton btn) {
        btn.setMaximumSize(new Dimension(196, 36));
        btn.setPreferredSize(new Dimension(196, 36));
        btn.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        btn.setHorizontalAlignment(SwingConstants.LEFT);
        btn.setFocusPainted(false);
        btn.setBackground(new Color(0xF6, 0xF8, 0xFC));
        btn.setBorder(new EmptyBorder(6, 12, 6, 12));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    private JPanel wrapNav(JButton btn) {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        p.add(btn, BorderLayout.CENTER);
        p.setMaximumSize(new Dimension(196, 36));
        return p;
    }

    private void styleToolButton(JButton btn) {
        btn.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        btn.setFocusPainted(false);
        btn.setBackground(Color.WHITE);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    private void setupEvents() {
        btnCompose.addActionListener(e -> openCompose(null, null, null, null, null, null));
        btnNavInbox.addActionListener(e -> switchFolder("INBOX"));
        btnNavSent.addActionListener(e -> switchFolder("SENT"));
        btnNavTrash.addActionListener(e -> switchFolder("TRASH"));

        btnRefresh.addActionListener(e -> reloadFolder(true));
        btnDelete.addActionListener(e -> deleteSelectedMail());
        btnToggleLog.addActionListener(e -> {
            logVisible = !logVisible;
            pnlProtocolLog.setVisible(logVisible);
            btnToggleLog.setText(logVisible ? "📡 Ẩn Console Socket" : "📡 Console Socket");
            revalidate();
        });

        // Tìm kiếm thời gian thực
        txtSearch.addActionListener(e -> applyFilter(txtSearch.getText().trim()));

        // Nhấp chọn thư trong bảng để đọc chi tiết
        mailTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int row = mailTable.getSelectedRow();
                if (row >= 0 && row < displayedSummaries.size()) {
                    MailSummary s = displayedSummaries.get(row);
                    loadMailDetail(s, row);
                }
            }
        });

        // Xử lý các tác vụ từ khung xem chi tiết
        detailPanel.setOnReply(mail -> {
            String to = mail.getFrom();
            String subj = mail.getSubject();
            if (!subj.toLowerCase().startsWith("re:")) subj = "Re: " + subj;
            String body = "\n\n--- Vào " + mail.getDate() + ", " + mail.getFrom() + " đã viết: ---\n> "
                    + mail.getDisplayText().replace("\n", "\n> ");
            openCompose(to, "", subj, body, mail.getMessageId(), null);
        });

        detailPanel.setOnForward(mail -> {
            String subj = mail.getSubject();
            if (!subj.toLowerCase().startsWith("fwd:")) subj = "Fwd: " + subj;
            String body = "\n\n---------- Thư chuyển tiếp ----------\nTừ: " + mail.getFrom()
                    + "\nNgày: " + mail.getDate() + "\nTiêu đề: " + mail.getSubject() + "\nĐến: " + mail.getTo()
                    + "\n\n" + mail.getDisplayText();
            openCompose("", "", subj, body, null, mail.getAttachments());
        });

        detailPanel.setOnDelete(this::deleteSelectedMail);
    }

    /** Chuyển thư mục (INBOX / SENT / TRASH) */
    public void switchFolder(String folder) {
        this.currentFolder = folder;
        btnNavInbox.setBackground(folder.equals("INBOX") ? new Color(0xE8, 0xF0, 0xFE) : new Color(0xF6, 0xF8, 0xFC));
        btnNavInbox.setFont(btnNavInbox.getFont().deriveFont(folder.equals("INBOX") ? Font.BOLD : Font.PLAIN));

        btnNavSent.setBackground(folder.equals("SENT") ? new Color(0xE8, 0xF0, 0xFE) : new Color(0xF6, 0xF8, 0xFC));
        btnNavSent.setFont(btnNavSent.getFont().deriveFont(folder.equals("SENT") ? Font.BOLD : Font.PLAIN));

        btnNavTrash.setBackground(folder.equals("TRASH") ? new Color(0xE8, 0xF0, 0xFE) : new Color(0xF6, 0xF8, 0xFC));
        btnNavTrash.setFont(btnNavTrash.getFont().deriveFont(folder.equals("TRASH") ? Font.BOLD : Font.PLAIN));

        detailPanel.clear();
        reloadFolder(true);
    }

    /** Tải lại danh sách thư của thư mục qua lệnh POP3 UIDL + TOP */
    public void reloadFolder(boolean showStatus) {
        if (showStatus) {
            btnRefresh.setEnabled(false);
            btnRefresh.setText("Đang tải...");
        }

        new SwingWorker<List<MailSummary>, Void>() {
            @Override
            protected List<MailSummary> doInBackground() throws Exception {
                return service.listFolder(currentFolder);
            }

            @Override
            protected void done() {
                try {
                    allSummaries = get();
                    applyFilter(txtSearch.getText().trim());
                    updateUnreadBadge();
                } catch (Exception ex) {
                    if (showStatus) {
                        JOptionPane.showMessageDialog(MailClientFrame.this,
                                "Không thể tải danh sách thư qua POP3:\n" + ex.getMessage(),
                                "Lỗi POP3", JOptionPane.ERROR_MESSAGE);
                    }
                } finally {
                    btnRefresh.setEnabled(true);
                    btnRefresh.setText("🔄 Làm mới");
                }
            }
        }.execute();
    }

    private void updateUnreadBadge() {
        if (currentFolder.equals("INBOX")) {
            long unread = allSummaries.stream().filter(s -> !s.read).count();
            lblInboxBadge.setText(String.valueOf(unread));
            lblInboxBadge.setVisible(unread > 0);
        }
    }

    private void applyFilter(String keyword) {
        displayedSummaries.clear();
        tableModel.setRowCount(0);
        String kw = keyword.toLowerCase();

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd/MM HH:mm");

        for (MailSummary s : allSummaries) {
            boolean match = kw.isEmpty()
                    || (s.from != null && s.from.toLowerCase().contains(kw))
                    || (s.to != null && s.to.toLowerCase().contains(kw))
                    || (s.subject != null && s.subject.toLowerCase().contains(kw));
            if (match) {
                displayedSummaries.add(s);
                String senderOrReceiver = currentFolder.equals("SENT") ? "Tới: " + s.to : s.from;
                String timeStr = s.date != null ? s.date.format(dtf) : s.rawDate;
                tableModel.addRow(new Object[]{
                        s.read ? " " : "●",
                        senderOrReceiver,
                        s.subject,
                        s.maybeAttachment ? "📎" : "",
                        timeStr
                });
            }
        }
    }

    /** Tải chi tiết một bức thư bằng lệnh POP3 RETR */
    private void loadMailDetail(MailSummary summary, int rowIndex) {
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        new SwingWorker<ParsedMail, Void>() {
            @Override
            protected ParsedMail doInBackground() throws Exception {
                return service.getMessage(currentFolder, summary.uid);
            }

            @Override
            protected void done() {
                setCursor(Cursor.getDefaultCursor());
                try {
                    ParsedMail pm = get();
                    summary.read = true;
                    tableModel.setValueAt(" ", rowIndex, 0);
                    mailTable.repaint();
                    updateUnreadBadge();

                    // Lấy raw message để cho phép xem thô
                    detailPanel.showMail(pm, currentFolder, summary.uid, "");
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(MailClientFrame.this,
                            "Không thể đọc nội dung thư qua POP3 RETR:\n" + ex.getMessage(),
                            "Lỗi", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    /** Xóa thư đang chọn (POP3 DELE + QUIT) */
    private void deleteSelectedMail() {
        int row = mailTable.getSelectedRow();
        if (row < 0 || row >= displayedSummaries.size()) {
            JOptionPane.showMessageDialog(this, "Hãy chọn một bức thư cần xóa.");
            return;
        }
        MailSummary s = displayedSummaries.get(row);
        String msg = currentFolder.equals("TRASH")
                ? "Bạn có chắc chắn muốn XÓA VĨNH VIỄN bức thư này?"
                : "Chuyển bức thư này vào Thùng rác?";
        int ans = JOptionPane.showConfirmDialog(this, msg, "Xác nhận xóa", JOptionPane.YES_NO_OPTION);
        if (ans != JOptionPane.YES_OPTION) return;

        btnDelete.setEnabled(false);
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                service.delete(currentFolder, Collections.singletonList(s.uid));
                return null;
            }

            @Override
            protected void done() {
                btnDelete.setEnabled(true);
                try {
                    get();
                    detailPanel.clear();
                    reloadFolder(false);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(MailClientFrame.this,
                            "Lỗi xóa thư: " + ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void openCompose(String to, String cc, String subject, String body, String inReplyTo, List<Attachment> atts) {
        ComposeDialog dlg = new ComposeDialog(this, service);
        dlg.prefill(to, cc, subject, body, inReplyTo, atts);
        dlg.setOnSent(() -> {
            if (currentFolder.equals("SENT") || currentFolder.equals("INBOX")) {
                reloadFolder(false);
            }
        });
        dlg.setVisible(true);
    }

    private void quickComposeGmail() {
        ComposeDialog dlg = new ComposeDialog(this, service);
        dlg.prefill("ten_nguoi_nhan@gmail.com", "",
                "Thử nghiệm gửi thư ra Gmail từ LTM Mail Server",
                "Xin chào,\n\nĐây là bức thư được gửi từ tài khoản nội bộ: " + service.getEmail()
                        + " thông qua LTM Mail Server (giao thức SMTP tự cài đặt) và relay qua smtp.gmail.com.\n\n"
                        + "Thời gian: " + java.time.LocalDateTime.now() + "\n\nTrân trọng,\n" + service.getFullName(),
                null, null);
        dlg.setVisible(true);
    }

    private void quickComposeLocal() {
        String target = service.getUsername().equals("alice") ? "bob" : "alice";
        ComposeDialog dlg = new ComposeDialog(this, service);
        dlg.prefill(target + "@" + service.getDomain(), "",
                "Chào " + target + "! Thử nghiệm gửi thư nội bộ",
                "Xin chào " + target + ",\n\nBức thư này được gửi hoàn toàn nội bộ trên máy chủ Localhost qua giao thức SMTP (cổng "
                        + service.getHost() + ") và lưu trữ trong cơ sở dữ liệu MySQL container ltm-mysql.\n\n"
                        + "Chúc bạn làm bài tập tốt!\n" + service.getFullName(),
                null, null);
        dlg.setVisible(true);
    }

    private void logout() {
        if (autoRefreshTimer != null) autoRefreshTimer.stop();
        new LoginFrame().setVisible(true);
        dispose();
    }

    /** Custom Table Cell Renderer: In đậm thư chưa đọc, màu sắc nhã nhặn như Gmail */
    private class MailTableRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            if (row < displayedSummaries.size()) {
                MailSummary s = displayedSummaries.get(row);
                if (!s.read) {
                    c.setFont(c.getFont().deriveFont(Font.BOLD));
                    c.setForeground(new Color(0x20, 0x21, 0x24));
                } else {
                    c.setFont(c.getFont().deriveFont(Font.PLAIN));
                    c.setForeground(new Color(0x5F, 0x63, 0x68));
                }
                if (column == 0) {
                    setForeground(new Color(0x1A, 0x73, 0xE8)); // Chấm xanh/đỏ chưa đọc
                }
            }
            if (!isSelected) {
                c.setBackground(Color.WHITE);
            }
            return c;
        }
    }
}
