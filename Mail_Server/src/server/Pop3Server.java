package server;

import common.MimeUtil;
import config.MailConfig;
import database.DatabaseManager;
import database.MailRecord;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * POP3 Server (RFC 1939) tự cài đặt, dữ liệu hộp thư lấy từ MySQL.
 * Lệnh chuẩn: USER, PASS, STAT, LIST, UIDL, RETR, TOP, DELE, RSET, NOOP, CAPA, QUIT.
 * Lệnh mở rộng: XFOLDER INBOX|SENT|TRASH – chọn thư mục (để client hiển thị giống Gmail).
 * Thư lấy ra được thêm header X-LTM-Id / X-LTM-Read / X-LTM-Folder để client biết trạng thái đã đọc.
 */
public class Pop3Server extends TcpServer {

    public Pop3Server(int port, MailServerManager manager) {
        super("POP3", port, manager);
    }

    @Override
    protected void handle(Socket socket) throws Exception {
        new Session(socket).run();
    }

    private class Session {
        private final Socket socket;
        private final String ip;
        private final BufferedReader in;
        private final BufferedWriter out;
        private final DatabaseManager db = DatabaseManager.getInstance();

        private String pendingUser;
        private String user;               // != null => trạng thái TRANSACTION
        private String folder = "INBOX";
        private List<MailRecord> mailbox = new ArrayList<>();
        private final Set<Integer> deleted = new HashSet<>();

        Session(Socket socket) throws IOException {
            this.socket = socket;
            this.ip = socket.getInetAddress().getHostAddress();
            this.in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.ISO_8859_1));
            this.out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.ISO_8859_1));
        }

        void run() throws IOException {
            manager.log("POP3", "Kết nối mới từ " + ip + ":" + socket.getPort());
            ok("LTM POP3 server sẵn sàng (" + MailConfig.getInstance().getDomain() + ")");
            String line;
            while ((line = in.readLine()) != null) {
                String[] p = line.trim().split("\\s+");
                String cmd = p[0].toUpperCase();
                manager.log("POP3", ip + " C: " + (cmd.equals("PASS") ? "PASS ********" : line));
                try {
                    if (cmd.equals("QUIT")) {
                        quit();
                        return;
                    }
                    if (cmd.equals("CAPA")) {
                        capa();
                        continue;
                    }
                    if (user == null) authorization(cmd, p);
                    else transaction(cmd, p);
                } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
                    err("Tham số không hợp lệ");
                } catch (Exception e) {
                    err("Lỗi server: " + e.getMessage());
                }
            }
        }

        private void authorization(String cmd, String[] p) throws Exception {
            switch (cmd) {
                case "USER" -> {
                    if (p.length < 2) { err("Thiếu tên đăng nhập"); return; }
                    pendingUser = MailServerManager.localPart(p[1]);
                    ok("Hãy gửi mật khẩu cho " + pendingUser);
                }
                case "PASS" -> {
                    if (pendingUser == null) { err("Cần lệnh USER trước"); return; }
                    String pass = p.length > 1 ? String.join(" ", java.util.Arrays.copyOfRange(p, 1, p.length)) : "";
                    if (db.authenticate(pendingUser, pass)) {
                        user = pendingUser;
                        loadFolder("INBOX");
                        ok("Đăng nhập thành công, có " + mailbox.size() + " thư (" + totalSize() + " octets)");
                        manager.event("POP3", ip, user, "LOGIN", "OK", mailbox.size() + " thư trong INBOX");
                    } else {
                        manager.event("POP3", ip, pendingUser, "LOGIN", "FAIL", "Sai mật khẩu");
                        pendingUser = null;
                        err("Sai tài khoản hoặc mật khẩu");
                    }
                }
                case "NOOP" -> ok("");
                default -> err("Cần đăng nhập trước (USER/PASS)");
            }
        }

        private void transaction(String cmd, String[] p) throws Exception {
            switch (cmd) {
                case "STAT" -> ok(countAlive() + " " + totalSize());
                case "LIST" -> {
                    if (p.length > 1) {
                        int n = msgNum(p[1]);
                        if (n < 0) return;
                        ok(n + " " + mailbox.get(n - 1).sizeBytes);
                    } else {
                        ok(countAlive() + " thư (" + totalSize() + " octets)");
                        for (int i = 0; i < mailbox.size(); i++)
                            if (!deleted.contains(i + 1)) line((i + 1) + " " + mailbox.get(i).sizeBytes);
                        line(".");
                    }
                }
                case "UIDL" -> {
                    if (p.length > 1) {
                        int n = msgNum(p[1]);
                        if (n < 0) return;
                        ok(n + " " + uid(mailbox.get(n - 1)));
                    } else {
                        ok("Danh sách UID");
                        for (int i = 0; i < mailbox.size(); i++)
                            if (!deleted.contains(i + 1)) line((i + 1) + " " + uid(mailbox.get(i)));
                        line(".");
                    }
                }
                case "RETR" -> {
                    int n = msgNum(p.length > 1 ? p[1] : "");
                    if (n < 0) return;
                    MailRecord m = mailbox.get(n - 1);
                    String raw = db.getRawMessage(m.id);
                    if (raw == null) { err("Thư không còn tồn tại"); return; }
                    ok(m.sizeBytes + " octets");
                    sendMultiline(extraHeaders(m) + raw, -1);
                    if (!m.read) {
                        db.markRead(m.id);
                        m.read = true;
                        manager.fireDataChanged();
                    }
                    manager.event("POP3", ip, user, "RETR " + n, "OK", "id=" + m.id + " (" + folder + ")");
                }
                case "TOP" -> {
                    int n = msgNum(p.length > 1 ? p[1] : "");
                    if (n < 0) return;
                    int lines = Integer.parseInt(p[2]);
                    MailRecord m = mailbox.get(n - 1);
                    String raw = db.getRawMessage(m.id);
                    if (raw == null) { err("Thư không còn tồn tại"); return; }
                    ok("Header và " + lines + " dòng đầu");
                    sendMultiline(extraHeaders(m) + raw, lines);
                }
                case "DELE" -> {
                    int n = msgNum(p.length > 1 ? p[1] : "");
                    if (n < 0) return;
                    deleted.add(n);
                    ok("Thư " + n + " đã được đánh dấu xóa");
                }
                case "RSET" -> {
                    deleted.clear();
                    ok("Đã bỏ đánh dấu xóa, có " + mailbox.size() + " thư");
                }
                case "NOOP" -> ok("");
                case "XFOLDER" -> {
                    String f = p.length > 1 ? p[1].toUpperCase() : "";
                    if (!List.of("INBOX", "SENT", "TRASH").contains(f)) { err("Thư mục phải là INBOX, SENT hoặc TRASH"); return; }
                    if (!deleted.isEmpty()) { err("Còn thư đang đánh dấu xóa, hãy QUIT hoặc RSET trước"); return; }
                    loadFolder(f);
                    ok("Đã chọn " + f + ", có " + mailbox.size() + " thư (" + totalSize() + " octets)");
                }
                case "USER", "PASS" -> err("Đã đăng nhập rồi");
                default -> err("Lệnh không được hỗ trợ");
            }
        }

        private void quit() throws Exception {
            if (user != null && !deleted.isEmpty()) {
                List<Long> ids = new ArrayList<>();
                for (int n : deleted) ids.add(mailbox.get(n - 1).id);
                db.deleteOrTrash(ids, folder);
                manager.event("POP3", ip, user, "UPDATE", "OK", (folder.equals("TRASH") ? "Xóa vĩnh viễn " : "Chuyển vào thùng rác ")
                        + ids.size() + " thư từ " + folder);
                manager.fireDataChanged();
            }
            ok("Tạm biệt" + (user != null ? " " + user : ""));
            manager.log("POP3", "Đóng kết nối " + ip);
        }

        private void capa() throws IOException {
            ok("Danh sách khả năng");
            line("USER");
            line("TOP");
            line("UIDL");
            line("XFOLDER");
            line("IMPLEMENTATION LTM-Mail-Server");
            line(".");
        }

        // -----------------------------------------------------------------
        private void loadFolder(String f) throws Exception {
            folder = f;
            mailbox = db.listMailbox(user, f);
            deleted.clear();
        }

        private String extraHeaders(MailRecord m) {
            return "X-LTM-Id: " + m.id + "\r\nX-LTM-Read: " + (m.read ? "1" : "0") + "\r\nX-LTM-Folder: " + folder + "\r\n";
        }

        private String uid(MailRecord m) {
            return "ltm-" + m.id;
        }

        private int msgNum(String s) throws IOException {
            int n;
            try {
                n = Integer.parseInt(s);
            } catch (NumberFormatException e) {
                err("Số thứ tự thư không hợp lệ");
                return -1;
            }
            if (n < 1 || n > mailbox.size()) { err("Không có thư số " + n); return -1; }
            if (deleted.contains(n)) { err("Thư " + n + " đã bị đánh dấu xóa"); return -1; }
            return n;
        }

        private int countAlive() {
            return mailbox.size() - deleted.size();
        }

        private long totalSize() {
            long t = 0;
            for (int i = 0; i < mailbox.size(); i++) if (!deleted.contains(i + 1)) t += mailbox.get(i).sizeBytes;
            return t;
        }

        /** Gửi dữ liệu nhiều dòng có dot-stuffing; bodyLines >= 0 => chỉ gửi header + n dòng thân (lệnh TOP). */
        private void sendMultiline(String raw, int bodyLines) throws IOException {
            String[] lines = raw.replace("\r\n", "\n").split("\n", -1);
            boolean inBody = false;
            int bodyCount = 0;
            int end = lines.length;
            if (end > 0 && lines[end - 1].isEmpty()) end--;
            for (int i = 0; i < end; i++) {
                String l = lines[i];
                if (inBody && bodyLines >= 0) {
                    if (bodyCount >= bodyLines) break;
                    bodyCount++;
                }
                if (!inBody && l.isEmpty()) inBody = true;
                if (l.startsWith(".")) out.write(".");
                out.write(l);
                out.write("\r\n");
            }
            out.write(".\r\n");
            out.flush();
            manager.log("POP3", ip + " S: <" + (bodyLines >= 0 ? "header thư" : "toàn bộ thư") + "> + \".\"");
        }

        private void ok(String s) throws IOException {
            String l = "+OK" + (s.isEmpty() ? "" : " " + s);
            line(l);
            manager.log("POP3", ip + " S: " + MimeUtil.toAscii(l));
        }

        private void err(String s) throws IOException {
            String l = "-ERR " + s;
            line(l);
            manager.log("POP3", ip + " S: " + MimeUtil.toAscii(l));
        }

        private void line(String s) throws IOException {
            out.write(MimeUtil.toAscii(s));
            out.write("\r\n");
            out.flush();
        }
    }
}
