package Server;

import Database.MessageRepository;
import Database.UserRepository;
import Logging.SystemLogger;
import model.Protocol;

import java.io.*;
import java.net.Socket;

/**
 * Mỗi client kết nối → một ClientHandler chạy trên thread riêng.
 *
 * Luồng đăng nhập:
 *   1. Client gửi LOGIN:username:password
 *   2. Server kiểm tra tên trống / tên đang online
 *   3. UserRepository.authenticateUser() → xác thực DB (hoặc demo mode)
 *   4. AUTH_OK:username → register vào UserManager
 *      AUTH_FAIL:lý do  → từ chối
 *
 * Sau khi đăng nhập:
 *   GROUP:content             → lưu DB, broadcast tất cả
 *   PRIVATE:toUser:content    → lưu DB, gửi đúng người (from + to)
 *   CREATE_GROUP:groupName    → broadcast GROUP_CREATED cho tất cả
 *   TYPING:1|0                → broadcast typing (trừ sender)
 *   PING                      → trả PONG ngay lập tức (đo RTT thật)
 *   LOGOUT                    → cleanup + đóng socket
 */
public class ClientHandler implements Runnable {

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

        // Chưa login → chỉ chấp nhận LOGIN
        if (username == null) {
            if (frame.startsWith(Protocol.LOGIN + ":")) {
                handleLogin(frame);
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

        if (!userRepo.authenticateUser(uname, pass)) {
            send(Protocol.build(Protocol.AUTH_FAIL, "Sai tên đăng nhập hoặc mật khẩu"));
            return;
        }

        this.username = uname;
        send(Protocol.build(Protocol.AUTH_OK, username));
        userManager.register(username, this);
        SystemLogger.info("LOGIN OK: " + username
            + " từ " + socket.getInetAddress().getHostAddress()
            + ":" + socket.getPort());
    }

    // ── Tin nhắn nhóm ─────────────────────────────────────────────────────────

    private void handleGroup(String frame) {
        String[] p = Protocol.parse(frame, 2);
        if (p.length < 2 || p[1].trim().isEmpty()) return;
        String content = p[1].trim();

        boolean saved = msgRepo.saveMessage(username, "GROUP", content);
        if (!saved) {
            SystemLogger.warning("[GROUP] Không lưu được vào DB: " + username);
        }

        userManager.broadcastGroupMessage(username, content);
        SystemLogger.info("[GROUP] " + username + ": " + content);
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

        boolean saved = msgRepo.saveMessage(username, to, content);
        if (!saved) {
            SystemLogger.warning("[PRIVATE] Không lưu được vào DB: " + username + "→" + to);
        }

        userManager.sendPrivateMessage(username, to, content);
        SystemLogger.info("[PRIVATE] " + username + " → " + to + ": " + content);
    }

    // ── Tạo nhóm ──────────────────────────────────────────────────────────────

    /**
     * CREATE_GROUP:groupName
     * → broadcast GROUP_CREATED:groupName:creator tới tất cả client
     */
    private void handleCreateGroup(String frame) {
        String[] p = Protocol.parse(frame, 2);
        if (p.length < 2 || p[1].trim().isEmpty()) {
            send(Protocol.build(Protocol.ERROR, "Tên nhóm không được trống"));
            return;
        }
        String groupName = p[1].trim();

        // Broadcast cho tất cả biết có nhóm mới được tạo
        userManager.broadcastAll(
            Protocol.build(Protocol.GROUP_CREATED, groupName, username)
        );
        SystemLogger.info("[CREATE_GROUP] " + username + " tạo nhóm: " + groupName);
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
