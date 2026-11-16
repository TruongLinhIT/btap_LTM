package http1;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;

/**
 * Endpoint HTTP/1.1 nội bộ hỗ trợ cho Trình duyệt Web Client (Chrome / Cốc Cốc):
 * - Phục vụ Trang chủ Google (/), Tìm kiếm Google (/search?q=...), Trang tài liệu HTTP (/about_http.html),
 *   Biểu mẫu kiểm thử POST (/post_test.html) và API xử lý POST (/api/submit, /api/json).
 * - Đảm bảo hoạt động độc lập với thư mục làm việc (nhờ nội dung HTML tích hợp sẵn).
 */
public class httpServer implements Runnable {

    public static final File WEB_ROOT = new File(".");
    public static final File WEB_ROOT_SUB = new File("web_root");
    public static final String DEFAULT_FILE = "index.html";
    public static final String FILE_NOT_FOUND = "404.html";
    public static final int DEFAULT_PORT = 8899;
    public static final boolean verbose = true;

    private final Socket connect;

    public httpServer(Socket c) {
        connect = c;
    }

    public static void main(String[] args) {
        int port = DEFAULT_PORT;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException ignored) {}
        }

        try (ServerSocket serverConnect = new ServerSocket(port)) {
            System.out.println("=================================================");
            System.out.println(" LTM HTTP/1.1 Endpoint da khoi dong tren cong " + port);
            System.out.println(" URL: http://localhost:" + port + "/");
            System.out.println("=================================================\n");

            while (true) {
                httpServer myserver = new httpServer(serverConnect.accept());
                Thread thread = new Thread(myserver);
                thread.start();
            }
        } catch (IOException e) {
            System.err.println("Loi khoi dong tren cong " + port + ": " + e.getMessage());
        }
    }

    private static String nowStr() {
        return new SimpleDateFormat("HH:mm:ss").format(new Date());
    }

    private static void emitLog(String msg) {
        if (verbose) {
            System.out.println("[" + nowStr() + "] " + msg);
        }
    }

    @Override
    public void run() {
        InputStream in = null;
        BufferedOutputStream dataOut = null;
        String rawTarget = "/";
        String method = "GET";
        String clientIp = connect.getInetAddress().getHostAddress();

        try {
            connect.setSoTimeout(15000);
            in = new BufferedInputStream(connect.getInputStream());
            dataOut = new BufferedOutputStream(connect.getOutputStream());

            String input = readLine(in);
            if (input == null || input.trim().isEmpty()) {
                return;
            }

            StringTokenizer parse = new StringTokenizer(input);
            if (!parse.hasMoreTokens()) {
                return;
            }
            method = parse.nextToken().toUpperCase();
            rawTarget = parse.hasMoreTokens() ? parse.nextToken() : "/";

            Map<String, String> headers = new HashMap<>();
            String headerLine;
            int contentLength = 0;
            while ((headerLine = readLine(in)) != null && !headerLine.isEmpty()) {
                int colonIdx = headerLine.indexOf(':');
                if (colonIdx > 0) {
                    String hName = headerLine.substring(0, colonIdx).trim().toLowerCase();
                    String hVal = headerLine.substring(colonIdx + 1).trim();
                    headers.put(hName, hVal);
                    if ("content-length".equals(hName)) {
                        try {
                            contentLength = Integer.parseInt(hVal);
                        } catch (NumberFormatException ignored) {}
                    }
                }
            }

            if (method.equals("OPTIONS")) {
                sendOptions(dataOut);
                emitLog(clientIp + " OPTIONS " + rawTarget + " -> 204 No Content");
                return;
            }

            if (!method.equals("GET") && !method.equals("HEAD") && !method.equals("POST")) {
                emitLog(clientIp + " " + method + " " + rawTarget + " -> 501 Not Implemented");
                send501(dataOut, method);
                return;
            }

            if (method.equals("POST")) {
                String contentType = headers.getOrDefault("content-type", "");
                handlePostRequest(in, dataOut, clientIp, rawTarget, contentLength, contentType);
                return;
            }

            String pathOnly = extractPathOnly(rawTarget);
            String query = extractQuery(rawTarget);
            boolean isHead = method.equals("HEAD");

            // 1. Trang chủ Google (/, /index.html, /google)
            if ("/".equals(pathOnly) || "/index.html".equalsIgnoreCase(pathOnly)) {
                sendHtmlResponse(dataOut, "HTTP/1.1 200 OK", buildGoogleHomeHtml(), isHead, null);
                emitLog(clientIp + " " + method + " " + rawTarget + " -> 200 OK (Google Home)");
                return;
            }

            // 2. Trang Tìm kiếm Google (/search?q=...)
            if ("/search".equalsIgnoreCase(pathOnly) || "/google".equalsIgnoreCase(pathOnly)) {
                handleSearchRequest(dataOut, clientIp, method, rawTarget, query);
                return;
            }

            // 3. Trang Biểu mẫu HTTP POST (/post_test.html)
            if ("/post_test.html".equalsIgnoreCase(pathOnly)) {
                sendHtmlResponse(dataOut, "HTTP/1.1 200 OK", buildPostFormHtml(), isHead, null);
                emitLog(clientIp + " " + method + " " + rawTarget + " -> 200 OK (POST Form Page)");
                return;
            }

            // 4. Trang Tài liệu Giao thức HTTP (/about_http.html)
            if ("/about_http.html".equalsIgnoreCase(pathOnly)) {
                String disp = isDownloadQuery(query) ? "attachment; filename=\"about_http.html\"" : null;
                sendHtmlResponse(dataOut, "HTTP/1.1 200 OK", buildAboutHttpHtml(), isHead, disp);
                emitLog(clientIp + " " + method + " " + rawTarget + " -> 200 OK (About HTTP Page)");
                return;
            }

            // 5. Nếu yêu cầu tệp khác trên đĩa (hoặc trả 404 Not Found)
            boolean forceDownload = isDownloadQuery(query);
            String fileRequested = normalizePath(rawTarget);
            if (fileRequested == null) {
                fileNotFound(dataOut, rawTarget, isHead);
                emitLog(clientIp + " " + method + " " + rawTarget + " -> 404 Not Found");
                return;
            }

            File file = findRequestedFile(fileRequested);
            if (file == null || !file.exists() || file.isDirectory()) {
                fileNotFound(dataOut, fileRequested, isHead);
                emitLog(clientIp + " " + method + " " + rawTarget + " -> 404 Not Found");
                return;
            }

            long totalLength = file.length();
            String contentType = getContentType(file.getName());
            String disposition = forceDownload ? "attachment; filename=\"" + file.getName() + "\"" : null;

            sendHeaders(dataOut, "HTTP/1.1 200 OK", contentType, totalLength, null, disposition);
            if (!isHead) {
                streamFileRange(file, 0, totalLength, dataOut);
            }
            dataOut.flush();
            emitLog(clientIp + " " + method + " " + rawTarget + " -> 200 OK (" + contentType + ", " + totalLength + " bytes)");

        } catch (SocketException ignored) {
        } catch (FileNotFoundException fnfe) {
            try {
                if (dataOut != null) {
                    fileNotFound(dataOut, rawTarget, method.equals("HEAD"));
                }
            } catch (IOException ignored) {}
        } catch (IOException ioe) {
            emitLog(clientIp + " Loi xu ly " + method + " " + rawTarget + ": " + ioe.getMessage());
        } finally {
            try {
                if (in != null) in.close();
                if (dataOut != null) dataOut.close();
                if (connect != null) connect.close();
            } catch (Exception ignored) {}
        }
    }

    private void sendHtmlResponse(OutputStream dataOut, String statusLine, String html,
                                  boolean headOnly, String disposition) throws IOException {
        byte[] bodyBytes = html.getBytes(StandardCharsets.UTF_8);
        sendHeaders(dataOut, statusLine, "text/html; charset=UTF-8", bodyBytes.length, null, disposition);
        if (!headOnly) {
            dataOut.write(bodyBytes, 0, bodyBytes.length);
        }
        dataOut.flush();
    }

    public static String buildGoogleHomeHtml() {
        return "<html>"
                + "<head><title>Google - Trình Duyệt Web Client (HTTP/1.1)</title></head>"
                + "<body bgcolor='#FFFFFF' text='#202124' link='#1A73E8' vlink='#1A73E8' alink='#EA4335'>"
                + "<table width='100%' cellpadding='10' cellspacing='0' border='0'>"
                + "<tr><td align='right'>"
                + "<font face='Arial, sans-serif' size='2'>"
                + "<a href='/search?q=Giao+thuc+HTTP+GET+HEAD+POST'>Tìm kiếm Google (GET)</a> &nbsp;&nbsp;|&nbsp;&nbsp; "
                + "<a href='/post_test.html'>Gửi biểu mẫu (POST)</a> &nbsp;&nbsp;|&nbsp;&nbsp; "
                + "<a href='/about_http.html'>Tài liệu HTTP/1.1</a> &nbsp;&nbsp;|&nbsp;&nbsp; "
                + "<a href='http://example.com'>Example.com</a>"
                + "</font>"
                + "</td></tr></table>"
                + "<table width='100%' cellpadding='0' cellspacing='0' border='0'><tr><td align='center'>"
                + "<br>"
                + "<font face='Arial, Helvetica, sans-serif' size='7'><b>"
                + "<font color='#4285F4'>G</font><font color='#EA4335'>o</font><font color='#FBBC05'>o</font>"
                + "<font color='#4285F4'>g</font><font color='#34A853'>l</font><font color='#EA4335'>e</font>"
                + "</b></font><br>"
                + "<font face='Arial, sans-serif' size='2' color='#5F6368'>"
                + "<b>Trình Duyệt Web Client (Tương tự Chrome / Cốc Cốc) &bull; Giao thức HTTP/1.1 trên TCP Socket</b>"
                + "</font><br><br>"
                + "<table width='76%' cellpadding='14' cellspacing='0' border='1' bordercolor='#DFE1E5' bgcolor='#F8F9FA'>"
                + "<tr><td align='center'>"
                + "<form action='/search' method='GET'>"
                + "<font face='Arial' size='2' color='#3C4043'><b>Nhập từ khóa tìm kiếm trên Google (Truy vấn qua phương thức HTTP GET):</b></font><br><br>"
                + "<input type='text' name='q' value='Giao thuc HTTP GET HEAD POST History Download' size='56'><br><br>"
                + "<input type='submit' value='  Tìm trên Google (HTTP GET)  '>"
                + "</form>"
                + "</td></tr></table>"
                + "<br>"
                + "<table width='88%' cellpadding='12' cellspacing='0' border='1' bordercolor='#E0E0E0' bgcolor='#FFFFFF'>"
                + "<tr bgcolor='#E8F0FE'>"
                + "<td width='55%'><font face='Arial' size='3' color='#1967D2'><b>1. GỬI BIỂU MẪU HTTP POST TRỰC TIẾP</b></font></td>"
                + "<td width='45%'><font face='Arial' size='3' color='#1967D2'><b>2. CÁC TÍNH NĂNG CỦA TRÌNH DUYỆT (BROWSER)</b></font></td>"
                + "</tr>"
                + "<tr>"
                + "<td valign='top' bgcolor='#FAFAFA'>"
                + "<form action='/api/submit' method='POST'>"
                + "<font face='Arial' size='2'><b>Họ và tên sinh viên:</b></font><br>"
                + "<input type='text' name='hoten' value='Nguyen Van A' size='36'><br><br>"
                + "<font face='Arial' size='2'><b>Mã số sinh viên:</b></font><br>"
                + "<input type='text' name='masv' value='21IT001' size='36'><br><br>"
                + "<font face='Arial' size='2'><b>Nội dung gửi qua HTTP POST:</b></font><br>"
                + "<input type='text' name='noidung' value='Kiem thu phuong thuc POST tu trinh duyet Web Client' size='42'><br><br>"
                + "<input type='submit' value='  Gửi Dữ Liệu (HTTP POST)  '>"
                + "</form>"
                + "</td>"
                + "<td valign='top'>"
                + "<font face='Arial' size='2'>"
                + "&bull; <b>HTTP GET:</b> Nhập địa chỉ URL hoặc từ khóa tìm kiếm lên thanh Omnibox.<br><br>"
                + "&bull; <b>HTTP HEAD:</b> Chọn <b>HEAD</b> hoặc bấm nút <b>[HEAD]</b> trên thanh công cụ để chỉ lấy HTTP Response Headers (Body = 0 bytes).<br><br>"
                + "&bull; <b>HTTP POST:</b> Điền biểu mẫu bên trái hoặc bấm nút <b>[POST]</b> để gửi dữ liệu Form / JSON.<br><br>"
                + "&bull; <b>Lịch sử (History - Ctrl+H):</b> Lưu vết, tìm kiếm và lọc các trang đã truy cập.<br><br>"
                + "&bull; <b>Tải xuống (Download - Ctrl+J):</b> Tải trang web / tài liệu về máy qua giao thức HTTP/1.1 (<a href='/about_http.html?download=true'>Tải thử trang tài liệu HTTP</a>).<br><br>"
                + "&bull; <b>Kiểm thử mã lỗi 404:</b> <a href='/trang_khong_ton_tai.html'>Mở đường dẫn không tồn tại (404 Not Found)</a>"
                + "</font>"
                + "</td>"
                + "</tr></table>"
                + "<br><font face='Arial' size='2' color='#70757A'>Web Client Browser &bull; Giao thức HTTP/1.1 (GET, HEAD, POST, History, Download)</font><br><br>"
                + "</td></tr></table></body></html>";
    }

    public static String buildPostFormHtml() {
        return "<html><head><title>Biểu Mẫu HTTP POST - Web Client Browser</title></head>"
                + "<body bgcolor='#F8F9FA' text='#202124' link='#1A73E8'>"
                + "<table width='86%' cellpadding='16' cellspacing='0' border='1' bordercolor='#DADCE0' bgcolor='#FFFFFF' align='center'>"
                + "<tr bgcolor='#E8F0FE'><td>"
                + "<font face='Arial' size='4' color='#1967D2'><b>BIỂU MẪU KIỂM THỬ PHƯƠNG THỨC HTTP POST</b></font><br>"
                + "<font face='Arial' size='2' color='#5F6368'>Trình duyệt đóng gói bản tin <code>POST /api/submit HTTP/1.1</code> kèm <code>Content-Length</code> và truyền qua TCP Socket.</font>"
                + "</td></tr>"
                + "<tr><td>"
                + "<form action='/api/submit' method='POST'>"
                + "<font face='Arial' size='2'><b>1. Họ và tên sinh viên:</b></font><br>"
                + "<input type='text' name='hoten' value='Nguyen Van A' size='45'><br><br>"
                + "<font face='Arial' size='2'><b>2. Mã số sinh viên:</b></font><br>"
                + "<input type='text' name='masv' value='21IT001' size='45'><br><br>"
                + "<font face='Arial' size='2'><b>3. Học phần:</b></font><br>"
                + "<input type='text' name='hocphan' value='Lap Trinh Mang - Web Client Browser' size='45'><br><br>"
                + "<font face='Arial' size='2'><b>4. Nội dung thông điệp POST:</b></font><br>"
                + "<input type='text' name='noidung' value='Gui du lieu bieu mau qua giao thuc HTTP POST' size='55'><br><br>"
                + "<input type='submit' value='  Gửi Biểu Mẫu Lên Server (HTTP POST)  '>"
                + "</form><br><hr size='1' color='#E0E0E0'>"
                + "<a href='/'><font face='Arial' size='2'><b>&larr; Quay về Trang chủ Google</b></font></a>"
                + "</td></tr></table></body></html>";
    }

    public static String buildAboutHttpHtml() {
        return "<html><head><title>Tài Liệu Giao Thức HTTP/1.1 - Web Client Browser</title></head>"
                + "<body bgcolor='#F8F9FA' text='#202124' link='#1A73E8'>"
                + "<table width='88%' cellpadding='16' cellspacing='0' border='1' bordercolor='#DADCE0' bgcolor='#FFFFFF' align='center'>"
                + "<tr bgcolor='#E8F0FE'><td>"
                + "<font face='Arial' size='4' color='#1967D2'><b>TỔNG QUAN GIAO THỨC HTTP/1.1 TRÊN WEB CLIENT (BROWSER)</b></font>"
                + "</td></tr>"
                + "<tr><td><font face='Arial' size='2'>"
                + "<b>1. Phương thức HTTP GET:</b> Dùng để yêu cầu lấy nội dung trang web hoặc tài nguyên từ URL.<br><br>"
                + "<b>2. Phương thức HTTP HEAD:</b> Giống GET nhưng chỉ nhận phần <i>Status-Line</i> và <i>Response Headers</i>, không tải phần thân (Body = 0 bytes), giúp kiểm tra nhanh trạng thái và kích thước tài nguyên.<br><br>"
                + "<b>3. Phương thức HTTP POST:</b> Dùng để gửi dữ liệu biểu mẫu (<code>application/x-www-form-urlencoded</code>) hoặc <code>application/json</code> trong phần Request Body.<br><br>"
                + "<b>4. Quản lý Lịch sử (History - Ctrl+H):</b> Ghi lại thời gian, phương thức HTTP, tiêu đề và URL đã truy cập.<br><br>"
                + "<b>5. Quản lý Tải xuống (Download - Ctrl+J):</b> Tải nội dung trang web / tệp từ bất kỳ URL nào về máy tính qua kết nối HTTP Socket.<br><br>"
                + "</font>"
                + "<hr size='1' color='#E0E0E0'>"
                + "<a href='/about_http.html?download=true'><font face='Arial' size='2'><b>[Tải tài liệu này về máy (Download)]</b></font></a>"
                + " &nbsp;&nbsp;|&nbsp;&nbsp; "
                + "<a href='/'><font face='Arial' size='2'><b>&larr; Quay về Trang chủ Google</b></font></a>"
                + "</td></tr></table></body></html>";
    }

    private void handleSearchRequest(BufferedOutputStream dataOut, String clientIp, String method,
                                     String rawTarget, String query) throws IOException {
        String keyword = "";
        if (query != null && !query.isEmpty()) {
            for (String pair : query.split("&")) {
                String[] kv = pair.split("=", 2);
                if (kv.length == 2 && ("q".equalsIgnoreCase(kv[0]) || "query".equalsIgnoreCase(kv[0]))) {
                    try {
                        keyword = URLDecoder.decode(kv[1], StandardCharsets.UTF_8.name()).trim();
                    } catch (Exception ignored) {
                        keyword = kv[1].trim();
                    }
                }
            }
        }
        if (keyword.isEmpty()) {
            keyword = "Giao thức HTTP GET HEAD POST History Download";
        }

        String escQ = escapeHtml(keyword);
        String html = "<html><head><title>" + escQ + " - Tìm trên Google</title></head>"
                + "<body bgcolor='#FFFFFF' text='#202124' link='#1A0DAB' vlink='#681DA8'>"
                + "<table width='100%' cellpadding='12' cellspacing='0' border='0' bgcolor='#F8F9FA'>"
                + "<tr>"
                + "<td width='160' align='center'>"
                + "<a href='/' style='text-decoration:none;'>"
                + "<font face='Arial' size='5'><b>"
                + "<font color='#4285F4'>G</font><font color='#EA4335'>o</font><font color='#FBBC05'>o</font>"
                + "<font color='#4285F4'>g</font><font color='#34A853'>l</font><font color='#EA4335'>e</font>"
                + "</b></font></a>"
                + "</td>"
                + "<td>"
                + "<form action='/search' method='GET'>"
                + "<input type='text' name='q' size='48' value='" + escQ + "'> "
                + "<input type='submit' value='Tìm kiếm Google (HTTP GET)'>"
                + "</form>"
                + "</td>"
                + "</tr></table>"
                + "<hr size='1' color='#EBEBEB'>"
                + "<table width='92%' cellpadding='6' cellspacing='0' border='0' align='center'>"
                + "<tr><td><font face='Arial' size='2' color='#70757A'>"
                + "Kết quả tìm kiếm cho từ khóa <b>\"" + escQ + "\"</b> &bull; Truy vấn bằng giao thức <b>HTTP/1.1 GET</b> qua TCP Socket"
                + "</font></td></tr>"

                + "<tr><td><br>"
                + "<font face='Arial' size='2' color='#202124'>/about_http.html &bull; Tài liệu Giao thức HTTP</font><br>"
                + "<a href='/about_http.html'><font face='Arial' size='4'><b>Tổng Quan Giao Thức HTTP/1.1 (GET, HEAD, POST) Trên Trình Duyệt Web Client</b></font></a><br>"
                + "<font face='Arial' size='2' color='#4D5156'>Tìm hiểu cơ chế hoạt động của các phương thức <b>GET</b>, <b>HEAD</b>, <b>POST</b>, <b>History</b> và <b>Download</b> cho từ khóa <b>" + escQ + "</b>.</font>"
                + "</td></tr>"

                + "<tr><td><br>"
                + "<font face='Arial' size='2' color='#202124'>/post_test.html &bull; Biểu mẫu HTTP POST</font><br>"
                + "<a href='/post_test.html'><font face='Arial' size='4'><b>Công Cụ Kiểm Thử Biểu Mẫu HTTP POST Trên Trình Duyệt</b></font></a><br>"
                + "<font face='Arial' size='2' color='#4D5156'>Trang thử nghiệm gửi dữ liệu biểu mẫu HTML Form qua phương thức <b>HTTP POST</b> trực tiếp trên trình duyệt.</font>"
                + "</td></tr>"

                + "<tr><td><br>"
                + "<font face='Arial' size='2' color='#202124'>http://example.com &bull; Website thực tế ngoài Internet</font><br>"
                + "<a href='http://example.com'><font face='Arial' size='4'><b>Example Domain - Kiểm thử kết nối HTTP thực tế trên Internet</b></font></a><br>"
                + "<font face='Arial' size='2' color='#4D5156'>Truy cập website thực tế ngoài Internet bằng giao thức HTTP/1.1 trên trình duyệt Web Client.</font>"
                + "</td></tr>"

                + "<tr><td><br>"
                + "<font face='Arial' size='2' color='#202124'>http://info.cern.ch &bull; Trang web đầu tiên trên thế giới</font><br>"
                + "<a href='http://info.cern.ch'><font face='Arial' size='4'><b>CERN - Trang Web HTTP Đầu Tiên Trên Thế Giới</b></font></a><br>"
                + "<font face='Arial' size='2' color='#4D5156'>Thử nghiệm duyệt web thực tế và kiểm tra HTTP Headers / Download trên trình duyệt.</font>"
                + "</td></tr>"

                + "<tr><td><br><hr size='1' color='#EBEBEB'>"
                + "<a href='/'><font face='Arial' size='2'><b>&larr; Quay lại Trang chủ Google</b></font></a>"
                + "</td></tr>"
                + "</table></body></html>";

        sendHtmlResponse(dataOut, "HTTP/1.1 200 OK", html, "HEAD".equalsIgnoreCase(method), null);
        emitLog(clientIp + " " + method + " " + rawTarget + " -> 200 OK (Google Search: " + keyword + ")");
    }

    private String extractPathOnly(String rawTarget) {
        if (rawTarget == null || rawTarget.isEmpty()) return "/";
        int q = rawTarget.indexOf('?');
        String p = q >= 0 ? rawTarget.substring(0, q) : rawTarget;
        int h = p.indexOf('#');
        return h >= 0 ? p.substring(0, h) : p;
    }

    private String extractQuery(String rawTarget) {
        if (rawTarget == null) return "";
        int q = rawTarget.indexOf('?');
        if (q < 0) return "";
        int h = rawTarget.indexOf('#', q);
        return h >= 0 ? rawTarget.substring(q + 1, h) : rawTarget.substring(q + 1);
    }

    private boolean isDownloadQuery(String query) {
        if (query == null || query.isEmpty()) return false;
        for (String param : query.split("&")) {
            String lower = param.trim().toLowerCase();
            if (lower.equals("download=true") || lower.equals("download=1")) {
                return true;
            }
        }
        return false;
    }

    private String normalizePath(String rawTarget) {
        if (rawTarget == null || rawTarget.isEmpty()) {
            return DEFAULT_FILE;
        }
        String pathOnly = extractPathOnly(rawTarget);
        if (pathOnly.endsWith("/")) {
            pathOnly += DEFAULT_FILE;
        }
        if (pathOnly.startsWith("/")) {
            pathOnly = pathOnly.substring(1);
        }
        if (pathOnly.isEmpty()) {
            pathOnly = DEFAULT_FILE;
        }

        try {
            pathOnly = URLDecoder.decode(pathOnly.replace("+", "%2B"), StandardCharsets.UTF_8.name());
        } catch (Exception ignored) {}

        if (pathOnly.contains("..")) {
            return null;
        }
        return pathOnly;
    }

    private void handlePostRequest(InputStream in, BufferedOutputStream dataOut, String clientIp,
                                   String path, int contentLength, String contentType) throws IOException {
        byte[] rawBodyBytes = new byte[0];
        if (contentLength > 0) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream(Math.min(contentLength, 65536));
            byte[] buffer = new byte[4096];
            int totalRead = 0;
            while (totalRead < contentLength) {
                int toRead = Math.min(buffer.length, contentLength - totalRead);
                int read = in.read(buffer, 0, toRead);
                if (read == -1) break;
                baos.write(buffer, 0, read);
                totalRead += read;
            }
            rawBodyBytes = baos.toByteArray();
        }

        String rawBody = new String(rawBodyBytes, StandardCharsets.UTF_8);
        String ctDisplay = (contentType == null || contentType.isEmpty()) ? "application/x-www-form-urlencoded" : contentType;

        emitLog(clientIp + " POST " + path + " -> 200 OK (" + rawBodyBytes.length + " bytes: " + rawBody + ")");

        StringBuilder tableRows = new StringBuilder();
        boolean isJsonOrRaw = ctDisplay.toLowerCase().contains("json")
                || ctDisplay.toLowerCase().contains("xml")
                || ctDisplay.toLowerCase().contains("text/plain");

        if (!rawBody.isEmpty() && !isJsonOrRaw && rawBody.contains("=")) {
            String[] pairs = rawBody.split("&");
            for (String pair : pairs) {
                String[] kv = pair.split("=", 2);
                String k = kv[0];
                String v = kv.length > 1 ? kv[1] : "";
                try {
                    k = URLDecoder.decode(k, StandardCharsets.UTF_8.name());
                    v = URLDecoder.decode(v, StandardCharsets.UTF_8.name());
                } catch (Exception ignored) {}
                tableRows.append("<tr>")
                        .append("<td bgcolor='#F8FAFC'><font face='Arial' size='2'><b>").append(escapeHtml(k)).append("</b></font></td>")
                        .append("<td><font face='Arial' size='2' color='#0F766E'><b>").append(escapeHtml(v)).append("</b></font></td>")
                        .append("</tr>");
            }
        } else if (!rawBody.isEmpty()) {
            tableRows.append("<tr>")
                    .append("<td bgcolor='#F8FAFC'><font face='Arial' size='2'><b>Payload</b></font></td>")
                    .append("<td><font face='Courier New' size='2'>").append(escapeHtml(rawBody)).append("</font></td>")
                    .append("</tr>");
        } else {
            tableRows.append("<tr><td colspan='2' align='center'><font face='Arial' size='2'>Không có dữ liệu body</font></td></tr>");
        }

        String responseHtml = "<html><head><title>Kết Quả HTTP POST - Web Client Browser</title></head>"
                + "<body bgcolor='#F8F9FA' text='#202124' link='#1A73E8'>"
                + "<table width='90%' cellpadding='16' cellspacing='0' border='1' bordercolor='#DADCE0' bgcolor='#FFFFFF' align='center'>"
                + "<tr><td bgcolor='#E6F4EA'>"
                + "<font face='Arial' size='4' color='#137333'><b>[HTTP/1.1 200 OK] GỬI DỮ LIỆU HTTP POST THÀNH CÔNG!</b></font><br>"
                + "<font face='Arial' size='2' color='#3C4043'>Trình duyệt Web Client đã đóng gói bản tin HTTP POST Request và truyền qua TCP Socket.</font>"
                + "</td></tr>"
                + "<tr><td>"
                + "<font face='Arial' size='2'>"
                + "<b>Đường dẫn (Endpoint):</b> <code>" + escapeHtml(path) + "</code><br>"
                + "<b>Loại nội dung (Content-Type):</b> <code>" + escapeHtml(ctDisplay) + "</code><br>"
                + "<b>Kích thước gói tin (Content-Length):</b> <code>" + rawBodyBytes.length + " bytes</code><br>"
                + "<b>Dữ liệu thô (Raw POST Body):</b> <code>" + escapeHtml(rawBody) + "</code>"
                + "</font><br><br>"
                + "<font face='Arial' size='3'><b>Bảng phân tích tham số POST đã giải mã (UTF-8):</b></font><br><br>"
                + "<table width='100%' cellpadding='8' cellspacing='0' border='1' bordercolor='#CBD5E1'>"
                + "<tr bgcolor='#EFF6FF'>"
                + "<th align='left' width='35%'><font face='Arial' size='2' color='#1E40AF'>Tên tham số (Field)</font></th>"
                + "<th align='left'><font face='Arial' size='2' color='#1E40AF'>Giá trị nhận được (Value)</font></th>"
                + "</tr>"
                + tableRows
                + "</table><br>"
                + "<a href='/'><font face='Arial' size='2'><b>&larr; Quay về Trang chủ Google</b></font></a>"
                + " &nbsp;|&nbsp; "
                + "<a href='/post_test.html'><font face='Arial' size='2'><b>Gửi tiếp biểu mẫu POST khác</b></font></a>"
                + "</td></tr></table></body></html>";

        sendHtmlResponse(dataOut, "HTTP/1.1 200 OK", responseHtml, false, null);
    }

    private void sendHeaders(OutputStream out, String statusLine, String contentType, long contentLength,
                             String contentRange, String contentDisposition) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append(statusLine).append("\r\n");
        sb.append("Server: LTM-WebClient-Endpoint/1.1\r\n");
        sb.append("Date: ").append(new Date()).append("\r\n");
        sb.append("Content-Type: ").append(contentType).append("\r\n");
        sb.append("Content-Length: ").append(contentLength).append("\r\n");
        if (contentRange != null) {
            sb.append("Content-Range: ").append(contentRange).append("\r\n");
        }
        if (contentDisposition != null) {
            sb.append("Content-Disposition: ").append(contentDisposition).append("\r\n");
        }
        sb.append("Access-Control-Allow-Origin: *\r\n");
        sb.append("Connection: close\r\n");
        sb.append("\r\n");
        out.write(sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    private void sendOptions(OutputStream out) throws IOException {
        String sb = "HTTP/1.1 204 No Content\r\n"
                + "Server: LTM-WebClient-Endpoint/1.1\r\n"
                + "Allow: GET, HEAD, POST, OPTIONS\r\n"
                + "Access-Control-Allow-Origin: *\r\n"
                + "Access-Control-Allow-Methods: GET, HEAD, POST, OPTIONS\r\n"
                + "Connection: close\r\n\r\n";
        out.write(sb.getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) != -1) {
            if (b == '\r') {
                in.mark(1);
                int next = in.read();
                if (next != '\n' && next != -1) {
                    in.reset();
                }
                break;
            } else if (b == '\n') {
                break;
            } else {
                baos.write(b);
            }
        }
        if (b == -1 && baos.size() == 0) {
            return null;
        }
        return baos.toString(StandardCharsets.UTF_8.name());
    }

    public static File findRequestedFile(String fileRequested) {
        File[] roots = {
                WEB_ROOT,
                WEB_ROOT_SUB,
                new File("Web_Client"),
                new File("Web_Client", "web_root")
        };
        for (File root : roots) {
            File f = new File(root, fileRequested);
            if (f.exists() && !f.isDirectory()) {
                return f;
            }
            if (fileRequested.startsWith("web_root/")) {
                File fSub = new File(root, fileRequested.substring("web_root/".length()));
                if (fSub.exists() && !fSub.isDirectory()) {
                    return fSub;
                }
            }
        }
        return null;
    }

    private void streamFileRange(File file, long offset, long length, OutputStream out) throws IOException {
        byte[] buffer = new byte[8192];
        long remaining = length;
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            raf.seek(offset);
            while (remaining > 0) {
                int toRead = (int) Math.min(buffer.length, remaining);
                int read = raf.read(buffer, 0, toRead);
                if (read == -1) break;
                out.write(buffer, 0, read);
                remaining -= read;
            }
        }
    }

    public static String getContentType(String fileRequested) {
        String lower = fileRequested.toLowerCase();
        if (lower.endsWith(".htm") || lower.endsWith(".html")) {
            return "text/html; charset=UTF-8";
        } else if (lower.endsWith(".txt")) {
            return "text/plain; charset=UTF-8";
        } else if (lower.endsWith(".css")) {
            return "text/css; charset=UTF-8";
        } else if (lower.endsWith(".js")) {
            return "application/javascript; charset=UTF-8";
        } else if (lower.endsWith(".json")) {
            return "application/json; charset=UTF-8";
        } else if (lower.endsWith(".png")) {
            return "image/png";
        } else if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "image/jpeg";
        } else {
            return "application/octet-stream";
        }
    }

    private long fileNotFound(OutputStream dataOut, String fileRequested, boolean headOnly) throws IOException {
        String fallback = "<html><head><title>404 Not Found</title></head>"
                + "<body bgcolor='#F8F9FA' text='#202124' link='#1A73E8'>"
                + "<table width='80%' cellpadding='20' cellspacing='0' border='1' bordercolor='#DADCE0' bgcolor='#FFFFFF' align='center'>"
                + "<tr><td>"
                + "<font face='Arial' size='5' color='#D93025'><b>HTTP/1.1 404 Not Found</b></font><br><br>"
                + "<font face='Arial' size='3'>Không tìm thấy đường dẫn yêu cầu: <code>" + escapeHtml(fileRequested) + "</code></font><br><br>"
                + "<a href='/'><font face='Arial' size='2'><b>&larr; Quay về Trang chủ Google</b></font></a>"
                + "</td></tr></table></body></html>";
        byte[] fileData = fallback.getBytes(StandardCharsets.UTF_8);

        sendHeaders(dataOut, "HTTP/1.1 404 File Not Found", "text/html; charset=UTF-8", fileData.length, null, null);
        if (!headOnly) {
            dataOut.write(fileData, 0, fileData.length);
        }
        dataOut.flush();
        return headOnly ? 0 : fileData.length;
    }

    private long send501(OutputStream dataOut, String method) throws IOException {
        String html = "<html><body><h1>501 Not Implemented</h1><p>Phương thức " + escapeHtml(method) + " không được hỗ trợ.</p></body></html>";
        byte[] data = html.getBytes(StandardCharsets.UTF_8);

        sendHeaders(dataOut, "HTTP/1.1 501 Not Implemented", "text/html; charset=UTF-8", data.length, null, null);
        dataOut.write(data, 0, data.length);
        dataOut.flush();
        return data.length;
    }

    private String escapeHtml(String str) {
        if (str == null) return "";
        return str.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
