import java.io.Serializable;
import java.util.Objects;

public class Account implements Serializable {
    private static final long serialVersionUID = 1L;

    private String username;
    private String password;
    private Double balance;
    private boolean isLocked;
    private String fullName;
    private String email;
    private String phone;

    public Account(String username, String password, Double balance, boolean isLocked) {
        this(username, password, balance, isLocked, "", "", "");
    }

    public Account(String username, String password, Double balance, boolean isLocked,
                   String fullName, String email, String phone) {
        this.username = username;
        this.password = password;
        this.balance = balance;
        this.isLocked = isLocked;
        this.fullName = fullName != null ? fullName : "";
        this.email = email != null ? email : "";
        this.phone = phone != null ? phone : "";
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

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName != null ? fullName : "";
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email != null ? email : "";
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone != null ? phone : "";
    }

    // So sánh xem 2 tài khoản có giống hệt nhau không
    public boolean isSame(Account other) {
        if (other == null) return false;
        return Objects.equals(this.username, other.username)
                && Objects.equals(this.password, other.password)
                && Double.compare(this.balance, other.balance) == 0
                && this.isLocked == other.isLocked
                && Objects.equals(this.fullName, other.fullName)
                && Objects.equals(this.email, other.email)
                && Objects.equals(this.phone, other.phone);
    }
}
