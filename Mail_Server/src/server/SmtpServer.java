package server;

import common.MimeUtil;
import config.MailConfig;
import database.DatabaseManager;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * SMTP Server (RFC 5321) tự cài đặt.
 * Lệnh hỗ trợ: HELO, EHLO, AUTH LOGIN, AUTH PLAIN, MAIL FROM, RCPT TO, DATA, RSET, NOOP, VRFY, HELP, QUIT.
 * - Người nhận @localhost: lưu vào hộp thư đến (bảng emails) trên MySQL.
 * - Người nhận bên ngoài (vd @gmail.com): yêu cầu AUTH, sau đó relay qua smtp.gmail.com.
 */
public class SmtpServer extends TcpServer {
    private static final int MAX_MESSAGE_BYTES = 25 * 1024 * 1024;
    private static final int MAX_RECIPIENTS = 100;

    public SmtpServer(int port, MailServerManager manager) {
        super("SMTP", port, manager);
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
        private final MailConfig cfg = MailConfig.getInstance();
        private final DatabaseManager db = DatabaseManager.getInstance();

        private String helo;
        private String authUser;
        private String mailFrom;
        private final List<String> rcpts = new ArrayList<>();

        Session(Socket socket) throws IOException {
            this.socket = socket;
            this.ip = socket.getInetAddress().getHostAddress();
            this.in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.ISO_8859_1));
            this.out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.ISO_8859_1));
        }

        void run() throws IOException {
            manager.log("SMTP", "Kết nối mới từ " + ip + ":" + socket.getPort());
            reply("220 " + cfg.getDomain() + " ESMTP LTM Mail Server sẵn sàng");
            String line;
            while ((line = in.readLine()) != null) {
                String shown = line.toUpperCase().startsWith("AUTH") && line.trim().split("\\s+").length > 2
                        ? line.trim().split("\\s+")[0] + " " + line.trim().split("\\s+")[1] + " ****" : line;
                manager.log("SMTP", ip + " C: " + shown);
                String upper = line.toUpperCase();
                String cmd = upper.split(" ", 2)[0];
                switch (cmd) {
                    case "HELO" -> {
                        helo = arg(line);
                        resetTransaction();
                        reply("250 " + cfg.getDomain() + " Xin chào " + helo);
                    }
                    case "EHLO" -> {
                        helo = arg(line);
                        resetTransaction();
                        reply("250-" + cfg.getDomain() + " Xin chào " + helo + " [" + ip + "]");
                        reply("250-AUTH LOGIN PLAIN");
                        reply("250-SIZE " + MAX_MESSAGE_BYTES);
                        reply("250-8BITMIME");
                        reply("250 HELP");
                    }
                    case "AUTH" -> handleAuth(line);
                    case "MAIL" -> handleMail(line);
                    case "RCPT" -> handleRcpt(line);
                    case "DATA" -> handleData();
                    case "RSET" -> {
                        resetTransaction();
                        reply("250 2.0.0 OK");
                    }
                    case "NOOP" -> reply("250 2.0.0 OK");
                    case "VRFY" -> reply("252 2.5.2 Không xác minh, nhưng sẽ thử gửi");
                    case "HELP" -> reply("214 Lệnh: HELO EHLO AUTH MAIL RCPT DATA RSET NOOP VRFY QUIT");
                    case "QUIT" -> {
                        reply("221 2.0.0 " + cfg.getDomain() + " Tạm biệt");
                        manager.log("SMTP", "Đóng kết nối " + ip);
                        return;
                    }
                    default -> reply("500 5.5.1 Lệnh không được hỗ trợ");
                }
            }
        }

        private void handleAuth(String line) throws IOException {
            if (helo == null) { reply("503 5.5.1 Gửi EHLO trước"); return; }
            if (authUser != null) { reply("503 5.5.1 Đã xác thực rồi"); return; }
            String[] p = line.trim().split("\\s+");
            String mech = p.length > 1 ? p[1].toUpperCase() : "";
            String user, pass;
            try {
                if (mech.equals("LOGIN")) {
                    if (p.length > 2) {
                        user = b64(p[2]);
                    } else {
                        reply("334 VXNlcm5hbWU6"); // "Username:"
                        user = b64(readLineOrThrow());
                    }
                    reply("334 UGFzc3dvcmQ6"); // "Password:"
                    pass = b64(readLineOrThrow());
                } else if (mech.equals("PLAIN")) {
                    String data;
                    if (p.length > 2) data = p[2];
                    else {
                        reply("334 ");
                        data = readLineOrThrow();
                    }
                    String[] parts = b64(data).split("\0");
                    if (parts.length < 3) { reply("501 5.5.2 Dữ liệu AUTH PLAIN sai"); return; }
                    user = parts[1];
                    pass = parts[2];
                } else {
                    reply("504 5.5.4 Cơ chế xác thực không hỗ trợ");
                    return;
                }
            } catch (IllegalArgumentException e) {
                reply("501 5.5.2 Base64 không hợp lệ");
                return;
            }
            String username = MailServerManager.localPart(user.trim());
            if (db.authenticate(username, pass)) {
                authUser = username;
                reply("235 2.7.0 Xác thực thành công");
                manager.event("SMTP", ip, username, "AUTH " + mech, "OK", "");
            } else {
                reply("535 5.7.8 Sai tài khoản hoặc mật khẩu");
                manager.event("SMTP", ip, username, "AUTH " + mech, "FAIL", "Sai mật khẩu");
            }
        }

        private void handleMail(String line) throws IOException {
            if (helo == null) { reply("503 5.5.1 Gửi HELO/EHLO trước"); return; }
            if (mailFrom != null) { reply("503 5.5.1 Đã có MAIL FROM, dùng RSET để bắt đầu lại"); return; }
            String addr = extractPath(line, "FROM:");
            if (addr == null) { reply("501 5.5.4 Cú pháp: MAIL FROM:<dia_chi>"); return; }
            if (authUser != null && !addr.isEmpty()) {
                // Người dùng đã đăng nhập chỉ được gửi bằng chính địa chỉ của mình
                if (!manager.isLocalAddress(addr) || !MailServerManager.localPart(addr).equals(authUser)) {
                    reply("553 5.7.1 Địa chỉ người gửi không thuộc tài khoản " + authUser);
                    return;
                }
            }
            if (authUser == null && !addr.isEmpty() && manager.isLocalAddress(addr)) {
                reply("530 5.7.0 Cần AUTH để gửi thư bằng địa chỉ nội bộ " + addr);
                return;
            }
            mailFrom = addr;
            reply("250 2.1.0 Người gửi <" + addr + "> OK");
        }

        private void handleRcpt(String line) throws IOException {
            if (mailFrom == null) { reply("503 5.5.1 Cần MAIL FROM trước"); return; }
            String addr = extractPath(line, "TO:");
            if (addr == null || addr.isEmpty() || !MimeUtil.isValidEmail(addr) && !addr.matches("[A-Za-z0-9._-]+")) {
                reply("501 5.1.3 Địa chỉ người nhận không hợp lệ");
                return;
            }
            if (!addr.contains("@")) addr = addr + "@" + cfg.getDomain();
            if (rcpts.size() >= MAX_RECIPIENTS) { reply("452 4.5.3 Quá nhiều người nhận"); return; }
            if (manager.isLocalAddress(addr)) {
                if (!db.userExists(MailServerManager.localPart(addr))) {
                    reply("550 5.1.1 Hộp thư <" + addr + "> không tồn tại");
                    return;
                }
            } else {
                if (authUser == null) {
                    reply("530 5.7.0 Cần xác thực (AUTH) để gửi thư ra ngoài – relay bị từ chối");
                    return;
                }
                if (!cfg.isGmailReady()) {
                    reply("550 5.7.1 Server chưa cấu hình Gmail relay nên không gửi được tới " + addr);
                    return;
                }
            }
            rcpts.add(addr.toLowerCase());
            reply("250 2.1.5 Người nhận <" + addr + "> OK");
        }

        private void handleData() throws IOException {
            if (rcpts.isEmpty()) { reply("503 5.5.1 Cần RCPT TO trước"); return; }
            reply("354 Bắt đầu nhập nội dung thư; kết thúc bằng <CRLF>.<CRLF>");
            StringBuilder sb = new StringBuilder();
            boolean tooBig = false;
            String l;
            while (true) {
                l = in.readLine();
                if (l == null) throw new IOException("Client ngắt kết nối giữa chừng khi gửi DATA");
                if (l.equals(".")) break;
                if (l.startsWith("..")) l = l.substring(1); // bỏ dot-stuffing
                if (!tooBig) {
                    sb.append(l).append("\r\n");
                    if (sb.length() > MAX_MESSAGE_BYTES) tooBig = true;
                }
            }
            if (tooBig) {
                reply("552 5.3.4 Thư vượt quá dung lượng cho phép");
                resetTransaction();
                return;
            }
            String id = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            String received = "Received: from " + (helo == null ? "unknown" : helo) + " ([" + ip + "])\r\n"
                    + "\tby " + cfg.getDomain() + " (LTM Mail Server) with " + (authUser != null ? "ESMTPA" : "ESMTP")
                    + " id " + id + ";\r\n\t" + MimeUtil.formatDate(ZonedDateTime.now()) + "\r\n";
            String raw = received + sb;

            MailDelivery.Result r = MailDelivery.deliver(manager, authUser, mailFrom, new ArrayList<>(rcpts), raw, ip);
            if (r.failed.isEmpty()) {
                reply("250 2.0.0 OK id=" + id + " – đã giao " + r.delivered.size() + " hộp thư nội bộ"
                        + (r.relayed.isEmpty() ? "" : ", relay " + r.relayed.size() + " địa chỉ qua Gmail"));
            } else {
                reply("554 5.0.0 Gửi thất bại tới: " + String.join(", ", r.failed.keySet()) + " – "
                        + String.join("; ", r.failed.values()).replace("\r", " ").replace("\n", " "));
            }
            resetTransaction();
        }

        // -----------------------------------------------------------------
        private void resetTransaction() {
            mailFrom = null;
            rcpts.clear();
        }

        private String readLineOrThrow() throws IOException {
            String l = in.readLine();
            if (l == null) throw new IOException("Mất kết nối");
            if (l.equals("*")) throw new IllegalArgumentException("cancel");
            return l.trim();
        }

        private String b64(String s) {
            return new String(Base64.getDecoder().decode(s.trim()), StandardCharsets.UTF_8);
        }

        private String arg(String line) {
            String[] p = line.trim().split("\\s+", 2);
            return p.length > 1 ? p[1] : "unknown";
        }

        /** MAIL FROM:<a@b> SIZE=123 -> a@b ; trả về null nếu sai cú pháp. */
        private String extractPath(String line, String key) {
            int i = line.toUpperCase().indexOf(key);
            if (i < 0) return null;
            String rest = line.substring(i + key.length()).trim();
            if (rest.startsWith("<")) {
                int gt = rest.indexOf('>');
                if (gt < 0) return null;
                return rest.substring(1, gt).trim();
            }
            return rest.split("\\s+")[0].trim();
        }

        private void reply(String s) throws IOException {
            s = MimeUtil.toAscii(s);
            out.write(s);
            out.write("\r\n");
            out.flush();
            manager.log("SMTP", ip + " S: " + s);
        }
    }
}
