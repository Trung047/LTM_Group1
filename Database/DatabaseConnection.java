package Database;

import Logging.SystemLogger;
import java.io.FileInputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

public class DatabaseConnection {

    private static String DB_URL;
    private static String USER;
    private static String PASS;

    static {
        try {
            Properties p = new Properties();
            p.load(new FileInputStream("config.properties"));
            String host = p.getProperty("db.host", "localhost");
            String port = p.getProperty("db.port", "3306");
            String name = p.getProperty("db.name", "ltm_db");
            USER = p.getProperty("db.username", "root");
            PASS = p.getProperty("db.password", "");
            DB_URL = "jdbc:mysql://" + host + ":" + port + "/" + name
                   + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
        } catch (IOException e) {
            // Fallback defaults
            DB_URL = "jdbc:mysql://localhost:3306/ltm_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
            USER   = "root";
            PASS   = "";
            SystemLogger.warning("Không đọc được config.properties, dùng giá trị mặc định DB.");
        }
    }

    public static Connection getConnection() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            return DriverManager.getConnection(DB_URL, USER, PASS);
        } catch (ClassNotFoundException e) {
            SystemLogger.error("Không tìm thấy MySQL JDBC Driver: " + e.getMessage());
        } catch (SQLException e) {
            SystemLogger.error("Lỗi kết nối CSDL: " + e.getMessage());
        }
        return null;
    }
}
