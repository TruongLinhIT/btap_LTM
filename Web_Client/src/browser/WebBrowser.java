package browser;

import http1.httpServer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
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
 * TRINH DUYET WEB CLIENT (BROWSER) - MON HOC LAP TRINH MANG
 * Giao dien va tinh nang tuong tu Chrome hoac Coc Coc:
 * - Da the (Multi-tabs) voi nut them the (+) va dong the (x)
 * - Thanh dia chi thong minh, cac nut Back, Forward, Reload, Home
 * - Ho tro day du cac phuong thuc HTTP: GET, HEAD, POST (co builder)
 * - Quan ly Lich su duyet web (History) tim kiem va luu tru
 * - Quan ly Tai xuong (Download Manager) voi tien do va mo file (.pdf, .mp3, .mp4, ...)
 * - Tich hop Developer Tools (F12) kiem tra HTTP Headers, Status, Latency
 * - Tich hop module phan tich URL & DNS IP (ke thua tu url.diachi va url.inet)
 * - Tich hop san Server Controller khoi dong Web Server cong 8080 ngay trong trinh duyet
 */
public class WebBrowser extends JFrame {

    private JTabbedPane tabbedPane;
    private JPanel bookmarkBar;
    private List<BrowserTab> tabs = new ArrayList<>();
    private Thread serverThread = null;
    private ServerSocket activeServerSocket = null;
    private JLabel lblServerStatus;

    public WebBrowser() {
        super("LTM Web Browser - Trình duyệt mạng (Tương tự Chrome / Cốc Cốc)");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1100, 750);
        setMinimumSize(new Dimension(800, 500));
        setLocationRelativeTo(null);

        initMenuBar();
        initUI();
        registerShortcuts();

        // Mo the mac dinh
        addNewTab("http://localhost:8080/");
    }

    private void initMenuBar() {
        JMenuBar menuBar = new JMenuBar();

        // 1. MENU TEP (File)
        JMenu menuFile = new JMenu("Tệp (File)");
        JMenuItem itemNewTab = new JMenuItem("Thẻ mới (New Tab)");
        itemNewTab.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_T, ActionEvent.CTRL_MASK));
        itemNewTab.addActionListener(e -> addNewTab("http://localhost:8080/"));

        JMenuItem itemCloseTab = new JMenuItem("Đóng thẻ hiện tại (Close Tab)");
        itemCloseTab.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_W, ActionEvent.CTRL_MASK));
        itemCloseTab.addActionListener(e -> closeCurrentTab());

        JMenuItem itemExit = new JMenuItem("Thoát");
        itemExit.addActionListener(e -> System.exit(0));

        menuFile.add(itemNewTab);
        menuFile.add(itemCloseTab);
        menuFile.addSeparator();
        menuFile.add(itemExit);
        menuBar.add(menuFile);

        // 2. MENU HTTP METHODS
        JMenu menuHttp = new JMenu("Phương thức HTTP");
        JMenuItem itemGet = new JMenuItem("Thực hiện HTTP GET");
        itemGet.addActionListener(e -> {
            BrowserTab t = getActiveTab();
            if (t != null) t.navigate(t.getCurrentUrl(), "GET", false);
        });

        JMenuItem itemHead = new JMenuItem("Thực hiện HTTP HEAD (Chỉ lấy Header)");
        itemHead.addActionListener(e -> {
            BrowserTab t = getActiveTab();
            if (t != null) t.navigate(t.getCurrentUrl(), "HEAD", false);
        });

        JMenuItem itemPost = new JMenuItem("Mở công cụ gửi HTTP POST (Post Dialog)...");
        itemPost.addActionListener(e -> {
            BrowserTab t = getActiveTab();
            String u = t != null ? t.getCurrentUrl() : "http://localhost:8080/api/submit";
            PostDialog dlg = new PostDialog(this, u, res -> {
                if (t != null) t.displayResponse(res);
            });
            dlg.setVisible(true);
        });

        menuHttp.add(itemGet);
        menuHttp.add(itemHead);
        menuHttp.add(itemPost);
        menuBar.add(menuHttp);

        // 3. MENU CONG CU (Tools)
        JMenu menuTools = new JMenu("Công cụ (Tools)");

        JMenuItem itemHistory = new JMenuItem("Lịch sử duyệt web (History)");
        itemHistory.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_H, ActionEvent.CTRL_MASK));
        itemHistory.addActionListener(e -> {
            HistoryDialog dlg = new HistoryDialog(this, url -> {
                BrowserTab t = getActiveTab();
                if (t != null) t.navigate(url, "GET", false);
            });
            dlg.setVisible(true);
        });

        JMenuItem itemDownloads = new JMenuItem("Trình quản lý tải xuống (Downloads)");
        itemDownloads.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_J, ActionEvent.CTRL_MASK));
        itemDownloads.addActionListener(e -> DownloadManager.showManagerDialog(this));

        JMenuItem itemDns = new JMenuItem("Phân tích URL & DNS IP (url.diachi & url.inet)");
        itemDns.addActionListener(e -> {
            BrowserTab t = getActiveTab();
            String u = t != null ? t.getCurrentUrl() : "http://vku.udn.vn";
            new DnsInspectorDialog(this, u).setVisible(true);
        });

        JMenuItem itemDevTools = new JMenuItem("Bật/Tắt DevTools HTTP Inspector");
        itemDevTools.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F12, 0));
        itemDevTools.addActionListener(e -> {
            BrowserTab t = getActiveTab();
            if (t != null) t.toggleDevTools();
        });

        JCheckBoxMenuItem itemSocketMode = new JCheckBoxMenuItem("Ưu tiên Socket TCP thuan (LTM Engine)", HttpEngine.isUseRawSocketForHttp());
        itemSocketMode.addActionListener(e -> HttpEngine.setUseRawSocketForHttp(itemSocketMode.isSelected()));

        menuTools.add(itemHistory);
        menuTools.add(itemDownloads);
        menuTools.addSeparator();
        menuTools.add(itemDns);
        menuTools.add(itemDevTools);
        menuTools.addSeparator();
        menuTools.add(itemSocketMode);
        menuBar.add(menuTools);

        // 4. MENU MAY CHU (Web Server)
        JMenu menuServer = new JMenu("Máy chủ (Web Server)");
        JMenuItem itemStartServer = new JMenuItem("Khởi động Web Server (Cổng 8080)");
        itemStartServer.addActionListener(e -> startLocalServer());

        JMenuItem itemStopServer = new JMenuItem("Dừng Web Server");
        itemStopServer.addActionListener(e -> stopLocalServer());

        menuServer.add(itemStartServer);
        menuServer.add(itemStopServer);
        menuBar.add(menuServer);

        // 5. MENU TRO GIUP (Help)
        JMenu menuHelp = new JMenu("Trợ giúp (Help)");
        JMenuItem itemAbout = new JMenuItem("Thông tin đồ án LTM");
        itemAbout.addActionListener(e -> {
            JOptionPane.showMessageDialog(this,
                    "=== ĐỒ ÁN MÔN HỌC LẬP TRÌNH MẠNG ===\n" +
                            "Đề tài: Web Client (Browser) & Web Server\n\n" +
                            "CÁC TÍNH NĂNG ĐÃ TRIỂN KHAI HOÀN CHỈNH:\n" +
                            "1. Giao thức HTTP: Đầy đủ GET, HEAD, POST.\n" +
                            "2. Đa phương tiện & Dịch vụ Server: .pdf, .mp3, .mp4, .png, .wav, .txt, .html.\n" +
                            "3. Lịch sử duyệt web (History): Tìm kiếm, lưu vết, mở lại.\n" +
                            "4. Tải xuống (Download): Tiến độ tải %, mở tệp, mở thư mục.\n" +
                            "5. Đa thẻ (Tabs): Đóng/mở thẻ như Chrome/Cốc Cốc.\n" +
                            "6. Developer Tools (F12): Xem HTTP Headers, Status, Latency.\n" +
                            "7. Kế thừa url.diachi & url.inet: Phân tích URL và DNS InetAddress.\n" +
                            "8. Động cơ mạng: Cả Socket TCP thuần và HttpURLConnection.",
                    "Giới thiệu LTM Web Browser", JOptionPane.INFORMATION_MESSAGE);
        });
        menuHelp.add(itemAbout);
        menuBar.add(menuHelp);

        setJMenuBar(menuBar);
    }

    private void initUI() {
        JPanel mainContainer = new JPanel(new BorderLayout());

        // 1. THANH DAU TRANG (Bookmark Bar tuong tu Chrome/Coc Coc)
        bookmarkBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 3));
        bookmarkBar.setBackground(new Color(241, 243, 244));
        bookmarkBar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(218, 220, 224)));

        addBookmarkButton("🏠 Localhost Server", "http://localhost:8080/");
        addBookmarkButton("📄 sample.pdf", "http://localhost:8080/sample.pdf");
        addBookmarkButton("🎵 sample.mp3", "http://localhost:8080/sample.mp3");
        addBookmarkButton("🎬 sample.mp4", "http://localhost:8080/sample.mp4");
        addBookmarkButton("🖼 sample.png", "http://localhost:8080/sample.png");
        addBookmarkButton("📝 sample.txt", "http://localhost:8080/sample.txt");
        addBookmarkButton("🎓 VKU Portal", "http://vku.udn.vn");

        JButton btnAddBookmark = new JButton("+ Thêm dấu trang");
        btnAddBookmark.setFont(new Font("SansSerif", Font.PLAIN, 11));
        btnAddBookmark.setFocusPainted(false);
        btnAddBookmark.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnAddBookmark.addActionListener(e -> {
            BrowserTab t = getActiveTab();
            if (t != null) {
                String title = JOptionPane.showInputDialog(this, "Nhập tên dấu trang:", "Dấu trang mới");
                if (title != null && !title.trim().isEmpty()) {
                    addBookmarkButton("⭐ " + title.trim(), t.getCurrentUrl());
                    bookmarkBar.revalidate();
                    bookmarkBar.repaint();
                }
            }
        });
        bookmarkBar.add(btnAddBookmark);

        // Nhan trang thai Server tren thanh Bookmark
        lblServerStatus = new JLabel(" |  Web Server: Chưa chạy");
        lblServerStatus.setFont(new Font("SansSerif", Font.PLAIN, 11));
        lblServerStatus.setForeground(new Color(120, 120, 120));
        bookmarkBar.add(lblServerStatus);

        JButton btnToggleServer = new JButton("Bật Server 8080");
        btnToggleServer.setFont(new Font("SansSerif", Font.PLAIN, 11));
        btnToggleServer.addActionListener(e -> {
            if (activeServerSocket == null) {
                startLocalServer();
                btnToggleServer.setText("Tắt Server");
            } else {
                stopLocalServer();
                btnToggleServer.setText("Bật Server 8080");
            }
        });
        bookmarkBar.add(btnToggleServer);

        mainContainer.add(bookmarkBar, BorderLayout.NORTH);

        // 2. TABBED PANE (Multi-tabs)
        tabbedPane = new JTabbedPane();
        tabbedPane.setFont(new Font("SansSerif", Font.PLAIN, 12));

        mainContainer.add(tabbedPane, BorderLayout.CENTER);
        setContentPane(mainContainer);
    }

    private void addBookmarkButton(String title, String url) {
        JButton b = new JButton(title);
        b.setFont(new Font("SansSerif", Font.PLAIN, 11));
        b.setFocusPainted(false);
        b.setCursor(new Cursor(Cursor.HAND_CURSOR));
        b.setToolTipText(url);
        b.addActionListener(e -> {
            BrowserTab t = getActiveTab();
            if (t != null) {
                t.navigate(url, "GET", false);
            } else {
                addNewTab(url);
            }
        });
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
        tabbedPane.addTab("Đang tải...", tab);
        int newIdx = tabbedPane.getTabCount() - 1;
        tabbedPane.setTabComponentAt(newIdx, createTabHeaderComponent(tab, "Trang mới"));
        tabbedPane.setSelectedIndex(newIdx);
    }

    private Component createTabHeaderComponent(BrowserTab tab, String initialTitle) {
        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        header.setOpaque(false);

        JLabel lblTitle = new JLabel(initialTitle);
        lblTitle.setFont(new Font("SansSerif", Font.PLAIN, 12));

        JButton btnClose = new JButton("×");
        btnClose.setFont(new Font("SansSerif", Font.BOLD, 13));
        btnClose.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));
        btnClose.setContentAreaFilled(false);
        btnClose.setFocusPainted(false);
        btnClose.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnClose.setToolTipText("Đóng thẻ này");
        btnClose.addActionListener(e -> closeTab(tab));

        header.add(lblTitle);
        header.add(btnClose);

        return header;
    }

    private void updateTabHeader(int index, String newTitle) {
        if (newTitle == null || newTitle.isEmpty()) newTitle = "Trang web";
        if (newTitle.length() > 18) {
            newTitle = newTitle.substring(0, 15) + "...";
        }
        Component comp = tabbedPane.getTabComponentAt(index);
        if (comp instanceof JPanel) {
            JPanel p = (JPanel) comp;
            if (p.getComponentCount() > 0 && p.getComponent(0) instanceof JLabel) {
                ((JLabel) p.getComponent(0)).setText(newTitle);
            }
        }
    }

    public void closeTab(BrowserTab tab) {
        int idx = tabbedPane.indexOfComponent(tab);
        if (idx != -1) {
            tabbedPane.remove(idx);
            tabs.remove(tab);
        }
        if (tabbedPane.getTabCount() == 0) {
            addNewTab("http://localhost:8080/");
        }
    }

    public void closeCurrentTab() {
        BrowserTab t = getActiveTab();
        if (t != null) {
            closeTab(t);
        }
    }

    public BrowserTab getActiveTab() {
        int idx = tabbedPane.getSelectedIndex();
        if (idx != -1 && idx < tabs.size()) {
            return (BrowserTab) tabbedPane.getComponentAt(idx);
        }
        return null;
    }

    private void registerShortcuts() {
        // Ctrl+T: New tab
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_T, ActionEvent.CTRL_MASK), "newTab");
        getRootPane().getActionMap().put("newTab", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                addNewTab("http://localhost:8080/");
            }
        });

        // Ctrl+W: Close tab
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_W, ActionEvent.CTRL_MASK), "closeTab");
        getRootPane().getActionMap().put("closeTab", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                closeCurrentTab();
            }
        });

        // F5: Reload
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0), "reload");
        getRootPane().getActionMap().put("reload", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                BrowserTab t = getActiveTab();
                if (t != null) t.reload();
            }
        });

        // F12: DevTools
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_F12, 0), "devTools");
        getRootPane().getActionMap().put("devTools", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                BrowserTab t = getActiveTab();
                if (t != null) t.toggleDevTools();
            }
        });
    }

    /**
     * Khoi dong Web Server cong 8080 ngay trong trinh duyet
     */
    public synchronized void startLocalServer() {
        if (activeServerSocket != null && !activeServerSocket.isClosed()) {
            JOptionPane.showMessageDialog(this, "Web Server đang chạy trên cổng 8080!", "Thông báo", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        new Thread(() -> {
            try {
                activeServerSocket = new ServerSocket(8080);
                SwingUtilities.invokeLater(() -> {
                    lblServerStatus.setText(" |  Web Server: ĐANG CHẠY (Cổng 8080)");
                    lblServerStatus.setForeground(new Color(19, 115, 51));
                });
                System.out.println("Web Server da bat tren cong 8080.");

                while (activeServerSocket != null && !activeServerSocket.isClosed()) {
                    try {
                        java.net.Socket client = activeServerSocket.accept();
                        new Thread(new httpServer(client)).start();
                    } catch (IOException ex) {
                        break;
                    }
                }
            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> {
                    lblServerStatus.setText(" |  Web Server: Lỗi cổng 8080 (Có thể đã bị chiếm)");
                    lblServerStatus.setForeground(Color.RED);
                });
            }
        }).start();
    }

    public synchronized void stopLocalServer() {
        try {
            if (activeServerSocket != null && !activeServerSocket.isClosed()) {
                activeServerSocket.close();
                activeServerSocket = null;
            }
            lblServerStatus.setText(" |  Web Server: ĐÃ DỪNG");
            lblServerStatus.setForeground(new Color(120, 120, 120));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        // Set Look and Feel cho giong Chrome tren he dieu hanh
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        SwingUtilities.invokeLater(() -> {
            WebBrowser browser = new WebBrowser();
            browser.setVisible(true);
            // Tu dong khoi dong luon local server de nguoi dung trai nghiem ngay lap tuc
            browser.startLocalServer();
        });
    }
}
