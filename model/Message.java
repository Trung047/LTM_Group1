package model;

import java.io.Serializable;
import java.time.LocalDateTime;

public class Message implements Serializable {
    private final String sender;
    private final String receiver;   // null = group
    private final String content;
    private final String type;
    private final LocalDateTime timestamp;

    public Message(String sender, String receiver, String content, String type) {
        this.sender    = sender;
        this.receiver  = receiver;
        this.content   = content;
        this.type      = type;
        this.timestamp = LocalDateTime.now();
    }

    public String getSender()    { return sender; }
    public String getReceiver()  { return receiver; }
    public String getContent()   { return content; }
    public String getType()      { return type; }
    public LocalDateTime getTimestamp() { return timestamp; }
}
