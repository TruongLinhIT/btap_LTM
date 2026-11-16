package browser;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Image;
import java.io.ByteArrayInputStream;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.util.Map;
import java.util.Stack;
import java.util.function.Consumer;
import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;

/**
 * Một thẻ trình duyệt (Tab) mang phong cách Google Chrome / Cốc Cốc:
 * - Thanh điều hướng Back, Forward, Reload, Home (Google)
 * - Thanh Omnibox thông minh: chọn GET / HEAD / POST, nhập URL hoặc tìm kiếm từ khóa trên Google
 * - Hỗ trợ gửi Form GET & Form POST ngay trên trang HTML
 * - Tích hợp nút nhanh: HEAD, POST, Download (Ctrl+J), History (Ctrl+H), DevTools (F12)
 */
public class BrowserTab extends JPanel {

    private final JFrame parentFrame;
    private final Consumer<String> titleChangeCallback;

    private final Stack<String> backStack = new Stack<>();
    private final Stack<String> forwardStack = new Stack<>();
    private String currentUrl = "";

    private JButton btnBack;
    private JButton btnForward;
    private JButton btnReload;
    private JButton btnHome;
    private JComboBox<String> cbMethod;
    private JTextField txtAddress;
    private JButton btnGo;

    private JPanel contentContainer;
    private CardLayout cardLayout;
    private JEditorPane htmlPane;
    private JLabel lblImageDisplay;
    private JTextArea txtPlainText;

    private JSplitPane mainSplit;
    private JPanel devToolsPanel;
    private boolean devToolsVisible = false;
    private JLabel badgeStatus;
    private JLabel badgeMethod;
    private JLabel badgeIp;
    private JLabel badgeTime;
    private JLabel badgeSize;
    private JTextArea txtResHeaders;
    private JTextArea txtReqHeaders;
    private JTextArea txtPageSource;
    private JTextArea txtDnsInfo;

    private JLabel lblStatus;
    private JProgressBar progressBar;
    private JLabel lblProtocol;

    public BrowserTab(JFrame parentFrame, String initialUrl, Consumer<String> titleChangeCallback) {
        this.parentFrame = parentFrame;
        this.titleChangeCallback = titleChangeCallback;
        setLayout(new BorderLayout());

        initUI();

        String home = WebBrowser.getHomeUrl();
        if (initialUrl != null && !initialUrl.isEmpty()) {
            navigate(initialUrl, "GET", false);
        } else {
            navigate(home, "GET", false);
        }
    }

    private void initUI() {
        // 1. THANH ĐIỀU HƯỚNG & OMNIBOX KIỂU CHROME / CỐC CỐC
        JPanel toolbar = new JPanel(new BorderLayout(8, 4));
        toolbar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(218, 220, 224)),
                BorderFactory.createEmptyBorder(6, 10, 6, 10)
        ));
        toolbar.setBackground(new Color(248, 249, 250));

        JPanel navButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        navButtons.setOpaque(false);

        btnBack = createToolButton(" < ", "Quay lại trang trước");
        btnBack.setEnabled(false);
        btnBack.addActionListener(e -> goBack());

        btnForward = createToolButton(" > ", "Tiến tới trang sau");
        btnForward.setEnabled(false);
        btnForward.addActionListener(e -> goForward());

        btnReload = createToolButton(" Tải lại ", "Tải lại trang (F5 / Ctrl + R)");
        btnReload.addActionListener(e -> reload());

        btnHome = createToolButton(" Trang chủ Google ", "Về Trang chủ Google");
        btnHome.addActionListener(e -> navigate(WebBrowser.getHomeUrl(), "GET", false));

        navButtons.add(btnBack);
        navButtons.add(btnForward);
        navButtons.add(btnReload);
        navButtons.add(btnHome);

        // Thanh địa chỉ Omnibox ở giữa
        JPanel addressPanel = new JPanel(new BorderLayout(6, 0));
        addressPanel.setOpaque(false);

        String[] methods = {"GET", "HEAD", "POST"};
        cbMethod = new JComboBox<>(methods);
        cbMethod.setFont(new Font("Segoe UI", Font.BOLD, 12));
        cbMethod.setToolTipText("Chọn phương thức giao thức HTTP (GET, HEAD, POST)");

        txtAddress = new JTextField();
        txtAddress.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        txtAddress.setToolTipText("Nhập địa chỉ URL hoặc nhập từ khóa để tìm kiếm trên Google");
        txtAddress.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(195, 205, 220), 1, true),
                BorderFactory.createEmptyBorder(5, 10, 5, 10)
        ));
        txtAddress.addActionListener(e -> triggerNavigate());

        btnGo = UiUtil.createColoredButton(" Đi / Gửi ", new Color(26, 115, 232), Color.WHITE, 8);
        btnGo.setToolTipText("Thực thi yêu cầu HTTP hoặc Tìm kiếm Google (Enter)");
        btnGo.addActionListener(e -> triggerNavigate());

        addressPanel.add(cbMethod, BorderLayout.WEST);
        addressPanel.add(txtAddress, BorderLayout.CENTER);
        addressPanel.add(btnGo, BorderLayout.EAST);

        // Các nút tính năng trình duyệt bên phải thanh Omnibox
        JPanel actionButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        actionButtons.setOpaque(false);

        JButton btnQuickHead = UiUtil.createOutlineButton("HEAD", new Color(195, 205, 220), new Color(109, 40, 217));
        btnQuickHead.setToolTipText("Gửi yêu cầu HTTP HEAD chỉ lấy Response Headers của trang hiện tại");
        btnQuickHead.addActionListener(e -> {
            cbMethod.setSelectedItem("HEAD");
            String target = txtAddress.getText().trim();
            if (target.isEmpty()) target = WebBrowser.getHomeUrl();
            navigate(target, "HEAD", false);
        });

        JButton btnQuickPost = UiUtil.createOutlineButton("POST", new Color(195, 205, 220), new Color(19, 115, 51));
        btnQuickPost.setToolTipText("Mở công cụ gửi dữ liệu HTTP POST");
        btnQuickPost.addActionListener(e -> {
            PostDialog dlg = new PostDialog(parentFrame, currentUrl, this::displayResponse);
            dlg.setVisible(true);
        });

        JButton btnQuickDownload = UiUtil.createOutlineButton("Tải xuống (Ctrl+J)", new Color(195, 205, 220), new Color(26, 115, 232));
        btnQuickDownload.setToolTipText("Tải trang hiện tại về máy hoặc mở trình quản lý Download (Ctrl + J)");
        btnQuickDownload.addActionListener(e -> {
            String target = currentUrl != null && !currentUrl.isEmpty() ? currentUrl : txtAddress.getText().trim();
            if (!target.isEmpty()) {
                DownloadManager.quickDownload(parentFrame, target);
            } else {
                DownloadManager.showManagerDialog(parentFrame);
            }
        });

        JButton btnQuickHistory = createToolButton("Lịch sử (Ctrl+H)", "Mở Lịch sử duyệt web (Ctrl + H)");
        btnQuickHistory.addActionListener(e -> {
            HistoryDialog dlg = new HistoryDialog(parentFrame, url -> navigate(url, "GET", false));
            dlg.setVisible(true);
        });

        JButton btnDevTools = createToolButton("DevTools (F12)", "Bật/Tắt Chrome DevTools soi gói tin HTTP Socket");
        btnDevTools.addActionListener(e -> toggleDevTools());

        actionButtons.add(btnQuickHead);
        actionButtons.add(btnQuickPost);
        actionButtons.add(btnQuickDownload);
        actionButtons.add(btnQuickHistory);
        actionButtons.add(btnDevTools);

        toolbar.add(navButtons, BorderLayout.WEST);
        toolbar.add(addressPanel, BorderLayout.CENTER);
        toolbar.add(actionButtons, BorderLayout.EAST);

        add(toolbar, BorderLayout.NORTH);

        // 2. KHUNG HIỂN THỊ NỘI DUNG TRANG WEB
        cardLayout = new CardLayout();
        contentContainer = new JPanel(cardLayout);

        htmlPane = new JEditorPane();
        htmlPane.setEditable(false);
        htmlPane.setContentType("text/html");
        HTMLEditorKit kit = new HTMLEditorKit();
        kit.setAutoFormSubmission(false);
        htmlPane.setEditorKit(kit);

        htmlPane.addHyperlinkListener(e -> {
            if (e instanceof javax.swing.text.html.FormSubmitEvent) {
                javax.swing.text.html.FormSubmitEvent fse = (javax.swing.text.html.FormSubmitEvent) e;
                String method = fse.getMethod() != null ? fse.getMethod().toString().toUpperCase() : "GET";
                String data = fse.getData() != null ? fse.getData() : "";
                String actionUrl = fse.getURL() != null ? fse.getURL().toString() : (WebBrowser.getHomeUrl() + "api/submit");
                if ("POST".equalsIgnoreCase(method)) {
                    sendPostRequest(actionUrl, data);
                } else {
                    String sep = actionUrl.contains("?") ? "&" : "?";
                    navigate(data.isEmpty() ? actionUrl : (actionUrl + sep + data), "GET", false);
                }
                return;
            }
            if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                handleHyperlinkClick(e);
            } else if (e.getEventType() == HyperlinkEvent.EventType.ENTERED) {
                lblStatus.setText("Liên kết: " + (e.getURL() != null ? e.getURL().toString() : (e.getDescription() != null ? e.getDescription() : "")));
            } else if (e.getEventType() == HyperlinkEvent.EventType.EXITED) {
                lblStatus.setText("Sẵn sàng");
            }
        });
        JScrollPane scrollHtml = new JScrollPane(htmlPane);
        scrollHtml.getVerticalScrollBar().setUnitIncrement(16);
        contentContainer.add(scrollHtml, "HTML");

        // Image Viewer
        lblImageDisplay = new JLabel("", JLabel.CENTER);
        contentContainer.add(new JScrollPane(lblImageDisplay), "IMAGE");

        // Plain Text Viewer
        txtPlainText = new JTextArea();
        txtPlainText.setEditable(false);
        txtPlainText.setFont(new Font("Consolas", Font.PLAIN, 13));
        txtPlainText.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        contentContainer.add(new JScrollPane(txtPlainText), "TEXT");

        // 3. CHROME DEVTOOLS PANEL (F12)
        devToolsPanel = createDevToolsPanel();
        devToolsPanel.setPreferredSize(new Dimension(800, 230));

        mainSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, contentContainer, devToolsPanel);
        mainSplit.setResizeWeight(0.68);
        mainSplit.setDividerSize(0);
        devToolsPanel.setVisible(false);

        add(mainSplit, BorderLayout.CENTER);

        // 4. STATUS BAR
        JPanel statusBar = new JPanel(new BorderLayout(8, 0));
        statusBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(218, 220, 224)),
                BorderFactory.createEmptyBorder(4, 12, 4, 12)
        ));
        statusBar.setBackground(new Color(241, 243, 244));

        lblStatus = new JLabel("Sẵn sàng");
        lblStatus.setFont(new Font("Segoe UI", Font.PLAIN, 12));

        JPanel statusRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        statusRight.setOpaque(false);

        progressBar = new JProgressBar(0, 100);
        progressBar.setPreferredSize(new Dimension(130, 14));
        progressBar.setVisible(false);

        lblProtocol = new JLabel("Giao thức: HTTP/1.1 TCP Socket");
        lblProtocol.setFont(new Font("Segoe UI", Font.BOLD, 11));
        lblProtocol.setForeground(new Color(26, 115, 232));

        statusRight.add(progressBar);
        statusRight.add(lblProtocol);

        statusBar.add(lblStatus, BorderLayout.WEST);
        statusBar.add(statusRight, BorderLayout.EAST);

        add(statusBar, BorderLayout.SOUTH);
    }

    private JPanel createDevToolsPanel() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createMatteBorder(2, 0, 0, 0, new Color(26, 115, 232)));

        JPanel topInfo = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        topInfo.setBackground(new Color(241, 243, 244));

        JLabel lblDevTitle = new JLabel("Chrome DevTools (Network & HTTP Socket Inspector):");
        lblDevTitle.setFont(new Font("Segoe UI", Font.BOLD, 12));
        topInfo.add(lblDevTitle);

        badgeStatus = createBadge("200 OK", new Color(19, 115, 51));
        badgeMethod = createBadge("GET", new Color(26, 115, 232));
        badgeIp = createBadge("IP: 127.0.0.1", new Color(60, 64, 67));
        badgeTime = createBadge("0 ms", new Color(60, 64, 67));
        badgeSize = createBadge("0 B", new Color(60, 64, 67));

        topInfo.add(badgeStatus);
        topInfo.add(badgeMethod);
        topInfo.add(badgeIp);
        topInfo.add(badgeTime);
        topInfo.add(badgeSize);

        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(new Font("Segoe UI", Font.BOLD, 11));
        txtResHeaders = createCodeArea();
        txtReqHeaders = createCodeArea();
        txtPageSource = createCodeArea();
        txtDnsInfo = createCodeArea();

        tabs.addTab("HTTP Response Headers", new JScrollPane(txtResHeaders));
        tabs.addTab("HTTP Request Packet (Socket)", new JScrollPane(txtReqHeaders));
        tabs.addTab("Page Source (HTML / Body)", new JScrollPane(txtPageSource));
        tabs.addTab("URL & DNS IP", new JScrollPane(txtDnsInfo));

        p.add(topInfo, BorderLayout.NORTH);
        p.add(tabs, BorderLayout.CENTER);
        return p;
    }

    private JLabel createBadge(String text, Color bg) {
        JLabel l = new JLabel(" " + text + " ");
        l.setOpaque(true);
        l.setBackground(bg);
        l.setForeground(Color.WHITE);
        l.setFont(new Font("Segoe UI", Font.BOLD, 11));
        l.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
        return l;
    }

    private JTextArea createCodeArea() {
        JTextArea area = new JTextArea();
        area.setEditable(false);
        area.setFont(new Font("Consolas", Font.PLAIN, 12));
        area.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        return area;
    }

    private JButton createToolButton(String text, String tooltip) {
        JButton b = UiUtil.createOutlineButton(text, new Color(203, 213, 225), new Color(30, 41, 59));
        b.setToolTipText(tooltip);
        b.setFont(new Font("Segoe UI", Font.BOLD, 12));
        return b;
    }

    private void triggerNavigate() {
        String rawInput = txtAddress.getText().trim();
        String method = (String) cbMethod.getSelectedItem();

        if (rawInput.isEmpty()) return;

        if ("POST".equalsIgnoreCase(method)) {
            String target = resolveInputToUrl(rawInput);
            PostDialog dlg = new PostDialog(parentFrame, target, this::displayResponse);
            dlg.setVisible(true);
        } else {
            navigate(rawInput, method, false);
        }
    }

    private String resolveInputToUrl(String input) {
        if (input == null || input.trim().isEmpty()) {
            return WebBrowser.getHomeUrl();
        }
        String s = input.trim();
        String baseHome = WebBrowser.getHomeUrl();
        if (baseHome.endsWith("/")) {
            baseHome = baseHome.substring(0, baseHome.length() - 1);
        }

        if (s.startsWith("http://") || s.startsWith("https://")) {
            return s;
        }
        if (s.startsWith("/")) {
            return baseHome + s;
        }
        boolean looksLikeUrl = !s.contains(" ")
                && (s.startsWith("localhost") || s.startsWith("127.0.0.1") || s.contains(".") || s.contains(":"));
        if (looksLikeUrl) {
            return "http://" + s;
        }
        try {
            return baseHome + "/search?q=" + URLEncoder.encode(s, StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            return baseHome + "/search?q=" + s.replace(" ", "+");
        }
    }

    public void navigate(String targetInput, String method, boolean isBackOrForward) {
        if (targetInput == null || targetInput.trim().isEmpty()) return;
        String targetUrl = resolveInputToUrl(targetInput);
        String reqMethod = method != null ? method.toUpperCase() : "GET";

        if (!isBackOrForward && !currentUrl.isEmpty() && !currentUrl.equals(targetUrl)) {
            backStack.push(currentUrl);
            forwardStack.clear();
        }

        currentUrl = targetUrl;
        txtAddress.setText(currentUrl);
        cbMethod.setSelectedItem(reqMethod);
        updateNavButtons();

        lblStatus.setText("Đang gửi " + reqMethod + " " + currentUrl + " qua giao thức HTTP/1.1...");
        progressBar.setVisible(true);
        progressBar.setIndeterminate(true);

        final String finalUrl = targetUrl;
        final String finalMethod = reqMethod;

        new Thread(() -> {
            try {
                HttpRequest req = new HttpRequest(finalUrl, finalMethod);
                HttpResponse res = HttpEngine.execute(req);

                SwingUtilities.invokeLater(() -> {
                    progressBar.setVisible(false);
                    lblProtocol.setText("Giao thức: " + res.getProtocolEngine() + " (" + res.getResponseTimeMs() + " ms)");
                    lblStatus.setText("Hoàn tất: HTTP " + res.getStatusCode() + " " + res.getStatusMessage() + " - " + finalUrl);
                    displayResponse(res);
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    progressBar.setVisible(false);
                    lblStatus.setText("Lỗi kết nối HTTP: " + ex.getMessage());
                    showErrorPage(finalUrl, ex.getMessage());
                });
            }
        }).start();
    }

    public void displayResponse(HttpResponse res) {
        this.currentUrl = res.getRequestUrl();
        txtAddress.setText(currentUrl);

        String pageTitle = extractTitle(res);
        HistoryManager.add(currentUrl, pageTitle, res.getRequestMethod());
        if (titleChangeCallback != null) {
            titleChangeCallback.accept(pageTitle);
        }

        updateDevTools(res);

        // 1. Nếu là phương thức HTTP HEAD -> hiển thị bảng phân tích HTTP Headers trực quan
        if ("HEAD".equalsIgnoreCase(res.getRequestMethod())) {
            cardLayout.show(contentContainer, "HTML");
            StringBuilder rows = new StringBuilder();
            for (Map.Entry<String, String> entry : res.getHeaders().entrySet()) {
                rows.append("<tr>")
                        .append("<td bgcolor='#F8FAFC'><font face='Consolas, Courier New' size='2'><b>")
                        .append(escapeHtml(entry.getKey())).append("</b></font></td>")
                        .append("<td><font face='Consolas, Courier New' size='2' color='#0F766E'>")
                        .append(escapeHtml(entry.getValue())).append("</font></td>")
                        .append("</tr>");
            }
            String headHtml = "<html><head><title>HTTP HEAD: " + escapeHtml(currentUrl) + "</title></head>"
                    + "<body bgcolor='#F8F9FA' text='#202124' link='#1A73E8'>"
                    + "<table width='92%' cellpadding='16' cellspacing='0' border='1' bordercolor='#DADCE0' bgcolor='#FFFFFF' align='center'>"
                    + "<tr bgcolor='#EDE9FE'><td>"
                    + "<font face='Arial' size='4' color='#5B21B6'><b>KẾT QUẢ KIỂM TRA PHƯƠNG THỨC HTTP HEAD</b></font><br>"
                    + "<font face='Arial' size='2' color='#4C1D95'>Phương thức <b>HEAD</b> yêu cầu Server chỉ trả về <b>HTTP Status Line &amp; Response Headers</b> mà không truyền phần thân (Body = 0 bytes).</font>"
                    + "</td></tr>"
                    + "<tr><td>"
                    + "<font face='Arial' size='2'>"
                    + "<b>Địa chỉ URL mục tiêu:</b> <code>" + escapeHtml(res.getRequestUrl()) + "</code><br>"
                    + "<b>Mã trạng thái (Status):</b> <font color='#137333'><b>HTTP/1.1 " + res.getStatusCode() + " " + escapeHtml(res.getStatusMessage()) + "</b></font><br>"
                    + "<b>Địa chỉ IP phân giải (DNS):</b> <code>" + escapeHtml(res.getResolvedIp()) + "</code><br>"
                    + "<b>Thời gian phản hồi (Latency):</b> <code>" + res.getResponseTimeMs() + " ms</code><br>"
                    + "<b>Kích thước Body nhận về:</b> <code>" + res.getBodyBytes().length + " bytes (Đúng chuẩn HEAD)</code>"
                    + "</font><br><br>"
                    + "<font face='Arial' size='3'><b>Danh sách các trường HTTP Response Headers nhận được:</b></font><br><br>"
                    + "<table width='100%' cellpadding='7' cellspacing='0' border='1' bordercolor='#CBD5E1'>"
                    + "<tr bgcolor='#EFF6FF'>"
                    + "<th align='left' width='32%'><font face='Arial' size='2' color='#1E40AF'>Tên Header</font></th>"
                    + "<th align='left'><font face='Arial' size='2' color='#1E40AF'>Giá trị (Header Value)</font></th>"
                    + "</tr>"
                    + rows
                    + "</table><br>"
                    + "<a href='" + escapeHtml(res.getRequestUrl()) + "'><font face='Arial' size='2'><b>Mở trang này bằng phương thức HTTP GET</b></font></a>"
                    + " &nbsp;&nbsp;|&nbsp;&nbsp; "
                    + "<a href='" + WebBrowser.getHomeUrl() + "'><font face='Arial' size='2'><b>Về Trang chủ Google</b></font></a>"
                    + "</td></tr></table></body></html>";
            renderHtmlContent(headHtml, res.getRequestUrl());
            setDevToolsVisible(true);
            return;
        }

        // 2. Nếu là Hình ảnh
        if (res.isImage()) {
            try {
                ByteArrayInputStream bais = new ByteArrayInputStream(res.getBodyBytes());
                Image img = ImageIO.read(bais);
                if (img != null) {
                    lblImageDisplay.setIcon(new ImageIcon(img));
                    cardLayout.show(contentContainer, "IMAGE");
                    return;
                }
            } catch (Exception ignored) {}
        }

        // 3. Nếu là HTML
        if (res.isHtml()) {
            cardLayout.show(contentContainer, "HTML");
            renderHtmlContent(res.getStringBody(), res.getRequestUrl());
            return;
        }

        // 4. Văn bản thuần
        cardLayout.show(contentContainer, "TEXT");
        txtPlainText.setText(res.getStringBody());
        txtPlainText.setCaretPosition(0);
    }

    private void renderHtmlContent(String html, String baseUrlStr) {
        String cleanHtml = html
                .replaceAll("(?i)<meta\\s+[^>]*>", "")
                .replaceAll("(?is)<script\\b[^>]*>.*?</script>", "");
        try {
            if (htmlPane.getDocument() instanceof HTMLDocument && baseUrlStr != null && !baseUrlStr.isEmpty()) {
                ((HTMLDocument) htmlPane.getDocument()).setBase(new URL(baseUrlStr));
            }
        } catch (Exception ignored) {}
        try {
            htmlPane.setText(cleanHtml);
        } catch (Exception e) {
            htmlPane.setText(html);
        }
        htmlPane.setCaretPosition(0);
    }

    private void updateDevTools(HttpResponse res) {
        badgeStatus.setText(" " + res.getStatusCode() + " " + res.getStatusMessage() + " ");
        if (res.getStatusCode() >= 200 && res.getStatusCode() < 300) {
            badgeStatus.setBackground(new Color(19, 115, 51));
        } else if (res.getStatusCode() >= 400) {
            badgeStatus.setBackground(new Color(220, 38, 38));
        } else {
            badgeStatus.setBackground(new Color(217, 119, 6));
        }

        badgeMethod.setText(" " + res.getRequestMethod() + " ");
        badgeIp.setText(" IP: " + res.getResolvedIp() + " ");
        badgeTime.setText(" " + res.getResponseTimeMs() + " ms ");
        badgeSize.setText(" " + formatBytes(res.getBodyBytes().length) + " ");

        txtResHeaders.setText(res.getRawHeadersString());
        txtReqHeaders.setText(res.getRawRequestText() != null && !res.getRawRequestText().isEmpty()
                ? res.getRawRequestText()
                : (res.getRequestMethod() + " " + res.getRequestUrl() + " HTTP/1.1\nHost: " + res.getResolvedIp()));
        txtPageSource.setText(res.getStringBody());

        UrlDnsInfo info = UrlDnsInfo.analyze(res.getRequestUrl());
        txtDnsInfo.setText(info.getFormattedSummary());
    }

    private void handleHyperlinkClick(HyperlinkEvent e) {
        String target = null;
        if (e.getURL() != null) {
            target = e.getURL().toString();
        } else if (e.getDescription() != null) {
            String desc = e.getDescription();
            try {
                URL base = new URL(currentUrl.isEmpty() ? WebBrowser.getHomeUrl() : currentUrl);
                target = new URL(base, desc).toString();
            } catch (Exception ex) {
                target = resolveInputToUrl(desc);
            }
        }
        if (target != null) {
            if (target.toLowerCase().contains("download=true")) {
                DownloadManager.quickDownload(parentFrame, target);
            } else {
                cbMethod.setSelectedItem("GET");
                navigate(target, "GET", false);
            }
        }
    }

    private void showErrorPage(String url, String errorMessage) {
        cardLayout.show(contentContainer, "HTML");
        String homeUrl = WebBrowser.getHomeUrl();
        String errorHtml = "<html><body bgcolor='#F8F9FA' text='#202124' link='#1A73E8'>"
                + "<table width='82%' cellpadding='24' cellspacing='0' border='1' bordercolor='#DADCE0' bgcolor='#FFFFFF' align='center'>"
                + "<tr><td>"
                + "<font face='Arial' size='5' color='#D93025'><b>Không thể truy cập trang web này</b></font><br><br>"
                + "<font face='Arial' size='3'>Máy chủ tại <b>" + escapeHtml(url) + "</b> phản hồi lỗi hoặc không thể kết nối.</font><br><br>"
                + "<font face='Consolas, Courier New' size='2' color='#5F6368'>Chi tiết kỹ thuật: " + escapeHtml(errorMessage) + "</font><br><br>"
                + "<hr size='1' color='#E0E0E0'><br>"
                + "<a href='" + homeUrl + "'><font face='Arial' size='3'><b>Quay về Trang chủ Google</b></font></a>"
                + "</td></tr></table></body></html>";
        renderHtmlContent(errorHtml, homeUrl);
    }

    private String extractTitle(HttpResponse res) {
        if ("HEAD".equalsIgnoreCase(res.getRequestMethod())) {
            return "HEAD: " + res.getStatusCode() + " (" + res.getRequestUrl() + ")";
        }
        if (res.isHtml()) {
            String body = res.getStringBody();
            int start = body.toLowerCase().indexOf("<title>");
            int end = body.toLowerCase().indexOf("</title>");
            if (start != -1 && end != -1 && end > start + 7) {
                return body.substring(start + 7, end).trim();
            }
        }
        try {
            URL u = new URL(res.getRequestUrl());
            String p = u.getPath();
            if (p != null && p.length() > 1) {
                return p.substring(p.lastIndexOf('/') + 1);
            }
            return u.getHost();
        } catch (Exception e) {
            return "Thẻ Google";
        }
    }

    public void goBack() {
        if (!backStack.isEmpty()) {
            forwardStack.push(currentUrl);
            String prevUrl = backStack.pop();
            navigate(prevUrl, "GET", true);
        }
    }

    public void goForward() {
        if (!forwardStack.isEmpty()) {
            backStack.push(currentUrl);
            String nextUrl = forwardStack.pop();
            navigate(nextUrl, "GET", true);
        }
    }

    public void reload() {
        if (!currentUrl.isEmpty()) {
            navigate(currentUrl, (String) cbMethod.getSelectedItem(), true);
        }
    }

    private void updateNavButtons() {
        btnBack.setEnabled(!backStack.isEmpty());
        btnForward.setEnabled(!forwardStack.isEmpty());
    }

    public void toggleDevTools() {
        setDevToolsVisible(!devToolsVisible);
    }

    public void setDevToolsVisible(boolean visible) {
        this.devToolsVisible = visible;
        devToolsPanel.setVisible(visible);
        mainSplit.setDividerSize(visible ? 6 : 0);
        if (visible) {
            mainSplit.setDividerLocation(0.64);
        }
        revalidate();
        repaint();
    }

    public String getCurrentUrl() {
        return currentUrl;
    }

    public void sendPostRequest(String actionUrl, String formBody) {
        String resolvedUrl = resolveInputToUrl(actionUrl);
        if (!currentUrl.isEmpty() && !currentUrl.equals(resolvedUrl)) {
            backStack.push(currentUrl);
            forwardStack.clear();
        }
        currentUrl = resolvedUrl;
        txtAddress.setText(currentUrl);
        cbMethod.setSelectedItem("POST");
        updateNavButtons();

        lblStatus.setText("Đang gửi HTTP POST: " + currentUrl + " ...");
        progressBar.setVisible(true);
        progressBar.setIndeterminate(true);

        final String finalUrl = resolvedUrl;
        new Thread(() -> {
            try {
                HttpRequest req = new HttpRequest(finalUrl, "POST");
                req.setContentType("application/x-www-form-urlencoded");
                if (formBody != null && !formBody.isEmpty()) {
                    req.setBody(formBody.getBytes(StandardCharsets.UTF_8));
                }
                HttpResponse res = HttpEngine.execute(req);
                SwingUtilities.invokeLater(() -> {
                    progressBar.setVisible(false);
                    lblProtocol.setText("Giao thức: " + res.getProtocolEngine() + " (" + res.getResponseTimeMs() + " ms)");
                    lblStatus.setText("Đã nhận phản hồi HTTP POST (" + res.getStatusCode() + " " + res.getStatusMessage() + ")");
                    displayResponse(res);
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    progressBar.setVisible(false);
                    lblStatus.setText("Lỗi POST: " + ex.getMessage());
                    showErrorPage(finalUrl, ex.getMessage());
                });
            }
        }).start();
    }

    private String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private String formatBytes(long bytes) {
        if (bytes <= 0) return "0 B";
        final String[] units = new String[]{"B", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(bytes) / Math.log10(1024));
        return new DecimalFormat("#,##0.#").format(bytes / Math.pow(1024, digitGroups)) + " " + units[digitGroups];
    }
}
