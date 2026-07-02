package UI;

import model.Protocol;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.nio.file.Files;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.*;

/**
 * ChatPanel — Màn hình chat chính.
 *
 * Phase D (so với bản Phase A/B/C):
 *  - Tách hẳn giao diện chat NHÓM (tab "Phòng chat") và chat RIÊNG
 *    (tab "Tin nhắn riêng", có danh sách hội thoại bên trái) — tính năng 3
 *  - Mỗi tin nhắn hiển thị đầy đủ ngày + giờ (không chỉ HH:mm) — tính năng 4
 *  - Tìm kiếm tin nhắn trong phòng hiện tại — tính năng 6
 *  - Gửi/nhận file & ảnh (hiển thị dạng liên kết 📎, bấm để lưu) — tính năng 7
 *  - Thu hồi tin nhắn của chính mình (liên kết dưới mỗi tin của mình) — tính năng 8
 *  - Quản lý thành viên phòng: thêm / xóa (chỉ người tạo phòng) — tính năng 1 & 2
 *
 * Giữ nguyên tinh thần UI gốc: HTML bubble trong JTextPane, DarkTheme, emoji bar,
 * ping/typing label, danh sách phòng + danh sách user.
 */
public class ChatPanel extends JPanel {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final long MAX_FILE_BYTES = 5L * 1024 * 1024; // 5MB, khop voi gioi han server

    // ── Callbacks ──────────────────────────────────────────────────────────────
    public interface ChatListener {
        void onSendMessage(String text);              // gui tin nhan nhom (phong hien tai)
        void onDisconnect();
        void onTyping(boolean isTyping);
        void onJoinRoom(String roomName);
        default void onCreateGroup() {}
        // Phase D
        default void onSendPrivateMessage(String toUser, String text) {}
        default void onSendFileGroup(File file) {}
        default void onSendFilePrivate(String toUser, File file) {}
        default void onRecallMessage(long id) {}
        default void onSearch(String keyword) {}
        default void onRemoveMember(String room, String username) {}
        default void onAddMember(String room, String username) {}
        default void onDeclineJoinRequest(String room, String username) {}
        default void onRequestRoomMembers(String room) {}
        default void onRequestRoomJoinRequests(String room) {}
    }

    // ── Mo hinh du lieu 1 bong tin nhan (dung chung cho phong + rieng, ho tro thu hoi) ──
    private static class Bubble {
        long    id;
        String  sender;
        String  content;     // van ban (rong neu la FILE)
        String  fileName;    // null neu la TEXT
        String  base64;      // null neu la TEXT
        boolean own;
        long    epochMillis;
        boolean recalled;
        boolean system;
    }

    // ── Fields chung ───────────────────────────────────────────────────────────
    private final UserListPanel userListPanel;
    private final JLabel        lblPing;
    private final JLabel        lblConnInfo;
    private final JLabel        lblOnlineStatus;
    private final DefaultListModel<String> roomModel = new DefaultListModel<>();
    private final JList<String> roomList;
    private       String        myUsername  = "";
    private       String        currentRoom = Protocol.DEFAULT_ROOM;
    private       ChatListener  chatListener;
    private       long          pingSentAt  = 0;

    // Tra cuu bubble theo id (dung chung ca phong lan rieng) — phuc vu click file/thu hoi
    private final Map<Long, Bubble> bubbleIndex = new HashMap<>();

    // ── Tab 1: Phong chat ─────────────────────────────────────────────────────
    private final JTextPane roomPane;
    private final JTextArea roomInput;
    private final JLabel    lblTyping;
    private final JLabel    lblRoomName;
    private final List<Bubble> roomBubbles = new ArrayList<>();
    private String roomHtmlBody = "";

    // ── Tab 2: Tin nhan rieng ────────────────────────────────────────────────
    private final DefaultListModel<String> peerModel = new DefaultListModel<>();
    private final JList<String>            peerList;
    private final JTextPane                privatePane;
    private final JTextArea                privateInput;
    private final JLabel                   lblPrivatePeer;
    private final Map<String, List<Bubble>> privateConversations = new LinkedHashMap<>();
    private String  currentPeer = null;
    private String  privateHtmlBody = "";

    // ── Constructor ────────────────────────────────────────────────────────────
    public ChatPanel() {
        setLayout(new BorderLayout());
        setBackground(DarkTheme.BG);

        userListPanel   = new UserListPanel();
        lblPing         = buildPingLabel();
        lblConnInfo     = new JLabel("Chưa kết nối");
        lblTyping       = new JLabel(" ");
        lblOnlineStatus = new JLabel("● Đang hoạt động");
        lblRoomName     = new JLabel(Protocol.DEFAULT_ROOM);
        lblPrivatePeer  = new JLabel("Chọn một người để nhắn riêng");
        roomList        = buildRoomList();
        peerList        = buildPeerList();

        roomPane    = buildChatPane();
        roomInput   = buildInputArea(() -> sendRoomMessage(), true);
        privatePane = buildChatPane();
        privateInput= buildInputArea(() -> sendPrivateMessage(), false);

        styleLabels();

        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(DarkTheme.FONT_BODY_B);
        tabs.setBackground(DarkTheme.BG2);
        tabs.setForeground(DarkTheme.TEXT);
        tabs.addTab("💬 Phòng chat",   buildRoomTab());
        tabs.addTab("🔒 Tin nhắn riêng", buildPrivateTab());

        add(tabs, BorderLayout.CENTER);

        userListPanel.setUserClickListener(username -> {
            if (!username.equals(myUsername)) {
                openPrivateConversation(username);
                tabs.setSelectedIndex(1);
            }
        });
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  TAB 1 — PHÒNG CHAT
    // ══════════════════════════════════════════════════════════════════════════

    private JPanel buildRoomTab() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(DarkTheme.BG);
        root.add(buildRoomHeader(), BorderLayout.NORTH);
        root.add(buildRoomCenter(), BorderLayout.CENTER);
        root.add(buildRoomInputBar(), BorderLayout.SOUTH);
        return root;
    }

    private JPanel buildRoomHeader() {
        JPanel header = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setColor(DarkTheme.BG2);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setColor(DarkTheme.BORDER);
                g2.fillRect(0, getHeight() - 1, getWidth(), 1);
                g2.dispose();
            }
        };
        header.setOpaque(false);
        header.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));
        header.setPreferredSize(new Dimension(0, 60));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        left.setOpaque(false);

        JPanel roomAvatar = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setPaint(new GradientPaint(0, 0, DarkTheme.PRIMARY, 36, 36, DarkTheme.ACCENT));
                g2.fillOval(0, 0, 36, 36);
                g2.setColor(Color.WHITE);
                g2.setFont(new Font("Segoe UI", Font.BOLD, 16));
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString("#", (36 - fm.stringWidth("#")) / 2,
                              (36 + fm.getAscent() - fm.getDescent()) / 2);
                g2.dispose();
            }
            @Override public Dimension getPreferredSize() { return new Dimension(36, 36); }
        };
        roomAvatar.setOpaque(false);

        lblRoomName.setFont(new Font("Segoe UI", Font.BOLD, 15));
        lblRoomName.setForeground(DarkTheme.TEXT);

        JPanel roomInfo = new JPanel();
        roomInfo.setLayout(new BoxLayout(roomInfo, BoxLayout.Y_AXIS));
        roomInfo.setOpaque(false);
        roomInfo.add(lblRoomName);
        roomInfo.add(lblOnlineStatus);

        left.add(roomAvatar);
        left.add(roomInfo);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);
        lblConnInfo.setFont(DarkTheme.FONT_MONO);
        lblConnInfo.setForeground(DarkTheme.TEXT3);
        right.add(lblConnInfo);
        right.add(buildSeparator());
        right.add(lblPing);
        right.add(buildSeparator());

        JButton btnSearch = DarkTheme.makeIconButton("🔍 Tìm kiếm", "Tìm tin nhắn trong phòng");
        btnSearch.addActionListener(e -> openSearchDialog());
        right.add(btnSearch);

        JButton btnMembers = DarkTheme.makeIconButton("👥 Thành viên", "Quản lý thành viên phòng");
        btnMembers.addActionListener(e -> openMembersDialog());
        right.add(btnMembers);

        JButton btnDisc = DarkTheme.makeIconButton("✕ Ngắt kết nối", "Ngắt kết nối");
        btnDisc.setForeground(DarkTheme.DANGER);
        btnDisc.addActionListener(e -> {
            int r = JOptionPane.showConfirmDialog(this,
                "Bạn có muốn ngắt kết nối không?", "Xác nhận",
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (r == JOptionPane.YES_OPTION && chatListener != null)
                chatListener.onDisconnect();
        });
        right.add(btnDisc);

        header.add(left,  BorderLayout.WEST);
        header.add(right, BorderLayout.EAST);
        return header;
    }

    private JPanel buildRoomCenter() {
        JPanel center = new JPanel(new BorderLayout());
        center.setBackground(DarkTheme.BG);

        JPanel sidebar = new JPanel(new BorderLayout());
        sidebar.setPreferredSize(new Dimension(240, 0));

        JPanel roomPanel = new JPanel(new BorderLayout());
        roomPanel.setBackground(DarkTheme.BG2);
        JLabel lblRooms = new JLabel("📋 DANH SÁCH PHÒNG");
        lblRooms.setForeground(DarkTheme.TEXT3);
        lblRooms.setBorder(BorderFactory.createEmptyBorder(10, 12, 5, 12));
        roomPanel.add(lblRooms, BorderLayout.NORTH);
        roomPanel.add(DarkTheme.makeScrollPane(roomList), BorderLayout.CENTER);

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, roomPanel, userListPanel);
        splitPane.setDividerLocation(220);
        splitPane.setBorder(null);

        sidebar.add(splitPane, BorderLayout.CENTER);

        JScrollPane chatScroll = DarkTheme.makeScrollPane(roomPane);
        JPanel chatArea = new JPanel(new BorderLayout());
        chatArea.setBackground(DarkTheme.BG);
        chatArea.add(chatScroll, BorderLayout.CENTER);
        lblTyping.setBorder(BorderFactory.createEmptyBorder(4, 16, 4, 0));
        chatArea.add(lblTyping, BorderLayout.SOUTH);

        center.add(sidebar, BorderLayout.WEST);
        center.add(chatArea, BorderLayout.CENTER);
        return center;
    }

    private JPanel buildRoomInputBar() {
        return buildGenericInputBar(roomInput, this::sendRoomMessage, true);
    }

    private void sendRoomMessage() {
        String text = roomInput.getText().trim();
        if (text.isEmpty()) return;
        if (chatListener != null) {
            chatListener.onSendMessage(text);
            chatListener.onTyping(false);
        }
        roomInput.setText("");
        roomInput.requestFocus();
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  TAB 2 — TIN NHẮN RIÊNG
    // ══════════════════════════════════════════════════════════════════════════

    private JPanel buildPrivateTab() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(DarkTheme.BG);

        JPanel peerPanel = new JPanel(new BorderLayout());
        peerPanel.setPreferredSize(new Dimension(220, 0));
        peerPanel.setBackground(DarkTheme.BG2);
        JLabel lblPeers = new JLabel("🔒 HỘI THOẠI RIÊNG");
        lblPeers.setForeground(DarkTheme.TEXT3);
        lblPeers.setBorder(BorderFactory.createEmptyBorder(10, 12, 5, 12));
        peerPanel.add(lblPeers, BorderLayout.NORTH);
        peerPanel.add(DarkTheme.makeScrollPane(peerList), BorderLayout.CENTER);

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));
        lblPrivatePeer.setFont(new Font("Segoe UI", Font.BOLD, 15));
        lblPrivatePeer.setForeground(DarkTheme.TEXT);
        header.add(lblPrivatePeer, BorderLayout.WEST);

        JScrollPane chatScroll = DarkTheme.makeScrollPane(privatePane);
        JPanel chatArea = new JPanel(new BorderLayout());
        chatArea.setBackground(DarkTheme.BG);
        chatArea.add(header, BorderLayout.NORTH);
        chatArea.add(chatScroll, BorderLayout.CENTER);
        chatArea.add(buildGenericInputBar(privateInput, this::sendPrivateMessage, false), BorderLayout.SOUTH);

        root.add(peerPanel, BorderLayout.WEST);
        root.add(chatArea,  BorderLayout.CENTER);
        return root;
    }

    private JList<String> buildPeerList() {
        JList<String> list = new JList<>(peerModel);
        list.setBackground(DarkTheme.BG2);
        list.setForeground(DarkTheme.TEXT);
        list.setFixedCellHeight(38);
        list.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && list.getSelectedValue() != null) {
                showPrivateConversation(list.getSelectedValue());
            }
        });
        return list;
    }

    /** Mo (hoac tao moi) 1 hoi thoai rieng voi 'peer' va chuyen sang xem no. */
    public void openPrivateConversation(String peer) {
        SwingUtilities.invokeLater(() -> {
            if (!privateConversations.containsKey(peer)) {
                privateConversations.put(peer, new ArrayList<>());
                peerModel.addElement(peer);
            }
            peerList.setSelectedValue(peer, true);
            showPrivateConversation(peer);
        });
    }

    private void showPrivateConversation(String peer) {
        currentPeer = peer;
        lblPrivatePeer.setText("🔒 " + peer);
        privateHtmlBody = "";
        List<Bubble> bubbles = privateConversations.getOrDefault(peer, new ArrayList<>());
        for (Bubble b : bubbles) privateHtmlBody += renderBubble(b);
        privatePane.setText(buildHtmlBase(privateHtmlBody));
        scrollToBottom(privatePane);
    }

    private void sendPrivateMessage() {
        String text = privateInput.getText().trim();
        if (text.isEmpty() || currentPeer == null) return;
        if (chatListener != null) chatListener.onSendPrivateMessage(currentPeer, text);
        privateInput.setText("");
        privateInput.requestFocus();
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  NHẬN FRAME TỪ SERVER (được gọi từ MessageReceiver)
    // ══════════════════════════════════════════════════════════════════════════

    /** Tin nhan van ban trong phong (Phase D: co id + epochMillis). */
    public void appendMessage(long id, String sender, String content, boolean isOwn, long epochMillis) {
        Bubble b = new Bubble();
        b.id = id; b.sender = sender; b.content = content; b.own = isOwn; b.epochMillis = epochMillis;
        SwingUtilities.invokeLater(() -> {
            roomBubbles.add(b);
            bubbleIndex.put(id, b);
            roomHtmlBody += renderBubble(b);
            roomPane.setText(buildHtmlBase(roomHtmlBody));
            scrollToBottom(roomPane);
        });
    }

    /** Tin nhan file/anh trong phong. */
    public void appendFileMessage(long id, String sender, String fileName, String base64, boolean isOwn, long epochMillis) {
        Bubble b = new Bubble();
        b.id = id; b.sender = sender; b.fileName = fileName; b.base64 = base64;
        b.own = isOwn; b.epochMillis = epochMillis;
        SwingUtilities.invokeLater(() -> {
            roomBubbles.add(b);
            bubbleIndex.put(id, b);
            roomHtmlBody += renderBubble(b);
            roomPane.setText(buildHtmlBase(roomHtmlBody));
            scrollToBottom(roomPane);
        });
    }

    /** Tin nhan rieng dang van ban (Phase D: co id + epochMillis). */
    public void appendPrivateMessage(long id, String from, String to, String content, boolean isOwn, long epochMillis) {
        String peer = isOwn ? to : from;
        Bubble b = new Bubble();
        b.id = id; b.sender = from; b.content = content; b.own = isOwn; b.epochMillis = epochMillis;
        SwingUtilities.invokeLater(() -> {
            List<Bubble> list = privateConversations.computeIfAbsent(peer, k -> {
                peerModel.addElement(peer);
                return new ArrayList<>();
            });
            list.add(b);
            bubbleIndex.put(id, b);
            if (peer.equals(currentPeer)) {
                privateHtmlBody += renderBubble(b);
                privatePane.setText(buildHtmlBase(privateHtmlBody));
                scrollToBottom(privatePane);
            }
        });
    }

    /** Tin nhan file/anh rieng. */
    public void appendPrivateFileMessage(long id, String from, String to, String fileName, String base64,
                                          boolean isOwn, long epochMillis) {
        String peer = isOwn ? to : from;
        Bubble b = new Bubble();
        b.id = id; b.sender = from; b.fileName = fileName; b.base64 = base64;
        b.own = isOwn; b.epochMillis = epochMillis;
        SwingUtilities.invokeLater(() -> {
            List<Bubble> list = privateConversations.computeIfAbsent(peer, k -> {
                peerModel.addElement(peer);
                return new ArrayList<>();
            });
            list.add(b);
            bubbleIndex.put(id, b);
            if (peer.equals(currentPeer)) {
                privateHtmlBody += renderBubble(b);
                privatePane.setText(buildHtmlBase(privateHtmlBody));
                scrollToBottom(privatePane);
            }
        });
    }

    /** Danh dau 1 tin nhan (phong hoac rieng) la da thu hoi va ve lai toan bo khung chua no. */
    public void markRecalled(long id) {
        SwingUtilities.invokeLater(() -> {
            Bubble b = bubbleIndex.get(id);
            if (b == null) return;
            b.recalled = true;
            if (roomBubbles.contains(b)) {
                rerenderRoom();
            } else {
                for (Map.Entry<String, List<Bubble>> e : privateConversations.entrySet()) {
                    if (e.getValue().contains(b)) {
                        if (e.getKey().equals(currentPeer)) rerenderPrivate();
                        break;
                    }
                }
            }
        });
    }

    private void rerenderRoom() {
        roomHtmlBody = "";
        for (Bubble b : roomBubbles) roomHtmlBody += renderBubble(b);
        roomPane.setText(buildHtmlBase(roomHtmlBody));
    }

    private void rerenderPrivate() {
        if (currentPeer == null) return;
        privateHtmlBody = "";
        for (Bubble b : privateConversations.getOrDefault(currentPeer, new ArrayList<>()))
            privateHtmlBody += renderBubble(b);
        privatePane.setText(buildHtmlBase(privateHtmlBody));
    }

    public void appendSystemMessage(String text) {
        Bubble b = new Bubble();
        b.system = true; b.content = text; b.epochMillis = System.currentTimeMillis();
        SwingUtilities.invokeLater(() -> {
            roomBubbles.add(b);
            roomHtmlBody += renderBubble(b);
            roomPane.setText(buildHtmlBase(roomHtmlBody));
            scrollToBottom(roomPane);
        });
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  RENDER BONG TIN NHAN (HTML) — dung chung cho phong + rieng
    // ══════════════════════════════════════════════════════════════════════════

    private String renderBubble(Bubble b) {
        if (b.system) {
            return "<div style='text-align:center;margin:12px 0;'>"
                 + "<span style='background:#1a1e3570;color:#94a3b8;border-radius:10px;padding:5px 14px;"
                 + "font-size:12px;border:1px solid #2a3060;'>ℹ️ " + escHtml(b.content) + "</span></div>";
        }

        String time  = Instant.ofEpochMilli(b.epochMillis).atZone(ZoneId.systemDefault()).toLocalDateTime().format(TIME_FMT);
        String align = b.own ? "right" : "left";
        String colorHex = colorToHex(DarkTheme.avatarColor(b.sender));
        String avatarHtml = "<table border='0' cellpadding='0' cellspacing='0'><tr>"
                + "<td bgcolor='" + colorHex + "' width='26' height='26' align='center'>"
                + "<font color='#ffffff' size='3'><b>" + Character.toUpperCase(b.sender.charAt(0))
                + "</b></font></td></tr></table>";

        StringBuilder sb = new StringBuilder();
        sb.append("<table width='100%' border='0' cellpadding='0' cellspacing='0'><tr><td align='").append(align).append("'>")
          .append("<font color='#64748b' size='2'>").append(b.own ? "Bạn" : escHtml(b.sender))
          .append(" &nbsp;&middot;&nbsp; ").append(time).append("</font></td></tr></table>");

        if (b.recalled) {
            sb.append("<table width='100%' border='0' cellpadding='0' cellspacing='0'><tr><td align='").append(align).append("'>")
              .append("<table border='0' cellpadding='0' cellspacing='4'><tr>");
            if (!b.own) sb.append("<td valign='bottom'>").append(avatarHtml).append("</td>");
            sb.append("<td bgcolor='#232842' style='padding:9px 14px;'>")
              .append("<font color='#64748b' size='3'><i>🚫 Tin nhắn đã được thu hồi</i></font></td>");
            if (b.own) sb.append("<td valign='bottom'>").append(avatarHtml).append("</td>");
            sb.append("</tr></table></td></tr></table>");
            return sb.toString();
        }

        String bgColor  = b.own ? "#7c3aed" : "#1a1e35";
        String txtColor = b.own ? "#ffffff" : "#e2e8f0";

        sb.append("<table width='100%' border='0' cellpadding='0' cellspacing='0'><tr><td align='").append(align).append("'>");
        sb.append("<table border='0' cellpadding='0' cellspacing='4'><tr>");
        if (!b.own) sb.append("<td valign='bottom'>").append(avatarHtml).append("</td>");
        sb.append("<td bgcolor='").append(bgColor).append("' style='padding:9px 14px;'>")
          .append("<font color='").append(txtColor).append("' size='3'>");

        if (b.fileName != null) {
            String sizeLabel = formatFileSize(b.base64);
            sb.append("📎 <a href='file:").append(b.id).append("' style='color:").append(txtColor).append(";'>")
              .append(escHtml(b.fileName)).append("</a> <font size='2'>(").append(sizeLabel).append(")</font>");
        } else {
            sb.append(escHtml(b.content).replace("\n", "<br/>"));
        }
        sb.append("</font></td>");
        if (b.own) sb.append("<td valign='bottom'>").append(avatarHtml).append("</td>");
        sb.append("</tr></table>");

        if (b.own) {
            sb.append("<table width='100%' border='0' cellpadding='0' cellspacing='0'><tr><td align='").append(align).append("'>")
              .append("<a href='recall:").append(b.id).append("' style='color:#64748b;font-size:11px;'>🗑 Thu hồi</a>")
              .append("</td></tr></table>");
        }
        sb.append("</td></tr></table>");
        return sb.toString();
    }

    private String formatFileSize(String base64) {
        long bytes = (long) (base64.length() * 0.75);
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  HYPERLINK: click file de luu, click "Thu hoi" de goi listener
    // ══════════════════════════════════════════════════════════════════════════

    private void handleHyperlink(HyperlinkEvent e) {
        if (e.getEventType() != HyperlinkEvent.EventType.ACTIVATED) return;
        String desc = e.getDescription();
        if (desc == null) return;

        if (desc.startsWith("file:")) {
            long id = Long.parseLong(desc.substring(5));
            Bubble b = bubbleIndex.get(id);
            if (b == null || b.base64 == null) return;
            saveFileToDisk(b.fileName, b.base64);
        } else if (desc.startsWith("recall:")) {
            long id = Long.parseLong(desc.substring(7));
            if (chatListener != null) chatListener.onRecallMessage(id);
        }
    }

    private void saveFileToDisk(String fileName, String base64) {
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File(fileName));
        int result = chooser.showSaveDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) return;
        try {
            byte[] data = Base64.getDecoder().decode(base64);
            Files.write(chooser.getSelectedFile().toPath(), data);
            JOptionPane.showMessageDialog(this, "Đã lưu file: " + chooser.getSelectedFile().getName());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Lỗi lưu file: " + ex.getMessage(),
                    "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  TÌM KIẾM (tính năng 6)
    // ══════════════════════════════════════════════════════════════════════════

    private JTextArea searchResultsArea;
    private JLabel    searchStatusLabel;

    private void openSearchDialog() {
        JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(this), "Tìm kiếm trong phòng: " + currentRoom);
        dialog.setSize(480, 420);
        dialog.setLocationRelativeTo(this);
        dialog.getContentPane().setBackground(DarkTheme.BG);
        dialog.setLayout(new BorderLayout(8, 8));

        JTextField field = new JTextField();
        field.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        JButton btnGo = new JButton("Tìm");

        JPanel top = new JPanel(new BorderLayout(6, 0));
        top.setBorder(BorderFactory.createEmptyBorder(10, 10, 6, 10));
        top.add(field, BorderLayout.CENTER);
        top.add(btnGo, BorderLayout.EAST);

        searchResultsArea = new JTextArea();
        searchResultsArea.setEditable(false);
        searchResultsArea.setLineWrap(true);
        searchResultsArea.setWrapStyleWord(true);
        searchResultsArea.setBackground(DarkTheme.SURFACE);
        searchResultsArea.setForeground(DarkTheme.TEXT);
        searchResultsArea.setFont(DarkTheme.FONT_BODY);

        searchStatusLabel = new JLabel(" ");
        searchStatusLabel.setForeground(DarkTheme.TEXT3);
        searchStatusLabel.setBorder(BorderFactory.createEmptyBorder(4, 10, 8, 10));

        Runnable doSearch = () -> {
            String kw = field.getText().trim();
            if (kw.isEmpty()) return;
            searchResultsArea.setText("");
            searchStatusLabel.setText("Đang tìm...");
            if (chatListener != null) chatListener.onSearch(kw);
        };
        btnGo.addActionListener(e -> doSearch.run());
        field.addActionListener(e -> doSearch.run());

        dialog.add(top, BorderLayout.NORTH);
        dialog.add(DarkTheme.makeScrollPane(searchResultsArea), BorderLayout.CENTER);
        dialog.add(searchStatusLabel, BorderLayout.SOUTH);
        dialog.setVisible(true);
    }

    /** Duoc goi tu MessageReceiver moi khi nhan 1 SEARCH_RESULT. */
    public void addSearchResult(String sender, String content, long epochMillis) {
        SwingUtilities.invokeLater(() -> {
            if (searchResultsArea == null) return;
            String time = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDateTime().format(TIME_FMT);
            searchResultsArea.append("[" + time + "] " + sender + ": " + content + "\n\n");
        });
    }

    /** Duoc goi tu MessageReceiver khi nhan SEARCH_DONE. */
    public void searchDone(int count) {
        SwingUtilities.invokeLater(() -> {
            if (searchStatusLabel != null) searchStatusLabel.setText("Tìm thấy " + count + " kết quả");
        });
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  QUẢN LÝ THÀNH VIÊN (tính năng 1 & 2)
    // ══════════════════════════════════════════════════════════════════════════

    private DefaultListModel<String> membersModel;
    private DefaultListModel<String> joinRequestModel;

    private void openMembersDialog() {
        JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(this), "Quản lý phòng: " + currentRoom);
        dialog.setSize(500, 520);
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new BorderLayout(8, 8));

        membersModel = new DefaultListModel<>();
        JList<String> memberList = new JList<>(membersModel);

        joinRequestModel = new DefaultListModel<>();
        JList<String> requestList = new JList<>(joinRequestModel);

        JButton btnRemove = new JButton("Xóa thành viên đã chọn");
        btnRemove.addActionListener(e -> {
            String sel = memberList.getSelectedValue();
            if (sel != null && chatListener != null) chatListener.onRemoveMember(currentRoom, sel);
        });

        JButton btnApprove = new JButton("Duyệt yêu cầu");
        btnApprove.addActionListener(e -> {
            String sel = requestList.getSelectedValue();
            if (sel != null && chatListener != null) {
                chatListener.onAddMember(currentRoom, sel);
                joinRequestModel.removeElement(sel);
            }
        });

        JButton btnDecline = new JButton("Từ chối yêu cầu");
        btnDecline.addActionListener(e -> {
            String sel = requestList.getSelectedValue();
            if (sel != null) {
                joinRequestModel.removeElement(sel);
                if (chatListener != null) chatListener.onDeclineJoinRequest(currentRoom, sel);
            }
        });

        JTextField addField = new JTextField();
        JButton btnAdd = new JButton("Thêm");
        btnAdd.addActionListener(e -> {
            String u = addField.getText().trim();
            if (!u.isEmpty() && chatListener != null) {
                chatListener.onAddMember(currentRoom, u);
                addField.setText("");
            }
        });

        JPanel addRow = new JPanel(new BorderLayout(6, 0));
        addRow.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        addRow.add(addField, BorderLayout.CENTER);
        addRow.add(btnAdd, BorderLayout.EAST);

        JPanel requestsPanel = new JPanel(new BorderLayout(6, 6));
        requestsPanel.setBorder(BorderFactory.createTitledBorder("Yêu cầu chờ duyệt"));
        requestsPanel.add(DarkTheme.makeScrollPane(requestList), BorderLayout.CENTER);
        JPanel requestButtons = new JPanel(new GridLayout(1, 2, 6, 0));
        requestButtons.add(btnApprove);
        requestButtons.add(btnDecline);
        requestsPanel.add(requestButtons, BorderLayout.SOUTH);

        JPanel membersPanel = new JPanel(new BorderLayout(6, 6));
        membersPanel.setBorder(BorderFactory.createTitledBorder("Thành viên hiện tại"));
        membersPanel.add(DarkTheme.makeScrollPane(memberList), BorderLayout.CENTER);
        membersPanel.add(btnRemove, BorderLayout.SOUTH);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, membersPanel, requestsPanel);
        split.setDividerLocation(240);
        split.setResizeWeight(0.5);

        JLabel note = new JLabel("<html><i>Chỉ người tạo phòng mới có thể thêm/xóa thành viên.<br/>"
                + "Phòng mặc định \"General\" không có người quản lý.</i></html>");
        note.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        note.setForeground(DarkTheme.TEXT3);
        note.setFont(DarkTheme.FONT_SMALL);

        dialog.add(addRow, BorderLayout.NORTH);
        dialog.add(split, BorderLayout.CENTER);
        dialog.add(note, BorderLayout.SOUTH);

        dialog.setVisible(true);
        if (chatListener != null) {
            chatListener.onRequestRoomMembers(currentRoom);
            chatListener.onRequestRoomJoinRequests(currentRoom);
        }
    }

    /** Duoc goi tu MessageReceiver khi nhan ROOM_MEMBERS. */
    public void setRoomMembers(String room, List<String> members) {
        SwingUtilities.invokeLater(() -> {
            if (membersModel == null || !room.equals(currentRoom)) return;
            membersModel.clear();
            for (String m : members) membersModel.addElement(m);
        });
    }

    public void setRoomJoinRequests(String room, List<String> requests) {
        SwingUtilities.invokeLater(() -> {
            if (joinRequestModel == null || !room.equals(currentRoom)) return;
            joinRequestModel.clear();
            for (String r : requests) joinRequestModel.addElement(r);
        });
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GỬI FILE (tính năng 7)
    // ══════════════════════════════════════════════════════════════════════════

    private void pickAndSendFile(boolean isPrivate) {
        JFileChooser chooser = new JFileChooser();
        int result = chooser.showOpenDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) return;
        File file = chooser.getSelectedFile();
        if (file.length() > MAX_FILE_BYTES) {
            JOptionPane.showMessageDialog(this, "File quá lớn (tối đa 5MB).",
                    "Lỗi", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (chatListener == null) return;
        if (isPrivate) {
            if (currentPeer == null) {
                JOptionPane.showMessageDialog(this, "Hãy chọn một người để gửi file riêng.");
                return;
            }
            chatListener.onSendFilePrivate(currentPeer, file);
        } else {
            chatListener.onSendFileGroup(file);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Public API (giu nguyen tu ban goc + phan mo rong Phase D)
    // ══════════════════════════════════════════════════════════════════════════

    public void setMyUsername(String name) {
        this.myUsername = name;
        userListPanel.setMyUsername(name);
    }

    public void setConnInfo(String info) {
        SwingUtilities.invokeLater(() -> lblConnInfo.setText(info));
    }

    public void markPingSent() { pingSentAt = System.currentTimeMillis(); }

    public void onPong() {
        if (pingSentAt == 0) return;
        long ms = System.currentTimeMillis() - pingSentAt;
        pingSentAt = 0;
        SwingUtilities.invokeLater(() -> {
            lblPing.setText("⚡ " + ms + " ms");
            lblPing.setForeground(ms < 50 ? DarkTheme.ONLINE :
                                  ms < 150 ? DarkTheme.WARNING : DarkTheme.DANGER);
        });
    }

    public void setOnlineUsersWithTime(Map<String, Long> userEpochMap) {
        userListPanel.setOnlineUsersWithTime(userEpochMap);
        SwingUtilities.invokeLater(() ->
            lblOnlineStatus.setText("● " + userEpochMap.size() + " thành viên đang online"));
    }

    public void addOnlineUser(String name, long epochJoin) {
        userListPanel.addOnlineUser(name, epochJoin);
        SwingUtilities.invokeLater(() ->
            lblOnlineStatus.setText("● " + userListPanel.getOnlineCount() + " thành viên đang online"));
    }

    public void removeOnlineUser(String name, long epochLeft) {
        userListPanel.removeOnlineUser(name, epochLeft);
        SwingUtilities.invokeLater(() ->
            lblOnlineStatus.setText("● " + userListPanel.getOnlineCount() + " thành viên đang online"));
    }

    public void showTyping(String text) {
        SwingUtilities.invokeLater(() -> lblTyping.setText(text));
    }

    public void clearChat() {
        roomHtmlBody = "";
        roomBubbles.clear();
        roomPane.setText(buildHtmlBase(""));
    }

    public UserListPanel getUserListPanel() { return userListPanel; }
    public void setChatListener(ChatListener l) { this.chatListener = l; }
    public void requestInputFocus() { roomInput.requestFocus(); }

    // ── Helpers ─────────────────────────────────────────────────────────────────
    private void styleLabels() {
        lblConnInfo.setFont(DarkTheme.FONT_MONO);
        lblConnInfo.setForeground(DarkTheme.TEXT3);
        lblTyping.setFont(DarkTheme.FONT_SMALL);
        lblTyping.setForeground(DarkTheme.TEXT3);
        lblOnlineStatus.setFont(DarkTheme.FONT_SMALL);
        lblOnlineStatus.setForeground(DarkTheme.ONLINE);
        lblPrivatePeer.setForeground(DarkTheme.TEXT);
    }

    private JLabel buildPingLabel() {
        JLabel l = new JLabel("⚡ --ms");
        l.setFont(DarkTheme.FONT_MONO);
        l.setForeground(DarkTheme.ONLINE);
        l.setBorder(new DarkTheme.RoundBorder(new Color(0x06b6d4), 10, 3));
        return l;
    }

    private JSeparator buildSeparator() {
        JSeparator sep = new JSeparator(SwingConstants.VERTICAL);
        sep.setPreferredSize(new Dimension(1, 16));
        sep.setForeground(DarkTheme.BORDER2);
        return sep;
    }

    private JList<String> buildRoomList() {
        JList<String> list = new JList<>(roomModel);
        list.setBackground(DarkTheme.BG2);
        list.setForeground(DarkTheme.TEXT);
        list.setFixedCellHeight(42);

        list.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    String room = list.getSelectedValue();
                    if (room != null && !room.equals(currentRoom) && chatListener != null) {
                        chatListener.onJoinRoom(room);
                    }
                }
            }
        });

        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value,
                    int index, boolean isSelected, boolean cellHasFocus) {
                Component c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value.equals(currentRoom)) {
                    setForeground(DarkTheme.PRIMARY);
                    setFont(DarkTheme.FONT_BOLD);
                }
                return c;
            }
        });
        return list;
    }

    public void setCurrentRoom(String roomName) {
        this.currentRoom = roomName;
        SwingUtilities.invokeLater(() -> {
            lblRoomName.setText(roomName);
            roomList.repaint();
        });
    }

    public String getCurrentRoom() {
        return currentRoom;
    }

    public void updateRoomList(List<String> rooms) {
        SwingUtilities.invokeLater(() -> {
            roomModel.clear();
            for (String room : rooms) roomModel.addElement(room);
        });
    }

    private JTextPane buildChatPane() {
        JTextPane pane = new JTextPane();
        pane.setEditable(false);
        pane.setBackground(DarkTheme.BG);
        pane.setForeground(DarkTheme.TEXT);
        pane.setFont(DarkTheme.FONT_BODY);
        pane.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));
        pane.setContentType("text/html");
        pane.setText(buildHtmlBase(""));
        pane.addHyperlinkListener(this::handleHyperlink);
        return pane;
    }

    private JTextArea buildInputArea(Runnable onSend, boolean isRoom) {
        JTextArea area = new JTextArea(2, 20);
        area.setFont(DarkTheme.FONT_BODY);
        area.setBackground(DarkTheme.SURFACE);
        area.setForeground(DarkTheme.TEXT);
        area.setCaretColor(DarkTheme.PRIMARY);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setBorder(BorderFactory.createEmptyBorder(4, 2, 4, 2));
        area.setOpaque(false);
        area.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER && !e.isShiftDown()) {
                    e.consume();
                    onSend.run();
                }
            }
            @Override public void keyTyped(KeyEvent e) {
                if (isRoom && chatListener != null) chatListener.onTyping(true);
            }
        });
        return area;
    }

    private String buildHtmlBase(String body) {
        return "<html><body style='"
             + "font-family:Segoe UI,sans-serif;font-size:13px;color:#e2e8f0;background:#0d0f1a;"
             + "margin:0;padding:0;'>" + body + "</body></html>";
    }

    private void scrollToBottom(JTextPane pane) {
        SwingUtilities.invokeLater(() -> {
            JScrollPane sp = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, pane);
            if (sp != null) sp.getVerticalScrollBar().setValue(sp.getVerticalScrollBar().getMaximum());
        });
    }

    /** Thanh nhap chung (emoji + [+] tao nhom hoac [📎] gui file + nut gui) dung cho ca 2 tab. */
    private JPanel buildGenericInputBar(JTextArea inputArea, Runnable onSend, boolean isRoom) {
        JPanel bar = new JPanel(new BorderLayout(8, 0)) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setColor(DarkTheme.BG2);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setColor(DarkTheme.BORDER);
                g2.fillRect(0, 0, getWidth(), 1);
                g2.dispose();
            }
        };
        bar.setOpaque(false);
        bar.setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));

        JPanel emojiBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 0));
        emojiBar.setOpaque(false);
        for (String em : new String[]{"😀","👍","❤️","🔥","🎉","😂","🚀","✅"}) {
            JButton eb = new JButton(em);
            eb.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 16));
            eb.setBorderPainted(false);
            eb.setContentAreaFilled(false);
            eb.setFocusPainted(false);
            eb.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            eb.addActionListener(e -> {
                inputArea.insert(em, inputArea.getCaretPosition());
                inputArea.requestFocus();
            });
            emojiBar.add(eb);
        }

        JPanel inputWrap = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(DarkTheme.SURFACE);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                g2.setColor(DarkTheme.BORDER2);
                g2.setStroke(new BasicStroke(1.2f));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
                g2.dispose();
            }
        };
        inputWrap.setOpaque(false);
        inputWrap.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 8));
        inputWrap.add(inputArea, BorderLayout.CENTER);

        JButton sendBtn = buildRoundActionButton("▶", () -> onSend.run(), "Gửi (Enter)");
        JButton fileBtn = buildRoundActionButton("📎", () -> pickAndSendFile(!isRoom), "Gửi file/ảnh");

        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setOpaque(false);
        topBar.add(emojiBar, BorderLayout.WEST);

        JPanel rightBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        rightBtns.setOpaque(false);
        rightBtns.add(fileBtn);
        if (isRoom) {
            JButton groupBtn = buildRoundActionButton("+", () -> {
                if (chatListener != null) chatListener.onCreateGroup();
            }, "Tạo nhóm mới");
            rightBtns.add(groupBtn);
        }
        rightBtns.add(sendBtn);

        JPanel main = new JPanel(new BorderLayout(6, 0));
        main.setOpaque(false);
        main.add(inputWrap, BorderLayout.CENTER);
        main.add(rightBtns, BorderLayout.EAST);

        bar.add(topBar, BorderLayout.NORTH);
        bar.add(main,   BorderLayout.CENTER);
        return bar;
    }

    private JButton buildRoundActionButton(String symbol, Runnable action, String tooltip) {
        JButton btn = new JButton(symbol) {
            private boolean hov = false;
            {
                addMouseListener(new MouseAdapter() {
                    public void mouseEntered(MouseEvent e) { hov = true;  repaint(); }
                    public void mouseExited(MouseEvent e)  { hov = false; repaint(); }
                });
            }
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                boolean primaryStyle = symbol.equals("▶");
                if (primaryStyle) {
                    g2.setPaint(new GradientPaint(0, 0, hov ? DarkTheme.PRIMARY_DK : DarkTheme.PRIMARY,
                            getWidth(), 0, hov ? DarkTheme.ACCENT_DK : DarkTheme.ACCENT));
                } else {
                    g2.setColor(hov ? DarkTheme.SURFACE : DarkTheme.BG2);
                }
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                g2.setColor(primaryStyle ? Color.WHITE : DarkTheme.PRIMARY);
                g2.setFont(new Font("Segoe UI Emoji", Font.BOLD, symbol.equals("+") ? 20 : 16));
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(symbol, (getWidth() - fm.stringWidth(symbol)) / 2,
                        (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
                g2.dispose();
            }
            @Override protected void paintBorder(Graphics g) {}
        };
        btn.setPreferredSize(new Dimension(44, 44));
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setToolTipText(tooltip);
        btn.addActionListener(e -> action.run());
        return btn;
    }

    private static String escHtml(String s) {
        return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");
    }

    private static String colorToHex(Color c) {
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }
}
