package browser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * Quản lý Lịch sử duyệt web (History) của Web Client.
 */
public class HistoryManager {

    public static class HistoryEntry {
        private final String url;
        private final String title;
        private final String method;
        private final String timestamp;

        public HistoryEntry(String url, String title, String method, String timestamp) {
            this.url = url;
            this.title = title != null && !title.isEmpty() ? title : url;
            this.method = method != null ? method.toUpperCase() : "GET";
            this.timestamp = timestamp;
        }

        public String getUrl() {
            return url;
        }

        public String getTitle() {
            return title;
        }

        public String getMethod() {
            return method;
        }

        public String getTimestamp() {
            return timestamp;
        }
    }

    private static File getHistoryFile() {
        File sub = new File("Web_Client");
        if (sub.isDirectory() && new File(sub, "src").isDirectory()) {
            return new File(sub, "browser_history.txt");
        }
        return new File("browser_history.txt");
    }

    private static final List<HistoryEntry> entries = Collections.synchronizedList(new ArrayList<>());
    private static final SimpleDateFormat SDF = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    static {
        loadHistory();
    }

    public static synchronized void add(String url, String title, String method) {
        if (url == null || url.trim().isEmpty() || "about:blank".equalsIgnoreCase(url)) {
            return;
        }
        String timeStr = SDF.format(new Date());
        HistoryEntry entry = new HistoryEntry(url.trim(), title, method, timeStr);
        entries.add(0, entry);
        saveHistory();
    }

    public static synchronized List<HistoryEntry> getAll() {
        return new ArrayList<>(entries);
    }

    public static synchronized List<HistoryEntry> search(String keyword) {
        return search(keyword, "ALL");
    }

    public static synchronized List<HistoryEntry> search(String keyword, String methodFilter) {
        List<HistoryEntry> res = new ArrayList<>();
        String lower = keyword != null ? keyword.trim().toLowerCase() : "";
        String mFilter = methodFilter != null ? methodFilter.trim().toUpperCase() : "ALL";

        for (HistoryEntry e : entries) {
            boolean matchMethod = "ALL".equals(mFilter) || "TẤT CẢ".equals(mFilter) || e.getMethod().equalsIgnoreCase(mFilter);
            boolean matchKeyword = lower.isEmpty()
                    || e.getUrl().toLowerCase().contains(lower)
                    || e.getTitle().toLowerCase().contains(lower)
                    || e.getMethod().toLowerCase().contains(lower)
                    || e.getTimestamp().toLowerCase().contains(lower);
            if (matchMethod && matchKeyword) {
                res.add(e);
            }
        }
        return res;
    }

    public static synchronized void removeAt(int index) {
        if (index >= 0 && index < entries.size()) {
            entries.remove(index);
            saveHistory();
        }
    }

    public static synchronized void clear() {
        entries.clear();
        saveHistory();
    }

    private static void loadHistory() {
        File file = getHistoryFile();
        if (!file.exists()) return;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split("\\|", 4);
                if (parts.length == 4) {
                    entries.add(new HistoryEntry(parts[3], parts[2], parts[1], parts[0]));
                }
            }
        } catch (Exception ignored) {}
    }

    private static void saveHistory() {
        try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(getHistoryFile()), StandardCharsets.UTF_8))) {
            int limit = Math.min(entries.size(), 500);
            for (int i = 0; i < limit; i++) {
                HistoryEntry e = entries.get(i);
                String safeTitle = e.getTitle().replace("|", " ").replace("\n", " ").replace("\r", "");
                pw.println(e.getTimestamp() + "|" + e.getMethod() + "|" + safeTitle + "|" + e.getUrl());
            }
        } catch (Exception ignored) {}
    }
}
