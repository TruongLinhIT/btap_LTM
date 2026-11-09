package server;

import common.MimeUtil;
import common.ParsedMail;
import config.MailConfig;
import database.DatabaseManager;
import protocol.Pop3Client;
import protocol.SmtpClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Cổng kết nối Gmail của Mail Server:
 * - relay(): chuyển thư của user nội bộ ra Internet qua SMTP của Gmail (smtp.gmail.com:465, SSL, AUTH LOGIN).
 *   Header Reply-To được đặt là  taikhoan+USER@gmail.com  để thư trả lời quay về đúng user nội bộ.
 * - fetch(): định kỳ lấy thư mới từ Gmail qua POP3 (pop.gmail.com:995, SSL) và lưu vào MySQL
 *   (giao cho user khớp với phần "+USER" trong địa chỉ nhận, nếu không có thì giao cho user mặc định).
 */
public class GmailGateway {
    private final MailServerManager manager;
    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> task;
    private final Object fetchLock = new Object();

    public GmailGateway(MailServerManager manager) {
        this.manager = manager;
    }

    // =====================================================================
    //  GỬI RA GMAIL (SMTP CLIENT -> smtp.gmail.com)
    // =====================================================================

    public void relay(String localUser, List<String> recipients, String raw) throws Exception {
        MailConfig cfg = MailConfig.getInstance();
        if (!cfg.isGmailReady()) throw new IllegalStateException("Chưa cấu hình tài khoản Gmail / App Password");
        String gmail = cfg.getGmailUser();
        String localAddr = (localUser == null ? "unknown" : localUser) + "@" + cfg.getDomain();
        String fullName = localUser == null ? "" : DatabaseManager.getInstance().getFullName(localUser);
        if (fullName == null || fullName.isBlank()) fullName = localUser;

        // Viết lại header: Gmail chỉ cho gửi bằng địa chỉ đã xác thực
        String[] hb = MimeUtil.splitHeaderBody(raw);
        String headers = hb[0];
        for (String h : new String[]{"From", "Sender", "Reply-To", "Return-Path", "Bcc", "Received"})
            headers = MimeUtil.removeHeader(headers, h);
        String plus = gmail.substring(0, gmail.indexOf('@')) + "+" + localUser + gmail.substring(gmail.indexOf('@'));
        String newHeaders = "From: " + MimeUtil.formatAddress(fullName + " (" + localAddr + ")", gmail) + "\r\n"
                + "Reply-To: " + MimeUtil.formatAddress(fullName, plus) + "\r\n"
                + "X-LTM-Relay: " + localAddr + "\r\n"
                + headers;
        String out = newHeaders + "\r\n\r\n" + hb[1];

        try (SmtpClient smtp = new SmtpClient(cfg.getGmailSmtpHost(), cfg.getGmailSmtpPort(), true)) {
            smtp.setTrace(s -> manager.log("GMAIL", "smtp.gmail.com " + s));
            smtp.connect(15000);
            smtp.ehlo(cfg.getDomain().equals("localhost") ? "ltm-mail-server" : cfg.getDomain());
            smtp.authLogin(gmail, cfg.getGmailAppPassword());
            smtp.sendMail(gmail, recipients, out);
            smtp.quit();
        }
    }

    /** Kiểm tra đăng nhập Gmail SMTP + POP3 (dùng cho nút "Kiểm tra" trên giao diện). */
    public String testConnection() {
        MailConfig cfg = MailConfig.getInstance();
        StringBuilder sb = new StringBuilder();
        try (SmtpClient smtp = new SmtpClient(cfg.getGmailSmtpHost(), cfg.getGmailSmtpPort(), true)) {
            smtp.connect(15000);
            smtp.ehlo("ltm-mail-server");
            smtp.authLogin(cfg.getGmailUser(), cfg.getGmailAppPassword());
            smtp.quit();
            sb.append("SMTP Gmail: OK (đăng nhập thành công)\n");
        } catch (Exception e) {
            sb.append("SMTP Gmail: LỖI – ").append(e.getMessage()).append("\n");
        }
        try (Pop3Client pop = new Pop3Client(cfg.getGmailPop3Host(), cfg.getGmailPop3Port(), true)) {
            pop.connect(15000);
            pop.login(cfg.getGmailUser(), cfg.getGmailAppPassword());
            int[] st = pop.stat();
            pop.quit();
            sb.append("POP3 Gmail: OK (").append(st[0]).append(" thư chờ tải qua POP)\n");
        } catch (Exception e) {
            sb.append("POP3 Gmail: LỖI – ").append(e.getMessage())
                    .append("\n  (Hãy bật POP trong Gmail: Cài đặt > Chuyển tiếp và POP/IMAP)\n");
        }
        return sb.toString();
    }

    // =====================================================================
    //  NHẬN TỪ GMAIL (POP3 CLIENT -> pop.gmail.com)
    // =====================================================================

    public synchronized void startFetching() {
        stopFetching();
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "gmail-fetcher");
            t.setDaemon(true);
            return t;
        });
        int interval = MailConfig.getInstance().getGmailFetchIntervalSec();
        task = scheduler.scheduleWithFixedDelay(this::fetchSafe, 3, interval, TimeUnit.SECONDS);
        manager.log("GMAIL", "Tự động lấy thư từ Gmail mỗi " + interval + " giây (POP3 SSL "
                + MailConfig.getInstance().getGmailPop3Host() + ":" + MailConfig.getInstance().getGmailPop3Port() + ")");
    }

    public synchronized void stopFetching() {
        if (task != null) task.cancel(true);
        if (scheduler != null) scheduler.shutdownNow();
        task = null;
        scheduler = null;
    }

    private void fetchSafe() {
        try {
            int n = fetch();
            if (n > 0) manager.log("GMAIL", "Đã nhận " + n + " thư mới từ Gmail");
        } catch (Exception e) {
            manager.log("GMAIL", "Lấy thư Gmail lỗi: " + e.getMessage());
        }
    }

    /** Lấy thư mới từ Gmail, trả về số thư đã lưu vào DB. */
    public int fetch() throws Exception {
        MailConfig cfg = MailConfig.getInstance();
        if (!cfg.isGmailReady()) throw new IllegalStateException("Chưa cấu hình Gmail");
        DatabaseManager db = DatabaseManager.getInstance();
        synchronized (fetchLock) {
            int saved = 0;
            try (Pop3Client pop = new Pop3Client(cfg.getGmailPop3Host(), cfg.getGmailPop3Port(), true)) {
                pop.connect(20000);
                pop.login(cfg.getGmailUser(), cfg.getGmailAppPassword());
                Map<Integer, String> uids = pop.uidl();

                // Lần chạy đầu: chỉ lấy N thư mới nhất, đánh dấu phần còn lại là đã xử lý
                if (db.countGmailFetched() == 0 && uids.size() > cfg.getGmailFetchInitial()) {
                    List<Integer> nums = new ArrayList<>(uids.keySet());
                    int skip = nums.size() - Math.max(0, cfg.getGmailFetchInitial());
                    for (int i = 0; i < skip; i++) db.markGmailFetched(uids.get(nums.get(i)));
                    manager.log("GMAIL", "Lần đầu đồng bộ: bỏ qua " + skip + " thư cũ, chỉ lấy " + cfg.getGmailFetchInitial() + " thư mới nhất");
                }

                for (Map.Entry<Integer, String> e : uids.entrySet()) {
                    String uid = e.getValue();
                    if (db.isGmailFetched(uid)) continue;
                    String raw = pop.retr(e.getKey());
                    ParsedMail pm = MimeUtil.parse(raw);
                    if (!pm.rawHeader("X-LTM-Relay").isEmpty()) {
                        db.markGmailFetched(uid); // bản sao thư do chính server relay đi -> bỏ qua
                        continue;
                    }
                    String target = resolveLocalUser(pm, cfg);
                    String from = MimeUtil.extractEmail(pm.getFrom());
                    db.saveIncoming(target, "INBOX", from, cfg.getGmailUser(), raw, "GMAIL", "RECEIVED");
                    db.markGmailFetched(uid);
                    saved++;
                    manager.event("GMAIL", cfg.getGmailPop3Host(), target, "FETCH", "OK",
                            "Từ " + from + ": " + pm.getSubject());
                }
                pop.quit();
            }
            if (saved > 0) manager.fireDataChanged();
            return saved;
        }
    }

    /** Tìm user nội bộ từ địa chỉ dạng taikhoan+USER@gmail.com trong To/Cc/Delivered-To. */
    private String resolveLocalUser(ParsedMail pm, MailConfig cfg) {
        String gmailLocal = cfg.getGmailUser().substring(0, cfg.getGmailUser().indexOf('@')).toLowerCase();
        Pattern p = Pattern.compile(Pattern.quote(gmailLocal) + "\\+([A-Za-z0-9._-]+)@", Pattern.CASE_INSENSITIVE);
        List<String> fields = new ArrayList<>();
        for (String h : new String[]{"delivered-to", "to", "cc"}) {
            List<String> v = pm.getHeaders().get(h);
            if (v != null) fields.addAll(v);
        }
        DatabaseManager db = DatabaseManager.getInstance();
        for (String f : fields) {
            Matcher m = p.matcher(f);
            while (m.find()) {
                String u = m.group(1).toLowerCase();
                if (db.userExists(u)) return u;
            }
        }
        String def = cfg.getGmailDefaultUser();
        return db.userExists(def) ? def : "admin";
    }
}
