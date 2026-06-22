package UI;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

public class UserListPanel extends JPanel {

    public interface UserClickListener {
        void onUserClicked(String username);
    }

    // Online: username → epoch join (Long)
    private final Map<String, Long>        onlineMap    = new LinkedHashMap<>();
    // Offline: username → epoch left (Long)
    private final Map<String, Long>        offlineMap   = new LinkedHashMap<>();

    private final DefaultListModel<String> onlineModel  = new DefaultListModel<>();
    private final DefaultListModel<String> offlineModel = new DefaultListModel<>();
    private final JList<String>            onlineList;
    private final JList<String>            offlineList;
    private final JLabel                   lblOnlineCount;
    private final JLabel                   lblOfflineCount;
    private final JTextField               searchField;
    private       String                   myUsername   = "";
    private       UserClickListener        clickListener;

    public UserListPanel() {
        setLayout(new BorderLayout());
        setBackground(DarkTheme.BG2);
        setPreferredSize(new Dimension(220, 0));
        setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, DarkTheme.BORDER));

        searchField = DarkTheme.makeTextField("🔍  Tìm kiếm...");
        searchField.setPreferredSize(new Dimension(0, 36));

        JPanel searchWrap = new JPanel(new BorderLayout());
        searchWrap.setBackground(DarkTheme.BG2);
        searchWrap.setBorder(BorderFactory.createEmptyBorder(10, 10, 6, 10));
        searchWrap.add(searchField);

        lblOnlineCount  = makeSectionBadge("0", DarkTheme.PRIMARY);
        lblOfflineCount = makeSectionBadge("0", DarkTheme.TEXT3);

        onlineList  = makeList(onlineModel,  true);
        offlineList = makeList(offlineModel, false);

        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBackground(DarkTheme.BG2);
        body.add(buildSection("● ONLINE",  lblOnlineCount,  onlineList));
        body.add(buildSection("○ OFFLINE", lblOfflineCount, offlineList));

        JScrollPane scroll = DarkTheme.makeScrollPane(body);
        scroll.setBackground(DarkTheme.BG2);
        scroll.getViewport().setBackground(DarkTheme.BG2);

        add(searchWrap, BorderLayout.NORTH);
        add(scroll,     BorderLayout.CENTER);

        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e)  { filter(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e)  { filter(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) {}
        });

        // Refresh offline timestamps moi 30 giay (hien "X phut truoc" chinh xac hon)
        javax.swing.Timer refreshTimer = new javax.swing.Timer(30_000, e -> SwingUtilities.invokeLater(this::filter));
        refreshTimer.setInitialDelay(30_000);
        refreshTimer.start();
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public void setMyUsername(String name) { this.myUsername = name; }

    /**
     * Nhan USER_LIST tu server: Map<username, epochJoinMillis>
     * Thay the toan bo danh sach online.
     */
    public void setOnlineUsersWithTime(Map<String, Long> userEpochMap) {
        SwingUtilities.invokeLater(() -> {
            onlineMap.clear();
            onlineMap.putAll(userEpochMap);
            // Xoa khoi offline neu gio online tro lai
            for (String u : userEpochMap.keySet()) offlineMap.remove(u);
            filter();
        });
    }

    /**
     * Nhan USER_JOIN: them vao online, xoa khoi offline neu co.
     * epochJoin = epoch millis luc join
     */
    public void addOnlineUser(String name, long epochJoin) {
        SwingUtilities.invokeLater(() -> {
            offlineMap.remove(name);
            onlineMap.put(name, epochJoin);
            filter();
        });
    }

    /**
     * Nhan USER_LEFT: chuyen tu online sang offline.
     * epochLeft = epoch millis luc ngat ket noi (de tinh "X phut truoc")
     */
    public void removeOnlineUser(String name, long epochLeft) {
        SwingUtilities.invokeLater(() -> {
            boolean wasOnline = onlineMap.remove(name) != null;
            if (wasOnline && !name.equals(myUsername)) {
                offlineMap.put(name, epochLeft);
            }
            filter();
        });
    }

    public void setUserClickListener(UserClickListener l) { this.clickListener = l; }

    public int getOnlineCount() { return onlineMap.size(); }

    // ── Helper: format "X phut truoc" ────────────────────────────────────────

    /**
     * Tinh khoang thoi gian tu epochMillis den bay gio, tra ve string dep.
     * Vi du: "Vua xong", "2 phut truoc", "1 gio truoc"
     */
    public static String formatTimeAgo(long epochMillis) {
        long diffMs  = System.currentTimeMillis() - epochMillis;
        long diffSec = diffMs / 1000;
        if (diffSec < 60)  return "Vừa xong";
        long diffMin = diffSec / 60;
        if (diffMin < 60)  return diffMin + " phút trước";
        long diffHr  = diffMin / 60;
        if (diffHr  < 24)  return diffHr  + " giờ trước";
        return (diffHr / 24) + " ngày trước";
    }

    // ── Filter (search) ───────────────────────────────────────────────────────

    private void filter() {
        String q = searchField.getText().trim().toLowerCase();
        onlineModel.clear();
        offlineModel.clear();
        for (String u : onlineMap.keySet())
            if (q.isEmpty() || u.toLowerCase().contains(q)) onlineModel.addElement(u);
        for (String u : offlineMap.keySet())
            if (q.isEmpty() || u.toLowerCase().contains(q)) offlineModel.addElement(u);
        lblOnlineCount.setText(String.valueOf(onlineModel.size()));
        lblOfflineCount.setText(String.valueOf(offlineModel.size()));
        // Bao danh sach ve lai de cell renderer re-paint (cap nhat "X phut truoc")
        onlineList.repaint();
        offlineList.repaint();
    }

    // ── Build helpers ─────────────────────────────────────────────────────────

    private JPanel buildSection(String title, JLabel badge, JList<String> list) {
        JLabel header = new JLabel(title);
        header.setFont(DarkTheme.FONT_LABEL);
        header.setForeground(DarkTheme.TEXT3);

        JPanel headerRow = new JPanel(new BorderLayout());
        headerRow.setBackground(DarkTheme.BG2);
        headerRow.setBorder(BorderFactory.createEmptyBorder(10, 12, 4, 12));
        headerRow.add(header, BorderLayout.WEST);
        headerRow.add(badge,  BorderLayout.EAST);

        JPanel section = new JPanel(new BorderLayout());
        section.setBackground(DarkTheme.BG2);
        section.add(headerRow, BorderLayout.NORTH);
        section.add(list,      BorderLayout.CENTER);
        return section;
    }

    private JList<String> makeList(DefaultListModel<String> model, boolean isOnline) {
        JList<String> list = new JList<>(model);
        list.setBackground(DarkTheme.BG2);
        list.setForeground(DarkTheme.TEXT);
        list.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));
        list.setCellRenderer(new UserCellRenderer(isOnline));
        list.setFixedCellHeight(52);
        list.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                int idx = list.locationToIndex(e.getPoint());
                if (idx >= 0 && clickListener != null)
                    clickListener.onUserClicked(model.get(idx));
            }
        });
        return list;
    }

    private JLabel makeSectionBadge(String text, Color color) {
        JLabel l = new JLabel(text) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 40));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        l.setFont(DarkTheme.FONT_MONO);
        l.setForeground(color);
        l.setHorizontalAlignment(SwingConstants.CENTER);
        l.setBorder(BorderFactory.createEmptyBorder(1, 7, 1, 7));
        return l;
    }

    // ── Cell renderer ─────────────────────────────────────────────────────────

    private class UserCellRenderer extends DefaultListCellRenderer {
        private final boolean isOnline;
        private String currentName = "";

        UserCellRenderer(boolean isOnline) { this.isOnline = isOnline; }

        @Override
        public Component getListCellRendererComponent(
                JList<?> list, Object value, int idx, boolean selected, boolean focused) {
            currentName = (String) value;
            return new JPanel() {
                final boolean sel = selected;
                final String  nm  = currentName;
                { setOpaque(false); }

                @Override protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    if (sel) {
                        g2.setColor(new Color(0x7c3aed20, true));
                        g2.fillRoundRect(4, 2, getWidth() - 8, getHeight() - 4, 10, 10);
                    }
                    int avX = 10, avY = (getHeight() - 32) / 2;
                    DarkTheme.paintAvatar(g2, nm, avX, avY, 32);

                    // Cham trang thai online/offline
                    g2.setColor(DarkTheme.BG2);
                    g2.fillOval(avX + 22, avY + 22, 12, 12);
                    g2.setColor(isOnline ? DarkTheme.ONLINE : DarkTheme.OFFLINE);
                    g2.fillOval(avX + 24, avY + 24, 8, 8);

                    int tx = avX + 40;
                    boolean isMe = nm.equals(myUsername);
                    g2.setColor(isOnline ? DarkTheme.TEXT : DarkTheme.TEXT3);
                    g2.setFont(DarkTheme.FONT_BODY_B);
                    g2.drawString(isMe ? nm + " (Bạn)" : nm, tx, getHeight() / 2);

                    // Dong trang thai: "Dang hoat dong" hoac "X phut truoc"
                    String statusText;
                    if (isOnline) {
                        statusText = "● Đang hoạt động";
                    } else {
                        Long epochLeft = offlineMap.get(nm);
                        statusText = epochLeft != null
                            ? "○ " + formatTimeAgo(epochLeft)
                            : "○ Offline";
                    }
                    g2.setColor(isOnline ? DarkTheme.ONLINE : DarkTheme.TEXT3);
                    g2.setFont(DarkTheme.FONT_SMALL);
                    g2.drawString(statusText, tx, getHeight() / 2 + 16);
                    g2.dispose();
                }

                @Override public Dimension getPreferredSize() { return new Dimension(0, 52); }
            };
        }
    }
}
