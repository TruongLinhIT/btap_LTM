package browser;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Lõi giao thức HTTP/1.1 của Web Client:
 * - Cài đặt trực tiếp trên TCP Socket (java.net.Socket) cho các phương thức GET, HEAD, POST và Download.
 * - Hỗ trợ giải mã Content-Length, Transfer-Encoding: chunked, và tự động chuyển hướng (301/302).
 * - Hỗ trợ fallback sang HttpURLConnection đối với HTTPS.
 */
public class HttpEngine {

    public interface DownloadProgressListener {
        void onProgress(long bytesRead, long totalBytes, int percent);
    }

    private static boolean useRawSocketForHttp = true;

    public static boolean isUseRawSocketForHttp() {
        return useRawSocketForHttp;
    }

    public static void setUseRawSocketForHttp(boolean useRaw) {
        useRawSocketForHttp = useRaw;
    }

    public static HttpResponse execute(HttpRequest request) throws Exception {
        return execute(request, null);
    }

    public static HttpResponse execute(HttpRequest request, DownloadProgressListener progressListener) throws Exception {
        return executeInternal(request, progressListener, 0);
    }

    private static HttpResponse executeInternal(HttpRequest request, DownloadProgressListener progressListener, int redirectCount) throws Exception {
        if (redirectCount > 5) {
            throw new Exception("Quá nhiều lần chuyển hướng (Redirect > 5)");
        }

        String urlStr = request.getUrl();
        if (!urlStr.startsWith("http://") && !urlStr.startsWith("https://")) {
            urlStr = "http://" + urlStr;
        }
        // Tự động ánh xạ localhost:8080 sang cổng nội bộ đang chạy của WebBrowser để tránh xung đột với dịch vụ khác trên máy
        int activePort = WebBrowser.getServerPort();
        if (activePort > 0 && activePort != 8080) {
            urlStr = urlStr.replace("http://localhost:8080", "http://localhost:" + activePort)
                           .replace("http://127.0.0.1:8080", "http://127.0.0.1:" + activePort);
        }
        request.setUrl(urlStr);

        URL url = new URL(urlStr);
        String protocol = url.getProtocol().toLowerCase();

        if ("http".equals(protocol) && useRawSocketForHttp) {
            try {
                HttpResponse res = executeWithSocket(request, progressListener);
                int code = res.getStatusCode();
                if ((code == 301 || code == 302 || code == 303 || code == 307 || code == 308)) {
                    String location = res.getHeaders().get("Location");
                    if (location == null) location = res.getHeaders().get("location");
                    if (location != null && !location.isEmpty()) {
                        URL nextUrl = new URL(url, location);
                        HttpRequest redirected = new HttpRequest(nextUrl.toString(),
                                code == 303 ? "GET" : request.getMethod());
                        return executeInternal(redirected, progressListener, redirectCount + 1);
                    }
                }
                return res;
            } catch (Exception ex) {
                if ("localhost".equalsIgnoreCase(url.getHost()) || "127.0.0.1".equals(url.getHost())) {
                    throw ex;
                }
                return executeWithHttpURLConnection(request, progressListener);
            }
        } else {
            return executeWithHttpURLConnection(request, progressListener);
        }
    }

    /**
     * Thực thi giao thức HTTP/1.1 trực tiếp qua TCP Socket (GET, HEAD, POST).
     */
    private static HttpResponse executeWithSocket(HttpRequest request, DownloadProgressListener progressListener) throws Exception {
        long startTime = System.currentTimeMillis();
        URL url = new URL(request.getUrl());
        String host = url.getHost();
        int port = url.getPort() != -1 ? url.getPort() : 80;
        String path = url.getPath();
        if (path == null || path.isEmpty()) {
            path = "/";
        }
        if (url.getQuery() != null && !url.getQuery().isEmpty()) {
            path += "?" + url.getQuery();
        }

        HttpResponse response = new HttpResponse();
        response.setRequestUrl(request.getUrl());
        response.setRequestMethod(request.getMethod());
        response.setProtocolEngine("HTTP/1.1 TCP Socket");

        InetAddress inet = InetAddress.getByName(host);
        response.setResolvedIp(inet.getHostAddress());

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(inet, port), 8000);
            socket.setSoTimeout(15000);

            OutputStream out = socket.getOutputStream();
            InputStream in = new BufferedInputStream(socket.getInputStream());

            // 1. Đóng gói bản tin HTTP Request theo chuẩn RFC 2616 / RFC 7230
            StringBuilder reqHeader = new StringBuilder();
            reqHeader.append(request.getMethod()).append(" ").append(path).append(" HTTP/1.1\r\n");
            reqHeader.append("Host: ").append(host);
            if (port != 80 && port != 443) {
                reqHeader.append(":").append(port);
            }
            reqHeader.append("\r\n");

            for (Map.Entry<String, String> entry : request.getHeaders().entrySet()) {
                reqHeader.append(entry.getKey()).append(": ").append(entry.getValue()).append("\r\n");
            }

            byte[] body = request.getBody();
            if ("POST".equalsIgnoreCase(request.getMethod())) {
                int bodyLen = body != null ? body.length : 0;
                reqHeader.append("Content-Length: ").append(bodyLen).append("\r\n");
            }
            reqHeader.append("\r\n");

            StringBuilder rawReqDisplay = new StringBuilder(reqHeader);
            if ("POST".equalsIgnoreCase(request.getMethod()) && body != null && body.length > 0) {
                rawReqDisplay.append(new String(body, StandardCharsets.UTF_8));
            }
            response.setRawRequestText(rawReqDisplay.toString());

            // Gửi HTTP Request Header & Body qua TCP Socket
            out.write(reqHeader.toString().getBytes(StandardCharsets.UTF_8));
            if ("POST".equalsIgnoreCase(request.getMethod()) && body != null && body.length > 0) {
                out.write(body);
            }
            out.flush();

            // 2. Đọc dòng trạng thái HTTP Response (Status-Line)
            String statusLine = readLine(in);
            if (statusLine == null || statusLine.isEmpty()) {
                throw new Exception("Server không trả về phản hồi HTTP hợp lệ.");
            }

            String[] statusParts = statusLine.split(" ", 3);
            if (statusParts.length >= 2) {
                try {
                    response.setStatusCode(Integer.parseInt(statusParts[1]));
                } catch (NumberFormatException e) {
                    response.setStatusCode(200);
                }
                response.setStatusMessage(statusParts.length >= 3 ? statusParts[2] : "");
            }

            // 3. Đọc toàn bộ HTTP Response Headers
            String line;
            boolean isChunked = false;
            while ((line = readLine(in)) != null && !line.isEmpty()) {
                int idx = line.indexOf(':');
                if (idx > 0) {
                    String key = line.substring(0, idx).trim();
                    String val = line.substring(idx + 1).trim();
                    response.addHeader(key, val);
                    if ("transfer-encoding".equalsIgnoreCase(key) && val.toLowerCase().contains("chunked")) {
                        isChunked = true;
                    }
                }
            }

            // 4. Phương thức HEAD chỉ nhận Header, không đọc Body
            if ("HEAD".equalsIgnoreCase(request.getMethod())) {
                response.setBodyBytes(new byte[0]);
                response.setResponseTimeMs(System.currentTimeMillis() - startTime);
                return response;
            }

            // 5. Đọc HTTP Response Body (theo Chunked, Content-Length hoặc đến khi đóng kết nối)
            ByteArrayOutputStream bodyBaos = new ByteArrayOutputStream();
            long expectedLength = response.getContentLength();

            if (isChunked) {
                while (true) {
                    String chunkLenStr = readLine(in);
                    if (chunkLenStr == null) break;
                    int semi = chunkLenStr.indexOf(';');
                    if (semi > 0) chunkLenStr = chunkLenStr.substring(0, semi);
                    int chunkLen;
                    try {
                        chunkLen = Integer.parseInt(chunkLenStr.trim(), 16);
                    } catch (NumberFormatException e) {
                        break;
                    }
                    if (chunkLen <= 0) {
                        readLine(in);
                        break;
                    }
                    byte[] buf = new byte[4096];
                    int rem = chunkLen;
                    while (rem > 0) {
                        int r = in.read(buf, 0, Math.min(buf.length, rem));
                        if (r == -1) break;
                        bodyBaos.write(buf, 0, r);
                        rem -= r;
                    }
                    readLine(in);
                }
            } else if (expectedLength > 0) {
                byte[] buffer = new byte[8192];
                long totalRead = 0;
                while (totalRead < expectedLength) {
                    int toRead = (int) Math.min(buffer.length, expectedLength - totalRead);
                    int read = in.read(buffer, 0, toRead);
                    if (read == -1) break;
                    bodyBaos.write(buffer, 0, read);
                    totalRead += read;
                    if (progressListener != null) {
                        int pct = (int) ((totalRead * 100) / expectedLength);
                        progressListener.onProgress(totalRead, expectedLength, pct);
                    }
                }
            } else if (expectedLength != 0) {
                byte[] buffer = new byte[8192];
                int read;
                long totalRead = 0;
                while ((read = in.read(buffer)) != -1) {
                    bodyBaos.write(buffer, 0, read);
                    totalRead += read;
                    if (progressListener != null) {
                        progressListener.onProgress(totalRead, -1, -1);
                    }
                }
            }

            response.setBodyBytes(bodyBaos.toByteArray());
            response.setResponseTimeMs(System.currentTimeMillis() - startTime);
            return response;
        }
    }

    private static HttpResponse executeWithHttpURLConnection(HttpRequest request, DownloadProgressListener progressListener) throws Exception {
        long startTime = System.currentTimeMillis();
        URL url = new URL(request.getUrl());
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod(request.getMethod());
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(15000);
        conn.setInstanceFollowRedirects(true);

        StringBuilder rawReq = new StringBuilder();
        String path = url.getPath() != null && !url.getPath().isEmpty() ? url.getPath() : "/";
        if (url.getQuery() != null) path += "?" + url.getQuery();
        rawReq.append(request.getMethod()).append(" ").append(path).append(" HTTP/1.1\r\n");
        rawReq.append("Host: ").append(url.getHost()).append("\r\n");

        for (Map.Entry<String, String> entry : request.getHeaders().entrySet()) {
            conn.setRequestProperty(entry.getKey(), entry.getValue());
            rawReq.append(entry.getKey()).append(": ").append(entry.getValue()).append("\r\n");
        }
        rawReq.append("\r\n");

        HttpResponse response = new HttpResponse();
        response.setRequestUrl(request.getUrl());
        response.setRequestMethod(request.getMethod());
        response.setProtocolEngine(url.getProtocol().toUpperCase() + " Connection");
        response.setRawRequestText(rawReq.toString());

        try {
            InetAddress inet = InetAddress.getByName(url.getHost());
            response.setResolvedIp(inet.getHostAddress());
        } catch (Exception e) {
            response.setResolvedIp("Unknown");
        }

        if ("POST".equalsIgnoreCase(request.getMethod())) {
            conn.setDoOutput(true);
            byte[] body = request.getBody();
            if (body != null && body.length > 0) {
                conn.setRequestProperty("Content-Length", String.valueOf(body.length));
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body);
                    os.flush();
                }
            }
        }

        int statusCode;
        try {
            statusCode = conn.getResponseCode();
        } catch (Exception e) {
            statusCode = 500;
        }
        response.setStatusCode(statusCode);
        response.setStatusMessage(conn.getResponseMessage());

        for (Map.Entry<String, List<String>> header : conn.getHeaderFields().entrySet()) {
            if (header.getKey() != null && !header.getValue().isEmpty()) {
                response.addHeader(header.getKey(), header.getValue().get(0));
            }
        }

        if ("HEAD".equalsIgnoreCase(request.getMethod())) {
            response.setResponseTimeMs(System.currentTimeMillis() - startTime);
            conn.disconnect();
            return response;
        }

        InputStream stream = statusCode >= 400 ? conn.getErrorStream() : conn.getInputStream();
        if (stream != null) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int read;
            long totalRead = 0;
            long totalBytes = conn.getContentLengthLong();

            while ((read = stream.read(chunk)) != -1) {
                buffer.write(chunk, 0, read);
                totalRead += read;
                if (progressListener != null) {
                    int pct = totalBytes > 0 ? (int) ((totalRead * 100) / totalBytes) : -1;
                    progressListener.onProgress(totalRead, totalBytes, pct);
                }
            }
            stream.close();
            response.setBodyBytes(buffer.toByteArray());
        }

        response.setResponseTimeMs(System.currentTimeMillis() - startTime);
        conn.disconnect();
        return response;
    }

    /**
     * Tải tài nguyên / trang web về ổ cứng qua giao thức HTTP/1.1 (TCP Socket trực tiếp).
     */
    public static void downloadToFile(String fileUrl, File destinationFile, DownloadProgressListener listener) throws Exception {
        if (!fileUrl.startsWith("http://") && !fileUrl.startsWith("https://")) {
            fileUrl = "http://" + fileUrl;
        }
        HttpRequest req = new HttpRequest(fileUrl, "GET");
        HttpResponse res = execute(req, listener);
        if (res.getStatusCode() >= 400) {
            throw new Exception("HTTP " + res.getStatusCode() + " " + res.getStatusMessage());
        }
        byte[] data = res.getBodyBytes();
        if (destinationFile.getParentFile() != null && !destinationFile.getParentFile().exists()) {
            destinationFile.getParentFile().mkdirs();
        }
        try (FileOutputStream fos = new FileOutputStream(destinationFile)) {
            fos.write(data);
            fos.flush();
        }
        if (listener != null) {
            listener.onProgress(data.length, data.length, 100);
        }
    }

    private static String readLine(InputStream in) throws Exception {
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
}
