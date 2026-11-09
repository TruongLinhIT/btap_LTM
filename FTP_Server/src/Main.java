import config.ServerConfig;
import database.DatabaseManager;
import database.UserModel;
import server.FtpEventListener;
import server.FtpServer;
import server.FtpSession;
import ui.MainServerFrame;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.Scanner;

/**
 * Điểm khởi chạy chương trình (Entry point) FTP Server:
 * - Mặc định khởi chạy giao diện GUI đồ họa (tương tự FileZilla Server).
 * - Hỗ trợ cờ dòng lệnh --cli hoặc --console để chạy chế độ máy chủ nền không cần GUI.
 */
public class Main {
    public static void main(String[] args) {
        boolean cliMode = false;
        int customPort = -1;

        for (int i = 0; i < args.length; i++) {
            if ("--cli".equalsIgnoreCase(args[i]) || "--console".equalsIgnoreCase(args[i]) || "--headless".equalsIgnoreCase(args[i])) {
                cliMode = true;
            } else if ("--port".equalsIgnoreCase(args[i]) && i + 1 < args.length) {
                try {
                    customPort = Integer.parseInt(args[++i]);
                } catch (Exception ignored) {}
            }
        }

        // Tự động kiểm tra nếu môi trường không hỗ trợ đồ họa
        if (GraphicsEnvironment.isHeadless()) {
            cliMode = true;
        }

        if (cliMode) {
            runCliMode(customPort);
        } else {
            runGuiMode();
        }
    }

    private static void runGuiMode() {
        System.out.println("Đang khởi chạy giao diện đồ họa FTP Server Manager...");
        SwingUtilities.invokeLater(() -> {
            try {
                MainServerFrame frame = new MainServerFrame();
                frame.setVisible(true);
            } catch (Exception e) {
                System.err.println("Lỗi khởi chạy GUI, chuyển sang chế độ CLI: " + e.getMessage());
                runCliMode(-1);
            }
        });
    }

    private static void runCliMode(int port) {
        ServerConfig config = ServerConfig.getInstance();
        if (port > 0) {
            config.setServerPort(port);
        }

        System.out.println("==================================================");
        System.out.println("     FTP SERVER - LẬP TRÌNH MẠNG (JAVA & MYSQL)   ");
        System.out.println("==================================================");
        System.out.println("Cấu hình kết nối MySQL:");
        System.out.println(" - JDBC URL : " + config.getDbJdbcUrl());
        System.out.println(" - User     : " + config.getDbUser());
        System.out.println("--------------------------------------------------");

        DatabaseManager db = DatabaseManager.getInstance();
        try {
            db.init();
            System.out.println("[DB] Kết nối cơ sở dữ liệu MySQL thành công!");
        } catch (Exception e) {
            System.err.println("[DB] Không thể kết nối MySQL: " + e.getMessage());
            System.err.println("[DB] Vui lòng đảm bảo Docker container 'ltm-mysql' trên cổng 3308 đang chạy.");
        }

        FtpServer server = new FtpServer();
        server.addListener(new FtpEventListener() {
            @Override
            public void onServerStatusChanged(boolean running, int p, String message) {
                System.out.println("[SERVER] " + message);
            }

            @Override
            public void onClientConnected(FtpSession session) {
                System.out.println("[+] Client kết nối: " + session.getClientIp() + ":" + session.getClientPort());
            }

            @Override
            public void onClientDisconnected(FtpSession session) {
                System.out.println("[-] Client ngắt kết nối: " + session.getClientIp() + " (" + session.getUsername() + ")");
            }

            @Override
            public void onCommand(FtpSession session, String commandLine) {
                System.out.println(" < [" + session.getClientIp() + "] " + commandLine);
            }

            @Override
            public void onResponse(FtpSession session, String responseLine) {
                System.out.println(" > [" + session.getClientIp() + "] " + responseLine);
            }

            @Override
            public void onTransfer(FtpSession session, String action, String filename, long bytesTransferred, boolean success) {
                System.out.println("[TRANSFER] " + action + ": " + filename + " (" + bytesTransferred + " bytes) - " + (success ? "OK" : "FAILED"));
            }
        });

        int serverPort = config.getServerPort();
        try {
            server.start(serverPort);
        } catch (Exception e) {
            System.err.println("Không thể khởi động cổng " + serverPort + ": " + e.getMessage());
            try {
                server.start(2121);
            } catch (Exception ex) {
                System.err.println("Không thể mở cổng 2121: " + ex.getMessage());
                return;
            }
        }

        System.out.println("\nCác lệnh Console: 'users', 'sessions', 'stop', 'start', 'quit'");
        Scanner scanner = new Scanner(System.in);
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine().trim();
            if ("quit".equalsIgnoreCase(line) || "exit".equalsIgnoreCase(line)) {
                server.stop();
                System.out.println("Đã đóng máy chủ. Tạm biệt!");
                break;
            } else if ("users".equalsIgnoreCase(line)) {
                List<UserModel> users = db.getAllUsers();
                System.out.println("--- Danh sách tài khoản (" + users.size() + ") ---");
                for (UserModel u : users) {
                    System.out.printf("ID: %-3d | User: %-12s | Home: %-25s | Active: %s\n",
                            u.getId(), u.getUsername(), u.getHomeDirectory(), u.isActive());
                }
            } else if ("sessions".equalsIgnoreCase(line)) {
                List<FtpSession> sessions = server.getActiveSessions();
                System.out.println("--- Phiên kết nối (" + sessions.size() + ") ---");
                for (FtpSession s : sessions) {
                    System.out.println(s);
                }
            } else if ("stop".equalsIgnoreCase(line)) {
                server.stop();
            } else if ("start".equalsIgnoreCase(line)) {
                try {
                    server.start(config.getServerPort());
                } catch (Exception e) {
                    System.err.println("Lỗi: " + e.getMessage());
                }
            }
        }
    }
}
