package browser;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.JButton;
import javax.swing.border.EmptyBorder;

/**
 * Tiện ích giao diện phong cách Google Chrome hiện đại,
 * đảm bảo độ tương phản chữ và màu nền rõ nét trên Windows Look & Feel.
 */
public class UiUtil {

    public static JButton createColoredButton(String text, Color bg, Color fg, int radius) {
        JButton btn = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (!isEnabled()) {
                    g2.setColor(new Color(226, 232, 240));
                } else if (getModel().isPressed()) {
                    g2.setColor(darken(bg, 0.85f));
                } else if (getModel().isRollover()) {
                    g2.setColor(brighten(bg, 1.12f));
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
        btn.setBorderPainted(false);
        btn.setBorder(new EmptyBorder(6, 12, 6, 12));
        btn.setForeground(fg);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btn.setFocusPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return btn;
    }

    public static JButton createOutlineButton(String text, Color borderColor, Color textColor) {
        JButton btn = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (!isEnabled()) {
                    g2.setColor(new Color(241, 245, 249));
                } else if (getModel().isPressed()) {
                    g2.setColor(new Color(219, 234, 254));
                } else if (getModel().isRollover()) {
                    g2.setColor(new Color(241, 245, 249));
                } else {
                    g2.setColor(Color.WHITE);
                }
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
                g2.setColor(isEnabled() ? borderColor : new Color(203, 213, 225));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        btn.setContentAreaFilled(false);
        btn.setOpaque(false);
        btn.setBorderPainted(false);
        btn.setBorder(new EmptyBorder(5, 10, 5, 10));
        btn.setForeground(textColor);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 11));
        btn.setFocusPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return btn;
    }

    private static Color darken(Color c, float factor) {
        return new Color(
                Math.max((int) (c.getRed() * factor), 0),
                Math.max((int) (c.getGreen() * factor), 0),
                Math.max((int) (c.getBlue() * factor), 0)
        );
    }

    private static Color brighten(Color c, float factor) {
        return new Color(
                Math.min((int) (c.getRed() * factor), 255),
                Math.min((int) (c.getGreen() * factor), 255),
                Math.min((int) (c.getBlue() * factor), 255)
        );
    }
}
