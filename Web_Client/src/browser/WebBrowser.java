package browser;

import http1.httpServer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * TRÌNH DUYỆT WEB CLIENT (BROWSER) TƯƠNG TỰ CHROME / CỐC CỐC
 * - Giao thức HTTP/1.1 cài đặt trực tiếp trên TCP Socket (java.net.Socket)
 * - Đầy đủ phương thức: GET, HEAD, POST
 * - Quản lý Lịch sử duyệt web (History - Ctrl+H)
 * - Quản lý Tải xuống (Download Manager - Ctrl+J)
 * - Đa thẻ (Multi-tabs), Thanh địa chỉ & Tìm kiếm Google (Omnibox), Dấu trang (Bookmarks), DevTools (F12)
 */
public class WebBrowser extends JFrame {

    private static volatile int serverPort = httpServer.DEFAULT_PORT;

    public static int getServerPort() {
        return serverPort;
    }

    public static String getHomeUrl() {
        return "http://localhost:" + serverPort + "/";
    }

    private JTabbedPane tabbedPane;
    private JPanel bookmarkBar;
    private final List<BrowserTab> tabs = new ArrayList<>();
    private ServerSocket activeServerSocket = null;

    public WebBrowser() {
        super("Google Chrome / Cốc Cốc - LTM Web Client Browser (HTTP/1.1: GET, HEAD, POST, History, Download)");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1160, 780);
        setMinimumSize(new Dimension(860, 540));
        setLocationRelativeTo(null);

        startLocalServer();

        initMenuBar();
        initUI();
        registerShortcuts();

        try {
            Thread.sleep(100);
        } catch (InterruptedException ignored) {}

        addNewTab(getHomeUrl());
    }

    private void initMenuBar() {
        JMenuBar menuBar = new JMenuBar();

        // 1. MENU TRÌNH DUYỆT
        JMenu menuFile = new JMenu("Trình duyệt (Browser)");
        JMenuItem itemNewTab = new JMenuItem("Thẻ Google mới (New Tab)");
        itemNewTab.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_T, ActionEvent.CTRL_MASK));
        itemNewTab.addActionListener(e -> addNewTab(getHomeUrl()));

        JMenuItem itemCloseTab = new JMenuItem("Đóng thẻ hiện tại (Close Tab)");
        itemCloseTab.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_W, ActionEvent.CTRL_MASK));
        itemCloseTab.addActionListener(e -> closeCurrentTab());

        JMenuItem itemExit = new JMenuItem("Thoát trình duyệt");
        itemExit.addActionListener(e -> System.exit(0));

        menuFile.add(itemNewTab);
        menuFile.add(itemCloseTab);
        menuFile.addSeparator();
        menuFile.add(itemExit);
        menuBar.add(menuFile);

        // 2. MENU GIAO THỨC HTTP (GET, HEAD, POST)
        JMenu menuHttp = new JMenu("Giao thức HTTP (GET / HEAD / POST)");
        JMenuItem itemGet = new JMenuItem("Thực hiện HTTP GET (Tải trang hiện tại)");
        itemGet.addActionListener(e -> {
            BrowserTab t = getActiveTab();
            if (t != null) t.navigate(t.getCurrentUrl(), "GET", false);
        });

        JMenuItem itemHead = new JMenuItem("Thực hiện HTTP HEAD (Chỉ lấy HTTP Response Headers)");
        itemHead.addActionListener(e -> {
            BrowserTab t = getActiveTab();
            if (t != null) t.navigate(t.getCurrentUrl(), "HEAD", false);
        });

        JMenuItem itemPost = new JMenuItem("Mở công cụ gửi HTTP POST (Post Request Builder)...");
        itemPost.addActionListener(e -> {
            BrowserTab t = getActiveTab();
            String u = getHomeUrl() + "api/submit";
            PostDialog dlg = new PostDialog(this, u, res -> {
                if (t != null) t.displayResponse(res);
            });
            dlg.setVisible(true);
        });

        menuHttp.add(itemGet);
        menuHttp.add(itemHead);
        menuHttp.add(itemPost);
        menuBar.add(menuHttp);

        // 3. MENU LỊCH SỬ & TẢI XUỐNG (HISTORY & DOWNLOAD)
        JMenu menuTools = new JMenu("Lịch sử & Tải xuống");

        JMenuItem itemHistory = new JMenuItem("Lịch sử duyệt web (History)");
        itemHistory.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_H, ActionEvent.CTRL_MASK));
        itemHistory.addActionListener(e -> {
            HistoryDialog dlg = new HistoryDialog(this, url -> {
                BrowserTab t = getActiveTab();
                if (t != null) t.navigate(url, "GET", false);
            });
            dlg.setVisible(true);
        });

        JMenuItem itemDownloads = new JMenuItem("Trình quản lý Tải xuống (Downloads)");
        itemDownloads.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_J, ActionEvent.CTRL_MASK));
        itemDownloads.addActionListener(e -> DownloadManager.showManagerDialog(this));

        JMenuItem itemDownloadCurrent = new JMenuItem("Lưu trang hiện tại về máy (Save Page As...)");
        itemDownloadCurrent.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, ActionEvent.CTRL_MASK));
        itemDownloadCurrent.addActionListener(e -> {
            BrowserTab t = getActiveTab();
            if (t != null) DownloadManager.startDownload(this, t.getCurrentUrl());
        });

        JMenuItem itemDns = new JMenuItem("Phân tích URL & Phân giải DNS IP (url.diachi & url.inet)");
        itemDns.addActionListener(e -> {
            BrowserTab t = getActiveTab();
            String u = t != null ? t.getCurrentUrl() : getHomeUrl();
            new DnsInspectorDialog(this, u).setVisible(true);
        });

        JMenuItem itemDevTools = new JMenuItem("Bật/Tắt Chrome DevTools (Network & HTTP Inspector)");
        itemDevTools.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F12, 0));
        itemDevTools.addActionListener(e -> {
            BrowserTab t = getActiveTab();
            if (t != null) t.toggleDevTools();
        });

        JCheckBoxMenuItem itemSocketMode = new JCheckBoxMenuItem(
                "Ưu tiên kết nối TCP Socket thuần cho HTTP/1.1", HttpEngine.isUseRawSocketForHttp());
        itemSocketMode.addActionListener(e -> HttpEngine.setUseRawSocketForHttp(itemSocketMode.isSelected()));

        menuTools.add(itemHistory);
        menuTools.add(itemDownloads);
        menuTools.add(itemDownloadCurrent);
        menuTools.addSeparator();
        menuTools.add(itemDns);
        menuTools.add(itemDevTools);
        menuTools.addSeparator();
        menuTools.add(itemSocketMode);
        menuBar.add(menuTools);

        // 4. MENU KIỂM THỬ & TRỢ GIÚP
        JMenu menuHelp = new JMenu("Kiểm thử & Trợ giúp");
        JMenuItem itemAutoTest = new JMenuItem("Chạy Kiểm thử Tự động Web Client (8/8 Tiêu chí)");
        itemAutoTest.addActionListener(e -> runAutoTest());

        JMenuItem itemAbout = new JMenuItem("Thông tin Trình duyệt Web Client");
        itemAbout.addActionListener(e -> JOptionPane.showMessageDialog(this,
                "=== TRÌNH DUYỆT WEB CLIENT (TƯƠNG TỰ CHROME / CỐC CỐC) ===\n\n"
                        + "1. Giao thức HTTP/1.1 cài đặt trực tiếp trên TCP Socket (java.net.Socket).\n"
                        + "2. Hỗ trợ đầy đủ 3 phương thức: GET, HEAD, POST.\n"
                        + "3. Tích hợp Trang chủ & Tìm kiếm Google (GET /search?q=...).\n"
                        + "4. Quản lý Lịch sử duyệt web (History - Ctrl+H): Lưu vết, lọc & tìm kiếm.\n"
                        + "5. Quản lý Tải xuống (Downloads - Ctrl+J): Tải trang web / tài liệu qua HTTP Socket.\n"
                        + "6. Đa thẻ (Multi-tabs), Dấu trang (Bookmarks), Chrome DevTools (F12).",
                "Giới thiệu Web Client Browser", JOptionPane.INFORMATION_MESSAGE));

        menuHelp.add(itemAutoTest);
        menuHelp.addSeparator();
        menuHelp.add(itemAbout);
        menuBar.add(menuHelp);

        setJMenuBar(menuBar);
    }

    private void initUI() {
        JPanel mainContainer = new JPanel(new BorderLayout());

        // THANH DẤU TRANG & LỐI TẮT TRÌNH DUYỆT (BOOKMARK BAR)
        JPanel topBarWrapper = new JPanel(new BorderLayout(8, 0));
        topBarWrapper.setBackground(new Color(241, 243, 244));
        topBarWrapper.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(218, 220, 224)),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)
        ));

        bookmarkBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));
        bookmarkBar.setOpaque(false);

        addBookmarkButton("Trang chủ Google", getHomeUrl());
        addBookmarkButton("Tìm kiếm Google (GET)", getHomeUrl() + "search?q=Giao+thuc+HTTP+GET+HEAD+POST");
        addBookmarkButton("Biểu mẫu HTTP POST", getHomeUrl() + "post_test.html");
        addBookmarkButton("Tài liệu HTTP/1.1", getHomeUrl() + "about_http.html");
        addBookmarkButton("Example.com", "http://example.com");
        addBookmarkButton("CERN (Web đầu tiên)", "http://info.cern.ch");

        JButton btnAddBookmark = UiUtil.createOutlineButton("+ Lưu dấu trang", new Color(195, 205, 220), new Color(26, 115, 232));
        btnAddBookmark.setToolTipText("Lưu trang hiện tại vào thanh dấu trang của trình duyệt");
        btnAddBookmark.addActionListener(e -> {
            BrowserTab t = getActiveTab();
            if (t != null) {
                String title = JOptionPane.showInputDialog(this, "Nhập tên dấu trang:", "Dấu trang mới");
                if (title != null && !title.trim().isEmpty()) {
                    addBookmarkButton(title.trim(), t.getCurrentUrl());
                    bookmarkBar.revalidate();
                    bookmarkBar.repaint();
                }
            }
        });
        bookmarkBar.add(btnAddBookmark);

        JPanel rightQuick = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 2));
        rightQuick.setOpaque(false);

        JButton btnNewTab = UiUtil.createColoredButton(" + Thẻ mới (Ctrl+T) ", new Color(26, 115, 232), Color.WHITE, 6);
        btnNewTab.setToolTipText("Mở thêm một thẻ trình duyệt mới");
        btnNewTab.addActionListener(e -> addNewTab(getHomeUrl()));

        JButton btnAutoTest = UiUtil.createColoredButton(" Tự động kiểm thử (8/8) ", new Color(19, 115, 51), Color.WHITE, 6);
        btnAutoTest.setToolTipText("Chạy tự động kiểm thử 8 tiêu chí của Web Client Browser");
        btnAutoTest.addActionListener(e -> runAutoTest());

        rightQuick.add(btnNewTab);
        rightQuick.add(btnAutoTest);

        topBarWrapper.add(bookmarkBar, BorderLayout.CENTER);
        topBarWrapper.add(rightQuick, BorderLayout.EAST);

        mainContainer.add(topBarWrapper, BorderLayout.NORTH);

        tabbedPane = new JTabbedPane();
        tabbedPane.setFont(new Font("Segoe UI", Font.PLAIN, 12));

        mainContainer.add(tabbedPane, BorderLayout.CENTER);
        setContentPane(mainContainer);
    }

    private void openUrl(String url) {
        BrowserTab t = getActiveTab();
        if (t != null) {
            t.navigate(url, "GET", false);
        } else {
            addNewTab(url);
        }
    }

    private void addBookmarkButton(String title, String url) {
        JButton b = UiUtil.createOutlineButton(title, new Color(210, 218, 228), new Color(30, 41, 59));
        b.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        b.setToolTipText(url);
        b.addActionListener(e -> openUrl(url));
        bookmarkBar.add(b);
    }

    public void addNewTab(String url) {
        BrowserTab[] tabHolder = new BrowserTab[1];
        BrowserTab tab = new BrowserTab(this, url, title -> {
            if (tabHolder[0] != null) {
                int idx = tabbedPane.indexOfComponent(tabHolder[0]);
                if (idx != -1) {
                    updateTabHeader(idx, title);
                }
            }
        });
        tabHolder[0] = tab;
        tabs.add(tab);
        tabbedPane.addTab("Thẻ mới", tab);
        int newIdx = tabbedPane.getTabCount() - 1;
        tabbedPane.setTabComponentAt(newIdx, createTabHeaderComponent(tab, "Google - Thẻ mới"));
        tabbedPane.setSelectedIndex(newIdx);
    }

    private Component createTabHeaderComponent(BrowserTab tab, String initialTitle) {
        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        header.setOpaque(false);

        JLabel lblTitle = new JLabel(initialTitle);
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblTitle.setForeground(new Color(32, 33, 36));

        JButton btnClose = new JButton(" × ");
        btnClose.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnClose.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));
        btnClose.setContentAreaFilled(false);
        btnClose.setFocusPainted(false);
        btnClose.setForeground(new Color(95, 99, 104));
        btnClose.setToolTipText("Đóng thẻ này (Ctrl+W)");
        btnClose.addActionListener(e -> {
            int idx = tabbedPane.indexOfComponent(tab);
            if (idx != -1) {
                closeTabAt(idx);
            }
        });

        header.add(lblTitle);
        header.add(btnClose);
        return header;
    }

    private void updateTabHeader(int index, String title) {
        if (title != null && title.length() > 26) {
            title = title.substring(0, 23) + "...";
        }
        Component c = tabbedPane.getTabComponentAt(index);
        if (c instanceof JPanel) {
            JPanel p = (JPanel) c;
            if (p.getComponentCount() > 0 && p.getComponent(0) instanceof JLabel) {
                ((JLabel) p.getComponent(0)).setText(title != null ? title : "Thẻ Google");
            }
        }
    }

    public void closeCurrentTab() {
        int idx = tabbedPane.getSelectedIndex();
        if (idx != -1) {
            closeTabAt(idx);
        }
    }

    private void closeTabAt(int idx) {
        if (tabbedPane.getTabCount() <= 1) {
            BrowserTab t = getActiveTab();
            if (t != null) t.navigate(getHomeUrl(), "GET", false);
            return;
        }
        Component comp = tabbedPane.getComponentAt(idx);
        if (comp instanceof BrowserTab) {
            tabs.remove(comp);
        }
        tabbedPane.remove(idx);
    }

    private BrowserTab getActiveTab() {
        Component c = tabbedPane.getSelectedComponent();
        if (c instanceof BrowserTab) {
            return (BrowserTab) c;
        }
        return null;
    }

    private void registerShortcuts() {
        JComponent root = getRootPane();

        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0), "RELOAD");
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_R, ActionEvent.CTRL_MASK), "RELOAD");
        root.getActionMap().put("RELOAD", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                BrowserTab t = getActiveTab();
                if (t != null) t.reload();
            }
        });
    }

    /**
     * Chạy tự động kiểm thử 8 tiêu chí cốt lõi của đề tài Web Client (Browser).
     */
    private void runAutoTest() {
        startLocalServer();

        javax.swing.JDialog dlg = new javax.swing.JDialog(this, "Kết Quả Kiểm Thử Tự Động Web Client Browser (8/8 Tiêu Chí)", true);
        dlg.setSize(700, 480);
        dlg.setLocationRelativeTo(this);
        dlg.setLayout(new BorderLayout(10, 10));

        javax.swing.JTextArea txtReport = new javax.swing.JTextArea("Đang thực thi 8 ca kiểm thử tự động cho Web Client Browser...\n");
        txtReport.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        txtReport.setEditable(false);
        txtReport.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
        dlg.add(new javax.swing.JScrollPane(txtReport), BorderLayout.CENTER);

        JPanel bot = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnClose = UiUtil.createColoredButton(" Đóng báo cáo ", new Color(26, 115, 232), Color.WHITE, 6);
        btnClose.setEnabled(false);
        btnClose.addActionListener(ev -> dlg.setVisible(false));
        bot.add(btnClose);
        dlg.add(bot, BorderLayout.SOUTH);

        new Thread(() -> {
            StringBuilder sb = new StringBuilder();
            sb.append("======================================================================\n");
            sb.append("   BÁO CÁO KIỂM THỬ TRÌNH DUYỆT WEB CLIENT (CHROME / CỐC CỐC - HTTP/1.1)\n");
            sb.append("======================================================================\n\n");
            int passed = 0;
            int total = 8;
            String base = getHomeUrl();

            // 1. Giao thức HTTP/1.1 & Phương thức GET Trang chủ
            try {
                HttpResponse r = HttpEngine.execute(new HttpRequest(base + "index.html", "GET"));
                if (r.getStatusCode() == 200 && r.getStringBody().contains("Google")) {
                    sb.append("  [ĐẠT] 1. Giao thức HTTP/1.1 (TCP Socket) & GET Trang chủ Google (200 OK)\n");
                    passed++;
                } else {
                    throw new Exception("HTTP " + r.getStatusCode());
                }
            } catch (Exception e) {
                sb.append("  [LỖI] 1. HTTP GET Trang chủ: ").append(e.getMessage()).append("\n");
            }

            // 2. Phương thức HTTP GET (Tìm kiếm Google với Query Params)
            try {
                HttpResponse r = HttpEngine.execute(new HttpRequest(base + "search?q=Lap+Trinh+Mang", "GET"));
                if (r.getStatusCode() == 200 && r.getStringBody().contains("Lap Trinh Mang")) {
                    sb.append("  [ĐẠT] 2. Phương thức HTTP GET: Tìm kiếm Google (/search?q=Lap+Trinh+Mang)\n");
                    passed++;
                } else {
                    throw new Exception("HTTP " + r.getStatusCode());
                }
            } catch (Exception e) {
                sb.append("  [LỖI] 2. HTTP GET Tìm kiếm: ").append(e.getMessage()).append("\n");
            }

            // 3. Phương thức HTTP HEAD
            try {
                HttpResponse r = HttpEngine.execute(new HttpRequest(base + "about_http.html", "HEAD"));
                if (r.getStatusCode() == 200 && r.getBodyBytes().length == 0 && r.getContentLength() > 0) {
                    sb.append("  [ĐẠT] 3. Phương thức HTTP HEAD: Chỉ nhận Response Headers (Body = 0 bytes)\n");
                    passed++;
                } else {
                    throw new Exception("HTTP " + r.getStatusCode());
                }
            } catch (Exception e) {
                sb.append("  [LỖI] 3. HTTP HEAD: ").append(e.getMessage()).append("\n");
            }

            // 4. Phương thức HTTP POST (Form UrlEncoded UTF-8)
            try {
                HttpRequest req = new HttpRequest(base + "api/submit", "POST");
                req.setContentType("application/x-www-form-urlencoded");
                req.setBody("hoten=Nguyen+Van+A&masv=21IT001".getBytes(java.nio.charset.StandardCharsets.UTF_8));
                HttpResponse r = HttpEngine.execute(req);
                if (r.getStatusCode() == 200 && r.getStringBody().contains("Nguyen Van A")) {
                    sb.append("  [ĐẠT] 4. Phương thức HTTP POST (Form UrlEncoded): Gửi & nhận phản hồi 200 OK\n");
                    passed++;
                } else {
                    throw new Exception("HTTP " + r.getStatusCode());
                }
            } catch (Exception e) {
                sb.append("  [LỖI] 4. HTTP POST (Form): ").append(e.getMessage()).append("\n");
            }

            // 5. Phương thức HTTP POST (JSON Payload UTF-8)
            try {
                HttpRequest req = new HttpRequest(base + "api/json", "POST");
                req.setContentType("application/json; charset=UTF-8");
                req.setBody("{\"hoten\":\"Tran Binh\",\"lop\":\"LTM\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
                HttpResponse r = HttpEngine.execute(req);
                if (r.getStatusCode() == 200 && r.getStringBody().contains("Tran Binh")) {
                    sb.append("  [ĐẠT] 5. Phương thức HTTP POST (JSON Payload): Gửi & nhận phản hồi 200 OK\n");
                    passed++;
                } else {
                    throw new Exception("HTTP " + r.getStatusCode());
                }
            } catch (Exception e) {
                sb.append("  [LỖI] 5. HTTP POST (JSON): ").append(e.getMessage()).append("\n");
            }

            // 6. Xử lý mã lỗi HTTP 404 Not Found & Phân giải DNS IP
            try {
                HttpResponse r = HttpEngine.execute(new HttpRequest(base + "duong_dan_khong_ton_tai.html", "GET"));
                UrlDnsInfo info = UrlDnsInfo.analyze(base);
                if (r.getStatusCode() == 404 && "localhost".equals(info.getHost()) && info.getPrimaryIp() != null) {
                    sb.append("  [ĐẠT] 6. Xử lý mã trạng thái HTTP 404 Not Found & Phân giải DNS IP\n");
                    passed++;
                } else {
                    throw new Exception("HTTP " + r.getStatusCode());
                }
            } catch (Exception e) {
                sb.append("  [LỖI] 6. HTTP 404 & DNS: ").append(e.getMessage()).append("\n");
            }

            // 7. Quản lý Lịch sử duyệt web (History Manager)
            try {
                HistoryManager.add(base + "autotest_history", "AutoTest History", "GET");
                if (!HistoryManager.search("autotest_history", "GET").isEmpty()) {
                    sb.append("  [ĐẠT] 7. Quản lý Lịch sử duyệt web (History - Ctrl+H): Lưu vết & tìm kiếm\n");
                    passed++;
                } else {
                    throw new Exception("Không tìm thấy lịch sử");
                }
            } catch (Exception e) {
                sb.append("  [LỖI] 7. History: ").append(e.getMessage()).append("\n");
            }

            // 8. Quản lý Tải xuống (Download Manager)
            try {
                java.io.File temp = java.io.File.createTempFile("ltm_browser_dl_", ".html");
                temp.deleteOnExit();
                HttpEngine.downloadToFile(base + "about_http.html", temp, null);
                if (temp.exists() && temp.length() > 0) {
                    sb.append("  [ĐẠT] 8. Quản lý Tải xuống (Download - Ctrl+J): Tải trang qua HTTP Socket\n");
                    passed++;
                } else {
                    throw new Exception("File tải về rỗng");
                }
            } catch (Exception e) {
                sb.append("  [LỖI] 8. Download: ").append(e.getMessage()).append("\n");
            }

            sb.append("\n======================================================================\n");
            sb.append("   KẾT LUẬN: ĐẠT ").append(passed).append(" / ").append(total).append(" TIÊU CHÍ CỦA ĐỀ TÀI WEB CLIENT (BROWSER)!\n");
            sb.append("======================================================================\n");

            SwingUtilities.invokeLater(() -> {
                txtReport.setText(sb.toString());
                txtReport.setCaretPosition(0);
                btnClose.setEnabled(true);
            });
        }).start();

        dlg.setVisible(true);
    }

    /**
     * Luôn khởi tạo ServerSocket riêng cho tiến trình trình duyệt hiện tại
     * (tránh xung đột với các ứng dụng khác đang chiếm cổng 8080 trên máy).
     */
    public synchronized void startLocalServer() {
        if (activeServerSocket != null && !activeServerSocket.isClosed()) {
            return;
        }
        int[] candidatePorts = {8899, 8898, 8897, 9088, 0};
        for (int p : candidatePorts) {
            try {
                activeServerSocket = new ServerSocket(p);
                serverPort = activeServerSocket.getLocalPort();
                break;
            } catch (IOException ignored) {}
        }
        if (activeServerSocket == null) {
            return;
        }

        Thread serverAcceptThread = new Thread(() -> {
            while (activeServerSocket != null && !activeServerSocket.isClosed()) {
                try {
                    Socket client = activeServerSocket.accept();
                    new Thread(new httpServer(client)).start();
                } catch (IOException ex) {
                    break;
                }
            }
        }, "LocalHttpServer-Thread");
        serverAcceptThread.setDaemon(true);
        serverAcceptThread.start();
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        SwingUtilities.invokeLater(() -> {
            WebBrowser browser = new WebBrowser();
            browser.setVisible(true);
        });
    }
}
