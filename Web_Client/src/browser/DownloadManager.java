package browser;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.io.File;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

/**
 * Trình quản lý tải xuống kiểu Google Chrome / Cốc Cốc (chrome://downloads - Ctrl+J).
 * Tải tài nguyên web trực tiếp qua giao thức HTTP/1.1 TCP Socket và lưu vào ổ đĩa.
 */
public class DownloadManager {

    public enum DownloadStatus {
        DOWNLOADING,
        COMPLETED,
        FAILED,
        CANCELLED
    }

    public static class DownloadTask {
        private final String url;
        private final File targetFile;
        private final String startTime;
        private long totalBytes = -1;
        private long downloadedBytes = 0;
        private int percent = 0;
        private DownloadStatus status = DownloadStatus.DOWNLOADING;
        private String errorMessage = "";
        private Thread workerThread;

        private JPanel uiCard;
        private JProgressBar progressBar;
        private JLabel lblStatus;
        private JButton btnOpen;
        private JButton btnOpenDir;
        private JButton btnCancel;

        public DownloadTask(String url, File targetFile) {
            this.url = url;
            this.targetFile = targetFile;
            this.startTime = new SimpleDateFormat("HH:mm:ss").format(new Date());
        }

        public String getFileName() {
            return targetFile != null ? targetFile.getName() : "downloaded_page.html";
        }

        public String getUrl() {
            return url;
        }

        public File getTargetFile() {
            return targetFile;
        }

        public DownloadStatus getStatus() {
            return status;
        }
    }

    private static final List<DownloadTask> tasks = Collections.synchronizedList(new ArrayList<>());
    private static JDialog managerDialog = null;
    private static JPanel taskListPanel = null;
    private static JTextField txtQuickUrl = null;

    public static File getDefaultDownloadsDir() {
        File sub = new File("Web_Client");
        File dir = (sub.isDirectory() && new File(sub, "src").isDirectory())
                ? new File(sub, "downloads")
                : new File("downloads");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    private static String extractDefaultFileName(String fileUrl) {
        String fileName = "google_page.html";
        try {
            String cleanUrl = fileUrl.startsWith("http://") || fileUrl.startsWith("https://")
                    ? fileUrl : "http://" + fileUrl;
            java.net.URL u = new java.net.URL(cleanUrl);
            String path = u.getPath();
            if (path != null && path.contains("/")) {
                String sub = path.substring(path.lastIndexOf('/') + 1).trim();
                if (!sub.isEmpty()) {
                    fileName = sub.contains(".") ? sub : (sub + ".html");
                } else if (u.getHost() != null && !u.getHost().isEmpty()) {
                    fileName = u.getHost().replace(".", "_") + ".html";
                }
            }
        } catch (Exception ignored) {}
        return fileName;
    }

    /**
     * Tải tài nguyên với hộp thoại chọn nơi lưu (Save As...)
     */
    public static void startDownload(JFrame parent, String fileUrl) {
        if (fileUrl == null || fileUrl.trim().isEmpty()) return;
        String fileName = extractDefaultFileName(fileUrl);

        JFileChooser fileChooser = new JFileChooser(getDefaultDownloadsDir());
        fileChooser.setDialogTitle("Lưu trang / tệp tải xuống (Chrome / Cốc Cốc Download)");
        fileChooser.setSelectedFile(new File(getDefaultDownloadsDir(), fileName));

        int userSelection = fileChooser.showSaveDialog(parent);
        if (userSelection != JFileChooser.APPROVE_OPTION) {
            return;
        }

        File targetFile = fileChooser.getSelectedFile();
        launchDownloadTask(parent, fileUrl, targetFile);
    }

    /**
     * Tải nhanh trực tiếp vào thư mục ./downloads/ không cần hỏi hộp thoại.
     */
    public static void quickDownload(JFrame parent, String fileUrl) {
        if (fileUrl == null || fileUrl.trim().isEmpty()) return;
        String fileName = extractDefaultFileName(fileUrl);
        File targetFile = new File(getDefaultDownloadsDir(), fileName);
        if (targetFile.exists()) {
            int dot = fileName.lastIndexOf('.');
            String base = dot > 0 ? fileName.substring(0, dot) : fileName;
            String ext = dot > 0 ? fileName.substring(dot) : ".html";
            targetFile = new File(getDefaultDownloadsDir(), base + "_" + (System.currentTimeMillis() % 10000) + ext);
        }
        launchDownloadTask(parent, fileUrl, targetFile);
    }

    private static void launchDownloadTask(JFrame parent, String fileUrl, File targetFile) {
        DownloadTask task = new DownloadTask(fileUrl, targetFile);
        tasks.add(0, task);

        showManagerDialog(parent);
        refreshTaskListUI();

        Thread worker = new Thread(() -> {
            try {
                HttpEngine.downloadToFile(fileUrl, targetFile, (bytesRead, totalBytes, percent) -> {
                    task.downloadedBytes = bytesRead;
                    task.totalBytes = totalBytes;
                    task.percent = percent;
                    SwingUtilities.invokeLater(() -> updateTaskUI(task));
                });
                task.status = DownloadStatus.COMPLETED;
                task.downloadedBytes = targetFile.length();
                task.totalBytes = targetFile.length();
                task.percent = 100;
                HistoryManager.add(fileUrl, "Đã tải xuống: " + targetFile.getName(), "GET");
            } catch (Exception e) {
                task.status = DownloadStatus.FAILED;
                task.errorMessage = e.getMessage();
            }
            SwingUtilities.invokeLater(() -> updateTaskUI(task));
        });
        task.workerThread = worker;
        worker.start();
    }

    public static void showManagerDialog(JFrame parent) {
        if (managerDialog != null && managerDialog.isVisible()) {
            managerDialog.toFront();
            refreshTaskListUI();
            return;
        }

        managerDialog = new JDialog(parent, "Trình quản lý Tải xuống - Chrome / Cốc Cốc Downloads (Ctrl + J)", false);
        managerDialog.setSize(760, 500);
        managerDialog.setLocationRelativeTo(parent);
        managerDialog.setLayout(new BorderLayout());

        // TOP BAR: TẢI TỪ URL BẤT KỲ
        JPanel topContainer = new JPanel(new BorderLayout(6, 6));
        topContainer.setBackground(new Color(248, 249, 250));
        topContainer.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(218, 220, 224)),
                BorderFactory.createEmptyBorder(10, 14, 10, 14)
        ));

        JPanel urlBar = new JPanel(new BorderLayout(8, 0));
        urlBar.setOpaque(false);
        JLabel lblTitle = new JLabel("⬇ Nhập URL cần tải về (HTTP GET): ");
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 12));

        txtQuickUrl = new JTextField(WebBrowser.getHomeUrl() + "about_http.html");
        txtQuickUrl.setFont(new Font("Consolas", Font.PLAIN, 13));
        txtQuickUrl.addActionListener(e -> quickDownload(parent, txtQuickUrl.getText().trim()));

        JPanel dlBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        dlBtns.setOpaque(false);
        JButton btnQuickDl = UiUtil.createColoredButton(" ⬇ Tải ngay ", new Color(26, 115, 232), Color.WHITE, 6);
        btnQuickDl.addActionListener(e -> quickDownload(parent, txtQuickUrl.getText().trim()));

        JButton btnSaveAs = UiUtil.createOutlineButton("Lưu thành (Save As...)", new Color(203, 213, 225), new Color(30, 41, 59));
        btnSaveAs.addActionListener(e -> startDownload(parent, txtQuickUrl.getText().trim()));

        dlBtns.add(btnQuickDl);
        dlBtns.add(btnSaveAs);

        urlBar.add(lblTitle, BorderLayout.WEST);
        urlBar.add(txtQuickUrl, BorderLayout.CENTER);
        urlBar.add(dlBtns, BorderLayout.EAST);

        JPanel sampleRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        sampleRow.setOpaque(false);
        JLabel lblSample = new JLabel("Chọn nhanh trang web để tải thử:");
        lblSample.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lblSample.setForeground(new Color(95, 99, 104));
        sampleRow.add(lblSample);

        String home = WebBrowser.getHomeUrl();
        String[][] samples = {
                {"📘 Tài liệu Giao thức HTTP", home + "about_http.html"},
                {"🏠 Trang chủ Google", home + "index.html"},
                {"🔎 Trang Kết quả Tìm kiếm", home + "search?q=HTTP+Browser"},
                {"🌐 Trang Example.com", "http://example.com"}
        };
        for (String[] s : samples) {
            JButton b = UiUtil.createOutlineButton(s[0], new Color(203, 213, 225), new Color(26, 115, 232));
            b.addActionListener(e -> {
                txtQuickUrl.setText(s[1]);
                quickDownload(parent, s[1]);
            });
            sampleRow.add(b);
        }

        topContainer.add(urlBar, BorderLayout.NORTH);
        topContainer.add(sampleRow, BorderLayout.SOUTH);
        managerDialog.add(topContainer, BorderLayout.NORTH);

        // DANH SÁCH TÁC VỤ TẢI XUỐNG
        taskListPanel = new JPanel();
        taskListPanel.setLayout(new BoxLayout(taskListPanel, BoxLayout.Y_AXIS));
        taskListPanel.setBackground(Color.WHITE);
        taskListPanel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JScrollPane scrollPane = new JScrollPane(taskListPanel);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        managerDialog.add(scrollPane, BorderLayout.CENTER);

        // BOTTOM BAR
        JPanel bottomBar = new JPanel(new BorderLayout(8, 0));
        bottomBar.setBackground(new Color(248, 249, 250));
        bottomBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(218, 220, 224)),
                BorderFactory.createEmptyBorder(8, 14, 8, 14)
        ));

        JLabel lblDirInfo = new JLabel("Thư mục lưu mặc định: " + getDefaultDownloadsDir().getAbsolutePath());
        lblDirInfo.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lblDirInfo.setForeground(new Color(95, 99, 104));

        JPanel rightBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        rightBtns.setOpaque(false);

        JButton btnOpenFolder = UiUtil.createOutlineButton("📂 Mở thư mục Downloads", new Color(203, 213, 225), new Color(30, 41, 59));
        btnOpenFolder.addActionListener(e -> {
            try {
                if (Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().open(getDefaultDownloadsDir());
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(managerDialog, "Không thể mở thư mục: " + ex.getMessage());
            }
        });

        JButton btnClear = UiUtil.createOutlineButton("Xóa danh sách đã xong", new Color(203, 213, 225), new Color(30, 41, 59));
        btnClear.addActionListener(e -> {
            tasks.removeIf(t -> t.status != DownloadStatus.DOWNLOADING);
            refreshTaskListUI();
        });

        JButton btnClose = UiUtil.createColoredButton(" Đóng ", new Color(26, 115, 232), Color.WHITE, 6);
        btnClose.addActionListener(e -> managerDialog.setVisible(false));

        rightBtns.add(btnOpenFolder);
        rightBtns.add(btnClear);
        rightBtns.add(btnClose);

        bottomBar.add(lblDirInfo, BorderLayout.WEST);
        bottomBar.add(rightBtns, BorderLayout.EAST);
        managerDialog.add(bottomBar, BorderLayout.SOUTH);

        refreshTaskListUI();
        managerDialog.setVisible(true);
    }

    private static void refreshTaskListUI() {
        if (taskListPanel == null) return;
        taskListPanel.removeAll();

        if (tasks.isEmpty()) {
            JLabel emptyLabel = new JLabel("Chưa có mục tải xuống nào. Hãy nhập URL hoặc bấm nút phía trên để tải thử!");
            emptyLabel.setForeground(new Color(100, 116, 139));
            emptyLabel.setFont(new Font("Segoe UI", Font.ITALIC, 13));
            emptyLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            taskListPanel.add(Box.createVerticalStrut(50));
            taskListPanel.add(emptyLabel);
        } else {
            synchronized (tasks) {
                for (DownloadTask task : tasks) {
                    JPanel card = createTaskCard(task);
                    taskListPanel.add(card);
                    taskListPanel.add(Box.createVerticalStrut(8));
                }
            }
        }
        taskListPanel.revalidate();
        taskListPanel.repaint();
    }

    private static JPanel createTaskCard(DownloadTask task) {
        JPanel card = new JPanel(new BorderLayout(10, 6));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(218, 220, 224), 1, true),
                BorderFactory.createEmptyBorder(10, 14, 10, 14)
        ));
        card.setBackground(new Color(248, 250, 252));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 96));

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        JLabel lblName = new JLabel("⬇ " + task.getFileName() + "   (" + task.startTime + ")");
        lblName.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lblName.setForeground(new Color(15, 23, 42));

        JLabel lblUrl = new JLabel("Nguồn HTTP: " + task.getUrl() + "  →  " + task.getTargetFile().getAbsolutePath());
        lblUrl.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lblUrl.setForeground(new Color(100, 116, 139));
        top.add(lblName, BorderLayout.NORTH);
        top.add(lblUrl, BorderLayout.SOUTH);

        JPanel center = new JPanel(new BorderLayout(6, 4));
        center.setOpaque(false);
        JProgressBar pb = new JProgressBar(0, 100);
        pb.setValue(Math.max(0, task.percent));
        pb.setStringPainted(true);
        if (task.percent < 0 && task.status == DownloadStatus.DOWNLOADING) {
            pb.setIndeterminate(true);
        }

        JLabel lblStat = new JLabel(formatStatusText(task));
        lblStat.setFont(new Font("Segoe UI", Font.BOLD, 11));
        lblStat.setForeground(task.status == DownloadStatus.COMPLETED ? new Color(19, 115, 51)
                : (task.status == DownloadStatus.FAILED ? new Color(220, 38, 38) : new Color(26, 115, 232)));
        center.add(pb, BorderLayout.CENTER);
        center.add(lblStat, BorderLayout.SOUTH);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        right.setOpaque(false);

        JButton btnOpen = UiUtil.createColoredButton(" Mở tệp ", new Color(19, 115, 51), Color.WHITE, 6);
        btnOpen.setEnabled(task.status == DownloadStatus.COMPLETED);
        btnOpen.addActionListener(e -> {
            try {
                if (Desktop.isDesktopSupported() && task.targetFile.exists()) {
                    Desktop.getDesktop().open(task.targetFile);
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(managerDialog, "Đã lưu tệp tại:\n" + task.targetFile.getAbsolutePath());
            }
        });

        JButton btnOpenDir = UiUtil.createOutlineButton("Thư mục", new Color(203, 213, 225), new Color(30, 41, 59));
        btnOpenDir.setEnabled(task.status == DownloadStatus.COMPLETED);
        btnOpenDir.addActionListener(e -> {
            try {
                if (Desktop.isDesktopSupported() && task.targetFile.getParentFile().exists()) {
                    Desktop.getDesktop().open(task.targetFile.getParentFile());
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(managerDialog, "Thư mục: " + task.targetFile.getParent());
            }
        });

        JButton btnCancel = UiUtil.createOutlineButton("Hủy", new Color(203, 213, 225), new Color(185, 28, 28));
        btnCancel.setEnabled(task.status == DownloadStatus.DOWNLOADING);
        btnCancel.addActionListener(e -> {
            if (task.workerThread != null) {
                task.workerThread.interrupt();
            }
            task.status = DownloadStatus.CANCELLED;
            updateTaskUI(task);
        });

        right.add(btnOpen);
        right.add(btnOpenDir);
        right.add(btnCancel);

        card.add(top, BorderLayout.NORTH);
        card.add(center, BorderLayout.CENTER);
        card.add(right, BorderLayout.EAST);

        task.uiCard = card;
        task.progressBar = pb;
        task.lblStatus = lblStat;
        task.btnOpen = btnOpen;
        task.btnOpenDir = btnOpenDir;
        task.btnCancel = btnCancel;

        return card;
    }

    private static void updateTaskUI(DownloadTask task) {
        if (task.progressBar != null) {
            if (task.percent >= 0) {
                task.progressBar.setIndeterminate(false);
                task.progressBar.setValue(task.percent);
            } else if (task.status == DownloadStatus.DOWNLOADING) {
                task.progressBar.setIndeterminate(true);
            }
        }
        if (task.lblStatus != null) {
            task.lblStatus.setText(formatStatusText(task));
            task.lblStatus.setForeground(task.status == DownloadStatus.COMPLETED ? new Color(19, 115, 51)
                    : (task.status == DownloadStatus.FAILED ? new Color(220, 38, 38) : new Color(26, 115, 232)));
        }
        if (task.btnOpen != null) {
            task.btnOpen.setEnabled(task.status == DownloadStatus.COMPLETED);
        }
        if (task.btnOpenDir != null) {
            task.btnOpenDir.setEnabled(task.status == DownloadStatus.COMPLETED);
        }
        if (task.btnCancel != null) {
            task.btnCancel.setEnabled(task.status == DownloadStatus.DOWNLOADING);
        }
    }

    private static String formatStatusText(DownloadTask task) {
        if (task.status == DownloadStatus.COMPLETED) {
            return "[OK] Hoàn tất tải xuống (" + formatBytes(task.downloadedBytes) + ") qua giao thức HTTP/1.1";
        } else if (task.status == DownloadStatus.FAILED) {
            return "[LỖI] Thất bại: " + (task.errorMessage != null ? task.errorMessage : "Lỗi kết nối");
        } else if (task.status == DownloadStatus.CANCELLED) {
            return "Đã hủy tải xuống";
        } else {
            String downStr = formatBytes(task.downloadedBytes);
            if (task.totalBytes > 0) {
                return "Đang tải: " + downStr + " / " + formatBytes(task.totalBytes) + " (" + task.percent + "%)";
            } else {
                return "Đang tải: " + downStr + " ...";
            }
        }
    }

    private static String formatBytes(long bytes) {
        if (bytes <= 0) return "0 B";
        final String[] units = new String[]{"B", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(bytes) / Math.log10(1024));
        return new DecimalFormat("#,##0.#").format(bytes / Math.pow(1024, digitGroups)) + " " + units[digitGroups];
    }
}
