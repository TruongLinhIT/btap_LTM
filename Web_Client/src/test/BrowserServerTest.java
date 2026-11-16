package test;

import browser.HistoryManager;
import browser.HttpEngine;
import browser.HttpRequest;
import browser.HttpResponse;
import browser.UrlDnsInfo;
import http1.httpServer;

import java.io.File;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Bộ kiểm thử tự động 8 tiêu chí cốt lõi của Trình duyệt Web Client (Chrome / Cốc Cốc):
 * 1. Giao thức HTTP/1.1 qua TCP Socket & GET Trang chủ Google
 * 2. Phương thức HTTP GET (Tìm kiếm Google /search?q=...)
 * 3. Phương thức HTTP HEAD (Chỉ lấy Response Headers, Body = 0 bytes)
 * 4. Phương thức HTTP POST (Form UrlEncoded UTF-8)
 * 5. Phương thức HTTP POST (JSON Payload UTF-8)
 * 6. Xử lý mã trạng thái lỗi HTTP 404 Not Found
 * 7. Quản lý Lịch sử duyệt web (HistoryManager)
 * 8. Quản lý Tải xuống (Download qua HTTP/1.1 Socket)
 */
public class BrowserServerTest {

    private static ServerSocket testServer;

    public static void main(String[] args) {
        System.out.println("=======================================================");
        System.out.println("BAT DAU KIEM TRA TRINH DUYET WEB CLIENT (HTTP/1.1)");
        System.out.println("=======================================================");

        int passed = 0;
        int failed = 0;

        try {
            testServer = new ServerSocket(0);
            final int port = testServer.getLocalPort();
            final String base = "http://localhost:" + port;

            Thread serverThread = new Thread(() -> {
                try {
                    while (!testServer.isClosed()) {
                        Socket client = testServer.accept();
                        new Thread(new httpServer(client)).start();
                    }
                } catch (Exception ignored) {}
            });
            serverThread.setDaemon(true);
            serverThread.start();
            Thread.sleep(150);
            System.out.println("[OK] HTTP/1.1 Endpoint dang chay tren cong " + port);

            // 1. HTTP GET Trang chủ Google
            if (runCase("1. Giao thuc HTTP/1.1 & GET Trang chu Google (/index.html)", () -> {
                HttpResponse r = HttpEngine.execute(new HttpRequest(base + "/index.html", "GET"));
                return r.getStatusCode() == 200 && r.isHtml() && r.getStringBody().contains("Google");
            })) passed++; else failed++;

            // 2. HTTP GET Tìm kiếm Google (/search?q=...)
            if (runCase("2. Phuong thuc HTTP GET Tim kiem Google (/search?q=Lap+Trinh+Mang)", () -> {
                HttpResponse r = HttpEngine.execute(new HttpRequest(base + "/search?q=Lap+Trinh+Mang", "GET"));
                return r.getStatusCode() == 200 && r.getStringBody().contains("Lap Trinh Mang");
            })) passed++; else failed++;

            // 3. HTTP HEAD
            if (runCase("3. Phuong thuc HTTP HEAD (Chi lay Response Headers, Body = 0 bytes)", () -> {
                HttpResponse r = HttpEngine.execute(new HttpRequest(base + "/about_http.html", "HEAD"));
                return r.getStatusCode() == 200 && r.getContentType().contains("text/html")
                        && r.getContentLength() > 0 && r.getBodyBytes().length == 0;
            })) passed++; else failed++;

            // 4. HTTP POST Form UTF-8
            if (runCase("4. Phuong thuc HTTP POST (Form UrlEncoded UTF-8)", () -> {
                HttpRequest req = new HttpRequest(base + "/api/submit", "POST");
                req.setContentType("application/x-www-form-urlencoded");
                req.setBody("hoten=Nguy%E1%BB%85n+V%C4%83n+A&masv=21IT001".getBytes(StandardCharsets.UTF_8));
                HttpResponse r = HttpEngine.execute(req);
                return r.getStatusCode() == 200 && r.getStringBody().contains("Nguyễn Văn A")
                        && r.getStringBody().contains("21IT001");
            })) passed++; else failed++;

            // 5. HTTP POST JSON UTF-8
            if (runCase("5. Phuong thuc HTTP POST (JSON Payload UTF-8)", () -> {
                HttpRequest req = new HttpRequest(base + "/api/json", "POST");
                req.setContentType("application/json; charset=UTF-8");
                req.setBody("{\"hoten\":\"Trần Bình\",\"diem\":9.5}".getBytes(StandardCharsets.UTF_8));
                HttpResponse r = HttpEngine.execute(req);
                return r.getStatusCode() == 200 && r.getStringBody().contains("Trần Bình");
            })) passed++; else failed++;

            // 6. Xử lý mã lỗi 404 & Phân giải DNS IP
            if (runCase("6. Xu ly ma loi HTTP 404 Not Found & Phan giai DNS IP", () -> {
                UrlDnsInfo dns = UrlDnsInfo.analyze(base + "/about_http.html");
                HttpResponse r404 = HttpEngine.execute(new HttpRequest(base + "/khong_ton_tai.html", "GET"));
                return "localhost".equals(dns.getHost()) && dns.getPrimaryIp() != null && r404.getStatusCode() == 404;
            })) passed++; else failed++;

            // 7. History Manager
            if (runCase("7. Quan ly Lich su duyet web (History: Luu vet, tim kiem & loc)", () -> {
                HistoryManager.add(base + "/search?q=test_history", "Test History Google", "GET");
                return !HistoryManager.search("test_history", "GET").isEmpty();
            })) passed++; else failed++;

            // 8. Download Manager
            if (runCase("8. Quan ly Tai xuong (Download qua HTTP/1.1 Socket ve o dia)", () -> {
                File temp = File.createTempFile("ltm_test_dl_", ".html");
                temp.deleteOnExit();
                HttpEngine.downloadToFile(base + "/about_http.html", temp, null);
                return temp.exists() && temp.length() > 0;
            })) passed++; else failed++;

            testServer.close();
        } catch (Exception e) {
            System.out.println("[LOI HE THONG] " + e.getMessage());
            e.printStackTrace();
            failed++;
        }

        System.out.println("\n=======================================================");
        System.out.println("KET QUA KIEM TRA: DAT " + passed + " / " + (passed + failed) + " TIEU CHI");
        System.out.println("=======================================================");
        if (failed > 0) {
            System.exit(1);
        }
    }

    private interface Check {
        boolean run() throws Exception;
    }

    private static boolean runCase(String name, Check check) {
        try {
            boolean ok = check.run();
            System.out.println((ok ? "  [PASS] " : "  [FAIL] ") + name);
            return ok;
        } catch (Exception e) {
            System.out.println("  [FAIL] " + name + " -> " + e.getMessage());
            return false;
        }
    }
}
