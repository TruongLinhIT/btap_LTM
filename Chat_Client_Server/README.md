# Chat LTM – Chat Client-Server (Java + MySQL/Docker)

## Đáp ứng yêu cầu bài tập

| Yêu cầu | Cách thực hiện |
|---|---|
| Giao diện thân thiện | Java Swing: danh sách hội thoại + trạng thái online, bong bóng chat trái/phải, badge tin chưa đọc |
| **Broadcast** | Hội thoại "📢 Mọi người": server gửi tới **tất cả** client đang online |
| **Multicast** | "＋ Nhóm": tạo nhóm chọn thành viên, server chỉ gửi tới **thành viên nhóm** (lưu DB) |
| Unicast | Chat riêng 1-1 |
| Icon biểu cảm | Nút 😀 mở bảng emoji (Unicode, lưu MySQL utf8mb4) |
| Text – Audio | Text; **tin nhắn thoại** (ghi âm → WAV, có nút phát trực tiếp & tải về máy); gửi file video/ảnh |
| Đính kèm | Nút 📎: ảnh (hiển thị trực tiếp), video, âm thanh, tệp bất kỳ ≤ 50MB |
| Database | MySQL 8 chạy bằng Docker: `users`, `chat_groups`, `group_members`, `messages` |
| 5–6 client | Chạy `run-client.bat` nhiều lần (mỗi lần 1 client) |

## Kiến trúc TCP

- **TCP (cổng 5000)** – truyền tin cậy, đúng thứ tự: đăng ký/đăng nhập, text, emoji, tạo nhóm, lịch sử,
  gửi/nhận file, ảnh, tin nhắn thoại (.wav). Mỗi client 1 kết nối, server 1 thread/client (`ClientHandler`).
  Giao thức tự định nghĩa: `common.Packet` (type + các field + data nhị phân).
- **Broadcast/Multicast**: ở tầng ứng dụng – server lặp qua danh sách người nhận (tất cả online / thành viên nhóm).

## Chạy

Yêu cầu: JDK, Docker Desktop.

```bat
docker compose up -d        :: MySQL ở localhost:3308 (db chatdb)
build.bat                   :: biên dịch vào thư mục out
run-server.bat              :: chạy server (tự tạo bảng)
run-client.bat              :: chạy mỗi client (mở nhiều client)
```

Client: nhập Server `localhost` (hoặc IP máy chạy server nếu test nhiều máy), Port `5000`, bấm **Đăng ký**
rồi **Đăng nhập**.

## Demo gợi ý

1. Đăng ký `an`, `binh`, `chi`, `dung`, `em` và đăng nhập trên các client.
2. Chat riêng; chọn "Mọi người" để Broadcast; tạo nhóm `nhom1` gồm vài người để Multicast.
3. Gửi emoji, ảnh, file, ghi âm 🎤 rồi bấm ⏹ để gửi tin nhắn thoại.
4. Bấm "▶ Nghe voice" để nghe trực tiếp hoặc bấm "💾 Tải về" để lưu file .wav về máy.
5. Đóng client, mở lại → lịch sử tin nhắn load từ MySQL.

## Cấu trúc

- `src/common/Packet.java` – gói tin TCP
- `src/server/` – `ChatServer`, `ClientHandler` (TCP), `UdpRelay` (UDP), `Database` (JDBC), `Msg`
- `src/client/` – `ClientApp` (đăng nhập), `MainFrame` (giao diện), `NetClient` (TCP), `UdpMedia`, `MediaManager` (âm thanh/video)
- Cấu hình server qua biến môi trường: `DB_URL`, `DB_USER`, `DB_PASS`, `CHAT_TCP_PORT`, `CHAT_UDP_PORT`

## Hạn chế / hướng mở rộng

- Video realtime hiện là chia sẻ màn hình (JDK không có API webcam); có thể thêm thư viện `webcam-capture`.
- Mật khẩu được băm SHA-256 + salt trong DB nhưng truyền qua TCP không mã hoá (có thể nâng cấp SSLSocket).
