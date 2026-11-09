package ui;

import database.DatabaseManager;
import database.UserModel;

import javax.swing.*;
import java.awt.*;
import java.io.File;

/**
 * Hộp thoại thêm mới hoặc chỉnh sửa người dùng FTP trong MySQL.
 */
public class UserDialog extends JDialog {
    private final UserModel user;
    private final boolean isEdit;
    private boolean saved = false;

    private JTextField txtUsername;
    private JPasswordField txtPassword;
    private JTextField txtHomeDir;
    private JCheckBox chkDownload;
    private JCheckBox chkUpload;
    private JCheckBox chkDelete;
    private JCheckBox chkModify;
    private JCheckBox chkActive;

    public UserDialog(Frame owner, UserModel user) {
        super(owner, user == null ? "Thêm người dùng mới" : "Chỉnh sửa người dùng", true);
        this.isEdit = user != null;
        this.user = isEdit ? user : new UserModel();
        initComponents();
        pack();
        setLocationRelativeTo(owner);
    }

    private void initComponents() {
        setLayout(new BorderLayout(10, 10));
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(15, 15, 10, 15));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(5, 5, 5, 5);

        // Tên đăng nhập
        gbc.gridx = 0; gbc.gridy = 0;
        form.add(new JLabel("Tên đăng nhập (Username):"), gbc);
        txtUsername = new JTextField(20);
        if (isEdit) {
            txtUsername.setText(user.getUsername());
            txtUsername.setEditable(false);
            txtUsername.setBackground(new Color(240, 240, 240));
        }
        gbc.gridx = 1; gbc.gridy = 0;
        form.add(txtUsername, gbc);

        // Mật khẩu
        gbc.gridx = 0; gbc.gridy = 1;
        form.add(new JLabel(isEdit ? "Mật khẩu mới (bỏ trống nếu giữ nguyên):" : "Mật khẩu:"), gbc);
        txtPassword = new JPasswordField(20);
        gbc.gridx = 1; gbc.gridy = 1;
        form.add(txtPassword, gbc);

        // Thư mục gốc
        gbc.gridx = 0; gbc.gridy = 2;
        form.add(new JLabel("Thư mục gốc (Home Dir):"), gbc);
        JPanel dirPanel = new JPanel(new BorderLayout(5, 0));
        txtHomeDir = new JTextField(isEdit ? user.getHomeDirectory() : "ftp_root/" + (txtUsername.getText().isEmpty() ? "user" : txtUsername.getText()));
        JButton btnBrowse = new JButton("Chọn...");
        btnBrowse.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser(new File("."));
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                txtHomeDir.setText(chooser.getSelectedFile().getPath().replace('\\', '/'));
            }
        });
        dirPanel.add(txtHomeDir, BorderLayout.CENTER);
        dirPanel.add(btnBrowse, BorderLayout.EAST);
        gbc.gridx = 1; gbc.gridy = 2;
        form.add(dirPanel, gbc);

        // Phân quyền
        JPanel permPanel = new JPanel(new GridLayout(2, 2, 5, 5));
        permPanel.setBorder(BorderFactory.createTitledBorder("Quyền hạn người dùng"));
        chkDownload = new JCheckBox("Cho phép tải về (Download)", isEdit ? user.isCanDownload() : true);
        chkUpload = new JCheckBox("Cho phép tải lên (Upload)", isEdit ? user.isCanUpload() : true);
        chkDelete = new JCheckBox("Cho phép xóa (Delete)", isEdit ? user.isCanDelete() : true);
        chkModify = new JCheckBox("Tạo thư mục/Đổi tên (Modify)", isEdit ? user.isCanModify() : true);
        permPanel.add(chkDownload);
        permPanel.add(chkUpload);
        permPanel.add(chkDelete);
        permPanel.add(chkModify);

        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 2;
        form.add(permPanel, gbc);

        // Kích hoạt
        chkActive = new JCheckBox("Kích hoạt tài khoản này", isEdit ? user.isActive() : true);
        gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 2;
        form.add(chkActive, gbc);

        add(form, BorderLayout.CENTER);

        // Nút Lưu / Hủy
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        JButton btnSave = new JButton("Lưu dữ liệu");
        btnSave.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnSave.setBackground(new Color(40, 167, 69));
        btnSave.setForeground(Color.WHITE);
        btnSave.addActionListener(e -> saveUser());

        JButton btnCancel = new JButton("Hủy bỏ");
        btnCancel.addActionListener(e -> dispose());

        btnPanel.add(btnSave);
        btnPanel.add(btnCancel);
        add(btnPanel, BorderLayout.SOUTH);
    }

    private void saveUser() {
        String username = txtUsername.getText().trim();
        String password = new String(txtPassword.getPassword()).trim();
        String homeDir = txtHomeDir.getText().trim();

        if (username.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Tên đăng nhập không được để trống!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (!isEdit && password.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Mật khẩu không được để trống!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (homeDir.isEmpty()) {
            homeDir = "ftp_root/" + username;
        }

        user.setUsername(username);
        user.setHomeDirectory(homeDir);
        user.setCanDownload(chkDownload.isSelected());
        user.setCanUpload(chkUpload.isSelected());
        user.setCanDelete(chkDelete.isSelected());
        user.setCanModify(chkModify.isSelected());
        user.setActive(chkActive.isSelected());

        DatabaseManager db = DatabaseManager.getInstance();
        boolean ok;
        if (isEdit) {
            ok = db.updateUser(user, password.isEmpty() ? null : password);
        } else {
            ok = db.addUser(user, password);
        }

        if (ok) {
            this.saved = true;
            dispose();
        } else {
            JOptionPane.showMessageDialog(this, "Không thể lưu vào cơ sở dữ liệu MySQL!\nKiểm tra xem tên đăng nhập có bị trùng lặp hoặc kết nối MySQL bị ngắt không.", "Lỗi Database", JOptionPane.ERROR_MESSAGE);
        }
    }

    public boolean isSaved() {
        return saved;
    }
}
