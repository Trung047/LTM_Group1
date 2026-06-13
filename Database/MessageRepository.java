package Database;

import Logging.SystemLogger;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class MessageRepository {

    public boolean saveMessage(String sender, String receiver, String content) {
        String query = "INSERT INTO messages (sender, receiver, content, timestamp) VALUES (?, ?, ?, NOW())";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
             
            pstmt.setString(1, sender);
            pstmt.setString(2, receiver);
            pstmt.setString(3, content);
            
            pstmt.executeUpdate();
            SystemLogger.info("Đã lưu tin nhắn từ " + sender + " đến " + receiver);
            return true;
        } catch (SQLException e) {
            SystemLogger.error("Lỗi lưu tin nhắn: " + e.getMessage());
            return false;
        }
    }
}
