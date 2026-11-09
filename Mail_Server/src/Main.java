import config.MailConfig;
import database.DatabaseManager;
import server.MailServerManager;
import server.ServerListener;
import ui.client.LoginFrame;
import ui.server.MainServerFrame;

import javax.swing.*;
import java.awt.*;
import java.util.Scanner;

/**
 * Điểm khởi chạy chương trình (Entry point) LTM Mail Server & Client:
 * - Mặc định: Khởi chạy giao diện Quản trị Mail Server (MainServerFrame) có tích hợp nút mở Mail Client & phpMyAdmin.
 * - Cờ --client hoặc --mail: Khởi chạy thẳng giao diện Mail Client phong cách Gmail.
 * - Cờ --cli hoặc --headless: Chạy máy chủ SMTP/POP3 chế độ dòng lệnh Console.
 */
public class Main {
    public static void main(String[] args) {
        boolean cliMode = false;
        boolean clientMode = false;

        for (String arg : args) {
            if ("--cli".equalsIgnoreCase(arg) || "--console".equalsIgnoreCase(arg) || "--headless".equalsIgnoreCase(arg)) {
                cliMode = true;
            } else if ("--client".equalsIgnoreCase(arg) || "--mail".equalsIgnoreCase(arg)) {
                clientMode = true;
            }
        }

        if (GraphicsEnvironment.isHeadless()) {
            cliMode = true;
        }

        if (cliMode) {
            runCliMode();
        } else if (clientMode) {
            runClientMode();
        } else {
            runServerGuiMode();
        }
    }

    private static void runServerGuiMode() {
        System.out.println("Đang khởi chạy giao diện LTM Mail Server Manager...");
        SwingUtilities.invokeLater(() -> {
            try {
                // Áp dụng Look & Feel giao diện hệ thống
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                new MainServerFrame().setVisible(true);
            } catch (Exception e) {
                System.err.println("Lỗi khởi chạy GUI Server, chuyển sang CLI: " + e.getMessage());
                runCliMode();
            }
        });
    }

    private static void runClientMode() {
        System.out.println("Đang khởi chạy giao diện LTM Mail Client...");
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                new LoginFrame().setVisible(true);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private static void runCliMode() {
        MailConfig config = MailConfig.getInstance();
        System.out.println("==========================================================");
        System.out.println("      LTM MAIL SERVER (SMTP + POP3 + MYSQL + GMAIL)       ");
        System.out.println("==========================================================");
        System.out.println("Cấu hình kết nối MySQL:");
        System.out.println(" - JDBC URL : " + config.getDbJdbcUrl());
        System.out.println(" - User     : " + config.getDbUser());
        System.out.println(" - SMTP Port: " + config.getSmtpPort());
        System.out.println(" - POP3 Port: " + config.getPop3Port());
        System.out.println("----------------------------------------------------------");

        DatabaseManager db = DatabaseManager.getInstance();
        try {
            db.init();
            System.out.println("[DB] Kết nối MySQL thành công!");
        } catch (Exception e) {
            System.err.println("[DB] Không thể kết nối MySQL: " + e.getMessage());
            System.err.println("[DB] Vui lòng đảm bảo Docker container 'ltm-mysql' trên cổng " + config.getDbPort() + " đang chạy.");
        }

        MailServerManager manager = MailServerManager.getInstance();
        manager.addListener(new ServerListener() {
            @Override
            public void onLog(String protocol, String message) {
                System.out.println(message);
            }
        });

        try {
            manager.start();
        } catch (Exception e) {
            System.err.println("Lỗi khởi động Server: " + e.getMessage());
            return;
        }

        System.out.println("\nCác lệnh Console: 'status', 'users', 'stop', 'start', 'quit'");
        Scanner scanner = new Scanner(System.in);
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine().trim();
            if ("quit".equalsIgnoreCase(line) || "exit".equalsIgnoreCase(line)) {
                manager.stop();
                System.out.println("Đã đóng máy chủ. Tạm biệt!");
                break;
            } else if ("status".equalsIgnoreCase(line)) {
                int[] stats = db.stats();
                System.out.println("Tài khoản: " + stats[0] + " | Số thư trong DB: " + stats[1]
                        + " | Đã relay Gmail: " + stats[2] + " | Lấy từ Gmail: " + stats[3]);
            } else if ("users".equalsIgnoreCase(line)) {
                try {
                    for (var u : db.listUsers()) {
                        System.out.printf("ID: %-3d | User: %-12s | Name: %-15s | Inbox: %d\n",
                                u.id, u.username, u.fullName, u.inboxCount);
                    }
                } catch (Exception ex) {
                    System.err.println("Lỗi: " + ex.getMessage());
                }
            } else if ("stop".equalsIgnoreCase(line)) {
                manager.stop();
            } else if ("start".equalsIgnoreCase(line)) {
                try {
                    manager.start();
                } catch (Exception e) {
                    System.err.println("Lỗi: " + e.getMessage());
                }
            }
        }
    }
}
