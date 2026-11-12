# BÁO CÁO BÀI TẬP LỚN LẬP TRÌNH MẠNG
## ĐỀ TÀI: WEB CLIENT (TRÌNH DUYỆT BROWSER) VÀ WEB SERVER

---

### 1. BẢNG ĐỐI CHIẾU TIÊU CHÍ ĐỀ BÀI (100% ĐẠT YÊU CẦU)

| STT | Yêu cầu trong đề bài | Hiện trạng trong đồ án | Đánh giá |
| :---: | :--- | :--- | :---: |
| 1 | **Web Server hoặc Web Client** | Đã làm **cả hai**: Trình duyệt Web Browser (Client) và Web Server cổng 8080 (Server). | **ĐẠT** |
| 2 | **Có giao thức HTTP** | Triển khai HTTP/1.0 và HTTP/1.1 bằng Java Socket thuần TCP. | **ĐẠT** |
| 3 | **Có hơn 1 dịch vụ của Server (.pdf, .mp3, .mp4...)** | Phục vụ 7 loại tệp: `.pdf`, `.mp3`, `.mp4`, `.png`, `.txt`, `.wav`, `.html`. | **ĐẠT** |
| 4 | **Browser có get, head, post** | Đầy đủ 3 phương thức GET, HEAD và POST (kèm form submit và PostDialog). | **ĐẠT** |
| 5 | **History, download,...** | Đầy đủ Lịch sử (`Ctrl+H`), Quản lý tải xuống (`Ctrl+J`), DevTools F12, DNS IP. | **ĐẠT** |
| 6 | **Tương tự Chrome hoặc Cốc Cốc** | Đa thẻ (Multi-tabs), Bookmark Bar, nút Back/Forward/Reload/Home, phím tắt Chrome. | **ĐẠT** |

---

### 2. CÁC NÚT BẤM TEST TRÊN GIAO DIỆN (DỄ DÙNG NHẤT)

Khi mở ứng dụng bằng **`run.bat`**, ngay phía trên cùng có **Thanh Kiểm Thử Nhanh (Quick Testing Bar)**:
1. **`⭐ TỰ ĐỘNG TEST (12/12 ĐẠT)`**: Bấm nút này để chạy tự động 12 ca kiểm thử và hiện hộp thoại xác nhận 100% đạt yêu cầu.
2. **Nhóm HTTP**:
   * **`📥 [GET]`**: Tải trang chủ `index.html`.
   * **`🔍 [HEAD]`**: Thử nghiệm HTTP HEAD, tự động mở khung DevTools xem Response Headers.
   * **`📨 [POST]`**: Mở hộp thoại gửi POST dữ liệu lên server.
3. **Nhóm Dịch vụ Server**:
   * **`📄 PDF`**: Xem dịch vụ tài liệu PDF (có nút Tải về + nút Mở tệp ngay).
   * **`🎵 MP3`**: Xem dịch vụ âm thanh MP3 (có nút Tải về + nút Mở tệp ngay).
   * **`🎬 MP4`**: Xem dịch vụ video MP4 (có nút Tải về + nút Mở tệp ngay).
   * **`🖼 PNG`**: Hiển thị hình ảnh trực tiếp trên trình duyệt.
   * **`📝 TXT`**: Hiển thị văn bản thuần.
4. **Nhóm Tiện ích Chrome**:
   * **`🕒 Lịch sử`**: Mở cửa sổ History (`Ctrl + H`).
   * **`⬇ Tải xuống`**: Mở cửa sổ Downloads (`Ctrl + J`).
   * **`🛠 DevTools`**: Bật/tắt thanh soi mã trạng thái và headers (`F12`).
   * **`🌐 DNS`**: Bóc tách URL và phân giải địa chỉ IP qua `InetAddress`.

---

### 3. CÁC FILE CHẠY NHANH
* **`run.bat`**: Khởi chạy Trình duyệt Web Browser (tự động bật Web Server cổng 8080).
* **`run-test.bat`**: Chạy bộ kiểm thử tự động toàn diện từ dòng lệnh.
* **`run-server.bat`**: Khởi động riêng Web Server độc lập nếu muốn.
* **`build.bat`**: Biên dịch lại toàn bộ mã nguồn Java của dự án.
