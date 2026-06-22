package Client;

import Logging.SystemLogger;
import model.Protocol;

import java.io.*;
import java.net.Socket;

/**
 * Lớp Client thuần – chịu trách nhiệm kết nối mạng.
 * UI (ChatFrame) đăng ký MessageListener để nhận frame từ server.
 */
public class Client {

    public interface MessageListener {
        void onFrame(String frame);
        default void onDisconnected() {}
    }

    private Socket         socket;
    private PrintWriter    writer;
    private BufferedReader reader;
    private boolean        running = false;
    private MessageListener listener;

    // ── Connect ──────────────────────────────────────────────────────────────

    public boolean connect(String host, int port) {
        try {
            socket = new Socket(host, port);
            socket.setTcpNoDelay(true);
            writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
            running = true;
            startReceiver();
            SystemLogger.info("Kết nối server " + host + ":" + port);
            return true;
        } catch (IOException e) {
            SystemLogger.error("Không thể kết nối: " + e.getMessage());
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

    public void send(String frame) {
        if (writer != null && running) writer.println(frame);
    }

    // ── Receive loop ─────────────────────────────────────────────────────────

    private void startReceiver() {
        Thread t = new Thread(() -> {
            try {
                String line;
                while (running && (line = reader.readLine()) != null) {
                    if (listener != null) listener.onFrame(line);
                }
            } catch (IOException e) {
                if (running) SystemLogger.warning("Mất kết nối server.");
            } finally {
                running = false;
                if (listener != null) listener.onDisconnected();
            }
        }, "receive-thread");
        t.setDaemon(true);
        t.start();
    }

    // ── Disconnect ───────────────────────────────────────────────────────────

    public void disconnect() {
        running = false;
        try {
            if (writer != null) writer.close();
            if (reader != null) reader.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException ignored) {}
        SystemLogger.info("Đã ngắt kết nối.");
    }

    public void setListener(MessageListener listener) {
        this.listener = listener;
    }

    public MessageListener getListener() { return listener; }

    public boolean isConnected() { return running; }
}
