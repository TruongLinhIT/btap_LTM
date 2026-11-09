package client;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Trình khách FTP chuẩn (FTP Client) hỗ trợ kết nối, đăng nhập và truyền tải tệp.
 */
public class SimpleFtpClient {
    public interface ProgressCallback {
        void onProgress(long transferred, long total);
    }

    private Socket controlSocket;
    private BufferedReader reader;
    private BufferedWriter writer;
    private String lastResponse;
    private String serverHost;

    public void connect(String host, int port) throws IOException {
        this.serverHost = host;
        controlSocket = new Socket();
        controlSocket.connect(new InetSocketAddress(host, port), 10000);
        reader = new BufferedReader(new InputStreamReader(controlSocket.getInputStream(), StandardCharsets.UTF_8));
        writer = new BufferedWriter(new OutputStreamWriter(controlSocket.getOutputStream(), StandardCharsets.UTF_8));

        readResponse();
        if (!lastResponse.startsWith("220")) {
            throw new IOException("Máy chủ từ chối kết nối: " + lastResponse);
        }
    }

    public boolean login(String username, String password) throws IOException {
        sendCommand("USER " + username);
        readResponse();
        if (lastResponse.startsWith("331")) {
            sendCommand("PASS " + password);
            readResponse();
        }
        return lastResponse.startsWith("230");
    }

    public String pwd() throws IOException {
        sendCommand("PWD");
        readResponse();
        if (lastResponse.startsWith("257")) {
            int firstQuote = lastResponse.indexOf('"');
            int lastQuote = lastResponse.lastIndexOf('"');
            if (firstQuote >= 0 && lastQuote > firstQuote) {
                return lastResponse.substring(firstQuote + 1, lastQuote);
            }
        }
        return "/";
    }

    public boolean cwd(String path) throws IOException {
        sendCommand("CWD " + path);
        readResponse();
        return lastResponse.startsWith("250");
    }

    public boolean cdup() throws IOException {
        sendCommand("CDUP");
        readResponse();
        return lastResponse.startsWith("250");
    }

    public boolean mkd(String dirName) throws IOException {
        sendCommand("MKD " + dirName);
        readResponse();
        return lastResponse.startsWith("257");
    }

    public boolean rmd(String dirName) throws IOException {
        sendCommand("RMD " + dirName);
        readResponse();
        return lastResponse.startsWith("250");
    }

    public boolean dele(String fileName) throws IOException {
        sendCommand("DELE " + fileName);
        readResponse();
        return lastResponse.startsWith("250");
    }

    public boolean rename(String oldName, String newName) throws IOException {
        sendCommand("RNFR " + oldName);
        readResponse();
        if (!lastResponse.startsWith("350")) return false;
        sendCommand("RNTO " + newName);
        readResponse();
        return lastResponse.startsWith("250");
    }

    public List<FtpFileItem> listFiles() throws IOException {
        sendCommand("TYPE A");
        readResponse();

        Socket dataSocket = openPassiveDataSocket();
        sendCommand("LIST");
        readResponse(); // 150

        List<FtpFileItem> items = new ArrayList<>();
        try (BufferedReader dataReader = new BufferedReader(new InputStreamReader(dataSocket.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = dataReader.readLine()) != null) {
                FtpFileItem item = parseUnixListLine(line);
                if (item != null) {
                    items.add(item);
                }
            }
        } finally {
            dataSocket.close();
        }

        readResponse(); // 226
        return items;
    }

    public void downloadFile(String remoteFile, File localDest, ProgressCallback cb) throws IOException {
        sendCommand("TYPE I");
        readResponse();

        // Lấy kích thước tệp trước nếu có
        long fileSize = -1;
        sendCommand("SIZE " + remoteFile);
        readResponse();
        if (lastResponse.startsWith("213")) {
            try {
                fileSize = Long.parseLong(lastResponse.substring(4).trim());
            } catch (Exception ignored) {}
        }

        Socket dataSocket = openPassiveDataSocket();
        sendCommand("RETR " + remoteFile);
        readResponse(); // 150

        try (InputStream in = new BufferedInputStream(dataSocket.getInputStream());
             OutputStream out = new BufferedOutputStream(new FileOutputStream(localDest))) {

            byte[] buffer = new byte[16384];
            int read;
            long transferred = 0;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                transferred += read;
                if (cb != null) {
                    cb.onProgress(transferred, fileSize);
                }
            }
            out.flush();
        } finally {
            dataSocket.close();
        }

        readResponse(); // 226
    }

    public void uploadFile(File localSource, String remoteFilename, ProgressCallback cb) throws IOException {
        sendCommand("TYPE I");
        readResponse();

        long fileSize = localSource.length();
        Socket dataSocket = openPassiveDataSocket();
        sendCommand("STOR " + remoteFilename);
        readResponse(); // 150

        try (InputStream in = new BufferedInputStream(new FileInputStream(localSource));
             OutputStream out = new BufferedOutputStream(dataSocket.getOutputStream())) {

            byte[] buffer = new byte[16384];
            int read;
            long transferred = 0;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                transferred += read;
                if (cb != null) {
                    cb.onProgress(transferred, fileSize);
                }
            }
            out.flush();
        } finally {
            dataSocket.close();
        }

        readResponse(); // 226
    }

    public void disconnect() {
        try {
            if (writer != null) {
                sendCommand("QUIT");
                readResponse();
            }
        } catch (Exception ignored) {}
        try {
            if (controlSocket != null && !controlSocket.isClosed()) {
                controlSocket.close();
            }
        } catch (Exception ignored) {}
    }

    public boolean isConnected() {
        return controlSocket != null && controlSocket.isConnected() && !controlSocket.isClosed();
    }

    public String getLastResponse() {
        return lastResponse;
    }

    // ---------- Hỗ trợ Passive Mode ----------

    private Socket openPassiveDataSocket() throws IOException {
        sendCommand("PASV");
        readResponse();
        if (!lastResponse.startsWith("227")) {
            throw new IOException("PASV thất bại: " + lastResponse);
        }

        Matcher m = Pattern.compile("(\\d+),(\\d+),(\\d+),(\\d+),(\\d+),(\\d+)").matcher(lastResponse);
        if (!m.find()) {
            throw new IOException("Định dạng PASV không hợp lệ: " + lastResponse);
        }

        String dataHost = m.group(1) + "." + m.group(2) + "." + m.group(3) + "." + m.group(4);
        int port = Integer.parseInt(m.group(5)) * 256 + Integer.parseInt(m.group(6));

        // Nếu PASV trả về localhost/0.0.0.0, dùng serverHost
        if (dataHost.equals("0.0.0.0") || dataHost.equals("127.0.0.1")) {
            dataHost = serverHost;
        }

        Socket s = new Socket();
        s.connect(new InetSocketAddress(dataHost, port), 10000);
        return s;
    }

    private void sendCommand(String cmd) throws IOException {
        writer.write(cmd + "\r\n");
        writer.flush();
    }

    private String readResponse() throws IOException {
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line).append("\n");
            // Kiểm tra phản hồi nhiều dòng: mã 3 chữ số theo sau là dấu cách
            if (line.length() >= 4 && Character.isDigit(line.charAt(0))
                    && Character.isDigit(line.charAt(1))
                    && Character.isDigit(line.charAt(2))
                    && line.charAt(3) == ' ') {
                break;
            }
        }
        lastResponse = sb.toString().trim();
        return lastResponse;
    }

    private FtpFileItem parseUnixListLine(String line) {
        if (line == null || line.isBlank()) return null;
        // drwxr-xr-x 1 owner group 0 Nov 07 09:30 folder
        String[] parts = line.trim().split("\\s+", 9);
        if (parts.length < 9) return null;

        String perms = parts[0];
        boolean isDir = perms.startsWith("d");
        long size = 0;
        try {
            size = Long.parseLong(parts[4]);
        } catch (Exception ignored) {}

        String dateStr = parts[5] + " " + parts[6] + " " + parts[7];
        String name = parts[8];

        if (name.equals(".") || name.equals("..")) return null;
        return new FtpFileItem(name, size, isDir, dateStr, perms);
    }
}
