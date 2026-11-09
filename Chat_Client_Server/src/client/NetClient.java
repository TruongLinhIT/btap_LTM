package client;

import common.Packet;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.function.Consumer;

/** Kết nối TCP tới server. */
public class NetClient {
    private final Socket socket;
    private final DataInputStream in;
    private final DataOutputStream out;
    public String username, token, host;
    public int udpPort;

    private NetClient(String host, int port) throws IOException {
        this.host = host;
        socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(host, port), 5000);
            socket.setTcpNoDelay(true);
            in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
        } catch (IOException e) {
            socket.close();
            throw e;
        }
    }

    public static void register(String host, int port, String user, String pass) throws IOException {
        NetClient c = new NetClient(host, port);
        try {
            new Packet("REGISTER").put("user", user).put("pass", pass).write(c.out);
            Packet r = Packet.read(c.in);
            if (r.type.equals("ERR")) throw new IOException(r.get("text"));
        } finally {
            c.close();
        }
    }

    public static NetClient login(String host, int port, String user, String pass) throws IOException {
        NetClient c = new NetClient(host, port);
        try {
            new Packet("LOGIN").put("user", user).put("pass", pass).write(c.out);
            Packet r = Packet.read(c.in);
            if (!r.type.equals("LOGIN_OK")) throw new IOException(r.get("text"));
            c.username = user;
            c.token = r.get("token");
            c.udpPort = (int) r.getLong("udp");
            return c;
        } catch (IOException e) {
            c.close();
            throw e;
        }
    }

    public void startReader(Consumer<Packet> handler) {
        Thread t = new Thread(() -> {
            try {
                while (true) handler.accept(Packet.read(in));
            } catch (IOException e) {
                handler.accept(new Packet("DISCONNECTED"));
            }
        }, "tcp-reader");
        t.setDaemon(true);
        t.start();
    }

    public synchronized void send(Packet p) throws IOException {
        p.write(out);
    }

    public void close() {
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }
}
