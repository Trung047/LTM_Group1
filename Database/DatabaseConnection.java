package Database;

import Logging.SystemLogger;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class DatabaseConnection {

    // Thông tin kết nối MySQL
    private static final String DB_URL = "jdbc:mysql://localhost:3306/ltm_db";
    private static final String USER = "root";
    private static final String PASS = "Kiet@2006!";
    /**
     * Tạo kết nối đến cơ sở dữ liệu
     */
    public static Connection getConnection() {
        Connection connection = null;

        try {
            // Nạp Driver MySQL
            Class.forName("com.mysql.cj.jdbc.Driver");

            // Kết nối Database
            connection = DriverManager.getConnection(DB_URL, USER, PASS);

            SystemLogger.info("Kết nối Database thành công.");

        } catch (ClassNotFoundException e) {
            SystemLogger.error("Không tìm thấy Driver MySQL: " + e.getMessage());

        } catch (SQLException e) {
            SystemLogger.error("Lỗi kết nối Database: " + e.getMessage());
        }

        return connection;
    }

    /**
     * Kiểm tra Database có hoạt động không
     */
    public static boolean testConnection() {

        try (Connection conn = getConnection()) {

            if (conn != null && !conn.isClosed()) {
                SystemLogger.info("Database đang hoạt động.");
                return true;
            }

        } catch (SQLException e) {
            SystemLogger.error("Lỗi kiểm tra kết nối: " + e.getMessage());
        }

        return false;
    }

    /**
     * Đóng Connection
     */
    public static void close(Connection conn) {

        try {

            if (conn != null && !conn.isClosed()) {
                conn.close();
            }

        } catch (SQLException e) {
            SystemLogger.error("Lỗi đóng Connection: " + e.getMessage());
        }
    }

    /**
     * Đóng PreparedStatement
     */
    public static void close(PreparedStatement ps) {

        try {

            if (ps != null) {
                ps.close();
            }

        } catch (SQLException e) {
            SystemLogger.error("Lỗi đóng PreparedStatement: " + e.getMessage());
        }
    }

    /**
     * Đóng ResultSet
     */
    public static void close(ResultSet rs) {

        try {

            if (rs != null) {
                rs.close();
            }

        } catch (SQLException e) {
            SystemLogger.error("Lỗi đóng ResultSet: " + e.getMessage());
        }
    }

    /**
     * Đóng đồng thời ResultSet, PreparedStatement và Connection
     */
    public static void close(ResultSet rs, PreparedStatement ps, Connection conn) {
        close(rs);
        close(ps);
        close(conn);
    }

    /**
     * Hiển thị thông tin Database
     */
    public static void printDatabaseInfo() {

        try (Connection conn = getConnection()) {

            if (conn != null) {
                System.out.println("=================================");
                System.out.println("Database URL : " + DB_URL);
                System.out.println("User         : " + USER);
                System.out.println("Product      : " + conn.getMetaData().getDatabaseProductName());
                System.out.println("Version      : " + conn.getMetaData().getDatabaseProductVersion());
                System.out.println("=================================");
            }

        } catch (SQLException e) {
            SystemLogger.error("Không lấy được thông tin Database: " + e.getMessage());
        }
    }

    /**
     * Chạy thử kết nối Database
     */
    public static void main(String[] args) {

        if (testConnection()) {

            System.out.println("================================");
            System.out.println("Kết nối MySQL thành công.");
            System.out.println("================================");

            printDatabaseInfo();

        } else {

            System.out.println("================================");
            System.out.println("Kết nối MySQL thất bại.");
            System.out.println("================================");

        }

    }

}
