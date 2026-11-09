package server;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Quản lý kết nối dữ liệu (Data Connection) trong FTP theo cả hai chế độ:
 * - Passive Mode (PASV / EPSV)
 * - Active Mode (PORT / EPRT)
 */
public class FtpDataConnection {
    public enum Mode {
        PASSIVE,
        ACTIVE
    }

    private final Mode mode;
    private ServerSocket passiveServerSocket;
    private InetAddress activeAddress;
    private int activePort;

    // Khởi tạo chế độ Passive (PASV / EPSV)
    public FtpDataConnection(ServerSocket serverSocket) {
        this.mode = Mode.PASSIVE;
        this.passiveServerSocket = serverSocket;
    }

    // Khởi tạo chế độ Active (PORT / EPRT)
    public FtpDataConnection(InetAddress address, int port) {
        this.mode = Mode.ACTIVE;
        this.activeAddress = address;
        this.activePort = port;
    }

    public Mode getMode() {
        return mode;
    }

    public int getPassivePort() {
        if (passiveServerSocket != null) {
            return passiveServerSocket.getLocalPort();
        }
        return -1;
    }

    /**
     * Mở socket truyền dữ liệu.
     * Ở chế độ PASV: chờ và chấp nhận kết nối từ Client (accept).
     * Ở chế độ ACTIVE: chủ động kết nối tới cổng của Client (connect).
     */
    public Socket openDataSocket(int timeoutMillis) throws IOException {
        if (mode == Mode.PASSIVE) {
            if (passiveServerSocket == null || passiveServerSocket.isClosed()) {
                throw new IOException("Passive ServerSocket không khả dụng hoặc đã đóng");
            }
            passiveServerSocket.setSoTimeout(timeoutMillis);
            Socket clientDataSocket = passiveServerSocket.accept();
            // Đóng passive server socket ngay sau khi client đã kết nối để tránh rò rỉ cổng
            try {
                passiveServerSocket.close();
            } catch (Exception ignored) {}
            return clientDataSocket;
        } else {
            // Active Mode
            Socket socket = new Socket();
            socket.connect(new java.net.InetSocketAddress(activeAddress, activePort), timeoutMillis);
            return socket;
        }
    }

    /**
     * Đóng kết nối dữ liệu nếu còn mở.
     */
    public synchronized void close() {
        if (passiveServerSocket != null && !passiveServerSocket.isClosed()) {
            try {
                passiveServerSocket.close();
            } catch (Exception ignored) {}
        }
    }
}
