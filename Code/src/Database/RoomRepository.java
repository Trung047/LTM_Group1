package Database;

import Logging.SystemLogger;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Thao tac voi bang rooms va room_members trong DB.
 *
 * Cau truc bang (xem migration_phase_c.sql):
 *   rooms(id, name, creator, created_at)
 *   room_members(id, room_name, username, joined_at)
 */
public class RoomRepository {

    // ── Luu phong ────────────────────────────────────────────────────────────

    /**
     * Luu mot phong moi vao DB. Bo qua neu phong da ton tai (UNIQUE name).
     *
     * @param roomName ten phong
     * @param creator  nguoi tao (co the null neu la phong mac dinh)
     * @return true neu luu thanh cong hoac phong da ton tai san
     */
    public boolean saveRoom(String roomName, String creator) {
        if (roomName == null || roomName.trim().isEmpty()) return false;

        Connection conn = DatabaseConnection.getConnection();
        if (conn == null) {
            SystemLogger.warning("[DB] Không kết nối được — bỏ qua lưu phòng: " + roomName);
            return false;
        }

        String sql = "INSERT IGNORE INTO rooms (name, creator) VALUES (?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, roomName.trim());
            ps.setString(2, creator);
            ps.executeUpdate();
            SystemLogger.info("[DB] Lưu phòng: " + roomName + " (tạo bởi " + creator + ")");
            return true;
        } catch (SQLException e) {
            SystemLogger.error("[DB] Lỗi lưu phòng: " + e.getMessage());
            return false;
        } finally {
            try { conn.close(); } catch (SQLException ignored) {}
        }
    }

    // ── Lay danh sach phong ──────────────────────────────────────────────────

    /**
     * Lay toan bo danh sach phong tu DB (dung khi Server khoi dong
     * de nap lai vao UserManager).
     *
     * @return danh sach ten phong, rong neu DB khong khả dung
     */
    public List<String> getAllRooms() {
        List<String> rooms = new ArrayList<>();
        Connection conn = DatabaseConnection.getConnection();
        if (conn == null) return rooms;

        String sql = "SELECT name FROM rooms ORDER BY created_at ASC";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                rooms.add(rs.getString("name"));
            }
        } catch (SQLException e) {
            SystemLogger.error("[DB] Lỗi lấy danh sách phòng: " + e.getMessage());
        } finally {
            try { conn.close(); } catch (SQLException ignored) {}
        }
        return rooms;
    }

    // ── Thanh vien phong ─────────────────────────────────────────────────────

    /**
     * Ghi nhan mot user da/dang tham gia mot phong. Bo qua neu da co
     * (UNIQUE room_name + username).
     *
     * @param roomName ten phong
     * @param username nguoi tham gia
     * @return true neu luu thanh cong
     */
    public boolean addMember(String roomName, String username) {
        if (roomName == null || username == null) return false;

        Connection conn = DatabaseConnection.getConnection();
        if (conn == null) {
            SystemLogger.warning("[DB] Không kết nối được — bỏ qua lưu thành viên phòng: "
                    + username + " → " + roomName);
            return false;
        }

        String sql = "INSERT IGNORE INTO room_members (room_name, username) VALUES (?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, roomName.trim());
            ps.setString(2, username.trim());
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            SystemLogger.error("[DB] Lỗi lưu thành viên phòng: " + e.getMessage());
            return false;
        } finally {
            try { conn.close(); } catch (SQLException ignored) {}
        }
    }

    /**
     * Lay danh sach thanh vien (tung tham gia) cua mot phong.
     *
     * @param roomName ten phong
     * @return danh sach username, rong neu DB khong khả dung
     */
    public List<String> getRoomMembers(String roomName) {
        List<String> members = new ArrayList<>();
        Connection conn = DatabaseConnection.getConnection();
        if (conn == null) return members;

        String sql = "SELECT username FROM room_members WHERE room_name = ? ORDER BY joined_at ASC";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, roomName);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                members.add(rs.getString("username"));
            }
        } catch (SQLException e) {
            SystemLogger.error("[DB] Lỗi lấy thành viên phòng: " + e.getMessage());
        } finally {
            try { conn.close(); } catch (SQLException ignored) {}
        }
        return members;
    }

    // ── Phase D: xóa thành viên (tính năng 1), kiểm tra creator/membership ──

    /**
     * Xóa một thành viên khỏi phòng (dùng khi creator xóa thành viên,
     * hoặc khi thành viên tự rời phòng vĩnh viễn khỏi danh sách DB).
     *
     * @param roomName tên phòng
     * @param username thành viên cần xóa
     * @return true nếu xóa thành công (kể cả khi trước đó chưa từng có, coi như đã đạt trạng thái mong muốn)
     */
    public boolean removeMember(String roomName, String username) {
        if (roomName == null || username == null) return false;

        Connection conn = DatabaseConnection.getConnection();
        if (conn == null) {
            SystemLogger.warning("[DB] Không kết nối được — bỏ qua xóa thành viên phòng: "
                    + username + " khỏi " + roomName);
            return false;
        }

        String sql = "DELETE FROM room_members WHERE room_name = ? AND username = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, roomName.trim());
            ps.setString(2, username.trim());
            ps.executeUpdate();
            SystemLogger.info("[DB] Đã xóa thành viên " + username + " khỏi phòng " + roomName);
            return true;
        } catch (SQLException e) {
            SystemLogger.error("[DB] Lỗi xóa thành viên phòng: " + e.getMessage());
            return false;
        } finally {
            try { conn.close(); } catch (SQLException ignored) {}
        }
    }

    /**
     * Lấy tên người tạo (creator) của một phòng — dùng để kiểm tra quyền
     * xóa/thêm thành viên (chỉ creator mới được phép).
     *
     * @param roomName tên phòng
     * @return username của creator, hoặc null nếu phòng không có creator
     *         (vd phòng mặc định "General") hoặc không tìm thấy/DB lỗi
     */
    public String getRoomCreator(String roomName) {
        Connection conn = DatabaseConnection.getConnection();
        if (conn == null) return null;

        String sql = "SELECT creator FROM rooms WHERE name = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, roomName);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getString("creator");
        } catch (SQLException e) {
            SystemLogger.error("[DB] Lỗi lấy creator phòng: " + e.getMessage());
        } finally {
            try { conn.close(); } catch (SQLException ignored) {}
        }
        return null;
    }

    /**
     * Kiểm tra một user có đang là thành viên của phòng hay không.
     *
     * @param roomName tên phòng
     * @param username username cần kiểm tra
     * @return true nếu là thành viên
     */
    public boolean isMember(String roomName, String username) {
        Connection conn = DatabaseConnection.getConnection();
        if (conn == null) return false;

        String sql = "SELECT 1 FROM room_members WHERE room_name = ? AND username = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, roomName);
            ps.setString(2, username);
            ResultSet rs = ps.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            SystemLogger.error("[DB] Lỗi kiểm tra thành viên phòng: " + e.getMessage());
            return false;
        } finally {
            try { conn.close(); } catch (SQLException ignored) {}
        }
    }
}
