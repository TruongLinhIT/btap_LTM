package test;

import browser.HttpEngine;
import browser.HttpRequest;
import browser.HttpResponse;
import browser.HistoryManager;
import browser.UrlDnsInfo;
import http1.httpServer;
import java.io.File;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class BrowserServerTest {

    private static ServerSocket testServer;

    public static void main(String[] args) {
        System.out.println("=======================================================");
        System.out.println("BAT DAU KIEM TRA TOAN DIEN WEB SERVER VA WEB CLIENT");
        System.out.println("=======================================================");

        int passed = 0;
        int failed = 0;

        try {
            // 1. Khoi dong Server tren cong 8080 trong luong rieng
            testServer = new ServerSocket(8080);
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
            Thread.sleep(500);
            System.out.println("[OK] Server khoi dong thanh cong tren cong 8080");

            // Test 1: GET index.html
            try {
                HttpRequest req = new HttpRequest("http://localhost:8080/index.html", "GET");
                HttpResponse res = HttpEngine.execute(req);
                assertTest("GET index.html", res.getStatusCode() == 200 && res.getStringBody().contains("HE THONG WEB SERVER"));
                passed++;
            } catch (Exception e) {
                System.err.println("[FAIL] GET index.html: " + e.getMessage());
                failed++;
            }

            // Test 2: GET multimedia .pdf
            try {
                HttpRequest req = new HttpRequest("http://localhost:8080/sample.pdf", "GET");
                HttpResponse res = HttpEngine.execute(req);
                assertTest("GET sample.pdf (Service PDF)", res.getStatusCode() == 200 && res.getContentType().contains("pdf") && res.getBodyBytes().length > 0);
                passed++;
            } catch (Exception e) {
                System.err.println("[FAIL] GET sample.pdf: " + e.getMessage());
                failed++;
            }

            // Test 3: GET multimedia .mp3
            try {
                HttpRequest req = new HttpRequest("http://localhost:8080/sample.mp3", "GET");
                HttpResponse res = HttpEngine.execute(req);
                assertTest("GET sample.mp3 (Service MP3)", res.getStatusCode() == 200 && res.getContentType().contains("audio/mpeg") && res.getBodyBytes().length > 0);
                passed++;
            } catch (Exception e) {
                System.err.println("[FAIL] GET sample.mp3: " + e.getMessage());
                failed++;
            }

            // Test 4: GET multimedia .mp4
            try {
                HttpRequest req = new HttpRequest("http://localhost:8080/sample.mp4", "GET");
                HttpResponse res = HttpEngine.execute(req);
                assertTest("GET sample.mp4 (Service MP4)", res.getStatusCode() == 200 && res.getContentType().contains("video/mp4") && res.getBodyBytes().length > 0);
                passed++;
            } catch (Exception e) {
                System.err.println("[FAIL] GET sample.mp4: " + e.getMessage());
                failed++;
            }

            // Test 5: GET multimedia .png
            try {
                HttpRequest req = new HttpRequest("http://localhost:8080/sample.png", "GET");
                HttpResponse res = HttpEngine.execute(req);
                assertTest("GET sample.png (Service PNG)", res.getStatusCode() == 200 && res.getContentType().contains("image/png") && res.getBodyBytes().length > 0);
                passed++;
            } catch (Exception e) {
                System.err.println("[FAIL] GET sample.png: " + e.getMessage());
                failed++;
            }

            // Test 6: GET text file .txt
            try {
                HttpRequest req = new HttpRequest("http://localhost:8080/sample.txt", "GET");
                HttpResponse res = HttpEngine.execute(req);
                assertTest("GET sample.txt (Service TXT)", res.getStatusCode() == 200 && res.getStringBody().contains("SAMPLE.TXT"));
                passed++;
            } catch (Exception e) {
                System.err.println("[FAIL] GET sample.txt: " + e.getMessage());
                failed++;
            }

            // Test 7: HEAD method (Chi nhan Header, khong co body)
            try {
                HttpRequest req = new HttpRequest("http://localhost:8080/index.html", "HEAD");
                HttpResponse res = HttpEngine.execute(req);
                assertTest("HTTP HEAD index.html (Headers only)", res.getStatusCode() == 200 && res.getBodyBytes().length == 0 && res.getContentLength() > 0);
                passed++;
            } catch (Exception e) {
                System.err.println("[FAIL] HEAD index.html: " + e.getMessage());
                failed++;
            }

            // Test 8: POST method voi parameters
            try {
                HttpRequest req = new HttpRequest("http://localhost:8080/api/submit", "POST");
                req.setContentType("application/x-www-form-urlencoded");
                req.setBody("hoten=Nguyen+Van+A&masv=21IT001&noidung=TestPOST".getBytes(StandardCharsets.UTF_8));
                HttpResponse res = HttpEngine.execute(req);
                assertTest("HTTP POST /api/submit", res.getStatusCode() == 200 && res.getStringBody().contains("Nguyen Van A") && res.getStringBody().contains("21IT001"));
                passed++;
            } catch (Exception e) {
                System.err.println("[FAIL] POST /api/submit: " + e.getMessage());
                failed++;
            }

            // Test 9: 404 Not Found
            try {
                HttpRequest req = new HttpRequest("http://localhost:8080/non_existing_file.html", "GET");
                HttpResponse res = HttpEngine.execute(req);
                assertTest("HTTP 404 File Not Found", res.getStatusCode() == 404);
                passed++;
            } catch (Exception e) {
                System.err.println("[FAIL] 404 check: " + e.getMessage());
                failed++;
            }

            // Test 10: UrlDnsInfo (diachi + inet integration)
            try {
                UrlDnsInfo info = UrlDnsInfo.analyze("http://localhost:8080/sample.pdf?download=true#anchor");
                assertTest("URL Parsing & DNS Resolution", "localhost".equals(info.getHost()) && 8080 == info.getEffectivePort() && info.getPrimaryIp() != null);
                passed++;
            } catch (Exception e) {
                System.err.println("[FAIL] UrlDnsInfo: " + e.getMessage());
                failed++;
            }

            // Test 11: History Manager
            try {
                HistoryManager.add("http://localhost:8080/test", "Test Page", "GET");
                assertTest("History Manager Tracking", HistoryManager.search("localhost").size() > 0);
                passed++;
            } catch (Exception e) {
                System.err.println("[FAIL] HistoryManager: " + e.getMessage());
                failed++;
            }

            // Test 12: File Download to Disk
            try {
                File tempFile = File.createTempFile("ltm_test_download", ".pdf");
                tempFile.deleteOnExit();
                HttpEngine.downloadToFile("http://localhost:8080/sample.pdf", tempFile, null);
                assertTest("Download File to Disk", tempFile.exists() && tempFile.length() > 0);
                passed++;
            } catch (Exception e) {
                System.err.println("[FAIL] downloadToFile: " + e.getMessage());
                failed++;
            }

            testServer.close();

        } catch (Exception e) {
            System.err.println("Loi chung: " + e.getMessage());
        }

        System.out.println("=======================================================");
        System.out.println("KET QUA KIEM TRA: Passed: " + passed + " / " + (passed + failed) + " tests.");
        System.out.println("=======================================================");
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void assertTest(String testName, boolean condition) {
        if (condition) {
            System.out.println("[PASS] " + testName);
        } else {
            System.err.println("[FAIL] " + testName + " - Dieu kien khong thoa man!");
            throw new RuntimeException("Assertion failed for " + testName);
        }
    }
}
