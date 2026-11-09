package database;

import java.sql.Timestamp;

/**
 * Model đại diện cho một người dùng FTP trong MySQL.
 */
public class UserModel {
    private int id;
    private String username;
    private String passwordHash;
    private String salt;
    private String homeDirectory;
    private boolean canUpload;
    private boolean canDownload;
    private boolean canDelete;
    private boolean canModify;
    private boolean isActive;
    private Timestamp createdAt;

    public UserModel() {
        this.canUpload = true;
        this.canDownload = true;
        this.canDelete = true;
        this.canModify = true;
        this.isActive = true;
    }

    public UserModel(String username, String homeDirectory) {
        this();
        this.username = username;
        this.homeDirectory = homeDirectory;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getSalt() { return salt; }
    public void setSalt(String salt) { this.salt = salt; }

    public String getHomeDirectory() { return homeDirectory; }
    public void setHomeDirectory(String homeDirectory) { this.homeDirectory = homeDirectory; }

    public boolean isCanUpload() { return canUpload; }
    public void setCanUpload(boolean canUpload) { this.canUpload = canUpload; }

    public boolean isCanDownload() { return canDownload; }
    public void setCanDownload(boolean canDownload) { this.canDownload = canDownload; }

    public boolean isCanDelete() { return canDelete; }
    public void setCanDelete(boolean canDelete) { this.canDelete = canDelete; }

    public boolean isCanModify() { return canModify; }
    public void setCanModify(boolean canModify) { this.canModify = canModify; }

    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    @Override
    public String toString() {
        return username + " (" + homeDirectory + ")";
    }
}
