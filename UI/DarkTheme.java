package UI;

import javax.swing.*;
import javax.swing.border.AbstractBorder;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

public class DarkTheme {

    // ─── Bảng màu nền ───────────────────────────────────────────────────────
    public static final Color BG       = new Color(0x0d0f1a);
    public static final Color BG2      = new Color(0x161929);
    public static final Color SURFACE  = new Color(0x1a1e35);
    public static final Color SURFACE2 = new Color(0x232842);

    // ─── Viền ───────────────────────────────────────────────────────────────
    public static final Color BORDER  = new Color(0x2a3060);
    public static final Color BORDER2 = new Color(0x3a4080);

    // ─── Màu chủ đạo ────────────────────────────────────────────────────────
    public static final Color PRIMARY    = new Color(0x7c3aed);
    public static final Color PRIMARY_DK = new Color(0x5b21b6);
    public static final Color ACCENT     = new Color(0x06b6d4);
    public static final Color ACCENT_DK  = new Color(0x0891b2);

    // ─── Trạng thái ─────────────────────────────────────────────────────────
    public static final Color ONLINE  = new Color(0x22c55e);
    public static final Color OFFLINE = new Color(0x64748b);
    public static final Color WARNING = new Color(0xf59e0b);
    public static final Color DANGER  = new Color(0xef4444);

    // ─── Chữ ────────────────────────────────────────────────────────────────
    public static final Color TEXT  = new Color(0xe2e8f0);
    public static final Color TEXT2 = new Color(0x94a3b8);
    public static final Color TEXT3 = new Color(0x64748b);

    // ─── Font ────────────────────────────────────────────────────────────────
    public static final Font FONT_BODY   = new Font("Segoe UI", Font.PLAIN,  13);
    public static final Font FONT_BOLD   = new Font("Segoe UI", Font.BOLD,   13);
    public static final Font FONT_BODY_B = new Font("Segoe UI", Font.BOLD,   13);
    public static final Font FONT_SMALL  = new Font("Segoe UI", Font.PLAIN,  11);
    public static final Font FONT_LABEL  = new Font("Segoe UI", Font.BOLD,   11);
    public static final Font FONT_MONO   = new Font("Consolas",  Font.PLAIN,  12);
    public static final Font FONT_TITLE  = new Font("Segoe UI", Font.BOLD,   15);

    // ─── Avatar colors ──────────────────────────────────────────────────────
    private static final Color[] AVATAR_COLORS = {
        new Color(0x7c3aed), new Color(0x06b6d4), new Color(0x10b981),
        new Color(0xf59e0b), new Color(0xef4444), new Color(0xec4899),
        new Color(0x8b5cf6), new Color(0x3b82f6), new Color(0x14b8a6),
        new Color(0xf97316)
    };

    public static Color avatarColor(String name) {
        if (name == null || name.isEmpty()) return AVATAR_COLORS[0];
        return AVATAR_COLORS[Math.abs(name.hashCode()) % AVATAR_COLORS.length];
    }

    // ─── Factories ──────────────────────────────────────────────────────────
    public static JScrollPane makeScrollPane(JComponent comp) {
        JScrollPane sp = new JScrollPane(comp);
        sp.setBorder(BorderFactory.createEmptyBorder());
        sp.setBackground(BG);
        sp.getViewport().setBackground(BG);
        sp.getVerticalScrollBar().setBackground(BG2);
        sp.getVerticalScrollBar().setUnitIncrement(16);
        return sp;
    }

    public static JButton makeIconButton(String text, String tooltip) {
        JButton btn = new JButton(text);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setFont(FONT_SMALL);
        btn.setForeground(TEXT2);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setToolTipText(tooltip);
        return btn;
    }

    public static JTextField makeTextField(String placeholder) {
        JTextField tf = new JTextField();
        tf.setToolTipText(placeholder);
        tf.setFont(FONT_BODY);
        tf.setForeground(TEXT);
        tf.setBackground(SURFACE2);
        tf.setCaretColor(PRIMARY);
        tf.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        return tf;
    }

    // ─── Avatar painter ─────────────────────────────────────────────────────
    public static void paintAvatar(Graphics2D g2, String name, int x, int y, int size) {
        g2.setColor(avatarColor(name));
        g2.fillOval(x, y, size, size);
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("Segoe UI", Font.BOLD, size / 2));
        String ch = (name == null || name.isEmpty()) ? "?" : name.substring(0, 1).toUpperCase();
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(ch,
            x + (size - fm.stringWidth(ch)) / 2,
            y + (size + fm.getAscent() - fm.getDescent()) / 2);
    }

    // ─── Round border ────────────────────────────────────────────────────────
    public static class RoundBorder extends AbstractBorder {
        private final Color color;
        private final int radius, thickness;

        public RoundBorder(Color color, int radius, int thickness) {
            this.color = color; this.radius = radius; this.thickness = thickness;
        }

        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.setStroke(new BasicStroke(thickness));
            g2.draw(new RoundRectangle2D.Float(
                x + thickness / 2f, y + thickness / 2f,
                w - thickness, h - thickness, radius, radius));
            g2.dispose();
        }

        @Override
        public Insets getBorderInsets(Component c) {
            int t = thickness + 4;
            return new Insets(t, t + 4, t, t + 4);
        }
    }
}
