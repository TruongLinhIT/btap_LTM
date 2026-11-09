package client;

/**
 * Đại diện cho một file hoặc thư mục trên FTP Server.
 */
public class FtpFileItem {
    private final String name;
    private final long size;
    private final boolean isDirectory;
    private final String dateStr;
    private final String permissions;

    public FtpFileItem(String name, long size, boolean isDirectory, String dateStr, String permissions) {
        this.name = name;
        this.size = size;
        this.isDirectory = isDirectory;
        this.dateStr = dateStr;
        this.permissions = permissions;
    }

    public String getName() { return name; }
    public long getSize() { return size; }
    public boolean isDirectory() { return isDirectory; }
    public String getDateStr() { return dateStr; }
    public String getPermissions() { return permissions; }

    @Override
    public String toString() {
        return (isDirectory ? "[DIR] " : "[FILE] ") + name + " (" + size + " bytes)";
    }
}
