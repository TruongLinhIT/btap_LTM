package browser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

public class HistoryManager {

    public static class HistoryEntry {
        private String url;
        private String title;
        private String method;
        private String timestamp;

        public HistoryEntry(String url, String title, String method, String timestamp) {
            this.url = url;
            this.title = title != null && !title.isEmpty() ? title : url;
            this.method = method != null ? method : "GET";
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

    private static final String HISTORY_FILE = "browser_history.txt";
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
        HistoryEntry entry = new HistoryEntry(url, title, method, timeStr);
        entries.add(0, entry);
        saveHistory();
    }

    public static synchronized List<HistoryEntry> getAll() {
        return new ArrayList<>(entries);
    }

    public static synchronized List<HistoryEntry> search(String keyword) {
        List<HistoryEntry> result = new ArrayList<>();
        if (keyword == null || keyword.trim().isEmpty()) {
            return getAll();
        }
        String lower = keyword.toLowerCase().trim();
        for (HistoryEntry e : entries) {
            if (e.getUrl().toLowerCase().contains(lower) || e.getTitle().toLowerCase().contains(lower)) {
                result.add(e);
            }
        }
        return result;
    }

    public static synchronized void clear() {
        entries.clear();
        saveHistory();
    }

    private static synchronized void saveHistory() {
        try (PrintWriter pw = new PrintWriter(new FileWriter(HISTORY_FILE))) {
            for (HistoryEntry e : entries) {
                pw.println(e.getTimestamp() + "\t" + e.getMethod() + "\t" + e.getTitle().replace("\t", " ") + "\t" + e.getUrl());
            }
        } catch (Exception ignored) {}
    }

    private static synchronized void loadHistory() {
        File f = new File(HISTORY_FILE);
        if (!f.exists()) return;
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split("\t", 4);
                if (parts.length >= 4) {
                    entries.add(new HistoryEntry(parts[3], parts[2], parts[1], parts[0]));
                }
            }
        } catch (Exception ignored) {}
    }
}
