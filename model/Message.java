import java.io.Serializable;
package model;

// Lưu thông tin của một tin nhắn
public class Message implements Serializable{

    private String sender;   // người gửi
    private String receiver; // người nhận
    private String content;  // nội dung tin nhắn
    private String type;     // loại tin nhắn

    // Hàm khởi tạo
    public Message(String sender,String receiver,String content,String type){
        this.sender=sender;
        this.receiver=receiver;
        this.content=content;
        this.type=type;
    }

    // Lấy thông tin người gửi
    public String getSender(){
        return sender;
    }

    // Lấy thông tin người nhận
    public String getReceiver(){
        return receiver;
    }

    // Lấy nội dung tin nhắn
    public String getContent(){
        return content;
    }

    // Lấy loại tin nhắn
    public String getType(){
        return type;
    }
}
