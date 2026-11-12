# BÁO CÁO BÀI TẬP LỚN LẬP TRÌNH MẠNG
## ĐỀ TÀI: WEB CLIENT (TRÌNH DUYỆT MẠNG BROWSER) VÀ WEB SERVER

---

### 1. Giới thiệu tổng quan
Dự án được xây dựng hoàn toàn bằng **Java Socket thuần (TCP)** và giao diện đồ họa **Java Swing**, mô phỏng các tính năng chính của một trình duyệt hiện đại như Google Chrome / Cốc Cốc kết hợp cùng Web Server:
* **Giao thức HTTP:** Hỗ trợ đầy đủ các phương thức `GET`, `HEAD`, `POST`.
* **Phục vụ Đa phương tiện:** Phục vụ và hiển thị nhiều định dạng tệp: `.html`, `.txt`, `.png`, `.pdf`, `.mp3`, `.mp4`, `.wav`.
* **Quản lý Đa thẻ (Multi-tabs):** Mở thẻ mới (`Ctrl + T`), đóng thẻ (`Ctrl + W`).
* **Trình quản lý tải xuống (Download Manager - `Ctrl + J`):** Theo dõi thanh tiến độ %, tốc độ tải, mở tệp và mở thư mục.
* **Lịch sử duyệt web (History - `Ctrl + H`):** Tìm kiếm, lọc và truy cập lại các URL.
* **Developer Tools (F12):** Xem Response/Request Headers, Mã trạng thái (200, 404), Độ trễ (Latency ms), Kích thước gói tin.
* **Tích hợp phân tích URL & DNS IP:** Kế thừa từ module `url.diachi` và `url.inet` để bóc tách thành phần URL và phân giải địa chỉ IP qua `InetAddress`.

---

### 2. Hướng dẫn khởi chạy nhanh bằng file `.bat`
Trong thư mục `d:\LTM\Web_Client` đã có sẵn các tệp thực thi:
1. **`run.bat`**: Khởi chạy Trình duyệt Web Browser (tự động bật Web Server cổng 8080).
2. **`run-test.bat`**: Chạy bộ kiểm thử tự động toàn diện (12 test cases).
3. **`run-server.bat`**: Khởi động riêng Web Server độc lập trên cổng 8080.
4. **`build.bat`**: Biên dịch lại toàn bộ mã nguồn Java của dự án.
