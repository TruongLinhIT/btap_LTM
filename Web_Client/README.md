# LTM Web Client – Trình Duyệt Web (Tương Tự Chrome / Cốc Cốc)

Dự án **Web Client (Trình duyệt Browser tương tự Chrome / Cốc Cốc)** cài đặt giao thức **HTTP/1.1 trực tiếp trên TCP Socket (`java.net.Socket`)**, đáp ứng đầy đủ các yêu cầu: **GET, HEAD, POST, History, Download**.

---

## 1. Đáp ứng yêu cầu đề bài

| STT | Yêu cầu đề bài | Thực hiện trong dự án |
| :---: | :--- | :--- |
| 1 | **Web Client (Browser tương tự Chrome / Cốc Cốc)** | Giao diện đa thẻ (**Multi-tabs** `Ctrl+T` / `Ctrl+W`), thanh điều hướng (`Back`, `Forward`, `Reload`, `Home`), thanh địa chỉ & tìm kiếm **Omnibox**, thanh **Dấu trang (Bookmarks)**, Trang chủ & Tìm kiếm **Google** (`/search?q=...`), và **Chrome DevTools (`F12`)**. |
| 2 | **Có giao thức HTTP** | Lõi `HttpEngine.java` đóng gói và phân tích trực tiếp bản tin **HTTP/1.1** (`Request-Line`, `Headers`, `Body`, `Content-Length`, `Chunked`, `Redirect 301/302`) qua `java.net.Socket`. |
| 3 | **Phương thức `GET`** | Duyệt trang web qua URL trên thanh địa chỉ Omnibox hoặc tìm kiếm từ khóa trên Google (`GET /search?q=...`). |
| 4 | **Phương thức `HEAD`** | Chọn `HEAD` trên thanh địa chỉ hoặc bấm nút **`HEAD`** trên thanh công cụ để chỉ lấy **HTTP Status & Response Headers** (`Body = 0 bytes`) và hiển thị bảng phân tích Header chi tiết. |
| 5 | **Phương thức `POST`** | Gửi biểu mẫu `<form method="POST">` trực tiếp trên trang web hoặc dùng công cụ **`POST` (`PostDialog`)** hỗ trợ cả `application/x-www-form-urlencoded` và `application/json`. |
| 6 | **Lịch sử duyệt web (`History`)** | Phím tắt **`Ctrl + H`** hoặc nút **`Lịch sử (Ctrl+H)`**: Tự động lưu vết vào `browser_history.txt`, hỗ trợ tìm kiếm từ khóa, lọc theo phương thức (`GET`, `HEAD`, `POST`), nhấp đúp để mở lại trang. |
| 7 | **Quản lý tải xuống (`Download`)** | Phím tắt **`Ctrl + J`** hoặc nút **`Tải xuống (Ctrl+J)`**: Tải trang web / tài liệu từ bất kỳ URL nào qua HTTP/1.1 Socket về thư mục `downloads/` hoặc chọn nơi lưu (`Save As...`), có thanh tiến trình `%`, nút **Mở tệp** và **Mở thư mục**. |

---

## 2. Cách biên dịch và chạy

```bat
build.bat       :: Biên dịch toàn bộ mã nguồn
run.bat         :: Khởi chạy Trình duyệt Web Client (Chrome / Cốc Cốc)
run-test.bat    :: Chạy bộ kiểm thử tự động 8/8 tiêu chí của Web Client
```
