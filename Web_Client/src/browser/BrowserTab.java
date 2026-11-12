package browser;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Image;
import java.io.ByteArrayInputStream;
import java.net.URL;
import java.text.DecimalFormat;
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
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.html.HTMLEditorKit;

public class BrowserTab extends JPanel {

    private JFrame parentFrame;
    private Consumer<String> titleChangeCallback;

    // Navigation stacks
    private Stack<String> backStack = new Stack<>();
    private Stack<String> forwardStack = new Stack<>();
    private String currentUrl = "";

    // Toolbar components
    private JButton btnBack;
    private JButton btnForward;
    private JButton btnReload;
    private JButton btnHome;
    private JTextField txtAddress;
    private JComboBox<String> cbMethod;
    private JButton btnGo;
    private JButton btnDevTools;
    private JButton btnDns;

    // Content Display
    private JPanel contentContainer;
    private CardLayout cardLayout;
    private JEditorPane htmlPane;
    private JLabel lblImageDisplay;
    private JTextArea txtPlainText;
    private JPanel mediaPanel;
    private JLabel lblMediaInfo;
    private JButton btnDownloadMedia;

    // DevTools & Inspector
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

    // Status bar
    private JLabel lblStatus;
    private JProgressBar progressBar;
    private JLabel lblProtocol;

    // Cache last response
    private HttpResponse lastResponse;

    public BrowserTab(JFrame parentFrame, String initialUrl, Consumer<String> titleChangeCallback) {
        this.parentFrame = parentFrame;
        this.titleChangeCallback = titleChangeCallback;
        setLayout(new BorderLayout());

        initUI();

        if (initialUrl != null && !initialUrl.isEmpty()) {
            navigate(initialUrl, "GET", false);
        } else {
            navigate("http://localhost:8080/", "GET", false);
        }
    }

    private void initUI() {
        // 1. TOOLBAR
        JPanel toolbar = new JPanel(new BorderLayout(6, 4));
        toolbar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(218, 220, 224)),
                BorderFactory.createEmptyBorder(6, 8, 6, 8)
        ));
        toolbar.setBackground(new Color(248, 249, 250));

        // Nav buttons (Left)
        JPanel navButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        navButtons.setOpaque(false);

        btnBack = createToolButton(" < ", "Quay lại trang trước");
        btnBack.setEnabled(false);
        btnBack.addActionListener(e -> goBack());

        btnForward = createToolButton(" > ", "Trang kế tiếp");
        btnForward.setEnabled(false);
        btnForward.addActionListener(e -> goForward());

        btnReload = createToolButton(" Tải lại ", "Tải lại trang (Reload)");
        btnReload.addActionListener(e -> reload());

        btnHome = createToolButton(" Trang chủ ", "Về trang chủ (Localhost Server)");
        btnHome.addActionListener(e -> navigate("http://localhost:8080/", "GET", false));

        navButtons.add(btnBack);
        navButtons.add(btnForward);
        navButtons.add(btnReload);
        navButtons.add(btnHome);

        // Address bar & Method (Center)
        JPanel addressPanel = new JPanel(new BorderLayout(6, 0));
        addressPanel.setOpaque(false);

        String[] methods = {"GET", "HEAD", "POST"};
        cbMethod = new JComboBox<>(methods);
        cbMethod.setFont(new Font("SansSerif", Font.BOLD, 12));
        cbMethod.setToolTipText("Chọn phương thức HTTP");

        txtAddress = new JTextField();
        txtAddress.setFont(new Font("SansSerif", Font.PLAIN, 13));
        txtAddress.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(180, 180, 180), 1, true),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)
        ));
        txtAddress.addActionListener(e -> triggerNavigate());

        btnGo = UiUtil.createColoredButton("  Đi  ", new Color(11, 87, 208), Color.WHITE, 4);
        btnGo.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnGo.addActionListener(e -> triggerNavigate());

        addressPanel.add(cbMethod, BorderLayout.WEST);
        addressPanel.add(txtAddress, BorderLayout.CENTER);
        addressPanel.add(btnGo, BorderLayout.EAST);

        // Action tools (Right)
        JPanel actionButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        actionButtons.setOpaque(false);

        btnDns = createToolButton(" DNS / URL ", "Xem phân tích URL & DNS IP (diachi.java & inet.java)");
        btnDns.addActionListener(e -> {
            DnsInspectorDialog dlg = new DnsInspectorDialog(parentFrame, txtAddress.getText());
            dlg.setVisible(true);
        });

        btnDevTools = createToolButton(" DevTools (F12) ", "Bật/Tắt công cụ kiểm tra HTTP Inspector");
        btnDevTools.addActionListener(e -> toggleDevTools());

        actionButtons.add(btnDns);
        actionButtons.add(btnDevTools);

        toolbar.add(navButtons, BorderLayout.WEST);
        toolbar.add(addressPanel, BorderLayout.CENTER);
        toolbar.add(actionButtons, BorderLayout.EAST);

        add(toolbar, BorderLayout.NORTH);

        // 2. CONTENT VIEWER (CardLayout)
        cardLayout = new CardLayout();
        contentContainer = new JPanel(cardLayout);

        // 2.1 HTML Pane
        htmlPane = new JEditorPane();
        htmlPane.setEditable(false);
        htmlPane.setContentType("text/html");
        HTMLEditorKit kit = new HTMLEditorKit();
        htmlPane.setEditorKit(kit);
        htmlPane.addHyperlinkListener(e -> {
            if (e instanceof javax.swing.text.html.FormSubmitEvent) {
                javax.swing.text.html.FormSubmitEvent fse = (javax.swing.text.html.FormSubmitEvent) e;
                String method = fse.getMethod().toString();
                String data = fse.getData();
                String actionUrl = fse.getURL() != null ? fse.getURL().toString() : "http://localhost:8080/api/submit";
                if ("POST".equalsIgnoreCase(method)) {
                    sendPostRequest(actionUrl, data);
                    return;
                } else {
                    navigate(actionUrl + (data != null && !data.isEmpty() ? "?" + data : ""), "GET", false);
                    return;
                }
            }
            if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                handleHyperlinkClick(e);
            } else if (e.getEventType() == HyperlinkEvent.EventType.ENTERED) {
                lblStatus.setText(e.getURL() != null ? e.getURL().toString() : (e.getDescription() != null ? e.getDescription() : ""));
            } else if (e.getEventType() == HyperlinkEvent.EventType.EXITED) {
                lblStatus.setText("Sẵn sàng");
            }
        });
        JScrollPane scrollHtml = new JScrollPane(htmlPane);
        scrollHtml.getVerticalScrollBar().setUnitIncrement(14);
        contentContainer.add(scrollHtml, "HTML");

        // 2.2 Image Pane
        lblImageDisplay = new JLabel("", JLabel.CENTER);
        JScrollPane scrollImage = new JScrollPane(lblImageDisplay);
        contentContainer.add(scrollImage, "IMAGE");

        // 2.3 Plain Text Pane
        txtPlainText = new JTextArea();
        txtPlainText.setEditable(false);
        txtPlainText.setFont(new Font("Monospaced", Font.PLAIN, 13));
        JScrollPane scrollText = new JScrollPane(txtPlainText);
        contentContainer.add(scrollText, "TEXT");

        // 2.4 Multimedia / Binary File Pane (.pdf, .mp3, .mp4, .wav...)
        mediaPanel = new JPanel(new BorderLayout(10, 10));
        mediaPanel.setBorder(BorderFactory.createEmptyBorder(40, 40, 40, 40));
        mediaPanel.setBackground(Color.WHITE);

        JPanel mediaCard = new JPanel(new BorderLayout(15, 15));
        mediaCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(218, 220, 224), 1),
                BorderFactory.createEmptyBorder(25, 25, 25, 25)
        ));
        mediaCard.setBackground(new Color(248, 249, 250));

        lblMediaInfo = new JLabel("Tệp đa phương tiện / Dịch vụ Server", JLabel.CENTER);
        lblMediaInfo.setFont(new Font("SansSerif", Font.PLAIN, 14));

        JPanel mediaActions = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 10));
        mediaActions.setOpaque(false);
        btnDownloadMedia = UiUtil.createColoredButton(" ⬇ Tải tệp này về máy (Download) ", new Color(11, 87, 208), Color.WHITE, 6);
        btnDownloadMedia.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnDownloadMedia.addActionListener(e -> {
            if (currentUrl != null && !currentUrl.isEmpty()) {
                DownloadManager.startDownload(parentFrame, currentUrl);
            }
        });
        mediaActions.add(btnDownloadMedia);

        JButton btnOpenDirectly = UiUtil.createColoredButton(" ▶ Mở tệp ngay bằng máy tính ", new Color(19, 115, 51), Color.WHITE, 6);
        btnOpenDirectly.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnOpenDirectly.addActionListener(e -> openLastMediaDirectly());
        mediaActions.add(btnOpenDirectly);

        mediaCard.add(lblMediaInfo, BorderLayout.CENTER);
        mediaCard.add(mediaActions, BorderLayout.SOUTH);
        mediaPanel.add(mediaCard, BorderLayout.NORTH);
        contentContainer.add(mediaPanel, "MEDIA");

        // 3. DEVTOOLS PANEL (Bottom)
        devToolsPanel = createDevToolsPanel();
        devToolsPanel.setPreferredSize(new Dimension(800, 240));

        mainSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, contentContainer, devToolsPanel);
        mainSplit.setResizeWeight(0.7);
        mainSplit.setDividerSize(0);
        devToolsPanel.setVisible(false);

        add(mainSplit, BorderLayout.CENTER);

        // 4. STATUS BAR (Bottom)
        JPanel statusBar = new JPanel(new BorderLayout(8, 0));
        statusBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(218, 220, 224)),
                BorderFactory.createEmptyBorder(4, 10, 4, 10)
        ));
        statusBar.setBackground(new Color(241, 243, 244));

        lblStatus = new JLabel("Sẵn sàng");
        lblStatus.setFont(new Font("SansSerif", Font.PLAIN, 12));

        JPanel statusRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        statusRight.setOpaque(false);

        progressBar = new JProgressBar(0, 100);
        progressBar.setPreferredSize(new Dimension(120, 14));
        progressBar.setVisible(false);

        lblProtocol = new JLabel("HTTP / Socket TCP");
        lblProtocol.setFont(new Font("SansSerif", Font.PLAIN, 11));
        lblProtocol.setForeground(new Color(95, 99, 104));

        statusRight.add(progressBar);
        statusRight.add(lblProtocol);

        statusBar.add(lblStatus, BorderLayout.WEST);
        statusBar.add(statusRight, BorderLayout.EAST);

        add(statusBar, BorderLayout.SOUTH);
    }

    private JPanel createDevToolsPanel() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createMatteBorder(2, 0, 0, 0, new Color(26, 115, 232)));

        // Top info bar
        JPanel topInfo = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        topInfo.setBackground(new Color(241, 243, 244));

        badgeStatus = createBadge("STATUS", new Color(19, 115, 51));
        badgeMethod = createBadge("GET", new Color(26, 115, 232));
        badgeIp = createBadge("IP: 127.0.0.1", new Color(60, 64, 67));
        badgeTime = createBadge("0 ms", new Color(60, 64, 67));
        badgeSize = createBadge("0 B", new Color(60, 64, 67));

        topInfo.add(badgeStatus);
        topInfo.add(badgeMethod);
        topInfo.add(badgeIp);
        topInfo.add(badgeTime);
        topInfo.add(badgeSize);

        // Tabs
        JTabbedPane tabs = new JTabbedPane();
        txtResHeaders = createCodeArea();
        txtReqHeaders = createCodeArea();
        txtPageSource = createCodeArea();
        txtDnsInfo = createCodeArea();

        tabs.addTab("Response Headers", new JScrollPane(txtResHeaders));
        tabs.addTab("Request Headers", new JScrollPane(txtReqHeaders));
        tabs.addTab("Page Source (HTML/Body)", new JScrollPane(txtPageSource));
        tabs.addTab("URL & DNS Details", new JScrollPane(txtDnsInfo));

        p.add(topInfo, BorderLayout.NORTH);
        p.add(tabs, BorderLayout.CENTER);
        return p;
    }

    private JLabel createBadge(String text, Color bg) {
        JLabel l = new JLabel(" " + text + " ");
        l.setOpaque(true);
        l.setBackground(bg);
        l.setForeground(Color.WHITE);
        l.setFont(new Font("SansSerif", Font.BOLD, 11));
        l.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
        return l;
    }

    private JTextArea createCodeArea() {
        JTextArea area = new JTextArea();
        area.setEditable(false);
        area.setFont(new Font("Monospaced", Font.PLAIN, 12));
        area.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        return area;
    }

    private JButton createToolButton(String text, String tooltip) {
        JButton b = UiUtil.createOutlineButton(text, new Color(195, 205, 220), new Color(30, 41, 59));
        b.setToolTipText(tooltip);
        b.setFont(new Font("Segoe UI", Font.BOLD, 12));
        return b;
    }

    private void triggerNavigate() {
        String url = txtAddress.getText().trim();
        String method = (String) cbMethod.getSelectedItem();

        if (url.isEmpty()) return;

        if ("POST".equalsIgnoreCase(method)) {
            // Mo hop thoai gui POST chuyen nghiep
            PostDialog dlg = new PostDialog(parentFrame, url, res -> {
                displayResponse(res);
            });
            dlg.setVisible(true);
        } else {
            navigate(url, method, false);
        }
    }

    public void navigate(String targetUrl, String method, boolean isBackOrForward) {
        if (targetUrl == null || targetUrl.trim().isEmpty()) return;
        targetUrl = targetUrl.trim();

        if (!targetUrl.startsWith("http://") && !targetUrl.startsWith("https://")) {
            targetUrl = "http://" + targetUrl;
        }

        if (!isBackOrForward && !currentUrl.isEmpty()) {
            backStack.push(currentUrl);
            forwardStack.clear();
        }

        currentUrl = targetUrl;
        txtAddress.setText(currentUrl);
        updateNavButtons();

        lblStatus.setText("Đang kết nối: " + currentUrl + " ...");
        progressBar.setVisible(true);
        progressBar.setIndeterminate(true);

        final String finalUrl = targetUrl;
        final String finalMethod = method;

        new Thread(() -> {
            try {
                HttpRequest req = new HttpRequest(finalUrl, finalMethod);
                HttpResponse res = HttpEngine.execute(req);

                SwingUtilities.invokeLater(() -> {
                    progressBar.setVisible(false);
                    lblStatus.setText("Đã tải xong (" + res.getStatusCode() + " " + res.getStatusMessage() + ")");
                    displayResponse(res);
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    progressBar.setVisible(false);
                    lblStatus.setText("Lỗi: " + ex.getMessage());
                    showErrorPage(finalUrl, ex.getMessage());
                });
            }
        }).start();
    }

    private boolean isDownloadableExtension(String url) {
        return url.endsWith(".pdf") || url.endsWith(".mp3") || url.endsWith(".mp4")
                || url.endsWith(".wav") || url.endsWith(".zip") || url.endsWith(".rar")
                || url.endsWith(".tar.gz");
    }

    public void displayResponse(HttpResponse res) {
        this.lastResponse = res;
        this.currentUrl = res.getRequestUrl();
        txtAddress.setText(currentUrl);

        // Cap nhat History
        String pageTitle = extractTitle(res);
        HistoryManager.add(currentUrl, pageTitle, res.getRequestMethod());
        if (titleChangeCallback != null) {
            titleChangeCallback.accept(pageTitle);
        }

        // Cap nhat DevTools Inspector
        updateDevTools(res);

        // Neu la phuong thuc HEAD thi chi can thong bao va hien thi DevTools headers
        if ("HEAD".equalsIgnoreCase(res.getRequestMethod())) {
            cardLayout.show(contentContainer, "TEXT");
            txtPlainText.setText("=== KET QUA HTTP HEAD REQUEST ===\n\n"
                    + "URL: " + res.getRequestUrl() + "\n"
                    + "Status: " + res.getStatusCode() + " " + res.getStatusMessage() + "\n"
                    + "Response Time: " + res.getResponseTimeMs() + " ms\n"
                    + "Resolved IP: " + res.getResolvedIp() + "\n\n"
                    + "--- HTTP HEADERS ---\n"
                    + res.getRawHeadersString() + "\n\n"
                    + "(Phuong thuc HEAD chi tra ve header ma khong tai phan than Body)");
            setDevToolsVisible(true);
            return;
        }

        // Neu la Image
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

        // Neu la Audio / Video / PDF
        if (res.isAudioOrVideoOrPdf()) {
            cardLayout.show(contentContainer, "MEDIA");
            String fileTypeTitle = "[ TỆP ĐA PHƯƠNG TIỆN ]";
            String lower = currentUrl.toLowerCase();
            if (lower.endsWith(".pdf")) fileTypeTitle = "[ TÀI LIỆU PDF ]";
            else if (lower.endsWith(".mp3") || lower.endsWith(".wav")) fileTypeTitle = "[ ÂM THANH MP3 / AUDIO ]";
            else if (lower.endsWith(".mp4")) fileTypeTitle = "[ VIDEO MP4 ]";

            lblMediaInfo.setText("<html><center>"
                    + "<div style='font-size:22px;color:#0B57D0;font-weight:bold;margin-bottom:8px;'>" + fileTypeTitle + "</div>"
                    + "<h2 style='color:#1E293B;margin:5px;'>Dịch vụ Đa phương tiện Web Server</h2>"
                    + "<p style='font-size:13px;'><b>Địa chỉ tệp:</b> " + res.getRequestUrl() + "</p>"
                    + "<p style='font-size:13px;'><b>Loại nội dung (MIME Type):</b> " + res.getContentType() + "</p>"
                    + "<p style='font-size:13px;'><b>Dung lượng:</b> " + formatBytes(res.getBodyBytes().length) + "</p>"
                    + "<p style='color:#137333;font-size:13px;margin-top:10px;'><b>[✓] Đã tải thành công qua giao thức HTTP 200 OK.</b></p>"
                    + "<p style='color:#5f6368;font-size:12px;'>Bạn có thể bấm <b>Tải về máy</b> hoặc bấm <b>Mở tệp ngay</b> để thưởng thức.</p>"
                    + "</center></html>");
            return;
        }

        // Neu la HTML
        if (res.isHtml()) {
            cardLayout.show(contentContainer, "HTML");
            String html = res.getStringBody();
            // Tránh lỗi ChangedCharSetException nổi tiếng của Swing JEditorPane khi gặp thẻ meta charset
            String cleanHtml = html.replaceAll("(?i)<meta\\s+[^>]*>", "");
            try {
                htmlPane.setText(cleanHtml);
            } catch (Exception e) {
                htmlPane.setText(html);
            }
            htmlPane.setCaretPosition(0);
            return;
        }

        // Mac dinh la Text/Plain hoac dinh dang khac
        cardLayout.show(contentContainer, "TEXT");
        txtPlainText.setText(res.getStringBody());
        txtPlainText.setCaretPosition(0);
    }

    private void updateDevTools(HttpResponse res) {
        badgeStatus.setText(" " + res.getStatusCode() + " " + res.getStatusMessage() + " ");
        if (res.getStatusCode() >= 200 && res.getStatusCode() < 300) {
            badgeStatus.setBackground(new Color(19, 115, 51));
        } else if (res.getStatusCode() >= 400) {
            badgeStatus.setBackground(new Color(234, 67, 53));
        } else {
            badgeStatus.setBackground(new Color(242, 153, 0));
        }

        badgeMethod.setText(" " + res.getRequestMethod() + " ");
        badgeIp.setText(" IP: " + res.getResolvedIp() + " ");
        badgeTime.setText(" " + res.getResponseTimeMs() + " ms ");
        badgeSize.setText(" " + formatBytes(res.getBodyBytes().length) + " ");

        txtResHeaders.setText(res.getRawHeadersString());

        StringBuilder reqH = new StringBuilder();
        reqH.append(res.getRequestMethod()).append(" ").append(res.getRequestUrl()).append(" HTTP/1.1\n");
        reqH.append("Host: ").append(res.getResolvedIp()).append("\n");
        reqH.append("User-Agent: LTM-WebBrowser/1.0\n");
        reqH.append("Accept: */*\n");
        txtReqHeaders.setText(reqH.toString());

        txtPageSource.setText(res.getStringBody());

        UrlDnsInfo info = UrlDnsInfo.analyze(res.getRequestUrl());
        txtDnsInfo.setText(info.getFormattedSummary());
    }

    private void handleHyperlinkClick(HyperlinkEvent e) {
        if (e.getURL() != null) {
            String href = e.getURL().toString();
            navigate(href, "GET", false);
        } else if (e.getDescription() != null) {
            String desc = e.getDescription();
            try {
                URL base = new URL(currentUrl);
                URL resolved = new URL(base, desc);
                navigate(resolved.toString(), "GET", false);
            } catch (Exception ex) {
                navigate(desc, "GET", false);
            }
        }
    }

    private void showErrorPage(String url, String errorMessage) {
        cardLayout.show(contentContainer, "HTML");
        String errorHtml = "<!DOCTYPE html><html><body style='font-family:Arial,sans-serif;padding:40px;background:#f8f9fa;text-align:center;'>"
                + "<h1 style='color:#ea4335;font-size:36px;'>[!] Lỗi Tải Trang</h1>"
                + "<p style='font-size:16px;'>Không thể kết nối đến máy chủ: <b>" + url + "</b></p>"
                + "<div style='background:#ffffff;border:1px solid #dadce0;border-radius:6px;padding:15px;max-width:550px;margin:20px auto;text-align:left;'>"
                + "<b>Chi tiết lỗi:</b> <code style='color:#d93025;'>" + errorMessage + "</code><br><br>"
                + "<b>Gợi ý khắc phục:</b>"
                + "<ul>"
                + "<li>Kiểm tra xem Web Server nội bộ đã khởi động chưa (cổng 8080).</li>"
                + "<li>Kiểm tra đường truyền mạng hoặc địa chỉ URL đã nhập.</li>"
                + "<li>Vào menu 'Máy chủ' để khởi động HttpServer có sẵn trong dự án.</li>"
                + "</ul></div>"
                + "<a href='http://localhost:8080/' style='display:inline-block;padding:8px 16px;background:#1a73e8;color:white;text-decoration:none;border-radius:4px;'>Về Localhost:8080</a>"
                + "</body></html>";
        htmlPane.setText(errorHtml);
        htmlPane.setCaretPosition(0);
    }

    private String extractTitle(HttpResponse res) {
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
            return "Trang web";
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
        mainSplit.setDividerSize(visible ? 5 : 0);
        if (visible) {
            mainSplit.setDividerLocation(0.65);
        }
        revalidate();
        repaint();
    }

    public String getCurrentUrl() {
        return currentUrl;
    }

    private void openLastMediaDirectly() {
        if (lastResponse == null || lastResponse.getBodyBytes() == null || lastResponse.getBodyBytes().length == 0) {
            JOptionPane.showMessageDialog(parentFrame, "Chưa có dữ liệu tệp để mở.");
            return;
        }
        try {
            String ext = ".bin";
            String lower = currentUrl.toLowerCase();
            if (lower.endsWith(".pdf")) ext = ".pdf";
            else if (lower.endsWith(".mp3")) ext = ".mp3";
            else if (lower.endsWith(".mp4")) ext = ".mp4";
            else if (lower.endsWith(".wav")) ext = ".wav";
            else if (lower.endsWith(".png")) ext = ".png";
            else if (lower.endsWith(".txt")) ext = ".txt";

            java.io.File temp = java.io.File.createTempFile("ltm_preview_", ext);
            temp.deleteOnExit();
            java.nio.file.Files.write(temp.toPath(), lastResponse.getBodyBytes());
            if (java.awt.Desktop.isDesktopSupported()) {
                java.awt.Desktop.getDesktop().open(temp);
            } else {
                JOptionPane.showMessageDialog(parentFrame, "Đã lưu tạm tệp tại: " + temp.getAbsolutePath());
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(parentFrame, "Không thể mở tệp tự động: " + ex.getMessage() + "\nHãy dùng nút Tải về máy.");
        }
    }

    public void sendPostRequest(String actionUrl, String formBody) {
        if (!actionUrl.startsWith("http://") && !actionUrl.startsWith("https://")) {
            actionUrl = "http://" + actionUrl;
        }
        backStack.push(currentUrl);
        forwardStack.clear();
        currentUrl = actionUrl;
        txtAddress.setText(currentUrl);
        updateNavButtons();

        lblStatus.setText("Đang gửi HTTP POST: " + actionUrl + " ...");
        progressBar.setVisible(true);
        progressBar.setIndeterminate(true);

        final String finalUrl = actionUrl;
        new Thread(() -> {
            try {
                HttpRequest req = new HttpRequest(finalUrl, "POST");
                req.setContentType("application/x-www-form-urlencoded");
                if (formBody != null && !formBody.isEmpty()) {
                    req.setBody(formBody.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
                HttpResponse res = HttpEngine.execute(req);
                SwingUtilities.invokeLater(() -> {
                    progressBar.setVisible(false);
                    lblStatus.setText("Đã nhận phản hồi POST (" + res.getStatusCode() + " " + res.getStatusMessage() + ")");
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

    private String formatBytes(long bytes) {
        if (bytes <= 0) return "0 B";
        final String[] units = new String[]{"B", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(bytes) / Math.log10(1024));
        return new DecimalFormat("#,##0.#").format(bytes / Math.pow(1024, digitGroups)) + " " + units[digitGroups];
    }
}
