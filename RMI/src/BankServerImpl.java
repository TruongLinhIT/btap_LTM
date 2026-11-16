import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.List;

public class BankServerImpl extends UnicastRemoteObject implements IBankService {
    private DBManager db;
    private String peerRmiUrl;
    private String serverName;

    
    private List<Account> lastAccounts = new ArrayList<>();

    public BankServerImpl(String serverName, DBManager db, String peerRmiUrl) throws RemoteException {
        super();
        this.serverName = serverName;
        this.db = db;
        this.peerRmiUrl = peerRmiUrl;

        
        this.lastAccounts = db.getAllAccounts();

       
        startAutoSyncThread();
    }

    // =========================================================================
    // LUỒNG TỰ ĐỘNG ĐỒNG BỘ CSDL (ĐƠN GIẢN, DỄ HIỂU)
    // =========================================================================
    private void startAutoSyncThread() {
        Thread syncThread = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(3000); 
                    checkAndSyncDatabase();
                } catch (Exception ignored) {
                }
            }
        });
        syncThread.setDaemon(true); 
        syncThread.start();
    }

    
    private void checkAndSyncDatabase() {
        if (peerRmiUrl == null || peerRmiUrl.trim().isEmpty()) {
            return;
        }

        List<Account> currentAccounts = db.getAllAccounts();

        // So sánh danh sách hiện tại trong CSDL với danh sách cũ
        if (isDatabaseChanged(lastAccounts, currentAccounts)) {
            System.out.println("[" + serverName + "] ⚡ Phát hiện CSDL bị sửa đổi trực tiếp -> Đang đồng bộ sang Server kia...");
            
            // Cập nhật lại danh sách của mình
            this.lastAccounts = currentAccounts;

            // Gọi RMI gửi toàn bộ danh sách mới sang Server đối tác
            IBankService peer = getPeerServer();
            if (peer != null) {
                try {
                    peer.syncDatabase(currentAccounts);
                } catch (Exception e) {
                    System.out.println("[" + serverName + "] ⚠️  Server đối tác chưa sẵn sàng để đồng bộ.");
                }
            }
        }
    }

    // Hàm so sánh 2 danh sách tài khoản xem có sự thay đổi nào không
    private boolean isDatabaseChanged(List<Account> oldList, List<Account> newList) {
        if (oldList.size() != newList.size()) {
            return true; // Số lượng tài khoản thay đổi (vừa thêm tài khoản mới)
        }
        for (int i = 0; i < oldList.size(); i++) {
            if (!oldList.get(i).isSame(newList.get(i))) {
                return true; // Có tài khoản bị sửa số dư, mật khẩu hoặc trạng thái khóa
            }
        }
        return false;
    }

   
    @Override
    public synchronized void syncDatabase(List<Account> accounts) throws RemoteException {
        // 1. Lưu các tài khoản nhận được vào MySQL của máy này
        for (Account acc : accounts) {
            db.saveOrUpdateAccount(acc);
        }

    
        this.lastAccounts = accounts;

        System.out.println("[" + serverName + "] 🔄 Đã đồng bộ CSDL thành công từ Server đối tác!");
    }

    // =========================================================================
    // CÁC HÀM GIAO DỊCH BÌNH THƯỜNG CỦA NGÂN HÀNG (LOGIN, LOGOUT, CHUYỂN TIỀN)
    // =========================================================================

    private IBankService getPeerServer() {
        if (peerRmiUrl == null || peerRmiUrl.trim().isEmpty()) {
            return null;
        }
        try {
            return (IBankService) Naming.lookup(peerRmiUrl);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public synchronized String login(String username, String password) throws RemoteException {
        Account acc = db.getAccount(username);

        if (acc == null || !acc.getPassword().equals(password)) {
            return "SAI_THONG_TIN";
        }

        if (acc.isLocked()) {
            return "DANG_DANG_NHAP_NOI_KHAC";
        }

        // 1. Khóa tài khoản trong CSDL cục bộ
        db.updateLockStatus(username, true);
        this.lastAccounts = db.getAllAccounts(); // Cập nhật lại danh sách
        System.out.println("[" + serverName + "] 👤 User '" + username + "' đã đăng nhập -> Đã khóa CSDL.");

        // 2. Đồng bộ khóa sang Server đối tác
        IBankService peer = getPeerServer();
        if (peer != null) {
            try {
                peer.syncLockStatus(username, true);
            } catch (Exception ignored) {}
        }

        return "OK";
    }

    @Override
    public synchronized void logout(String username) throws RemoteException {
        db.updateLockStatus(username, false);
        this.lastAccounts = db.getAllAccounts();
        System.out.println("[" + serverName + "] 🔓 User '" + username + "' đã đăng xuất.");

        IBankService peer = getPeerServer();
        if (peer != null) {
            try {
                peer.syncLockStatus(username, false);
            } catch (Exception ignored) {}
        }
    }

    @Override
    public synchronized double getBalance(String username) throws RemoteException {
        Account acc = db.getAccount(username);
        return (acc != null) ? acc.getBalance() : 0;
    }

    @Override
    public synchronized String transfer(String fromUser, String toUser, double amount) throws RemoteException {
        if (amount <= 0) return "Số tiền chuyển phải lớn hơn 0!";
        if (fromUser.equalsIgnoreCase(toUser)) return "Không thể tự chuyển tiền cho chính mình!";

        Account fromAcc = db.getAccount(fromUser);
        Account toAcc = db.getAccount(toUser);

        if (fromAcc == null) return "Tài khoản người gửi không tồn tại!";
        if (toAcc == null) return "Tài khoản người nhận không tồn tại!";
        if (fromAcc.getBalance() < amount) return "Số dư không đủ để thực hiện giao dịch!";

        double newFromBalance = fromAcc.getBalance() - amount;
        double newToBalance = toAcc.getBalance() + amount;

        // 1. Cập nhật vào CSDL cục bộ
        boolean ok = db.updateTransfer(fromUser, newFromBalance, toUser, newToBalance);
        if (!ok) return "Lỗi CSDL khi thực hiện giao dịch!";

        this.lastAccounts = db.getAllAccounts();
        System.out.printf("[%s] 💸 Đã chuyển %,.0f VNĐ từ %s -> %s\n", serverName, amount, fromUser, toUser);

        // 2. Đồng bộ sang Server đối tác
        IBankService peer = getPeerServer();
        if (peer != null) {
            try {
                peer.syncBalance(fromUser, newFromBalance, toUser, newToBalance);
            } catch (Exception ignored) {}
        }

        return "OK";
    }

    @Override
    public synchronized void syncLockStatus(String username, boolean isLocked) throws RemoteException {
        db.updateLockStatus(username, isLocked);
        this.lastAccounts = db.getAllAccounts();
        System.out.println("[" + serverName + " - 🔄 ĐỒNG BỘ] Cập nhật is_locked = " + isLocked + " cho user '" + username + "'");
    }

    @Override
    public synchronized void syncBalance(String fromUser, double newFromBalance, String toUser, double newToBalance) throws RemoteException {
        db.updateTransfer(fromUser, newFromBalance, toUser, newToBalance);
        this.lastAccounts = db.getAllAccounts();
        System.out.printf("[%s - 🔄 ĐỒNG BỘ] Số dư mới: %s = %,.0f VNĐ, %s = %,.0f VNĐ\n", 
                serverName, fromUser, newFromBalance, toUser, newToBalance);
    }
}
