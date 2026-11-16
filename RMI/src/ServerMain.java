import java.net.DatagramSocket;
import java.net.InetAddress;
import java.rmi.Naming;
import java.rmi.registry.LocateRegistry;

public class ServerMain {
   
    private static final String PEER_IP = "10.225.243.198"; 

    // =========================================================================
    // 2. CẤU HÌNH RMI SERVER CỤC BỘ
    // =========================================================================
    private static final String SERVER_NAME = "BankServer";
    private static final int RMI_PORT = 1099;

    // =========================================================================
    // 3. CẤU HÌNH CSDL MYSQL DOCKER
    // =========================================================================
    private static final String DB_HOST = "localhost";
    private static final int DB_PORT = 3308;
    private static final String DB_NAME = "rmi_db";
    private static final String DB_USER = "root";
    private static final String DB_PASS = "root123";

    public static void main(String[] args) {
        try {
            System.out.println("==========================================");
            System.out.println("⏳ Đang khởi động " + SERVER_NAME + "...");

            // 1. Kết nối CSDL MySQL Docker của máy này
            DBManager db = new DBManager(DB_HOST, DB_PORT, DB_NAME, DB_USER, DB_PASS);
            if (!db.testConnection()) {
                System.err.println("❌ Không thể kết nối Database MySQL!");
                System.err.println("👉 Hãy đảm bảo Docker đang chạy: docker compose up -d");
                return;
            }
            db.resetAllLocks(); // Reset mở khóa các tài khoản tránh kẹt lock
            System.out.println("✅ Đã kết nối Database (" + DB_NAME + ") thành công!");

            // 2. Tự động xác định IP mạng LAN thực tế của máy này
            String myIp = detectLocalIp();
            System.setProperty("java.rmi.server.hostname", myIp);

            // 3. Khởi tạo RMI Registry
            try {
                LocateRegistry.createRegistry(RMI_PORT);
            } catch (Exception e) {
                // Registry đã mở từ trước
            }

            // 4. Chuẩn hóa URL Server đối tác (nếu có)
            String peerRmiUrl = "";
            if (PEER_IP != null && !PEER_IP.trim().isEmpty()) {
                String cleanPeerIp = PEER_IP.trim().replace("rmi://", "");
                if (cleanPeerIp.contains("/")) {
                    cleanPeerIp = cleanPeerIp.substring(0, cleanPeerIp.indexOf("/"));
                }
                if (!cleanPeerIp.contains(":")) {
                    cleanPeerIp = cleanPeerIp + ":" + RMI_PORT;
                }
                peerRmiUrl = "rmi://" + cleanPeerIp + "/BankService";
            }

            // 5. Đăng ký dịch vụ RMI
            String myRmiUrl = "rmi://" + myIp + ":" + RMI_PORT + "/BankService";
            IBankService service = new BankServerImpl(SERVER_NAME, db, peerRmiUrl);
            Naming.rebind(myRmiUrl, service);

            System.out.println("🚀 " + SERVER_NAME + " đã sẵn sàng!");
            System.out.println("📍 Địa chỉ RMI của máy này: " + myRmiUrl);
            if (!peerRmiUrl.isEmpty()) {
                System.out.println("🔄 Đang đồng bộ 2 chiều với đối tác: " + peerRmiUrl);
            } else {
                System.out.println("ℹ️  Chế độ độc lập (chưa cấu hình PEER_IP)");
            }
            System.out.println("==========================================");

        } catch (Exception e) {
            System.err.println("❌ Lỗi khởi động Server: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Tự động tìm IP IPv4 mạng LAN thật của máy tính
    private static String detectLocalIp() {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.connect(InetAddress.getByName("8.8.8.8"), 10002);
            return socket.getLocalAddress().getHostAddress();
        } catch (Exception e) {
            try {
                return InetAddress.getLocalHost().getHostAddress();
            } catch (Exception ex) {
                return "localhost";
            }
        }
    }
}
