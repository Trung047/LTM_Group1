# LTM_Group1


import java.time.LocalDateTime; // lưu thông tin người dùng
public class User {
    private int userId;            
    private String username;         
    private String passwordHash;    
    private String email;            
    private LocalDateTime createdAt; 
    private String status;

import java.time.LocalDateTime; // lưu lịch sử chat
public class Message {
    private int messageId;       
    private int senderId;           
    private int receiverId;         
    private String content;         
    private LocalDateTime sendTime; 
    private boolean isRead;        
}

import java.time.LocalDateTime; // Ghi log hệ thống 
public class SystemLog {
    private int logId;              
    private String logLevel;         
    private String action;           
    private Integer userId;            
    private String details;         
    private LocalDateTime logTime;  
}
