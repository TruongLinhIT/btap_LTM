package ui.server;

import database.DatabaseManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Hộp thoại thêm mới hoặc đổi mật khẩu người dùng trong MySQL Database (mail_db.users).
 */
public class UserDialog extends JDialog {
    private final JTextField txtUsername = new JTextField();
    private final JTextField txtFullName = new JTextField();
    private final JPasswordField txtPassword = new JPasswordField();
    private final JButton btnSave = new JButton("Lưu tài khoản");
    private final JButton btnCancel = new JButton("Hủy bỏ");
    private boolean saved = false;

    public UserDialog(Frame owner, String editUsername) {
        super(owner, editUsername == null ? "Thêm người dùng mới" : "Đổi mật khẩu người dùng", true);
        setSize(400, 260);
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout());

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(new EmptyBorder(16, 20, 16, 20));
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(6, 6, 6, 6);
        g.fill = GridBagConstraints.HORIZONTAL;

        g.gridx = 0; g.gridy = 0; g.weightx = 0;
        form.add(new JLabel("Tên đăng nhập (username):"), g);
        g.gridx = 1; g.weightx = 1;
        form.add(txtUsername, g);

        g.gridx = 0; g.gridy = 1; g.weightx = 0;
        form.add(new JLabel("Họ và tên hiển thị:"), g);
        g.gridx = 1; g.weightx = 1;
        form.add(txtFullName, g);

        g.gridx = 0; g.gridy = 2; g.weightx = 0;
        form.add(new JLabel("Mật khẩu:"), g);
        g.gridx = 1; g.weightx = 1;
        form.add(txtPassword, g);

        if (editUsername != null) {
            txtUsername.setText(editUsername);
            txtUsername.setEditable(false);
            txtFullName.setEnabled(false);
            btnSave.setText("Đổi mật khẩu");
        }

        add(form, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        btnSave.setBackground(new Color(0x1A, 0x73, 0xE8));
        btnSave.setForeground(Color.WHITE);
        btnSave.setFont(btnSave.getFont().deriveFont(Font.BOLD));
        bottom.add(btnSave);
        bottom.add(btnCancel);
        add(bottom, BorderLayout.SOUTH);

        btnCancel.addActionListener(e -> dispose());
        btnSave.addActionListener(e -> {
            String u = txtUsername.getText().trim();
            String p = new String(txtPassword.getPassword()).trim();
            String n = txtFullName.getText().trim();
            if (u.isBlank() || p.isBlank()) {
                JOptionPane.showMessageDialog(this, "Vui lòng nhập đầy đủ tên đăng nhập và mật khẩu!");
                return;
            }
            try {
                DatabaseManager db = DatabaseManager.getInstance();
                if (editUsername != null) {
                    db.changePassword(u, p);
                    JOptionPane.showMessageDialog(this, "Đã đổi mật khẩu thành công!");
                } else {
                    if (db.userExists(u)) {
                        JOptionPane.showMessageDialog(this, "Tên đăng nhập đã tồn tại!");
                        return;
                    }
                    db.addUser(u, p, n);
                    JOptionPane.showMessageDialog(this, "Đã thêm người dùng mới thành công!");
                }
                saved = true;
                dispose();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Lỗi cơ sở dữ liệu: " + ex.getMessage(),
                        "Lỗi", JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    public boolean isSaved() {
        return saved;
    }
}
