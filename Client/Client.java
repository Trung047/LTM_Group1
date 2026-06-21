import java.io.*;
import java.net.*;
import java.util.Properties;

public class Client {
    private Socket socket;
    private PrintWriter writer;
    private BufferedReader reader;
    private Thread receiveThread;
    private boolean isRunning = false;

    // Hàm kết nối được gọi bởi LoginFrame hoặc UI
    public boolean connect() {
        try {
            // Đọc cấu hình từ config.properties của Client
            Properties prop = new Properties();
            prop.load(new FileInputStream("Client/config.properties"));
            String host = prop.getProperty("server.host", "localhost");
            int port = Integer.parseInt(prop.getProperty("server.port", "8080"));

            this.socket = new Socket(host, port);
            this.writer = new PrintWriter(socket.getOutputStream(), true);
            this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            this.isRunning = true;

            // Khởi tạo luồng ngầm tự động nhận tin nhắn từ Server về cho Client
            startListening();

            System.out.println("[Client] Kết nối Server thành công!");
            return true;
        } catch (IOException e) {
            System.out.println("[Client Lỗi] Không thể kết nối: " + e.getMessage());
            return false;
        }
    }

    // Luồng ngầm liên tục lắng nghe Server bắn dữ liệu về
    private void startListening() {
        receiveThread = new Thread(() -> {
            try {
                String message;
                while (isRunning && (message = reader.readLine()) != null) {
                    // Nhận được tin nhắn thô từ Server -> Ném sang MessageHandler của bên Client xử lý
                    MessageHandler.handleFromServer(message);
                }
            } catch (IOException e) {
                System.out.println("[Client] Mất kết nối tới Server.");
            } finally {
                disconnect();
            }
        });
        receiveThread.start();
    }

    // Hàm gửi tin nhắn được gọi bởi ChatFrame (giao diện chat) khi bấm nút Gửi
    public void sendMessage(String message) {
        if (writer != null) {
            writer.println(message);
        }
    }

    // Ngắt kết nối an toàn
    public void disconnect() {
        try {
            isRunning = false;
            if (reader != null) reader.close();
            if (writer != null) writer.close();
            if (socket != null && !socket.isClosed()) socket.close();
            System.out.println("[Client] Đã đóng kết nối mạng.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
