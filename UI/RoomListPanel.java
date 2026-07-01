package UI;

import model.Room;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class RoomListPanel extends JPanel {
    private DefaultListModel<Room> roomListModel;
    private JList<Room> roomList;
    private JButton btnCreateRoom;
    private JButton btnJoinRoom;

    public RoomListPanel() {
        initComponents();
        setupListeners();
    }

    private void initComponents() {
        setLayout(new BorderLayout());
        setPreferredSize(new Dimension(250, 400));
        
        // Khởi tạo Model và JList
        roomListModel = new DefaultListModel<>();
        roomList = new JList<>(roomListModel);
        roomList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        
        // Custom UI một chút cho đồng bộ với DarkTheme (nếu có)
        roomList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                Component c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                // Bạn có thể set icon cho phòng ở đây nếu muốn
                // setIcon(new ImageIcon("path/to/icon.png"));
                return c;
            }
        });

        // Đưa JList vào ScrollPane
        JScrollPane scrollPane = new JScrollPane(roomList);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Danh sách phòng chat"));

        // Khởi tạo các nút chức năng
        JPanel buttonPanel = new JPanel(new GridLayout(1, 2, 5, 5));
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        
        btnCreateRoom = new JButton("Tạo phòng");
        btnJoinRoom = new JButton("Vào phòng");
        
        buttonPanel.add(btnCreateRoom);
        buttonPanel.add(btnJoinRoom);

        // Thêm vào panel chính
        add(scrollPane, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);
    }

    private void setupListeners() {
        // Sự kiện khi bấm "Vào phòng"
        btnJoinRoom.addActionListener(e -> {
            Room selectedRoom = roomList.getSelectedValue();
            if (selectedRoom != null) {
                // TODO: Gọi hàm từ Controller hoặc thông báo cho ChatFrame để gửi request lên Server
                System.out.println("Request join room: " + selectedRoom.getId() + " - " + selectedRoom.getName());
            } else {
                JOptionPane.showMessageDialog(this, 
                    "Vui lòng chọn một phòng từ danh sách!", 
                    "Thông báo", JOptionPane.WARNING_MESSAGE);
            }
        });

        // Sự kiện khi bấm "Tạo phòng"
        btnCreateRoom.addActionListener(e -> {
            String roomName = JOptionPane.showInputDialog(this, 
                "Nhập tên phòng muốn tạo:", 
                "Tạo phòng mới", JOptionPane.PLAIN_MESSAGE);
                
            if (roomName != null && !roomName.trim().isEmpty()) {
                // TODO: Gọi hàm gửi request tạo phòng (kèm packet Protocol) lên Server
                System.out.println("Request create room: " + roomName);
            }
        });
        
        // Hỗ trợ double-click vào phòng để join
        roomList.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                if (evt.getClickCount() == 2) {
                    btnJoinRoom.doClick();
                }
            }
        });
    }

    /**
     * Hàm này được Client gọi khi nhận được danh sách phòng từ Server (thường qua MessageReceiver)
     */
    public void updateRoomList(List<Room> rooms) {
        SwingUtilities.invokeLater(() -> {
            roomListModel.clear();
            for (Room room : rooms) {
                roomListModel.addElement(room);
            }
        });
    }
}
