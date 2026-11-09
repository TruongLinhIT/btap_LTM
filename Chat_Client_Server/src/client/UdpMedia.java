package client;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.DataInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

/** Kênh UDP tới server relay (thoại/video thời gian thực). */
public class UdpMedia {
    public interface Listener {
        void onMedia(int type, String sender, byte[] payload);
    }

    public static final int AUDIO = 1, VIDEO = 2;
    private final DatagramSocket sock;
    private final InetAddress server;
    private final int port;

    public UdpMedia(String host, int port) throws IOException {
        this.sock = new DatagramSocket();
        this.server = InetAddress.getByName(host);
        this.port = port;
    }

    public void hello(String user, String token) {
        try {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            DataOutputStream d = new DataOutputStream(b);
            d.writeByte(0);
            d.writeUTF(user);
            d.writeUTF(token);
            raw(b.toByteArray());
        } catch (IOException ignored) {
        }
    }

    /** Gửi HELLO định kỳ để giữ ánh xạ địa chỉ UDP. */
    public void keepAlive(String user, String token) {
        Thread t = new Thread(() -> {
            while (!sock.isClosed()) {
                hello(user, token);
                try {
                    Thread.sleep(10_000);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }, "udp-hello");
        t.setDaemon(true);
        t.start();
    }

    private void raw(byte[] data) throws IOException {
        sock.send(new DatagramPacket(data, data.length, server, port));
    }

    public void send(int type, String sender, String recipients, byte[] payload) {
        try {
            ByteArrayOutputStream b = new ByteArrayOutputStream(payload.length + 64);
            DataOutputStream d = new DataOutputStream(b);
            d.writeByte(type);
            d.writeUTF(sender);
            d.writeUTF(recipients);
            d.write(payload);
            if (b.size() <= 65000) raw(b.toByteArray());
        } catch (IOException ignored) {
        }
    }

    public void startReceiver(Listener l) {
        Thread t = new Thread(() -> {
            byte[] buf = new byte[65535];
            while (!sock.isClosed()) {
                try {
                    DatagramPacket p = new DatagramPacket(buf, buf.length);
                    sock.receive(p);
                    DataInputStream d = new DataInputStream(new ByteArrayInputStream(buf, 0, p.getLength()));
                    int type = d.readByte();
                    String sender = d.readUTF();
                    d.readUTF(); // recipients
                    l.onMedia(type, sender, d.readAllBytes());
                } catch (IOException e) {
                    // bỏ qua
                }
            }
        }, "udp-receiver");
        t.setDaemon(true);
        t.start();
    }

    public void close() {
        sock.close();
    }
}
