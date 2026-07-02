package Server;

import Database.RoomRepository;
import Logging.SystemLogger;
import model.Protocol;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class UserManager {

    // map: username → handler
    private final ConcurrentHashMap<String, ClientHandler> clients    = new ConcurrentHashMap<>();
    // map: username → epoch millis lúc đăng nhập
    private final ConcurrentHashMap<String, Long>          joinTimes  = new ConcurrentHashMap<>();
    // danh sach phong hien co (in-memory, nap tu DB luc khoi dong). "General" la phong mac dinh.
    private final Set<String> rooms = ConcurrentHashMap.newKeySet();
    // map: username → ten phong hien tai
    private final ConcurrentHashMap<String, String> currentRoom = new ConcurrentHashMap<>();

    private final RoomRepository roomRepo = new RoomRepository();

    public UserManager() {
        // Nap danh sach phong tu DB (neu DB khong khả dung, dung mac dinh Protocol.DEFAULT_ROOM)
        List<String> dbRooms = roomRepo.getAllRooms();
        if (dbRooms.isEmpty()) {
            rooms.add(Protocol.DEFAULT_ROOM);
            roomRepo.saveRoom(Protocol.DEFAULT_ROOM, null);
        } else {
            rooms.addAll(dbRooms);
        }
    }

    /**
     * Dang ky client moi.
     * Thu tu DUNG de tranh race condition:
     *   1. Them vao map (co joinTime)
     *   2. Gan phong mac dinh (DEFAULT_ROOM) cho client moi
     *   3. Gui USER_LIST day du cho chinh client moi
     *   4. Broadcast USER_JOIN cho NGUOI KHAC
     *   5. Broadcast USER_LIST moi cho TAT CA (dong bo sidebar)
     */
    public synchronized void register(String username, ClientHandler handler) {
        clients.put(username, handler);
        joinTimes.put(username, System.currentTimeMillis());
        SystemLogger.info("User dang nhap: " + username + " | Online: " + clients.size());

        // Buoc 2: gan phong mac dinh ngay tu dau de loc broadcast theo phong hoat dong dung
        joinRoom(username, Protocol.DEFAULT_ROOM);

        // Buoc 3: gui danh sach day du (CO chinh minh) cho client moi
        handler.send(Protocol.build(Protocol.USER_LIST, buildUserList()));

        // Buoc 4: thong bao USER_JOIN cho nguoi khac
        long epoch = joinTimes.get(username);
        broadcastExcept(
            Protocol.build(Protocol.USER_JOIN, username + "|" + epoch),
            username
        );

        // Buoc 5: broadcast USER_LIST moi cho tat ca de dong bo
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
        currentRoom.remove(username);
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

    /** Gui 1 frame cho tat ca client dang co currentRoom = room (khong gui cho nguoi ngoai phong). */
    private synchronized void broadcastToRoom(String room, String frame) {
        for (Map.Entry<String, String> e : currentRoom.entrySet()) {
            if (e.getValue().equals(room)) {
                ClientHandler h = clients.get(e.getKey());
                if (h != null) h.send(frame);
            }
        }
    }

    /**
     * Broadcast tin nhan nhom (van ban) CHI cho nhung user dang o dung phong "room".
     * Phase D: kem id (de client co the RECALL) va epochMillis (de hien ngay+gio day du).
     */
    public synchronized void broadcastGroupMessage(long id, String sender, String room, long epochMillis, String content) {
        String frame = Protocol.build(Protocol.MSG_GROUP,
                String.valueOf(id), room, sender, String.valueOf(epochMillis), content);
        broadcastToRoom(room, frame);
    }

    /** Broadcast tin nhan file/anh trong nhom, chi cho user dang o dung phong. */
    public synchronized void broadcastFileGroupMessage(long id, String sender, String room, long epochMillis,
                                                         String fileName, String base64) {
        String frame = Protocol.build(Protocol.MSG_FILE_GROUP,
                String.valueOf(id), room, sender, String.valueOf(epochMillis), fileName, base64);
        broadcastToRoom(room, frame);
    }

    /** Bao cho ca phong biet 1 tin nhan nhom vua bi thu hoi. */
    public synchronized void broadcastRecall(long id, String room) {
        broadcastToRoom(room, Protocol.build(Protocol.MSG_RECALLED, String.valueOf(id), room));
    }

    public synchronized void sendPrivateMessage(long id, String from, String to, long epochMillis, String content) {
        String frame  = Protocol.build(Protocol.MSG_PRIVATE,
                String.valueOf(id), from, to, String.valueOf(epochMillis), content);
        ClientHandler toH   = clients.get(to);
        ClientHandler fromH = clients.get(from);
        if (toH   != null) toH.send(frame);
        if (fromH != null && fromH != toH) fromH.send(frame);
    }

    public synchronized void sendFilePrivateMessage(long id, String from, String to, long epochMillis,
                                                      String fileName, String base64) {
        String frame  = Protocol.build(Protocol.MSG_FILE_PRIVATE,
                String.valueOf(id), from, to, String.valueOf(epochMillis), fileName, base64);
        ClientHandler toH   = clients.get(to);
        ClientHandler fromH = clients.get(from);
        if (toH   != null) toH.send(frame);
        if (fromH != null && fromH != toH) fromH.send(frame);
    }

    /** Bao cho ca 2 phia (nguoi gui + nguoi nhan) biet 1 tin nhan rieng vua bi thu hoi. */
    public synchronized void sendPrivateRecall(long id, String from, String to) {
        String frame = Protocol.build(Protocol.MSG_RECALLED_PRIVATE, String.valueOf(id), from, to);
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

    // ── Phòng chat (DB-backed, đồng bộ với bộ nhớ) ──────────────────────────────

    /** Dang ky 1 phong moi vao danh sach + luu xuong DB (vd khi CREATE_GROUP). Bo qua neu da ton tai. */
    public synchronized void registerRoom(String roomName, String creator) {
        if (roomName == null || roomName.trim().isEmpty()) return;
        String name = roomName.trim();
        rooms.add(name);
        roomRepo.saveRoom(name, creator);
    }

    /** Ghi nhan phong hien tai cua 1 user (vd khi JOIN_ROOM). Tu dong dang ky phong neu chua co + luu thanh vien xuong DB. */
    public synchronized boolean joinRoom(String username, String roomName) {
        if (username == null || roomName == null || roomName.trim().isEmpty()) return false;
        String name = roomName.trim();
        rooms.add(name);
        currentRoom.put(username, name);
        roomRepo.saveRoom(name, null);
        roomRepo.addMember(name, username);
        return true;
    }

    /** Lay phong hien tai cua 1 user, mac dinh Protocol.DEFAULT_ROOM neu chua co. */
    public synchronized String getCurrentRoom(String username) {
        return currentRoom.getOrDefault(username, Protocol.DEFAULT_ROOM);
    }

    /** Lay danh sach toan bo phong hien co. */
    public synchronized List<String> getRooms() {
        return new ArrayList<>(rooms);
    }

    // ── Phase D: Quan ly thanh vien phong (tinh nang 1 & 2) ─────────────────────

    /** Lay ten nguoi tao (creator) cua 1 phong; null neu khong co (vd phong mac dinh). */
    public String getRoomCreator(String roomName) {
        return roomRepo.getRoomCreator(roomName);
    }

    /** Lay danh sach thanh vien (tung tham gia) cua 1 phong. */
    public List<String> getRoomMembersList(String roomName) {
        return roomRepo.getRoomMembers(roomName);
    }

    /**
     * Xoa 1 thanh vien khoi phong: xoa khoi DB; neu ho dang XEM dung phong do
     * thi tu dong chuyen ho ve DEFAULT_ROOM va gui rieng KICKED_FROM_ROOM.
     * Sau do broadcast MEMBER_REMOVED cho ca phong biet.
     */
    public synchronized void removeMemberFromRoom(String roomName, String username) {
        roomRepo.removeMember(roomName, username);

        ClientHandler kicked = clients.get(username);
        if (kicked != null && roomName.equals(currentRoom.get(username))) {
            currentRoom.put(username, Protocol.DEFAULT_ROOM);
            kicked.send(Protocol.build(Protocol.KICKED_FROM_ROOM, roomName));
        }
        broadcastToRoom(roomName, Protocol.build(Protocol.MEMBER_REMOVED, roomName, username));
        // Dam bao chinh nguoi bi xoa cung nhan duoc thong bao du ho dang o phong nao khac
        if (kicked != null) kicked.send(Protocol.build(Protocol.MEMBER_REMOVED, roomName, username));
    }

    /**
     * Them chu dong 1 thanh vien vao phong (khong can ho tu JOIN_ROOM).
     * Broadcast MEMBER_ADDED cho ca phong, va rieng cho nguoi vua duoc them
     * (kem ROOM_LIST moi) de UI cua ho cap nhat ngay ca khi dang o phong khac.
     */
    public synchronized void addMemberToRoom(String roomName, String username) {
        roomRepo.addMember(roomName, username);
        String frame = Protocol.build(Protocol.MEMBER_ADDED, roomName, username);
        broadcastToRoom(roomName, frame);

        ClientHandler h = clients.get(username);
        if (h != null) {
            h.send(frame);
            h.send(Protocol.build(Protocol.ROOM_LIST, String.join(",", getRooms())));
        }
    }

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
