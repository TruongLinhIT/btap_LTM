package browser;

import java.net.InetAddress;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Phân tích cấu trúc URL và phân giải tên miền DNS ra địa chỉ IP (kế thừa url.diachi & url.inet).
 */
public class UrlDnsInfo {
    private String rawUrl;
    private String protocol;
    private String host;
    private int port;
    private int defaultPort;
    private String path;
    private String file;
    private String query;
    private String ref;
    private String primaryIp;
    private List<String> allIps = new ArrayList<>();
    private String error;

    public static UrlDnsInfo analyze(String urlString) {
        UrlDnsInfo info = new UrlDnsInfo();
        info.rawUrl = urlString;
        try {
            if (!urlString.startsWith("http://") && !urlString.startsWith("https://")) {
                urlString = "http://" + urlString;
            }
            URL u = new URL(urlString);
            info.protocol = u.getProtocol();
            info.host = u.getHost();
            info.port = u.getPort();
            info.defaultPort = u.getDefaultPort();
            info.path = u.getPath();
            info.file = u.getFile();
            info.query = u.getQuery();
            info.ref = u.getRef();

            if (info.host != null && !info.host.isEmpty()) {
                InetAddress[] addresses = InetAddress.getAllByName(info.host);
                if (addresses.length > 0) {
                    info.primaryIp = addresses[0].getHostAddress();
                    for (InetAddress addr : addresses) {
                        info.allIps.add(addr.getHostAddress());
                    }
                }
            }
        } catch (Exception e) {
            info.error = e.getMessage();
        }
        return info;
    }

    public String getFormattedSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== 1. PHÂN TÍCH CẤU TRÚC URL (Kế thừa url.diachi) ===\n");
        sb.append("URL gốc      : ").append(rawUrl).append("\n");
        sb.append("Giao thức    : ").append(protocol != null ? protocol.toUpperCase() : "N/A").append("\n");
        sb.append("Tên miền Host: ").append(host != null ? host : "N/A").append("\n");
        sb.append("Cổng (Port)  : ").append(port != -1 ? port : (defaultPort + " (Mặc định)")).append("\n");
        sb.append("Đường dẫn    : ").append(path != null && !path.isEmpty() ? path : "/").append("\n");
        sb.append("Tệp (File)   : ").append(file != null ? file : "").append("\n");
        sb.append("Truy vấn (?) : ").append(query != null ? query : "Không có").append("\n");
        sb.append("Neo (#Ref)   : ").append(ref != null ? ref : "Không có").append("\n\n");

        sb.append("=== 2. PHÂN GIẢI TÊN MIỀN DNS IP (Kế thừa url.inet) ===\n");
        if (error != null) {
            sb.append("Lỗi phân giải DNS: ").append(error).append("\n");
        } else {
            sb.append("Địa chỉ IP chính : ").append(primaryIp != null ? primaryIp : "Unknown").append("\n");
            sb.append("Danh sách IP (InetAddress.getAllByName):\n");
            for (String ip : allIps) {
                sb.append("  • ").append(ip).append("\n");
            }
        }
        return sb.toString();
    }

    public String getPrimaryIp() {
        return primaryIp != null ? primaryIp : (error != null ? "DNS Error" : "127.0.0.1");
    }

    public String getHost() {
        return host;
    }

    public int getEffectivePort() {
        if (port != -1) return port;
        if ("https".equalsIgnoreCase(protocol)) return 443;
        return 80;
    }

    public String getProtocol() {
        return protocol;
    }
}
