package Client;

import UI.ChatPanel;
import javax.swing.SwingUtilities;
import java.io.BufferedReader;
import java.io.IOException;

/**
 * Chạy trên thread riêng, liên tục đọc tin nhắn từ server
 * rồi hiển thị lên ChatPanel.
 *
 * Người 2 (ChatFrame) chỉ cần gọi:
 *   new MessageReceiver(reader, chatPanel, username).start();
 */
public class MessageReceiver extends Thread {

    private final BufferedReader reader;
    private final ChatPanel chatPanel;
    private final String myUsername; // để biết tin nào là của mình

    public MessageReceiver(BufferedReader reader, ChatPanel chatPanel, String myUsername) {
        super("receive-thread");
        this.reader = reader;
        this.chatPanel = chatPanel;
        this.myUsername = myUsername;
        setDaemon(true); // tắt app thì thread này tự dừng theo
    }

    @Override
    public void run() {
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                final String msg = line;
                // Cập nhật UI phải chạy trên EDT của Swing
                SwingUtilities.invokeLater(() -> display(msg));
            }
        } catch (IOException e) {
            SwingUtilities.invokeLater(() ->
                chatPanel.appendSystemMessage("Mất kết nối với server."));
        }
    }

    /**
     * Parse tin nhắn từ server rồi gọi ChatPanel hiển thị đúng loại.
     *
     * Format server gửi về (xem MessageHandler.java):
     *   Nhóm:    [TênNgười] : nội dung
     *   Riêng:   [Private] TênNgười -> TênKhác : nội dung
     *   Hệ thống: [Server] ...
     */
    private void display(String raw) {
        if (raw.startsWith("[Private]")) {
            // Tin nhắn riêng — hiển thị nổi bật giữa màn hình
            chatPanel.appendSystemMessage(raw);

        } else if (raw.startsWith("[Server]")) {
            // Thông báo hệ thống: ai đó join/leave
            chatPanel.appendSystemMessage(raw.substring("[Server]".length()).trim());

        } else if (raw.startsWith("[") && raw.contains("] : ")) {
            // Tin nhắn nhóm: [TênNgười] : nội dung
            int closeBracket = raw.indexOf("] : ");
            String sender  = raw.substring(1, closeBracket).trim();
            String content = raw.substring(closeBracket + 4).trim();

            boolean isOwn = sender.equals(myUsername);
            chatPanel.appendMessage(sender, content, isOwn);

        } else {
            // Không khớp format nào — cứ hiện lên cho chắc
            chatPanel.appendSystemMessage(raw);
        }
    }
}
