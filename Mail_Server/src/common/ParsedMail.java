package common;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Kết quả phân tích (parse) một thư theo chuẩn RFC 5322 / MIME.
 */
public class ParsedMail {
    final Map<String, List<String>> headers = new LinkedHashMap<>();
    String textBody = "";
    String htmlBody = "";
    final List<Attachment> attachments = new ArrayList<>();

    /** Giá trị header thô (chưa giải mã), lấy giá trị đầu tiên. */
    public String rawHeader(String name) {
        List<String> v = headers.get(name.toLowerCase());
        return v == null || v.isEmpty() ? "" : v.get(0);
    }

    /** Giá trị header đã giải mã encoded-word (=?UTF-8?B?...?=). */
    public String header(String name) {
        return MimeUtil.decodeHeader(rawHeader(name));
    }

    public Map<String, List<String>> getHeaders() { return headers; }
    public String getFrom() { return header("From"); }
    public String getTo() { return header("To"); }
    public String getCc() { return header("Cc"); }
    public String getSubject() { return header("Subject"); }
    public String getDate() { return rawHeader("Date"); }
    public String getMessageId() { return rawHeader("Message-ID"); }
    public String getTextBody() { return textBody; }
    public String getHtmlBody() { return htmlBody; }
    public List<Attachment> getAttachments() { return attachments; }

    /** Nội dung dạng văn bản để hiển thị/lưu DB: ưu tiên text/plain, nếu không có thì chuyển HTML -> text. */
    public String getDisplayText() {
        if (!textBody.isBlank()) return textBody;
        if (!htmlBody.isBlank()) return MimeUtil.htmlToText(htmlBody);
        return "";
    }
}
