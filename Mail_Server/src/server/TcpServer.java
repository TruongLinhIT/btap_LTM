package server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Khung chung cho một TCP server đa luồng: mỗi client kết nối được xử lý trong một luồng riêng.
 */
public abstract class TcpServer {
    protected final int port;
    protected final MailServerManager manager;
    private final String name;
    private ServerSocket serverSocket;
    private ExecutorService pool;
    private volatile boolean running;
    private final AtomicInteger active = new AtomicInteger();

    protected TcpServer(String name, int port, MailServerManager manager) {
        this.name = name;
        this.port = port;
        this.manager = manager;
    }

    public void start() throws IOException {
        serverSocket = new ServerSocket(port);
        pool = Executors.newFixedThreadPool(Math.max(4, config.MailConfig.getInstance().getMaxClients()));
        running = true;
        Thread t = new Thread(this::acceptLoop, name + "-acceptor");
        t.setDaemon(true);
        t.start();
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket s = serverSocket.accept();
                pool.submit(() -> {
                    active.incrementAndGet();
                    try (s) {
                        s.setSoTimeout(10 * 60 * 1000);
                        handle(s);
                    } catch (Exception e) {
                        if (running) manager.log(name, "Lỗi phiên " + s.getInetAddress().getHostAddress() + ": " + e.getMessage());
                    } finally {
                        active.decrementAndGet();
                    }
                });
            } catch (SocketException e) {
                if (running) manager.log(name, "Socket lỗi: " + e.getMessage());
            } catch (IOException e) {
                if (running) manager.log(name, "Accept lỗi: " + e.getMessage());
            }
        }
    }

    protected abstract void handle(Socket socket) throws Exception;

    public void stop() {
        running = false;
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (IOException ignored) {
        }
        if (pool != null) pool.shutdownNow();
    }

    public boolean isRunning() { return running; }

    public int getActive() { return active.get(); }
}
