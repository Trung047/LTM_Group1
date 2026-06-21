package Database;

import Logging.SystemLogger;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

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
