package ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicButtonUI;
import java.awt.*;

/**
 * Tiện ích chuẩn hóa hiển thị JButton trên Windows LookAndFeel,
 * khắc phục lỗi nút bấm bị ghi đè nền trắng khiến chữ trắng không nhìn thấy.
 */
public final class UiStyle {
    private UiStyle() {}

    public static void styleButton(JButton btn, Color bg, Color fg) {
        styleButton(btn, bg, fg, null, 8, 6, 14);
    }

    public static void styleButton(JButton btn, Color bg, Color fg, Color borderColor, int radius, int padV, int padH) {
        btn.setUI(new FlatButtonUI(radius, borderColor));
        btn.setBackground(bg);
        btn.setForeground(fg);
        btn.setOpaque(false);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setRolloverEnabled(true);
        btn.setBorder(new EmptyBorder(padV, padH, padV, padH));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    public static void styleSecondaryButton(JButton btn) {
        styleButton(btn, new Color(0xF3, 0xF4, 0xF6), new Color(0x1F, 0x29, 0x37),
                new Color(0xD1, 0xD5, 0xDB), 6, 5, 12);
    }

    public static void styleDangerButton(JButton btn) {
        styleButton(btn, new Color(0xDC, 0x26, 0x26), Color.WHITE, null, 6, 5, 12);
    }

    private static final class FlatButtonUI extends BasicButtonUI {
        private final int radius;
        private final Color borderColor;

        FlatButtonUI(int radius, Color borderColor) {
            this.radius = radius;
            this.borderColor = borderColor;
        }

        @Override
        public void paint(Graphics g, JComponent c) {
            AbstractButton b = (AbstractButton) c;
            ButtonModel m = b.getModel();

            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            Color base = b.getBackground() != null ? b.getBackground() : new Color(0xF3, 0xF4, 0xF6);
            Color fill;
            if (!b.isEnabled()) {
                fill = new Color(0xE5, 0xE7, 0xEB);
            } else if (m.isPressed() || m.isArmed()) {
                fill = shiftColor(base, isDark(base) ? 24 : -22);
            } else if (m.isRollover()) {
                fill = shiftColor(base, isDark(base) ? 14 : -12);
            } else {
                fill = base;
            }

            g2.setColor(fill);
            g2.fillRoundRect(0, 0, c.getWidth(), c.getHeight(), radius, radius);

            if (borderColor != null) {
                g2.setColor(borderColor);
                g2.drawRoundRect(0, 0, c.getWidth() - 1, c.getHeight() - 1, radius, radius);
            }
            g2.dispose();

            super.paint(g, c);
        }

        private static boolean isDark(Color c) {
            return (c.getRed() + c.getGreen() + c.getBlue()) < 260;
        }

        private static Color shiftColor(Color c, int delta) {
            int r = Math.max(0, Math.min(255, c.getRed() + delta));
            int g = Math.max(0, Math.min(255, c.getGreen() + delta));
            int b = Math.max(0, Math.min(255, c.getBlue() + delta));
            return new Color(r, g, b);
        }
    }
}
