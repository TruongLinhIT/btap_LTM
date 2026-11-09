package client;

import common.Attachment;
import common.MimeUtil;
import common.ParsedMail;
import protocol.Pop3Client;
import protocol.SmtpClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Tầng nghiệp vụ của Mail Client: đọc thư bằng POP3, gửi thư bằng SMTP (đều tự cài đặt bằng Socket).
 * Mỗi thao tác mở một phiên kết nối mới tới Mail Server rồi đóng lại (giống các mail client POP3 thông thường).
 */
public class MailClientService {
    private final String host;
    private final int smtpPort;
    private final int pop3Port;
    private final String username;
    private final String password;
    private final String domain;
    private String fullName = "";
    private Consumer<String> trace = s -> {};
    private final Map<String, String> fullCache = new ConcurrentHashMap<>();

    public MailClientService(String host, int smtpPort, int pop3Port, String email, String password) {
        this.host = host;
        this.smtpPort = smtpPort;
        this.pop3Port = pop3Port;
        int at = email.indexOf('@');
        this.username = (at < 0 ? email : email.substring(0, at)).trim().toLowerCase();
        this.domain = at < 0 ? "localhost" : email.substring(at + 1).trim().toLowerCase();
        this.password = password;
    }

    public void setTrace(Consumer<String> trace) { this.trace = trace == null ? s -> {} : trace; }
    public void setFullName(String n) { fullName = n == null ? "" : n; }
    public String getFullName() { return fullName; }
    public String getEmail() { return username + "@" + domain; }
    public String getUsername() { return username; }
    public String getDomain() { return domain; }
    public String getHost() { return host; }

    private Pop3Client openPop(String folder) throws IOException {
        Pop3Client pop = new Pop3Client(host, pop3Port, false);
        pop.setTrace(s -> trace.accept("[POP3] " + s));
        try {
            pop.connect(5000);
            pop.login(username, password);
            if (folder != null && !folder.equals("INBOX")) pop.command("XFOLDER " + folder);
        } catch (IOException e) {
            pop.close();
            throw e;
        }
        return pop;
    }

    /** Kiểm tra đăng nhập (POP3 USER/PASS). */
    public int login() throws IOException {
        try (Pop3Client pop = openPop("INBOX")) {
            int[] st = pop.stat();
            pop.quit();
            return st[0];
        }
    }

    /** Lấy danh sách thư của thư mục (UIDL + TOP n 0), mới nhất ở đầu. */
    public List<MailSummary> listFolder(String folder) throws IOException {
        List<MailSummary> list = new ArrayList<>();
        try (Pop3Client pop = openPop(folder)) {
            Map<Integer, String> uids = pop.uidl();
            Map<Integer, Integer> sizes = pop.list();
            for (Map.Entry<Integer, String> e : uids.entrySet()) {
                String header = pop.top(e.getKey(), 0);
                list.add(0, new MailSummary(e.getValue(), header, sizes.getOrDefault(e.getKey(), 0)));
            }
            pop.quit();
        }
        return list;
    }

    /** Tải toàn bộ thư (RETR) – server sẽ đánh dấu đã đọc. */
    public ParsedMail getMessage(String folder, String uid) throws IOException {
        String raw = fullCache.get(uid);
        if (raw == null) {
            try (Pop3Client pop = openPop(folder)) {
                Integer num = findNum(pop, uid);
                if (num == null) throw new IOException("Thư không còn trên server");
                raw = pop.retr(num);
                pop.quit();
            }
            fullCache.put(uid, raw);
        }
        return MimeUtil.parse(raw);
    }

    /** Xóa thư (DELE + QUIT): INBOX/SENT -> Thùng rác, Thùng rác -> xóa vĩnh viễn. */
    public void delete(String folder, List<String> uidsToDelete) throws IOException {
        try (Pop3Client pop = openPop(folder)) {
            Map<Integer, String> uids = pop.uidl();
            for (Map.Entry<Integer, String> e : uids.entrySet())
                if (uidsToDelete.contains(e.getValue())) pop.dele(e.getKey());
            pop.quit(); // QUIT -> server thực hiện xóa (UPDATE state)
        }
        uidsToDelete.forEach(fullCache::remove);
    }

    private Integer findNum(Pop3Client pop, String uid) throws IOException {
        for (Map.Entry<Integer, String> e : pop.uidl().entrySet()) if (e.getValue().equals(uid)) return e.getKey();
        return null;
    }

    /** Soạn và gửi thư qua SMTP (EHLO, AUTH LOGIN, MAIL FROM, RCPT TO, DATA, QUIT). */
    public String send(List<String> to, List<String> cc, List<String> bcc, String subject, String body,
                       List<Attachment> attachments, String inReplyTo) throws IOException {
        String raw = MimeUtil.buildMessage(fullName, getEmail(), to, cc, subject, body, attachments, inReplyTo, domain);
        Set<String> rcpts = new LinkedHashSet<>();
        for (List<String> l : List.of(to, cc, bcc)) for (String a : l) rcpts.add(MimeUtil.extractEmail(a));
        try (SmtpClient smtp = new SmtpClient(host, smtpPort, false)) {
            smtp.setTrace(s -> trace.accept("[SMTP] " + s));
            smtp.connect(5000);
            smtp.ehlo("ltm-mail-client");
            smtp.authLogin(username, password);
            String resp = smtp.sendMail(getEmail(), new ArrayList<>(rcpts), raw);
            smtp.quit();
            return resp;
        }
    }
}
