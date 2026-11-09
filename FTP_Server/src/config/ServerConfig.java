package config;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Properties;

/**
 * Lớp quản lý cấu hình FTP Server và kết nối Database.
 */
public class ServerConfig {
    private static final String CONFIG_FILE = "server.properties";

    private int serverPort = 21;
    private String dbHost = "localhost";
    private int dbPort = 3308;
    private String dbName = "ftp_db";
    private String dbUser = "root";
    private String dbPass = "root123";

    private String rootDirectory = "ftp_root";
    private int pasvPortMin = 20000;
    private int pasvPortMax = 20100;
    private int maxClients = 50;
    private int timeoutSeconds = 300;

    private static ServerConfig instance;

    public static synchronized ServerConfig getInstance() {
        if (instance == null) {
            instance = new ServerConfig();
            instance.load();
        }
        return instance;
    }

    public ServerConfig() {
        // Có thể ghi đè bởi biến môi trường nếu có
        String envPort = System.getenv("FTP_PORT");
        if (envPort != null && !envPort.isBlank()) {
            try { serverPort = Integer.parseInt(envPort.trim()); } catch (Exception ignored) {}
        }
        String envDbPort = System.getenv("DB_PORT");
        if (envDbPort != null && !envDbPort.isBlank()) {
            try { dbPort = Integer.parseInt(envDbPort.trim()); } catch (Exception ignored) {}
        }
    }

    public void load() {
        File file = new File(CONFIG_FILE);
        if (!file.exists()) {
            save();
            return;
        }
        Properties props = new Properties();
        try (FileInputStream in = new FileInputStream(file)) {
            props.load(in);
            serverPort = Integer.parseInt(props.getProperty("server.port", String.valueOf(serverPort)));
            dbHost = props.getProperty("db.host", dbHost);
            dbPort = Integer.parseInt(props.getProperty("db.port", String.valueOf(dbPort)));
            dbName = props.getProperty("db.name", dbName);
            dbUser = props.getProperty("db.user", dbUser);
            dbPass = props.getProperty("db.pass", dbPass);
            rootDirectory = props.getProperty("root.directory", rootDirectory);
            pasvPortMin = Integer.parseInt(props.getProperty("pasv.port.min", String.valueOf(pasvPortMin)));
            pasvPortMax = Integer.parseInt(props.getProperty("pasv.port.max", String.valueOf(pasvPortMax)));
            maxClients = Integer.parseInt(props.getProperty("max.clients", String.valueOf(maxClients)));
            timeoutSeconds = Integer.parseInt(props.getProperty("timeout.seconds", String.valueOf(timeoutSeconds)));
        } catch (Exception e) {
            System.err.println("Không thể đọc file config: " + e.getMessage());
        }
    }

    public void save() {
        Properties props = new Properties();
        props.setProperty("server.port", String.valueOf(serverPort));
        props.setProperty("db.host", dbHost);
        props.setProperty("db.port", String.valueOf(dbPort));
        props.setProperty("db.name", dbName);
        props.setProperty("db.user", dbUser);
        props.setProperty("db.pass", dbPass);
        props.setProperty("root.directory", rootDirectory);
        props.setProperty("pasv.port.min", String.valueOf(pasvPortMin));
        props.setProperty("pasv.port.max", String.valueOf(pasvPortMax));
        props.setProperty("max.clients", String.valueOf(maxClients));
        props.setProperty("timeout.seconds", String.valueOf(timeoutSeconds));

        try (FileOutputStream out = new FileOutputStream(CONFIG_FILE)) {
            props.store(out, "FTP Server Configuration");
        } catch (Exception e) {
            System.err.println("Không thể ghi file config: " + e.getMessage());
        }
    }

    public String getDbJdbcUrl() {
        return "jdbc:mysql://" + dbHost + ":" + dbPort + "/" + dbName +
                "?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=UTF-8&serverTimezone=Asia/Ho_Chi_Minh";
    }

    // Getters and Setters
    public int getServerPort() { return serverPort; }
    public void setServerPort(int serverPort) { this.serverPort = serverPort; }

    public String getDbHost() { return dbHost; }
    public void setDbHost(String dbHost) { this.dbHost = dbHost; }

    public int getDbPort() { return dbPort; }
    public void setDbPort(int dbPort) { this.dbPort = dbPort; }

    public String getDbName() { return dbName; }
    public void setDbName(String dbName) { this.dbName = dbName; }

    public String getDbUser() { return dbUser; }
    public void setDbUser(String dbUser) { this.dbUser = dbUser; }

    public String getDbPass() { return dbPass; }
    public void setDbPass(String dbPass) { this.dbPass = dbPass; }

    public String getRootDirectory() { return rootDirectory; }
    public void setRootDirectory(String rootDirectory) { this.rootDirectory = rootDirectory; }

    public int getPasvPortMin() { return pasvPortMin; }
    public void setPasvPortMin(int pasvPortMin) { this.pasvPortMin = pasvPortMin; }

    public int getPasvPortMax() { return pasvPortMax; }
    public void setPasvPortMax(int pasvPortMax) { this.pasvPortMax = pasvPortMax; }

    public int getMaxClients() { return maxClients; }
    public void setMaxClients(int maxClients) { this.maxClients = maxClients; }

    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
}
