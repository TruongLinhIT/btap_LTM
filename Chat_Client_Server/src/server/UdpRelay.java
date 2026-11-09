package server;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * UDP relay cho thoại / video thời gian thực.
 * Gói: [type:byte][sender:UTF][recipients:UTF][payload]
 * type 0 = HELLO(user, token) đăng ký địa chỉ UDP; 1 = audio; 2 = video.
 * recipients: "*" (broadcast) hoặc danh sách tên cách nhau dấu phẩy (unicast / multicast).
 */
public class UdpRelay extends Thread {
    private final ChatServer server;
    private final DatagramSocket socket;
    private final Map<String, SocketAddress> addrs = new ConcurrentHashMap<>();

    UdpRelay(ChatServer server, int port) throws java.io.IOException {
        super("udp-relay");
        setDaemon(true);
        this.server = server;
        this.socket = new DatagramSocket(port);
    }

    void remove(String user) {
        addrs.remove(user);
    }

    @Override
    public void run() {
        byte[] buf = new byte[65535];
        while (true) {
            try {
                DatagramPacket in = new DatagramPacket(buf, buf.length);
                socket.receive(in);
                DataInputStream d = new DataInputStream(new ByteArrayInputStream(buf, 0, in.getLength()));
                int type = d.readByte();
                SocketAddress from = new InetSocketAddress(in.getAddress(), in.getPort());
                if (type == 0) {
                    String user = d.readUTF();
                    String token = d.readUTF();
                    ClientHandler h = server.online.get(user);
                    if (h != null && token.equals(h.token)) addrs.put(user, from);
                } else if (type == 1 || type == 2) {
                    String sender = d.readUTF();
                    String recipients = d.readUTF();
                    if (!from.equals(addrs.get(sender))) continue; // chống giả mạo
                    Iterable<String> targets = recipients.equals("*") ? addrs.keySet() : java.util.List.of(recipients.split(","));
                    for (String t : targets) {
                        SocketAddress a = addrs.get(t);
                        if (a == null || t.equals(sender)) continue;
                        socket.send(new DatagramPacket(buf, in.getLength(), a));
                    }
                }
            } catch (Exception e) {
                // bỏ qua gói lỗi
            }
        }
    }
}
