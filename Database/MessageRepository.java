package Database;

import Logging.SystemLogger;
import model.Message;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Thao tác với bảng messages trong DB.
 *
 * Cấu trúc bảng (từ schema.sql / migration_phase_c.sql + migration_phase_d.sql):
 *   messages(id, sender, receiver, content, room, recalled, type, file_name, created_at)
 *   receiver = 'GROUP'    → tin nhắn nhóm (room = tên phòng, vd 'General')
 *   receiver = username   → tin nhắn riêng (room = NULL, không thuộc phòng nào)
 *   type     = 'TEXT'     → content là văn bản
 *   type     = 'FILE'     → content là dữ liệu Base64, file_name là tên file gốc
 *   recalled = 1          → tin đã bị người gửi thu hồi (Phase D)
 */
public class MessageRepository {

    // ── Lưu tin nhắn văn bản ─────────────────────────────────────────────────

    /**
     * Lưu tin nhắn văn bản vào DB (dùng chung cho nhóm và riêng).
     *
     * @param sender   tên người gửi
     * @param receiver 'GROUP' hoặc username người nhận
     * @param room     tên phòng (chỉ áp dụng khi receiver = 'GROUP'; null cho tin riêng)
     * @param content  nội dung tin nhắn
     * @return id được sinh ra (auto-increment) nếu lưu thành công, -1 nếu thất bại
     */
    public long saveMessage(String sender, String receiver, String room, String content) {
        return saveInternal(sender, receiver, room, content, "TEXT", null);
    }

    /** Lưu tin nhắn nhóm gắn với một phòng cụ thể (wrapper tiện dùng). */
    public long saveGroupMessage(String sender, String room, String content) {
        return saveMessage(sender, "GROUP", room, content);
    }

    /** Lưu tin nhắn riêng (wrapper tiện dùng). Tin riêng không thuộc phòng nào. */
    public long savePrivateMessage(String sender, String receiver, String content) {
        return saveMessage(sender, receiver, null, content);
    }

    // ── Lưu tin nhắn file/ảnh (Phase D — tính năng 7) ───────────────────────

    /**
     * Lưu tin nhắn file/ảnh (nội dung đã mã hóa Base64) vào bảng messages.
     *
     * @param sender   người gửi
     * @param receiver 'GROUP' hoặc username người nhận
     * @param room     tên phòng nếu receiver = 'GROUP', null nếu là tin riêng
     * @param fileName tên file gốc
     * @param base64   nội dung file đã mã hóa Base64
     * @return id được sinh ra nếu lưu thành công, -1 nếu thất bại
     */
    public long saveFileMessage(String sender, String receiver, String room, String fileName, String base64) {
        return saveInternal(sender, receiver, room, base64, "FILE", fileName);
    }

    private long saveInternal(String sender, String receiver, String room, String content,
                               String type, String fileName) {
        Connection conn = DatabaseConnection.getConnection();
        if (conn == null) {
            SystemLogger.warning("[DB] Không có kết nối — bỏ qua lưu tin nhắn: "
                    + sender + " → " + receiver);
            return -1;
        }

        String sql = "INSERT INTO messages (sender, receiver, content, room, type, file_name, created_at) "
                   + "VALUES (?, ?, ?, ?, ?, ?, NOW())";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, sender);
            ps.setString(2, receiver != null ? receiver : "GROUP");
            ps.setString(3, content);
            ps.setString(4, room);
            ps.setString(5, type);
            ps.setString(6, fileName);
            int rows = ps.executeUpdate();
            if (rows > 0) {
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    long id = keys.next() ? keys.getLong(1) : -1;
                    SystemLogger.info("[DB] Lưu tin nhắn (" + type + ") id=" + id + ": "
                            + sender + " → " + receiver + (room != null ? " [" + room + "]" : ""));
                    return id;
                }
            }
            return -1;
        } catch (SQLException e) {
            SystemLogger.error("[DB] Lỗi lưu tin nhắn: " + e.getMessage());
            return -1;
        } finally {
            try { conn.close(); } catch (SQLException ignored) {}
        }
    }

    // ── Thu hồi tin nhắn (Phase D — tính năng 8) ────────────────────────────

    /**
     * Thu hồi 1 tin nhắn. Chỉ cho phép nếu người yêu cầu đúng là người gửi
     * tin nhắn đó (kiểm tra lại với DB, không tin client).
     *
     * @param id                id tin nhắn cần thu hồi
     * @param requesterUsername username của người đang yêu cầu thu hồi
     * @return true nếu thu hồi thành công (đúng chủ, tồn tại, chưa thu hồi trước đó)
     */
    public boolean recallMessage(long id, String requesterUsername) {
        Connection conn = DatabaseConnection.getConnection();
        if (conn == null) return false;

        String checkSql = "SELECT sender, recalled FROM messages WHERE id = ?";
        try (PreparedStatement check = conn.prepareStatement(checkSql)) {
            check.setLong(1, id);
            try (ResultSet rs = check.executeQuery()) {
                if (!rs.next()) {
                    SystemLogger.warning("[DB] Thu hồi thất bại — không tìm thấy tin nhắn id=" + id);
                    return false;
                }
                if (!rs.getString("sender").equals(requesterUsername)) {
                    SystemLogger.warning("[DB] Thu hồi bị từ chối — " + requesterUsername
                            + " không phải người gửi tin nhắn id=" + id);
                    return false;
                }
                if (rs.getBoolean("recalled")) {
                    return true; // da thu hoi tu truoc, coi nhu thanh cong (idempotent)
                }
            }

            String sql = "UPDATE messages SET recalled = 1 WHERE id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, id);
                boolean ok = ps.executeUpdate() > 0;
                if (ok) SystemLogger.info("[DB] Đã thu hồi tin nhắn id=" + id + " bởi " + requesterUsername);
                return ok;
            }
        } catch (SQLException e) {
            SystemLogger.error("[DB] Lỗi thu hồi tin nhắn: " + e.getMessage());
            return false;
        } finally {
            try { conn.close(); } catch (SQLException ignored) {}
        }
    }

    // ── Tra cứu 1 tin nhắn theo id (Phase D — hỗ trợ thu hồi) ───────────────

    /**
     * Lấy đầy đủ thông tin 1 tin nhắn theo id (dùng sau khi thu hồi để biết
     * đây là tin nhóm (room) hay tin riêng (receiver = username) và thông
     * báo đúng người liên quan).
     *
     * @param id id tin nhắn
     * @return Message nếu tìm thấy, null nếu không có / lỗi DB
     */
    public Message getMessageById(long id) {
        Connection conn = DatabaseConnection.getConnection();
        if (conn == null) return null;

        String sql = "SELECT id, sender, receiver, content, room, recalled, type, file_name, created_at "
                   + "FROM messages WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return new Message(
                    rs.getLong("id"), rs.getString("sender"), rs.getString("receiver"),
                    rs.getString("room"), rs.getString("content"),
                    rs.getTimestamp("created_at").getTime(),
                    rs.getBoolean("recalled"), rs.getString("type"), rs.getString("file_name")
                );
            }
        } catch (SQLException e) {
            SystemLogger.error("[DB] Lỗi tra cứu tin nhắn theo id: " + e.getMessage());
            return null;
        } finally {
            try { conn.close(); } catch (SQLException ignored) {}
        }
    }

    // ── Lấy lịch sử (Phase D: trả về Message giàu dữ liệu thay vì String[]) ─

    /**
     * Lấy N tin nhắn nhóm gần nhất của một phòng cụ thể.
     *
     * @param room  tên phòng (vd 'General')
     * @param limit số tin nhắn muốn lấy (VD: 50)
     * @return danh sách Message theo thứ tự cũ → mới
     */
    public List<Message> getRecentGroupMessages(String room, int limit) {
        List<Message> list = new ArrayList<>();
        Connection conn = DatabaseConnection.getConnection();
        if (conn == null) return list;

        String sql = "SELECT id, sender, content, room, recalled, type, file_name, created_at FROM ("
                   + "  SELECT id, sender, content, room, recalled, type, file_name, created_at FROM messages "
                   + "  WHERE receiver = 'GROUP' AND room = ? "
                   + "  ORDER BY created_at DESC LIMIT ?"
                   + ") sub ORDER BY created_at ASC";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, room);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs, "GROUP"));
            }
        } catch (SQLException e) {
            SystemLogger.error("[DB] Lỗi lấy lịch sử nhóm: " + e.getMessage());
        } finally {
            try { conn.close(); } catch (SQLException ignored) {}
        }
        return list;
    }

    /**
     * Lấy N tin nhắn riêng gần nhất giữa 2 người.
     *
     * @param user1 tên người thứ nhất
     * @param user2 tên người thứ hai
     * @param limit số tin nhắn
     * @return danh sách Message theo thứ tự cũ → mới
     */
    public List<Message> getRecentPrivateMessages(String user1, String user2, int limit) {
        List<Message> list = new ArrayList<>();
        Connection conn = DatabaseConnection.getConnection();
        if (conn == null) return list;

        String sql = "SELECT id, sender, receiver, content, recalled, type, file_name, created_at FROM ("
                   + "  SELECT id, sender, receiver, content, recalled, type, file_name, created_at FROM messages "
                   + "  WHERE (sender = ? AND receiver = ?) OR (sender = ? AND receiver = ?) "
                   + "  ORDER BY created_at DESC LIMIT ?"
                   + ") sub ORDER BY created_at ASC";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, user1); ps.setString(2, user2);
            ps.setString(3, user2); ps.setString(4, user1);
            ps.setInt(5, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new Message(
                        rs.getLong("id"), rs.getString("sender"), rs.getString("receiver"),
                        null, rs.getString("content"),
                        rs.getTimestamp("created_at").getTime(),
                        rs.getBoolean("recalled"), rs.getString("type"), rs.getString("file_name")
                    ));
                }
            }
        } catch (SQLException e) {
            SystemLogger.error("[DB] Lỗi lấy lịch sử private: " + e.getMessage());
        } finally {
            try { conn.close(); } catch (SQLException ignored) {}
        }
        return list;
    }

    // ── Tìm kiếm tin nhắn trong 1 phòng (Phase D — tính năng 6) ─────────────

    /**
     * Tìm tin nhắn nhóm theo từ khóa, chỉ trong phạm vi 1 phòng cụ thể
     * (tin đã thu hồi bị loại khỏi kết quả tìm kiếm).
     *
     * @param room    tên phòng cần tìm
     * @param keyword từ khóa (tìm trong nội dung, không phân biệt hoa/thường)
     * @param limit   số kết quả tối đa
     * @return danh sách Message khớp, mới → cũ
     */
    public List<Message> searchMessagesInRoom(String room, String keyword, int limit) {
        List<Message> list = new ArrayList<>();
        Connection conn = DatabaseConnection.getConnection();
        if (conn == null) return list;

        String sql = "SELECT id, sender, content, room, recalled, type, file_name, created_at FROM messages "
                   + "WHERE receiver = 'GROUP' AND room = ? AND recalled = 0 AND type = 'TEXT' "
                   + "AND content LIKE ? ORDER BY created_at DESC LIMIT ?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, room);
            ps.setString(2, "%" + keyword + "%");
            ps.setInt(3, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs, "GROUP"));
            }
        } catch (SQLException e) {
            SystemLogger.error("[DB] Lỗi tìm kiếm tin nhắn trong phòng: " + e.getMessage());
        } finally {
            try { conn.close(); } catch (SQLException ignored) {}
        }
        return list;
    }

    private Message mapRow(ResultSet rs, String receiver) throws SQLException {
        return new Message(
            rs.getLong("id"), rs.getString("sender"), receiver,
            rs.getString("room"), rs.getString("content"),
            rs.getTimestamp("created_at").getTime(),
            rs.getBoolean("recalled"), rs.getString("type"), rs.getString("file_name")
        );
    }

    // ── Lấy toàn bộ lịch sử tin nhắn (giữ nguyên từ bản gốc, không ai gọi hiện tại) ──

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

    // ── Tìm kiếm & thống kê (giữ nguyên từ bản gốc, không ai gọi hiện tại) ────

    /**
     * Tìm kiếm tin nhắn theo từ khóa (trong sender, receiver, content) — toàn DB.
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

    // ── Xóa tin nhắn (giữ nguyên từ bản gốc, không ai gọi hiện tại) ──────────

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
