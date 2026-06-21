package Database;

import Logging.SystemLogger;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class MessageRepository {

    public boolean saveMessage(String sender, String receiver, String content) {
        String query = "INSERT INTO messages (sender, receiver, content) VALUES (?, ?, ?)";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
             
            if (conn == null) return false;

            pstmt.setString(1, sender);
            pstmt.setString(2, receiver);
            pstmt.setString(3, content);
            
            int rowsAffected = pstmt.executeUpdate();
            
            if (rowsAffected > 0) {
                SystemLogger.info("Đã lưu tin nhắn từ " + sender + " gửi cho " + receiver);
                return true;
            }
            return false;
            
        } catch (SQLException e) {
            SystemLogger.error("Lỗi khi lưu tin nhắn vào CSDL: " + e.getMessage());
            return false;
        }
    }
}
