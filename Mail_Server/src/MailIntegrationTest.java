import common.Attachment;
import common.MimeUtil;
import common.ParsedMail;
import config.MailConfig;
import database.DatabaseManager;
import database.MailRecord;
import protocol.Pop3Client;
import protocol.SmtpClient;
import server.MailServerManager;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Kiểm thử tự động tích hợp (Integration Test) cho LTM Mail Server:
 * 1. Kiểm thử MIME Utility (mã hóa header UTF-8, multipart đính kèm, trích xuất địa chỉ).
 * 2. Kiểm thử kết nối MySQL Container (ltm-mysql trên cổng 3308).
 * 3. Khởi động Mail Server (SMTP Server cổng 2525 & POP3 Server cổng 1100).
 * 4. Kiểm thử gửi thư qua SMTP Socket (Alice gửi cho Bob với tệp đính kèm).
 * 5. Kiểm thử nhận thư qua POP3 Socket (Bob đăng nhập, STAT, LIST, RETR, TOP, DELE).
 * 6. Kiểm tra lưu trữ trong cơ sở dữ liệu MySQL (bảng emails & mail_logs).
 */
public class MailIntegrationTest {
    public static void main(String[] args) {
        System.out.println("======================================================================");
        System.out.println("     BẮT ĐẦU KIỂM THỬ TỰ ĐỘNG LTM MAIL SERVER (SMTP + POP3 + MYSQL)   ");
        System.out.println("======================================================================");

        int passed = 0;
        int total = 0;

        // ---------------- TEST 1: MIME & ĐÍNH KÈM ----------------
        total++;
        System.out.println("\n[1] Kiểm tra MIME Utility (Tạo và giải mã thư chuẩn RFC 5322)...");
        try {
            Attachment att = new Attachment("tailieu.txt", "text/plain", "Dữ liệu đính kèm thử nghiệm 12345".getBytes(StandardCharsets.UTF_8));
            String raw = MimeUtil.buildMessage("Alice Nguyễn", "alice@localhost", List.of("bob@localhost"), null,
                    "Tiêu đề thư có dấu tiếng Việt: Thử nghiệm LTM Mail Server!",
                    "Xin chào Bob,\nĐây là nội dung thư thử nghiệm.\nTrân trọng,\nAlice",
                    List.of(att), null, "localhost");

            ParsedMail parsed = MimeUtil.parse(raw);
            if (!parsed.getSubject().contains("Thử nghiệm LTM Mail Server!")) {
                throw new RuntimeException("Tiêu đề giải mã không đúng: " + parsed.getSubject());
            }
            if (parsed.getAttachments().size() != 1 || !parsed.getAttachments().get(0).getFileName().equals("tailieu.txt")) {
                throw new RuntimeException("Tệp đính kèm giải mã không đúng!");
            }
            System.out.println("  => MIME Test: PASS (Tiêu đề: \"" + parsed.getSubject() + "\", 1 tệp đính kèm: "
                    + parsed.getAttachments().get(0).getFileName() + ")");
            passed++;
        } catch (Exception ex) {
            System.err.println("  => MIME Test: FAILED: " + ex.getMessage());
        }

        // ---------------- TEST 2: KẾT NỐI MYSQL ----------------
        total++;
        System.out.println("\n[2] Kiểm tra kết nối cơ sở dữ liệu MySQL (Port " + MailConfig.getInstance().getDbPort() + ")...");
        DatabaseManager db = DatabaseManager.getInstance();
        boolean dbOk = false;
        try {
            db.init();
            dbOk = true;
            System.out.println("  => MySQL Test: PASS (Kết nối database 'mail_db' thành công, đã khởi tạo bảng và tài khoản mẫu)");
            passed++;
        } catch (Exception ex) {
            System.out.println("  => [LƯU Ý] MySQL Container chưa chạy: " + ex.getMessage());
            System.out.println("     (Khi chạy ứng dụng trên máy thật có bật Docker 'ltm-mysql', tính năng DB sẽ kích hoạt đầy đủ)");
        }

        // ---------------- TEST 3: KHỞI ĐỘNG SERVER & GIAO THỨC SOCKET ----------------
        if (dbOk) {
            total++;
            System.out.println("\n[3] Khởi động Mail Server (SMTP: " + MailConfig.getInstance().getSmtpPort()
                    + ", POP3: " + MailConfig.getInstance().getPop3Port() + ")...");
            MailServerManager manager = MailServerManager.getInstance();
            try {
                manager.start();
                System.out.println("  => Server Start: PASS (SMTP và POP3 Server đang lắng nghe)");
                passed++;

                // TEST 4: SMTP CLIENT GỬI THƯ TỪ ALICE CHO BOB
                total++;
                System.out.println("\n[4] Kiểm tra gửi thư qua SMTP Socket (Client -> Mail Server)...");
                try (SmtpClient smtp = new SmtpClient("localhost", MailConfig.getInstance().getSmtpPort(), false)) {
                    smtp.connect(5000);
                    smtp.ehlo("test-client");
                    smtp.authLogin("alice", "123456");

                    String testMsg = MimeUtil.buildMessage("Alice", "alice@localhost", List.of("bob@localhost"), null,
                            "Thư kiểm thử tự động Socket SMTP",
                            "Chào Bob, bức thư này được gửi qua socket SMTP trực tiếp trên cổng 2525!",
                            null, null, "localhost");
                    smtp.sendMail("alice@localhost", List.of("bob@localhost"), testMsg);
                    smtp.quit();
                    System.out.println("  => SMTP Send Test: PASS (Gửi thành công thư từ alice tới bob)");
                    passed++;
                }

                // TEST 5: POP3 CLIENT ĐĂNG NHẬP VÀ ĐỌC THƯ CỦA BOB
                total++;
                System.out.println("\n[5] Kiểm tra nhận thư qua POP3 Socket (Bob đăng nhập và đọc thư)...");
                try (Pop3Client pop = new Pop3Client("localhost", MailConfig.getInstance().getPop3Port(), false)) {
                    pop.connect(5000);
                    pop.login("bob", "123456");

                    int[] stat = pop.stat();
                    System.out.println("  => POP3 STAT: " + stat[0] + " thư, " + stat[1] + " octets");

                    Map<Integer, String> uids = pop.uidl();
                    System.out.println("  => POP3 UIDL: " + uids.size() + " thư được đánh số UID");

                    if (stat[0] > 0) {
                        String retr = pop.retr(stat[0]);
                        ParsedMail pm = MimeUtil.parse(retr);
                        System.out.println("  => POP3 RETR thư mới nhất: Tiêu đề = \"" + pm.getSubject() + "\" từ " + pm.getFrom());
                    }
                    pop.quit();
                    System.out.println("  => POP3 Read Test: PASS");
                    passed++;
                }

                // TEST 6: KIỂM TRA LƯU TRỮ TRONG DATABASE
                total++;
                System.out.println("\n[6] Kiểm tra dữ liệu trong MySQL (Bảng emails & mail_logs)...");
                List<MailRecord> bobMails = db.listMailbox("bob", "INBOX");
                System.out.println("  => Hộp thư đến (INBOX) của bob trong MySQL có: " + bobMails.size() + " thư");
                if (!bobMails.isEmpty()) {
                    System.out.println("  => Database Verification: PASS");
                    passed++;
                } else {
                    throw new RuntimeException("Không tìm thấy thư của bob trong database!");
                }

                manager.stop();
                System.out.println("\n  => Đã dừng Mail Server an toàn.");
            } catch (Exception ex) {
                System.err.println("  => Socket Test FAILED: " + ex.getMessage());
                ex.printStackTrace();
            }
        }

        System.out.println("\n======================================================================");
        System.out.println(" KẾT QUẢ KIỂM THỬ: " + passed + "/" + total + " BÀI KIỂM TRA THÀNH CÔNG");
        System.out.println("======================================================================");
    }
}
