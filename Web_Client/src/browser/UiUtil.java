package browser;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.border.EmptyBorder;

/**
 * Tiện ích hỗ trợ tạo các thành phần giao diện đồ họa đẹp mắt,
 * giải quyết triệt để lỗi màu chữ bị trùng màu nền trên Windows Look and Feel.
 */
public class UiUtil {

    /**
     * Tạo nút bấm màu nền nổi bật với chữ tương phản cao, tự vẽ nền không bị Windows L&F ghi đè.
     */
    public static JButton createColoredButton(String text, Color bg, Color fg, int radius) {
        JButton btn = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (getModel().isPressed()) {
                    g2.setColor(darken(bg, 0.85f));
                } else if (getModel().isRollover()) {
                    g2.setColor(brighten(bg, 1.15f));
                } else {
                    g2.setColor(bg);
                }
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), radius, radius);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        btn.setContentAreaFilled(false);
        btn.setOpaque(false);
        btn.setBorder(new EmptyBorder(6, 14, 6, 14));
        btn.setForeground(fg);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btn.setFocusPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return btn;
    }

    /**
     * Tạo nút bấm viền mỏng (Outline) với chữ đậm màu tương phản, nền trắng sạch sẽ.
     */
    public static JButton createOutlineButton(String text, Color borderColor, Color textColor) {
        JButton btn = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (getModel().isPressed()) {
                    g2.setColor(new Color(220, 230, 245));
                } else if (getModel().isRollover()) {
                    g2.setColor(new Color(241, 245, 249));
                } else {
                    g2.setColor(Color.WHITE);
                }
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 6, 6);
                g2.setColor(borderColor);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 6, 6);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        btn.setContentAreaFilled(false);
        btn.setOpaque(false);
        btn.setBorder(new EmptyBorder(5, 10, 5, 10));
        btn.setForeground(textColor);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 11));
        btn.setFocusPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return btn;
    }

    private static Color darken(Color c, float factor) {
        return new Color(
                Math.max((int)(c.getRed() * factor), 0),
                Math.max((int)(c.getGreen() * factor), 0),
                Math.max((int)(c.getBlue() * factor), 0)
        );
    }

    private static Color brighten(Color c, float factor) {
        return new Color(
                Math.min((int)(c.getRed() * factor), 255),
                Math.min((int)(c.getGreen() * factor), 255),
                Math.min((int)(c.getBlue() * factor), 255)
        );
    }
}
