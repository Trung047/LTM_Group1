package Server;

import Logging.SystemLogger;

import java.io.*;
import java.net.*;
import java.util.Properties;

/**
 * Entry point của Server.
 *
 * Khởi động: chạy main() → lắng nghe port từ config.properties (mặc định 8080)
 * Mỗi client kết nối → tạo ClientHandler trên thread riêng.
 */
public class Server {

    public static void main(String[] args) {
        int port = readPort();
        UserManager userManager = new UserManager();

        SystemLogger.info("NetChat Server khởi động trên cổng " + port);

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            serverSocket.setReuseAddress(true);
            SystemLogger.info("Đang chờ client kết nối...");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                clientSocket.setTcpNoDelay(true);   // giảm latency, ping chính xác hơn
                SystemLogger.info("Client mới kết nối: "
                    + clientSocket.getInetAddress().getHostAddress()
                    + ":" + clientSocket.getPort());

                ClientHandler handler = new ClientHandler(clientSocket, userManager);
                Thread t = new Thread(handler, "client-" + clientSocket.getPort());
                t.setDaemon(true);
                t.start();
            }

        } catch (IOException e) {
            SystemLogger.error("Server lỗi: " + e.getMessage());
            System.exit(1);
        }
    }

    /** Đọc server.port từ config.properties, mặc định 8080. */
    private static int readPort() {
        try (FileInputStream fis = new FileInputStream("config.properties")) {
            Properties p = new Properties();
            p.load(fis);
            int port = Integer.parseInt(p.getProperty("server.port", "8080").trim());
            if (port < 1 || port > 65535) throw new NumberFormatException("Port ngoài phạm vi");
            return port;
        } catch (Exception e) {
            SystemLogger.warning("Không đọc được config, dùng port mặc định 8080. Lý do: " + e.getMessage());
            return 8080;
        }
    }
}
