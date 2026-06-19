import java.io.*;
import java.net.*;
import java.util.Scanner;
import model.Message;
import model.Protocol;

public class Client {

    private static final String HOST = "localhost";
    private static final int PORT = 8080;

    public static void main(String[] args) {
        try (Socket socket = new Socket(HOST, PORT)) {

            System.out.println("Connected to server!");

            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
            Scanner scanner = new Scanner(System.in);

            // can 1 thread rieng de nhan tin nhan, neu khong viec doc tu server se block viec go tin nhan
            Thread receiveThread = new Thread(() -> {
                try {
                    String message;
                    while ((message = reader.readLine()) != null) {
                        System.out.println(message);
                    }
                } catch (IOException e) {
                    System.out.println("Disconnected from server.");
                }
            });
            receiveThread.start();

            while (scanner.hasNextLine()) {
                writer.println(scanner.nextLine());
            }

        } catch (IOException e) {
            System.out.println("Cannot connect to server: " + e.getMessage());
        }
    }
}
