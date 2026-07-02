package UI;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.FileInputStream;
import java.util.Properties;

/**
 * LoginPanel — Màn hình đăng nhập.
 *
 * Thay đổi so với bản gốc:
 *  - Có ô nhập Password
 *  - Đọc host/port mặc định từ config.properties
 *  - Callback LoginListener trả về password
 *  - Sử dụng ChatFrame.doLogin() để xử lý kết nối thực sự
 */
public class LoginPanel extends JPanel {

    public interface LoginListener {
        void onConnect(String username, String password, String host, int port);
    }

    // ── Colors ──────────────────────────────────────────────────────────────
    private static final Color BG           = new Color(0x0d0f1a);
    private static final Color SURFACE      = new Color(0x1a1e35);
    private static final Color SURFACE_LIGHT= new Color(0x232842);
    private static final Color BORDER_COLOR = new Color(0x2a3060);
    private static final Color PRIMARY      = new Color(0x7c3aed);
    private static final Color ACCENT       = new Color(0x06b6d4);
    private static final Color TEXT_MAIN    = Color.WHITE;
    private static final Color TEXT_MUTED   = new Color(0x94a3b8);
    private static final Color TEXT_DARK    = new Color(0x64748b);
    private static final Color ONLINE       = new Color(0x22c55e);
    private static final Color DANGER       = new Color(0xef4444);
    private static final Color WARNING      = new Color(0xf59e0b);

    // ── Components ──────────────────────────────────────────────────────────
    private final RoundTextField  tfUsername = new RoundTextField("Nhập tên người dùng...", "");
    private final RoundTextField  tfHost;
    private final RoundTextField  tfPort;
    private final RoundPassField  tfPassword = new RoundPassField("Mật khẩu (để trống = demo)");
    private final GradientButton  btnConnect = new GradientButton("Kết nối");
    private final JLabel          lblStatus  = new JLabel(" ");
    private final JLabel          lblCharCount  = new JLabel("0 / 32");
    private final JLabel          lblIpStatus   = new JLabel(" ");
    private       LoginListener   loginListener;

    public LoginPanel() {
        // Đọc defaults từ config
        String defaultHost = "127.0.0.1";
        String defaultPort = "8080";
        try {
            Properties p = new Properties();
            p.load(new FileInputStream("config.properties"));
            String h = p.getProperty("server.host", "127.0.0.1");
            defaultHost = h.equals("0.0.0.0") ? "127.0.0.1" : h;
            defaultPort = p.getProperty("server.port", "8080");
        } catch (Exception ignored) {}

        tfHost = new RoundTextField("IP server", defaultHost);
        tfPort = new RoundTextField("Port", defaultPort);

        setBackground(BG);
        setLayout(new GridBagLayout());

        JPanel card = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(SURFACE);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 24, 24);
                g2.setPaint(new GradientPaint(0, 0, PRIMARY, getWidth(), 0, ACCENT));
                g2.setStroke(new BasicStroke(2.0f));
                g2.drawRoundRect(1, 1, getWidth() - 2, getHeight() - 2, 24, 24);
                g2.dispose();
            }
        };
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setOpaque(false);
        card.setBorder(BorderFactory.createEmptyBorder(36, 40, 32, 40));
        card.setPreferredSize(new Dimension(420, 530));
        card.setMaximumSize(new Dimension(420, 530));

        card.add(buildLogo());
        card.add(Box.createVerticalStrut(24));

        addFormField(card, "👤  Tên người dùng", tfUsername,  lblCharCount);
        addFormField(card, "🔑  Mật khẩu",       tfPassword,  new JLabel(" "));
        addFormField(card, "🖥️  Server IP",       tfHost,      lblIpStatus);
        addFormField(card, "🔌  Port",            tfPort,      new JLabel(" "));

        btnConnect.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
        btnConnect.setAlignmentX(Component.CENTER_ALIGNMENT);
        card.add(btnConnect);
        card.add(Box.createVerticalStrut(10));

        lblStatus.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblStatus.setForeground(DANGER);
        lblStatus.setAlignmentX(Component.CENTER_ALIGNMENT);
        card.add(lblStatus);

        // Demo mode hint
        JLabel hint = new JLabel("💡 Demo: để trống mật khẩu, không cần DB");
        hint.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        hint.setForeground(TEXT_DARK);
        hint.setAlignmentX(Component.CENTER_ALIGNMENT);
        card.add(Box.createVerticalStrut(4));
        card.add(hint);

        add(card);
        wireListeners();
    }

    private void addFormField(JPanel parent, String label, JComponent field, JLabel statusLabel) {
        JLabel l = new JLabel(label);
        l.setFont(new Font("Segoe UI", Font.BOLD, 12));
        l.setForeground(TEXT_MUTED);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        parent.add(l);
        parent.add(Box.createVerticalStrut(5));

        field.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        parent.add(field);

        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
        row.add(statusLabel, BorderLayout.WEST);
        statusLabel.setFont(statusLabel == lblCharCount
            ? new Font("Consolas", Font.PLAIN, 12)
            : new Font("Segoe UI", Font.BOLD, 12));
        statusLabel.setForeground(TEXT_DARK);
        parent.add(row);
        parent.add(Box.createVerticalStrut(10));
    }

    private JPanel buildLogo() {
        JPanel logo = new JPanel();
        logo.setLayout(new BoxLayout(logo, BoxLayout.X_AXIS));
        logo.setOpaque(false);
        logo.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel icon = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setPaint(new GradientPaint(0, 0, PRIMARY, getWidth(), getWidth(), ACCENT));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                g2.setColor(Color.WHITE);
                g2.setFont(new Font("Segoe UI", Font.BOLD, 22));
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString("N", (getWidth() - fm.stringWidth("N")) / 2,
                              (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
                g2.dispose();
            }
        };
        icon.setPreferredSize(new Dimension(44, 44));
        icon.setMaximumSize(new Dimension(44, 44));
        icon.setOpaque(false);

        JPanel text = new JPanel();
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        text.setOpaque(false);

        JLabel title = new JLabel("NetChat");
        title.setFont(new Font("Segoe UI", Font.BOLD, 26));
        title.setForeground(Color.WHITE);

        JLabel sub = new JLabel("Kết nối · Trò chuyện · Chia sẻ");
        sub.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        sub.setForeground(TEXT_MUTED);

        text.add(title);
        text.add(Box.createVerticalStrut(2));
        text.add(sub);

        logo.add(icon);
        logo.add(Box.createHorizontalStrut(12));
        logo.add(text);
        return logo;
    }

    private void wireListeners() {
        tfUsername.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { updateCharCount(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { updateCharCount(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) {}
        });
        tfHost.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { validateIP(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { validateIP(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) {}
        });
        btnConnect.addActionListener(e -> doConnect());

        KeyAdapter enterKey = new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) doConnect();
            }
        };
        tfUsername.addKeyListener(enterKey);
        tfPassword.addKeyListener(enterKey);
        tfHost.addKeyListener(enterKey);
        tfPort.addKeyListener(enterKey);
    }

    private void updateCharCount() {
        int len = tfUsername.getText().length();
        lblCharCount.setText(len + " / 32");
        lblCharCount.setForeground(len > 32 ? DANGER : len > 28 ? WARNING : TEXT_DARK);
    }

    private void validateIP() {
        String ip = tfHost.getText().trim();
        if (ip.isEmpty()) { lblIpStatus.setText(""); return; }
        boolean ok = ip.matches("^(\\d{1,3}\\.){3}\\d{1,3}$") || ip.equalsIgnoreCase("localhost");
        lblIpStatus.setText(ok ? "✔ Hợp lệ" : "✘ Sai định dạng");
        lblIpStatus.setForeground(ok ? ONLINE : DANGER);
    }

    private void doConnect() {
        String username = tfUsername.getText().trim();
        String host     = tfHost.getText().trim();
        String portStr  = tfPort.getText().trim();

        if (username.isEmpty()) { showStatus("Vui lòng nhập tên người dùng."); return; }
        if (username.length() > 32) { showStatus("Tên quá dài (tối đa 32 ký tự)."); return; }
        if (host.isEmpty()) { showStatus("Vui lòng nhập địa chỉ Server IP."); return; }

        int port;
        try {
            port = Integer.parseInt(portStr);
            if (port < 1 || port > 65535) throw new NumberFormatException();
        } catch (NumberFormatException ex) {
            showStatus("Port phải là số từ 1 đến 65535."); return;
        }

        showStatus("Đang kết nối...", ACCENT);
        btnConnect.setEnabled(false);
        if (loginListener != null) loginListener.onConnect(username, new String(tfPassword.getPassword()), host, port);
    }

    public void showStatus(String msg)              { showStatus(msg, DANGER); }
    public void showStatus(String msg, Color color) {
        SwingUtilities.invokeLater(() -> { lblStatus.setText(msg); lblStatus.setForeground(color); });
    }
    public String getPassword() { return new String(tfPassword.getPassword()); }
    public void resetConnectButton() {
        SwingUtilities.invokeLater(() -> btnConnect.setEnabled(true));
    }
    public void setLoginListener(LoginListener l) { this.loginListener = l; }

    // ── Entry point ──────────────────────────────────────────────────────────

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("NetChat — Đăng nhập");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(420, 560);
            frame.setLocationRelativeTo(null);

            LoginPanel panel = new LoginPanel();
            frame.getContentPane().setBackground(BG);
            frame.add(panel);

            panel.setLoginListener((username, password, host, port) ->
                ChatFrame.doLogin(frame, panel, username, password, host, port));

            frame.setVisible(true);
        });
    }

    // ── Inner components ─────────────────────────────────────────────────────

    private static class RoundTextField extends JTextField {
        private final String placeholder;
        private boolean focused = false;

        RoundTextField(String placeholder, String initial) {
            super(initial);
            this.placeholder = placeholder;
            setOpaque(false);
            setForeground(TEXT_MAIN);
            setCaretColor(PRIMARY);
            setFont(new Font("Segoe UI", Font.PLAIN, 14));
            setBackground(SURFACE_LIGHT);
            setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
            addFocusListener(new FocusAdapter() {
                public void focusGained(FocusEvent e) { focused = true;  repaint(); }
                public void focusLost(FocusEvent e)   { focused = false; repaint(); }
            });
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(getBackground());
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
            g2.setColor(focused ? PRIMARY : BORDER_COLOR);
            g2.setStroke(new BasicStroke(focused ? 1.5f : 1.0f));
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
            super.paintComponent(g);
            if (getText().isEmpty() && !isFocusOwner()) {
                g2.setFont(getFont());
                g2.setColor(TEXT_DARK);
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(placeholder, getInsets().left, (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
            }
            g2.dispose();
        }
    }

    private static class RoundPassField extends JPasswordField {
        private final String placeholder;
        private boolean focused = false;

        RoundPassField(String placeholder) {
            this.placeholder = placeholder;
            setOpaque(false);
            setForeground(TEXT_MAIN);
            setCaretColor(PRIMARY);
            setFont(new Font("Segoe UI", Font.PLAIN, 14));
            setBackground(SURFACE_LIGHT);
            setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
            setEchoChar('●');
            addFocusListener(new FocusAdapter() {
                public void focusGained(FocusEvent e) { focused = true;  repaint(); }
                public void focusLost(FocusEvent e)   { focused = false; repaint(); }
            });
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(getBackground());
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
            g2.setColor(focused ? PRIMARY : BORDER_COLOR);
            g2.setStroke(new BasicStroke(focused ? 1.5f : 1.0f));
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
            super.paintComponent(g);
            if (getPassword().length == 0 && !isFocusOwner()) {
                g2.setFont(new Font("Segoe UI", Font.PLAIN, 14));
                g2.setColor(TEXT_DARK);
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(placeholder, getInsets().left, (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
            }
            g2.dispose();
        }
    }

    private static class GradientButton extends JButton {
        private boolean hovered = false;

        GradientButton(String text) {
            super(text);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setForeground(Color.WHITE);
            setFont(new Font("Segoe UI", Font.BOLD, 14));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                public void mouseEntered(MouseEvent e) { hovered = true;  repaint(); }
                public void mouseExited(MouseEvent e)  { hovered = false; repaint(); }
            });
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setPaint(new GradientPaint(0, 0,
                hovered ? PRIMARY.darker() : PRIMARY,
                getWidth(), 0,
                hovered ? ACCENT.darker() : ACCENT));
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
            g2.setColor(getForeground());
            g2.setFont(getFont());
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(getText(),
                (getWidth() - fm.stringWidth(getText())) / 2,
                (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
            g2.dispose();
        }
    }
}
