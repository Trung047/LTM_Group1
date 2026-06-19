package model;
public class Protocol {

    // Đăng nhập / đăng xuất
    public static final String LOGIN = "LOGIN";
    public static final String LOGOUT = "LOGOUT";

    // Chat nhóm
    public static final String GROUP = "GROUP";

    // Chat riêng
    public static final String PRIVATE = "PRIVATE";

    // Tham gia / rời phòng chat
    public static final String JOIN = "JOIN";
    public static final String LEAVE = "LEAVE";

    // Tin nhắn hệ thống
    public static final String SYSTEM = "SYSTEM";

    // Thông báo lỗi
    public static final String ERROR = "ERROR";

    private Protocol() {
        // Ngăn tạo đối tượng Protocol
    }
}
