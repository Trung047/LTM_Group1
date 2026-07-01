package UI;

import Client.Client;
import Client.MessageReceiver;
import model.Protocol;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Base64;
import java.util.Timer;
import java.util.TimerTask;

/**
 * ChatFrame - Cua so chat chinh sau khi dang nhap thanh cong.
 *
 * Tinh nang:
 *  - Ping that: markPingSent() ghi timestamp, PING, server tra PONG,
 *    onPong() tinh System.currentTimeMillis() - pingSentAt = RTT thuc te
 *  - Typing debounce: gui TYPING:1 khi bat dau go, TYPING:0 sau 1.5s dung
 *  - Ngat ket noi -> quay lai LoginPanel
 *  - Private message: gui qua tab "Tin nhắn riêng" (ChatPanel.ChatListener.onSendPrivateMessage)
 *  - Tao nhom: click nut "+" ben canh input -> hien dialog nhap ten nhom
 *  - Phase D: gui file/anh, thu hoi tin nhan, tim kiem, quan ly thanh vien phong
 *
 * FIX RACE CONDITION USER_LIST:
 *  Dung client.setRealListener() thay vi setListener() de dam bao
 *  cac frame bi buffer (USER_LIST, USER_JOIN) duoc phat lai sau khi
 *  MessageReceiver da san sang.
 */
public class ChatFrame extends JFrame {

    private final Client    client;
    private final ChatPanel chatPanel;
    private final String    username;

    // Timer dung chung cho ping va typing debounce
    private final Timer   pingTimer  = new Timer("ping-timer", true);
    private       TimerTask typingTask = null;
    private       boolean   isTyping   = false;

    public ChatFrame(String username, String host, int port, Client client) {
        this.username = username;
        this.client   = client;

        setTitle("NetChat — " + username);
        setSize(1040, 680);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

        chatPanel = new ChatPanel();
        chatPanel.setMyUsername(username);
        chatPanel.setConnInfo(host + ":" + port);
        add(chatPanel);

        // Gan MessageReceiver truoc khi hien cua so
        // Dung setRealListener de dam bao frame bi buffer duoc phat lai ngay
        MessageReceiver receiver = new MessageReceiver(chatPanel, username, client);
        client.setRealListener(receiver);

        // Lang nghe su kien tu ChatPanel
        chatPanel.setChatListener(new ChatPanel.ChatListener() {
            @Override public void onSendMessage(String text) { sendMessage(text); }
            @Override public void onDisconnect()              { doDisconnect(); }
            @Override public void onTyping(boolean typing)   { handleTyping(typing); }
            @Override public void onCreateGroup()            { doCreateGroup(); }
            @Override public void onJoinRoom(String roomName){ joinRoom(roomName); }

            // ── Phase D ───────────────────────────────────────────────────
            @Override public void onSendPrivateMessage(String toUser, String text) {
                client.sendPrivate(toUser, text);
            }
            @Override public void onSendFileGroup(File file) {
                sendFile(file, null);
            }
            @Override public void onSendFilePrivate(String toUser, File file) {
                sendFile(file, toUser);
            }
            @Override public void onRecallMessage(long id) {
                client.sendRecall(id);
            }
            @Override public void onSearch(String keyword) {
                client.sendSearch(chatPanel.getCurrentRoom(), keyword);
            }
            @Override public void onRemoveMember(String room, String username) {
                client.sendRemoveMember(room, username);
            }
            @Override public void onAddMember(String room, String username) {
                client.sendAddMember(room, username);
            }
            @Override public void onDeclineJoinRequest(String room, String username) {
                client.sendDeclineJoinRequest(room, username);
            }
            @Override public void onRequestRoomMembers(String room) {
                client.requestRoomMembers(room);
            }
            @Override public void onRequestRoomJoinRequests(String room) {
                client.requestRoomJoinRequests(room);
            }
        });

        // Ping timer: gui PING moi 5 giay, do RTT that
        pingTimer.scheduleAtFixedRate(new TimerTask() {
            @Override public void run() {
                chatPanel.markPingSent();
                client.sendPing();
            }
        }, 3000, 5000);

        // Dong cua so -> ngat ket noi sach
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { doDisconnect(); }
        });

        chatPanel.appendSystemMessage("Chao mung " + username + " den phong chat! 🎉");
        chatPanel.appendSystemMessage("Chat riêng: mở tab \"Tin nhắn riêng\" hoặc click vào tên người dùng bên phải");
        chatPanel.appendSystemMessage("Tao nhom: bam nut [+] ben canh o nhap tin");
        chatPanel.requestInputFocus();
    }

    // ── Gui tin nhan nhom ────────────────────────────────────────────────────

    private void sendMessage(String text) {
        client.sendGroup(text);
    }

    // ── Gui file/anh (Phase D — tinh nang 7) ────────────────────────────────

    /** doi = null -> gui vao phong hien tai; doi != null -> gui rieng cho nguoi do. */
    private void sendFile(File file, String toUser) {
        try {
            byte[] bytes  = Files.readAllBytes(file.toPath());
            String base64 = Base64.getEncoder().encodeToString(bytes);
            if (toUser == null) {
                client.sendFileGroup(file.getName(), base64);
            } else {
                client.sendFilePrivate(toUser, file.getName(), base64);
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Không thể đọc file: " + e.getMessage(),
                    "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ── Tao nhom ─────────────────────────────────────────────────────────────

    private void doCreateGroup() {
        String groupName = JOptionPane.showInputDialog(
            this,
            "Nhap ten nhom:",
            "Tao nhom moi",
            JOptionPane.PLAIN_MESSAGE
        );
        if (groupName == null) return; // Nguoi dung bam Cancel
        groupName = groupName.trim();
        if (groupName.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "Ten nhom khong duoc de trong!",
                "Loi", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (groupName.length() > 50) {
            JOptionPane.showMessageDialog(this,
                "Ten nhom qua dai (toi da 50 ky tu)!",
                "Loi", JOptionPane.WARNING_MESSAGE);
            return;
        }
        client.sendCreateGroup(groupName);
        client.requestRoomList();
    }

    // ── Typing debounce ───────────────────────────────────────────────────────

    private synchronized void handleTyping(boolean typing) {
        if (typing && !isTyping) {
            isTyping = true;
            client.sendTyping(true);
        }
        if (typingTask != null) typingTask.cancel();
        typingTask = new TimerTask() {
            @Override public void run() {
                isTyping = false;
                client.sendTyping(false);
            }
        };
        pingTimer.schedule(typingTask, 1500);
    }

    // ── Ngat ket noi ─────────────────────────────────────────────────────────

    private void doDisconnect() {
        pingTimer.cancel();
        client.sendLogout();
        client.disconnect();
        dispose();
        SwingUtilities.invokeLater(() -> {
            LoginPanel loginPanel = new LoginPanel();
            JFrame loginFrame = new JFrame("NetChat — Dang nhap");
            loginFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            loginFrame.setSize(420, 620);
            loginFrame.setLocationRelativeTo(null);
            loginPanel.setLoginListener((uname, password, host, port) ->
                doLogin(loginFrame, loginPanel, uname, password, host, port));
            loginFrame.add(loginPanel);
            loginFrame.setVisible(true);
        });
    }

    // ── Static helper: xu ly dang nhap tu LoginPanel ────────────────────────

    public static void doLogin(JFrame loginFrame, LoginPanel loginPanel,
                               String uname, String password, String host, int port) {
        new Thread(() -> {
            Client c = new Client();

            if (!c.connect(host, port)) {
                loginPanel.showStatus("Khong the ket noi server " + host + ":" + port);
                loginPanel.resetConnectButton();
                return;
            }

            final String[] result = { null };
            final Object   lock   = new Object();

            c.setListener(frame -> {
                if (frame.startsWith(Protocol.AUTH_OK   + ":") ||
                    frame.startsWith(Protocol.AUTH_FAIL + ":")) {
                    synchronized (lock) {
                        result[0] = frame;
                        lock.notifyAll();
                    }
                }
            });

            c.sendLogin(uname, password);

            synchronized (lock) {
                try { lock.wait(5000); } catch (InterruptedException ignored) {}
            }

            if (result[0] == null) {
                loginPanel.showStatus("Server khong phan hoi (timeout 5s).");
                loginPanel.resetConnectButton();
                c.disconnect();
                return;
            }

            if (result[0].startsWith(Protocol.AUTH_FAIL)) {
                String[] p = Protocol.parse(result[0], 2);
                loginPanel.showStatus(p.length > 1 ? p[1] : "Dang nhap that bai.");
                loginPanel.resetConnectButton();
                c.disconnect();
                return;
            }

            SwingUtilities.invokeLater(() -> {
                loginFrame.setVisible(false);
                loginFrame.dispose();
                new ChatFrame(uname, host, port, c).setVisible(true);
            });

        }, "login-thread").start();
    }

    // ── Static helper: dang ky tai khoan moi (Phase D — tinh nang 9) ────────

    public interface RegisterCallback {
        void onResult(boolean ok, String message);
    }

    /**
     * Ket noi tam thoi toi server, gui REGISTER, cho ket qua roi ngat ket noi.
     * Khong tu dong dang nhap sau khi dang ky thanh cong — nguoi dung quay lai
     * form dang nhap va nhap lai tai khoan vua tao, giu 2 luong tach biet don gian.
     */
    public static void doRegister(String host, int port, String uname, String password, RegisterCallback callback) {
        new Thread(() -> {
            Client c = new Client();

            if (!c.connect(host, port)) {
                callback.onResult(false, "Không thể kết nối server " + host + ":" + port);
                return;
            }

            final String[] result = { null };
            final Object   lock   = new Object();

            c.setListener(frame -> {
                if (frame.startsWith(Protocol.REGISTER_OK   + ":") ||
                    frame.startsWith(Protocol.REGISTER_FAIL + ":")) {
                    synchronized (lock) {
                        result[0] = frame;
                        lock.notifyAll();
                    }
                }
            });

            c.sendRegister(uname, password);

            synchronized (lock) {
                try { lock.wait(5000); } catch (InterruptedException ignored) {}
            }

            c.disconnect();

            if (result[0] == null) {
                callback.onResult(false, "Server không phản hồi (timeout 5s).");
                return;
            }
            if (result[0].startsWith(Protocol.REGISTER_FAIL)) {
                String[] p = Protocol.parse(result[0], 2);
                callback.onResult(false, p.length > 1 ? p[1] : "Đăng ký thất bại.");
                return;
            }
            callback.onResult(true, "Đăng ký thành công! Hãy đăng nhập.");
        }, "register-thread").start();
    }

    // ── Chuyển phòng ───────────────────────────────────────────────────────────
    private void joinRoom(String roomName) {
        client.sendJoinRoom(roomName);
        chatPanel.setCurrentRoom(roomName);
        chatPanel.clearChat();
        chatPanel.appendSystemMessage("Đã chuyển sang phòng: " + roomName);
    }
}
