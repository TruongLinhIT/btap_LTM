package browser;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
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

public class PostDialog extends JDialog {

    private JTextField txtUrl;
    private JComboBox<String> cbContentType;
    private DefaultTableModel paramsModel;
    private JTable tblParams;
    private JTextArea txtRawBody;
    private JTextArea txtResponseHeaders;
    private JTextArea txtResponseBody;
    private JLabel lblStatus;
    private Consumer<HttpResponse> onResponseReceived;

    public PostDialog(JFrame parent, String initialUrl, Consumer<HttpResponse> onResponseReceived) {
        super(parent, "Công cụ gửi HTTP POST Request (Tương tự Postman / Chrome Network)", true);
        this.onResponseReceived = onResponseReceived;
        setSize(850, 620);
        setLocationRelativeTo(parent);
        setLayout(new BorderLayout(8, 8));

        initUI(initialUrl != null && !initialUrl.isEmpty() ? initialUrl : "http://localhost:8080/api/submit");
    }

    private void initUI(String defaultUrl) {
        // TOP PANEL: URL + Content-Type + Send Button
        JPanel topPanel = new JPanel(new BorderLayout(8, 8));
        topPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(218, 220, 224)),
                BorderFactory.createEmptyBorder(12, 12, 12, 12)
        ));

        JPanel urlRow = new JPanel(new BorderLayout(6, 0));
        JLabel lblMethod = new JLabel(" POST ");
        lblMethod.setOpaque(true);
        lblMethod.setBackground(new Color(234, 67, 53));
        lblMethod.setForeground(Color.WHITE);
        lblMethod.setFont(new Font("SansSerif", Font.BOLD, 13));
        lblMethod.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

        txtUrl = new JTextField(defaultUrl);
        txtUrl.setFont(new Font("Monospaced", Font.PLAIN, 13));

        JButton btnSend = new JButton(" Gửi HTTP POST ");
        btnSend.setBackground(new Color(26, 115, 232));
        btnSend.setForeground(Color.WHITE);
        btnSend.setFont(new Font("SansSerif", Font.BOLD, 13));
        btnSend.addActionListener(e -> executePost());

        urlRow.add(lblMethod, BorderLayout.WEST);
        urlRow.add(txtUrl, BorderLayout.CENTER);
        urlRow.add(btnSend, BorderLayout.EAST);

        JPanel typeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        typeRow.add(new JLabel("Content-Type:"));
        String[] types = {
                "application/x-www-form-urlencoded",
                "application/json",
                "text/plain",
                "application/xml"
        };
        cbContentType = new JComboBox<>(types);
        cbContentType.addActionListener(e -> updateBodyPlaceholder());
        typeRow.add(cbContentType);

        topPanel.add(urlRow, BorderLayout.NORTH);
        topPanel.add(typeRow, BorderLayout.SOUTH);
        add(topPanel, BorderLayout.NORTH);

        // CENTER SPLIT PANE: Request Body (Top/Left) vs Response (Bottom/Right)
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        splitPane.setResizeWeight(0.5);

        // 1. REQUEST TABS
        JTabbedPane reqTabs = new JTabbedPane();

        // Tab Form Data (Key - Value)
        JPanel formPanel = new JPanel(new BorderLayout(5, 5));
        paramsModel = new DefaultTableModel(new Object[]{"Tên tham số (Key)", "Giá trị (Value)"}, 0);
        paramsModel.addRow(new Object[]{"hoten", "Nguyen Van A"});
        paramsModel.addRow(new Object[]{"masv", "21IT001"});
        paramsModel.addRow(new Object[]{"noidung", "Thu nghiem gui du lieu POST tu trinh duyet Java Client"});

        tblParams = new JTable(paramsModel);
        tblParams.setRowHeight(24);
        formPanel.add(new JScrollPane(tblParams), BorderLayout.CENTER);

        JPanel btnParamsBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        JButton btnAddParam = new JButton("+ Thêm tham số");
        btnAddParam.addActionListener(e -> paramsModel.addRow(new Object[]{"param", "value"}));
        JButton btnDelParam = new JButton("- Xóa tham số chọn");
        btnDelParam.addActionListener(e -> {
            int row = tblParams.getSelectedRow();
            if (row != -1) paramsModel.removeRow(row);
        });
        JButton btnSyncToRaw = new JButton("Chuyển sang Raw Body");
        btnSyncToRaw.addActionListener(e -> {
            txtRawBody.setText(buildFormEncodedString());
            reqTabs.setSelectedIndex(1);
        });
        btnParamsBar.add(btnAddParam);
        btnParamsBar.add(btnDelParam);
        btnParamsBar.add(btnSyncToRaw);
        formPanel.add(btnParamsBar, BorderLayout.SOUTH);

        reqTabs.addTab("Form UrlEncoded (Key-Value)", formPanel);

        // Tab Raw Body
        txtRawBody = new JTextArea();
        txtRawBody.setFont(new Font("Monospaced", Font.PLAIN, 12));
        txtRawBody.setText("hoten=Nguyen+Van+A&masv=21IT001&noidung=Thu+nghiem+gui+POST");
        reqTabs.addTab("Raw Body / JSON / Text", new JScrollPane(txtRawBody));

        splitPane.setTopComponent(reqTabs);

        // 2. RESPONSE TABS
        JPanel resPanel = new JPanel(new BorderLayout());
        JPanel resHeaderBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        lblStatus = new JLabel("Chưa gửi yêu cầu.");
        lblStatus.setFont(new Font("SansSerif", Font.BOLD, 12));
        resHeaderBar.add(new JLabel("Trạng thái Server: "));
        resHeaderBar.add(lblStatus);
        resPanel.add(resHeaderBar, BorderLayout.NORTH);

        JTabbedPane resTabs = new JTabbedPane();
        txtResponseBody = new JTextArea();
        txtResponseBody.setEditable(false);
        txtResponseBody.setFont(new Font("Monospaced", Font.PLAIN, 12));
        resTabs.addTab("Nội dung phản hồi (Body)", new JScrollPane(txtResponseBody));

        txtResponseHeaders = new JTextArea();
        txtResponseHeaders.setEditable(false);
        txtResponseHeaders.setFont(new Font("Monospaced", Font.PLAIN, 12));
        resTabs.addTab("HTTP Response Headers", new JScrollPane(txtResponseHeaders));

        resPanel.add(resTabs, BorderLayout.CENTER);
        splitPane.setBottomComponent(resPanel);

        add(splitPane, BorderLayout.CENTER);

        // BOTTOM BAR: Close & View in Browser button
        JPanel bottomBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        JButton btnViewInBrowser = new JButton("Hiển thị kết quả này lên tab Trình duyệt");
        btnViewInBrowser.setEnabled(false);
        JButton btnClose = new JButton("Đóng");
        btnClose.addActionListener(e -> setVisible(false));

        bottomBar.add(btnViewInBrowser);
        bottomBar.add(btnClose);
        add(bottomBar, BorderLayout.SOUTH);
    }

    private void updateBodyPlaceholder() {
        String type = (String) cbContentType.getSelectedItem();
        if ("application/json".equals(type) && (txtRawBody.getText().isEmpty() || txtRawBody.getText().contains("="))) {
            txtRawBody.setText("{\n  \"hoten\": \"Nguyen Van A\",\n  \"masv\": \"21IT001\",\n  \"noidung\": \"Kiem tra JSON POST\"\n}");
        }
    }

    private String buildFormEncodedString() {
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
            JOptionPane.showMessageDialog(this, "Vui lòng nhập URL!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String contentType = (String) cbContentType.getSelectedItem();
        byte[] bodyBytes;

        if ("application/x-www-form-urlencoded".equals(contentType)) {
            String formStr = buildFormEncodedString();
            if (formStr.isEmpty()) {
                formStr = txtRawBody.getText();
            }
            bodyBytes = formStr.getBytes(StandardCharsets.UTF_8);
        } else {
            bodyBytes = txtRawBody.getText().getBytes(StandardCharsets.UTF_8);
        }

        lblStatus.setText("Đang gửi yêu cầu POST...");
        lblStatus.setForeground(Color.BLUE);

        new Thread(() -> {
            try {
                HttpRequest req = new HttpRequest(url, "POST");
                req.setContentType(contentType);
                req.setBody(bodyBytes);

                HttpResponse res = HttpEngine.execute(req);

                javax.swing.SwingUtilities.invokeLater(() -> {
                    lblStatus.setText(res.getStatusCode() + " " + res.getStatusMessage() + " (" + res.getResponseTimeMs() + " ms)");
                    if (res.getStatusCode() == 200) {
                        lblStatus.setForeground(new Color(19, 115, 51));
                    } else {
                        lblStatus.setForeground(new Color(234, 67, 53));
                    }
                    txtResponseHeaders.setText(res.getRawHeadersString());
                    txtResponseBody.setText(res.getStringBody());

                    HistoryManager.add(url, "POST Response: " + res.getStatusCode(), "POST");

                    if (onResponseReceived != null) {
                        onResponseReceived.accept(res);
                    }
                });
            } catch (Exception ex) {
                javax.swing.SwingUtilities.invokeLater(() -> {
                    lblStatus.setText("Thất bại: " + ex.getMessage());
                    lblStatus.setForeground(Color.RED);
                    txtResponseBody.setText("Lỗi kết nối: " + ex.getMessage());
                });
            }
        }).start();
    }
}
