package http1;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;

public class httpServer implements Runnable {

    static final File WEB_ROOT = new File(".");
    static final File WEB_ROOT_SUB = new File("web_root");
    static final String DEFAULT_FILE = "index.html";
    static final String FILE_NOT_FOUND = "404.html";
    static final int PORT = 8080;
    static final boolean verbose = true;
    private Socket connect;

    public httpServer(Socket c) {
        connect = c;
    }

    public static void main(String[] args) {
        try {
            ServerSocket serverConnect = new ServerSocket(PORT);
            System.out.println("=================================================");
            System.out.println(" HTTP Web Server da khoi dong thanh cong!");
            System.out.println(" Lang nghe tren cong: " + PORT);
            System.out.println(" URL truy cap: http://localhost:" + PORT + "/");
            System.out.println(" Ho tro giao thuc: GET, HEAD, POST");
            System.out.println(" Dich vu: .pdf, .mp3, .mp4, .png, .wav, .txt, .html");
            System.out.println("=================================================\n");

            while (true) {
                httpServer myserver = new httpServer(serverConnect.accept());
                if (verbose) {
                    System.out.println("Ket noi moi mo (" + new Date() + ")");
                }
                Thread thread = new Thread(myserver);
                thread.start();
            }
        } catch (IOException e) {
            System.err.println("Loi ket noi Server: " + e.getMessage());
        }
    }

    @Override
    public void run() {
        BufferedReader in = null;
        PrintWriter out = null;
        BufferedOutputStream dataOut = null;
        String fileRequested = null;

        try {
            in = new BufferedReader(new InputStreamReader(connect.getInputStream(), StandardCharsets.UTF_8));
            out = new PrintWriter(connect.getOutputStream());
            dataOut = new BufferedOutputStream(connect.getOutputStream());

            String input = in.readLine();
            if (input == null || input.trim().isEmpty()) {
                return;
            }

            StringTokenizer parse = new StringTokenizer(input);
            String method = parse.nextToken().toUpperCase();
            fileRequested = parse.nextToken();

            // Doc toan bo headers
            Map<String, String> headers = new HashMap<>();
            String headerLine;
            int contentLength = 0;
            while ((headerLine = in.readLine()) != null && !headerLine.isEmpty()) {
                int colonIdx = headerLine.indexOf(":");
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

            // Kiem tra phuong thuc duoc ho tro (GET, HEAD, POST)
            if (!method.equals("GET") && !method.equals("HEAD") && !method.equals("POST")) {
                if (verbose) {
                    System.out.println("501 Not Implemented: Phuong thuc " + method + " khong ho tro.");
                }
                send501(out, dataOut, method);
                return;
            }

            // Xu ly POST request
            if (method.equals("POST")) {
                handlePostRequest(in, out, dataOut, fileRequested, contentLength);
                return;
            }

            // Xu ly GET va HEAD
            // Chuan hoa duong dan file
            if (fileRequested.endsWith("/")) {
                fileRequested += DEFAULT_FILE;
            }
            if (fileRequested.startsWith("/")) {
                fileRequested = fileRequested.substring(1);
            }
            if (fileRequested.isEmpty()) {
                fileRequested = DEFAULT_FILE;
            }

            File file = findRequestedFile(fileRequested);
            if (file == null || !file.exists() || file.isDirectory()) {
                fileNotFound(out, dataOut, fileRequested);
                return;
            }

            int fileLength = (int) file.length();
            String content = getContentType(file.getName());

            // Gui Response Header
            out.println("HTTP/1.1 200 OK");
            out.println("Server: Java HTTP Server / LTM Web Server 1.0");
            out.println("Date: " + new Date());
            out.println("Content-Type: " + content);
            out.println("Content-Length: " + fileLength);
            out.println("Accept-Ranges: bytes");
            out.println("Connection: close");
            out.println();
            out.flush();

            // Neu la GET thi gui du lieu noi dung file, neu HEAD thi chi gui header
            if (method.equals("GET")) {
                byte[] fileData = readFileData(file, fileLength);
                dataOut.write(fileData, 0, fileLength);
                dataOut.flush();
            }

            if (verbose) {
                System.out.println("[" + method + "] Tra ve file: " + file.getName() + " (" + content + ", " + fileLength + " bytes)");
            }

        } catch (FileNotFoundException fnfe) {
            try {
                fileNotFound(out, dataOut, fileRequested);
            } catch (IOException ioe) {
                System.err.println("Loi gui 404: " + ioe.getMessage());
            }
        } catch (IOException ioe) {
            System.err.println("Loi Server: " + ioe.getMessage());
        } finally {
            try {
                if (in != null) in.close();
                if (out != null) out.close();
                if (dataOut != null) dataOut.close();
                if (connect != null) connect.close();
            } catch (Exception e) {
                System.err.println("Loi dong ket noi: " + e.getMessage());
            }
            if (verbose) {
                System.out.println("Dong ket noi client.\n");
            }
        }
    }

    private void handlePostRequest(BufferedReader in, PrintWriter out, BufferedOutputStream dataOut,
                                   String path, int contentLength) throws IOException {
        StringBuilder postData = new StringBuilder();
        if (contentLength > 0) {
            char[] buffer = new char[1024];
            int read = 0;
            int totalRead = 0;
            while (totalRead < contentLength && (read = in.read(buffer, 0, Math.min(buffer.length, contentLength - totalRead))) != -1) {
                postData.append(buffer, 0, read);
                totalRead += read;
            }
        }

        String rawBody = postData.toString();
        if (verbose) {
            System.out.println("[POST] Yeu cau den duong dan: " + path);
            System.out.println("[POST] Body nhan duoc: " + rawBody);
        }

        // Phan tich cac tham so POST (neu co dang key=value&key2=value2)
        StringBuilder tableRows = new StringBuilder();
        if (!rawBody.isEmpty()) {
            String[] pairs = rawBody.split("&");
            for (String pair : pairs) {
                String[] kv = pair.split("=", 2);
                String k = kv[0];
                String v = kv.length > 1 ? kv[1] : "";
                try {
                    k = URLDecoder.decode(k, StandardCharsets.UTF_8.name());
                    v = URLDecoder.decode(v, StandardCharsets.UTF_8.name());
                } catch (Exception ignored) {}
                tableRows.append("<tr><td style='border:1px solid #ddd;padding:8px;font-weight:bold;'>")
                        .append(escapeHtml(k))
                        .append("</td><td style='border:1px solid #ddd;padding:8px;'>")
                        .append(escapeHtml(v))
                        .append("</td></tr>");
            }
        } else {
            tableRows.append("<tr><td colspan='2' style='border:1px solid #ddd;padding:8px;text-align:center;'>Khong co body du lieu</td></tr>");
        }

        String responseHtml = "<!DOCTYPE html>\n" +
                "<html lang=\"vi\">\n" +
                "<head><meta charset=\"UTF-8\"><title>Ket Qua POST - Java Web Server</title>\n" +
                "<style>body{font-family:Arial,sans-serif;padding:30px;background:#f8f9fa;}" +
                ".box{background:white;padding:25px;border-radius:8px;max-width:650px;margin:auto;box-shadow:0 2px 8px rgba(0,0,0,0.1);}" +
                "h2{color:#1a73e8;margin-top:0;}table{width:100%;border-collapse:collapse;margin:15px 0;}" +
                "a{color:#1a73e8;text-decoration:none;font-weight:bold;}</style></head>\n" +
                "<body>\n" +
                "<div class=\"box\">\n" +
                "<h2> Xu ly POST thanh cong!</h2>\n" +
                "<p><b>Server:</b> Java HTTP/1.1 Multi-threaded Server</p>\n" +
                "<p><b>Duong dan (Endpoint):</b> " + escapeHtml(path) + "</p>\n" +
                "<p><b>Du lieu Body Raw:</b> <code>" + escapeHtml(rawBody) + "</code></p>\n" +
                "<h3>Cac truong du lieu da phan tich:</h3>\n" +
                "<table><tr><th style='border:1px solid #ddd;padding:8px;background:#e8f0fe;'>Truong (Field)</th>" +
                "<th style='border:1px solid #ddd;padding:8px;background:#e8f0fe;'>Gia tri (Value)</th></tr>\n" +
                tableRows.toString() +
                "</table>\n" +
                "<p style='margin-top:20px;'><a href=\"/\">&larr; Quay ve Trang Chu</a></p>\n" +
                "</div>\n" +
                "</body></html>";

        byte[] bodyBytes = responseHtml.getBytes(StandardCharsets.UTF_8);

        out.println("HTTP/1.1 200 OK");
        out.println("Server: Java HTTP Server / LTM Web Server 1.0");
        out.println("Date: " + new Date());
        out.println("Content-Type: text/html; charset=UTF-8");
        out.println("Content-Length: " + bodyBytes.length);
        out.println("Connection: close");
        out.println();
        out.flush();

        dataOut.write(bodyBytes, 0, bodyBytes.length);
        dataOut.flush();
    }

    private File findRequestedFile(String fileRequested) {
        // Thu tim o thu muc hien tai
        File f1 = new File(WEB_ROOT, fileRequested);
        if (f1.exists() && !f1.isDirectory()) {
            return f1;
        }
        // Thu tim trong thu muc web_root/
        File f2 = new File(WEB_ROOT_SUB, fileRequested);
        if (f2.exists() && !f2.isDirectory()) {
            return f2;
        }
        // Neu fileRequested co tien to web_root/
        if (fileRequested.startsWith("web_root/")) {
            File f3 = new File(WEB_ROOT, fileRequested.substring("web_root/".length()));
            if (f3.exists() && !f3.isDirectory()) {
                return f3;
            }
        }
        return null;
    }

    private byte[] readFileData(File file, int filelength) throws IOException {
        FileInputStream fileIn = null;
        byte[] fileData = new byte[filelength];
        try {
            fileIn = new FileInputStream(file);
            int read = 0;
            int totalRead = 0;
            while (totalRead < filelength && (read = fileIn.read(fileData, totalRead, filelength - totalRead)) != -1) {
                totalRead += read;
            }
        } finally {
            if (fileIn != null) {
                fileIn.close();
            }
        }
        return fileData;
    }

    private String getContentType(String fileRequested) {
        String lower = fileRequested.toLowerCase();
        if (lower.endsWith(".htm") || lower.endsWith(".html")) {
            return "text/html; charset=UTF-8";
        } else if (lower.endsWith(".txt")) {
            return "text/plain; charset=UTF-8";
        } else if (lower.endsWith(".css")) {
            return "text/css";
        } else if (lower.endsWith(".js")) {
            return "application/javascript";
        } else if (lower.endsWith(".json")) {
            return "application/json";
        } else if (lower.endsWith(".pdf")) {
            return "application/pdf";
        } else if (lower.endsWith(".mp3")) {
            return "audio/mpeg";
        } else if (lower.endsWith(".wav")) {
            return "audio/wav";
        } else if (lower.endsWith(".mp4")) {
            return "video/mp4";
        } else if (lower.endsWith(".png")) {
            return "image/png";
        } else if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "image/jpeg";
        } else if (lower.endsWith(".gif")) {
            return "image/gif";
        } else {
            return "application/octet-stream";
        }
    }

    private void fileNotFound(PrintWriter out, OutputStream dataOut, String fileRequested) throws IOException {
        File file = findRequestedFile(FILE_NOT_FOUND);
        byte[] fileData;
        int filelength;
        if (file != null && file.exists()) {
            filelength = (int) file.length();
            fileData = readFileData(file, filelength);
        } else {
            String fallback = "<html><body><h1>404 Not Found</h1><p>File not found: " + escapeHtml(fileRequested) + "</p></body></html>";
            fileData = fallback.getBytes(StandardCharsets.UTF_8);
            filelength = fileData.length;
        }

        out.println("HTTP/1.1 404 File Not Found");
        out.println("Server: Java HTTP Server / LTM Web Server 1.0");
        out.println("Date: " + new Date());
        out.println("Content-Type: text/html; charset=UTF-8");
        out.println("Content-Length: " + filelength);
        out.println("Connection: close");
        out.println();
        out.flush();

        dataOut.write(fileData, 0, filelength);
        dataOut.flush();

        if (verbose) {
            System.out.println("File " + fileRequested + " Not Found (404)");
        }
    }

    private void send501(PrintWriter out, OutputStream dataOut, String method) throws IOException {
        String html = "<html><body><h1>501 Not Implemented</h1><p>Phuong thuc " + escapeHtml(method) + " khong duoc ho tro.</p></body></html>";
        byte[] data = html.getBytes(StandardCharsets.UTF_8);

        out.println("HTTP/1.1 501 Not Implemented");
        out.println("Server: Java HTTP Server / LTM Web Server 1.0");
        out.println("Date: " + new Date());
        out.println("Content-Type: text/html; charset=UTF-8");
        out.println("Content-Length: " + data.length);
        out.println("Connection: close");
        out.println();
        out.flush();

        dataOut.write(data, 0, data.length);
        dataOut.flush();
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
