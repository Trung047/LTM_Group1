package UI;

import javax.swing.*;
import javax.swing.border.AbstractBorder;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

/**
 * DarkTheme — Bảng màu, phông chữ và helper component
 * dùng chung cho toàn bộ giao diện ứng dụng.
 */
public class DarkTheme {

    // ─── Bảng màu nền ────────────────────────────────────────────────────────
    public static final Color BG         = new Color(0x0d0f1a);  // Nền chính
    public static final Color BG2        = new Color(0x161929);  // Nền phụ (header/footer)
    public static final Color SURFACE    = new Color(0x1a1e35);  // Card, bubble người khác
    public static final Color SURFACE2   = new Color(0x232842);  // Input area

    // ─── Bảng màu viền ───────────────────────────────────────────────────────
    public static final Color BORDER     = new Color(0x2a3060);  // Viền nhẹ
    public static final Color BORDER2    = new Color(0x3a4080);  // Viền đậm hơn

    // ─── Bảng màu chủ đạo ────────────────────────────────────────────────────
    public static final Color PRIMARY    = new Color(0x7c3aed);  // Tím chính
    public static final Color PRIMARY_DK = new Color(0x5b21b6);  // Tím đậm hơn (hover)
    public static final Color ACCENT     = new Color(0x06b6d4);  // Xanh nhạt (cyan)
    public static final Color ACCENT_DK  = new Color(0x0891b2);  // Cyan đậm hơn (hover)

    // ─── Bảng màu trạng thái ────────────────────────────────────────────────
    public static final Color ONLINE     = new Color(0x22c55e);  // Xanh lá (online)
    public static final Color WARNING    = new Color(0xf59e0b);  // Vàng (cảnh báo)
    public static final Color DANGER     = new Color(0xef4444);  // Đỏ (nguy hiểm)

    // ─── Bảng màu văn bản ────────────────────────────────────────────────────
    public static final Color TEXT       = new Color(0xe2e8f0);  // Văn bản chính
    public static final Color TEXT2      = new Color(0x94a3b8);  // Văn bản phụ
    public static final Color TEXT3      = new Color(0x64748b);  // Văn bản mờ

    // ─── Phông chữ ───────────────────────────────────────────────────────────
    public static final Font FONT_BODY   = new Font("Segoe UI", Font.PLAIN, 13);
    public static final Font FONT_BOLD   = new Font("Segoe UI", Font.BOLD, 13);
    public static final Font FONT_SMALL  = new Font("Segoe UI", Font.PLAIN, 11);
    public static final Font FONT_MONO   = new Font("Consolas",  Font.PLAIN, 12);
    public static final Font FONT_TITLE  = new Font("Segoe UI", Font.BOLD, 15);

    // ─── Bảng màu Avatar (xác định theo tên người dùng) ─────────────────────
    private static final Color[] AVATAR_COLORS = {
        new Color(0x7c3aed), new Color(0x06b6d4), new Color(0x10b981),
        new Color(0xf59e0b), new Color(0xef4444), new Color(0xec4899),
        new Color(0x8b5cf6), new Color(0x3b82f6), new Color(0x14b8a6),
        new Color(0xf97316),
    };

    /**
     * Trả về màu avatar ổn định cho một tên người dùng.
     * Cùng tên => cùng màu mỗi lần.
     */
    public static Color avatarColor(String name) {
        if (name == null || name.isEmpty()) return AVATAR_COLORS[0];
        int idx = Math.abs(name.hashCode()) % AVATAR_COLORS.length;
        return AVATAR_COLORS[idx];
    }

    // ─── Factory: JScrollPane kiểu dark ─────────────────────────────────────
    public static JScrollPane makeScrollPane(JComponent comp) {
        JScrollPane sp = new JScrollPane(comp);
        sp.setBorder(BorderFactory.createEmptyBorder());
        sp.setBackground(BG);
        sp.getViewport().setBackground(BG);
        sp.getVerticalScrollBar().setBackground(BG2);
        sp.getVerticalScrollBar().setUnitIncrement(16);
        return sp;
    }

    // ─── Factory: JButton dạng icon nhỏ ────────────────────────────────────
    public static JButton makeIconButton(String text, String tooltip) {
        JButton btn = new JButton(text) {
            private boolean hov = false;
            {
                addMouseListener(new java.awt.event.MouseAdapter() {
                    public void mouseEntered(java.awt.event.MouseEvent e) { hov = true; repaint(); }
                    public void mouseExited(java.awt.event.MouseEvent e)  { hov = false; repaint(); }
                });
            }
            @Override
            protected void paintComponent(Graphics g) {
                if (hov) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                        RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(new Color(0x2a3060));
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                    g2.dispose();
                }
                super.paintComponent(g);
            }
        };
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setFont(FONT_SMALL);
        btn.setForeground(TEXT2);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setToolTipText(tooltip);
        return btn;
    }

    // ─── Border bo góc tùy chỉnh ─────────────────────────────────────────────
    public static class RoundBorder extends AbstractBorder {
        private final Color color;
        private final int   radius;
        private final int   thickness;

        public RoundBorder(Color color, int radius, int thickness) {
            this.color     = color;
            this.radius    = radius;
            this.thickness = thickness;
        }

        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.setStroke(new BasicStroke(thickness));
            g2.draw(new RoundRectangle2D.Float(
                    x + thickness / 2f, y + thickness / 2f,
                    w - thickness, h - thickness,
                    radius, radius));
            g2.dispose();
        }

        @Override
        public Insets getBorderInsets(Component c) {
            int t = thickness + 4;
            return new Insets(t, t + 4, t, t + 4);
        }
    }
}
