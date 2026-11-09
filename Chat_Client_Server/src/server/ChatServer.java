package server;

import common.Packet;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Server chat: TCP (điều khiển, text, file) + UDP relay (thoại/video realtime) + MySQL. */
public class ChatServer {
    public static final int TCP_PORT = Integer.parseInt(env("CHAT_TCP_PORT", "5000"));
    public static final int UDP_PORT = Integer.parseInt(env("CHAT_UDP_PORT", "5001"));

    final Map<String, ClientHandler> online = new ConcurrentHashMap<>();
    final Database db;
    final UdpRelay udp;
    final Path uploadDir = Path.of("uploads");

    static String env(String k, String def) {
        String v = System.getenv(k);
        return v == null || v.isBlank() ? def : v;
    }

    ChatServer() throws IOException {
        db = new Database(
                env("DB_URL", "jdbc:mysql://localhost:3308/chatdb?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=UTF-8&serverTimezone=Asia/Ho_Chi_Minh"),
                env("DB_USER", "chat"), env("DB_PASS", "chat123"));
        udp = new UdpRelay(this, UDP_PORT);
        Files.createDirectories(uploadDir);
    }

    void run() throws Exception {
        for (int i = 1; ; i++) {
            try {
                db.init();
                break;
            } catch (Exception e) {
                if (i >= 30) throw e;
                System.out.println("Chờ MySQL sẵn sàng... (" + e.getMessage() + ")");
                Thread.sleep(2000);
            }
        }
        System.out.println("Đã kết nối MySQL.");
        udp.start();
        ExecutorService pool = Executors.newCachedThreadPool();
        try (ServerSocket ss = new ServerSocket(TCP_PORT)) {
            System.out.println("Server chạy: TCP " + TCP_PORT + " | UDP " + UDP_PORT);
            while (true) {
                Socket s = ss.accept();
                s.setTcpNoDelay(true);
                pool.execute(new ClientHandler(this, s));
            }
        }
    }

    String saveFile(String fileName, byte[] data) throws IOException {
        Path p = uploadDir.resolve(System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 6) + "_" + fileName);
        Files.write(p, data);
        return p.toString();
    }

    Packet usersPacket() throws Exception {
        return new Packet("USERS")
                .put("all", String.join(",", db.allUsers()))
                .put("online", String.join(",", online.keySet()));
    }

    void broadcastUsers() {
        try {
            Packet p = usersPacket();
            for (ClientHandler h : online.values()) h.trySend(p);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    void pushGroups(String user) {
        ClientHandler h = online.get(user);
        if (h == null) return;
        try {
            StringBuilder sb = new StringBuilder();
            for (var e : db.groupsOf(user).entrySet()) {
                if (sb.length() > 0) sb.append('|');
                sb.append(e.getKey()).append('=').append(String.join(",", e.getValue()));
            }
            h.trySend(new Packet("GROUPS").put("groups", sb.toString()));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) throws Exception {
        new ChatServer().run();
    }
}
