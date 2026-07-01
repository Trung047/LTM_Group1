package model;

public class Protocol {
    // Auth
    public static final String LOGIN   = "LOGIN";
    public static final String LOGOUT  = "LOGOUT";

    // Chat
    public static final String GROUP   = "GROUP";
    public static final String PRIVATE = "PRIVATE";

    // Typing
    public static final String TYPING  = "TYPING";

    // Ping
    public static final String PING    = "PING";
    public static final String PONG    = "PONG";

    // Server → Client broadcasts
    public static final String USER_LIST = "USER_LIST"; // USER_LIST:u1|epoch,u2|epoch
    public static final String USER_JOIN = "USER_JOIN"; // USER_JOIN:username|epoch
    public static final String USER_LEFT = "USER_LEFT"; // USER_LEFT:username|epoch

    // Server → Client message delivery
    public static final String MSG_GROUP   = "MSG_GROUP";
    public static final String MSG_PRIVATE = "MSG_PRIVATE";

    // Thêm các hằng số mới liên quan đến Room:
    public static final String ROOM_LIST = "ROOM_LIST";
    public static final String JOIN_ROOM = "JOIN_ROOM";

    // ...
    // Auth results
    public static final String AUTH_OK   = "AUTH_OK";
    public static final String AUTH_FAIL = "AUTH_FAIL";

    // Tao nhom
    public static final String CREATE_GROUP  = "CREATE_GROUP";  // CREATE_GROUP:tenNhom
    public static final String GROUP_CREATED = "GROUP_CREATED"; // GROUP_CREATED:tenNhom:nguoiTao

    // Errors
    public static final String ERROR = "ERROR";

    private Protocol() {}

    public static String build(String cmd, String... parts) {
        if (parts.length == 0) return cmd;
        return cmd + ":" + String.join(":", parts);
    }

    public static String[] parse(String frame, int maxParts) {
        return frame.split(":", maxParts);
    }
}
