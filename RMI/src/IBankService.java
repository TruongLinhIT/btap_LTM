import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

public interface IBankService extends Remote {
    // --- CÁC HÀM PHỤC VỤ CLIENT GIAO DỊCH ---
    String login(String username, String password) throws RemoteException;
    void logout(String username) throws RemoteException;
    double getBalance(String username) throws RemoteException;
    String transfer(String fromUser, String toUser, double amount) throws RemoteException;

    // --- CÁC HÀM ĐỒNG BỘ 2 CHIỀU GIỮA 2 SERVER ---
    void syncLockStatus(String username, boolean isLocked) throws RemoteException;
    void syncBalance(String fromUser, double newFromBalance, String toUser, double newToBalance) throws RemoteException;

    // Đồng bộ toàn bộ danh sách tài khoản khi phát hiện CSDL có thay đổi
    void syncDatabase(List<Account> accounts) throws RemoteException;
}
