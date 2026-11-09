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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * POP3 client tự cài đặt bằng Socket (RFC 1939) – hỗ trợ SSL (pop.gmail.com:995).
 * Được dùng bởi: Mail Client (đọc thư từ Mail Server localhost) và Mail Server (lấy thư từ Gmail về).
 */
public class Pop3Client implements AutoCloseable {
    private final String host;
    private final int port;
    private final boolean ssl;
    private Socket socket;
    private BufferedReader in;
    private BufferedWriter out;
    private Consumer<String> trace = s -> {};

    public Pop3Client(String host, int port, boolean ssl) {
        this.host = host;
        this.port = port;
        this.ssl = ssl;
    }

    public void setTrace(Consumer<String> trace) {
        this.trace = trace == null ? s -> {} : trace;
    }

    public void connect(int timeoutMs) throws IOException {
        socket = ssl ? SSLSocketFactory.getDefault().createSocket() : new Socket();
        socket.connect(new InetSocketAddress(host, port), timeoutMs);
        socket.setSoTimeout(Math.max(timeoutMs, 30000));
        in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.ISO_8859_1));
        out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.ISO_8859_1));
        readStatus();
    }

    public void login(String user, String pass) throws IOException {
        command("USER " + user);
        trace.accept("C: PASS ********");
        out.write("PASS " + pass + "\r\n");
        out.flush();
        readStatus();
    }

    /** STAT -> [số thư, tổng dung lượng] */
    public int[] stat() throws IOException {
        String[] p = command("STAT").split("\\s+");
        return new int[]{Integer.parseInt(p[0]), Integer.parseInt(p[1])};
    }

    /** UIDL -> map: số thứ tự -> UID */
    public Map<Integer, String> uidl() throws IOException {
        command("UIDL");
        Map<Integer, String> map = new LinkedHashMap<>();
        for (String line : readMultiLine().split("\r\n")) {
            if (line.isBlank()) continue;
            String[] p = line.trim().split("\\s+", 2);
            map.put(Integer.parseInt(p[0]), p.length > 1 ? p[1] : p[0]);
        }
        return map;
    }

    /** LIST -> map: số thứ tự -> kích thước (byte) */
    public Map<Integer, Integer> list() throws IOException {
        command("LIST");
        Map<Integer, Integer> map = new LinkedHashMap<>();
        for (String line : readMultiLine().split("\r\n")) {
            if (line.isBlank()) continue;
            String[] p = line.trim().split("\\s+");
            map.put(Integer.parseInt(p[0]), Integer.parseInt(p[1]));
        }
        return map;
    }

    /** TOP n lines: lấy header + n dòng đầu của thư (không đánh dấu đã đọc). */
    public String top(int msg, int lines) throws IOException {
        command("TOP " + msg + " " + lines);
        return readMultiLine();
    }

    /** RETR n: tải toàn bộ thư. */
    public String retr(int msg) throws IOException {
        command("RETR " + msg);
        return readMultiLine();
    }

    public void dele(int msg) throws IOException {
        command("DELE " + msg);
    }

    public void rset() throws IOException {
        command("RSET");
    }

    /** Gửi lệnh một dòng, trả về phần text sau "+OK". */
    public String command(String cmd) throws IOException {
        trace.accept("C: " + cmd);
        out.write(cmd + "\r\n");
        out.flush();
        return readStatus();
    }

    public void quit() {
        try {
            command("QUIT");
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

    private String readStatus() throws IOException {
        String line = in.readLine();
        if (line == null) throw new IOException("Server đã đóng kết nối");
        trace.accept("S: " + line);
        if (!line.startsWith("+OK")) throw new IOException(line.startsWith("-ERR") ? line.substring(4).trim() : line);
        return line.length() > 3 ? line.substring(3).trim() : "";
    }

    /** Đọc phản hồi nhiều dòng (kết thúc bằng dòng "."), bỏ dot-stuffing. */
    private String readMultiLine() throws IOException {
        StringBuilder sb = new StringBuilder();
        int count = 0;
        while (true) {
            String line = in.readLine();
            if (line == null) throw new IOException("Mất kết nối khi đọc dữ liệu");
            if (line.equals(".")) break;
            if (line.startsWith("..")) line = line.substring(1);
            sb.append(line).append("\r\n");
            count++;
        }
        trace.accept("S: <" + count + " dòng dữ liệu> + \".\"");
        return sb.toString();
    }
}
