package browser;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.io.File;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
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
import javax.swing.SwingUtilities;

public class DownloadManager {

    public enum DownloadStatus {
        DOWNLOADING,
        COMPLETED,
        FAILED,
        CANCELLED
    }

    public static class DownloadTask {
        private String url;
        private File targetFile;
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
        }

        public String getFileName() {
            return targetFile != null ? targetFile.getName() : "unnamed_file";
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

    /**
     * Bat dau tai mot file tu URL
     */
    public static void startDownload(JFrame parent, String fileUrl) {
        // Lay ten file mac dinh tu URL
        String fileName = "downloaded_file";
        try {
            String path = new java.net.URL(fileUrl).getPath();
            if (path != null && path.contains("/")) {
                String sub = path.substring(path.lastIndexOf('/') + 1);
                if (!sub.isEmpty()) {
                    fileName = sub;
                }
            }
        } catch (Exception ignored) {}

        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Lưu file tải về (Trình duyệt LTM)");
        fileChooser.setSelectedFile(new File(System.getProperty("user.home") + File.separator + "Downloads" + File.separator + fileName));

        int userSelection = fileChooser.showSaveDialog(parent);
        if (userSelection != JFileChooser.APPROVE_OPTION) {
            return;
        }

        File targetFile = fileChooser.getSelectedFile();
        DownloadTask task = new DownloadTask(fileUrl, targetFile);
        tasks.add(0, task);

        // Hien thi Dialog Download Manager
        showManagerDialog(parent);
        refreshTaskListUI();

        // Chay luong tai ngam
        Thread worker = new Thread(() -> {
            try {
                HttpEngine.downloadToFile(fileUrl, targetFile, (bytesRead, totalBytes, percent) -> {
                    task.downloadedBytes = bytesRead;
                    task.totalBytes = totalBytes;
                    task.percent = percent;
                    SwingUtilities.invokeLater(() -> updateTaskUI(task));
                });
                task.status = DownloadStatus.COMPLETED;
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
            return;
        }

        managerDialog = new JDialog(parent, "Trình quản lý tải xuống (Downloads - Tương tự Chrome/Cốc Cốc)", false);
        managerDialog.setSize(650, 450);
        managerDialog.setLocationRelativeTo(parent);
        managerDialog.setLayout(new BorderLayout());

        taskListPanel = new JPanel();
        taskListPanel.setLayout(new BoxLayout(taskListPanel, BoxLayout.Y_AXIS));
        taskListPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JScrollPane scrollPane = new JScrollPane(taskListPanel);
        scrollPane.getVerticalScrollBar().setUnitIncrement(12);
        managerDialog.add(scrollPane, BorderLayout.CENTER);

        JPanel bottomBar = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnClear = new JButton("Xóa danh sách hoàn tất");
        btnClear.addActionListener(e -> {
            tasks.removeIf(t -> t.status == DownloadStatus.COMPLETED || t.status == DownloadStatus.FAILED);
            refreshTaskListUI();
        });
        JButton btnClose = new JButton("Đóng");
        btnClose.addActionListener(e -> managerDialog.setVisible(false));

        bottomBar.add(btnClear);
        bottomBar.add(btnClose);
        managerDialog.add(bottomBar, BorderLayout.SOUTH);

        refreshTaskListUI();
        managerDialog.setVisible(true);
    }

    private static void refreshTaskListUI() {
        if (taskListPanel == null) return;
        taskListPanel.removeAll();

        if (tasks.isEmpty()) {
            JLabel emptyLbl = new JLabel("Chưa có tệp tải xuống nào.", JLabel.CENTER);
            emptyLbl.setAlignmentX(Component.CENTER_ALIGNMENT);
            taskListPanel.add(Box.createVerticalStrut(20));
            taskListPanel.add(emptyLbl);
        } else {
            for (DownloadTask task : tasks) {
                JPanel card = createTaskCard(task);
                taskListPanel.add(card);
                taskListPanel.add(Box.createVerticalStrut(8));
            }
        }
        taskListPanel.revalidate();
        taskListPanel.repaint();
    }

    private static JPanel createTaskCard(DownloadTask task) {
        JPanel card = new JPanel(new BorderLayout(10, 6));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new java.awt.Color(218, 220, 224), 1),
                BorderFactory.createEmptyBorder(10, 12, 10, 12)
        ));
        card.setBackground(java.awt.Color.WHITE);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 95));

        // Top info
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        JLabel lblName = new JLabel("<html><b>" + task.getFileName() + "</b></html>");
        lblName.setFont(lblName.getFont().deriveFont(13f));
        JLabel lblUrl = new JLabel("Từ: " + task.getUrl());
        lblUrl.setFont(lblUrl.getFont().deriveFont(11f));
        lblUrl.setForeground(java.awt.Color.GRAY);
        top.add(lblName, BorderLayout.NORTH);
        top.add(lblUrl, BorderLayout.SOUTH);

        // Center progress
        JPanel center = new JPanel(new BorderLayout(5, 4));
        center.setOpaque(false);
        JProgressBar pb = new JProgressBar(0, 100);
        pb.setValue(Math.max(0, task.percent));
        pb.setStringPainted(true);
        if (task.percent < 0 && task.status == DownloadStatus.DOWNLOADING) {
            pb.setIndeterminate(true);
        }

        JLabel lblStat = new JLabel(formatStatusText(task));
        lblStat.setFont(lblStat.getFont().deriveFont(11f));
        center.add(pb, BorderLayout.CENTER);
        center.add(lblStat, BorderLayout.SOUTH);

        // Right actions
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        right.setOpaque(false);

        JButton btnOpen = new JButton("Mở file");
        btnOpen.setEnabled(task.status == DownloadStatus.COMPLETED);
        btnOpen.addActionListener(e -> {
            try {
                if (Desktop.isDesktopSupported() && task.targetFile.exists()) {
                    Desktop.getDesktop().open(task.targetFile);
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(managerDialog, "Không thể mở file: " + ex.getMessage());
            }
        });

        JButton btnOpenDir = new JButton("Mở thư mục");
        btnOpenDir.setEnabled(task.status == DownloadStatus.COMPLETED);
        btnOpenDir.addActionListener(e -> {
            try {
                if (Desktop.isDesktopSupported() && task.targetFile.getParentFile().exists()) {
                    Desktop.getDesktop().open(task.targetFile.getParentFile());
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(managerDialog, "Không thể mở thư mục: " + ex.getMessage());
            }
        });

        JButton btnCancel = new JButton("Hủy");
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
            return " Hoàn tất (" + formatBytes(task.downloadedBytes) + ")";
        } else if (task.status == DownloadStatus.FAILED) {
            return " Thất bại: " + (task.errorMessage != null ? task.errorMessage : "Lỗi mạng");
        } else if (task.status == DownloadStatus.CANCELLED) {
            return " Đã hủy";
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
