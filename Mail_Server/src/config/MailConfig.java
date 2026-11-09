package config;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * Cấu hình Mail Server: cổng SMTP/POP3, kết nối MySQL (container ltm-mysql ở thư mục LTM)
 * và tài khoản Gmail dùng để chuyển tiếp (relay) thư ra Internet / lấy thư từ Gmail về.
 * Đọc/ghi file mail.properties ở thư mục chạy chương trình.
 */
public class MailConfig {
    private static final String CONFIG_FILE = "mail.properties";
    private static MailConfig instance;

    // ===== Server =====
    private String domain = "localhost";
    private int smtpPort = 2525;
    private int pop3Port = 1100;
    private int maxClients = 50;

    // ===== Database (docker-compose.yml ở D:\LTM: ltm-mysql, cổng 3308, root/root123) =====
    private String dbHost = "localhost";
    private int dbPort = 3308;
    private String dbName = "mail_db";
    private String dbUser = "root";
    private String dbPass = "root123";

    // ===== Gmail gateway =====
    private boolean gmailEnabled = false;
    private String gmailUser = "";          // vd: tenban@gmail.com
    private String gmailAppPassword = "";   // Mật khẩu ứng dụng 16 ký tự (App Password)
    private String gmailSmtpHost = "smtp.gmail.com";
    private int gmailSmtpPort = 465;        // SMTP over SSL
    private String gmailPop3Host = "pop.gmail.com";
    private int gmailPop3Port = 995;        // POP3 over SSL
    private boolean gmailFetchEnabled = true;
    private int gmailFetchIntervalSec = 60;
    private int gmailFetchInitial = 10;     // lần đầu chỉ lấy N thư mới nhất
    private String gmailDefaultUser = "admin"; // thư Gmail không xác định được người nhận -> giao cho user này

    public static synchronized MailConfig getInstance() {
        if (instance == null) {
            instance = new MailConfig();
            instance.load();
        }
        return instance;
    }

    public synchronized void load() {
        File f = new File(CONFIG_FILE);
        if (!f.exists()) {
            save();
            return;
        }
        Properties p = new Properties();
        try (InputStreamReader r = new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8)) {
            p.load(r);
        } catch (Exception e) {
            System.err.println("Không đọc được " + CONFIG_FILE + ": " + e.getMessage());
            return;
        }
        domain = p.getProperty("mail.domain", domain).trim();
        smtpPort = intOf(p, "smtp.port", smtpPort);
        pop3Port = intOf(p, "pop3.port", pop3Port);
        maxClients = intOf(p, "max.clients", maxClients);

        dbHost = p.getProperty("db.host", dbHost).trim();
        dbPort = intOf(p, "db.port", dbPort);
        dbName = p.getProperty("db.name", dbName).trim();
        dbUser = p.getProperty("db.user", dbUser).trim();
        dbPass = p.getProperty("db.pass", dbPass);

        gmailEnabled = Boolean.parseBoolean(p.getProperty("gmail.enabled", String.valueOf(gmailEnabled)).trim());
        gmailUser = p.getProperty("gmail.user", gmailUser).trim();
        gmailAppPassword = p.getProperty("gmail.app_password", gmailAppPassword).replace(" ", "");
        gmailSmtpHost = p.getProperty("gmail.smtp.host", gmailSmtpHost).trim();
        gmailSmtpPort = intOf(p, "gmail.smtp.port", gmailSmtpPort);
        gmailPop3Host = p.getProperty("gmail.pop3.host", gmailPop3Host).trim();
        gmailPop3Port = intOf(p, "gmail.pop3.port", gmailPop3Port);
        gmailFetchEnabled = Boolean.parseBoolean(p.getProperty("gmail.fetch.enabled", String.valueOf(gmailFetchEnabled)).trim());
        gmailFetchIntervalSec = intOf(p, "gmail.fetch.interval_seconds", gmailFetchIntervalSec);
        gmailFetchInitial = intOf(p, "gmail.fetch.initial_count", gmailFetchInitial);
        gmailDefaultUser = p.getProperty("gmail.fetch.default_user", gmailDefaultUser).trim();
    }

    public synchronized void save() {
        Properties p = new Properties();
        p.setProperty("mail.domain", domain);
        p.setProperty("smtp.port", String.valueOf(smtpPort));
        p.setProperty("pop3.port", String.valueOf(pop3Port));
        p.setProperty("max.clients", String.valueOf(maxClients));
        p.setProperty("db.host", dbHost);
        p.setProperty("db.port", String.valueOf(dbPort));
        p.setProperty("db.name", dbName);
        p.setProperty("db.user", dbUser);
        p.setProperty("db.pass", dbPass);
        p.setProperty("gmail.enabled", String.valueOf(gmailEnabled));
        p.setProperty("gmail.user", gmailUser);
        p.setProperty("gmail.app_password", gmailAppPassword);
        p.setProperty("gmail.smtp.host", gmailSmtpHost);
        p.setProperty("gmail.smtp.port", String.valueOf(gmailSmtpPort));
        p.setProperty("gmail.pop3.host", gmailPop3Host);
        p.setProperty("gmail.pop3.port", String.valueOf(gmailPop3Port));
        p.setProperty("gmail.fetch.enabled", String.valueOf(gmailFetchEnabled));
        p.setProperty("gmail.fetch.interval_seconds", String.valueOf(gmailFetchIntervalSec));
        p.setProperty("gmail.fetch.initial_count", String.valueOf(gmailFetchInitial));
        p.setProperty("gmail.fetch.default_user", gmailDefaultUser);
        try (OutputStreamWriter w = new OutputStreamWriter(new FileOutputStream(CONFIG_FILE), StandardCharsets.UTF_8)) {
            p.store(w, "LTM Mail Server Configuration (SMTP + POP3 + MySQL + Gmail gateway)");
        } catch (Exception e) {
            System.err.println("Không ghi được " + CONFIG_FILE + ": " + e.getMessage());
        }
    }

    private static int intOf(Properties p, String key, int def) {
        try {
            return Integer.parseInt(p.getProperty(key, String.valueOf(def)).trim());
        } catch (Exception e) {
            return def;
        }
    }

    public String getServerJdbcUrl() {
        return "jdbc:mysql://" + dbHost + ":" + dbPort + "/?useSSL=false&allowPublicKeyRetrieval=true"
                + "&serverTimezone=Asia/Ho_Chi_Minh&characterEncoding=UTF-8";
    }

    public String getDbJdbcUrl() {
        return "jdbc:mysql://" + dbHost + ":" + dbPort + "/" + dbName + "?useSSL=false&allowPublicKeyRetrieval=true"
                + "&serverTimezone=Asia/Ho_Chi_Minh&characterEncoding=UTF-8";
    }

    public boolean isGmailReady() {
        return gmailEnabled && !gmailUser.isBlank() && !gmailAppPassword.isBlank();
    }

    // ===== Getters / Setters =====
    public String getDomain() { return domain; }
    public void setDomain(String v) { domain = v; }
    public int getSmtpPort() { return smtpPort; }
    public void setSmtpPort(int v) { smtpPort = v; }
    public int getPop3Port() { return pop3Port; }
    public void setPop3Port(int v) { pop3Port = v; }
    public int getMaxClients() { return maxClients; }
    public String getDbHost() { return dbHost; }
    public void setDbHost(String v) { dbHost = v; }
    public int getDbPort() { return dbPort; }
    public void setDbPort(int v) { dbPort = v; }
    public String getDbName() { return dbName; }
    public void setDbName(String v) { dbName = v; }
    public String getDbUser() { return dbUser; }
    public void setDbUser(String v) { dbUser = v; }
    public String getDbPass() { return dbPass; }
    public void setDbPass(String v) { dbPass = v; }
    public boolean isGmailEnabled() { return gmailEnabled; }
    public void setGmailEnabled(boolean v) { gmailEnabled = v; }
    public String getGmailUser() { return gmailUser; }
    public void setGmailUser(String v) { gmailUser = v; }
    public String getGmailAppPassword() { return gmailAppPassword; }
    public void setGmailAppPassword(String v) { gmailAppPassword = v == null ? "" : v.replace(" ", ""); }
    public String getGmailSmtpHost() { return gmailSmtpHost; }
    public void setGmailSmtpHost(String v) { gmailSmtpHost = v; }
    public int getGmailSmtpPort() { return gmailSmtpPort; }
    public void setGmailSmtpPort(int v) { gmailSmtpPort = v; }
    public String getGmailPop3Host() { return gmailPop3Host; }
    public void setGmailPop3Host(String v) { gmailPop3Host = v; }
    public int getGmailPop3Port() { return gmailPop3Port; }
    public void setGmailPop3Port(int v) { gmailPop3Port = v; }
    public boolean isGmailFetchEnabled() { return gmailFetchEnabled; }
    public void setGmailFetchEnabled(boolean v) { gmailFetchEnabled = v; }
    public int getGmailFetchIntervalSec() { return Math.max(15, gmailFetchIntervalSec); }
    public void setGmailFetchIntervalSec(int v) { gmailFetchIntervalSec = v; }
    public int getGmailFetchInitial() { return gmailFetchInitial; }
    public String getGmailDefaultUser() { return gmailDefaultUser; }
    public void setGmailDefaultUser(String v) { gmailDefaultUser = v; }
}
