# BÁO CÁO BÀI TẬP LỚN LẬP TRÌNH MẠNG (LTM)
## ĐỀ TÀI: MAIL SERVER CÓ GIAO THỨC POP3 & SMTP, KẾT NỐI DATABASE MYSQL VÀ GMAIL GATEWAY (TƯƠNG TỰ GMAIL)

---

### 1. Giới thiệu & Đáp ứng yêu cầu đề bài (Theo ảnh đính kèm)
* **Số thứ tự đề bài**: `3`
* **Lựa chọn**: `Mail Server `
* **Giao thức POP3 và SMTP**: 
  - Triển khai **đầy đủ và tuân thủ chuẩn giao thức quốc tế** bằng **Java Socket thuần (TCP)** (không phụ thuộc bất kỳ thư viện bên thứ ba nào như JavaMail).
  - **SMTP Server (RFC 5321)**: Lắng nghe cổng `2525`, hỗ trợ đầy đủ các lệnh `HELO`, `EHLO`, `AUTH LOGIN`, `AUTH PLAIN`, `MAIL FROM`, `RCPT TO`, `DATA` (kèm dot-stuffing), `RSET`, `NOOP`, `VRFY`, `HELP`, `QUIT`.
  - **POP3 Server (RFC 1939)**: Lắng nghe cổng `1100`, hỗ trợ các lệnh `USER`, `PASS`, `STAT`, `LIST`, `UIDL`, `RETR`, `TOP`, `DELE`, `RSET`, `NOOP`, `CAPA`, `QUIT` và lệnh mở rộng `XFOLDER` để duyệt theo thư mục (`INBOX`, `SENT`, `TRASH`).
* **Gửi được thư trên Localhost và trên Gmail**:
  - **Gửi thư trên Localhost**: Client kết nối SMTP Server cổng `2525`, gửi thư nội bộ giữa các tài khoản dạng `ten@localhost` (ví dụ `alice@localhost` gửi cho `bob@localhost`). Thư được lưu tức thì vào hộp thư đến của người nhận trên Database MySQL.
  - **Gửi thư trên Gmail**: Khi người nhận có địa chỉ ngoài (như `@gmail.com`), Mail Server tự động chuyển tiếp (**SMTP Relay**) qua máy chủ của Google (`smtp.gmail.com:465`, mã hóa SSL/TLS, xác thực OAuth2/App Password). Đồng thời tích hợp cơ chế nhận thư từ Gmail về qua **POP3 SSL** (`pop.gmail.com:995`).
* **Có dữ liệu trên Database của Server**:
  - Kết nối trực tiếp tới container **MySQL** có sẵn trong thư mục `d:\LTM` (`ltm-mysql`, cổng `3308`, tài khoản `root/root123`).
  - Quản lý và theo dõi trực quan bằng **phpMyAdmin** chạy trên container `ltm-phpmyadmin` (cổng `8085`).
  - Toàn bộ thông tin tài khoản, danh sách email (tiêu đề, nội dung, tệp đính kèm, trạng thái đọc), nhật ký socket đều được lưu vào cơ sở dữ liệu `mail_db`.
* **_Tương tự Gmail**:
  - Cung cấp giao diện **Mail Client** chuyên nghiệp, hiện đại, màu sắc và bố cục mô phỏng chính xác **Gmail**: Nút *Soạn thư* lớn, các tab *Hộp thư đến*, *Thư đã gửi*, *Thùng rác*, ô *Tìm kiếm thư*, xem chi tiết thư kèm avatar, đính kèm tệp và tải tệp về, trả lời (Reply), chuyển tiếp (Forward).
  - Cung cấp giao diện **Mail Server Manager** (tương tự bảng điều khiển chuyên nghiệp): Dashboard thống kê thẻ chỉ số, nhật ký socket thời gian thực, quản lý người dùng MySQL, kiểm tra và thử nghiệm Gmail Gateway.

---

### 2. Cấu hình Docker & MySQL (Container có sẵn ở thư mục cha `LTM`)
Theo file cấu hình `d:\LTM\docker-compose.yml`:
* **MySQL Container**: `ltm-mysql`
  - Cổng Host: `3308` (Cổng container nội bộ: `3306`)
  - Tài khoản Root: `root`
  - Mật khẩu Root: `root123`
  - Cơ sở dữ liệu: `mail_db`
* **phpMyAdmin Container**: `ltm-phpmyadmin`
  - Truy cập trình duyệt web: **`http://localhost:8085`**
  - Đăng nhập: Server: `mysql`, User: `root`, Password: `root123`

---

### 3. Cấu trúc Database (`mail_db`)

Cơ sở dữ liệu gồm 4 bảng chính được tự động khởi tạo khi ứng dụng chạy (hoặc có thể nạp từ file `init.sql`):

#### 3.1. Bảng `users` (Quản lý tài khoản người dùng)
| Tên cột | Kiểu dữ liệu | Ý nghĩa |
|---|---|---|
| `id` | `INT AUTO_INCREMENT PRIMARY KEY` | Khóa chính ID người dùng |
| `username` | `VARCHAR(50) UNIQUE` | Tên đăng nhập (ví dụ: `admin`, `alice`, `bob`) |
| `password_hash` | `VARCHAR(128)` | Mật khẩu băm an toàn (SHA-256 + Salt) |
| `salt` | `VARCHAR(64)` | Chuỗi muối ngẫu nhiên cho mỗi tài khoản |
| `full_name` | `VARCHAR(100)` | Họ tên hiển thị trong thư |
| `is_active` | `TINYINT(1)` | Trạng thái kích hoạt (1: Hoạt động, 0: Bị khóa) |
| `created_at` | `TIMESTAMP` | Thời điểm tạo tài khoản |

#### 3.2. Bảng `emails` (Lưu trữ thư điện tử)
| Tên cột | Kiểu dữ liệu | Ý nghĩa |
|---|---|---|
| `id` | `BIGINT AUTO_INCREMENT PRIMARY KEY` | Khóa chính thư |
| `owner` | `VARCHAR(50)` | Chủ sở hữu hòm thư (`username`) |
| `folder` | `VARCHAR(10)` | Thư mục: `INBOX`, `SENT`, `TRASH` |
| `message_id` | `VARCHAR(255)` | Định danh thư RFC 5322 (Message-ID) |
| `sender` | `VARCHAR(320)` | Địa chỉ người gửi (RFC 5321) |
| `recipients` | `TEXT` | Danh sách người nhận (To/Cc) |
| `subject` | `VARCHAR(998)` | Tiêu đề thư (đã giải mã UTF-8) |
| `body_text` | `MEDIUMTEXT` | Nội dung văn bản trích xuất để tra cứu |
| `raw_message` | `LONGTEXT` | Toàn bộ thông điệp MIME gốc (RFC 5322) |
| `size_bytes` | `INT` | Kích thước bức thư (bytes) |
| `has_attachment`| `TINYINT(1)` | Có tệp đính kèm hay không (1/0) |
| `is_read` | `TINYINT(1)` | Trạng thái đã đọc (1) hay chưa đọc (0) |
| `source` | `VARCHAR(10)` | Nguồn thư: `LOCAL` (nội bộ) hoặc `GMAIL` |
| `status` | `VARCHAR(20)` | `DELIVERED` (Đã giao), `RELAYED` (Đã gửi ra Gmail), `RECEIVED` (Nhận từ Gmail) |
| `created_at` | `TIMESTAMP` | Thời điểm nhận/gửi thư |

#### 3.3. Bảng `mail_logs` (Nhật ký kiểm toán giao thức)
Lưu lại lịch sử hoạt động các lệnh gửi/nhận SMTP & POP3:
* `id`, `protocol` (SMTP / POP3 / GMAIL / SYSTEM), `client_ip`, `username`, `command`, `status`, `message`, `created_at`.

#### 3.4. Bảng `gmail_fetched` (Đồng bộ thư Gmail)
Lưu các UID thư đã tải từ máy chủ `pop.gmail.com` để tránh tải trùng lặp.

---

### 4. Tài khoản mẫu có sẵn trong hệ thống
Khi khởi động lần đầu, hệ thống tự động khởi tạo sẵn các tài khoản sau trong MySQL:
1. **`admin`** | Mật khẩu: **`admin123`** | Quản trị viên hệ thống
2. **`alice`** | Mật khẩu: **`123456`** | Người dùng Alice Nguyễn
3. **`bob`** | Mật khẩu: **`123456`** | Người dùng Bob Trần

---

### 5. Cấu trúc mã nguồn dự án

```
d:\LTM\Mail_Server\
├── .idea/                      # Cấu hình IntelliJ IDEA
├── lib/
│   └── mysql-connector-j-8.4.0.jar # Driver JDBC kết nối MySQL 8.x
├── out/                        # Thư mục chứa file class sau khi biên dịch
├── src/
│   ├── Main.java               # Điểm khởi chạy chính (Server GUI / Client GUI / CLI)
│   ├── MailIntegrationTest.java# Bộ kiểm thử tự động toàn diện
│   ├── client/                 # Tầng nghiệp vụ Mail Client
│   │   ├── MailClientService.java # Tác vụ kết nối POP3 đọc thư, SMTP gửi thư
│   │   └── MailSummary.java    # Dữ liệu tóm tắt phục vụ danh sách thư
│   ├── common/                 # Tiện ích giao thức & xử lý MIME
│   │   ├── Attachment.java     # Đại diện một tệp đính kèm (tên, MIME type, byte data)
│   │   ├── MimeUtil.java       # Bộ tạo và phân tích cú pháp MIME RFC 5322/2047/2231
│   │   └── ParsedMail.java     # Đối tượng thư đã bóc tách header, thân text/html, attachments
│   ├── config/
│   │   └── MailConfig.java     # Quản lý đọc/ghi mail.properties & kết nối MySQL
│   ├── database/               # Tầng truy cập cơ sở dữ liệu MySQL
│   │   ├── DatabaseManager.java# Thực hiện CRUD trên các bảng MySQL
│   │   ├── MailRecord.java     # Model bảng emails
│   │   └── UserRecord.java     # Model bảng users
│   ├── protocol/               # Bộ Client Socket tự cài đặt
│   │   ├── Pop3Client.java     # POP3 Client thuần Socket RFC 1939 (hỗ trợ SSL)
│   │   └── SmtpClient.java     # SMTP Client thuần Socket RFC 5321 (hỗ trợ SSL + AUTH)
│   ├── server/                 # Tầng Máy chủ Mail Server
│   │   ├── GmailGateway.java   # Cổng chuyển tiếp SMTP Relay & POP3 Fetch Gmail
│   │   ├── MailDelivery.java   # Phân luồng giao thư (nội bộ vs relay ra Gmail)
│   │   ├── MailServerManager.java # Điều phối máy chủ, log và sự kiện
│   │   ├── Pop3Server.java     # POP3 Server Socket lắng nghe cổng 1100
│   │   ├── ServerListener.java # Interface thông báo sự kiện lên giao diện
│   │   ├── SmtpServer.java     # SMTP Server Socket lắng nghe cổng 2525
│   │   └── TcpServer.java      # Lớp cơ sở TCP Server đa luồng
│   └── ui/                     # Giao diện đồ họa (Java Swing)
│       ├── client/             # Giao diện Mail Client tương tự Gmail
│       │   ├── ComposeDialog.java   # Hộp thoại soạn thư phong cách Gmail
│       │   ├── LoginFrame.java      # Màn hình đăng nhập tài khoản
│       │   ├── MailClientFrame.java # Màn hình chính Mail Client Gmail-style
│       │   └── MailDetailPanel.java # Khung hiển thị chi tiết bức thư
│       └── server/             # Giao diện Quản trị Mail Server Manager
│           ├── MainServerFrame.java # Bảng điều khiển quản lý máy chủ chuyên nghiệp
│           └── UserDialog.java      # Hộp thoại thêm/sửa người dùng MySQL
├── build.bat                   # Script biên dịch dự án bằng javac
├── run.bat                     # Chạy giao diện Quản trị Mail Server Manager (GUI)
├── run-client.bat              # Chạy giao diện Mail Client trực tiếp (GUI)
├── run-cli.bat                 # Chạy máy chủ Mail Server chế độ dòng lệnh (CLI)
├── init.sql                    # Script tạo Database và bảng thủ công (nếu cần)
├── mail.properties             # Tệp lưu cấu hình hệ thống
└── Mail_Server.iml             # Cấu hình Module IntelliJ IDEA
```

---

### 6. Hướng dẫn cài đặt & Khởi chạy

#### Bước 1: Khởi động Docker Container (MySQL & phpMyAdmin)
Mở PowerShell hoặc Command Prompt tại thư mục `d:\LTM`:
```bash
cd d:\LTM
docker compose up -d
```
Kiểm tra container đang chạy:
* MySQL: cổng `3308`
* phpMyAdmin: mở trình duyệt web tới **`http://localhost:8085`**

#### Bước 2: Biên dịch dự án
Chạy tệp `build.bat` tại `d:\LTM\Mail_Server`:
```bash
build.bat
```
*(Yêu cầu máy đã cài JDK 27 hoặc JDK 17+)*. Thông báo `BIEN DICH THANH CONG - BUILD OK` xuất hiện khi hoàn tất.

#### Bước 3: Khởi chạy chương trình
Có 3 cách chạy:
1. **Chạy Mail Server Manager (Khuyến nghị)**:
   ```bash
   run.bat
   ```
   Giao diện Server Manager mở ra, tự động kết nối MySQL container và khởi động máy chủ trên cổng SMTP `2525`, POP3 `1100`.
   Trên giao diện có sẵn nút:
   * **✉ Mở Mail Client**: Mở giao diện đọc/gửi thư Gmail-style.
   * **🌐 phpMyAdmin (8085)**: Mở nhanh trang quản lý DB trên trình duyệt web.

2. **Chạy trực tiếp Mail Client**:
   ```bash
   run-client.bat
   ```
   Mở màn hình đăng nhập LTM Mail (nhập tài khoản `alice` / `123456`).

3. **Chạy chế độ Console (CLI)**:
   ```bash
   run-cli.bat
   ```

---

### 7. Hướng dẫn cấu hình gửi và nhận thư với Gmail

Để gửi thư ra thế giới thật tới các địa chỉ `@gmail.com` và nhận thư từ Gmail về:
1. Vào trang tài khoản Google của bạn: [https://myaccount.google.com/security](https://myaccount.google.com/security)
2. Bật tính năng **Xác minh 2 bước (2-Step Verification)**.
3. Tìm kiếm mục **Mật khẩu ứng dụng (App passwords)**, tạo một mật khẩu mới cho ứng dụng "Mail" (sẽ sinh ra một mã gồm 16 ký tự, ví dụ: `abcd efgh ijkl mnop`).
4. Trên giao diện **Mail Server Manager**, vào Tab **`⚙ Cấu hình Gmail Gateway`**:
   * Tích chọn: ☑ *Bật chuyển tiếp (Relay) qua Gmail khi gửi tới địa chỉ ngoài*
   * Nhập địa chỉ Gmail của bạn vào ô: *Địa chỉ Gmail của bạn*
   * Dán mật khẩu 16 ký tự vào ô: *Mật khẩu ứng dụng (App Password)*
   * Bấm **Lưu cấu hình Gmail**
   * Bấm **Kiểm tra kết nối Gmail ngay** (Server sẽ tự động bắt tay socket SSL với `smtp.gmail.com:465` và `pop.gmail.com:995`).
5. Trong giao diện Gmail cá nhân của bạn trên trình duyệt web, vào mục: *Cài đặt (Settings) > Xem tất cả chế độ cài đặt > Chuyển tiếp và POP/IMAP* > Tích chọn **Bật POP cho tất cả thư** để cho phép server tải thư về qua POP3 SSL.

---

### 8. Kịch bản Demo chi tiết khi nộp bài / Báo cáo cho Giảng viên

#### Kịch bản 1: Demo gửi thư nội bộ trên Localhost
1. Khởi động máy chủ bằng `run.bat`.
2. Bấm nút **✉ Mở Mail Client**, đăng nhập bằng tài khoản:
   * Email: `alice@localhost`
   * Mật khẩu: `123456`
3. Mở thêm một cửa sổ Mail Client thứ hai, đăng nhập bằng:
   * Email: `bob@localhost`
   * Mật khẩu: `123456`
4. Từ cửa sổ của **Alice**, bấm nút **＋ Soạn thư**:
   * Đến: `bob@localhost`
   * Tiêu đề: `Bài tập lớn Lập trình mạng - Mail Server`
   * Nội dung: `Chào Bob, đây là thư gửi nội bộ qua socket SMTP!`
   * Bấm nút **📎 Đính kèm tệp** và chọn 1 tệp văn bản hoặc hình ảnh bất kỳ.
   * Bấm **Gửi**.
5. Quan sát:
   * Trên cửa sổ **Mail Server Manager (Tab Live Log)**: Hiển thị toàn bộ tiến trình lệnh socket SMTP (`EHLO`, `AUTH LOGIN`, `MAIL FROM`, `RCPT TO`, `DATA`, dot-stuffing).
   * Trên cửa sổ của **Bob**: Bấm **🔄 Làm mới**, bức thư của Alice xuất hiện ngay lập tức trong *Hộp thư đến* với tiêu đề in đậm và biểu tượng `📎`.
   * Bấm vào thư để đọc chi tiết, thấy thông tin người gửi, nội dung và nút **💾 Lưu** để tải tệp đính kèm về máy.

#### Kịch bản 2: Kiểm tra dữ liệu trực tiếp trong MySQL (phpMyAdmin)
1. Trên Mail Server Manager, bấm nút **🌐 phpMyAdmin (8085)**.
2. Chọn cơ sở dữ liệu `mail_db`:
   * Bảng `emails`: Thấy rõ bức thư vừa gửi được lưu vào với đầy đủ `owner = 'bob'`, `folder = 'INBOX'`, `subject`, `size_bytes`, `raw_message` và một bản sao trong hộp thư `owner = 'alice'`, `folder = 'SENT'`.
   * Bảng `mail_logs`: Thấy các bản ghi nhật ký kiểm toán giao thức SMTP và POP3 vừa diễn ra.

#### Kịch bản 3: Demo gửi thư ra Gmail thật
1. Cấu hình tài khoản Gmail và App Password ở Tab *Cấu hình Gmail Gateway*.
2. Trong Mail Client của **Alice**, bấm **⚡ Mẫu: Gửi Gmail** (hoặc bấm *Soạn thư*, nhập địa chỉ Gmail thật của bạn, ví dụ `tenban@gmail.com`).
3. Bấm **Gửi**.
4. Mail Server nhận diện người nhận không phải `@localhost`, tiến hành xác thực `AUTH LOGIN` và relay bức thư qua `smtp.gmail.com:465`.
5. Mở ứng dụng Gmail thật trên điện thoại hoặc trình duyệt web, bạn sẽ nhận được thư ngay lập tức trong Hộp thư đến!
