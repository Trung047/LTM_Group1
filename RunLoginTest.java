import Client.Client;

public class RunLoginTest {
    public static void main(String[] args) throws Exception {
        String host = "127.0.0.1";
        int port = 8080;
        String username = args.length > 0 ? args[0] : "testuser";
        String password = args.length > 1 ? args[1] : "";

        Client c = new Client();
        c.setListener(frame -> {
            System.out.println("RECV: " + frame);
        });

        System.out.println("Connecting to " + host + ":" + port + "...");
        if (!c.connect(host, port)) {
            System.out.println("Connect failed");
            return;
        }

        System.out.println("Sending LOGIN: " + username);
        c.sendLogin(username, password);

        // Wait for server response
        Thread.sleep(3000);

        System.out.println("Disconnecting");
        c.disconnect();
    }
}
