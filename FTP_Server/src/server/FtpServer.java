package server;

import config.ServerConfig;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Lớp điều khiển chính của FTP Server:
 * - Lắng nghe kết nối TCP trên cổng cấu hình (mặc định 21 hoặc 2121)
 * - Quản lý Thread Pool và phiên làm việc của các Client đồng thời
 * - Bắn các sự kiện (Events) tới UI / Console
 */
public class FtpServer {
    private final ServerConfig config;
    private ServerSocket serverSocket;
    private ExecutorService threadPool;
    private final List<FtpClientHandler> activeHandlers = new CopyOnWriteArrayList<>();
    private final List<FtpEventListener> listeners = new CopyOnWriteArrayList<>();

    private volatile boolean running = false;
    private int boundPort;

    public FtpServer() {
        this.config = ServerConfig.getInstance();
    }

    public synchronized void start(int port) throws IOException {
        if (running) {
            throw new IllegalStateException("FTP Server đang chạy!");
        }

        try {
            serverSocket = new ServerSocket(port, 50);
            this.boundPort = port;
        } catch (IOException e) {
            // Nếu cổng 21 bị từ chối quyền hoặc bị chiếm, thử cổng 2121
            if (port == 21) {
                System.out.println("Cổng 21 không thể mở, tự động chuyển sang cổng 2121...");
                serverSocket = new ServerSocket(2121, 50);
                this.boundPort = 2121;
            } else {
                throw e;
            }
        }

        config.setServerPort(this.boundPort);
        config.save();

        this.running = true;
        this.threadPool = Executors.newFixedThreadPool(config.getMaxClients());

        fireServerStatus(true, boundPort, "FTP Server đã khởi động thành công trên cổng " + boundPort);

        // Luồng chấp nhận kết nối (Acceptor Thread)
        Thread acceptThread = new Thread(this::listenLoop, "FtpServer-Acceptor");
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    private void listenLoop() {
        while (running && serverSocket != null && !serverSocket.isClosed()) {
            try {
                Socket clientSocket = serverSocket.accept();
                if (!running) {
                    clientSocket.close();
                    break;
                }

                FtpSession session = new FtpSession(clientSocket);
                FtpClientHandler handler = new FtpClientHandler(session, this);
                activeHandlers.add(handler);

                fireClientConnected(session);
                threadPool.submit(handler);
            } catch (IOException e) {
                if (running) {
                    System.err.println("Lỗi chấp nhận kết nối: " + e.getMessage());
                }
            }
        }
    }

    public synchronized void stop() {
        if (!running) return;

        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (Exception ignored) {}

        // Đóng toàn bộ phiên của các client
        for (FtpClientHandler handler : activeHandlers) {
            handler.closeSession();
        }
        activeHandlers.clear();

        if (threadPool != null) {
            threadPool.shutdownNow();
        }

        fireServerStatus(false, boundPort, "FTP Server đã dừng.");
    }

    public void disconnectClient(String sessionId) {
        for (FtpClientHandler handler : activeHandlers) {
            if (handler.getSession().getId().equals(sessionId)) {
                handler.closeSession();
                break;
            }
        }
    }

    void onClientClosed(FtpClientHandler handler) {
        activeHandlers.remove(handler);
        fireClientDisconnected(handler.getSession());
    }

    public boolean isRunning() {
        return running;
    }

    public int getBoundPort() {
        return boundPort;
    }

    public List<FtpSession> getActiveSessions() {
        List<FtpSession> list = new ArrayList<>();
        for (FtpClientHandler h : activeHandlers) {
            list.add(h.getSession());
        }
        return Collections.unmodifiableList(list);
    }

    // ---------- Quản lý Event Listeners ----------

    public void addListener(FtpEventListener listener) {
        listeners.add(listener);
    }

    public void removeListener(FtpEventListener listener) {
        listeners.remove(listener);
    }

    void fireServerStatus(boolean isRunning, int port, String msg) {
        for (FtpEventListener l : listeners) {
            try { l.onServerStatusChanged(isRunning, port, msg); } catch (Exception ignored) {}
        }
    }

    void fireClientConnected(FtpSession session) {
        for (FtpEventListener l : listeners) {
            try { l.onClientConnected(session); } catch (Exception ignored) {}
        }
    }

    void fireClientDisconnected(FtpSession session) {
        for (FtpEventListener l : listeners) {
            try { l.onClientDisconnected(session); } catch (Exception ignored) {}
        }
    }

    void fireCommand(FtpSession session, String cmd) {
        for (FtpEventListener l : listeners) {
            try { l.onCommand(session, cmd); } catch (Exception ignored) {}
        }
    }

    void fireResponse(FtpSession session, String resp) {
        for (FtpEventListener l : listeners) {
            try { l.onResponse(session, resp); } catch (Exception ignored) {}
        }
    }

    void fireTransfer(FtpSession session, String action, String filename, long bytesTransferred, boolean success) {
        for (FtpEventListener l : listeners) {
            try { l.onTransfer(session, action, filename, bytesTransferred, success); } catch (Exception ignored) {}
        }
    }
}
