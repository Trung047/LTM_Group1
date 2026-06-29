package UI;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * ChatPanel — Màn hình chat chính.
 *
 * Tính năng mới so với bản gốc:
 *  - appendPrivateMessage() hiển thị tin nhắn riêng nổi bật
 *  - onPong() cập nhật ping thật
 *  - Typing indicator thật (gọi từ MessageReceiver)
 *  - Sidebar UserListPanel click → điền @username vào input
 *  - Ping timer (gọi sendPing mỗi 5 giây từ ChatFrame)
 */
public class ChatPanel extends JPanel {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    // ── Callbacks ──────────────────────────────────────────────────────────────
    public interface ChatListener {
        void onSendMessage(String text);
        void onDisconnect();
        void onTyping(boolean isTyping);
        void onJoinRoom(String roomName);
        // Nguoi dung bam nut tao nhom
        default void onCreateGroup() {}
    }

    // ── Fields ─────────────────────────────────────────────────────────────────
    private final UserListPanel userListPanel;
    private final JTextPane     chatPane;
    private final JTextArea     inputArea;
    private final JLabel        lblPing;
    private final JLabel        lblConnInfo;
    private final JLabel        lblTyping;
    private final JLabel        lblOnlineStatus;
    private final JLabel        lblRoomName;       
    private final DefaultListModel<String> roomModel = new DefaultListModel<>(); 
    private final JList<String> roomList;         
    private       String        myUsername  = "";
    private       String        currentRoom = "Phòng chung";
    private       ChatListener  chatListener;
    private       String        lastSender  = "";
    private       long          pingSentAt  = 0;
    private       String        currentHtmlBody = "";

    // ── Constructor ────────────────────────────────────────────────────────────
    public ChatPanel() {
        setLayout(new BorderLayout());
        setBackground(DarkTheme.BG);

        userListPanel   = new UserListPanel();
        chatPane        = buildChatPane();
        inputArea       = buildInputArea();
        lblPing         = buildPingLabel();
        lblConnInfo     = new JLabel("Chưa kết nối");
        lblTyping       = new JLabel(" ");
        lblOnlineStatus = new JLabel("● Đang hoạt động");
        lblRoomName     = new JLabel("Phòng chung");
        roomList        = buildRoomList();

        styleLabels();

        add(buildHeader(),   BorderLayout.NORTH);
        add(buildCenter(),   BorderLayout.CENTER);
        add(buildInputBar(), BorderLayout.SOUTH);

        // Click user → điền @username vào input
        userListPanel.setUserClickListener(username -> {
            if (!username.equals(myUsername)) {
                String cur = inputArea.getText();
                if (cur.isEmpty() || cur.endsWith(" ")) {
                    inputArea.setText(cur + "@" + username + " ");
                } else {
                    inputArea.setText(cur + " @" + username + " ");
                }
                inputArea.requestFocus();
                inputArea.setCaretPosition(inputArea.getText().length());
            }
        });
    }

    // ── Header ─────────────────────────────────────────────────────────────────
    private JPanel buildHeader() {
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

        // Left
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

        // Right
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);
        lblConnInfo.setFont(DarkTheme.FONT_MONO);
        lblConnInfo.setForeground(DarkTheme.TEXT3);
        right.add(lblConnInfo);
        right.add(buildSeparator());
        right.add(lblPing);

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

    // ── Center ──────────────────────────────────────────────────────────────────
    private JPanel buildCenter() {
    JPanel center = new JPanel(new BorderLayout());
    center.setBackground(DarkTheme.BG);

    // Sidebar chứa Room List và User List
    JPanel sidebar = new JPanel(new BorderLayout());
    sidebar.setPreferredSize(new Dimension(240, 0));

    // === DANH SÁCH PHÒNG ===
    JPanel roomPanel = new JPanel(new BorderLayout());
    roomPanel.setBackground(DarkTheme.BG2);
    JLabel lblRooms = new JLabel("📋 DANH SÁCH PHÒNG");
    lblRooms.setForeground(DarkTheme.TEXT3);
    lblRooms.setBorder(BorderFactory.createEmptyBorder(10, 12, 5, 12));
    roomPanel.add(lblRooms, BorderLayout.NORTH);
    roomPanel.add(DarkTheme.makeScrollPane(roomList), BorderLayout.CENTER);

    // Ghép Room + User
    JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, roomPanel, userListPanel);
    splitPane.setDividerLocation(220);
    splitPane.setBorder(null);

    sidebar.add(splitPane, BorderLayout.CENTER);

    // Khu vực chat
    JScrollPane chatScroll = DarkTheme.makeScrollPane(chatPane);
    JPanel chatArea = new JPanel(new BorderLayout());
    chatArea.setBackground(DarkTheme.BG);
    chatArea.add(chatScroll, BorderLayout.CENTER);
    lblTyping.setBorder(BorderFactory.createEmptyBorder(4, 16, 4, 0));
    chatArea.add(lblTyping, BorderLayout.SOUTH);

    center.add(sidebar, BorderLayout.WEST);
    center.add(chatArea, BorderLayout.CENTER);
    return center;
}

    // ── Input bar ───────────────────────────────────────────────────────────────
    private JPanel buildInputBar() {
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

        // Emoji quick bar
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

        // Send button
        JButton sendBtn = new JButton() {
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
                g2.setPaint(new GradientPaint(0, 0,
                    hov ? DarkTheme.PRIMARY_DK : DarkTheme.PRIMARY,
                    getWidth(), 0,
                    hov ? DarkTheme.ACCENT_DK  : DarkTheme.ACCENT));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                g2.setColor(Color.WHITE);
                g2.setFont(new Font("Segoe UI Emoji", Font.BOLD, 17));
                FontMetrics fm = g2.getFontMetrics();
                String txt = "▶";
                g2.drawString(txt,
                    (getWidth() - fm.stringWidth(txt)) / 2,
                    (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
                g2.dispose();
            }
            @Override protected void paintBorder(Graphics g) {}
        };
        sendBtn.setPreferredSize(new Dimension(48, 48));
        sendBtn.setContentAreaFilled(false);
        sendBtn.setBorderPainted(false);
        sendBtn.setFocusPainted(false);
        sendBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        sendBtn.setToolTipText("Gửi (Enter)");
        sendBtn.addActionListener(e -> sendCurrentMessage());

        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setOpaque(false);
        topBar.add(emojiBar, BorderLayout.WEST);

        // Nut tao nhom [+]
        JButton groupBtn = new JButton("+") {
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
                g2.setColor(hov ? DarkTheme.SURFACE : DarkTheme.BG2);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                g2.setColor(DarkTheme.PRIMARY);
                g2.setFont(new Font("Segoe UI", Font.BOLD, 20));
                FontMetrics fm = g2.getFontMetrics();
                String txt = "+";
                g2.drawString(txt,
                    (getWidth() - fm.stringWidth(txt)) / 2,
                    (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
                g2.dispose();
            }
            @Override protected void paintBorder(Graphics g) {}
        };
        groupBtn.setPreferredSize(new Dimension(48, 48));
        groupBtn.setContentAreaFilled(false);
        groupBtn.setBorderPainted(false);
        groupBtn.setFocusPainted(false);
        groupBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        groupBtn.setToolTipText("Tao nhom moi");
        groupBtn.addActionListener(e -> {
            if (chatListener != null) chatListener.onCreateGroup();
        });

        JPanel rightBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        rightBtns.setOpaque(false);
        rightBtns.add(groupBtn);
        rightBtns.add(sendBtn);

        JPanel main = new JPanel(new BorderLayout(6, 0));
        main.setOpaque(false);
        main.add(inputWrap, BorderLayout.CENTER);
        main.add(rightBtns, BorderLayout.EAST);

        bar.add(topBar, BorderLayout.NORTH);
        bar.add(main,   BorderLayout.CENTER);
        return bar;
    }

    // ── Chat pane ───────────────────────────────────────────────────────────────
    private JTextPane buildChatPane() {
        JTextPane pane = new JTextPane();
        pane.setEditable(false);
        pane.setBackground(DarkTheme.BG);
        pane.setForeground(DarkTheme.TEXT);
        pane.setFont(DarkTheme.FONT_BODY);
        pane.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));
        pane.setContentType("text/html");
        pane.setText(buildHtmlBase(""));
        return pane;
    }

    private JTextArea buildInputArea() {
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
                    sendCurrentMessage();
                }
            }
            @Override public void keyTyped(KeyEvent e) {
                if (chatListener != null) chatListener.onTyping(true);
            }
        });
        return area;
    }

    // ── Bubble rendering ────────────────────────────────────────────────────────
    private String buildHtmlBase(String body) {
        return "<html><body style='"
             + "font-family:Segoe UI,sans-serif;"
             + "font-size:13px;"
             + "color:#e2e8f0;"
             + "background:#0d0f1a;"
             + "margin:0;padding:0;'>"
             + body
             + "</body></html>";
    }

    public void appendMessage(String sender, String text, boolean isOwn) {
        String time      = LocalTime.now().format(TIME_FMT);
        boolean showMeta = !sender.equals(lastSender);
        lastSender = sender;
        String colorHex  = colorToHex(DarkTheme.avatarColor(sender));

        String avatarHtml = "<div style='display:inline-block;width:28px;height:28px;"
                          + "border-radius:50%;background:" + colorHex + ";"
                          + "color:white;font-weight:bold;font-size:12px;"
                          + "text-align:center;line-height:28px;flex-shrink:0;'>"
                          + Character.toUpperCase(sender.charAt(0))
                          + "</div>";

        StringBuilder sb = new StringBuilder();
        if (showMeta) {
            sb.append("<div style='margin-top:14px;margin-bottom:2px;"
                    + "font-size:11px;color:#64748b;");
            if (isOwn) sb.append("text-align:right;padding-right:4px;");
            else       sb.append("padding-left:36px;");
            sb.append("'>");
            sb.append(isOwn ? "Bạn" : escHtml(sender));
            sb.append(" &nbsp;·&nbsp; ").append(time).append("</div>");
        }
        sb.append("<div style='display:flex;align-items:flex-end;gap:8px;");
        if (isOwn) sb.append("flex-direction:row-reverse;justify-content:flex-start;");
        sb.append("margin:2px 0;'>");
        if (!isOwn) sb.append(avatarHtml);

        String bgColor = isOwn ? "linear-gradient(135deg,#7c3aed,#06b6d4)" : "#1a1e35";
        String txtColor = isOwn ? "#ffffff" : "#e2e8f0";
        String borderR  = isOwn ? "18px 18px 4px 18px" : "18px 18px 18px 4px";

        sb.append("<div style='background:").append(bgColor)
          .append(";color:").append(txtColor)
          .append(";border-radius:").append(borderR)
          .append(";padding:9px 14px;max-width:65%;font-size:13px;line-height:1.5;word-break:break-word;'>")
          .append(escHtml(text).replace("\n", "<br/>"))
          .append("</div>");

        if (isOwn) sb.append(avatarHtml);
        sb.append("</div>");

        appendHtml(sb.toString());
    }

    /**
     * Hiển thị tin nhắn riêng tư với nền nổi bật màu tím.
     */
    public void appendPrivateMessage(String from, String to, String content, boolean isOwn) {
        lastSender = "";
        String time  = LocalTime.now().format(TIME_FMT);
        String label = isOwn ? "🔒 Bạn → " + to : "🔒 " + from + " → Bạn";
        String html  = "<div style='margin:10px 20px;"
                     + "background:linear-gradient(135deg,#4c1d95,#1e3a5f);"
                     + "border:1px solid #7c3aed;"
                     + "border-radius:12px;padding:10px 14px;'>"
                     + "<div style='font-size:11px;color:#a78bfa;margin-bottom:4px;'>"
                     + escHtml(label) + " &nbsp;·&nbsp; " + time + "</div>"
                     + "<div style='color:#e2e8f0;font-size:13px;'>"
                     + escHtml(content).replace("\n", "<br/>")
                     + "</div></div>";
        appendHtml(html);
    }

    public void appendSystemMessage(String text) {
        lastSender = "";
        String html = "<div style='text-align:center;margin:12px 0;'>"
                    + "<span style='background:#1a1e3570;color:#94a3b8;"
                    + "border-radius:10px;padding:5px 14px;"
                    + "font-size:12px;border:1px solid #2a3060;'>"
                    + "ℹ️ " + escHtml(text) + "</span></div>";
        appendHtml(html);
    }

    public void appendDivider(String label) {
        String html = "<div style='display:flex;align-items:center;margin:16px 0;gap:10px;'>"
                    + "<div style='flex:1;height:1px;background:#2a3060;'></div>"
                    + "<span style='font-size:11px;color:#64748b;'>" + escHtml(label) + "</span>"
                    + "<div style='flex:1;height:1px;background:#2a3060;'></div></div>";
        appendHtml(html);
    }

    private void appendHtml(String fragment) {
        currentHtmlBody += fragment;
        chatPane.setText(buildHtmlBase(currentHtmlBody));
        SwingUtilities.invokeLater(() -> {
            JScrollPane sp = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, chatPane);
            if (sp != null) sp.getVerticalScrollBar().setValue(sp.getVerticalScrollBar().getMaximum());
        });
    }

    // ── Public API ──────────────────────────────────────────────────────────────

    public void setMyUsername(String name) {
        this.myUsername = name;
        userListPanel.setMyUsername(name);
    }

    public void setConnInfo(String info) {
        SwingUtilities.invokeLater(() -> lblConnInfo.setText(info));
    }

    /** Ghi lại thời điểm gửi PING để tính round-trip. */
    public void markPingSent() { pingSentAt = System.currentTimeMillis(); }

    /** Gọi khi nhận PONG từ server — cập nhật label ping. */
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

    // Nhan USER_LIST tu server: Map<username, epochJoinMillis>
    public void setOnlineUsersWithTime(Map<String, Long> userEpochMap) {
        userListPanel.setOnlineUsersWithTime(userEpochMap);
        SwingUtilities.invokeLater(() ->
            lblOnlineStatus.setText("● " + userEpochMap.size() + " thành viên đang online"));
    }

    // Nhan USER_JOIN: username va epoch millis luc join
    public void addOnlineUser(String name, long epochJoin) {
        userListPanel.addOnlineUser(name, epochJoin);
        SwingUtilities.invokeLater(() ->
            lblOnlineStatus.setText("● " + userListPanel.getOnlineCount() + " thành viên đang online"));
    }

    // Nhan USER_LEFT: username va epoch millis luc ngat ket noi
    public void removeOnlineUser(String name, long epochLeft) {
        userListPanel.removeOnlineUser(name, epochLeft);
        SwingUtilities.invokeLater(() ->
            lblOnlineStatus.setText("● " + userListPanel.getOnlineCount() + " thành viên đang online"));
    }

    public void showTyping(String text) {
        SwingUtilities.invokeLater(() -> lblTyping.setText(text));
    }

    public void clearChat() {
        currentHtmlBody = "";
        lastSender      = "";
        chatPane.setText(buildHtmlBase(""));
    }

    public UserListPanel getUserListPanel() { return userListPanel; }
    public void setChatListener(ChatListener l) { this.chatListener = l; }
    public void requestInputFocus() { inputArea.requestFocus(); }

    // ── Send ────────────────────────────────────────────────────────────────────
    private void sendCurrentMessage() {
        String text = inputArea.getText().trim();
        if (text.isEmpty()) return;
        if (chatListener != null) {
            chatListener.onSendMessage(text);
            chatListener.onTyping(false);
        }
        inputArea.setText("");
        inputArea.requestFocus();
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────
    private void styleLabels() {
        lblConnInfo.setFont(DarkTheme.FONT_MONO);
        lblConnInfo.setForeground(DarkTheme.TEXT3);
        lblTyping.setFont(DarkTheme.FONT_SMALL);
        lblTyping.setForeground(DarkTheme.TEXT3);
        lblOnlineStatus.setFont(DarkTheme.FONT_SMALL);
        lblOnlineStatus.setForeground(DarkTheme.ONLINE);
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

    // ── Room List Methods ───────────────────────────────────────────────────────
private JList<String> buildRoomList() {
    JList<String> list = new JList<>(roomModel);
    list.setBackground(DarkTheme.BG2);
    list.setForeground(DarkTheme.TEXT);
    list.setFixedCellHeight(42);

    list.addMouseListener(new MouseAdapter() {
        @Override
        public void mouseClicked(MouseEvent e) {
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

    public void updateRoomList(List<String> rooms) {
    SwingUtilities.invokeLater(() -> {
        roomModel.clear();
        for (String room : rooms) {
            roomModel.addElement(room);
        }
    });
    }

    private static String escHtml(String s) {
        return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");
    }

    private static String colorToHex(Color c) {
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }
}
