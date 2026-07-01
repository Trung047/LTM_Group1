package UI;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;
import java.util.function.Consumer;

/**
 * Sidebar danh sach cac phong nhom (tach rieng tung nhom).
 */
public class RoomListPanel extends JPanel {

    private final DefaultListModel<String> model = new DefaultListModel<>();
    private final JList<String> list = new JList<>(model);
    private Consumer<String> onSelectRoom;

    public RoomListPanel() {
        setLayout(new BorderLayout());
        setBackground(DarkTheme.BG_SIDEBAR);
        setBorder(new EmptyBorder(8, 10, 8, 10));

        JLabel title = new JLabel("PHÒNG NHÓM");
        title.setFont(DarkTheme.FONT_SMALL);
        title.setForeground(DarkTheme.FG_MUTED);
        add(title, BorderLayout.NORTH);

        list.setBackground(DarkTheme.BG_SIDEBAR);
        list.setForeground(DarkTheme.FG_TEXT);
        list.setFont(DarkTheme.FONT_NORMAL);
        list.setFixedCellHeight(30);
        list.setSelectionBackground(DarkTheme.BG_INPUT);
        list.setSelectionForeground(DarkTheme.ACCENT);
        list.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                String room = list.getSelectedValue();
                if (room != null && onSelectRoom != null) onSelectRoom.accept(room);
            }
        });

        JScrollPane sp = new JScrollPane(list);
        sp.setBorder(null);
        sp.getViewport().setBackground(DarkTheme.BG_SIDEBAR);
        sp.setPreferredSize(new Dimension(180, 140));
        add(sp, BorderLayout.CENTER);
    }

    public void setRooms(List<String> rooms) {
        model.clear();
        for (String r : rooms) model.addElement(r);
        if (!rooms.isEmpty()) list.setSelectedIndex(0);
    }

    public void setOnSelectRoom(Consumer<String> callback) { this.onSelectRoom = callback; }
}
