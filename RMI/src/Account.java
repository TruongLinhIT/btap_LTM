import java.io.Serializable;

public class Account implements Serializable {
    private static final long serialVersionUID = 1L;

    private String username;
    private String password;
    private Double balance;
    private boolean isLocked;

    public Account(String username, String password, Double balance, boolean isLocked) {
        this.username = username;
        this.password = password;
        this.balance = balance;
        this.isLocked = isLocked;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Double getBalance() {
        return balance;
    }

    public void setBalance(Double balance) {
        this.balance = balance;
    }

    public boolean isLocked() {
        return isLocked;
    }

    public void setLocked(boolean locked) {
        isLocked = locked;
    }

    // So sánh xem 2 tài khoản có giống hệt nhau không
    public boolean isSame(Account other) {
        if (other == null) return false;
        return this.username.equals(other.username)
                && this.password.equals(other.password)
                && Double.compare(this.balance, other.balance) == 0
                && this.isLocked == other.isLocked;
    }
}
