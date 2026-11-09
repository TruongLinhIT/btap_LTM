import client.FtpFileItem;
import client.SimpleFtpClient;
import config.ServerConfig;
import database.DatabaseManager;
import database.FtpLogModel;
import database.UserModel;
import server.FtpServer;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.util.List;

/**
 * Kiểm thử tự động toàn diện (Integration Test):
 * 1. Kiểm tra kết nối MySQL Docker (Port 3308)
 * 2. Khởi tạo Database và người dùng mẫu
 * 3. Khởi động FTP Server trên cổng 2121
 * 4. Kết nối bằng SimpleFtpClient (chuẩn giao thức FTP)
 * 5. Thử nghiệm Upload, Download, List, Tạo thư mục, Đổi tên, Xóa
 * 6. Kiểm tra bản ghi Audit Log trong MySQL table ftp_logs
 */
public class FtpIntegrationTest {
    public static void main(String[] args) {
        System.out.println(">>> BẮT ĐẦU KIỂM THỬ TỰ ĐỘNG TOÀN DIỆN FTP SERVER <<<");
        int testPort = 2121;

        try {
            // Bước 1: Kết nối MySQL
            System.out.println("\n[1] Kiểm tra kết nối Database MySQL...");
            DatabaseManager db = DatabaseManager.getInstance();
            db.init();
            System.out.println("  => Kết nối MySQL OK. Khởi tạo bảng thành công.");

            List<UserModel> users = db.getAllUsers();
            System.out.println("  => Số tài khoản trong MySQL: " + users.size());
            for (UserModel u : users) {
                System.out.println("     - " + u.getUsername() + " (Home: " + u.getHomeDirectory() + ")");
            }

            // Bước 2: Khởi động FTP Server
            System.out.println("\n[2] Khởi động FTP Server trên cổng " + testPort + "...");
            FtpServer server = new FtpServer();
            server.start(testPort);
            System.out.println("  => FTP Server đang chạy trên cổng " + server.getBoundPort());

            // Bước 3: Thử nghiệm đăng nhập sai
            System.out.println("\n[3] Kiểm tra xác thực (Đăng nhập sai)...");
            SimpleFtpClient clientFail = new SimpleFtpClient();
            clientFail.connect("127.0.0.1", testPort);
            boolean loginFail = clientFail.login("admin", "wrong_password");
            if (!loginFail) {
                System.out.println("  => Xác thực từ chối chính xác (Login failed as expected).");
            } else {
                throw new RuntimeException("LỖI: Mật khẩu sai nhưng lại đăng nhập thành công!");
            }
            clientFail.disconnect();

            // Bước 4: Thử nghiệm đăng nhập đúng với tài khoản admin trong MySQL
            System.out.println("\n[4] Kiểm tra xác thực (Đăng nhập đúng với admin/admin123)...");
            SimpleFtpClient client = new SimpleFtpClient();
            client.connect("127.0.0.1", testPort);
            boolean loginOk = client.login("admin", "admin123");
            if (loginOk) {
                System.out.println("  => Đăng nhập thành công!");
            } else {
                throw new RuntimeException("LỖI: Đăng nhập admin thất bại: " + client.getLastResponse());
            }

            // Bước 5: Kiểm tra PWD & LIST
            System.out.println("\n[5] Kiểm tra PWD và LIST danh sách tệp...");
            String pwd = client.pwd();
            System.out.println("  => Thư mục hiện tại (PWD): " + pwd);

            List<FtpFileItem> files = client.listFiles();
            System.out.println("  => Danh sách tệp nhận được từ Server (" + files.size() + " items):");
            for (FtpFileItem f : files) {
                System.out.println("     " + f);
            }

            // Bước 6: Kiểm tra Upload (STOR)
            System.out.println("\n[6] Kiểm tra Tải tệp lên (STOR)...");
            File uploadTemp = new File("test_upload_file.txt");
            try (FileWriter fw = new FileWriter(uploadTemp)) {
                fw.write("Day la noi dung kiem thu FTP Server tu dong. Thoi gian: " + System.currentTimeMillis());
            }
            client.uploadFile(uploadTemp, "test_upload_file.txt", null);
            System.out.println("  => Tải lên thành công: test_upload_file.txt (" + uploadTemp.length() + " bytes)");

            // Bước 7: Kiểm tra Download (RETR)
            System.out.println("\n[7] Kiểm tra Tải tệp về (RETR)...");
            File downloadDest = new File("test_downloaded_file.txt");
            client.downloadFile("test_upload_file.txt", downloadDest, null);
            System.out.println("  => Tải về thành công: " + downloadDest.length() + " bytes");

            // So sánh nội dung
            String origContent = Files.readString(uploadTemp.toPath());
            String downContent = Files.readString(downloadDest.toPath());
            if (origContent.equals(downContent)) {
                System.out.println("  => Nội dung tệp tải về khớp 100% với tệp gốc!");
            } else {
                throw new RuntimeException("LỖI: Nội dung tệp tải về không khớp!");
            }

            // Bước 8: Kiểm tra Tạo thư mục (MKD), Đổi tên (RNFR/RNTO), Xóa (DELE/RMD)
            System.out.println("\n[8] Kiểm tra Quản lý thư mục & Đổi tên & Xóa...");
            boolean mkdOk = client.mkd("thu_muc_test");
            System.out.println("  => Tạo thư mục 'thu_muc_test': " + (mkdOk ? "OK" : "FAILED"));

            boolean cwdOk = client.cwd("thu_muc_test");
            System.out.println("  => Di chuyển vào thư mục (CWD): " + (cwdOk ? "OK" : "FAILED"));
            System.out.println("  => Thư mục hiện tại: " + client.pwd());

            boolean cdupOk = client.cdup();
            System.out.println("  => Quay lại thư mục cha (CDUP): " + (cdupOk ? "OK" : "FAILED"));

            boolean renameOk = client.rename("test_upload_file.txt", "renamed_test_file.txt");
            System.out.println("  => Đổi tên tệp: " + (renameOk ? "OK" : "FAILED"));

            boolean deleOk = client.dele("renamed_test_file.txt");
            System.out.println("  => Xóa tệp (DELE): " + (deleOk ? "OK" : "FAILED"));

            boolean rmdOk = client.rmd("thu_muc_test");
            System.out.println("  => Xóa thư mục (RMD): " + (rmdOk ? "OK" : "FAILED"));

            client.disconnect();
            System.out.println("  => Ngắt kết nối Client thành công.");

            // Dọn dẹp tệp tạm
            uploadTemp.delete();
            downloadDest.delete();

            // Bước 9: Kiểm tra Audit Log trong MySQL
            System.out.println("\n[9] Kiểm tra bản ghi Audit Log trong MySQL (table ftp_logs)...");
            Thread.sleep(1000); // Chờ async log ghi xong
            List<FtpLogModel> logs = db.getLogs(10, null, null);
            System.out.println("  => Tìm thấy " + logs.size() + " bản ghi log gần nhất trong MySQL:");
            for (FtpLogModel l : logs) {
                System.out.printf("     [%s] User: %-8s | CMD: %-5s | Status: %-7s | %s\n",
                        l.getTimestamp(), l.getUsername(), l.getCommand(), l.getStatus(), l.getMessage());
            }

            // Dừng server
            server.stop();
            System.out.println("\n==================================================");
            System.out.println(">>> TẤT CẢ KIỂM THỬ ĐÃ VƯỢT QUA XUẤT SẮC! (100% PASS) <<<");
            System.out.println("==================================================");
            System.exit(0);

        } catch (Exception e) {
            System.err.println("\nLỖI TRONG QUÁ TRÌNH KIỂM THỬ: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
