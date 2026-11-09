package browser;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
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
        setSize(650, 480);
        setLocationRelativeTo(parent);
        setLayout(new BorderLayout(8, 8));

        initUI(initialUrl != null && !initialUrl.isEmpty() ? initialUrl : "http://vku.udn.vn");
    }

    private void initUI(String defaultVal) {
        JPanel topPanel = new JPanel(new BorderLayout(6, 6));
        topPanel.setBorder(BorderFactory.createEmptyBorder(10, 12, 6, 12));

        JLabel lbl = new JLabel("Nhập URL hoặc Hostname:");
        lbl.setFont(new Font("SansSerif", Font.BOLD, 12));

        JPanel row = new JPanel(new BorderLayout(6, 0));
        txtInput = new JTextField(defaultVal);
        txtInput.setFont(new Font("Monospaced", Font.PLAIN, 13));
        txtInput.addActionListener(e -> analyze());

        JButton btnAnalyze = new JButton(" Phân tích URL & Tra DNS ");
        btnAnalyze.setBackground(new Color(26, 115, 232));
        btnAnalyze.setForeground(Color.WHITE);
        btnAnalyze.setFont(new Font("SansSerif", Font.BOLD, 12));
        btnAnalyze.addActionListener(e -> analyze());

        row.add(txtInput, BorderLayout.CENTER);
        row.add(btnAnalyze, BorderLayout.EAST);

        topPanel.add(lbl, BorderLayout.NORTH);
        topPanel.add(row, BorderLayout.CENTER);
        add(topPanel, BorderLayout.NORTH);

        txtResult = new JTextArea();
        txtResult.setEditable(false);
        txtResult.setFont(new Font("Monospaced", Font.PLAIN, 13));
        txtResult.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        add(new JScrollPane(txtResult), BorderLayout.CENTER);

        JPanel bottomBar = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnClose = new JButton("Đóng");
        btnClose.addActionListener(e -> setVisible(false));
        bottomBar.add(btnClose);
        add(bottomBar, BorderLayout.SOUTH);

        analyze();
    }

    private void analyze() {
        String input = txtInput.getText().trim();
        if (input.isEmpty()) return;

        txtResult.setText("Đang tra cứu DNS và phân tích cấu trúc URL...\n");

        new Thread(() -> {
            UrlDnsInfo info = UrlDnsInfo.analyze(input);
            String summary = info.getFormattedSummary();
            javax.swing.SwingUtilities.invokeLater(() -> txtResult.setText(summary));
        }).start();
    }
}
