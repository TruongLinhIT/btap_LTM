package browser;

import java.net.InetAddress;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Lop ket noi giua kien thuc package url (diachi.java va inet.java)
 * giup trinh duyet phan tich URL va tra cuu DNS IP.
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
        sb.append("=== PHAN TICH URL (Tuong tu diachi.java) ===\n");
        sb.append("URL: ").append(rawUrl).append("\n");
        sb.append("Protocol: ").append(protocol != null ? protocol : "N/A").append("\n");
        sb.append("Host: ").append(host != null ? host : "N/A").append("\n");
        sb.append("Port: ").append(port != -1 ? port : (defaultPort + " (Default)")).append("\n");
        sb.append("Path: ").append(path != null ? path : "/").append("\n");
        sb.append("File: ").append(file != null ? file : "").append("\n");
        sb.append("Query: ").append(query != null ? query : "Khong co").append("\n");
        sb.append("Ref (Anchor): ").append(ref != null ? ref : "Khong co").append("\n\n");

        sb.append("=== TRA CUU DNS / IP (Tuong tu inet.java) ===\n");
        if (error != null) {
            sb.append("Loi DNS: ").append(error).append("\n");
        } else {
            sb.append("IP chinh: ").append(primaryIp != null ? primaryIp : "Unknown").append("\n");
            sb.append("Tat ca cac dia chi IP tim thay (getAllByName):\n");
            for (String ip : allIps) {
                sb.append("  - ").append(ip).append("\n");
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
