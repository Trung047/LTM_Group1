package Database;

import Logging.SystemLogger;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseConnection {
    private static final String DB_URL = "jdbc:mysql://localhost:3306/ltm_db";
    private static final String USER = "root";       // Tên đăng nhập database
    private static final String PASS = "123456";     // Mật khẩu database

    public static Connection getConnection() {
        Connection connection = null;
        try {
            Class.forName("com.mysql.cj.jdbc.Driver"); 
            connection = DriverManager.getConnection(DB_URL, USER, PASS);
        } catch (ClassNotFoundException e) {
            SystemLogger.error("Không tìm thấy Driver kết nối CSDL: " + e.getMessage());
        } catch (SQLException e) {
            SystemLogger.error("Lỗi kết nối CSDL: " + e.getMessage());
        }
        return connection;
    }
}
