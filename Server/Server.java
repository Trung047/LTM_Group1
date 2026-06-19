import java.io.*;
import java.net.*;
package Server;

public class Server {

    private static final int PORT = 8080;

    public static void main(String[] args) {
        UserManager userManager = new UserManager();

        System.out.println("Server started on port " + PORT);

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {

            System.out.println("Waiting for clients...");

            while (true) {
                // accept() se bi block o day cho den khi co client ket noi
                Socket clientSocket = serverSocket.accept();
                System.out.println("[Server] New client: " + clientSocket.getInetAddress().getHostAddress());

                ClientHandler handler = new ClientHandler(clientSocket, userManager);
                userManager.addClient(handler);

                // moi client chay tren 1 thread rieng, neu khong thi client nay se block client khac
                new Thread(handler).start();
            }

        } catch (IOException e) {
            System.out.println("[Server] Error: " + e.getMessage());
        }
    }
}
