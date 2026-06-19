package UI;

import UI.DarkTheme;
import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

/**
 * UserListPanel — Sidebar hiển thị danh sách người dùng online/offline.
 * Custom cell renderer với avatar tròn và chấm trạng thái.
 */
public class UserListFrame extends JFrame {
    // ─── Callback ─────────────────────────────────────────
    public interface UserClickListener {
        void onUserClicked(String username);
    }

    // ─── Data ─────────────────────────────────────────────
    private final List<String> onlineUsers = new ArrayList<>();
    private final List<String> offlineUsers = new ArrayList<>();
    // ─── UI ───────────────────────────────────────────────
    private final DefaultListModel<String> onlineModel = new DefaultListModel<>();
    private final DefaultListModel<String> offlineModel = new DefaultListModel<>();
    private final JList<String> onlineList;
    private final JList<String> offlineList;
    private final JLabel lblOnlineCount;
    private final JLabel lblOfflineCount;
    private final JTextField searchField;
    private String myUsername = "";
    private UserClickListener clickListener;

    // ─── Constructor ──────────────────────────────────────
    public UserListFrame() {
        setLayout(new BorderLayout());
        setBackground(DarkTheme.BG2);
        setPreferredSize(new Dimension(220, 0));
        setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, DarkTheme.BORDER));
        // Search bar
        searchField = DarkTheme.makeTextField("🔍  Tìm kiếm...");
        searchField.setPreferredSize(new Dimension(0, 36));
        JPanel searchWrap = new JPanel(new BorderLayout());
        searchWrap.setBackground(DarkTheme.BG2);
        searchWrap.setBorder(BorderFactory.createEmptyBorder(10, 10, 6, 10));
        searchWrap.add(searchField);
        lblOnlineCount = makeSectionBadge("0", DarkTheme.PRIMARY);
        lblOfflineCount = makeSectionBadge("0", DarkTheme.TEXT3);
        // List renderers
        onlineList = makeList(onlineModel, true);
        offlineList = makeList(offlineModel, false);
        // Sections
        JPanel onlineSection = buildSection("● ONLINE", lblOnlineCount, onlineList);
        JPanel offlineSection = buildSection("○ OFFLINE", lblOfflineCount, offlineList);
        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBackground(DarkTheme.BG2);
        body.add(onlineSection);
        body.add(offlineSection);
        JScrollPane scrollPane = DarkTheme.makeScrollPane(body);
        scrollPane.setBackground(DarkTheme.BG2);
        scrollPane.getViewport().setBackground(DarkTheme.BG2);
        add(searchWrap, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
        // Search listener
        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                filter();
            }

            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                filter();
            }

            public void changedUpdate(javax.swing.event.DocumentEvent e) {
            }
        });
    }

    private void setBorder(MatteBorder matteBorder) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'setBorder'");
    }

    // ─── Build helpers ────────────────────────────────────
    private JPanel buildSection(String title, JLabel badge, JList<String> list) {
        JLabel header = new JLabel(title);
        header.setFont(DarkTheme.FONT_LABEL);
        header.setForeground(DarkTheme.TEXT3);
        JPanel headerRow = new JPanel(new BorderLayout());
        headerRow.setBackground(DarkTheme.BG2);
        headerRow.setBorder(BorderFactory.createEmptyBorder(10, 12, 4, 12));
        headerRow.add(header, BorderLayout.WEST);
        headerRow.add(badge, BorderLayout.EAST);
        JPanel section = new JPanel(new BorderLayout());
        section.setBackground(DarkTheme.BG2);
        section.add(headerRow, BorderLayout.NORTH);
        section.add(list, BorderLayout.CENTER);
        return section;
    }

    private JList<String> makeList(DefaultListModel<String> model, boolean isOnline) {
        JList<String> list = new JList<>(model);
        list.setBackground(DarkTheme.BG2);
        list.setForeground(DarkTheme.TEXT);
        list.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));
        list.setCellRenderer(new UserCellRenderer(isOnline));
        list.setFixedCellHeight(48);
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int idx = list.locationToIndex(e.getPoint());
                if (idx >= 0 && clickListener != null) {
                    clickListener.onUserClicked(model.get(idx));
                }
            }
        });
        return list;
    }

    private JLabel makeSectionBadge(String text, Color color) {
        JLabel l = new JLabel(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(
                        getBackground().getRed(),
                        getBackground().getGreen(),
                        getBackground().getBlue(), 40));
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

    // ─── Public API ───────────────────────────────────────
    public void setMyUsername(String name) {
        this.myUsername = name;
    }

    /** Cập nhật toàn bộ danh sách online */
    public void setOnlineUsers(List<String> users) {
        SwingUtilities.invokeLater(() -> {
            onlineUsers.clear();
            onlineUsers.addAll(users);
            filter();
        });
    }

    /** Thêm một user vào danh sách online */
    public void addOnlineUser(String name) {
        SwingUtilities.invokeLater(() -> {
            offlineUsers.remove(name);
            if (!onlineUsers.contains(name))
                onlineUsers.add(name);
            filter();
        });
    }

    /** Chuyển user sang offline */
    public void removeOnlineUser(String name) {
        SwingUtilities.invokeLater(() -> {
            onlineUsers.remove(name);
            if (!offlineUsers.contains(name) && !name.equals(myUsername))
                offlineUsers.add(name);
            filter();
        });
    }

    public void setUserClickListener(UserClickListener l) {
        this.clickListener = l;
    }

    // ─── Filter ───────────────────────────────────────────
    private void filter() {
        String q = searchField.getText().trim().toLowerCase();
        onlineModel.clear();
        offlineModel.clear();
        for (String u : onlineUsers)
            if (q.isEmpty() || u.toLowerCase().contains(q))
                onlineModel.addElement(u);
        for (String u : offlineUsers)
            if (q.isEmpty() || u.toLowerCase().contains(q))
                offlineModel.addElement(u);
        lblOnlineCount.setText(String.valueOf(onlineModel.size()));
        lblOfflineCount.setText(String.valueOf(offlineModel.size()));
    }

    // ─── Cell renderer ────────────────────────────────────
    private class UserCellRenderer extends DefaultListCellRenderer {
        private final boolean isOnline;
        private String currentName = "";

        UserCellRenderer(boolean isOnline) {
            this.isOnline = isOnline;
        }

        @Override
        public Component getListCellRendererComponent(
                JList<?> list, Object value, int idx,
                boolean selected, boolean focused) {
            currentName = (String) value;
            return new JPanel() {
                final boolean sel = selected;
                final String nm = currentName;

                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                            RenderingHints.VALUE_ANTIALIAS_ON);
                    // Row background
                    if (sel) {
                        g2.setColor(new Color(0x7c3aed20, true));
                        g2.fillRoundRect(4, 2, getWidth() - 8, getHeight() - 4, 10, 10);
                    }
                    // Avatar (32x32)
                    int avX = 10, avY = (getHeight() - 32) / 2;
                    DarkTheme.paintAvatar(g2, nm, avX, avY, 32);
                    // Status dot
                    Color dot = isOnline ? DarkTheme.ONLINE : DarkTheme.OFFLINE;
                    g2.setColor(DarkTheme.BG2);
                    g2.fillOval(avX + 22, avY + 22, 12, 12);
                    g2.setColor(dot);
                    g2.fillOval(avX + 24, avY + 24, 8, 8);
                    // Name
                    int tx = avX + 40;
                    boolean isMe = nm.equals(myUsername);
                    String display = isMe ? nm + " (Bạn)" : nm;
                    g2.setColor(isOnline ? DarkTheme.TEXT : DarkTheme.TEXT3);
                    g2.setFont(DarkTheme.FONT_BODY_B);
                    g2.drawString(display, tx, getHeight() / 2 + 2);
                    // Status text
                    g2.setColor(DarkTheme.TEXT3);
                    g2.setFont(DarkTheme.FONT_SMALL);
                    g2.drawString(isOnline ? "● Đang hoạt động" : "○ Offline",
                            tx, getHeight() / 2 + 16);
                    g2.dispose();
                }

                @Override
                public Dimension getPreferredSize() {
                    return new Dimension(0, 48);
                }

                {
                    setOpaque(false);
                }
            };
        }
    }
}
