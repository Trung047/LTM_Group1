package Client;

import UI.ChatPanel;
import model.Protocol;

import javax.swing.*;
import java.util.*;

/**
 * Parse frame tu server va cap nhat ChatPanel.
 * Chay tren EDT (SwingUtilities.invokeLater) de an toan voi Swing.
 *
 * Frame server → client (Phase D — cap nhat so voi Phase C):
 *   MSG_GROUP:id:room:sender:epochMillis:content
 *   MSG_PRIVATE:id:from:to:epochMillis:content
 *   MSG_FILE_GROUP:id:room:sender:epochMillis:tenFile:base64
 *   MSG_FILE_PRIVATE:id:from:to:epochMillis:tenFile:base64
 *   MSG_RECALLED:id:room
 *   MSG_RECALLED_PRIVATE:id:from:to
 *   MEMBER_REMOVED:room:username
 *   MEMBER_ADDED:room:username
 *   KICKED_FROM_ROOM:room
 *   ROOM_MEMBERS:room:u1,u2,u3
 *   SEARCH_RESULT:id:room:sender:epoch:content
 *   SEARCH_DONE:soLuong
 *   USER_LIST:u1|epochJoin,u2|epochJoin     (epoch = millis)
 *   USER_JOIN:username|epochJoin
 *   USER_LEFT:username|epochLeft
 *   GROUP_CREATED:groupName:creator
 *   ROOM_LIST:room1,room2,room3
 *   TYPING:username:1|0
 *   PONG
 *   ERROR:ly do
 */
public class MessageReceiver implements Client.MessageListener {

    private final ChatPanel chatPanel;
    private final String    myUsername;
    private final Client    client;

    public MessageReceiver(ChatPanel chatPanel, String myUsername, Client client) {
        this.chatPanel  = chatPanel;
        this.myUsername = myUsername;
        this.client      = client;
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

        // ── Tin nhan nhom (id:room:sender:epoch:content) ────────────────────
        if (frame.startsWith(Protocol.MSG_GROUP + ":")) {
            String[] p = Protocol.parse(frame, 6);
            if (p.length < 6) return;
            long   id      = parseLong(p[1]);
            String room    = p[2];
            String sender  = p[3];
            long   epoch   = parseEpoch(p[4]);
            String content = p[5];
            if (room.equals(chatPanel.getCurrentRoom())) {
                chatPanel.appendMessage(id, sender, content, sender.equals(myUsername), epoch);
            }

        // ── Tin nhan file/anh trong nhom ─────────────────────────────────────
        } else if (frame.startsWith(Protocol.MSG_FILE_GROUP + ":")) {
            String[] p = Protocol.parse(frame, 7);
            if (p.length < 7) return;
            long   id       = parseLong(p[1]);
            String room     = p[2];
            String sender   = p[3];
            long   epoch    = parseEpoch(p[4]);
            String fileName = p[5];
            String base64   = p[6];
            if (room.equals(chatPanel.getCurrentRoom())) {
                chatPanel.appendFileMessage(id, sender, fileName, base64, sender.equals(myUsername), epoch);
            }

        // ── Tin nhan rieng (id:from:to:epoch:content) ────────────────────────
        } else if (frame.startsWith(Protocol.MSG_PRIVATE + ":")) {
            String[] p = Protocol.parse(frame, 6);
            if (p.length < 6) return;
            long   id      = parseLong(p[1]);
            String from    = p[2];
            String to      = p[3];
            long   epoch   = parseEpoch(p[4]);
            String content = p[5];
            chatPanel.appendPrivateMessage(id, from, to, content, from.equals(myUsername), epoch);

        // ── Tin nhan file/anh rieng ───────────────────────────────────────────
        } else if (frame.startsWith(Protocol.MSG_FILE_PRIVATE + ":")) {
            String[] p = Protocol.parse(frame, 7);
            if (p.length < 7) return;
            long   id       = parseLong(p[1]);
            String from     = p[2];
            String to       = p[3];
            long   epoch    = parseEpoch(p[4]);
            String fileName = p[5];
            String base64   = p[6];
            chatPanel.appendPrivateFileMessage(id, from, to, fileName, base64, from.equals(myUsername), epoch);

        // ── Thu hoi tin nhan (nhom hoac rieng — deu chi can id de danh dau) ──
        } else if (frame.startsWith(Protocol.MSG_RECALLED + ":")) {
            String[] p = Protocol.parse(frame, 3);
            if (p.length < 2) return;
            chatPanel.markRecalled(parseLong(p[1]));

        } else if (frame.startsWith(Protocol.MSG_RECALLED_PRIVATE + ":")) {
            String[] p = Protocol.parse(frame, 4);
            if (p.length < 2) return;
            chatPanel.markRecalled(parseLong(p[1]));

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
            client.requestRoomList();

        // ── Danh sach phong (ROOM_LIST:room1,room2,room3) ───────────────────
        } else if (frame.startsWith(Protocol.ROOM_LIST + ":")) {
            String[] p = Protocol.parse(frame, 2);
            if (p.length < 2 || p[1].trim().isEmpty()) return;
            List<String> rooms = Arrays.asList(p[1].split(","));
            chatPanel.updateRoomList(rooms);

        // ── Phase D: quan ly thanh vien phong ─────────────────────────────────
        } else if (frame.startsWith(Protocol.MEMBER_REMOVED + ":")) {
            String[] p = Protocol.parse(frame, 3);
            if (p.length < 3) return;
            chatPanel.appendSystemMessage("👤 " + p[2] + " đã bị xóa khỏi phòng " + p[1] + ".");

        } else if (frame.startsWith(Protocol.MEMBER_ADDED + ":")) {
            String[] p = Protocol.parse(frame, 3);
            if (p.length < 3) return;
            String msg = p[2].equals(myUsername)
                ? "👤 Bạn đã được thêm vào phòng " + p[1] + "."
                : "👤 " + p[2] + " đã được thêm vào phòng " + p[1] + ".";
            chatPanel.appendSystemMessage(msg);

        } else if (frame.startsWith(Protocol.KICKED_FROM_ROOM + ":")) {
            String[] p = Protocol.parse(frame, 2);
            if (p.length < 2) return;
            chatPanel.appendSystemMessage("⚠️ Bạn đã bị xóa khỏi phòng " + p[1] + " — đã chuyển về phòng "
                    + Protocol.DEFAULT_ROOM + ".");
            chatPanel.setCurrentRoom(Protocol.DEFAULT_ROOM);
            chatPanel.clearChat();
            client.sendJoinRoom(Protocol.DEFAULT_ROOM);

        } else if (frame.startsWith(Protocol.ROOM_MEMBERS + ":")) {
            String[] p = Protocol.parse(frame, 3);
            if (p.length < 3) return;
            String room = p[1];
            List<String> members = p[2].trim().isEmpty()
                    ? new ArrayList<>() : Arrays.asList(p[2].split(","));
            chatPanel.setRoomMembers(room, members);

        // ── Phase D: tim kiem ──────────────────────────────────────────────────
        } else if (frame.startsWith(Protocol.SEARCH_RESULT + ":")) {
            String[] p = Protocol.parse(frame, 5);
            if (p.length < 5) return;
            chatPanel.addSearchResult(p[2], p[4], parseEpoch(p[3]));

        } else if (frame.startsWith(Protocol.SEARCH_DONE + ":")) {
            String[] p = Protocol.parse(frame, 2);
            if (p.length < 2) return;
            try { chatPanel.searchDone(Integer.parseInt(p[1].trim())); }
            catch (NumberFormatException ignored) { chatPanel.searchDone(0); }

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

        // ── AUTH_FAIL / REGISTER_* da xu ly o LoginPanel, bo qua ─────────────
        } else if (frame.startsWith(Protocol.AUTH_FAIL + ":")
                || frame.startsWith(Protocol.REGISTER_OK + ":")
                || frame.startsWith(Protocol.REGISTER_FAIL + ":")) {
            // khong lam gi

        // ── Frame khong ro → hien thi tho de debug ───────────────────────────
        } else {
            chatPanel.appendSystemMessage("[?] " + frame);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

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

    private long parseEpoch(String s) {
        try { return Long.parseLong(s.trim()); }
        catch (Exception e) { return System.currentTimeMillis(); }
    }

    private long parseLong(String s) {
        try { return Long.parseLong(s.trim()); }
        catch (Exception e) { return -1; }
    }
}
