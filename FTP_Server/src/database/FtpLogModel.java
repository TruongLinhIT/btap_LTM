package database;

import java.sql.Timestamp;

/**
 * Model đại diện cho một bản ghi log hoạt động FTP trong MySQL.
 */
public class FtpLogModel {
    private long id;
    private String username;
    private String clientIp;
    private String command;
    private String path;
    private long fileSize;
    private String status;
    private String message;
    private Timestamp timestamp;

    public FtpLogModel() {}

    public FtpLogModel(String username, String clientIp, String command, String path, long fileSize, String status, String message) {
        this.username = username;
        this.clientIp = clientIp;
        this.command = command;
        this.path = path;
        this.fileSize = fileSize;
        this.status = status;
        this.message = message;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getClientIp() { return clientIp; }
    public void setClientIp(String clientIp) { this.clientIp = clientIp; }

    public String getCommand() { return command; }
    public void setCommand(String command) { this.command = command; }

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }

    public long getFileSize() { return fileSize; }
    public void setFileSize(long fileSize) { this.fileSize = fileSize; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public Timestamp getTimestamp() { return timestamp; }
    public void setTimestamp(Timestamp timestamp) { this.timestamp = timestamp; }
}
