package server;

import config.MailConfig;
import database.DatabaseManager;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Điều phối toàn bộ Mail Server: SMTP server, POP3 server, Gmail gateway và hệ thống log.
 */
public class MailServerManager {
    private static MailServerManager instance;
    private final List<ServerListener> listeners = new CopyOnWriteArrayList<>();
    private final MailConfig config = MailConfig.getInstance();
    private SmtpServer smtpServer;
    private Pop3Server pop3Server;
    private final GmailGateway gmailGateway = new GmailGateway(this);
    private static final DateTimeFormatter TF = DateTimeFormatter.ofPattern("HH:mm:ss");

    public static synchronized MailServerManager getInstance() {
        if (instance == null) instance = new MailServerManager();
        return instance;
    }

    public void addListener(ServerListener l) { listeners.add(l); }

    public GmailGateway getGmailGateway() { return gmailGateway; }

    public synchronized void start() throws Exception {
        if (isRunning()) return;
        DatabaseManager.getInstance().init();
        log("SYSTEM", "Đã kết nối MySQL " + config.getDbHost() + ":" + config.getDbPort() + "/" + config.getDbName());
        smtpServer = new SmtpServer(config.getSmtpPort(), this);
        pop3Server = new Pop3Server(config.getPop3Port(), this);
        try {
            smtpServer.start();
            pop3Server.start();
        } catch (Exception e) {
            stop();
            throw e;
        }
        log("SYSTEM", "SMTP server đang lắng nghe cổng " + config.getSmtpPort()
                + " | POP3 server đang lắng nghe cổng " + config.getPop3Port() + " | domain: " + config.getDomain());
        if (config.isGmailReady()) {
            log("GMAIL", "Gmail gateway BẬT: relay qua " + config.getGmailSmtpHost() + ":" + config.getGmailSmtpPort()
                    + " bằng tài khoản " + config.getGmailUser());
            if (config.isGmailFetchEnabled()) gmailGateway.startFetching();
        } else {
            log("GMAIL", "Gmail gateway TẮT (chưa cấu hình tài khoản Gmail + App Password) – chỉ gửi được thư nội bộ @" + config.getDomain());
        }
        DatabaseManager.getInstance().logAsync("SYSTEM", "-", "-", "START", "OK", "Mail Server started");
        fireStatus();
    }

    public synchronized void stop() {
        if (smtpServer != null) smtpServer.stop();
        if (pop3Server != null) pop3Server.stop();
        gmailGateway.stopFetching();
        smtpServer = null;
        pop3Server = null;
        log("SYSTEM", "Mail Server đã dừng");
        fireStatus();
    }

    public boolean isRunning() {
        return smtpServer != null && smtpServer.isRunning();
    }

    public int getActiveSessions() {
        return (smtpServer == null ? 0 : smtpServer.getActive()) + (pop3Server == null ? 0 : pop3Server.getActive());
    }

    /** Log hiển thị lên GUI (không ghi DB). */
    public void log(String protocol, String message) {
        String line = "[" + LocalTime.now().format(TF) + "] [" + protocol + "] " + message;
        System.out.println(line);
        for (ServerListener l : listeners) l.onLog(protocol, line);
    }

    /** Sự kiện quan trọng: hiển thị GUI + ghi bảng mail_logs. */
    public void event(String protocol, String ip, String user, String command, String status, String message) {
        log(protocol, (user == null || user.isEmpty() ? "" : user + "@") + ip + " " + command + " -> " + status
                + (message == null || message.isEmpty() ? "" : " (" + message + ")"));
        DatabaseManager.getInstance().logAsync(protocol, ip, user, command, status, message);
    }

    public void fireDataChanged() {
        for (ServerListener l : listeners) l.onDataChanged();
    }

    private void fireStatus() {
        for (ServerListener l : listeners) l.onStatusChanged();
    }

    // ---- tiện ích địa chỉ ----

    /** Địa chỉ có thuộc domain của server này không (vd: alice@localhost). */
    public boolean isLocalAddress(String email) {
        int at = email.lastIndexOf('@');
        if (at < 0) return true;
        String d = email.substring(at + 1).toLowerCase();
        return d.equals(config.getDomain().toLowerCase()) || d.equals("localhost") || d.equals("127.0.0.1");
    }

    public static String localPart(String email) {
        int at = email.lastIndexOf('@');
        return (at < 0 ? email : email.substring(0, at)).toLowerCase();
    }
}
