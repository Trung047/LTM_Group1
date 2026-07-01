package Client;

import Logging.SystemLogger;
import model.Protocol;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

/**
 * Lop Client thuan - chiu trach nhiem ket noi mang.
 * UI (ChatFrame) dang ky MessageListener de nhan frame tu server.
 *
 * FIX RACE CONDITION:
 *   Khi server gui USER_LIST ngay sau AUTH_OK, listener that (MessageReceiver)
 *   co the chua duoc set vi ChatFrame chua khoi tao xong.
 *   Giai phap: buffer cac frame den khi listener that duoc gan vao.
 */
public class Client {

    public interface MessageListener {
        void onFrame(String frame);
        default void onDisconnected() {}
    }

    private Socket         socket;
    private PrintWriter    writer;
    private BufferedReader reader;
    private boolean        running  = false;
    private MessageListener listener;

    // Buffer cac frame khi chua co listener that
    private final List<String> pendingFrames = new ArrayList<>();
    // Co biet listener hien tai la listener "tam" (login) hay "that" (MessageReceiver)
    private boolean listenerIsReal = false;

    // ── Connect ──────────────────────────────────────────────────────────────

    public boolean connect(String host, int port) {
        try {
            socket = new Socket(host, port);
            socket.setTcpNoDelay(true);
            writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
            running = true;
            startReceiver();
            SystemLogger.info("Ket noi server " + host + ":" + port);
            return true;
        } catch (IOException e) {
            SystemLogger.error("Khong the ket noi: " + e.getMessage());
            return false;
        }
    }

    // ── Send helpers ─────────────────────────────────────────────────────────

    public void sendLogin(String username, String password) {
        send(Protocol.build(Protocol.LOGIN, username, password));
    }

    public void sendGroup(String content) {
        send(Protocol.build(Protocol.GROUP, content));
    }

    public void sendCreateGroup(String groupName) {
        send(Protocol.build(Protocol.CREATE_GROUP, groupName));
    }

    public void sendPrivate(String toUser, String content) {
        send(Protocol.build(Protocol.PRIVATE, toUser, content));
    }

    public void sendTyping(boolean isTyping) {
        send(Protocol.build(Protocol.TYPING, isTyping ? "1" : "0"));
    }

    public void sendPing() {
        send(Protocol.PING);
    }

    public void sendLogout() {
        send(Protocol.LOGOUT);
    }

    public void sendJoinRoom(String roomName) {
        send(Protocol.build(Protocol.JOIN_ROOM, roomName));
    }

    /**
     * Yeu cau server gui lai danh sach phong hien co.
     * Duoc goi tu ChatFrame.doCreateGroup() sau khi tao nhom moi.
     */
    public void requestRoomList() {
        send(Protocol.ROOM_LIST);
    }

    // ── Phase D: Quan ly thanh vien phong (tinh nang 1 & 2) ─────────────────────

    public void sendRemoveMember(String roomName, String username) {
        send(Protocol.build(Protocol.REMOVE_MEMBER, roomName, username));
    }

    public void sendAddMember(String roomName, String username) {
        send(Protocol.build(Protocol.ADD_MEMBER, roomName, username));
    }

    public void requestRoomMembers(String roomName) {
        send(Protocol.build(Protocol.GET_ROOM_MEMBERS, roomName));
    }

    public void requestRoomJoinRequests(String roomName) {
        send(Protocol.build(Protocol.GET_ROOM_JOIN_REQUESTS, roomName));
    }

    public void sendDeclineJoinRequest(String roomName, String username) {
        send(Protocol.build(Protocol.DECLINE_JOIN_REQUEST, roomName, username));
    }

    // ── Phase D: Tim kiem (tinh nang 6) ──────────────────────────────────────────

    public void sendSearch(String roomName, String keyword) {
        send(Protocol.build(Protocol.SEARCH_MSG, roomName, keyword));
    }

    // ── Phase D: Gui file/anh (tinh nang 7) ──────────────────────────────────────

    public void sendFileGroup(String fileName, String base64Content) {
        send(Protocol.build(Protocol.FILE_GROUP, fileName, base64Content));
    }

    public void sendFilePrivate(String toUser, String fileName, String base64Content) {
        send(Protocol.build(Protocol.FILE_PRIVATE, toUser, fileName, base64Content));
    }

    // ── Phase D: Thu hoi tin nhan (tinh nang 8) ──────────────────────────────────

    public void sendRecall(long messageId) {
        send(Protocol.build(Protocol.RECALL, String.valueOf(messageId)));
    }

    // ── Phase D: Dang ky tai khoan moi (tinh nang 9) ─────────────────────────────

    public void sendRegister(String username, String password) {
        send(Protocol.build(Protocol.REGISTER, username, password));
    }

    public void send(String frame) {
        if (writer != null && running) writer.println(frame);
    }

    // ── Receive loop ─────────────────────────────────────────────────────────

    private void startReceiver() {
        Thread t = new Thread(() -> {
            try {
                String line;
                while (running && (line = reader.readLine()) != null) {
                    dispatchFrame(line);
                }
            } catch (IOException e) {
                if (running) SystemLogger.warning("Mat ket noi server.");
            } finally {
                running = false;
                if (listener != null) listener.onDisconnected();
            }
        }, "receive-thread");
        t.setDaemon(true);
        t.start();
    }

    /**
     * Gui frame den listener.
     * Neu listener chua phai listener that (listenerIsReal = false),
     * cac frame khong phai AUTH se duoc buffer lai de phat lai sau.
     */
    private synchronized void dispatchFrame(String frame) {
        if (listenerIsReal) {
            // Listener that da san sang: gui thang
            if (listener != null) listener.onFrame(frame);
        } else {
            // Van dang dung listener tam (login): chi xu ly AUTH frame
            boolean isAuth = frame.startsWith(Protocol.AUTH_OK + ":")
                          || frame.startsWith(Protocol.AUTH_FAIL + ":");
            if (isAuth) {
                if (listener != null) listener.onFrame(frame);
            } else {
                // Buffer lai de phat sau khi listener that duoc set
                pendingFrames.add(frame);
            }
        }
    }

    // ── Disconnect ───────────────────────────────────────────────────────────

    public void disconnect() {
        running = false;
        try {
            if (writer != null) writer.close();
            if (reader != null) reader.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException ignored) {}
        SystemLogger.info("Da ngat ket noi.");
    }

    /**
     * Set listener tam (dung trong qua trinh dang nhap).
     * Frame khong phai AUTH se bi buffer lai.
     */
    public synchronized void setListener(MessageListener listener) {
        this.listener      = listener;
        this.listenerIsReal = false;
    }

    /**
     * Set listener that sau khi dang nhap thanh cong (MessageReceiver).
     * Se phat lai tat ca frame da bi buffer.
     */
    public synchronized void setRealListener(MessageListener listener) {
        this.listener       = listener;
        this.listenerIsReal = true;
        // Phat lai cac frame bi buffer (vi du: USER_LIST gui ngay sau AUTH_OK)
        List<String> toReplay = new ArrayList<>(pendingFrames);
        pendingFrames.clear();
        for (String frame : toReplay) {
            listener.onFrame(frame);
        }
    }

    public MessageListener getListener() { return listener; }

    public boolean isConnected() { return running; }
}
