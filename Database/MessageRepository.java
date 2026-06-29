package Database;

import Logging.SystemLogger;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Thao tác với bảng messages trong DB.
 *
 * Cấu trúc bảng (từ schema.sql):
 *   messages(id, sender, receiver, content, created_at)
 *   receiver = 'GROUP'    → tin nhắn nhóm
 *   receiver = username   → tin nhắn riêng
 */
public class MessageRepository {

    // ── Lưu tin nhắn ─────────────────────────────────────────────────────────

    /**
     * Lưu tin nhắn vào DB (dùng chung cho nhóm và riêng).
     *
     * @param sender   tên người gửi
     * @param receiver 'GROUP' hoặc username người nhận
     * @param content  nội dung tin nhắn
     * @return true nếu lưu thành công
     */
    public boolean saveMessage(String sender, String receiver, String content) {
        Connection conn = DatabaseConnection.getConnection();
        if (conn == null) {
            SystemLogger.warning("[DB] Demo mode — bỏ qua lưu tin nhắn: "
                    + sender + " → " + receiver);
            return false;
        }

        String sql = "INSERT INTO messages (sender, receiver, content, created_at) "
                   + "VALUES (?, ?, ?, NOW())";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, sender);
            ps.setString(2, receiver != null ? receiver : "GROUP");
            ps.setString(3, content);
            int rows = ps.executeUpdate();
            if (rows > 0) {
                SystemLogger.info("[DB] Lưu tin nhắn: " + sender + " → " + receiver);
                return true;
            }
            return false;
        } catch (SQLException e) {
            SystemLogger.error("[DB] Lỗi lưu tin nhắn: " + e.getMessage());
            return false;
        } finally {
            try { conn.close(); } catch (SQLException ignored) {}
        }
    }

    /**
     * Lưu tin nhắn nhóm (wrapper tiện dùng).
     *
     * @param sender  tên người gửi
     * @param content nội dung
     * @return true nếu lưu thành công
     */
    public boolean saveGroupMessage(String sender, String content) {
        return saveMessage(sender, "GROUP", content);
    }

    /**
     * Lưu tin nhắn riêng (wrapper tiện dùng).
     *
     * @param sender   tên người gửi
     * @param receiver tên người nhận
     * @param content  nội dung
     * @return true nếu lưu thành công
     */
    public boolean savePrivateMessage(String sender, String receiver, String content) {
        return saveMessage(sender, receiver, content);
    }

    // ── Lấy lịch sử ──────────────────────────────────────────────────────────

    /**
     * Lấy N tin nhắn nhóm gần nhất (load lịch sử khi user mới join).
     *
     * @param limit số tin nhắn muốn lấy (VD: 50)
     * @return danh sách [sender, content] theo thứ tự cũ → mới
     */
    public List<String[]> getRecentGroupMessages(int limit) {
        List<String[]> list = new ArrayList<>();
        Connection conn = DatabaseConnection.getConnection();
        if (conn == null) return list;

        // Lấy N tin mới nhất rồi đảo lại để hiển thị cũ trước
        String sql = "SELECT sender, content FROM ("
                   + "  SELECT sender, content, created_at FROM messages "
                   + "  WHERE receiver = 'GROUP' "
                   + "  ORDER BY created_at DESC LIMIT ?"
                   + ") sub ORDER BY created_at ASC";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(new String[]{ rs.getString("sender"), rs.getString("content") });
            }
        } catch (SQLException e) {
            SystemLogger.error("[DB] Lỗi lấy lịch sử nhóm: " + e.getMessage());
        } finally {
            try { conn.close(); } catch (SQLException ignored) {}
        }
        return list;
    }

    /**
     * Lấy N tin nhắn riêng giữa 2 người (load lịch sử private khi mở chat).
     *
     * @param user1 tên người thứ nhất
     * @param user2 tên người thứ hai
     * @param limit số tin nhắn
     * @return danh sách [sender, receiver, content] theo thứ tự cũ → mới
     */
    public List<String[]> getRecentPrivateMessages(String user1, String user2, int limit) {
        List<String[]> list = new ArrayList<>();
        Connection conn = DatabaseConnection.getConnection();
        if (conn == null) return list;

        String sql = "SELECT sender, receiver, content FROM ("
                   + "  SELECT sender, receiver, content, created_at FROM messages "
                   + "  WHERE (sender = ? AND receiver = ?) OR (sender = ? AND receiver = ?) "
                   + "  ORDER BY created_at DESC LIMIT ?"
                   + ") sub ORDER BY created_at ASC";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, user1); ps.setString(2, user2);
            ps.setString(3, user2); ps.setString(4, user1);
            ps.setInt(5, limit);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(new String[]{
                        rs.getString("sender"),
                        rs.getString("receiver"),
                        rs.getString("content")
                });
            }
        } catch (SQLException e) {
            SystemLogger.error("[DB] Lỗi lấy lịch sử private: " + e.getMessage());
        } finally {
            try { conn.close(); } catch (SQLException ignored) {}
        }
        return list;
    }

    /**
     * Lấy toàn bộ lịch sử tin nhắn (nhóm + riêng), sắp xếp theo thời gian.
     *
     * @return danh sách [sender, receiver, content]
     */
    public List<String[]> getAllMessages() {
        List<String[]> list = new ArrayList<>();
        Connection conn = DatabaseConnection.getConnection();
        if (conn == null) return list;

        String sql = "SELECT sender, receiver, content "
                   + "FROM messages "
                   + "ORDER BY created_at ASC";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(new String[]{
                        rs.getString("sender"),
                        rs.getString("receiver"),
                        rs.getString("content")
                });
            }
        } catch (SQLException e) {
            SystemLogger.error("[DB] Lỗi lấy toàn bộ lịch sử: " + e.getMessage());
        } finally {
            try { conn.close(); } catch (SQLException ignored) {}
        }
        return list;
    }

    // ── Tìm kiếm & thống kê ──────────────────────────────────────────────────

    /**
     * Tìm kiếm tin nhắn theo từ khóa (trong sender, receiver, content).
     *
     * @param keyword từ khóa tìm kiếm
     * @return danh sách [sender, receiver, content]
     */
    public List<String[]> searchMessages(String keyword) {
        List<String[]> list = new ArrayList<>();
        Connection conn = DatabaseConnection.getConnection();
        if (conn == null) return list;

        String sql = "SELECT sender, receiver, content "
                   + "FROM messages "
                   + "WHERE sender LIKE ? OR receiver LIKE ? OR content LIKE ? "
                   + "ORDER BY created_at DESC";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            String search = "%" + keyword + "%";
            ps.setString(1, search);
            ps.setString(2, search);
            ps.setString(3, search);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(new String[]{
                        rs.getString("sender"),
                        rs.getString("receiver"),
                        rs.getString("content")
                });
            }
        } catch (SQLException e) {
            SystemLogger.error("[DB] Lỗi tìm kiếm tin nhắn: " + e.getMessage());
        } finally {
            try { conn.close(); } catch (SQLException ignored) {}
        }
        return list;
    }

    /**
     * Đếm tổng số tin nhắn trong DB.
     *
     * @return số lượng tin nhắn, 0 nếu lỗi
     */
    public int countMessages() {
        Connection conn = DatabaseConnection.getConnection();
        if (conn == null) return 0;

        String sql = "SELECT COUNT(*) FROM messages";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            SystemLogger.error("[DB] Lỗi đếm tin nhắn: " + e.getMessage());
        } finally {
            try { conn.close(); } catch (SQLException ignored) {}
        }
        return 0;
    }

    // ── Xóa tin nhắn ─────────────────────────────────────────────────────────

    /**
     * Xóa một tin nhắn theo ID.
     *
     * @param id id của tin nhắn trong bảng messages
     * @return true nếu xóa thành công
     */
    public boolean deleteMessage(int id) {
        Connection conn = DatabaseConnection.getConnection();
        if (conn == null) return false;

        String sql = "DELETE FROM messages WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            if (ps.executeUpdate() > 0) {
                SystemLogger.info("[DB] Đã xóa tin nhắn ID = " + id);
                return true;
            }
        } catch (SQLException e) {
            SystemLogger.error("[DB] Lỗi xóa tin nhắn: " + e.getMessage());
        } finally {
            try { conn.close(); } catch (SQLException ignored) {}
        }
        return false;
    }

    /**
     * Xóa toàn bộ lịch sử chat (dùng khi cần reset).
     *
     * @return true nếu xóa thành công
     */
    public boolean clearMessages() {
        Connection conn = DatabaseConnection.getConnection();
        if (conn == null) return false;

        String sql = "DELETE FROM messages";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.executeUpdate();
            SystemLogger.info("[DB] Đã xóa toàn bộ lịch sử chat.");
            return true;
        } catch (SQLException e) {
            SystemLogger.error("[DB] Lỗi xóa lịch sử: " + e.getMessage());
        } finally {
            try { conn.close(); } catch (SQLException ignored) {}
        }
        return false;
    }
}
