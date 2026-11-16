package browser;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;

/**
 * Công cụ tạo và gửi gói tin HTTP POST (PostDialog) trên Web Client.
 */
public class PostDialog extends JDialog {

    private JTextField txtUrl;
    private JComboBox<String> cbContentType;
    private DefaultTableModel paramsModel;
    private JTable paramsTable;
    private JTextArea txtRawBody;
    private JTextArea txtResponseHeaders;
    private JTextArea txtResponseBody;
    private JLabel lblStatus;
    private HttpResponse lastPostResponse;
    private final Consumer<HttpResponse> onResponseReceived;

    public PostDialog(JFrame parent, String defaultUrl, Consumer<HttpResponse> onResponseReceived) {
        super(parent, "Công cụ gửi HTTP POST Request - Trình duyệt Web Client", false);
        this.onResponseReceived = onResponseReceived;
        setSize(760, 580);
        setLocationRelativeTo(parent);
        setLayout(new BorderLayout(8, 8));

        String target = (defaultUrl != null && !defaultUrl.isEmpty() && defaultUrl.contains("/api/"))
                ? defaultUrl
                : (WebBrowser.getHomeUrl() + "api/submit");
        initUI(target);
    }

    private void initUI(String defaultUrl) {
        // 1. TOP BAR: URL + SEND BUTTON
        JPanel topPanel = new JPanel(new BorderLayout(8, 8));
        topPanel.setBackground(new Color(248, 249, 250));
        topPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(218, 220, 224)),
                BorderFactory.createEmptyBorder(12, 14, 12, 14)
        ));

        JPanel urlRow = new JPanel(new BorderLayout(8, 0));
        urlRow.setOpaque(false);
        JLabel lblMethod = new JLabel(" [HTTP POST] URL: ");
        lblMethod.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lblMethod.setForeground(new Color(19, 115, 51));

        txtUrl = new JTextField(defaultUrl);
        txtUrl.setFont(new Font("Consolas", Font.PLAIN, 13));

        JButton btnSend = UiUtil.createColoredButton(" Gửi HTTP POST (Socket TCP) ", new Color(19, 115, 51), Color.WHITE, 6);
        btnSend.addActionListener(e -> executePost());

        urlRow.add(lblMethod, BorderLayout.WEST);
        urlRow.add(txtUrl, BorderLayout.CENTER);
        urlRow.add(btnSend, BorderLayout.EAST);

        JPanel typeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        typeRow.setOpaque(false);
        typeRow.add(new JLabel("Định dạng dữ liệu (Content-Type):"));

        String[] types = {
                "application/x-www-form-urlencoded",
                "application/json; charset=UTF-8",
                "text/plain; charset=UTF-8"
        };
        cbContentType = new JComboBox<>(types);
        cbContentType.setFont(new Font("Segoe UI", Font.BOLD, 12));
        cbContentType.addActionListener(e -> updateBodyPlaceholder());
        typeRow.add(cbContentType);

        topPanel.add(urlRow, BorderLayout.NORTH);
        topPanel.add(typeRow, BorderLayout.SOUTH);
        add(topPanel, BorderLayout.NORTH);

        // 2. CENTER SPLIT: REQUEST BODY (TOP) & SERVER RESPONSE (BOTTOM)
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        splitPane.setResizeWeight(0.48);

        JTabbedPane reqTabs = new JTabbedPane();
        reqTabs.setFont(new Font("Segoe UI", Font.BOLD, 12));

        // Tab 1: Form Key-Value
        JPanel formPanel = new JPanel(new BorderLayout(6, 6));
        formPanel.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        paramsModel = new DefaultTableModel(new Object[]{"Tên tham số (Key)", "Giá trị (Value - UTF-8)"}, 0);
        paramsModel.addRow(new Object[]{"hoten", "Nguyễn Văn A"});
        paramsModel.addRow(new Object[]{"masv", "21IT001"});
        paramsModel.addRow(new Object[]{"lop", "Lập Trình Mạng"});
        paramsModel.addRow(new Object[]{"noidung", "Gửi dữ liệu qua giao thức HTTP POST"});

        paramsTable = new JTable(paramsModel);
        paramsTable.setRowHeight(25);
        paramsTable.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        formPanel.add(new JScrollPane(paramsTable), BorderLayout.CENTER);

        JPanel tableBtnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        JButton btnAddRow = UiUtil.createOutlineButton("+ Thêm trường", new Color(203, 213, 225), new Color(26, 115, 232));
        btnAddRow.addActionListener(e -> paramsModel.addRow(new Object[]{"tham_so_moi", "gia_tri"}));

        JButton btnRemoveRow = UiUtil.createOutlineButton("- Xóa dòng chọn", new Color(203, 213, 225), new Color(185, 28, 28));
        btnRemoveRow.addActionListener(e -> {
            int row = paramsTable.getSelectedRow();
            if (row != -1) paramsModel.removeRow(row);
        });
        tableBtnPanel.add(btnAddRow);
        tableBtnPanel.add(btnRemoveRow);
        formPanel.add(tableBtnPanel, BorderLayout.SOUTH);

        reqTabs.addTab("1. Tham số Biểu mẫu (Form Key-Value)", formPanel);

        // Tab 2: Raw Body (JSON / Text)
        JPanel rawPanel = new JPanel(new BorderLayout());
        txtRawBody = new JTextArea("{\n  \"hoten\": \"Nguyễn Văn A\",\n  \"masv\": \"21IT001\",\n  \"noidung\": \"Kiểm thử JSON POST trên trình duyệt\"\n}");
        txtRawBody.setFont(new Font("Consolas", Font.PLAIN, 13));
        rawPanel.add(new JScrollPane(txtRawBody), BorderLayout.CENTER);
        reqTabs.addTab("2. Dữ liệu thô (JSON / Raw Payload)", rawPanel);

        splitPane.setTopComponent(reqTabs);

        // Bottom: Response
        JPanel resPanel = new JPanel(new BorderLayout(4, 4));
        resPanel.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));

        JPanel resHeaderBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        lblStatus = new JLabel("Sẵn sàng gửi HTTP POST...");
        lblStatus.setFont(new Font("Segoe UI", Font.BOLD, 12));
        resHeaderBar.add(new JLabel("Phản hồi từ Server:"));
        resHeaderBar.add(lblStatus);
        resPanel.add(resHeaderBar, BorderLayout.NORTH);

        JTabbedPane resTabs = new JTabbedPane();
        resTabs.setFont(new Font("Segoe UI", Font.BOLD, 12));

        txtResponseBody = new JTextArea();
        txtResponseBody.setEditable(false);
        txtResponseBody.setFont(new Font("Consolas", Font.PLAIN, 12));
        resTabs.addTab("HTTP Response Body", new JScrollPane(txtResponseBody));

        txtResponseHeaders = new JTextArea();
        txtResponseHeaders.setEditable(false);
        txtResponseHeaders.setFont(new Font("Consolas", Font.PLAIN, 12));
        resTabs.addTab("HTTP Request & Response Headers", new JScrollPane(txtResponseHeaders));

        resPanel.add(resTabs, BorderLayout.CENTER);
        splitPane.setBottomComponent(resPanel);
        splitPane.setPreferredSize(new Dimension(720, 420));

        add(splitPane, BorderLayout.CENTER);

        // 3. BOTTOM BAR
        JPanel bottomBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        JButton btnViewInTab = UiUtil.createColoredButton(" Hiển thị kết quả lên Tab trình duyệt ", new Color(26, 115, 232), Color.WHITE, 6);
        btnViewInTab.addActionListener(e -> {
            if (lastPostResponse != null && onResponseReceived != null) {
                onResponseReceived.accept(lastPostResponse);
                setVisible(false);
            } else {
                executePost();
            }
        });

        JButton btnClose = UiUtil.createOutlineButton("Đóng", new Color(203, 213, 225), new Color(30, 41, 59));
        btnClose.addActionListener(e -> setVisible(false));

        bottomBar.add(btnViewInTab);
        bottomBar.add(btnClose);
        add(bottomBar, BorderLayout.SOUTH);
    }

    private void updateBodyPlaceholder() {
        String type = (String) cbContentType.getSelectedItem();
        if (type != null && type.contains("json") && txtRawBody.getText().trim().isEmpty()) {
            txtRawBody.setText("{\n  \"hoten\": \"Nguyễn Văn A\",\n  \"masv\": \"21IT001\",\n  \"noidung\": \"Kiểm thử JSON POST\"\n}");
        }
    }

    private String buildFormEncodedString() {
        if (paramsTable.isEditing() && paramsTable.getCellEditor() != null) {
            paramsTable.getCellEditor().stopCellEditing();
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < paramsModel.getRowCount(); i++) {
            Object k = paramsModel.getValueAt(i, 0);
            Object v = paramsModel.getValueAt(i, 1);
            if (k != null && !k.toString().trim().isEmpty()) {
                if (sb.length() > 0) sb.append("&");
                try {
                    sb.append(URLEncoder.encode(k.toString().trim(), StandardCharsets.UTF_8.name()))
                            .append("=")
                            .append(URLEncoder.encode(v != null ? v.toString() : "", StandardCharsets.UTF_8.name()));
                } catch (Exception ignored) {}
            }
        }
        return sb.toString();
    }

    private void executePost() {
        String url = txtUrl.getText().trim();
        if (url.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Vui lòng nhập địa chỉ URL!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String contentType = (String) cbContentType.getSelectedItem();
        byte[] bodyBytes;

        if (contentType != null && contentType.startsWith("application/x-www-form-urlencoded")) {
            String formStr = buildFormEncodedString();
            if (formStr.isEmpty()) {
                formStr = txtRawBody.getText();
            }
            bodyBytes = formStr.getBytes(StandardCharsets.UTF_8);
        } else {
            bodyBytes = txtRawBody.getText().getBytes(StandardCharsets.UTF_8);
        }

        lblStatus.setText("Đang gửi gói tin HTTP POST qua Socket TCP...");
        lblStatus.setForeground(new Color(26, 115, 232));

        new Thread(() -> {
            try {
                HttpRequest req = new HttpRequest(url, "POST");
                req.setContentType(contentType);
                req.setBody(bodyBytes);

                HttpResponse res = HttpEngine.execute(req);
                lastPostResponse = res;

                javax.swing.SwingUtilities.invokeLater(() -> {
                    lblStatus.setText("HTTP " + res.getStatusCode() + " " + res.getStatusMessage() + " (" + res.getResponseTimeMs() + " ms)");
                    lblStatus.setForeground(res.getStatusCode() == 200 ? new Color(19, 115, 51) : new Color(220, 38, 38));

                    txtResponseHeaders.setText("=== GÓI TIN REQUEST ĐÃ GỬI ===\n"
                            + res.getRawRequestText()
                            + "\n\n=== GÓI TIN RESPONSE HEADER TỪ SERVER ===\n"
                            + res.getRawHeadersString());
                    txtResponseBody.setText(res.getStringBody());

                    HistoryManager.add(url, "POST Response: " + res.getStatusCode(), "POST");
                    if (onResponseReceived != null) {
                        onResponseReceived.accept(res);
                    }
                });
            } catch (Exception ex) {
                javax.swing.SwingUtilities.invokeLater(() -> {
                    lblStatus.setText("Lỗi kết nối: " + ex.getMessage());
                    lblStatus.setForeground(Color.RED);
                    txtResponseBody.setText("Không thể gửi POST: " + ex.getMessage());
                });
            }
        }).start();
    }
}
