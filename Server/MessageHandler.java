// Xử lý tin nhắn
public class MessageHandler {

    public static String handleMessage(String sender, String message) {
        // Nếu là chat riêng (@Tên nội_dung)
        if (message.startsWith("@")) {
            String[] data = message.split(" ", 2);

            if (data.length > 1) {
                String receiver = data[0].substring(1);
                String content = data[1];

                Message msg = new Message(sender, receiver, content, Protocol.PRIVATE);

                return "[Private] " + msg.getSender() + " -> " + msg.getReceiver() + " : " + msg.getContent();
            }
        }

        // Nếu không có @ thì chat nhóm
        Message msg = new Message(sender, null, message, Protocol.GROUP);
        return "[" + msg.getSender() + "] : " + msg.getContent();
    }
    
    //Hàm để hứng tin nhắn từ Client.java bắn qua
    public static void handleFromServer(String rawMessage) {
        System.out.println("[SERVER TRẢ VỀ THÔ]: " + rawMessage);
    }
}
