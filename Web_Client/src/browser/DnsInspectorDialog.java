package browser;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;

public class DnsInspectorDialog extends JDialog {

    private JTextField txtInput;
    private JTextArea txtResult;

    public DnsInspectorDialog(JFrame parent, String initialUrl) {
        super(parent, "Phân tích URL & Tra cứu DNS IP (Kế thừa url.diachi & url.inet)", false);
        setSize(660, 480);
        setLocationRelativeTo(parent);
        setLayout(new BorderLayout(8, 8));

        initUI(initialUrl != null && !initialUrl.isEmpty() ? initialUrl : WebBrowser.getHomeUrl());
    }

    private void initUI(String defaultVal) {
        JPanel topPanel = new JPanel(new BorderLayout(6, 6));
        topPanel.setBorder(BorderFactory.createEmptyBorder(12, 14, 8, 14));
        topPanel.setBackground(new Color(248, 249, 250));

        JLabel lbl = new JLabel("Nhập địa chỉ URL hoặc tên miền cần bóc tách & phân giải DNS IP:");
        lbl.setFont(new Font("Segoe UI", Font.BOLD, 12));

        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        txtInput = new JTextField(defaultVal);
        txtInput.setFont(new Font("Consolas", Font.PLAIN, 13));
        txtInput.addActionListener(e -> analyze());

        JButton btnAnalyze = UiUtil.createColoredButton(" Phân tích URL & Tra DNS ", new Color(26, 115, 232), Color.WHITE, 6);
        btnAnalyze.addActionListener(e -> analyze());

        row.add(txtInput, BorderLayout.CENTER);
        row.add(btnAnalyze, BorderLayout.EAST);

        topPanel.add(lbl, BorderLayout.NORTH);
        topPanel.add(row, BorderLayout.CENTER);
        add(topPanel, BorderLayout.NORTH);

        txtResult = new JTextArea();
        txtResult.setEditable(false);
        txtResult.setFont(new Font("Consolas", Font.PLAIN, 13));
        txtResult.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        add(new JScrollPane(txtResult), BorderLayout.CENTER);

        JPanel bottomBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 8));
        JButton btnClose = UiUtil.createOutlineButton("Đóng", new Color(203, 213, 225), new Color(30, 41, 59));
        btnClose.addActionListener(e -> setVisible(false));
        bottomBar.add(btnClose);
        add(bottomBar, BorderLayout.SOUTH);

        analyze();
    }

    private void analyze() {
        String input = txtInput.getText().trim();
        if (input.isEmpty()) return;

        txtResult.setText("Đang phân tích cấu trúc URL và tra cứu DNS IP qua InetAddress...\n");

        new Thread(() -> {
            UrlDnsInfo info = UrlDnsInfo.analyze(input);
            String summary = info.getFormattedSummary();
            javax.swing.SwingUtilities.invokeLater(() -> txtResult.setText(summary));
        }).start();
    }
}
