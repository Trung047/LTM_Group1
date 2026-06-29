package UI;

import Client.Client;
import Client.MessageReceiver;
import model.Protocol;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
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
 *  - Private message: nhan dang "@username noi_dung"
 *  - Tao nhom: click nut "+" ben canh input -> hien dialog nhap ten nhom
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
        setSize(980, 650);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

        chatPanel = new ChatPanel();
        chatPanel.setMyUsername(username);
        chatPanel.setConnInfo(host + ":" + port);
        add(chatPanel);

        // Gan MessageReceiver truoc khi hien cua so
        // Dung setRealListener de dam bao frame bi buffer duoc phat lai ngay
        MessageReceiver receiver = new MessageReceiver(chatPanel, username);
        client.setRealListener(receiver);

        // Lang nghe su kien tu ChatPanel
        chatPanel.setChatListener(new ChatPanel.ChatListener() {
            @Override public void onSendMessage(String text) { sendMessage(text); }
            @Override public void onDisconnect()              { doDisconnect(); }
            @Override public void onTyping(boolean typing)   { handleTyping(typing); }
            @Override public void onCreateGroup()            { doCreateGroup(); }
            @Override public void onJoinRoom(String roomName){ joinRoom(roomName); }
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
        chatPanel.appendSystemMessage("Nhan rieng: go @ten_nguoi noi_dung");
        chatPanel.appendSystemMessage("Tao nhom: bam nut [+] ben canh o nhap tin");
        chatPanel.requestInputFocus();
    }

    // ── Gui tin nhan ─────────────────────────────────────────────────────────

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
            // Format sai (@abc khong co noi dung) -> gui nhom binh thuong
        }
        client.sendGroup(text);
    }

    // ── Tao nhom ─────────────────────────────────────────────────────────────

    /**
     * Hien dialog nhap ten nhom, gui CREATE_GROUP len server.
     * Server se broadcast GROUP_CREATED cho tat ca client.
     */
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
        // Reset timer: dung go 1.5s -> gui TYPING:0
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
        // Quay lai man hinh dang nhap
        SwingUtilities.invokeLater(() -> {
            LoginPanel loginPanel = new LoginPanel();
            JFrame loginFrame = new JFrame("NetChat — Dang nhap");
            loginFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            loginFrame.setSize(420, 540);
            loginFrame.setLocationRelativeTo(null);
            loginPanel.setLoginListener((uname, password, host, port) ->
                doLogin(loginFrame, loginPanel, uname, password, host, port));
            loginFrame.add(loginPanel);
            loginFrame.setVisible(true);
        });
    }

    // ── Static helper: xu ly dang nhap tu LoginPanel ────────────────────────

    /**
     * Ket noi server va thuc hien handshake LOGIN.
     *
     * Luong:
     *   1. Tao Client, ket noi TCP toi host:port
     *   2. Dang ky listener tam de bat AUTH_OK / AUTH_FAIL
     *   3. Gui LOGIN:username:password
     *   4. Cho toi da 5 giay
     *   5a. AUTH_OK  -> tao ChatFrame, dong LoginFrame
     *   5b. AUTH_FAIL -> hien thi ly do, reset nut ket noi
     *   5c. Timeout  -> bao loi, ngat ket noi
     *
     * NOTE: Dung setListener() (khong phai setRealListener()) cho listener tam
     * de cac frame USER_LIST den truoc khi ChatFrame san sang duoc buffer lai.
     */
    public static void doLogin(JFrame loginFrame, LoginPanel loginPanel,
                               String uname, String password, String host, int port) {
        new Thread(() -> {
            Client c = new Client();

            // Buoc 1: ket noi TCP
            if (!c.connect(host, port)) {
                loginPanel.showStatus("Khong the ket noi server " + host + ":" + port);
                loginPanel.resetConnectButton();
                return;
            }

            // Buoc 2: listener tam cho AUTH_OK / AUTH_FAIL
            // Dung setListener() (khong phai setRealListener()) ->
            // cac frame khac (USER_LIST...) se bi buffer den khi listener that duoc set
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

            // Buoc 3: gui LOGIN
            c.sendLogin(uname, password);

            // Buoc 4: cho phan hoi (timeout 5 giay)
            synchronized (lock) {
                try { lock.wait(5000); } catch (InterruptedException ignored) {}
            }

            // Buoc 5: xu ly ket qua
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

            // AUTH_OK -> mo ChatFrame
            // ChatFrame constructor se goi setRealListener() -> buffer duoc phat lai
            SwingUtilities.invokeLater(() -> {
                loginFrame.setVisible(false);
                loginFrame.dispose();
                new ChatFrame(uname, host, port, c).setVisible(true);
            });

        }, "login-thread").start();
    }
    // ── Chuyển phòng ───────────────────────────────────────────────────────────
    private void joinRoom(String roomName) {
        client.sendJoinRoom(roomName);
        chatPanel.setCurrentRoom(roomName);
        chatPanel.clearChat();
        chatPanel.appendSystemMessage("Đã chuyển sang phòng: " + roomName);
    }
}
