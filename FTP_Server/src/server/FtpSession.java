package server;

import database.UserModel;

import java.net.Socket;
import java.util.UUID;

/**
 * Quản lý trạng thái và phiên làm việc của một Client kết nối tới FTP Server.
 */
public class FtpSession {
    private final String id;
    private final Socket controlSocket;
    private final String clientIp;
    private final int clientPort;
    private final long connectedTimeMillis;

    private UserModel user;
    private boolean authenticated;
    private String userParam; // Username chờ lệnh PASS
    private String currentVirtualDir = "/";
    private String transferType = "A"; // "A" = ASCII, "I" = Binary
    private long restOffset = 0; // Vị trí tiếp tục truyền file
    private String renameFromPath; // Đường dẫn cho lệnh RNFR -> RNTO
    private FtpDataConnection dataConnection;
    private String state = "CONNECTING"; // CONNECTING, IDLE, DOWNLOADING, UPLOADING, DISCONNECTED

    public FtpSession(Socket controlSocket) {
        this.id = UUID.randomUUID().toString().substring(0, 8);
        this.controlSocket = controlSocket;
        this.clientIp = controlSocket.getInetAddress().getHostAddress();
        this.clientPort = controlSocket.getPort();
        this.connectedTimeMillis = System.currentTimeMillis();
    }

    public String getId() { return id; }
    public Socket getControlSocket() { return controlSocket; }
    public String getClientIp() { return clientIp; }
    public int getClientPort() { return clientPort; }
    public long getConnectedTimeMillis() { return connectedTimeMillis; }

    public UserModel getUser() { return user; }
    public void setUser(UserModel user) { this.user = user; }

    public boolean isAuthenticated() { return authenticated; }
    public void setAuthenticated(boolean authenticated) { this.authenticated = authenticated; }

    public String getUserParam() { return userParam; }
    public void setUserParam(String userParam) { this.userParam = userParam; }

    public String getCurrentVirtualDir() { return currentVirtualDir; }
    public void setCurrentVirtualDir(String currentVirtualDir) { this.currentVirtualDir = currentVirtualDir; }

    public String getTransferType() { return transferType; }
    public void setTransferType(String transferType) { this.transferType = transferType; }

    public long getRestOffset() { return restOffset; }
    public void setRestOffset(long restOffset) { this.restOffset = restOffset; }

    public String getRenameFromPath() { return renameFromPath; }
    public void setRenameFromPath(String renameFromPath) { this.renameFromPath = renameFromPath; }

    public synchronized FtpDataConnection getDataConnection() { return dataConnection; }
    public synchronized void setDataConnection(FtpDataConnection dataConnection) {
        if (this.dataConnection != null && this.dataConnection != dataConnection) {
            this.dataConnection.close();
        }
        this.dataConnection = dataConnection;
    }

    public String getState() { return state; }
    public void setState(String state) { this.state = state; }

    public String getUsername() {
        return user != null ? user.getUsername() : (userParam != null ? userParam : "Anonymous");
    }

    public void close() {
        this.state = "DISCONNECTED";
        if (dataConnection != null) {
            dataConnection.close();
        }
        try {
            if (controlSocket != null && !controlSocket.isClosed()) {
                controlSocket.close();
            }
        } catch (Exception ignored) {}
    }

    @Override
    public String toString() {
        return "[" + clientIp + ":" + clientPort + " | " + getUsername() + " | " + state + "]";
    }
}
