import java.io.*;
import java.net.*;

// moi client ket noi vao se co 1 ClientHandler rieng chay tren 1 thread rieng
public class ClientHandler implements Runnable {

    private Socket socket;
    private BufferedReader reader;
    private PrintWriter writer;
    private UserManager userManager;
    private String username;

    public ClientHandler(Socket socket, UserManager userManager) {
        this.socket = socket;
        this.userManager = userManager;
    }

    @Override
    public void run() {
        try {
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            writer = new PrintWriter(socket.getOutputStream(), true);

            writer.println("Enter your name: ");
            username = reader.readLine();

            if (username == null || username.trim().isEmpty()) {
                username = "Anonymous";
            }

            System.out.println("[Server] " + username + " connected.");
            userManager.broadcastAll("[System] " + username + " joined the chat.");

            String message;
            while ((message = reader.readLine()) != null) {
                System.out.println("[" + username + "]: " + message);
                userManager.broadcast("[" + username + "]: " + message, this);
                writer.println("[You]: " + message); // gui lai cho chinh nguoi gui de ho biet tin nhan da duoc gui thanh cong
            }

        } catch (IOException e) {
            System.out.println("[Server] " + username + " lost connection.");
        } finally {
            // du co loi hay khong van phai don dep, neu khong socket se bi treo
            disconnect();
        }
    }

    public void sendMessage(String message) {
        if (writer != null) {
            writer.println(message);
        }
    }

    private void disconnect() {
        try {
            userManager.removeClient(this);
            userManager.broadcastAll("[System] " + username + " left the chat.");
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            System.out.println("[Server] Error closing connection: " + e.getMessage());
        }
    }

    public String getUsername() {
        return username;
    }
}
