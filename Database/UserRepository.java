package Database;

import Logging.SystemLogger;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class UserRepository {
    
    // Hàm kiểm tra đăng nhập
    public boolean authenticateUser(String username, String password) {
        String query = "SELECT * FROM users WHERE username = ? AND password = ?";
        
        // Dùng DatabaseConnection để lấy kết nối
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            
            if (conn == null) return false; // Nếu mất kết nối thì báo false

            pstmt.setString(1, username);
            pstmt.setString(2, password); 
            
            ResultSet rs = pstmt.executeQuery();
            
            if (rs.next()) {
                SystemLogger.info("Client đăng nhập thành công: " + username);
                return true;
            } else {
                SystemLogger.info("Client đăng nhập thất bại: Sai tài khoản hoặc mật khẩu.");
                return false;
            }
        } catch (SQLException e) {
            SystemLogger.error("Lỗi truy vấn tài khoản: " + e.getMessage());
            return false;
        }
    }
}
/**
 * Kiểm tra username đã tồn tại hay chưa.
 */
public boolean userExists(String username) {

    String sql = "SELECT username FROM users WHERE username=?";

    try (Connection conn = DatabaseConnection.getConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {

        if (conn == null) return false;

        ps.setString(1, username);

        ResultSet rs = ps.executeQuery();

        return rs.next();

    } catch (SQLException e) {

        SystemLogger.error("Lỗi kiểm tra username: " + e.getMessage());

    }

    return false;
}

/**
 * Đăng ký tài khoản mới.
 */
public boolean registerUser(String username, String password) {

    if (userExists(username)) {
        SystemLogger.warning("Username đã tồn tại: " + username);
        return false;
    }

    String sql = "INSERT INTO users(username,password) VALUES(?,?)";

    try (Connection conn = DatabaseConnection.getConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {

        if (conn == null) return false;

        ps.setString(1, username);
        ps.setString(2, password);

        if (ps.executeUpdate() > 0) {

            SystemLogger.info("Đăng ký tài khoản thành công: " + username);

            return true;
        }

    } catch (SQLException e) {

        SystemLogger.error("Lỗi đăng ký tài khoản: " + e.getMessage());

    }

    return false;
}

/**
 * Đổi mật khẩu.
 */
public boolean changePassword(String username, String newPassword) {

    String sql = "UPDATE users SET password=? WHERE username=?";

    try (Connection conn = DatabaseConnection.getConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {

        if (conn == null) return false;

        ps.setString(1, newPassword);
        ps.setString(2, username);

        if (ps.executeUpdate() > 0) {

            SystemLogger.info("Đổi mật khẩu thành công: " + username);

            return true;
        }

    } catch (SQLException e) {

        SystemLogger.error("Lỗi đổi mật khẩu: " + e.getMessage());

    }

    return false;
}

/**
 * Lấy ID người dùng theo username.
 */
public int getUserId(String username) {

    String sql = "SELECT id FROM users WHERE username=?";

    try (Connection conn = DatabaseConnection.getConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {

        if (conn == null) return -1;

        ps.setString(1, username);

        ResultSet rs = ps.executeQuery();

        if (rs.next()) {
            return rs.getInt("id");
        }

    } catch (SQLException e) {

        SystemLogger.error("Lỗi lấy User ID: " + e.getMessage());

    }

    return -1;
}

/**
 * Lấy danh sách tất cả tài khoản.
 */
public List<String> getAllUsers() {

    List<String> users = new ArrayList<>();

    String sql = "SELECT username FROM users ORDER BY username";

    try (Connection conn = DatabaseConnection.getConnection();
         PreparedStatement ps = conn.prepareStatement(sql);
         ResultSet rs = ps.executeQuery()) {

        while (rs.next()) {

            users.add(rs.getString("username"));

        }

    } catch (SQLException e) {

        SystemLogger.error("Lỗi lấy danh sách User: " + e.getMessage());

    }

    return users;
}

/**
 * Xóa tài khoản.
 */
public boolean deleteUser(String username) {

    String sql = "DELETE FROM users WHERE username=?";

    try (Connection conn = DatabaseConnection.getConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {

        ps.setString(1, username);

        if (ps.executeUpdate() > 0) {

            SystemLogger.info("Đã xóa tài khoản: " + username);

            return true;
        }

    } catch (SQLException e) {

        SystemLogger.error("Lỗi xóa tài khoản: " + e.getMessage());

    }

    return false;
}
