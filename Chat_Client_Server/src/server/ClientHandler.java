package server;

import common.Packet;

import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Xử lý một kết nối TCP của một client. */
class ClientHandler implements Runnable {
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");
    private static final String NAME_RE = "[\\p{L}\\p{N}_.-]{2,20}";
    private static final Set<String> KINDS = Set.of("PRIVATE", "GROUP", "BROADCAST");
    private static final Set<String> MEDIA = Set.of("TEXT", "IMAGE", "AUDIO", "VIDEO", "FILE");

    private final ChatServer srv;
    private final Socket sock;
    private DataInputStream in;
    private DataOutputStream out;
    String username;
    String token;

    ClientHandler(ChatServer srv, Socket sock) {
        this.srv = srv;
        this.sock = sock;
    }

    synchronized void send(Packet p) throws IOException {
        p.write(out);
    }

    void trySend(Packet p) {
        try {
            send(p);
        } catch (IOException e) {
            try {
                sock.close();
            } catch (IOException ignored) {
            }
        }
    }

    private void err(String text) throws IOException {
        send(new Packet("ERR").put("text", text));
    }

    @Override
    public void run() {
        try {
            in = new DataInputStream(new BufferedInputStream(sock.getInputStream()));
            out = new DataOutputStream(new BufferedOutputStream(sock.getOutputStream()));
            while (true) {
                Packet p = Packet.read(in);
                try {
                    handle(p);
                } catch (IOException e) {
                    throw e;
                } catch (Exception e) {
                    e.printStackTrace();
                    err("Lỗi máy chủ: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            // client ngắt kết nối
        } finally {
            if (username != null) {
                srv.online.remove(username, this);
                srv.udp.remove(username);
                srv.broadcastUsers();
                System.out.println("[-] " + username + " đã thoát");
            }
            try {
                sock.close();
            } catch (IOException ignored) {
            }
        }
    }

    private void handle(Packet p) throws Exception {
        if (username == null && !p.type.equals("LOGIN") && !p.type.equals("REGISTER")) {
            err("Chưa đăng nhập");
            return;
        }
        switch (p.type) {
            case "REGISTER" -> register(p);
            case "LOGIN" -> login(p);
            case "MSG" -> message(p);
            case "HISTORY" -> history(p);
            case "GET_FILE" -> getFile(p);
            case "CREATE_GROUP" -> createGroup(p);
            default -> err("Lệnh không hợp lệ: " + p.type);
        }
    }

    private void register(Packet p) throws Exception {
        String u = p.get("user"), pw = p.get("pass");
        if (!u.matches(NAME_RE)) {
            err("Tên đăng nhập 2-20 ký tự (chữ, số, _ . -), không có khoảng trắng");
        } else if (pw.length() < 3) {
            err("Mật khẩu tối thiểu 3 ký tự");
        } else if (!srv.db.register(u, pw)) {
            err("Tên đăng nhập đã tồn tại");
        } else {
            send(new Packet("REGISTER_OK"));
            srv.broadcastUsers();
        }
    }

    private void login(Packet p) throws Exception {
        String u = p.get("user");
        if (!srv.db.login(u, p.get("pass"))) {
            err("Sai tên đăng nhập hoặc mật khẩu");
            return;
        }
        token = UUID.randomUUID().toString();
        if (srv.online.putIfAbsent(u, this) != null) {
            token = null;
            err("Tài khoản đang được đăng nhập ở nơi khác");
            return;
        }
        username = u;
        send(new Packet("LOGIN_OK").put("token", token).put("udp", ChatServer.UDP_PORT));
        System.out.println("[+] " + u + " đăng nhập từ " + sock.getRemoteSocketAddress());
        srv.pushGroups(u);
        srv.broadcastUsers();
    }

    private void createGroup(Packet p) throws Exception {
        String name = p.get("name");
        if (!name.matches(NAME_RE)) {
            err("Tên nhóm 2-20 ký tự (chữ, số, _ . -), không có khoảng trắng");
            return;
        }
        List<String> members = new ArrayList<>();
        for (String m : p.get("members").split(",")) if (!m.isBlank()) members.add(m.trim());
        String e = srv.db.createGroup(name, username, members);
        if (e != null) {
            err(e);
            return;
        }
        for (String m : srv.db.members(name)) srv.pushGroups(m);
        send(new Packet("INFO").put("text", "Đã tạo nhóm " + name));
    }

    private static String safeName(String n) {
        n = n.replaceAll("[\\\\/:*?\"<>|]", "_");
        if (n.isBlank()) n = "file";
        return n.length() > 100 ? n.substring(n.length() - 100) : n;
    }

    private Packet toPacket(Msg m) {
        return new Packet("MSG").put("id", m.id()).put("from", m.sender()).put("kind", m.kind())
                .put("to", m.target()).put("media", m.media()).put("text", m.content())
                .put("fname", m.fileName()).put("time", FMT.format(m.time().toLocalDateTime()));
    }

    private void message(Packet p) throws Exception {
        String kind = p.get("kind"), to = p.get("to");
        String media = p.get("media").isEmpty() ? "TEXT" : p.get("media");
        String text = p.get("text");
        if (!KINDS.contains(kind) || !MEDIA.contains(media)) {
            err("Tin nhắn không hợp lệ");
            return;
        }
        if (text.length() > 5000) {
            err("Tin nhắn quá dài (tối đa 5000 ký tự)");
            return;
        }
        switch (kind) {
            case "PRIVATE" -> {
                if (to.equals(username) || !srv.db.userExists(to)) {
                    err("Người nhận không hợp lệ");
                    return;
                }
            }
            case "GROUP" -> {
                if (!srv.db.members(to).contains(username)) {
                    err("Bạn không thuộc nhóm này");
                    return;
                }
            }
            default -> to = "*";
        }
        String fname = null, path = null;
        if (!media.equals("TEXT")) {
            if (p.data == null || p.data.length == 0) {
                err("Thiếu dữ liệu đính kèm");
                return;
            }
            fname = safeName(p.get("fname"));
            path = srv.saveFile(fname, p.data);
        } else if (text.isBlank()) {
            return;
        }
        Msg m = srv.db.saveMessage(username, kind, to, media, text, fname, path);
        Packet pk = toPacket(m);

        Collection<String> recipients;
        switch (kind) {
            case "PRIVATE" -> recipients = List.of(username, to);
            case "GROUP" -> recipients = srv.db.members(to);          // MULTICAST
            default -> recipients = new ArrayList<>(srv.online.keySet()); // BROADCAST
        }
        for (String r : recipients) {
            ClientHandler h = srv.online.get(r);
            if (h != null) h.trySend(pk);
        }
    }

    private void history(Packet p) throws Exception {
        String kind = p.get("kind"), target = p.get("target");
        if (!KINDS.contains(kind)) return;
        if (kind.equals("GROUP") && !srv.db.members(target).contains(username)) {
            err("Bạn không thuộc nhóm này");
            return;
        }
        send(new Packet("HISTORY_BEGIN").put("kind", kind).put("target", target));
        for (Msg m : srv.db.history(username, kind, target)) send(toPacket(m).put("h", "1"));
        send(new Packet("HISTORY_END"));
    }

    private void getFile(Packet p) throws Exception {
        Msg m = srv.db.getMessage(p.getLong("id"));
        if (m == null || m.filePath() == null || !srv.db.canAccess(username, m)) {
            err("Không tìm thấy tệp");
            return;
        }
        send(new Packet("FILE_DATA").put("rid", p.get("rid")).put("id", m.id())
                .put("fname", m.fileName()).put("media", m.media())
                .data(Files.readAllBytes(Path.of(m.filePath()))));
    }
}
