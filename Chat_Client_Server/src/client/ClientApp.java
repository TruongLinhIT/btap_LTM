package client;

import javax.swing.*;
import java.awt.*;

/** Màn hình đăng nhập / đăng ký. */
public class ClientApp {
    public static void main(String[] args) throws Exception {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        SwingUtilities.invokeLater(ClientApp::showLogin);
    }

    static void showLogin() {
        JFrame f = new JFrame("Chat LTM - Đăng nhập");
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JPanel header = new JPanel(new GridLayout(2, 1));
        header.setBackground(new Color(0, 132, 255));
        header.setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20));
        JLabel t = new JLabel("💬 Chat LTM");
        t.setFont(new Font("Segoe UI Emoji", Font.BOLD, 26));
        t.setForeground(Color.WHITE);
        JLabel s = new JLabel("Chat Client-Server (TCP + UDP + MySQL)");
        s.setForeground(new Color(220, 235, 255));
        header.add(t);
        header.add(s);

        JTextField host = new JTextField("localhost", 16);
        JTextField port = new JTextField("5000", 16);
        JTextField user = new JTextField(16);
        JPasswordField pass = new JPasswordField(16);
        JButton login = new JButton("Đăng nhập");
        JButton reg = new JButton("Đăng ký");
        login.setBackground(new Color(0, 132, 255));
        login.setForeground(Color.WHITE);
        JLabel status = new JLabel(" ");
        status.setForeground(Color.RED);

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(5, 5, 5, 5);
        c.fill = GridBagConstraints.HORIZONTAL;
        String[] labels = {"Server", "Port", "Tên đăng nhập", "Mật khẩu"};
        JComponent[] fields = {host, port, user, pass};
        for (int i = 0; i < 4; i++) {
            c.gridx = 0;
            c.gridy = i;
            form.add(new JLabel(labels[i]), c);
            c.gridx = 1;
            form.add(fields[i], c);
        }
        JPanel btns = new JPanel(new GridLayout(1, 2, 8, 0));
        btns.add(login);
        btns.add(reg);
        c.gridx = 0;
        c.gridy = 4;
        c.gridwidth = 2;
        form.add(btns, c);
        c.gridy = 5;
        form.add(status, c);

        Runnable doLogin = () -> {
            status.setText(" ");
            login.setEnabled(false);
            String h = host.getText().trim(), u = user.getText().trim(), p = new String(pass.getPassword());
            new Thread(() -> {
                try {
                    NetClient net = NetClient.login(h, Integer.parseInt(port.getText().trim()), u, p);
                    SwingUtilities.invokeLater(() -> {
                        try {
                            new MainFrame(net).setVisible(true);
                            f.dispose();
                        } catch (Exception e) {
                            net.close();
                            status.setText(e.getMessage());
                            login.setEnabled(true);
                        }
                    });
                } catch (Exception e) {
                    SwingUtilities.invokeLater(() -> {
                        status.setText(e.getMessage());
                        login.setEnabled(true);
                    });
                }
            }).start();
        };
        login.addActionListener(e -> doLogin.run());
        pass.addActionListener(e -> doLogin.run());
        reg.addActionListener(e -> {
            String h = host.getText().trim(), u = user.getText().trim(), p = new String(pass.getPassword());
            new Thread(() -> {
                try {
                    NetClient.register(h, Integer.parseInt(port.getText().trim()), u, p);
                    SwingUtilities.invokeLater(() -> {
                        status.setForeground(new Color(0, 140, 60));
                        status.setText("Đăng ký thành công, hãy đăng nhập");
                    });
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> {
                        status.setForeground(Color.RED);
                        status.setText(ex.getMessage());
                    });
                }
            }).start();
        });

        f.add(header, BorderLayout.NORTH);
        f.add(form);
        f.pack();
        f.setLocationRelativeTo(null);
        f.setVisible(true);
    }
}
