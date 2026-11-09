package database;

import java.sql.Timestamp;

/**
 * Một bản ghi thư trong bảng emails (mỗi người sở hữu 1 bản: hộp thư đến / đã gửi / thùng rác).
 */
public class MailRecord {
    public long id;
    public String owner;          // username chủ hộp thư
    public String folder;         // INBOX | SENT | TRASH
    public String messageId;
    public String sender;
    public String recipients;
    public String subject;
    public String bodyText;
    public String rawMessage;
    public int sizeBytes;
    public boolean hasAttachment;
    public boolean read;
    public String source;         // LOCAL | GMAIL
    public String status;         // DELIVERED | RELAYED | FAILED | RECEIVED
    public Timestamp createdAt;
}
