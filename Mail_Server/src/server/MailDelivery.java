package server;

import common.MimeUtil;
import config.MailConfig;
import database.DatabaseManager;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Giao thư sau khi SMTP server nhận xong lệnh DATA:
 * 1. Người nhận nội bộ (@localhost)  -> lưu vào INBOX của từng người trong MySQL.
 * 2. Người nhận bên ngoài (@gmail.com...) -> relay qua Gmail SMTP (smtp.gmail.com:465, SSL).
 * 3. Lưu một bản vào thư mục SENT của người gửi (nếu người gửi là user nội bộ đã AUTH).
 * 4. Nếu relay lỗi -> gửi thư báo lỗi (bounce) từ MAILER-DAEMON vào hộp thư người gửi.
 */
public final class MailDelivery {
    private MailDelivery() {}

    public static class Result {
        public final List<String> delivered = new ArrayList<>();
        public final List<String> relayed = new ArrayList<>();
        public final Map<String, String> failed = new LinkedHashMap<>();
    }

    public static Result deliver(MailServerManager manager, String authUser, String envelopeFrom,
                                 List<String> rcpts, String raw, String ip) {
        DatabaseManager db = DatabaseManager.getInstance();
        MailConfig cfg = MailConfig.getInstance();
        Result r = new Result();
        String allRcpts = String.join(", ", rcpts);
        String sender = envelopeFrom == null || envelopeFrom.isEmpty()
                ? MimeUtil.extractEmail(MimeUtil.parse(raw).getFrom()) : envelopeFrom;

        List<String> external = new ArrayList<>();
        for (String rc : rcpts) {
            if (manager.isLocalAddress(rc)) {
                String user = MailServerManager.localPart(rc);
                try {
                    db.saveIncoming(user, "INBOX", sender, allRcpts, raw, "LOCAL", "DELIVERED");
                    r.delivered.add(rc);
                    manager.event("SMTP", ip, authUser, "DELIVER " + rc, "OK", "Lưu vào INBOX của " + user);
                } catch (Exception e) {
                    r.failed.put(rc, "Lỗi lưu DB: " + e.getMessage());
                    manager.event("SMTP", ip, authUser, "DELIVER " + rc, "FAIL", e.getMessage());
                }
            } else {
                external.add(rc);
            }
        }

        String relayError = null;
        if (!external.isEmpty()) {
            try {
                manager.getGmailGateway().relay(authUser, external, raw);
                r.relayed.addAll(external);
                manager.event("GMAIL", ip, authUser, "RELAY " + String.join(",", external), "OK",
                        "Đã gửi qua " + cfg.getGmailSmtpHost());
            } catch (Exception e) {
                relayError = e.getMessage();
                for (String x : external) r.failed.put(x, "Relay Gmail lỗi: " + relayError);
                manager.event("GMAIL", ip, authUser, "RELAY " + String.join(",", external), "FAIL", relayError);
            }
        }

        if (authUser != null) {
            String status = external.isEmpty() ? "DELIVERED" : (relayError == null ? "RELAYED" : "FAILED");
            try {
                db.saveIncoming(authUser, "SENT", sender, allRcpts, raw, "LOCAL", status);
            } catch (Exception e) {
                manager.log("SMTP", "Không lưu được bản SENT: " + e.getMessage());
            }
            if (relayError != null) sendBounce(authUser, external, relayError, cfg.getDomain());
        }
        manager.fireDataChanged();
        return r;
    }

    private static void sendBounce(String user, List<String> failed, String error, String domain) {
        try {
            String raw = MimeUtil.buildMessage("Mail Delivery System", "mailer-daemon@" + domain,
                    List.of(user + "@" + domain), null, "Không gửi được thư (Delivery Status Notification)",
                    "Thư của bạn KHÔNG gửi được tới:\n  " + String.join("\n  ", failed)
                            + "\n\nLý do: " + error
                            + "\n\nHãy kiểm tra cấu hình Gmail (tài khoản + App Password) trên Mail Server.",
                    null, null, domain);
            DatabaseManager.getInstance().saveIncoming(user, "INBOX", "mailer-daemon@" + domain,
                    user + "@" + domain, raw, "LOCAL", "DELIVERED");
        } catch (Exception ignored) {
        }
    }
}
