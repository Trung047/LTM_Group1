package Database;

import Logging.SystemLogger;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Quản lý kết nối MySQL.
 * Đọc thông tin kết nối từ config.properties thay vì hardcode.
 */
public class DatabaseConnection {

    // ── Đọc config từ file config.properties ──────────────────────────────────
    private static final String DB_URL;
    private static final String DB_USER;
    private static final String DB_PASS;

    static {
        Properties props = new Properties();
        String url  = "jdbc:mysql://localhost:3306/ltm_db";
        String user = "root";
        String pass = "123456";

        boolean loaded = false;
        try (InputStream fis = new FileInputStream("config.properties")) {
            props.load(fis);
            loaded = true;
            System.out.println("[DB] Đọc config.properties từ working directory thành công.");
        } catch (IOException ignored) {
            try (InputStream is = DatabaseConnection.class
                    .getClassLoader()
                    .getResourceAsStream("config.properties")) {
                if (is != null) {
                    props.load(is);
                    loaded = true;
                    System.out.println("[DB] Đọc config.properties từ classpath thành công.");
                }
            } catch (IOException e) {
                System.out.println("[DB] Lỗi đọc config.properties: " + e.getMessage());
            }
        }

        if (loaded) {
            String host = props.getProperty("db.host", "localhost");
            String port = props.getProperty("db.port", "3306");
            String name = props.getProperty("db.name", "ltm_db");
            user = props.getProperty("db.username", "root");
            pass = props.getProperty("db.password", "");
            url = "jdbc:mysql://" + host + ":" + port + "/" + name
                    + "?useSSL=false&allowPublicKeyRetrieval=true"
                    + "&serverTimezone=Asia/Ho_Chi_Minh"
                    + "&characterEncoding=UTF-8";
        } else {
            System.out.println("[DB] Không tìm thấy config.properties — dùng giá trị mặc định.");
        }

        DB_URL  = url;
        DB_USER = user;
        DB_PASS = pass;
    }

    // ── Tạo kết nối ──────────────────────────────────────────────────────────

    /**
     * Tạo và trả về một Connection mới đến MySQL.
     * Trả về null nếu không kết nối được (demo/offline mode).
     */
    public static Connection getConnection() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
            SystemLogger.info("Kết nối Database thành công.");
            return conn;
        } catch (ClassNotFoundException e) {
            SystemLogger.warning("Không tìm thấy Driver MySQL, chuyển sang chế độ fallback: " + e.getMessage());
        } catch (SQLException e) {
            SystemLogger.warning("Lỗi kết nối Database, chuyển sang chế độ fallback: " + e.getMessage());
        }
        return null;
    }

    // ── Kiểm tra kết nối ─────────────────────────────────────────────────────

    /**
     * Kiểm tra Database có hoạt động không.
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

    // ── Đóng tài nguyên ──────────────────────────────────────────────────────

    /** Đóng Connection. */
    public static void close(Connection conn) {
        try {
            if (conn != null && !conn.isClosed()) conn.close();
        } catch (SQLException e) {
            SystemLogger.error("Lỗi đóng Connection: " + e.getMessage());
        }
    }

    /** Đóng PreparedStatement. */
    public static void close(PreparedStatement ps) {
        try {
            if (ps != null) ps.close();
        } catch (SQLException e) {
            SystemLogger.error("Lỗi đóng PreparedStatement: " + e.getMessage());
        }
    }

    /** Đóng ResultSet. */
    public static void close(ResultSet rs) {
        try {
            if (rs != null) rs.close();
        } catch (SQLException e) {
            SystemLogger.error("Lỗi đóng ResultSet: " + e.getMessage());
        }
    }

    /** Đóng đồng thời ResultSet, PreparedStatement và Connection. */
    public static void close(ResultSet rs, PreparedStatement ps, Connection conn) {
        close(rs);
        close(ps);
        close(conn);
    }

    // ── Thông tin Database ────────────────────────────────────────────────────

    /** In thông tin Database ra console (dùng để debug). */
    public static void printDatabaseInfo() {
        try (Connection conn = getConnection()) {
            if (conn != null) {
                System.out.println("=================================");
                System.out.println("Database URL : " + DB_URL);
                System.out.println("User         : " + DB_USER);
                System.out.println("Product      : " + conn.getMetaData().getDatabaseProductName());
                System.out.println("Version      : " + conn.getMetaData().getDatabaseProductVersion());
                System.out.println("=================================");
            }
        } catch (SQLException e) {
            SystemLogger.error("Không lấy được thông tin Database: " + e.getMessage());
        }
    }

    // ── Test nhanh ───────────────────────────────────────────────────────────

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
