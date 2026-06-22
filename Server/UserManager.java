package Server;

import Logging.SystemLogger;
import model.Protocol;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class UserManager {

    // map: username → handler
    private final ConcurrentHashMap<String, ClientHandler> clients    = new ConcurrentHashMap<>();
    // map: username → epoch millis lúc đăng nhập
    private final ConcurrentHashMap<String, Long>          joinTimes  = new ConcurrentHashMap<>();

    /**
     * Dang ky client moi.
     * Thu tu DUNG de tranh race condition:
     *   1. Them vao map (co joinTime)
     *   2. Gui USER_LIST day du cho chinh client moi
     *   3. Broadcast USER_JOIN cho NGUOI KHAC
     *   4. Broadcast USER_LIST moi cho TAT CA (dong bo sidebar)
     */
    public synchronized void register(String username, ClientHandler handler) {
        clients.put(username, handler);
        joinTimes.put(username, System.currentTimeMillis());
        SystemLogger.info("User dang nhap: " + username + " | Online: " + clients.size());

        // Buoc 2: gui danh sach day du (CO chinh minh) cho client moi
        handler.send(Protocol.build(Protocol.USER_LIST, buildUserList()));

        // Buoc 3: thong bao USER_JOIN cho nguoi khac
        long epoch = joinTimes.get(username);
        broadcastExcept(
            Protocol.build(Protocol.USER_JOIN, username + "|" + epoch),
            username
        );

        // Buoc 4: broadcast USER_LIST moi cho tat ca de dong bo
        broadcastAll(Protocol.build(Protocol.USER_LIST, buildUserList()));
    }

    /**
     * Huy dang ky khi ngat ket noi.
     * USER_LEFT kèm epoch de client tinh "X phut truoc".
     */
    public synchronized void unregister(String username) {
        if (username == null || !clients.containsKey(username)) return;
        clients.remove(username);
        joinTimes.remove(username);
        SystemLogger.info("User ngat ket noi: " + username + " | Con: " + clients.size());

        long leftAt = System.currentTimeMillis();
        broadcastAll(Protocol.build(Protocol.USER_LEFT, username + "|" + leftAt));
        broadcastAll(Protocol.build(Protocol.USER_LIST, buildUserList()));
    }

    public synchronized boolean isOnline(String username) {
        return clients.containsKey(username);
    }

    public synchronized void broadcastAll(String frame) {
        for (ClientHandler h : clients.values()) h.send(frame);
    }

    public synchronized void broadcastExcept(String frame, String excludeUsername) {
        for (Map.Entry<String, ClientHandler> e : clients.entrySet()) {
            if (!e.getKey().equals(excludeUsername)) e.getValue().send(frame);
        }
    }

    public synchronized void broadcastGroupMessage(String sender, String content) {
        broadcastAll(Protocol.build(Protocol.MSG_GROUP, sender, content));
    }

    public synchronized void sendPrivateMessage(String from, String to, String content) {
        String frame  = Protocol.build(Protocol.MSG_PRIVATE, from, to, content);
        ClientHandler toH   = clients.get(to);
        ClientHandler fromH = clients.get(from);
        if (toH   != null) toH.send(frame);
        if (fromH != null && fromH != toH) fromH.send(frame);
    }

    public synchronized void broadcastTyping(String username, String status) {
        broadcastExcept(Protocol.build(Protocol.TYPING, username, status), username);
    }

    public synchronized List<String> getOnlineUsers() {
        return new ArrayList<>(clients.keySet());
    }

    public synchronized int getCount() { return clients.size(); }

    /**
     * Build danh sach: "Alice|1718000000000,Bob|1718000001000"
     * Moi entry = username|epochMillis
     */
    private String buildUserList() {
        if (clients.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (String u : clients.keySet()) {
            if (sb.length() > 0) sb.append(",");
            long epoch = joinTimes.getOrDefault(u, System.currentTimeMillis());
            sb.append(u).append("|").append(epoch);
        }
        return sb.toString();
    }
}
