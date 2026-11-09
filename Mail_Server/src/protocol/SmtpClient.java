package protocol;

import javax.net.ssl.SSLSocketFactory;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.function.Consumer;

/**
 * SMTP client tự cài đặt bằng Socket (RFC 5321) – hỗ trợ SSL (cổng 465 của Gmail) và AUTH LOGIN.
 * Được dùng bởi: Mail Client (gửi thư tới Mail Server localhost) và Mail Server (relay thư sang Gmail).
 */
public class SmtpClient implements AutoCloseable {
    private final String host;
    private final int port;
    private final boolean ssl;
    private Socket socket;
    private BufferedReader in;
    private BufferedWriter out;
    private Consumer<String> trace = s -> {};
    private final List<String> capabilities = new ArrayList<>();

    public SmtpClient(String host, int port, boolean ssl) {
        this.host = host;
        this.port = port;
        this.ssl = ssl;
    }

    public void setTrace(Consumer<String> trace) {
        this.trace = trace == null ? s -> {} : trace;
    }

    public void connect(int timeoutMs) throws IOException {
        if (ssl) {
            socket = SSLSocketFactory.getDefault().createSocket();
        } else {
            socket = new Socket();
        }
        socket.connect(new InetSocketAddress(host, port), timeoutMs);
        socket.setSoTimeout(Math.max(timeoutMs, 30000));
        in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.ISO_8859_1));
        out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.ISO_8859_1));
        expect(readResponse(), 220);
    }

    public List<String> ehlo(String clientName) throws IOException {
        send("EHLO " + clientName);
        Response r = readResponse();
        if (r.code != 250) {
            send("HELO " + clientName);
            r = readResponse();
            expect(r, 250);
        }
        capabilities.clear();
        for (int i = 1; i < r.lines.size(); i++) capabilities.add(r.lines.get(i).substring(4).trim().toUpperCase());
        return capabilities;
    }

    public void authLogin(String user, String pass) throws IOException {
        send("AUTH LOGIN");
        expect(readResponse(), 334);
        sendSecret(Base64.getEncoder().encodeToString(user.getBytes(StandardCharsets.UTF_8)), "<username base64>");
        expect(readResponse(), 334);
        sendSecret(Base64.getEncoder().encodeToString(pass.getBytes(StandardCharsets.UTF_8)), "<password base64 ***>");
        expect(readResponse(), 235);
    }

    public void mailFrom(String addr) throws IOException {
        send("MAIL FROM:<" + addr + ">");
        expect(readResponse(), 250);
    }

    public void rcptTo(String addr) throws IOException {
        send("RCPT TO:<" + addr + ">");
        Response r = readResponse();
        if (r.code != 250 && r.code != 251) throw new SmtpException(r.code, r.text());
    }

    /** Gửi nội dung thư bằng lệnh DATA (có dot-stuffing). Trả về phản hồi cuối của server. */
    public String data(String rawMessage) throws IOException {
        send("DATA");
        expect(readResponse(), 354);
        String msg = rawMessage.replace("\r\n", "\n").replace("\r", "\n");
        if (msg.endsWith("\n")) msg = msg.substring(0, msg.length() - 1);
        int lines = 0;
        for (String line : msg.split("\n", -1)) {
            if (line.startsWith(".")) out.write(".");
            out.write(line);
            out.write("\r\n");
            lines++;
        }
        out.write(".\r\n");
        out.flush();
        trace.accept("C: <" + lines + " dòng nội dung thư> + \".\"");
        Response r = readResponse();
        expect(r, 250);
        return r.text();
    }

    /** Gửi trọn vẹn một thư: MAIL FROM, RCPT TO (nhiều người nhận), DATA. */
    public String sendMail(String from, List<String> recipients, String rawMessage) throws IOException {
        mailFrom(from);
        for (String r : recipients) rcptTo(r);
        return data(rawMessage);
    }

    public void quit() {
        try {
            send("QUIT");
            readResponse();
        } catch (Exception ignored) {
        }
    }

    @Override
    public void close() {
        try {
            if (socket != null) socket.close();
        } catch (IOException ignored) {
        }
    }

    // ---------------------------------------------------------------------
    private void send(String line) throws IOException {
        trace.accept("C: " + line);
        out.write(line);
        out.write("\r\n");
        out.flush();
    }

    private void sendSecret(String line, String shown) throws IOException {
        trace.accept("C: " + shown);
        out.write(line);
        out.write("\r\n");
        out.flush();
    }

    private Response readResponse() throws IOException {
        Response r = new Response();
        while (true) {
            String line = in.readLine();
            if (line == null) throw new IOException("Server đã đóng kết nối");
            trace.accept("S: " + line);
            r.lines.add(line);
            if (line.length() < 4 || line.charAt(3) != '-') {
                try {
                    r.code = Integer.parseInt(line.substring(0, Math.min(3, line.length())));
                } catch (NumberFormatException e) {
                    r.code = 0;
                }
                return r;
            }
        }
    }

    private static void expect(Response r, int code) throws SmtpException {
        if (r.code != code) throw new SmtpException(r.code, r.text());
    }

    private static class Response {
        int code;
        final List<String> lines = new ArrayList<>();

        String text() {
            return String.join(" | ", lines);
        }
    }

    public static class SmtpException extends IOException {
        private final int code;

        public SmtpException(int code, String msg) {
            super(msg);
            this.code = code;
        }

        public int getCode() { return code; }
    }
}
