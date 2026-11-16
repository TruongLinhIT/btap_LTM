import java.rmi.Naming;
import java.util.Scanner;

public class ClientMain {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);

        System.out.print("Nhập IP Server (Enter lấy mặc định localhost): ");
        String serverIp = sc.nextLine().trim();
        if (serverIp.isEmpty()) serverIp = "localhost";

        System.out.print("Nhập Port Server (Enter lấy mặc định 1099): ");
        String portInput = sc.nextLine().trim();
        int serverPort = portInput.isEmpty() ? 1099 : Integer.parseInt(portInput);

        try {
            String rmiUrl = "rmi://" + serverIp + ":" + serverPort + "/BankService";
            IBankService bank = (IBankService) Naming.lookup(rmiUrl);

            System.out.println("--- ĐĂNG NHẬP NGÂN HÀNG ---");
            System.out.print("Tài khoản (user1 / user2): ");
            String username = sc.nextLine();
            System.out.print("Mật khẩu (mặc định 123): ");
            String password = sc.nextLine();

            String loginResult = bank.login(username, password);

            if (loginResult.equals("SAI_THONG_TIN")) {
                System.out.println("Lỗi: Sai tài khoản hoặc mật khẩu!");
                return;
            } else if (loginResult.equals("DANG_DANG_NHAP_NOI_KHAC")) {
                System.out.println("CẢNH BÁO: Tài khoản này đang được đăng nhập ở một thiết bị khác! Vui lòng thử lại sau.");
                return;
            }

            System.out.println("Đăng nhập thành công!");

            // Vòng lặp menu giao dịch
            while (true) {
                System.out.println("\n--- MENU (" + username + ") ---");
                System.out.println("1. Xem số dư");
                System.out.println("2. Chuyển tiền");
                System.out.println("3. Đăng xuất");
                System.out.print("Chọn: ");
                String choice = sc.nextLine();

                if (choice.equals("1")) {
                    double balance = bank.getBalance(username);
                    System.out.println("-> Số dư hiện tại: " + balance + " VNĐ");
                } else if (choice.equals("2")) {
                    System.out.print("Nhập tài khoản người nhận: ");
                    String toUser = sc.nextLine();
                    System.out.print("Nhập số tiền cần chuyển: ");
                    double amount = Double.parseDouble(sc.nextLine());

                    String msg = bank.transfer(username, toUser, amount);
                    System.out.println("-> Kết quả: " + msg);
                } else if (choice.equals("3")) {
                    bank.logout(username);
                    System.out.println("Đã đăng xuất an toàn.");
                    break;
                }
            }

        } catch (Exception e) {
            System.out.println("Lỗi kết nối RMI: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
