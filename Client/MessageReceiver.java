package Client;

import UI.ChatPanel;
import model.Protocol;

import javax.swing.*;
import java.util.*;

/**
 * Parse frame tu server va cap nhat ChatPanel.
 * Chay tren EDT (SwingUtilities.invokeLater) de an toan voi Swing.
 *
 * Frame server → client:
 *   MSG_GROUP:sender:content
 *   MSG_PRIVATE:from:to:content
 *   USER_LIST:u1|epochJoin,u2|epochJoin     (epoch = millis)
 *   USER_JOIN:username|epochJoin
 *   USER_LEFT:username|epochLeft
 *   GROUP_CREATED:groupName:creator
 *   TYPING:username:1|0
 *   PONG
 *   ERROR:ly do
 */
public class MessageReceiver implements Client.MessageListener {

    private final ChatPanel chatPanel;
    private final String    myUsername;

    public MessageReceiver(ChatPanel chatPanel, String myUsername) {
        this.chatPanel  = chatPanel;
        this.myUsername = myUsername;
    }

    @Override
    public void onFrame(String frame) {
        SwingUtilities.invokeLater(() -> dispatch(frame));
    }

    @Override
    public void onDisconnected() {
        SwingUtilities.invokeLater(() ->
            chatPanel.appendSystemMessage("⚠️ Mất kết nối với server."));
    }

    // ── Dispatcher ────────────────────────────────────────────────────────────

    private void dispatch(String frame) {
        if (frame == null || frame.isEmpty()) return;

        // ── Tin nhan nhom ────────────────────────────────────────────────────
        if (frame.startsWith(Protocol.MSG_GROUP + ":")) {
            String[] p = Protocol.parse(frame, 3);
            if (p.length < 3) return;
            chatPanel.appendMessage(p[1], p[2], p[1].equals(myUsername));

        // ── Tin nhan rieng ────────────────────────────────────────────────────
        } else if (frame.startsWith(Protocol.MSG_PRIVATE + ":")) {
            String[] p = Protocol.parse(frame, 4);
            if (p.length < 4) return;
            chatPanel.appendPrivateMessage(p[1], p[2], p[3], p[1].equals(myUsername));

        // ── Danh sach online (USER_LIST:u1|epochJoin,u2|epochJoin) ───────────
        } else if (frame.startsWith(Protocol.USER_LIST + ":")) {
            String[] p = Protocol.parse(frame, 2);
            if (p.length < 2 || p[1].trim().isEmpty()) {
                chatPanel.setOnlineUsersWithTime(new LinkedHashMap<>());
                return;
            }
            chatPanel.setOnlineUsersWithTime(parseUserList(p[1]));

        // ── Tham gia (USER_JOIN:username|epochJoin) ───────────────────────────
        } else if (frame.startsWith(Protocol.USER_JOIN + ":")) {
            String[] p = Protocol.parse(frame, 2);
            if (p.length < 2 || p[1].trim().isEmpty()) return;
            String[] parts = p[1].trim().split("\\|", 2);
            String   who   = parts[0];
            long epoch = parseEpoch(parts.length > 1 ? parts[1] : "");
            chatPanel.addOnlineUser(who, epoch);
            chatPanel.appendSystemMessage("👋 " + who + " đã tham gia phòng chat.");

        // ── Roi khoi (USER_LEFT:username|epochLeft) ───────────────────────────
        } else if (frame.startsWith(Protocol.USER_LEFT + ":")) {
            String[] p = Protocol.parse(frame, 2);
            if (p.length < 2 || p[1].trim().isEmpty()) return;
            String[] parts     = p[1].trim().split("\\|", 2);
            String   who       = parts[0];
            long     epochLeft = parseEpoch(parts.length > 1 ? parts[1] : "");
            chatPanel.removeOnlineUser(who, epochLeft);
            chatPanel.appendSystemMessage("👋 " + who + " đã rời phòng chat.");

        // ── Nhom moi duoc tao ────────────────────────────────────────────────
        } else if (frame.startsWith(Protocol.GROUP_CREATED + ":")) {
            String[] p = Protocol.parse(frame, 3);
            if (p.length < 3) return;
            String msg = p[2].equals(myUsername)
                ? "🏠 Bạn đã tạo nhóm: " + p[1]
                : "🏠 " + p[2] + " đã tạo nhóm: " + p[1];
            chatPanel.appendSystemMessage(msg);

        // ── Ping that ─────────────────────────────────────────────────────────
        } else if (frame.equals(Protocol.PONG)) {
            chatPanel.onPong();

        // ── Typing indicator ──────────────────────────────────────────────────
        } else if (frame.startsWith(Protocol.TYPING + ":")) {
            String[] p = Protocol.parse(frame, 3);
            if (p.length < 3) return;
            boolean isTyping = "1".equals(p[2]);
            if (!p[1].equals(myUsername))
                chatPanel.showTyping(isTyping ? p[1] + " đang nhập..." : " ");

        // ── Loi tu server ────────────────────────────────────────────────────
        } else if (frame.startsWith(Protocol.ERROR + ":")) {
            String[] p = Protocol.parse(frame, 2);
            chatPanel.appendSystemMessage("⚠️ " + (p.length > 1 ? p[1] : "Lỗi không xác định"));

        // ── AUTH_FAIL da xu ly o LoginPanel, bo qua ───────────────────────────
        } else if (frame.startsWith(Protocol.AUTH_FAIL + ":")) {
            // khong lam gi

        // ── Frame khong ro → hien thi tho de debug ───────────────────────────
        } else {
            chatPanel.appendSystemMessage("[?] " + frame);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Parse "Alice|1718000000000,Bob|1718000001000"
     * → Map { Alice → 1718000000000L, Bob → 1718000001000L }
     */
    private Map<String, Long> parseUserList(String raw) {
        Map<String, Long> map = new LinkedHashMap<>();
        for (String entry : raw.split(",")) {
            entry = entry.trim();
            if (entry.isEmpty()) continue;
            String[] parts = entry.split("\\|", 2);
            String   name  = parts[0].trim();
            long     epoch = parseEpoch(parts.length > 1 ? parts[1].trim() : "");
            if (!name.isEmpty()) map.put(name, epoch);
        }
        return map;
    }

    /** Parse chuoi epoch string, tra ve currentTimeMillis neu loi. */
    private long parseEpoch(String s) {
        try { return Long.parseLong(s.trim()); }
        catch (Exception e) { return System.currentTimeMillis(); }
    }
}
