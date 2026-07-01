package Database;

import model.Room;
import Logging.SystemLogger; // Import SystemLogger của bạn

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class RoomRepository {

    /**
     * Tạo phòng mới trong database
     * @return ID của phòng vừa tạo, hoặc -1 nếu thất bại
     */
    public int createRoom(String roomName) {
        String sql = "INSERT INTO rooms (name) VALUES (?)";
        
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            conn = DatabaseConnection.getConnection();
            if (conn == null) return -1;

            ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, roomName);
            int affectedRows = ps.executeUpdate();

            if (affectedRows > 0) {
                rs = ps.getGeneratedKeys();
                if (rs.next()) {
                    int newRoomId = rs.getInt(1);
                    SystemLogger.info("Tạo phòng mới thành công: [" + newRoomId + "] " + roomName);
                    return newRoomId; 
                }
            }
        } catch (SQLException e) {
            SystemLogger.error("Lỗi khi tạo phòng (createRoom): " + e.getMessage());
        } finally {
            // Sử dụng hàm đóng tài nguyên tiện ích từ DatabaseConnection của bạn
            DatabaseConnection.close(rs, ps, conn);
        }
        return -1;
    }

    /**
     * Lấy toàn bộ danh sách phòng hiện có
     */
    public List<Room> getAllRooms() {
        List<Room> rooms = new ArrayList<>();
        String sql = "SELECT id, name FROM rooms";
        
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            conn = DatabaseConnection.getConnection();
            if (conn == null) return rooms;

            ps = conn.prepareStatement(sql);
            rs = ps.executeQuery();

            while (rs.next()) {
                rooms.add(new Room(rs.getInt("id"), rs.getString("name")));
            }
        } catch (SQLException e) {
            SystemLogger.error("Lỗi lấy danh sách phòng (getAllRooms): " + e.getMessage());
        } finally {
            DatabaseConnection.close(rs, ps, conn);
        }
        return rooms;
    }

    /**
     * Tìm kiếm phòng theo ID
     */
    public Room getRoomById(int roomId) {
        String sql = "SELECT id, name FROM rooms WHERE id = ?";
        
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            conn = DatabaseConnection.getConnection();
            if (conn == null) return null;

            ps = conn.prepareStatement(sql);
            ps.setInt(1, roomId);
            rs = ps.executeQuery();

            if (rs.next()) {
                return new Room(rs.getInt("id"), rs.getString("name"));
            }
        } catch (SQLException e) {
            SystemLogger.error("Lỗi tìm phòng (getRoomById): " + e.getMessage());
        } finally {
            DatabaseConnection.close(rs, ps, conn);
        }
        return null;
    }

    /**
     * Xóa phòng theo ID
     */
    public boolean deleteRoom(int roomId) {
        String sql = "DELETE FROM rooms WHERE id = ?";
        
        Connection conn = null;
        PreparedStatement ps = null;

        try {
            conn = DatabaseConnection.getConnection();
            if (conn == null) return false;

            ps = conn.prepareStatement(sql);
            ps.setInt(1, roomId);
            
            int affectedRows = ps.executeUpdate();
            if (affectedRows > 0) {
                SystemLogger.info("Đã xóa phòng có ID: " + roomId);
                return true;
            }
        } catch (SQLException e) {
            SystemLogger.error("Lỗi khi xóa phòng (deleteRoom): " + e.getMessage());
        } finally {
            // Không có ResultSet nên chỉ truyền ps và conn
            DatabaseConnection.close(null, ps, conn);
        }
        return false;
    }
}
