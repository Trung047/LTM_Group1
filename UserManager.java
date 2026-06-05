import java.util.ArrayList;

// class nay quan ly danh sach tat ca client dang ket noi va xu ly viec gui tin nhan
public class UserManager {

    private ArrayList<ClientHandler> clientList = new ArrayList<>();

    public synchronized void addClient(ClientHandler client) {
        clientList.add(client);
    }

    public synchronized void removeClient(ClientHandler client) {
        clientList.remove(client);
    }

    public synchronized void broadcast(String message, ClientHandler sender) {
        for (ClientHandler client : clientList) {
            if (client != sender) { // khong gui lai cho chinh nguoi da gui
                client.sendMessage(message);
            }
        }
    }

    // dung cho thong bao he thong nhu join/leave, can gui cho tat ca ke ca nguoi gui
    public synchronized void broadcastAll(String message) {
        for (ClientHandler client : clientList) {
            client.sendMessage(message);
        }
    }

    public synchronized int getClientCount() {
        return clientList.size();
    }
}
