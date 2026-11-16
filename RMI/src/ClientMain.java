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
                System.out.println("1. Xem số dư & thông tin cá nhân");
                System.out.println("2. Chuyển tiền");
                System.out.println("3. Chỉnh sửa thông tin cá nhân");
                System.out.println("4. Xem danh sách thông tin các user (Đồng bộ)");
                System.out.println("5. Đăng xuất");
                System.out.print("Chọn: ");
                String choice = sc.nextLine();

                if (choice.equals("1")) {
                    Account info = bank.getAccountInfo(username);
                    if (info != null) {
                        System.out.println("-> Tài khoản: " + info.getUsername());
                        System.out.println("-> Họ và tên: " + info.getFullName());
                        System.out.println("-> Email    : " + info.getEmail());
                        System.out.println("-> SĐT      : " + info.getPhone());
                        System.out.println("-> Số dư    : " + info.getBalance() + " VNĐ");
                    }
                } else if (choice.equals("2")) {
                    System.out.print("Nhập tài khoản người nhận: ");
                    String toUser = sc.nextLine();
                    System.out.print("Nhập số tiền cần chuyển: ");
                    double amount = Double.parseDouble(sc.nextLine());

                    String msg = bank.transfer(username, toUser, amount);
                    System.out.println("-> Kết quả: " + msg);
                } else if (choice.equals("3")) {
                    Account cur = bank.getAccountInfo(username);
                    System.out.println("--- CHỈNH SỬA THÔNG TIN CÁ NHÂN ---");
                    System.out.print("Họ và tên [" + (cur != null ? cur.getFullName() : "") + "]: ");
                    String fullName = sc.nextLine().trim();
                    if (fullName.isEmpty() && cur != null) fullName = cur.getFullName();

                    System.out.print("Email [" + (cur != null ? cur.getEmail() : "") + "]: ");
                    String email = sc.nextLine().trim();
                    if (email.isEmpty() && cur != null) email = cur.getEmail();

                    System.out.print("Số điện thoại [" + (cur != null ? cur.getPhone() : "") + "]: ");
                    String phone = sc.nextLine().trim();
                    if (phone.isEmpty() && cur != null) phone = cur.getPhone();

                    System.out.print("Mật khẩu mới (Enter để giữ nguyên): ");
                    String newPass = sc.nextLine().trim();

                    String res = bank.updateUserInfo(username, fullName, email, phone, newPass);
                    if ("OK".equals(res)) {
                        System.out.println("-> Đã cập nhật và đồng bộ thông tin thành công!");
                    } else {
                        System.out.println("-> Lỗi: " + res);
                    }
                } else if (choice.equals("4")) {
                    java.util.List<Account> profiles = bank.getAllUserProfiles();
                    System.out.println("--- DANH SÁCH KHÁCH HÀNG ---");
                    for (Account acc : profiles) {
                        System.out.printf("- %s | Họ tên: %s | Email: %s | SĐT: %s | Trạng thái: %s%n",
                                acc.getUsername(), acc.getFullName(), acc.getEmail(), acc.getPhone(),
                                acc.isLocked() ? "Đang online" : "Offline");
                    }
                } else if (choice.equals("5")) {
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
