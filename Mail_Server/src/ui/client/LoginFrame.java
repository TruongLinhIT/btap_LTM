package ui.client;

import client.MailClientService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Màn hình đăng nhập Mail Client (kết nối POP3 để xác thực).
 */
public class LoginFrame extends JFrame {
    private final JTextField hostField = new JTextField("localhost");
    private final JTextField smtpField = new JTextField("2525");
    private final JTextField popField = new JTextField("1100");
    private final JTextField emailField = new JTextField("alice@localhost");
    private final JPasswordField passField = new JPasswordField("123456");
    private final JTextField nameField = new JTextField("");
    private final JButton loginBtn = new JButton("Đăng nhập");

    public LoginFrame() {
        super("LTM Mail – Đăng nhập");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(440, 520);
        setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout());
        root.setBorder(new EmptyBorder(20, 30, 20, 30));
        root.setBackground(Color.WHITE);

        JLabel logo = new JLabel("<html><center><span style='font-size:28px;color:#EA4335'><b>M</b></span>"
                + "<span style='font-size:22px;color:#555'> LTM Mail</span><br>"
                + "<span style='color:#777'>Đăng nhập để tiếp tục</span></center></html>", SwingConstants.CENTER);
        root.add(logo, BorderLayout.NORTH);

        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(5, 4, 5, 4);
        g.fill = GridBagConstraints.HORIZONTAL;
        int r = 0;
        r = row(form, g, r, "Email:", emailField);
        r = row(form, g, r, "Mật khẩu:", passField);
        r = row(form, g, r, "Tên hiển thị:", nameField);
        r = row(form, g, r, "Mail Server:", hostField);
        r = row(form, g, r, "Cổng SMTP:", smtpField);
        r = row(form, g, r, "Cổng POP3:", popField);
        JLabel hint = new JLabel("<html><span style='color:#888'>Tài khoản mẫu: alice / bob (123456), admin (admin123)</span></html>");
        g.gridx = 0; g.gridy = r; g.gridwidth = 2;
        form.add(hint, g);
        root.add(form, BorderLayout.CENTER);

        loginBtn.setBackground(new Color(0x0B, 0x57, 0xD0));
        loginBtn.setForeground(Color.WHITE);
        loginBtn.setFont(loginBtn.getFont().deriveFont(Font.BOLD, 14f));
        loginBtn.setPreferredSize(new Dimension(10, 40));
        root.add(loginBtn, BorderLayout.SOUTH);
        setContentPane(root);

        loginBtn.addActionListener(e -> doLogin());
        getRootPane().setDefaultButton(loginBtn);
    }

    private int row(JPanel p, GridBagConstraints g, int r, String label, JComponent c) {
        g.gridwidth = 1;
        g.gridx = 0; g.gridy = r; g.weightx = 0;
        p.add(new JLabel(label), g);
        g.gridx = 1; g.weightx = 1;
        p.add(c, g);
        return r + 1;
    }

    private void doLogin() {
        String email = emailField.getText().trim();
        if (!email.contains("@")) email = email + "@localhost";
        int smtp, pop;
        try {
            smtp = Integer.parseInt(smtpField.getText().trim());
            pop = Integer.parseInt(popField.getText().trim());
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Cổng không hợp lệ");
            return;
        }
        MailClientService svc = new MailClientService(hostField.getText().trim(), smtp, pop, email,
                new String(passField.getPassword()));
        svc.setFullName(nameField.getText().isBlank() ? svc.getUsername() : nameField.getText().trim());
        loginBtn.setEnabled(false);
        loginBtn.setText("Đang kết nối...");
        new SwingWorker<Integer, Void>() {
            @Override
            protected Integer doInBackground() throws Exception {
                return svc.login();
            }

            @Override
            protected void done() {
                try {
                    get();
                    new MailClientFrame(svc).setVisible(true);
                    dispose();
                } catch (Exception ex) {
                    Throwable c = ex.getCause() == null ? ex : ex.getCause();
                    JOptionPane.showMessageDialog(LoginFrame.this, "Đăng nhập thất bại:\n" + c.getMessage()
                                    + "\n\n(Hãy chắc chắn Mail Server đang chạy)", "Lỗi", JOptionPane.ERROR_MESSAGE);
                    loginBtn.setEnabled(true);
                    loginBtn.setText("Đăng nhập");
                }
            }
        }.execute();
    }
}
