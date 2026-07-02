# NetChat — Hướng dẫn chạy trên IntelliJ IDEA

## Cấu trúc project

```
NetChat/
├── config.properties          ← Cấu hình chung (server port, DB)
├── schema.sql                 ← SQL tạo DB (chạy 1 lần)
├── model/
│   ├── Protocol.java          ← Hằng số giao thức
│   └── Message.java
├── Logging/
│   └── SystemLogger.java
├── Database/
│   ├── DatabaseConnection.java
│   ├── UserRepository.java
│   └── MessageRepository.java
├── Server/
│   ├── Server.java            ← ▶ Run Configuration #1
│   ├── ClientHandler.java
│   └── UserManager.java
├── Client/
│   ├── Client.java
│   └── MessageReceiver.java
└── UI/
    ├── LoginPanel.java        ← ▶ Run Configuration #2 (main)
    ├── ChatFrame.java
    ├── ChatPanel.java
    ├── UserListPanel.java
    └── DarkTheme.java
```

---

## Bước 1 — Cài đặt IntelliJ

1. Mở IntelliJ → **File → New → Project from Existing Sources** (hoặc tạo Java project mới)
2. Đặt Source Root là thư mục chứa các package (`model`, `Server`, `UI`, ...)
3. **Working directory** phải là thư mục gốc chứa `config.properties`
   - Vào **Run → Edit Configurations → Working directory** → chọn thư mục gốc

---

## Bước 2 — Thêm MySQL JDBC Driver (nếu dùng DB)

- Vào **File → Project Structure → Libraries → + → From Maven**
- Thêm: `mysql:mysql-connector-java:8.0.33`

> **Demo không cần DB:** Nếu không có MySQL, server sẽ tự động cho phép đăng nhập
> (UserRepository trả về `true` khi DB không kết nối được).

---

## Bước 3 — Tạo DB (tùy chọn)

```sql
-- Chạy schema.sql trong MySQL Workbench hoặc terminal:
mysql -u root -p < schema.sql
```

Cập nhật `config.properties`:
```properties
db.username=root
db.password=YOUR_PASSWORD
db.name=ltm_db
```

---

## Bước 4 — Tạo Run Configurations

### Run Configuration 1: Server
| Field | Value |
|-------|-------|
| Main class | `Server.Server` |
| Working dir | `/đường/dẫn/tới/NetChat` |

### Run Configuration 2: Client (LoginPanel)
| Field | Value |
|-------|-------|
| Main class | `UI.LoginPanel` |
| Working dir | `/đường/dẫn/tới/NetChat` |

---

## Bước 5 — Chạy demo

1. **Chạy Server** trước → xem console `"NetChat Server khởi động trên cổng 9999"`
2. **Chạy LoginPanel** (có thể chạy nhiều instance) → cửa sổ đăng nhập hiện ra
3. Nhập tên bất kỳ, để trống mật khẩu → **Kết nối**
4. Mở thêm một instance nữa với tên khác → hai người có thể chat

---

## Tính năng đã hoàn chỉnh

| Tính năng | Trạng thái |
|-----------|------------|
| Login với username | ✅ |
| Demo mode (không cần DB) | ✅ |
| Chat nhóm | ✅ |
| Chat riêng (`@username nội dung`) | ✅ |
| Danh sách user online (sidebar) | ✅ |
| Thông báo join / leave | ✅ |
| Ping thật (ms) | ✅ |
| Typing indicator | ✅ |
| Lưu tin nhắn vào DB | ✅ |
| Click user → điền @mention | ✅ |
| Ngắt kết nối → quay lại login | ✅ |

---

## Giao thức (Protocol)

```
Client → Server:
  LOGIN:username:password
  GROUP:nội dung
  PRIVATE:toUser:nội dung
  TYPING:1 / TYPING:0
  PING
  LOGOUT

Server → Client:
  AUTH_OK:username
  AUTH_FAIL:lý do
  MSG_GROUP:sender:nội dung
  MSG_PRIVATE:from:to:nội dung
  USER_LIST:u1,u2,u3
  USER_JOIN:username
  USER_LEFT:username
  TYPING:username:1|0
  PONG
  ERROR:lý do
```
