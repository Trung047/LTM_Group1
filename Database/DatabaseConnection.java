package database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseConnection {
    private static final String URL = "jdbc:sqlserver://localhost:1433;"
            + "databaseName=ChatAppDB;"
            + "encrypt=true;trustServerCertificate=true;";
    
    private static final String USER = "sa";       
    private static final String PASSWORD = "123456"; 

    public static Connection getConnection() throws SQLException {
        try {
            Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
        } catch (ClassNotFoundException e) {
            System.err.println("Không tìm thấy Microsoft JDBC Driver! Hãy add file .jar vào thư viện.");
            e.printStackTrace();
        }
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }
}
