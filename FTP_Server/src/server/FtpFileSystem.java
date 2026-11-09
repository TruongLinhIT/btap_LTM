package server;

import database.UserModel;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Hệ thống tệp ảo (Virtual File System) và Sandboxing:
 * - Ánh xạ đường dẫn ảo "/" sang thư mục gốc vật lý của người dùng.
 * - Ngăn chặn tấn công Path Traversal ("../") vượt ra khỏi thư mục được chỉ định.
 * - Định dạng danh sách tệp theo chuẩn UNIX (RFC 959) và MLSD (RFC 3659).
 */
public class FtpFileSystem {
    private final UserModel user;
    private final Path homePath;
    private static final SimpleDateFormat UNIX_DATE_FORMAT = new SimpleDateFormat("MMM dd HH:mm", Locale.US);
    private static final SimpleDateFormat MLSD_DATE_FORMAT = new SimpleDateFormat("yyyyMMddHHmmss", Locale.US);

    public FtpFileSystem(UserModel user) {
        this.user = user;
        // Chuẩn hóa đường dẫn home
        Path hp = Paths.get(user.getHomeDirectory()).toAbsolutePath().normalize();
        if (!Files.exists(hp)) {
            try {
                Files.createDirectories(hp);
            } catch (IOException e) {
                System.err.println("Không thể tạo thư mục home cho " + user.getUsername() + ": " + e.getMessage());
            }
        }
        this.homePath = hp;
    }

    public Path getHomePath() {
        return homePath;
    }

    /**
     * Chuẩn hóa đường dẫn ảo kết hợp giữa thư mục hiện tại và tham số người dùng nhập.
     */
    public String normalizeVirtualPath(String currentDir, String inputPath) {
        if (inputPath == null || inputPath.isBlank()) {
            return currentDir;
        }

        inputPath = inputPath.replace('\\', '/').trim();
        List<String> parts = new ArrayList<>();

        if (inputPath.startsWith("/")) {
            // Đường dẫn tuyệt đối từ root ảo
            for (String part : inputPath.split("/")) {
                addVirtualPart(parts, part);
            }
        } else {
            // Đường dẫn tương đối từ thư mục hiện tại
            for (String part : currentDir.split("/")) {
                addVirtualPart(parts, part);
            }
            for (String part : inputPath.split("/")) {
                addVirtualPart(parts, part);
            }
        }

        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            sb.append("/").append(part);
        }
        return sb.length() == 0 ? "/" : sb.toString();
    }

    private void addVirtualPart(List<String> parts, String part) {
        if (part == null || part.isEmpty() || part.equals(".")) {
            return;
        }
        if (part.equals("..")) {
            if (!parts.isEmpty()) {
                parts.remove(parts.size() - 1);
            }
        } else {
            parts.add(part);
        }
    }

    /**
     * Chuyển đường dẫn ảo thành đường dẫn thực trên đĩa (Kiểm tra bảo mật Chroot Jail).
     */
    public Path resolvePhysical(String currentDir, String inputPath) {
        String virtual = normalizeVirtualPath(currentDir, inputPath);
        String rel = virtual.startsWith("/") ? virtual.substring(1) : virtual;

        Path resolved = homePath.resolve(rel).normalize();
        // Kiểm tra bảo mật: Không được phép vượt ra ngoài homePath
        if (!resolved.startsWith(homePath)) {
            return null;
        }
        return resolved;
    }

    /**
     * Định dạng danh sách theo chuẩn UNIX ls -l (FileZilla phân tích chuẩn nhất).
     */
    public String getUnixListing(Path dir) throws IOException {
        if (!Files.exists(dir) || !Files.isDirectory(dir)) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path entry : stream) {
                BasicFileAttributes attrs = Files.readAttributes(entry, BasicFileAttributes.class);
                boolean isDir = attrs.isDirectory();
                String perms = isDir ? "drwxr-xr-x" : "-rw-r--r--";
                long size = isDir ? 0 : attrs.size();
                String dateStr;
                synchronized (UNIX_DATE_FORMAT) {
                    dateStr = UNIX_DATE_FORMAT.format(new Date(attrs.lastModifiedTime().toMillis()));
                }
                String name = entry.getFileName().toString();

                sb.append(String.format("%s   1 %-8s %-8s %10d %s %s\r\n",
                        perms, user.getUsername(), "user", size, dateStr, name));
            }
        }
        return sb.toString();
    }

    /**
     * Định dạng danh sách theo chuẩn MLSD (RFC 3659).
     */
    public String getMlsdListing(Path dir) throws IOException {
        if (!Files.exists(dir) || !Files.isDirectory(dir)) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path entry : stream) {
                BasicFileAttributes attrs = Files.readAttributes(entry, BasicFileAttributes.class);
                boolean isDir = attrs.isDirectory();
                String type = isDir ? "dir" : "file";
                long size = isDir ? 0 : attrs.size();
                String dateStr;
                synchronized (MLSD_DATE_FORMAT) {
                    dateStr = MLSD_DATE_FORMAT.format(new Date(attrs.lastModifiedTime().toMillis()));
                }
                String name = entry.getFileName().toString();

                sb.append(String.format("type=%s;size=%d;modify=%s; %s\r\n",
                        type, size, dateStr, name));
            }
        }
        return sb.toString();
    }

    /**
     * Định dạng danh sách theo chuẩn NLST (chỉ có tên).
     */
    public String getNlstListing(Path dir) throws IOException {
        if (!Files.exists(dir) || !Files.isDirectory(dir)) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path entry : stream) {
                sb.append(entry.getFileName().toString()).append("\r\n");
            }
        }
        return sb.toString();
    }

    public InputStream openInputStream(Path file, long offset) throws IOException {
        InputStream in = Files.newInputStream(file, StandardOpenOption.READ);
        if (offset > 0) {
            long skipped = in.skip(offset);
            if (skipped < offset) {
                System.out.println("Cảnh báo: Chỉ nhảy qua được " + skipped + "/" + offset + " bytes");
            }
        }
        return in;
    }

    public OutputStream openOutputStream(Path file, boolean append) throws IOException {
        if (append) {
            return Files.newOutputStream(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
        } else {
            return Files.newOutputStream(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
        }
    }
}
