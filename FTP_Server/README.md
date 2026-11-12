# BÁO CÁO BÀI TẬP LỚN LẬP TRÌNH MẠNG (LTM)
## ĐỀ TÀI: FTP SERVER CÓ DATABASE & GIAO THỨC FTP CHUẨN (TƯƠNG TỰ FILEZILLA)

---

### 1. Giới thiệu & Đáp ứng yêu cầu đề bài (Theo ảnh đính kèm)
* **Số thứ tự đề tài**: `2`
* **Lựa chọn**: `FTP Server `
* **Giao thức FTP**: Triển khai đầy đủ và tuân thủ chặt chẽ chuẩn giao thức **RFC 959** và **RFC 3659** (USER, PASS, SYST, FEAT, PWD, CWD, CDUP, TYPE, PASV, EPSV, PORT, EPRT, LIST, NLST, MLSD, RETR, STOR, APPE, DELE, MKD, RMD, RNFR, RNTO, SIZE, MDTM, REST, NOOP, QUIT).
* **Server có Database**: Tích hợp trực tiếp với cơ sở dữ liệu **MySQL** đang chạy trên Docker container (`ltm-mysql`, cổng `3308`, quản lý qua phpMyAdmin `http://localhost:8085`).
  - Quản lý xác thực tài khoản (`ftp_users`), băm mật khẩu bảo mật `SHA-256 + Salt`.
  - Phân quyền chi tiết từng người dùng: Quyền tải về (Download), Tải lên (Upload), Xóa tệp (Delete), Tạo thư mục/Đổi tên (Modify), Kích hoạt tài khoản (Active).
  - Ghi nhật ký kiểm toán và lịch sử truyền tải tự động vào MySQL (`ftp_logs`).
  - Lưu trữ cấu hình hệ thống (`ftp_settings`).
* **Tương tự FileZilla**:
  - Giao diện quản lý máy chủ đồ họa chuyên nghiệp (GUI Swing) tương tự **FileZilla Server**: Dashboard giám sát kết nối theo thời gian thực, ngắt kết nối (kick client), console lệnh trực tiếp, quản lý người dùng, tra cứu nhật ký MySQL.
  - Cho phép bất kỳ phần mềm **FileZilla Client**, **WinSCP** hoặc lệnh `ftp` trên hệ điều hành kết nối vào và thực hiện thao tác tệp mượt mà.
  - Tích hợp sẵn một tab **FTP Client (Tương tự FileZilla Client)** ngay trong ứng dụng để kiểm thử truyền tải tệp tức thì.

---

### 2. Cấu hình Docker & MySQL (Container có sẵn ở thư mục cha `LTM`)
Trong file `d:\LTM\docker-compose.yml`:
* **MySQL Container**: `ltm-mysql`
  - Cổng Host: `3308` (Cổng container: `3306`)
  - Tài khoản Root: `root`
  - Mật khẩu Root: `root123`
  - Tên Cơ sở dữ liệu: `ftp_db`
* **phpMyAdmin Container**: `ltm-phpmyadmin`
  - Truy cập trình duyệt web: `http://localhost:8085`

---

### 3. Cấu trúc Database (`ftp_db`)

#### 3.1. Bảng `ftp_users` (Quản lý tài khoản & Phân quyền)
| Tên cột | Kiểu dữ liệu | Ý nghĩa |
|---|---|---|
| `id` | `INT AUTO_INCREMENT PRIMARY KEY` | Khóa chính |
| `username` | `VARCHAR(50) UNIQUE` | Tên đăng nhập |
| `password_hash` | `VARCHAR(128)` | Mật khẩu đã băm (SHA-256 + Salt) |
| `salt` | `VARCHAR(64)` | Chuỗi muối tạo ngẫu nhiên |
| `home_directory` | `VARCHAR(255)` | Đường dẫn thư mục gốc vật lý |
| `can_upload` | `TINYINT(1)` | Cho phép tải tệp lên (STOR/APPE) |
| `can_download` | `TINYINT(1)` | Cho phép tải tệp về (RETR) |
| `can_delete` | `TINYINT(1)` | Cho phép xóa tệp/thư mục (DELE/RMD) |
| `can_modify` | `TINYINT(1)` | Cho phép tạo thư mục/đổi tên (MKD/RNTO) |
| `is_active` | `TINYINT(1)` | Trạng thái tài khoản (1: Hoạt động, 0: Khóa) |
| `created_at` | `TIMESTAMP` | Thời điểm tạo |

#### 3.2. Bảng `ftp_logs` (Nhật ký kiểm toán & Lịch sử truyền tệp)
| Tên cột | Kiểu dữ liệu | Ý nghĩa |
|---|---|---|
| `id` | `BIGINT AUTO_INCREMENT PRIMARY KEY` | Khóa chính |
| `username` | `VARCHAR(50)` | Tài khoản thực hiện |
| `client_ip` | `VARCHAR(64)` | IP của Client kết nối |
| `command` | `VARCHAR(50)` | Lệnh FTP (LOGIN, RETR, STOR, DELE, v.v.) |
| `path` | `VARCHAR(500)` | Đường dẫn tệp/thư mục thao tác |
| `file_size` | `BIGINT` | Dung lượng truyền tải (bytes) |
| `status` | `VARCHAR(20)` | `SUCCESS`, `FAILED`, `DENIED` |
| `message` | `VARCHAR(500)` | Mô tả chi tiết hành động |
| `timestamp` | `TIMESTAMP` | Thời điểm diễn ra |

---

### 4. Tài khoản mẫu có sẵn khi khởi động lần đầu
Khi khởi chạy lần đầu, server sẽ tự động tạo các tài khoản mẫu trong MySQL:
1. **admin**:
   - Mật khẩu: `admin123`
   - Thư mục: `ftp_root/admin`
   - Quyền hạn: Toàn quyền (Tải về, Tải lên, Xóa, Đổi tên/Tạo thư mục).
2. **student**:
   - Mật khẩu: `123456`
   - Thư mục: `ftp_root/student`
   - Quyền hạn: Toàn quyền.
3. **anonymous**:
   - Mật khẩu: `anonymous`
   - Thư mục: `ftp_root/public`
   - Quyền hạn: Chỉ được xem và tải về (Read-only).

*(Bạn cũng có thể thêm/sửa/xóa tài khoản trực tiếp trên giao diện Server hoặc qua giao diện phpMyAdmin tại `http://localhost:8085`)*

---

### 5. Cấu trúc thư mục dự án
```
d:\LTM\FTP_Server
├── .idea/                         # Cấu hình IntelliJ IDEA
├── lib/
│   └── mysql-connector-j-8.4.0.jar# JDBC Driver kết nối MySQL 8
├── ftp_root/                      # Thư mục chứa dữ liệu FTP thực tế trên đĩa
│   ├── admin/                     # Thư mục của tài khoản admin
│   ├── public/                    # Thư mục công cộng (anonymous)
│   └── student/                   # Thư mục của tài khoản student
├── src/
│   ├── Main.java                  # Điểm khởi chạy chính (Chạy GUI hoặc CLI)
│   ├── FtpIntegrationTest.java    # Chương trình kiểm thử tự động toàn diện
│   ├── config/
│   │   └── ServerConfig.java      # Quản lý file cấu hình & JDBC URL
│   ├── database/
│   │   ├── DatabaseManager.java   # Xử lý kết nối MySQL, CRUD user & audit logs
│   │   ├── UserModel.java         # Đối tượng người dùng
│   │   └── FtpLogModel.java       # Đối tượng nhật ký
│   ├── server/
│   │   ├── FtpServer.java         # Socket Server chính, quản lý kết nối TCP
│   │   ├── FtpClientHandler.java  # Xử lý chu trình các lệnh chuẩn RFC 959
│   │   ├── FtpSession.java        # Lưu phiên làm việc của từng Client
│   │   ├── FtpFileSystem.java     # Sandboxing ảo hóa đường dẫn, chống Jailbreak
│   │   ├── FtpDataConnection.java # Quản lý Passive (PASV) & Active (PORT) mode
│   │   └── FtpEventListener.java  # Bắt sự kiện gửi về UI/Console
│   ├── client/
│   │   ├── SimpleFtpClient.java   # Client FTP chuẩn dùng cho Test Tool
│   │   └── FtpFileItem.java       # Đối tượng tệp tin trả về từ lệnh LIST
│   └── ui/
│       ├── MainServerFrame.java   # Giao diện chính FileZilla Server-like
│       └── UserDialog.java        # Hộp thoại Thêm/Sửa người dùng
├── build.bat                      # File biên dịch dự án bằng Java 27
├── run.bat                        # File chạy Server với giao diện đồ họa GUI
├── run-cli.bat                    # File chạy Server ở chế độ dòng lệnh Console
├── init.sql                       # File SQL khởi tạo cơ sở dữ liệu
└── README.md                      # Hướng dẫn chi tiết
```

---

### 6. Hướng dẫn Biên dịch và Chạy chương trình

#### Cách 1: Chạy bằng file Batch (Khuyên dùng)
1. **Biên dịch mã nguồn**:
   Nhấp đúp chuột vào file **`build.bat`** (hoặc mở cmd chạy `build.bat`).
2. **Khởi chạy FTP Server (Giao diện đồ họa GUI)**:
   Nhấp đúp chuột vào file **`run.bat`**.
3. **Khởi chạy FTP Server (Dòng lệnh Console)**:
   Nhấp đúp chuột vào file **`run-cli.bat`**.

#### Cách 2: Mở trong IntelliJ IDEA
1. Mở thư mục `d:\LTM\FTP_Server` trong IntelliJ IDEA.
2. IntelliJ sẽ tự động nhận diện Project SDK Java 27 và thư viện trong thư mục `lib/`.
3. Mở file `src/Main.java` và nhấn nút **Run** (mũi tên xanh).

---

### 7. Hướng dẫn Kết nối bằng phần mềm FileZilla Client
1. Mở phần mềm **FileZilla Client**.
2. Trên thanh **Quickconnect (Kết nối nhanh)** nhập:
   - **Host**: `127.0.0.1` (hoặc `localhost`)
   - **Username**: `admin`
   - **Password**: `admin123`
   - **Port**: `21` (hoặc `2121` tùy theo cổng hiển thị trên Server)
3. Hoặc vào **File -> Site Manager -> New Site**:
   - **Protocol**: `FTP - File Transfer Protocol`
   - **Host**: `127.0.0.1`, **Port**: `21` (hoặc `2121`)
   - **Encryption**: `Only use plain FTP (insecure)`
   - **Logon Type**: `Normal`
   - **User**: `admin`, **Password**: `admin123`
4. Nhấn **Quickconnect / Connect**:
   - FileZilla sẽ tải danh sách cây thư mục từ máy chủ.
   - Bạn có thể kéo thả tệp từ máy để upload lên server, tải tệp về, tạo thư mục mới, đổi tên hoặc xóa tệp.
   - Toàn bộ hành động trên FileZilla sẽ hiển thị ngay lập tức trên màn hình Console và được lưu vào bảng `ftp_logs` trong MySQL!
