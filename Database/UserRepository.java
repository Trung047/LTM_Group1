package Database;

import Logging.SystemLogger;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Thao tác với bảng users trong DB.
 *
 * Cấu trúc bảng:
 *   users(id, username, password, created_at)
 *
 * Mật khẩu lưu dạng plain text (phù hợp cho project học tập).
 */
public class UserRepository {

    // ── Xác thực đăng nhập ───────────────────────────────────────────────────

    /**
     * Kiểm tra đăng nhập: so sánh username + password với DB.
     * Server gọi hàm này khi nhận frame LOGIN:username:password.
     *
     * @param username tên đăng nhập
     * @param password mật khẩu plain text
     * @return true nếu đăng nhập thành công
     */
    public boolean authenticateUser(String username, String password) {
        String sql = "SELECT id FROM users WHERE username = ? AND password = ?";

        try (Connection conn = DatabaseConnection.getConnection()) {
            if (conn == null) {
                SystemLogger.warning("[DB] Demo mode — DB không kết nối, cho phép đăng nhập demo: " + username);
                // Trong demo mode (không có DB) cho phép đăng nhập mọi username
                return true;
            }
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, username);
                ps.setString(2, password);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    SystemLogger.info("Client đăng nhập thành công: " + username);
                    return true;
                } else {
                    SystemLogger.info("Client đăng nhập thất bại — sai tài khoản hoặc mật khẩu: " + username);
                    return false;
                }
            }
        } catch (SQLException e) {
            SystemLogger.error("Lỗi truy vấn xác thực: " + e.getMessage());
            return false;
        }
    }

    // ── Kiểm tra tồn tại ─────────────────────────────────────────────────────

    /**
     * Kiểm tra username đã tồn tại trong DB hay chưa.
     *
     * @param username tên cần kiểm tra
     * @return true nếu đã tồn tại
     */
    public boolean userExists(String username) {
        String sql = "SELECT id FROM users WHERE username = ?";

        try (Connection conn = DatabaseConnection.getConnection()) {
            if (conn == null) return false;
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, username);
                ResultSet rs = ps.executeQuery();
                return rs.next();
            }
        } catch (SQLException e) {
            SystemLogger.error("Lỗi kiểm tra username: " + e.getMessage());
        }
        return false;
    }

    // ── Đăng ký tài khoản ────────────────────────────────────────────────────

    /**
     * Đăng ký tài khoản mới (lưu mật khẩu plain text).
     *
     * @param username tên đăng nhập mới
     * @param password mật khẩu
     * @return true nếu đăng ký thành công, false nếu username đã tồn tại
     */
    public boolean registerUser(String username, String password) {
        if (userExists(username)) {
            SystemLogger.warning("Username đã tồn tại: " + username);
            return false;
        }

        String sql = "INSERT INTO users (username, password) VALUES (?, ?)";

        try (Connection conn = DatabaseConnection.getConnection()) {
            if (conn == null) return false;
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, username);
                ps.setString(2, password);
                if (ps.executeUpdate() > 0) {
                    SystemLogger.info("Đăng ký tài khoản thành công: " + username);
                    return true;
                }
            }
        } catch (SQLException e) {
            SystemLogger.error("Lỗi đăng ký tài khoản: " + e.getMessage());
        }
        return false;
    }

    // ── Đổi mật khẩu ─────────────────────────────────────────────────────────

    /**
     * Đổi mật khẩu cho một tài khoản.
     *
     * @param username    tên tài khoản
     * @param newPassword mật khẩu mới (plain text)
     * @return true nếu đổi thành công
     */
    public boolean changePassword(String username, String newPassword) {
        String sql = "UPDATE users SET password = ? WHERE username = ?";

        try (Connection conn = DatabaseConnection.getConnection()) {
            if (conn == null) return false;
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, newPassword);
                ps.setString(2, username);
                if (ps.executeUpdate() > 0) {
                    SystemLogger.info("Đổi mật khẩu thành công: " + username);
                    return true;
                }
            }
        } catch (SQLException e) {
            SystemLogger.error("Lỗi đổi mật khẩu: " + e.getMessage());
        }
        return false;
    }

    // ── Truy vấn thông tin ───────────────────────────────────────────────────

    /**
     * Lấy ID của user theo username.
     *
     * @param username tên tài khoản
     * @return id trong DB, -1 nếu không tìm thấy hoặc lỗi
     */
    public int getUserId(String username) {
        String sql = "SELECT id FROM users WHERE username = ?";

        try (Connection conn = DatabaseConnection.getConnection()) {
            if (conn == null) return -1;
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, username);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) return rs.getInt("id");
            }
        } catch (SQLException e) {
            SystemLogger.error("Lỗi lấy User ID: " + e.getMessage());
        }
        return -1;
    }

    /**
     * Lấy danh sách tất cả username trong DB (sắp xếp a→z).
     * Dùng để hiển thị danh sách tài khoản có thể đăng nhập.
     *
     * @return danh sách username
     */
    public List<String> getAllUsers() {
        List<String> users = new ArrayList<>();
        String sql = "SELECT username FROM users ORDER BY username";

        try (Connection conn = DatabaseConnection.getConnection()) {
            if (conn == null) return users;
            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    users.add(rs.getString("username"));
                }
            }
        } catch (SQLException e) {
            SystemLogger.error("Lỗi lấy danh sách User: " + e.getMessage());
        }
        return users;
    }

    // ── Xóa tài khoản ────────────────────────────────────────────────────────

    /**
     * Xóa tài khoản khỏi DB.
     *
     * @param username tên tài khoản cần xóa
     * @return true nếu xóa thành công
     */
    public boolean deleteUser(String username) {
        String sql = "DELETE FROM users WHERE username = ?";

        try (Connection conn = DatabaseConnection.getConnection()) {
            if (conn == null) return false;
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, username);
                if (ps.executeUpdate() > 0) {
                    SystemLogger.info("Đã xóa tài khoản: " + username);
                    return true;
                }
            }
        } catch (SQLException e) {
            SystemLogger.error("Lỗi xóa tài khoản: " + e.getMessage());
        }
        return false;
    }
}
