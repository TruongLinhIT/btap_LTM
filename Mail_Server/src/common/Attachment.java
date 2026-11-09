package common;

/**
 * Một tệp đính kèm trong thư (tên, kiểu MIME, dữ liệu nhị phân).
 */
public class Attachment {
    private final String fileName;
    private final String contentType;
    private final byte[] data;

    public Attachment(String fileName, String contentType, byte[] data) {
        this.fileName = fileName == null || fileName.isBlank() ? "attachment.bin" : fileName;
        this.contentType = contentType == null || contentType.isBlank() ? "application/octet-stream" : contentType;
        this.data = data == null ? new byte[0] : data;
    }

    public String getFileName() { return fileName; }
    public String getContentType() { return contentType; }
    public byte[] getData() { return data; }

    public String getSizeText() {
        long n = data.length;
        if (n < 1024) return n + " B";
        if (n < 1024 * 1024) return String.format("%.1f KB", n / 1024.0);
        return String.format("%.1f MB", n / (1024.0 * 1024));
    }

    @Override
    public String toString() {
        return fileName + " (" + getSizeText() + ")";
    }
}
