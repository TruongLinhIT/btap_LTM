package common;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Gói tin dùng chung cho kênh TCP giữa Client và Server.
 * Định dạng: [type:UTF][số field:int]([key:UTF][value:UTF])*[độ dài data:int][data:byte[]]
 */
public class Packet {
    public static final int MAX_DATA = 64 * 1024 * 1024;

    public final String type;
    private final Map<String, String> fields = new LinkedHashMap<>();
    public byte[] data;

    public Packet(String type) {
        this.type = type;
    }

    public Packet put(String key, String value) {
        fields.put(key, value == null ? "" : value);
        return this;
    }

    public Packet put(String key, long value) {
        return put(key, Long.toString(value));
    }

    public Packet data(byte[] d) {
        this.data = d;
        return this;
    }

    public String get(String key) {
        return fields.getOrDefault(key, "");
    }

    public long getLong(String key) {
        try {
            return Long.parseLong(get(key));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public void write(DataOutputStream out) throws IOException {
        out.writeUTF(type);
        out.writeInt(fields.size());
        for (Map.Entry<String, String> e : fields.entrySet()) {
            out.writeUTF(e.getKey());
            out.writeUTF(e.getValue());
        }
        if (data == null) {
            out.writeInt(0);
        } else {
            out.writeInt(data.length);
            out.write(data);
        }
        out.flush();
    }

    public static Packet read(DataInputStream in) throws IOException {
        Packet p = new Packet(in.readUTF());
        int n = in.readInt();
        for (int i = 0; i < n; i++) {
            String k = in.readUTF();
            p.fields.put(k, in.readUTF());
        }
        int len = in.readInt();
        if (len < 0 || len > MAX_DATA) throw new IOException("Kích thước dữ liệu không hợp lệ: " + len);
        p.data = new byte[len];
        in.readFully(p.data);
        return p;
    }
}
