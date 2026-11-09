package server;

/**
 * Lắng nghe sự kiện từ Mail Server để hiển thị lên giao diện.
 */
public interface ServerListener {
    /** Dòng log (protocol: SMTP / POP3 / GMAIL / SYSTEM). */
    void onLog(String protocol, String message);

    /** Dữ liệu (thư / người dùng) thay đổi -> giao diện nên làm mới. */
    default void onDataChanged() {}

    /** Trạng thái server thay đổi (start/stop). */
    default void onStatusChanged() {}
}
