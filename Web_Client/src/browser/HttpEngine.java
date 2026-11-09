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
 * HTTP Engine xu ly cac phuong thuc HTTP GET, HEAD, POST bang ca 2 co che:
 * 1. Raw Socket TCP (LTM Socket Programming truc tiep)
 * 2. HttpURLConnection (Ho tro day du HTTP/HTTPS)
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

    /**
     * Thuc thi yeu cau HTTP (GET, HEAD, POST)
     */
    public static HttpResponse execute(HttpRequest request) throws Exception {
        return execute(request, null);
    }

    public static HttpResponse execute(HttpRequest request, DownloadProgressListener progressListener) throws Exception {
        String urlStr = request.getUrl();
        if (!urlStr.startsWith("http://") && !urlStr.startsWith("https://")) {
            urlStr = "http://" + urlStr;
            request.setUrl(urlStr);
        }

        URL url = new URL(urlStr);
        String protocol = url.getProtocol().toLowerCase();

        // Neu la http thuong va bat che do Socket thi su dung Socket de trinh dien bai tap LTM
        if ("http".equals(protocol) && useRawSocketForHttp) {
            try {
                return executeWithSocket(request, progressListener);
            } catch (Exception ex) {
                System.out.println("Socket fallback sang HttpURLConnection: " + ex.getMessage());
                return executeWithHttpURLConnection(request, progressListener);
            }
        } else {
            return executeWithHttpURLConnection(request, progressListener);
        }
    }

    /**
     * Thuc thi HTTP qua TCP Socket thuan (Dac trung cua mon hoc Lap trinh Mang)
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
        if (url.getQuery() != null) {
            path += "?" + url.getQuery();
        }

        HttpResponse response = new HttpResponse();
        response.setRequestUrl(request.getUrl());
        response.setRequestMethod(request.getMethod());

        // Tra cuu DNS IP bang InetAddress (kien thuc bai inet.java)
        InetAddress inet = InetAddress.getByName(host);
        response.setResolvedIp(inet.getHostAddress());

        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(inet, port), 8000);
        socket.setSoTimeout(15000);

        OutputStream out = socket.getOutputStream();
        InputStream in = new BufferedInputStream(socket.getInputStream());

        // 1. Tao HTTP Request Header
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

        // Gui Request Header
        out.write(reqHeader.toString().getBytes(StandardCharsets.UTF_8));

        // Gui Request Body neu co (POST)
        if ("POST".equalsIgnoreCase(request.getMethod()) && body != null && body.length > 0) {
            out.write(body);
        }
        out.flush();

        // 2. Doc HTTP Response Status Line
        String statusLine = readLine(in);
        if (statusLine == null || statusLine.isEmpty()) {
            throw new Exception("Server khong tra ve du lieu");
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

        // 3. Doc cac HTTP Headers
        String line;
        long contentLength = -1;
        while ((line = readLine(in)) != null && !line.isEmpty()) {
            int colon = line.indexOf(":");
            if (colon > 0) {
                String k = line.substring(0, colon).trim();
                String v = line.substring(colon + 1).trim();
                response.addHeader(k, v);
                if ("content-length".equalsIgnoreCase(k)) {
                    try {
                        contentLength = Long.parseLong(v);
                    } catch (NumberFormatException ignored) {}
                }
            }
        }

        // 4. Neu la phuong thuc HEAD thi xong, KHONG doc Body!
        if ("HEAD".equalsIgnoreCase(request.getMethod())) {
            response.setResponseTimeMs(System.currentTimeMillis() - startTime);
            socket.close();
            return response;
        }

        // 5. Doc Body neu la GET hoac POST
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int bytesRead;
        long totalRead = 0;

        if (contentLength > 0) {
            while (totalRead < contentLength && (bytesRead = in.read(chunk, 0, (int) Math.min(chunk.length, contentLength - totalRead))) != -1) {
                buffer.write(chunk, 0, bytesRead);
                totalRead += bytesRead;
                if (progressListener != null) {
                    int pct = (int) ((totalRead * 100) / contentLength);
                    progressListener.onProgress(totalRead, contentLength, pct);
                }
            }
        } else {
            // Khong ro Content-Length -> doc den het luong (Connection close)
            while ((bytesRead = in.read(chunk)) != -1) {
                buffer.write(chunk, 0, bytesRead);
                totalRead += bytesRead;
                if (progressListener != null) {
                    progressListener.onProgress(totalRead, -1, -1);
                }
            }
        }

        response.setBodyBytes(buffer.toByteArray());
        response.setResponseTimeMs(System.currentTimeMillis() - startTime);
        socket.close();
        return response;
    }

    /**
     * Thuc thi HTTP qua HttpURLConnection (Ho tro https va redirect)
     */
    private static HttpResponse executeWithHttpURLConnection(HttpRequest request, DownloadProgressListener progressListener) throws Exception {
        long startTime = System.currentTimeMillis();
        URL url = new URL(request.getUrl());
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(20000);
        conn.setRequestMethod(request.getMethod());
        conn.setInstanceFollowRedirects(true);

        for (Map.Entry<String, String> entry : request.getHeaders().entrySet()) {
            conn.setRequestProperty(entry.getKey(), entry.getValue());
        }

        // DNS resolution
        HttpResponse response = new HttpResponse();
        response.setRequestUrl(request.getUrl());
        response.setRequestMethod(request.getMethod());
        try {
            InetAddress inet = InetAddress.getByName(url.getHost());
            response.setResolvedIp(inet.getHostAddress());
        } catch (Exception e) {
            response.setResolvedIp("Unknown");
        }

        // Gui du lieu POST neu co
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

        // Neu la HEAD thi khong doc body
        if ("HEAD".equalsIgnoreCase(request.getMethod())) {
            response.setResponseTimeMs(System.currentTimeMillis() - startTime);
            conn.disconnect();
            return response;
        }

        InputStream stream;
        if (statusCode >= 400) {
            stream = conn.getErrorStream();
            if (stream == null) {
                stream = conn.getInputStream();
            }
        } else {
            stream = conn.getInputStream();
        }

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
     * Tai file truc tiep ve o dia voi tien do ro rang
     */
    public static void downloadToFile(String fileUrl, File destinationFile, DownloadProgressListener listener) throws Exception {
        HttpRequest req = new HttpRequest(fileUrl, "GET");
        URL u = new URL(fileUrl);
        HttpURLConnection conn = (HttpURLConnection) u.openConnection();
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(30000);
        conn.setRequestProperty("User-Agent", "LTM-WebBrowser/1.0");

        int code = conn.getResponseCode();
        if (code >= 400) {
            throw new Exception("Server tra ve ma loi: " + code + " " + conn.getResponseMessage());
        }

        long totalSize = conn.getContentLengthLong();
        try (InputStream in = new BufferedInputStream(conn.getInputStream());
             FileOutputStream out = new FileOutputStream(destinationFile)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            long totalDownloaded = 0;

            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
                totalDownloaded += bytesRead;
                if (listener != null) {
                    int pct = totalSize > 0 ? (int) ((totalDownloaded * 100) / totalSize) : -1;
                    listener.onProgress(totalDownloaded, totalSize, pct);
                }
            }
            out.flush();
        } finally {
            conn.disconnect();
        }
    }

    private static String readLine(InputStream in) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) != -1) {
            if (b == '\r') {
                int next = in.read();
                if (next == '\n' || next == -1) {
                    break;
                } else {
                    baos.write(b);
                    baos.write(next);
                }
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
