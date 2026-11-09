package server;

import config.ServerConfig;
import database.DatabaseManager;
import database.UserModel;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Xử lý toàn bộ chu trình giao tiếp chuẩn FTP (RFC 959, RFC 3659) cho một kết nối Client.
 */
public class FtpClientHandler implements Runnable {
    private final FtpSession session;
    private final FtpServer server;
    private final ServerConfig config;
    private final DatabaseManager db;

    private BufferedReader reader;
    private BufferedWriter writer;
    private FtpFileSystem fileSystem;
    private volatile boolean running = true;

    public FtpClientHandler(FtpSession session, FtpServer server) {
        this.session = session;
        this.server = server;
        this.config = ServerConfig.getInstance();
        this.db = DatabaseManager.getInstance();
    }

    public FtpSession getSession() {
        return session;
    }

    @Override
    public void run() {
        try {
            Socket socket = session.getControlSocket();
            socket.setSoTimeout(config.getTimeoutSeconds() * 1000);

            reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));

            // Gửi lời chào chuẩn FTP
            sendResponse(220, "Service ready for new user (FTP Server - LTM Java & MySQL)");

            String line;
            while (running && (line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                server.fireCommand(session, line);
                processCommand(line);
            }
        } catch (SocketTimeoutException te) {
            sendResponseQuiet(421, "Timeout: Ket noi da bi ngat do khong hoat dong.");
        } catch (Exception e) {
            if (running) {
                System.err.println("Lỗi kết nối client " + session.getClientIp() + ": " + e.getMessage());
            }
        } finally {
            closeSession();
        }
    }

    private void processCommand(String commandLine) {
        final String cmd;
        final String args;
        int spaceIdx = commandLine.indexOf(' ');
        if (spaceIdx > 0) {
            cmd = commandLine.substring(0, spaceIdx).toUpperCase(Locale.ROOT);
            args = commandLine.substring(spaceIdx + 1).trim();
        } else {
            cmd = commandLine.toUpperCase(Locale.ROOT);
            args = "";
        }

        try {
            switch (cmd) {
                case "USER":
                    handleUser(args);
                    break;
                case "PASS":
                    handlePass(args);
                    break;
                case "QUIT":
                    handleQuit();
                    break;
                case "NOOP":
                    sendResponse(200, "OK");
                    break;
                case "SYST":
                    sendResponse(215, "UNIX Type: L8");
                    break;
                case "FEAT":
                    handleFeat();
                    break;
                case "OPTS":
                    handleOpts(args);
                    break;
                case "AUTH":
                    // Từ chối TLS/SSL để FileZilla tự động chuyển sang Plain FTP an toàn
                    sendResponse(502, "Explicit TLS not supported. Please connect using Plain FTP.");
                    break;
                case "PBSZ":
                case "PROT":
                    sendResponse(200, "OK");
                    break;
                case "PWD":
                case "XPWD":
                    requireAuth(() -> sendResponse(257, "\"" + session.getCurrentVirtualDir() + "\" is current directory."));
                    break;
                case "CWD":
                    requireAuth(() -> handleCwd(args));
                    break;
                case "CDUP":
                    requireAuth(() -> handleCdup());
                    break;
                case "TYPE":
                    handleType(args);
                    break;
                case "PASV":
                    requireAuth(() -> handlePasv());
                    break;
                case "EPSV":
                    requireAuth(() -> handleEpsv());
                    break;
                case "PORT":
                    requireAuth(() -> handlePort(args));
                    break;
                case "EPRT":
                    requireAuth(() -> handleEprt(args));
                    break;
                case "LIST":
                    requireAuth(() -> handleList(args, false));
                    break;
                case "NLST":
                    requireAuth(() -> handleNlst(args));
                    break;
                case "MLSD":
                    requireAuth(() -> handleMlsd(args));
                    break;
                case "SIZE":
                    requireAuth(() -> handleSize(args));
                    break;
                case "MDTM":
                    requireAuth(() -> handleMdtm(args));
                    break;
                case "REST":
                    handleRest(args);
                    break;
                case "RETR":
                    requireAuth(() -> handleRetr(args));
                    break;
                case "STOR":
                    requireAuth(() -> handleStor(args, false));
                    break;
                case "APPE":
                    requireAuth(() -> handleStor(args, true));
                    break;
                case "DELE":
                    requireAuth(() -> handleDele(args));
                    break;
                case "MKD":
                case "XMKD":
                    requireAuth(() -> handleMkd(args));
                    break;
                case "RMD":
                case "XRMD":
                    requireAuth(() -> handleRmd(args));
                    break;
                case "RNFR":
                    requireAuth(() -> handleRnfr(args));
                    break;
                case "RNTO":
                    requireAuth(() -> handleRnto(args));
                    break;
                case "ABOR":
                    sendResponse(226, "Abort command successful.");
                    break;
                default:
                    sendResponse(502, "Command not implemented: " + cmd);
                    break;
            }
        } catch (Exception e) {
            sendResponse(550, "Internal server error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void requireAuth(Runnable action) {
        if (!session.isAuthenticated()) {
            sendResponse(530, "Please login with USER and PASS first.");
        } else {
            action.run();
        }
    }

    // ---------- Xử lý Xác thực (USER / PASS) qua MySQL ----------

    private void handleUser(String username) {
        if (username.isEmpty()) {
            sendResponse(501, "Syntax error in parameters or arguments.");
            return;
        }
        session.setUserParam(username);
        sendResponse(331, "User name okay, need password for " + username);
    }

    private void handlePass(String password) {
        String username = session.getUserParam();
        if (username == null || username.isEmpty()) {
            sendResponse(503, "Login with USER first.");
            return;
        }

        UserModel user = db.authenticate(username, password);
        if (user != null) {
            session.setUser(user);
            session.setAuthenticated(true);
            session.setCurrentVirtualDir("/");
            session.setState("IDLE");
            this.fileSystem = new FtpFileSystem(user);

            db.logAsync(user.getUsername(), session.getClientIp(), "LOGIN", "/", 0, "SUCCESS", "Đăng nhập thành công từ " + session.getClientIp());
            sendResponse(230, "User logged in, proceed.");
        } else {
            db.logAsync(username, session.getClientIp(), "LOGIN", "/", 0, "FAILED", "Sai tên đăng nhập hoặc mật khẩu.");
            sendResponse(530, "Authentication failed.");
        }
    }

    private void handleQuit() {
        if (session.isAuthenticated()) {
            db.logAsync(session.getUsername(), session.getClientIp(), "QUIT", session.getCurrentVirtualDir(), 0, "SUCCESS", "Người dùng ngắt kết nối.");
        }
        sendResponse(221, "Goodbye. Have a nice day!");
        running = false;
    }

    private void handleFeat() {
        sendRawLine("211-Features:");
        sendRawLine(" UTF8");
        sendRawLine(" MDTM");
        sendRawLine(" REST STREAM");
        sendRawLine(" SIZE");
        sendRawLine(" MLSD");
        sendRawLine(" PASV");
        sendRawLine(" EPSV");
        sendRawLine("211 End");
    }

    private void handleOpts(String args) {
        String[] parts = args.split("\\s+");
        if (parts.length > 0 && parts[0].equalsIgnoreCase("UTF8")) {
            sendResponse(200, "OPTS UTF8 is set to ON.");
        } else {
            sendResponse(200, "OPTS command recognized.");
        }
    }

    private void handleType(String type) {
        if (type.equalsIgnoreCase("I") || type.equalsIgnoreCase("L 8")) {
            session.setTransferType("I");
            sendResponse(200, "Type set to I (Binary).");
        } else if (type.equalsIgnoreCase("A")) {
            session.setTransferType("A");
            sendResponse(200, "Type set to A (ASCII).");
        } else {
            session.setTransferType(type);
            sendResponse(200, "Type set to " + type);
        }
    }

    // ---------- Điều hướng thư mục (PWD, CWD, CDUP) ----------

    private void handleCwd(String path) {
        if (path.isEmpty()) {
            path = "/";
        }
        String normalizedVirtual = fileSystem.normalizeVirtualPath(session.getCurrentVirtualDir(), path);
        Path physical = fileSystem.resolvePhysical(session.getCurrentVirtualDir(), path);

        if (physical != null && Files.exists(physical) && Files.isDirectory(physical)) {
            session.setCurrentVirtualDir(normalizedVirtual);
            sendResponse(250, "Directory changed to \"" + normalizedVirtual + "\".");
        } else {
            sendResponse(550, "Failed to change directory. Path does not exist or is not a directory.");
        }
    }

    private void handleCdup() {
        handleCwd("..");
    }

    // ---------- Chế độ kết nối dữ liệu (PASV, EPSV, PORT, EPRT) ----------

    private void handlePasv() {
        try {
            ServerSocket pasvSocket = createPassiveServerSocket();
            session.setDataConnection(new FtpDataConnection(pasvSocket));

            // Lấy địa chỉ IP máy chủ để trả lời cho Client
            InetAddress localAddr = session.getControlSocket().getLocalAddress();
            String ipStr = localAddr.getHostAddress();
            if (ipStr.equals("0.0.0.0") || ipStr.contains(":")) {
                ipStr = "127.0.0.1";
            }
            String[] ipParts = ipStr.split("\\.");
            int port = pasvSocket.getLocalPort();
            int p1 = port / 256;
            int p2 = port % 256;

            String pasvMsg = String.format("(%s,%s,%s,%s,%d,%d)", ipParts[0], ipParts[1], ipParts[2], ipParts[3], p1, p2);
            sendResponse(227, "Entering Passive Mode " + pasvMsg);
        } catch (Exception e) {
            sendResponse(425, "Cannot enter passive mode: " + e.getMessage());
        }
    }

    private void handleEpsv() {
        try {
            ServerSocket pasvSocket = createPassiveServerSocket();
            session.setDataConnection(new FtpDataConnection(pasvSocket));
            int port = pasvSocket.getLocalPort();
            sendResponse(229, "Entering Extended Passive Mode (|||" + port + "|)");
        } catch (Exception e) {
            sendResponse(425, "Cannot enter extended passive mode: " + e.getMessage());
        }
    }

    private ServerSocket createPassiveServerSocket() throws IOException {
        int min = config.getPasvPortMin();
        int max = config.getPasvPortMax();
        for (int p = min; p <= max; p++) {
            try {
                return new ServerSocket(p, 1, session.getControlSocket().getLocalAddress());
            } catch (IOException ignored) {}
        }
        // Nếu dải cổng cấu hình bị chiếm, cấp cổng ngẫu nhiên khả dụng
        return new ServerSocket(0, 1, session.getControlSocket().getLocalAddress());
    }

    private void handlePort(String args) {
        String[] parts = args.split(",");
        if (parts.length != 6) {
            sendResponse(501, "Syntax error in parameters (IP,IP,IP,IP,P1,P2).");
            return;
        }
        try {
            String ip = parts[0] + "." + parts[1] + "." + parts[2] + "." + parts[3];
            int port = Integer.parseInt(parts[4]) * 256 + Integer.parseInt(parts[5]);
            session.setDataConnection(new FtpDataConnection(InetAddress.getByName(ip), port));
            sendResponse(200, "PORT command successful.");
        } catch (Exception e) {
            sendResponse(501, "Illegal PORT command: " + e.getMessage());
        }
    }

    private void handleEprt(String args) {
        // Cú pháp: |<net-prt>|<net-addr>|<tcp-port>|
        String[] parts = args.split("\\|");
        if (parts.length < 4) {
            sendResponse(501, "Syntax error in EPRT command.");
            return;
        }
        try {
            String ip = parts[2];
            int port = Integer.parseInt(parts[3]);
            session.setDataConnection(new FtpDataConnection(InetAddress.getByName(ip), port));
            sendResponse(200, "EPRT command successful.");
        } catch (Exception e) {
            sendResponse(501, "Illegal EPRT command: " + e.getMessage());
        }
    }

    // ---------- Danh sách tệp tin (LIST, NLST, MLSD) ----------

    private void handleList(String args, boolean nameOnly) {
        Path target = fileSystem.resolvePhysical(session.getCurrentVirtualDir(), args);
        if (target == null || !Files.exists(target)) {
            sendResponse(550, "File or directory not found.");
            return;
        }

        try (Socket dataSocket = session.getDataConnection().openDataSocket(15000);
             BufferedWriter dataWriter = new BufferedWriter(new OutputStreamWriter(dataSocket.getOutputStream(), StandardCharsets.UTF_8))) {

            sendResponse(150, "Opening ASCII mode data connection for file list.");

            String listing;
            if (Files.isDirectory(target)) {
                listing = nameOnly ? fileSystem.getNlstListing(target) : fileSystem.getUnixListing(target);
            } else {
                // Nếu là file đơn lẻ
                BasicFileAttributes attrs = Files.readAttributes(target, BasicFileAttributes.class);
                String dateStr = new SimpleDateFormat("MMM dd HH:mm", Locale.US).format(new Date(attrs.lastModifiedTime().toMillis()));
                listing = String.format("-rw-r--r--   1 %-8s %-8s %10d %s %s\r\n",
                        session.getUsername(), "user", attrs.size(), dateStr, target.getFileName());
            }

            dataWriter.write(listing);
            dataWriter.flush();
            sendResponse(226, "Transfer complete.");
            db.logAsync(session.getUsername(), session.getClientIp(), "LIST", session.getCurrentVirtualDir(), 0, "SUCCESS", "Xem danh sách thư mục.");
        } catch (Exception e) {
            sendResponse(426, "Connection closed; transfer aborted: " + e.getMessage());
        } finally {
            session.setDataConnection(null);
        }
    }

    private void handleNlst(String args) {
        handleList(args, true);
    }

    private void handleMlsd(String args) {
        Path target = fileSystem.resolvePhysical(session.getCurrentVirtualDir(), args);
        if (target == null || !Files.exists(target) || !Files.isDirectory(target)) {
            sendResponse(550, "Directory not found.");
            return;
        }

        try (Socket dataSocket = session.getDataConnection().openDataSocket(15000);
             BufferedWriter dataWriter = new BufferedWriter(new OutputStreamWriter(dataSocket.getOutputStream(), StandardCharsets.UTF_8))) {

            sendResponse(150, "Opening ASCII mode data connection for MLSD.");
            String listing = fileSystem.getMlsdListing(target);
            dataWriter.write(listing);
            dataWriter.flush();
            sendResponse(226, "Transfer complete.");
            db.logAsync(session.getUsername(), session.getClientIp(), "MLSD", session.getCurrentVirtualDir(), 0, "SUCCESS", "MLSD listing.");
        } catch (Exception e) {
            sendResponse(426, "Connection closed; transfer aborted: " + e.getMessage());
        } finally {
            session.setDataConnection(null);
        }
    }

    // ---------- Truyền tệp: Tải về (RETR) & Tải lên (STOR / APPE) ----------

    private void handleRetr(String filename) {
        if (!session.getUser().isCanDownload()) {
            sendResponse(550, "Permission denied: Account does not have download privilege.");
            db.logAsync(session.getUsername(), session.getClientIp(), "RETR", filename, 0, "DENIED", "Không có quyền tải về");
            return;
        }

        Path target = fileSystem.resolvePhysical(session.getCurrentVirtualDir(), filename);
        if (target == null || !Files.exists(target) || Files.isDirectory(target)) {
            sendResponse(550, "File not found or is a directory.");
            return;
        }

        long offset = session.getRestOffset();
        session.setRestOffset(0); // Reset sau khi dùng

        session.setState("DOWNLOADING");
        try (Socket dataSocket = session.getDataConnection().openDataSocket(15000);
             InputStream fileIn = fileSystem.openInputStream(target, offset);
             OutputStream dataOut = new BufferedOutputStream(dataSocket.getOutputStream())) {

            long totalSize = Files.size(target);
            sendResponse(150, "Opening BINARY mode data connection for " + target.getFileName() + " (" + totalSize + " bytes).");

            byte[] buffer = new byte[16384];
            int read;
            long transferred = offset;

            while ((read = fileIn.read(buffer)) != -1) {
                dataOut.write(buffer, 0, read);
                transferred += read;
            }
            dataOut.flush();
            sendResponse(226, "Transfer complete.");

            server.fireTransfer(session, "DOWNLOAD", target.getFileName().toString(), transferred, true);
            db.logAsync(session.getUsername(), session.getClientIp(), "RETR", filename, transferred, "SUCCESS", "Tải về thành công (" + transferred + " bytes)");
        } catch (Exception e) {
            sendResponse(426, "Data connection closed abnormally: " + e.getMessage());
            db.logAsync(session.getUsername(), session.getClientIp(), "RETR", filename, 0, "FAILED", e.getMessage());
        } finally {
            session.setState("IDLE");
            session.setDataConnection(null);
        }
    }

    private void handleStor(String filename, boolean append) {
        if (!session.getUser().isCanUpload()) {
            sendResponse(550, "Permission denied: Account does not have upload privilege.");
            db.logAsync(session.getUsername(), session.getClientIp(), append ? "APPE" : "STOR", filename, 0, "DENIED", "Không có quyền tải lên");
            return;
        }

        Path target = fileSystem.resolvePhysical(session.getCurrentVirtualDir(), filename);
        if (target == null) {
            sendResponse(553, "Invalid file path (Access violation).");
            return;
        }

        session.setState("UPLOADING");
        try (Socket dataSocket = session.getDataConnection().openDataSocket(15000);
             InputStream dataIn = new BufferedInputStream(dataSocket.getInputStream());
             OutputStream fileOut = fileSystem.openOutputStream(target, append)) {

            sendResponse(150, "Opening BINARY mode data connection for " + target.getFileName() + ".");

            byte[] buffer = new byte[16384];
            int read;
            long totalBytes = 0;

            while ((read = dataIn.read(buffer)) != -1) {
                fileOut.write(buffer, 0, read);
                totalBytes += read;
            }
            fileOut.flush();
            sendResponse(226, "Transfer complete.");

            server.fireTransfer(session, "UPLOAD", target.getFileName().toString(), totalBytes, true);
            db.logAsync(session.getUsername(), session.getClientIp(), append ? "APPE" : "STOR", filename, totalBytes, "SUCCESS", "Tải lên thành công (" + totalBytes + " bytes)");
        } catch (Exception e) {
            sendResponse(426, "Upload aborted: " + e.getMessage());
            db.logAsync(session.getUsername(), session.getClientIp(), append ? "APPE" : "STOR", filename, 0, "FAILED", e.getMessage());
        } finally {
            session.setState("IDLE");
            session.setDataConnection(null);
        }
    }

    // ---------- Quản lý tệp/thư mục (DELE, MKD, RMD, RNFR, RNTO) ----------

    private void handleDele(String filename) {
        if (!session.getUser().isCanDelete()) {
            sendResponse(550, "Permission denied: Delete not allowed.");
            db.logAsync(session.getUsername(), session.getClientIp(), "DELE", filename, 0, "DENIED", "Không có quyền xóa tệp");
            return;
        }
        Path target = fileSystem.resolvePhysical(session.getCurrentVirtualDir(), filename);
        if (target == null || !Files.exists(target) || Files.isDirectory(target)) {
            sendResponse(550, "File not found or is a directory.");
            return;
        }
        try {
            Files.delete(target);
            sendResponse(250, "File deleted successfully.");
            db.logAsync(session.getUsername(), session.getClientIp(), "DELE", filename, 0, "SUCCESS", "Xóa tệp thành công");
        } catch (Exception e) {
            sendResponse(550, "Could not delete file: " + e.getMessage());
        }
    }

    private void handleMkd(String dirName) {
        if (!session.getUser().isCanModify()) {
            sendResponse(550, "Permission denied: Make directory not allowed.");
            db.logAsync(session.getUsername(), session.getClientIp(), "MKD", dirName, 0, "DENIED", "Không có quyền tạo thư mục");
            return;
        }
        Path target = fileSystem.resolvePhysical(session.getCurrentVirtualDir(), dirName);
        if (target == null) {
            sendResponse(553, "Invalid path.");
            return;
        }
        try {
            Files.createDirectories(target);
            sendResponse(257, "\"" + dirName + "\" directory created.");
            db.logAsync(session.getUsername(), session.getClientIp(), "MKD", dirName, 0, "SUCCESS", "Tạo thư mục");
        } catch (Exception e) {
            sendResponse(550, "Cannot create directory: " + e.getMessage());
        }
    }

    private void handleRmd(String dirName) {
        if (!session.getUser().isCanDelete()) {
            sendResponse(550, "Permission denied: Remove directory not allowed.");
            db.logAsync(session.getUsername(), session.getClientIp(), "RMD", dirName, 0, "DENIED", "Không có quyền xóa thư mục");
            return;
        }
        Path target = fileSystem.resolvePhysical(session.getCurrentVirtualDir(), dirName);
        if (target == null || !Files.exists(target) || !Files.isDirectory(target)) {
            sendResponse(550, "Directory not found.");
            return;
        }
        try {
            Files.delete(target);
            sendResponse(250, "Directory removed.");
            db.logAsync(session.getUsername(), session.getClientIp(), "RMD", dirName, 0, "SUCCESS", "Xóa thư mục");
        } catch (Exception e) {
            sendResponse(550, "Cannot remove directory (may not be empty): " + e.getMessage());
        }
    }

    private void handleRnfr(String path) {
        if (!session.getUser().isCanModify()) {
            sendResponse(550, "Permission denied: Rename not allowed.");
            return;
        }
        Path target = fileSystem.resolvePhysical(session.getCurrentVirtualDir(), path);
        if (target == null || !Files.exists(target)) {
            sendResponse(550, "File or directory does not exist.");
            return;
        }
        session.setRenameFromPath(path);
        sendResponse(350, "File exists, ready for destination name.");
    }

    private void handleRnto(String newPath) {
        String fromVirtual = session.getRenameFromPath();
        if (fromVirtual == null) {
            sendResponse(503, "Bad sequence of commands: Send RNFR first.");
            return;
        }
        session.setRenameFromPath(null);

        Path fromTarget = fileSystem.resolvePhysical(session.getCurrentVirtualDir(), fromVirtual);
        Path toTarget = fileSystem.resolvePhysical(session.getCurrentVirtualDir(), newPath);

        if (fromTarget == null || toTarget == null) {
            sendResponse(553, "Invalid path.");
            return;
        }

        try {
            Files.move(fromTarget, toTarget, StandardCopyOption.REPLACE_EXISTING);
            sendResponse(250, "Rename successful.");
            db.logAsync(session.getUsername(), session.getClientIp(), "RNTO", fromVirtual + " -> " + newPath, 0, "SUCCESS", "Đổi tên tệp/thư mục");
        } catch (Exception e) {
            sendResponse(550, "Rename failed: " + e.getMessage());
        }
    }

    private void handleSize(String filename) {
        Path target = fileSystem.resolvePhysical(session.getCurrentVirtualDir(), filename);
        if (target == null || !Files.exists(target) || Files.isDirectory(target)) {
            sendResponse(550, "File not found or is a directory.");
            return;
        }
        try {
            sendResponse(213, String.valueOf(Files.size(target)));
        } catch (Exception e) {
            sendResponse(550, "Cannot obtain size: " + e.getMessage());
        }
    }

    private void handleMdtm(String filename) {
        Path target = fileSystem.resolvePhysical(session.getCurrentVirtualDir(), filename);
        if (target == null || !Files.exists(target)) {
            sendResponse(550, "File not found.");
            return;
        }
        try {
            long millis = Files.getLastModifiedTime(target).toMillis();
            String dateStr = new SimpleDateFormat("yyyyMMddHHmmss", Locale.US).format(new Date(millis));
            sendResponse(213, dateStr);
        } catch (Exception e) {
            sendResponse(550, "Cannot obtain modification time: " + e.getMessage());
        }
    }

    private void handleRest(String offsetStr) {
        try {
            long offset = Long.parseLong(offsetStr.trim());
            if (offset < 0) throw new NumberFormatException();
            session.setRestOffset(offset);
            sendResponse(350, "Restarting at " + offset + ". Send STORE or RETRIEVE to initiate transfer.");
        } catch (Exception e) {
            sendResponse(501, "Marker must be a positive integer.");
        }
    }

    // ---------- Tiện ích gửi dữ liệu ----------

    public synchronized void sendResponse(int code, String message) {
        String resp = code + " " + message;
        sendRawLine(resp);
        server.fireResponse(session, resp);
    }

    private void sendResponseQuiet(int code, String message) {
        try {
            sendRawLine(code + " " + message);
        } catch (Exception ignored) {}
    }

    private synchronized void sendRawLine(String line) {
        try {
            if (writer != null) {
                writer.write(line + "\r\n");
                writer.flush();
            }
        } catch (Exception ignored) {}
    }

    public void closeSession() {
        running = false;
        session.close();
        server.onClientClosed(this);
    }
}
