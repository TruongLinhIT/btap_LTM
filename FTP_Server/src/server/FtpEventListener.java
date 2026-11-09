package server;

/**
 * Interface cho phép UI hoặc Console lắng nghe các sự kiện của FTP Server.
 */
public interface FtpEventListener {
    void onServerStatusChanged(boolean running, int port, String message);
    void onClientConnected(FtpSession session);
    void onClientDisconnected(FtpSession session);
    void onCommand(FtpSession session, String commandLine);
    void onResponse(FtpSession session, String responseLine);
    void onTransfer(FtpSession session, String action, String filename, long bytesTransferred, boolean success);
}
