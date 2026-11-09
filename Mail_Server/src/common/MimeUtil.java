package common;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tiện ích MIME tự viết (không dùng JavaMail):
 * - Tạo thư (RFC 5322 + MIME multipart, header UTF-8 dạng encoded-word, nội dung base64)
 * - Phân tích thư (multipart lồng nhau, base64, quoted-printable, charset, tệp đính kèm)
 *
 * Quy ước: thư "thô" (raw) được biểu diễn bằng String mà mỗi ký tự = 1 byte (ISO-8859-1),
 * nhờ vậy dữ liệu 8-bit đi qua socket / database không bị biến đổi.
 */
public final class MimeUtil {
    private MimeUtil() {}

    public static final String CRLF = "\r\n";
    private static final Pattern ENCODED_WORD = Pattern.compile("=\\?([^?]+)\\?([BbQq])\\?([^?]*)\\?=");
    private static final Pattern BETWEEN_ENCODED = Pattern.compile("(\\?=)\\s+(=\\?)");

    // ======================================================================
    //  TẠO THƯ
    // ======================================================================

    /**
     * Tạo nội dung thư hoàn chỉnh (header + body) sẵn sàng gửi qua lệnh DATA của SMTP.
     */
    public static String buildMessage(String fromName, String fromEmail, List<String> to, List<String> cc,
                                      String subject, String body, List<Attachment> attachments,
                                      String inReplyTo, String domain) {
        StringBuilder sb = new StringBuilder();
        sb.append("From: ").append(formatAddress(fromName, fromEmail)).append(CRLF);
        sb.append("To: ").append(String.join(", ", to)).append(CRLF);
        if (cc != null && !cc.isEmpty()) sb.append("Cc: ").append(String.join(", ", cc)).append(CRLF);
        sb.append("Subject: ").append(encodeHeader(subject == null ? "" : subject)).append(CRLF);
        sb.append("Date: ").append(formatDate(ZonedDateTime.now())).append(CRLF);
        sb.append("Message-ID: <").append(UUID.randomUUID()).append("@").append(domain == null ? "localhost" : domain).append(">").append(CRLF);
        if (inReplyTo != null && !inReplyTo.isBlank()) {
            sb.append("In-Reply-To: ").append(inReplyTo).append(CRLF);
            sb.append("References: ").append(inReplyTo).append(CRLF);
        }
        sb.append("MIME-Version: 1.0").append(CRLF);
        sb.append("X-Mailer: LTM Mail Client (Java Socket)").append(CRLF);

        String textPart = "Content-Type: text/plain; charset=UTF-8" + CRLF
                + "Content-Transfer-Encoding: base64" + CRLF + CRLF
                + base64Lines((body == null ? "" : body).getBytes(StandardCharsets.UTF_8));

        if (attachments == null || attachments.isEmpty()) {
            sb.append(textPart);
        } else {
            String boundary = "----=_LTM_" + UUID.randomUUID().toString().replace("-", "");
            sb.append("Content-Type: multipart/mixed; boundary=\"").append(boundary).append("\"").append(CRLF).append(CRLF);
            sb.append("This is a multi-part message in MIME format.").append(CRLF);
            sb.append("--").append(boundary).append(CRLF).append(textPart);
            for (Attachment a : attachments) {
                String name = encodeHeader(a.getFileName());
                sb.append("--").append(boundary).append(CRLF);
                sb.append("Content-Type: ").append(a.getContentType()).append("; name=\"").append(name).append("\"").append(CRLF);
                sb.append("Content-Transfer-Encoding: base64").append(CRLF);
                sb.append("Content-Disposition: attachment; filename=\"").append(name).append("\"").append(CRLF).append(CRLF);
                sb.append(base64Lines(a.getData()));
            }
            sb.append("--").append(boundary).append("--").append(CRLF);
        }
        return sb.toString();
    }

    private static String base64Lines(byte[] data) {
        String s = Base64.getMimeEncoder(76, CRLF.getBytes(StandardCharsets.US_ASCII)).encodeToString(data);
        return s.isEmpty() ? CRLF : s + CRLF;
    }

    public static String formatAddress(String name, String email) {
        if (name == null || name.isBlank()) return "<" + email + ">";
        String n = isAscii(name) ? "\"" + name.replace("\"", "'") + "\"" : encodeHeader(name);
        return n + " <" + email + ">";
    }

    /** Mã hóa header có ký tự Unicode thành encoded-word =?UTF-8?B?...?= (RFC 2047). */
    public static String encodeHeader(String s) {
        if (s == null) return "";
        if (isAscii(s)) return s;
        StringBuilder out = new StringBuilder();
        StringBuilder chunk = new StringBuilder();
        int bytes = 0;
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            String c = new String(Character.toChars(cp));
            int len = c.getBytes(StandardCharsets.UTF_8).length;
            if (bytes + len > 45) {
                appendWord(out, chunk.toString());
                chunk.setLength(0);
                bytes = 0;
            }
            chunk.append(c);
            bytes += len;
            i += Character.charCount(cp);
        }
        if (!chunk.isEmpty()) appendWord(out, chunk.toString());
        return out.toString();
    }

    private static void appendWord(StringBuilder out, String text) {
        if (!out.isEmpty()) out.append(CRLF).append(" ");
        out.append("=?UTF-8?B?").append(Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8))).append("?=");
    }

    public static boolean isAscii(String s) {
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) > 126) return false;
        return true;
    }

    public static String formatDate(ZonedDateTime t) {
        return DateTimeFormatter.RFC_1123_DATE_TIME.withLocale(Locale.ENGLISH).format(t);
    }

    /** Đọc header Date của thư; trả về null nếu không đọc được. */
    public static ZonedDateTime parseDate(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String s = raw.replaceAll("\\(.*?\\)", "").replaceAll("\\s+", " ").trim();
        String[] patterns = {"EEE, d MMM yyyy HH:mm:ss Z", "d MMM yyyy HH:mm:ss Z", "EEE, d MMM yyyy HH:mm Z",
                "EEE, d MMM yyyy HH:mm:ss z"};
        for (String p : patterns) {
            try {
                return ZonedDateTime.parse(s, DateTimeFormatter.ofPattern(p, Locale.ENGLISH))
                        .withZoneSameInstant(ZoneId.systemDefault());
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    // ======================================================================
    //  PHÂN TÍCH THƯ
    // ======================================================================

    /** Tách thư thành [khối header, phần thân]. */
    public static String[] splitHeaderBody(String raw) {
        int idx = raw.indexOf("\r\n\r\n");
        int sepLen = 4;
        int idx2 = raw.indexOf("\n\n");
        if (idx < 0 || (idx2 >= 0 && idx2 < idx)) {
            idx = idx2;
            sepLen = 2;
        }
        if (idx < 0) return new String[]{raw, ""};
        return new String[]{raw.substring(0, idx), raw.substring(idx + sepLen)};
    }

    /** Phân tích khối header (có xử lý dòng gập - folding). Key được chuyển về chữ thường. */
    public static Map<String, List<String>> parseHeaders(String block) {
        Map<String, List<String>> map = new LinkedHashMap<>();
        String current = null;
        StringBuilder value = new StringBuilder();
        for (String line : block.split("\r?\n")) {
            if (!line.isEmpty() && (line.charAt(0) == ' ' || line.charAt(0) == '\t') && current != null) {
                value.append(' ').append(line.trim());
                continue;
            }
            if (current != null) map.computeIfAbsent(current, k -> new ArrayList<>()).add(value.toString().trim());
            int colon = line.indexOf(':');
            if (colon > 0) {
                current = line.substring(0, colon).trim().toLowerCase();
                value.setLength(0);
                value.append(line.substring(colon + 1));
            } else {
                current = null;
            }
        }
        if (current != null) map.computeIfAbsent(current, k -> new ArrayList<>()).add(value.toString().trim());
        return map;
    }

    public static ParsedMail parse(String raw) {
        ParsedMail pm = new ParsedMail();
        if (raw == null) return pm;
        String[] hb = splitHeaderBody(raw);
        parsePart(hb[0], hb[1], pm, true, 0);
        return pm;
    }

    private static void parsePart(String headerBlock, String body, ParsedMail pm, boolean top, int depth) {
        Map<String, List<String>> h = parseHeaders(headerBlock);
        if (top) pm.headers.putAll(h);
        String ct = first(h, "content-type");
        if (ct.isEmpty()) ct = "text/plain; charset=us-ascii";
        String mime = ct.split(";")[0].trim().toLowerCase();
        String cte = first(h, "content-transfer-encoding").trim().toLowerCase();
        String disposition = first(h, "content-disposition");
        String fileName = param(disposition, "filename");
        if (fileName.isEmpty()) fileName = param(ct, "name");
        fileName = decodeHeader(fileName);

        if (mime.startsWith("multipart/") && depth < 20) {
            String boundary = param(ct, "boundary");
            if (boundary.isEmpty()) {
                pm.textBody += body;
                return;
            }
            for (String part : splitMultipart(body, boundary)) {
                String[] hb = splitHeaderBody(part.startsWith("\r\n") || part.startsWith("\n") ? "X-Empty: 1\r\n" + part : part);
                parsePart(hb[0], hb[1], pm, false, depth + 1);
            }
            return;
        }

        byte[] data = decodeTransfer(body, cte);
        boolean isAttachment = disposition.toLowerCase().startsWith("attachment") || !fileName.isEmpty();
        if (!isAttachment && mime.equals("text/plain")) {
            pm.textBody += decodeCharset(data, param(ct, "charset"));
        } else if (!isAttachment && mime.equals("text/html")) {
            pm.htmlBody += decodeCharset(data, param(ct, "charset"));
        } else if (mime.equals("message/rfc822") && fileName.isEmpty()) {
            pm.attachments.add(new Attachment("forwarded-message.eml", mime, data));
        } else {
            if (fileName.isEmpty()) fileName = "attachment" + (pm.attachments.size() + 1);
            pm.attachments.add(new Attachment(fileName, mime, data));
        }
    }

    private static List<String> splitMultipart(String body, String boundary) {
        List<String> parts = new ArrayList<>();
        String delim = "--" + boundary;
        String[] lines = body.split("\r?\n", -1);
        StringBuilder cur = null;
        boolean firstLine = true;
        for (String line : lines) {
            String t = line.stripTrailing();
            if (t.equals(delim + "--")) {
                if (cur != null) parts.add(cur.toString());
                cur = null;
                break;
            }
            if (t.equals(delim)) {
                if (cur != null) parts.add(cur.toString());
                cur = new StringBuilder();
                firstLine = true;
                continue;
            }
            if (cur != null) {
                if (!firstLine) cur.append(CRLF);
                cur.append(line);
                firstLine = false;
            }
        }
        if (cur != null && !cur.isEmpty()) parts.add(cur.toString());
        return parts;
    }

    public static byte[] decodeTransfer(String body, String cte) {
        try {
            if (cte.contains("base64")) {
                return Base64.getMimeDecoder().decode(body.replaceAll("[^A-Za-z0-9+/=]", ""));
            }
            if (cte.contains("quoted-printable")) {
                return decodeQuotedPrintable(body, false);
            }
        } catch (Exception ignored) {
        }
        return body.getBytes(StandardCharsets.ISO_8859_1);
    }

    private static byte[] decodeQuotedPrintable(String s, boolean underscoreIsSpace) {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '=' ) {
                if (i + 1 < s.length() && (s.charAt(i + 1) == '\r' || s.charAt(i + 1) == '\n')) {
                    i++;
                    if (s.charAt(i) == '\r' && i + 1 < s.length() && s.charAt(i + 1) == '\n') i++;
                    continue; // soft line break
                }
                if (i + 2 < s.length()) {
                    try {
                        out.write(Integer.parseInt(s.substring(i + 1, i + 3), 16));
                        i += 2;
                        continue;
                    } catch (NumberFormatException ignored) {
                    }
                }
                out.write('=');
            } else if (c == '_' && underscoreIsSpace) {
                out.write(' ');
            } else {
                out.write(c & 0xFF);
            }
        }
        return out.toByteArray();
    }

    public static String decodeCharset(byte[] data, String charset) {
        Charset cs = StandardCharsets.UTF_8;
        try {
            if (charset != null && !charset.isBlank() && !charset.equalsIgnoreCase("us-ascii")) cs = Charset.forName(charset.trim());
        } catch (Exception ignored) {
        }
        return new String(data, cs);
    }

    /** Giải mã header: encoded-word (RFC 2047) và header UTF-8 8-bit. */
    public static String decodeHeader(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        String s = latin1ToUtf8IfValid(raw);
        s = BETWEEN_ENCODED.matcher(s).replaceAll("$1$2");
        Matcher m = ENCODED_WORD.matcher(s);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String charset = m.group(1);
            int star = charset.indexOf('*');
            if (star > 0) charset = charset.substring(0, star);
            String enc = m.group(2).toUpperCase();
            String text = m.group(3);
            String decoded;
            try {
                byte[] bytes = enc.equals("B") ? Base64.getMimeDecoder().decode(text)
                        : decodeQuotedPrintable(text, true);
                decoded = decodeCharset(bytes, charset);
            } catch (Exception e) {
                decoded = m.group(0);
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(decoded));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String latin1ToUtf8IfValid(String s) {
        boolean high = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c > 0xFF) return s; // đã là Unicode thật
            if (c > 0x7F) high = true;
        }
        if (!high) return s;
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(s.getBytes(StandardCharsets.ISO_8859_1))).toString();
        } catch (CharacterCodingException e) {
            return s;
        }
    }

    private static String first(Map<String, List<String>> h, String key) {
        List<String> v = h.get(key);
        return v == null || v.isEmpty() ? "" : v.get(0);
    }

    /** Lấy tham số trong header kiểu: type/sub; name="value"; name2=value2 (hỗ trợ cơ bản RFC 2231). */
    public static String param(String header, String name) {
        if (header == null || header.isEmpty()) return "";
        // RFC 2231: filename*=UTF-8''ten%20file.pdf
        Matcher m2231 = Pattern.compile("(?i)(?:^|;)\\s*" + Pattern.quote(name) + "\\*=\\s*\"?([^;\"]*)\"?").matcher(header);
        if (m2231.find()) {
            String v = m2231.group(1);
            int q = v.indexOf("''");
            String cs = q > 0 ? v.substring(0, q) : "UTF-8";
            String enc = q >= 0 ? v.substring(q + 2) : v;
            try {
                return java.net.URLDecoder.decode(enc.replace("+", "%2B"), Charset.forName(cs));
            } catch (Exception e) {
                return enc;
            }
        }
        Matcher m = Pattern.compile("(?i)(?:^|;)\\s*" + Pattern.quote(name) + "\\s*=\\s*(\"([^\"]*)\"|[^;\\s]+)").matcher(header);
        if (m.find()) return m.group(2) != null ? m.group(2) : m.group(1);
        return "";
    }

    // ======================================================================
    //  ĐỊA CHỈ EMAIL
    // ======================================================================

    /** "Nguyen Van A" &lt;a@localhost&gt; -> a@localhost (chữ thường). */
    public static String extractEmail(String addr) {
        if (addr == null) return "";
        String s = addr.trim();
        int lt = s.lastIndexOf('<');
        int gt = s.lastIndexOf('>');
        if (lt >= 0 && gt > lt) s = s.substring(lt + 1, gt);
        return s.trim().toLowerCase();
    }

    /** Tên hiển thị của địa chỉ (nếu không có thì trả về email). */
    public static String displayName(String addr) {
        if (addr == null) return "";
        String s = decodeHeader(addr).trim();
        int lt = s.lastIndexOf('<');
        if (lt > 0) {
            String n = s.substring(0, lt).trim().replaceAll("^\"|\"$", "").trim();
            if (!n.isEmpty()) return n;
        }
        return extractEmail(s);
    }

    /** Tách danh sách địa chỉ "a@x, \"B, C\" &lt;b@y&gt;; c@z" thành từng địa chỉ. */
    public static List<String> splitAddresses(String list) {
        List<String> out = new ArrayList<>();
        if (list == null) return out;
        StringBuilder cur = new StringBuilder();
        boolean inQuote = false, inAngle = false;
        for (char c : list.toCharArray()) {
            if (c == '"') inQuote = !inQuote;
            else if (c == '<' && !inQuote) inAngle = true;
            else if (c == '>' && !inQuote) inAngle = false;
            if ((c == ',' || c == ';') && !inQuote && !inAngle) {
                if (!cur.toString().isBlank()) out.add(cur.toString().trim());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        if (!cur.toString().isBlank()) out.add(cur.toString().trim());
        return out;
    }

    public static boolean isValidEmail(String email) {
        return email != null && email.matches("^[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+$");
    }

    // ======================================================================
    //  HTML -> TEXT
    // ======================================================================

    public static String htmlToText(String html) {
        if (html == null) return "";
        String s = html.replaceAll("(?is)<(script|style|head)[^>]*>.*?</\\1\\s*>", "");
        s = s.replaceAll("(?i)<br\\s*/?>", "\n");
        s = s.replaceAll("(?i)</(p|div|tr|li|h[1-6]|table)\\s*>", "\n");
        s = s.replaceAll("(?s)<[^>]+>", "");
        s = s.replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'");
        Matcher m = Pattern.compile("&#(\\d+);").matcher(s);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String rep;
            try {
                rep = new String(Character.toChars(Integer.parseInt(m.group(1))));
            } catch (Exception e) {
                rep = m.group(0);
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(rep));
        }
        m.appendTail(sb);
        s = sb.toString().replace("&amp;", "&");
        s = s.replaceAll("[ \\t]+\\n", "\n").replaceAll("\\n{3,}", "\n\n");
        return s.trim();
    }

    // ======================================================================
    //  CHỈNH SỬA HEADER (dùng khi relay sang Gmail)
    // ======================================================================

    /** Xóa toàn bộ header có tên name (kể cả các dòng gập). */
    public static String removeHeader(String headerBlock, String name) {
        StringBuilder out = new StringBuilder();
        boolean skipping = false;
        String prefix = name.toLowerCase() + ":";
        for (String line : headerBlock.split("\r?\n")) {
            boolean continuation = !line.isEmpty() && (line.charAt(0) == ' ' || line.charAt(0) == '\t');
            if (continuation) {
                if (!skipping) out.append(line).append(CRLF);
                continue;
            }
            skipping = line.toLowerCase().startsWith(prefix);
            if (!skipping) out.append(line).append(CRLF);
        }
        String r = out.toString();
        return r.endsWith(CRLF) ? r.substring(0, r.length() - 2) : r;
    }

    /** Chuẩn hóa xuống dòng thành CRLF (yêu cầu của SMTP/POP3). */
    public static String normalizeCrlf(String s) {
        return s.replace("\r\n", "\n").replace("\r", "\n").replace("\n", CRLF);
    }

    /** Bỏ dấu tiếng Việt -> ASCII (dòng phản hồi của SMTP/POP3 phải là ASCII 7-bit). */
    public static String toAscii(String s) {
        if (s == null || isAscii(s)) return s;
        String n = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").replace('đ', 'd').replace('Đ', 'D');
        StringBuilder sb = new StringBuilder();
        for (char c : n.toCharArray()) sb.append(c < 127 ? c : '?');
        return sb.toString();
    }
}
