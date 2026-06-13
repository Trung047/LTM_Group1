package Database;

import Logging.SystemLogger;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseConnection {
    // Thay đổi thông tin này cho phù hợp với CSDL của bạn
    private static final String URL = "jdbc:mysql://localhost:3306/your_chat_database"; 
    private static final String USER = "root";
    private static final String PASSWORD = "your_password";

    public static Connection getConnection() throws SQLException {
        try {
            return DriverManager.getConnection(URL, USER, PASSWORD);
        } catch (SQLException e) {
            SystemLogger.error("Lỗi kết nối Database: " + e.getMessage());
            throw e;
        }
    }
}
