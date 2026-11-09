package client;

import common.MimeUtil;
import common.ParsedMail;

import java.time.ZonedDateTime;

/**
 * Thông tin tóm tắt một thư để hiển thị trong danh sách (lấy bằng lệnh POP3 TOP n 0).
 */
public class MailSummary {
    public final String uid;
    public final String from;
    public final String to;
    public final String subject;
    public final String rawDate;
    public final ZonedDateTime date;
    public boolean read;
    public final boolean maybeAttachment;
    public final int size;

    public MailSummary(String uid, String headerText, int size) {
        this.uid = uid;
        ParsedMail pm = MimeUtil.parse(headerText);
        this.from = pm.getFrom();
        this.to = pm.getTo();
        String s = pm.getSubject();
        this.subject = s == null || s.isBlank() ? "(không có tiêu đề)" : s;
        this.rawDate = pm.getDate();
        this.date = MimeUtil.parseDate(rawDate);
        this.read = !"0".equals(pm.rawHeader("X-LTM-Read"));
        this.maybeAttachment = pm.rawHeader("Content-Type").toLowerCase().startsWith("multipart/mixed");
        this.size = size;
    }
}
