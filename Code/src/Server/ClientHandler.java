package Server;

import Database.DatabaseConnection;
import Database.MessageRepository;
import Database.UserRepository;
import Logging.SystemLogger;
import model.Message;
import model.Protocol;

import java.io.*;
import java.net.Socket;
import java.util.Base64;
import java.util.List;

/**
 * Mỗi client kết nối → một ClientHandler chạy trên thread riêng.
 *
 * Luồng đăng nhập:
 *   1. Client gửi LOGIN:username:password (hoặc REGISTER:username:password để tạo tài khoản mới)
 *   2. Server kiểm tra tên trống / tên đang online
 *   3. UserRepository.authenticateUser() → xác thực DB (bắt buộc, không còn demo mode)
 *   4. AUTH_OK:username → register vào UserManager
 *      AUTH_FAIL:lý do  → từ chối
 *
 * Sau khi đăng nhập:
 *   GROUP:content               → lưu DB theo đúng phòng hiện tại, broadcast CHỈ cho user trong phòng đó
 *   PRIVATE:toUser:content      → lưu DB, gửi đúng người (from + to)
 *   CREATE_GROUP:groupName      → tạo phòng mới, broadcast GROUP_CREATED + ROOM_LIST cho tất cả
 *   JOIN_ROOM:roomName          → chuyển phòng hiện tại của client trên server, gửi lại lịch sử phòng đó
 *   REMOVE_MEMBER:room:user     → (chỉ creator) xóa thành viên khỏi phòng
 *   ADD_MEMBER:room:user        → (chỉ creator) thêm thành viên vào phòng
 *   GET_ROOM_MEMBERS:room       → trả về danh sách thành viên phòng
 *   SEARCH_MSG:room:keyword     → tìm tin nhắn trong phòng hiện tại
 *   FILE_GROUP:tenFile:base64   → gửi file/ảnh vào phòng hiện tại
 *   FILE_PRIVATE:to:tenFile:b64 → gửi file/ảnh riêng
 *   RECALL:id                   → thu hồi 1 tin nhắn của chính mình
 *   TYPING:1|0                  → broadcast typing (trừ sender)
 *   PING                        → trả PONG ngay lập tức (đo RTT thật)
 *   LOGOUT                      → cleanup + đóng socket
 */
public class ClientHandler implements Runnable {

    /** Gioi han kich thuoc file (Base64) de tranh OOM tren giao thuc dong-van-ban. ~7MB Base64 ~ 5MB file goc. */
    private static final int MAX_FILE_BASE64_LENGTH = 7 * 1024 * 1024;

    private static final int SEARCH_LIMIT  = 100;
    private static final int HISTORY_LIMIT = 50;

    private final Socket            socket;
    private final UserManager       userManager;
    private final UserRepository    userRepo = new UserRepository();
    private final MessageRepository msgRepo  = new MessageRepository();

    private PrintWriter out;
    private String      username = null;   // null = chưa đăng nhập

    public ClientHandler(Socket socket, UserManager userManager) {
        this.socket      = socket;
        this.userManager = userManager;
    }

    // ── Main loop ─────────────────────────────────────────────────────────────

    @Override
    public void run() {
        try (
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), "UTF-8"));
            PrintWriter writer = new PrintWriter(
                    new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true)
        ) {
            this.out = writer;

            String line;
            while ((line = in.readLine()) != null) {
                handleFrame(line.trim());
            }

        } catch (IOException e) {
            SystemLogger.warning("Mất kết nối: " +
                (username != null ? username : socket.getInetAddress().getHostAddress()));
        } finally {
            cleanup();
        }
    }

    // ── Frame dispatcher ──────────────────────────────────────────────────────

    private void handleFrame(String frame) {
        if (frame.isEmpty()) return;

        // Chưa login → chỉ chấp nhận LOGIN hoặc REGISTER
        if (username == null) {
            if (frame.startsWith(Protocol.LOGIN + ":")) {
                handleLogin(frame);
            } else if (frame.startsWith(Protocol.REGISTER + ":")) {
                handleRegister(frame);
            } else {
                send(Protocol.build(Protocol.AUTH_FAIL, "Chưa đăng nhập"));
            }
            return;
        }

        // Đã login
        if (frame.equals(Protocol.PING)) {
            send(Protocol.PONG);

        } else if (frame.equals(Protocol.LOGOUT)) {
            cleanup();

        } else if (frame.startsWith(Protocol.GROUP + ":")) {
            handleGroup(frame);

        } else if (frame.startsWith(Protocol.PRIVATE + ":")) {
            handlePrivate(frame);

        } else if (frame.startsWith(Protocol.CREATE_GROUP + ":")) {
            handleCreateGroup(frame);

        } else if (frame.startsWith(Protocol.JOIN_ROOM + ":")) {
            handleJoinRoom(frame);

        } else if (frame.equals(Protocol.ROOM_LIST)) {
            sendRoomList();

        } else if (frame.startsWith(Protocol.REMOVE_MEMBER + ":")) {
            handleRemoveMember(frame);

        } else if (frame.startsWith(Protocol.ADD_MEMBER + ":")) {
            handleAddMember(frame);

        } else if (frame.startsWith(Protocol.GET_ROOM_MEMBERS + ":")) {
            handleGetRoomMembers(frame);

        } else if (frame.startsWith(Protocol.SEARCH_MSG + ":")) {
            handleSearch(frame);

        } else if (frame.startsWith(Protocol.FILE_GROUP + ":")) {
            handleFileGroup(frame);

        } else if (frame.startsWith(Protocol.FILE_PRIVATE + ":")) {
            handleFilePrivate(frame);

        } else if (frame.startsWith(Protocol.RECALL + ":")) {
            handleRecall(frame);

        } else if (frame.startsWith(Protocol.TYPING + ":")) {
            String[] p = Protocol.parse(frame, 2);
            if (p.length == 2) {
                userManager.broadcastTyping(username, p[1]);
            }
        }
        // Frame không rõ → bỏ qua (không crash)
    }

    // ── Luồng đăng nhập ───────────────────────────────────────────────────────

    private void handleLogin(String frame) {
        String[] p = Protocol.parse(frame, 3);
        if (p.length < 3) {
            send(Protocol.build(Protocol.AUTH_FAIL, "Sai định dạng LOGIN"));
            return;
        }

        String uname = p[1].trim();
        String pass  = p[2].trim();

        if (uname.isEmpty()) {
            send(Protocol.build(Protocol.AUTH_FAIL, "Tên người dùng không được trống"));
            return;
        }

        if (uname.length() > 32) {
            send(Protocol.build(Protocol.AUTH_FAIL, "Tên quá dài (tối đa 32 ký tự)"));
            return;
        }

        if (userManager.isOnline(uname)) {
            send(Protocol.build(Protocol.AUTH_FAIL, "Tên '" + uname + "' đang được sử dụng"));
            return;
        }

        if (!DatabaseConnection.testConnection()) {
            SystemLogger.error("[LOGIN] Database không khả dụng — từ chối đăng nhập: " + uname);
            send(Protocol.build(Protocol.AUTH_FAIL, "Không thể kết nối Database. Vui lòng thử lại sau."));
            return;
        }

        if (!userRepo.authenticateUser(uname, pass)) {
            send(Protocol.build(Protocol.AUTH_FAIL, "Sai tên đăng nhập hoặc mật khẩu"));
            return;
        }

        this.username = uname;
        send(Protocol.build(Protocol.AUTH_OK, username));
        userManager.register(username, this);   // gan phong mac dinh (DEFAULT_ROOM) ngay trong register()
        sendChatHistory(userManager.getCurrentRoom(username));
        sendRoomList();
        SystemLogger.info("LOGIN OK: " + username
            + " từ " + socket.getInetAddress().getHostAddress()
            + ":" + socket.getPort());
    }

    /**
     * Đăng ký tài khoản mới (tính năng 9). Không yêu cầu đã đăng nhập.
     * Sau khi REGISTER_OK, client vẫn cần gửi LOGIN riêng để vào phòng chat
     * (registration không tự động đăng nhập, giữ 2 luồng tách biệt và đơn giản).
     */
    private void handleRegister(String frame) {
        String[] p = Protocol.parse(frame, 3);
        if (p.length < 3) {
            send(Protocol.build(Protocol.REGISTER_FAIL, "Sai định dạng REGISTER"));
            return;
        }

        String uname = p[1].trim();
        String pass  = p[2].trim();

        if (uname.isEmpty() || pass.isEmpty()) {
            send(Protocol.build(Protocol.REGISTER_FAIL, "Tên đăng nhập và mật khẩu không được trống"));
            return;
        }
        if (uname.length() > 32) {
            send(Protocol.build(Protocol.REGISTER_FAIL, "Tên quá dài (tối đa 32 ký tự)"));
            return;
        }
        if (!DatabaseConnection.testConnection()) {
            send(Protocol.build(Protocol.REGISTER_FAIL, "Không thể kết nối Database. Vui lòng thử lại sau."));
            return;
        }
        if (userRepo.userExists(uname)) {
            send(Protocol.build(Protocol.REGISTER_FAIL, "Tên đăng nhập '" + uname + "' đã tồn tại"));
            return;
        }
        if (userRepo.registerUser(uname, pass)) {
            send(Protocol.build(Protocol.REGISTER_OK, uname));
            SystemLogger.info("[REGISTER] Tài khoản mới: " + uname);
        } else {
            send(Protocol.build(Protocol.REGISTER_FAIL, "Đăng ký thất bại, vui lòng thử lại"));
        }
    }

    /**
     * Gui lai lich su chat nhom gan nhat (toi da 50 tin) cua MOT phong cu the,
     * dung khi: vua dang nhap (phong mac dinh) hoac vua chuyen phong (JOIN_ROOM).
     * Bao gom ca tin nhan van ban va file/anh; tin da thu hoi van gui frame
     * MSG_GROUP nhung noi dung se la nhan da thu hoi (client hien thi phu hop).
     */
    private void sendChatHistory(String room) {
        List<Message> history = msgRepo.getRecentGroupMessages(room, HISTORY_LIMIT);
        for (Message m : history) {
            sendHistoryMessage(m);
        }
    }

    private void sendHistoryMessage(Message m) {
        if (m.isRecalled()) {
            send(Protocol.build(Protocol.MSG_RECALLED, String.valueOf(m.getId()), m.getRoom()));
            return;
        }
        if (m.isFile()) {
            send(Protocol.build(Protocol.MSG_FILE_GROUP,
                    String.valueOf(m.getId()), m.getRoom(), m.getSender(),
                    String.valueOf(m.getEpochMillis()), m.getFileName(), m.getContent()));
        } else {
            send(Protocol.build(Protocol.MSG_GROUP,
                    String.valueOf(m.getId()), m.getRoom(), m.getSender(),
                    String.valueOf(m.getEpochMillis()), m.getContent()));
        }
    }

    // ── Tin nhắn nhóm ─────────────────────────────────────────────────────────

    private void handleGroup(String frame) {
        String[] p = Protocol.parse(frame, 2);
        if (p.length < 2 || p[1].trim().isEmpty()) return;
        String content = p[1].trim();
        String room    = userManager.getCurrentRoom(username);

        long id = msgRepo.saveGroupMessage(username, room, content);
        if (id < 0) {
            SystemLogger.warning("[GROUP] Không lưu được vào DB: " + username);
        }
        long epoch = System.currentTimeMillis();
        userManager.broadcastGroupMessage(id, username, room, epoch, content);
        SystemLogger.info("[GROUP][" + room + "] " + username + ": " + content);
    }

    // ── Tin nhắn riêng ────────────────────────────────────────────────────────

    private void handlePrivate(String frame) {
        String[] p = Protocol.parse(frame, 3);
        if (p.length < 3 || p[2].trim().isEmpty()) return;

        String to      = p[1].trim();
        String content = p[2].trim();

        if (to.equals(username)) {
            send(Protocol.build(Protocol.ERROR, "Không thể nhắn riêng cho chính mình"));
            return;
        }

        if (!userManager.isOnline(to)) {
            send(Protocol.build(Protocol.ERROR, "'" + to + "' không online hoặc không tồn tại"));
            return;
        }

        long id = msgRepo.savePrivateMessage(username, to, content);
        if (id < 0) {
            SystemLogger.warning("[PRIVATE] Không lưu được vào DB: " + username + "→" + to);
        }
        long epoch = System.currentTimeMillis();
        userManager.sendPrivateMessage(id, username, to, epoch, content);
        SystemLogger.info("[PRIVATE] " + username + " → " + to + ": " + content);
    }

    // ── Tạo nhóm ──────────────────────────────────────────────────────────────

    private void handleCreateGroup(String frame) {
        String[] p = Protocol.parse(frame, 2);
        if (p.length < 2 || p[1].trim().isEmpty()) {
            send(Protocol.build(Protocol.ERROR, "Tên nhóm không được trống"));
            return;
        }
        String groupName = p[1].trim();

        userManager.registerRoom(groupName, username);

        userManager.broadcastAll(
            Protocol.build(Protocol.GROUP_CREATED, groupName, username)
        );
        userManager.broadcastAll(
            Protocol.build(Protocol.ROOM_LIST, String.join(",", userManager.getRooms()))
        );
        SystemLogger.info("[CREATE_GROUP] " + username + " tạo nhóm: " + groupName);
    }

    // ── Phòng chat ───────────────────────────────────────────────────────────

    private void handleJoinRoom(String frame) {
        String[] p = Protocol.parse(frame, 2);
        if (p.length < 2 || p[1].trim().isEmpty()) {
            send(Protocol.build(Protocol.ERROR, "Tên phòng không được trống"));
            return;
        }
        String roomName = p[1].trim();
        userManager.joinRoom(username, roomName);
        sendChatHistory(roomName);
        SystemLogger.info("[JOIN_ROOM] " + username + " → " + roomName);
    }

    /** Gui danh sach phong hien co cho client dang yeu cau. */
    private void sendRoomList() {
        send(Protocol.build(Protocol.ROOM_LIST, String.join(",", userManager.getRooms())));
    }

    // ── Phase D: Quan ly thanh vien phong (tinh nang 1 & 2) ─────────────────────

    /** REMOVE_MEMBER:room:username — chi creator cua phong moi duoc xoa thanh vien. */
    private void handleRemoveMember(String frame) {
        String[] p = Protocol.parse(frame, 3);
        if (p.length < 3 || p[1].trim().isEmpty() || p[2].trim().isEmpty()) {
            send(Protocol.build(Protocol.ERROR, "Sai định dạng REMOVE_MEMBER"));
            return;
        }
        String room   = p[1].trim();
        String target = p[2].trim();

        String creator = userManager.getRoomCreator(room);
        if (creator == null || !creator.equals(username)) {
            send(Protocol.build(Protocol.ERROR, "Chỉ người tạo phòng mới được xóa thành viên"));
            return;
        }
        if (target.equals(creator)) {
            send(Protocol.build(Protocol.ERROR, "Không thể xóa chính người tạo phòng"));
            return;
        }
        userManager.removeMemberFromRoom(room, target);
        SystemLogger.info("[REMOVE_MEMBER] " + username + " đã xóa " + target + " khỏi " + room);
    }

    /** ADD_MEMBER:room:username — chi creator cua phong moi duoc them thanh vien. */
    private void handleAddMember(String frame) {
        String[] p = Protocol.parse(frame, 3);
        if (p.length < 3 || p[1].trim().isEmpty() || p[2].trim().isEmpty()) {
            send(Protocol.build(Protocol.ERROR, "Sai định dạng ADD_MEMBER"));
            return;
        }
        String room   = p[1].trim();
        String target = p[2].trim();

        String creator = userManager.getRoomCreator(room);
        if (creator == null || !creator.equals(username)) {
            send(Protocol.build(Protocol.ERROR, "Chỉ người tạo phòng mới được thêm thành viên"));
            return;
        }
        if (!userRepo.userExists(target)) {
            send(Protocol.build(Protocol.ERROR, "Tài khoản '" + target + "' không tồn tại"));
            return;
        }
        userManager.addMemberToRoom(room, target);
        SystemLogger.info("[ADD_MEMBER] " + username + " đã thêm " + target + " vào " + room);
    }

    /** GET_ROOM_MEMBERS:room — tra ve danh sach thanh vien cua 1 phong. */
    private void handleGetRoomMembers(String frame) {
        String[] p = Protocol.parse(frame, 2);
        if (p.length < 2 || p[1].trim().isEmpty()) return;
        String room = p[1].trim();
        List<String> members = userManager.getRoomMembersList(room);
        send(Protocol.build(Protocol.ROOM_MEMBERS, room, String.join(",", members)));
    }

    // ── Phase D: Tim kiem (tinh nang 6) ──────────────────────────────────────────

    /** SEARCH_MSG:room:keyword — tim tin nhan van ban trong 1 phong cu the. */
    private void handleSearch(String frame) {
        String[] p = Protocol.parse(frame, 3);
        if (p.length < 3 || p[1].trim().isEmpty() || p[2].trim().isEmpty()) {
            send(Protocol.build(Protocol.SEARCH_DONE, "0"));
            return;
        }
        String room    = p[1].trim();
        String keyword = p[2].trim();

        List<Message> results = msgRepo.searchMessagesInRoom(room, keyword, SEARCH_LIMIT);
        for (Message m : results) {
            send(Protocol.build(Protocol.SEARCH_RESULT,
                    String.valueOf(m.getId()), m.getRoom(), m.getSender(),
                    String.valueOf(m.getEpochMillis()), m.getContent()));
        }
        send(Protocol.build(Protocol.SEARCH_DONE, String.valueOf(results.size())));
        SystemLogger.info("[SEARCH] " + username + " tìm '" + keyword + "' trong " + room
                + " → " + results.size() + " kết quả");
    }

    // ── Phase D: Gui file/anh (tinh nang 7) ──────────────────────────────────────

    private void handleFileGroup(String frame) {
        String[] p = Protocol.parse(frame, 3);
        if (p.length < 3 || p[1].trim().isEmpty() || p[2].trim().isEmpty()) {
            send(Protocol.build(Protocol.ERROR, "Sai định dạng FILE_GROUP"));
            return;
        }
        String fileName = p[1].trim();
        String base64   = p[2].trim();
        if (!isValidBase64File(base64)) return;

        String room = userManager.getCurrentRoom(username);
        long id = msgRepo.saveFileMessage(username, "GROUP", room, fileName, base64);
        if (id < 0) {
            send(Protocol.build(Protocol.ERROR, "Không thể lưu file — vui lòng thử lại"));
            return;
        }
        long epoch = System.currentTimeMillis();
        userManager.broadcastFileGroupMessage(id, username, room, epoch, fileName, base64);
        SystemLogger.info("[FILE_GROUP][" + room + "] " + username + " gửi file: " + fileName);
    }

    private void handleFilePrivate(String frame) {
        String[] p = Protocol.parse(frame, 4);
        if (p.length < 4 || p[1].trim().isEmpty() || p[2].trim().isEmpty() || p[3].trim().isEmpty()) {
            send(Protocol.build(Protocol.ERROR, "Sai định dạng FILE_PRIVATE"));
            return;
        }
        String to       = p[1].trim();
        String fileName = p[2].trim();
        String base64   = p[3].trim();
        if (!isValidBase64File(base64)) return;

        if (to.equals(username)) {
            send(Protocol.build(Protocol.ERROR, "Không thể gửi file cho chính mình"));
            return;
        }
        if (!userManager.isOnline(to)) {
            send(Protocol.build(Protocol.ERROR, "'" + to + "' không online hoặc không tồn tại"));
            return;
        }

        long id = msgRepo.saveFileMessage(username, to, null, fileName, base64);
        if (id < 0) {
            send(Protocol.build(Protocol.ERROR, "Không thể lưu file — vui lòng thử lại"));
            return;
        }
        long epoch = System.currentTimeMillis();
        userManager.sendFilePrivateMessage(id, username, to, epoch, fileName, base64);
        SystemLogger.info("[FILE_PRIVATE] " + username + " → " + to + " gửi file: " + fileName);
    }

    private boolean isValidBase64File(String base64) {
        if (base64.length() > MAX_FILE_BASE64_LENGTH) {
            send(Protocol.build(Protocol.ERROR, "File quá lớn (tối đa khoảng 5MB)"));
            return false;
        }
        try {
            Base64.getDecoder().decode(base64);
            return true;
        } catch (IllegalArgumentException e) {
            send(Protocol.build(Protocol.ERROR, "Dữ liệu file không hợp lệ"));
            return false;
        }
    }

    // ── Phase D: Thu hoi tin nhan (tinh nang 8) ──────────────────────────────────

    /** RECALL:id — thu hoi tin nhan cua chinh minh (server kiem tra lai quyen so huu voi DB). */
    private void handleRecall(String frame) {
        String[] p = Protocol.parse(frame, 2);
        if (p.length < 2 || p[1].trim().isEmpty()) return;

        long id;
        try {
            id = Long.parseLong(p[1].trim());
        } catch (NumberFormatException e) {
            send(Protocol.build(Protocol.ERROR, "id tin nhắn không hợp lệ"));
            return;
        }

        Message before = msgRepo.getMessageById(id);
        if (before == null) {
            send(Protocol.build(Protocol.ERROR, "Không tìm thấy tin nhắn"));
            return;
        }

        boolean ok = msgRepo.recallMessage(id, username);
        if (!ok) {
            send(Protocol.build(Protocol.ERROR, "Không thể thu hồi tin nhắn (không phải tin của bạn?)"));
            return;
        }

        if ("GROUP".equals(before.getReceiver())) {
            userManager.broadcastRecall(id, before.getRoom());
        } else {
            userManager.sendPrivateRecall(id, before.getSender(), before.getReceiver());
        }
        SystemLogger.info("[RECALL] " + username + " đã thu hồi tin nhắn id=" + id);
    }

    // ── Tiện ích ──────────────────────────────────────────────────────────────

    public void send(String frame) {
        if (out != null) out.println(frame);
    }

    private void cleanup() {
        if (username != null) {
            String leavingUser = username;
            username = null;
            userManager.unregister(leavingUser);
        }
        try {
            if (!socket.isClosed()) socket.close();
        } catch (IOException ignored) {}
    }
}
