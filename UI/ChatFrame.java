package UI;

import Client.Client;
import Client.MessageReceiver;
import model.Protocol;

import javax.swing.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Timer;
import java.util.TimerTask;

/**
 * ChatFrame — Cửa sổ chat chính sau khi đăng nhập thành công.
 *
 * Tính năng:
 *  - Ping thật: markPingSent() ghi timestamp → PING → server trả PONG
 *    → onPong() tính System.currentTimeMillis() - pingSentAt = RTT thực tế
 *  - Typing debounce: gửi TYPING:1 khi bắt đầu gõ, TYPING:0 sau 1.5s dừng
 *  - Ngắt kết nối → quay lại LoginPanel
 *  - Private message: nhận dạng "@username nội_dung"
 */
public class ChatFrame extends JFrame {

    private final Client    client;
    private final ChatPanel chatPanel;
    private final String    username;

    // Timer dùng chung cho ping và typing debounce
    private final Timer   pingTimer  = new Timer("ping-timer", true);
    private       TimerTask typingTask = null;
    private       boolean   isTyping   = false;

    public ChatFrame(String username, String host, int port, Client client) {
        this.username = username;
        this.client   = client;

        setTitle("NetChat — " + username);
        setSize(980, 650);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

        chatPanel = new ChatPanel();
        chatPanel.setMyUsername(username);
        chatPanel.setConnInfo(host + ":" + port);
        add(chatPanel);

        // Gắn MessageReceiver — xử lý mọi frame đến từ server
        MessageReceiver receiver = new MessageReceiver(chatPanel, username);
        client.setListener(receiver);

        // Lắng nghe sự kiện từ ChatPanel
        chatPanel.setChatListener(new ChatPanel.ChatListener() {
            @Override public void onSendMessage(String text) { sendMessage(text); }
            @Override public void onDisconnect()              { doDisconnect(); }
            @Override public void onTyping(boolean typing)   { handleTyping(typing); }
        });

        // Ping timer: gửi PING mỗi 5 giây, đo RTT thật
        pingTimer.scheduleAtFixedRate(new TimerTask() {
            @Override public void run() {
                // Ghi timestamp trước khi gửi để tính RTT chính xác
                chatPanel.markPingSent();
                client.sendPing();
            }
        }, 3000, 5000);

        // Đóng cửa sổ → ngắt kết nối sạch
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { doDisconnect(); }
        });

        chatPanel.appendSystemMessage("Chào mừng " + username + " đến phòng chat! 🎉");
        chatPanel.appendSystemMessage("Nhắn riêng: gõ @tên_người nội_dung");
        chatPanel.requestInputFocus();
    }

    // ── Gửi tin nhắn ─────────────────────────────────────────────────────────

    private void sendMessage(String text) {
        if (text.startsWith("@")) {
            int spaceIdx = text.indexOf(' ');
            if (spaceIdx > 1) {
                String toUser  = text.substring(1, spaceIdx).trim();
                String content = text.substring(spaceIdx + 1).trim();
                if (!content.isEmpty()) {
                    client.sendPrivate(toUser, content);
                    return;
                }
            }
            // Format sai (@abc không có nội dung) → gửi nhóm bình thường
        }
        client.sendGroup(text);
    }

    // ── Typing debounce ───────────────────────────────────────────────────────

    private synchronized void handleTyping(boolean typing) {
        if (typing && !isTyping) {
            isTyping = true;
            client.sendTyping(true);
        }
        // Reset timer: dừng gõ 1.5s → gửi TYPING:0
        if (typingTask != null) typingTask.cancel();
        typingTask = new TimerTask() {
            @Override public void run() {
                isTyping = false;
                client.sendTyping(false);
            }
        };
        pingTimer.schedule(typingTask, 1500);
    }

    // ── Ngắt kết nối ─────────────────────────────────────────────────────────

    private void doDisconnect() {
        pingTimer.cancel();
        client.sendLogout();
        client.disconnect();
        dispose();
        // Quay lại màn hình đăng nhập
        SwingUtilities.invokeLater(() -> {
            LoginPanel loginPanel = new LoginPanel();
            JFrame loginFrame = new JFrame("NetChat — Đăng nhập");
            loginFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            loginFrame.setSize(420, 540);
            loginFrame.setLocationRelativeTo(null);
            loginPanel.setLoginListener((uname, password, host, port) ->
                doLogin(loginFrame, loginPanel, uname, password, host, port));
            loginFrame.add(loginPanel);
            loginFrame.setVisible(true);
        });
    }

    // ── Static helper: xử lý đăng nhập từ LoginPanel ────────────────────────

    /**
     * Kết nối server và thực hiện handshake LOGIN.
     *
     * Luồng:
     *   1. Tạo Client, kết nối TCP tới host:port
     *   2. Đăng ký listener tạm để bắt AUTH_OK / AUTH_FAIL
     *   3. Gửi LOGIN:username:password
     *   4. Chờ tối đa 5 giây
     *   5a. AUTH_OK  → tạo ChatFrame, đóng LoginFrame
     *   5b. AUTH_FAIL → hiển thị lý do, reset nút kết nối
     *   5c. Timeout  → báo lỗi, ngắt kết nối
     */
    public static void doLogin(JFrame loginFrame, LoginPanel loginPanel,
                               String uname, String password, String host, int port) {
        new Thread(() -> {
            Client c = new Client();

            // Bước 1: kết nối TCP
            if (!c.connect(host, port)) {
                loginPanel.showStatus("Không thể kết nối server " + host + ":" + port);
                loginPanel.resetConnectButton();
                return;
            }

            // Bước 2: listener tạm chờ AUTH_OK / AUTH_FAIL
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

            // Bước 3: gửi LOGIN
            c.sendLogin(uname, password);

            // Bước 4: chờ phản hồi (timeout 5 giây)
            synchronized (lock) {
                try { lock.wait(5000); } catch (InterruptedException ignored) {}
            }

            // Bước 5: xử lý kết quả
            if (result[0] == null) {
                loginPanel.showStatus("Server không phản hồi (timeout 5s).");
                loginPanel.resetConnectButton();
                c.disconnect();
                return;
            }

            if (result[0].startsWith(Protocol.AUTH_FAIL)) {
                String[] p = Protocol.parse(result[0], 2);
                loginPanel.showStatus(p.length > 1 ? p[1] : "Đăng nhập thất bại.");
                loginPanel.resetConnectButton();
                c.disconnect();
                return;
            }

            // AUTH_OK → mở ChatFrame
            SwingUtilities.invokeLater(() -> {
                loginFrame.setVisible(false);
                loginFrame.dispose();
                new ChatFrame(uname, host, port, c).setVisible(true);
            });

        }, "login-thread").start();
    }
}
