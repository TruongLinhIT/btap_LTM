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
        t.setFont(new Font("Segoe UI", Font.BOLD, 24));
        t.setForeground(Color.WHITE);
        JLabel s = new JLabel("Chat Client-Server (TCP + MySQL)");
        s.setForeground(new Color(220, 235, 255));
        header.add(t);
        header.add(s);

        JTextField host = new JTextField("localhost", 16);
        JTextField port = new JTextField("5000", 16);
        JTextField user = new JTextField(16);
        JPasswordField pass = new JPasswordField(16);
        JButton login = createPrimaryButton("Đăng nhập");
        JButton reg = createSecondaryButton("Đăng ký");
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
            JLabel lbl = new JLabel(labels[i]);
            lbl.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            form.add(lbl, c);
            c.gridx = 1;
            fields[i].setFont(new Font("Segoe UI", Font.PLAIN, 13));
            form.add(fields[i], c);
        }
        JPanel btns = new JPanel(new GridLayout(1, 2, 10, 0));
        btns.setPreferredSize(new Dimension(0, 36));
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

    static JButton createPrimaryButton(String text) {
        JButton b = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (!isEnabled()) {
                    g2.setColor(new Color(170, 195, 225));
                } else if (getModel().isPressed()) {
                    g2.setColor(new Color(0, 105, 217));
                } else if (getModel().isRollover()) {
                    g2.setColor(new Color(24, 144, 255));
                } else {
                    g2.setColor(new Color(0, 132, 255));
                }
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        b.setFont(new Font("Segoe UI", Font.BOLD, 14));
        b.setForeground(Color.WHITE);
        b.setContentAreaFilled(false);
        b.setBorderPainted(false);
        b.setFocusPainted(false);
        b.setOpaque(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    static JButton createSecondaryButton(String text) {
        JButton b = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getModel().isRollover() ? new Color(240, 242, 245) : Color.WHITE);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                g2.setColor(new Color(200, 205, 215));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        b.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        b.setForeground(new Color(50, 50, 50));
        b.setContentAreaFilled(false);
        b.setBorderPainted(false);
        b.setFocusPainted(false);
        b.setOpaque(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }
}
