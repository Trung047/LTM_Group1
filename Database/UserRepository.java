package Database;

import Logging.SystemLogger;
import java.sql.*;

public class UserRepository {

    /**
     * Xác thực tài khoản.
     * Trả về true nếu username+password khớp trong DB.
     * Nếu DB không kết nối được, trả về true (chế độ demo không cần DB).
     */
    public boolean authenticateUser(String username, String password) {
        Connection conn = DatabaseConnection.getConnection();
        if (conn == null) {
            SystemLogger.warning("DB không khả dụng — cho phép đăng nhập demo: " + username);
            return true; // demo mode
        }
        String sql = "SELECT id FROM users WHERE username = ? AND password = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, password);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                SystemLogger.info("Đăng nhập thành công: " + username);
                return true;
            } else {
                SystemLogger.info("Đăng nhập thất bại: " + username);
                return false;
            }
        } catch (SQLException e) {
            SystemLogger.error("Lỗi xác thực: " + e.getMessage());
            return true; // demo fallback
        } finally {
            try { conn.close(); } catch (SQLException ignored) {}
        }
    }

    /**
     * Đăng ký tài khoản mới (nếu chưa tồn tại).
     */
    public boolean registerUser(String username, String password) {
        Connection conn = DatabaseConnection.getConnection();
        if (conn == null) return false;
        String checkSql = "SELECT id FROM users WHERE username = ?";
        try (PreparedStatement ps = conn.prepareStatement(checkSql)) {
            ps.setString(1, username);
            if (ps.executeQuery().next()) return false; // đã tồn tại
        } catch (SQLException e) {
            SystemLogger.error("Lỗi kiểm tra user: " + e.getMessage());
            return false;
        }
        String insertSql = "INSERT INTO users (username, password) VALUES (?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
            ps.setString(1, username);
            ps.setString(2, password);
            ps.executeUpdate();
            SystemLogger.info("Đăng ký thành công: " + username);
            return true;
        } catch (SQLException e) {
            SystemLogger.error("Lỗi đăng ký: " + e.getMessage());
            return false;
        } finally {
            try { conn.close(); } catch (SQLException ignored) {}
        }
    }
}
