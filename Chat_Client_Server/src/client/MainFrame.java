package client;

import common.Packet;

import javax.imageio.ImageIO;
import javax.sound.sampled.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/** Cửa sổ chat chính. */
public class MainFrame extends JFrame {
    record Conv(String kind, String key) {
        String id() {
            return kind + ":" + key;
        }
    }

    private static final Font FONT = new Font("Segoe UI", Font.PLAIN, 14);
    private static final Font EMOJI_FONT = new Font("Segoe UI Emoji", Font.PLAIN, 20);
    private static final Color BLUE = new Color(0, 132, 255);
    private static final String[] EMOJIS = ("😀 😁 😂 🤣 😊 😍 😘 😎 🤔 😅 😢 😭 😡 😱 😴 🤗 🙄 😉 😇 🥰 😜 🤩 😏 😬 " +
            "👍 👎 👏 🙏 💪 👌 ✌ 🤝 👋 🙌 ❤ 💔 💖 🔥 ⭐ 🎉 🎁 🌹 ☀ 🌙 ☕ 🍕 🍺 🎂 " +
            "🐶 🐱 🐼 🐵 🚀 💡 📚 💻 📷 🎵 ⚽ 🏆 ✅ ❌ ❓ 💯").split(" ");

    private final NetClient net;
    private final String me;
    private final MediaManager media;

    private List<String> allUsers = new ArrayList<>();
    private Set<String> onlineUsers = new HashSet<>();
    private final Map<String, List<String>> groups = new TreeMap<>();
    private final Map<String, Integer> unread = new HashMap<>();

    private final DefaultListModel<Conv> convModel = new DefaultListModel<>();
    private final JList<Conv> convList = new JList<>(convModel);
    private final JPanel msgPanel = new JPanel();
    private final JScrollPane scroll;
    private final JTextField input = new JTextField();
    private final JLabel title = new JLabel("Chọn một cuộc trò chuyện");
    private final JLabel subtitle = new JLabel(" ");
    private final JButton micBtn = new JButton("🎤");

    private Conv current;
    private boolean updatingList;
    private final Set<Long> shown = new HashSet<>();
    private final AtomicLong rid = new AtomicLong();
    private final Map<Long, Consumer<Packet>> pending = new ConcurrentHashMap<>();

    /** Panel cuộn theo chiều rộng viewport. */
    private static class ScrollPanel extends JPanel implements Scrollable {
        ScrollPanel() {
            super(new BorderLayout());
        }

        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        public int getScrollableUnitIncrement(Rectangle r, int o, int d) {
            return 20;
        }

        public int getScrollableBlockIncrement(Rectangle r, int o, int d) {
            return 100;
        }

        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }

    /** Bong bóng chat bo tròn. */
    private static class Bubble extends JPanel {
        private final Color bg;

        Bubble(Color bg) {
            this.bg = bg;
            setOpaque(false);
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setBorder(new EmptyBorder(8, 12, 6, 12));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(bg);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 18, 18);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    public MainFrame(NetClient net) throws IOException {
        super("Chat LTM - " + net.username);
        this.net = net;
        this.me = net.username;

        UdpMedia udp = new UdpMedia(net.host, net.udpPort);
        udp.hello(me, net.token);
        udp.keepAlive(me, net.token);
        media = new MediaManager(udp, me, m -> SwingUtilities.invokeLater(() -> showError(m)));

        msgPanel.setLayout(new BoxLayout(msgPanel, BoxLayout.Y_AXIS));
        msgPanel.setOpaque(false);
        ScrollPanel wrapper = new ScrollPanel();
        wrapper.setBackground(new Color(245, 247, 250));
        wrapper.add(msgPanel, BorderLayout.NORTH);
        scroll = new JScrollPane(wrapper);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(16);

        setLayout(new BorderLayout());
        add(buildSidebar(), BorderLayout.WEST);
        add(buildChatArea(), BorderLayout.CENTER);
        setSize(1050, 680);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                media.shutdown();
                net.close();
                System.exit(0);
            }
        });

        net.startReader(p -> SwingUtilities.invokeLater(() -> onPacket(p)));
    }

    // ================= UI =================

    private JComponent buildSidebar() {
        JPanel side = new JPanel(new BorderLayout());
        side.setPreferredSize(new Dimension(280, 0));
        side.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, new Color(220, 220, 225)));

        JPanel top = new JPanel(new BorderLayout());
        top.setBackground(BLUE);
        top.setBorder(new EmptyBorder(12, 14, 12, 10));
        JLabel who = new JLabel("👤 " + me);
        who.setFont(new Font("Segoe UI", Font.BOLD, 16));
        who.setForeground(Color.WHITE);
        JButton newGroup = new JButton("＋ Nhóm") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getModel().isRollover() ? new Color(240, 245, 255) : Color.WHITE);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        newGroup.setFont(new Font("Segoe UI", Font.BOLD, 13));
        newGroup.setForeground(new Color(0, 102, 204));
        newGroup.setContentAreaFilled(false);
        newGroup.setBorderPainted(false);
        newGroup.setFocusPainted(false);
        newGroup.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        newGroup.setPreferredSize(new Dimension(80, 28));
        newGroup.addActionListener(e -> createGroupDialog());
        top.add(who, BorderLayout.CENTER);
        top.add(newGroup, BorderLayout.EAST);

        convList.setFixedCellHeight(46);
        convList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        convList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean sel, boolean foc) {
                JLabel c = (JLabel) super.getListCellRendererComponent(l, v, i, sel, foc);
                Conv cv = (Conv) v;
                int n = unread.getOrDefault(cv.id(), 0);
                String badge = n > 0 ? "  <font color='red'>(" + n + ")</font>" : "";
                String name;
                switch (cv.kind()) {
                    case "BROADCAST" -> name = "📢 Mọi người (Broadcast)";
                    case "GROUP" -> name = "👥 " + cv.key() + " <font color='gray'>(Multicast)</font>";
                    default -> {
                        boolean on = onlineUsers.contains(cv.key());
                        name = "<font color='" + (on ? "#2ecc71" : "#aaaaaa") + "'>●</font> " + cv.key();
                    }
                }
                c.setText("<html>" + (n > 0 ? "<b>" : "") + name + badge + (n > 0 ? "</b>" : "") + "</html>");
                c.setFont(new Font("Segoe UI", Font.PLAIN, 14));
                c.setBorder(new EmptyBorder(0, 12, 0, 8));
                return c;
            }
        });
        convList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && !updatingList && convList.getSelectedValue() != null)
                select(convList.getSelectedValue());
        });

        side.add(top, BorderLayout.NORTH);
        side.add(new JScrollPane(convList), BorderLayout.CENTER);
        return side;
    }

    private JComponent buildChatArea() {
        JPanel area = new JPanel(new BorderLayout());

        JPanel header = new JPanel(new BorderLayout());
        header.setBorder(new EmptyBorder(10, 14, 10, 14));
        header.setBackground(Color.WHITE);
        title.setFont(new Font("Segoe UI", Font.BOLD, 17));
        subtitle.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        subtitle.setForeground(Color.GRAY);
        JPanel titles = new JPanel(new GridLayout(2, 1, 0, 2));
        titles.setOpaque(false);
        titles.add(title);
        titles.add(subtitle);
        header.add(titles, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout(6, 0));
        bottom.setBorder(new EmptyBorder(8, 10, 8, 10));
        bottom.setBackground(Color.WHITE);
        JButton emojiBtn = tool("😀", "Biểu tượng cảm xúc");
        JButton attachBtn = tool("📎", "Đính kèm ảnh / video / âm thanh / tệp");
        micBtn.setFont(EMOJI_FONT);
        micBtn.setToolTipText("Bấm để ghi âm tin nhắn thoại, bấm lần nữa để gửi");
        micBtn.setFocusable(false);
        JPanel tools = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        tools.setOpaque(false);
        tools.add(emojiBtn);
        tools.add(attachBtn);
        tools.add(micBtn);
        input.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        input.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(200, 200, 205)), new EmptyBorder(6, 8, 6, 8)));
        JButton send = createPrimaryButton("Gửi ➤");
        send.setPreferredSize(new Dimension(85, 36));
        bottom.add(tools, BorderLayout.WEST);
        bottom.add(input, BorderLayout.CENTER);
        bottom.add(send, BorderLayout.EAST);

        emojiBtn.addActionListener(e -> showEmojiPopup(emojiBtn));
        attachBtn.addActionListener(e -> attach());
        micBtn.addActionListener(e -> toggleRecord());
        send.addActionListener(e -> sendText());
        input.addActionListener(e -> sendText());

        area.add(header, BorderLayout.NORTH);
        area.add(scroll, BorderLayout.CENTER);
        area.add(bottom, BorderLayout.SOUTH);
        return area;
    }

    private JButton tool(String text, String tip) {
        JButton b = new JButton(text);
        b.setFont(EMOJI_FONT);
        b.setToolTipText(tip);
        b.setFocusable(false);
        return b;
    }

    private void showEmojiPopup(JComponent anchor) {
        JPopupMenu pop = new JPopupMenu();
        JPanel grid = new JPanel(new GridLayout(0, 8, 2, 2));
        for (String em : EMOJIS) {
            JButton b = new JButton(em);
            b.setFont(EMOJI_FONT);
            b.setMargin(new Insets(2, 4, 2, 4));
            b.setFocusable(false);
            b.setBorderPainted(false);
            b.setContentAreaFilled(false);
            b.addActionListener(e -> {
                input.replaceSelection(em);
                input.requestFocusInWindow();
            });
            grid.add(b);
        }
        pop.add(grid);
        pop.show(anchor, 0, -pop.getPreferredSize().height - 4);
    }

    // ================= Danh sách hội thoại =================

    private void refreshConvList() {
        updatingList = true;
        String sel = current == null ? null : current.id();
        convModel.clear();
        convModel.addElement(new Conv("BROADCAST", "*"));
        for (String g : groups.keySet()) convModel.addElement(new Conv("GROUP", g));
        List<String> users = new ArrayList<>(allUsers);
        users.remove(me);
        users.sort(Comparator.comparing((String u) -> !onlineUsers.contains(u)).thenComparing(String::compareToIgnoreCase));
        for (String u : users) convModel.addElement(new Conv("PRIVATE", u));
        for (int i = 0; i < convModel.size(); i++)
            if (convModel.get(i).id().equals(sel)) convList.setSelectedIndex(i);
        updatingList = false;
        convList.repaint();
        updateSubtitle();
    }

    private void select(Conv c) {
        current = c;
        unread.remove(c.id());
        convList.repaint();
        msgPanel.removeAll();
        shown.clear();
        msgPanel.revalidate();
        msgPanel.repaint();
        title.setText(convTitle(c));
        updateSubtitle();
        sendAsync(new Packet("HISTORY").put("kind", c.kind()).put("target", c.key()));
        input.requestFocusInWindow();
    }

    private String convTitle(Conv c) {
        return switch (c.kind()) {
            case "BROADCAST" -> "📢 Mọi người (Broadcast)";
            case "GROUP" -> "👥 " + c.key();
            default -> c.key();
        };
    }

    private void updateSubtitle() {
        if (current == null) return;
        subtitle.setText(switch (current.kind()) {
            case "BROADCAST" -> "Gửi tới tất cả người đang online (" + onlineUsers.size() + ")";
            case "GROUP" -> "Multicast tới: " + String.join(", ", groups.getOrDefault(current.key(), List.of()));
            default -> onlineUsers.contains(current.key()) ? "● Đang online" : "○ Offline";
        });
    }

    private String recipients() {
        return switch (current.kind()) {
            case "BROADCAST" -> "*";
            case "GROUP" -> String.join(",", groups.getOrDefault(current.key(), List.of()));
            default -> current.key();
        };
    }

    // ================= Nhận gói tin =================

    private void onPacket(Packet p) {
        switch (p.type) {
            case "USERS" -> {
                allUsers = new ArrayList<>(Arrays.asList(p.get("all").split(",")));
                allUsers.removeIf(String::isBlank);
                onlineUsers = new HashSet<>(Arrays.asList(p.get("online").split(",")));
                refreshConvList();
                if (current == null && convModel.size() > 0) convList.setSelectedIndex(0);
            }
            case "GROUPS" -> {
                groups.clear();
                for (String g : p.get("groups").split("\\|")) {
                    int i = g.indexOf('=');
                    if (i > 0) groups.put(g.substring(0, i), new ArrayList<>(Arrays.asList(g.substring(i + 1).split(","))));
                }
                refreshConvList();
            }
            case "MSG" -> onMessage(p);
            case "FILE_DATA" -> {
                Consumer<Packet> cb = pending.remove(p.getLong("rid"));
                if (cb != null) cb.accept(p);
            }
            case "ERR" -> showError(p.get("text"));
            case "INFO" -> JOptionPane.showMessageDialog(this, p.get("text"));
            case "DISCONNECTED" -> {
                JOptionPane.showMessageDialog(this, "Mất kết nối tới server", "Chat LTM", JOptionPane.ERROR_MESSAGE);
                media.shutdown();
                System.exit(0);
            }
            default -> {
            }
        }
    }

    private void onMessage(Packet m) {
        String kind = m.get("kind"), from = m.get("from");
        String key = switch (kind) {
            case "PRIVATE" -> from.equals(me) ? m.get("to") : from;
            case "GROUP" -> m.get("to");
            default -> "*";
        };
        Conv c = new Conv(kind, key);
        if (current != null && current.equals(c)) {
            addBubble(m);
        } else if (m.get("h").isEmpty() && !from.equals(me)) {
            unread.merge(c.id(), 1, Integer::sum);
            convList.repaint();
            Toolkit.getDefaultToolkit().beep();
        }
    }

    private void showError(String t) {
        JOptionPane.showMessageDialog(this, t, "Thông báo", JOptionPane.WARNING_MESSAGE);
    }

    // ================= Gửi =================

    private void sendAsync(Packet p) {
        new Thread(() -> {
            try {
                net.send(p);
            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> showError("Gửi thất bại: " + e.getMessage()));
            }
        }).start();
    }

    private void sendText() {
        String t = input.getText().trim();
        if (t.isEmpty() || current == null) return;
        input.setText("");
        sendAsync(new Packet("MSG").put("kind", current.kind()).put("to", current.key())
                .put("media", "TEXT").put("text", t));
    }

    private void sendFile(String mediaType, String fname, byte[] data) {
        if (current == null) return;
        sendAsync(new Packet("MSG").put("kind", current.kind()).put("to", current.key())
                .put("media", mediaType).put("fname", fname).put("text", "").data(data));
    }

    private void attach() {
        if (current == null) return;
        JFileChooser fc = new JFileChooser();
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File f = fc.getSelectedFile();
        if (f.length() > 50L * 1024 * 1024) {
            showError("Tệp quá lớn (tối đa 50MB)");
            return;
        }
        try {
            String n = f.getName().toLowerCase();
            String type = n.matches(".*\\.(png|jpe?g|gif|bmp)") ? "IMAGE"
                    : n.matches(".*\\.(mp4|avi|mkv|mov|webm|wmv)") ? "VIDEO"
                    : n.matches(".*\\.(wav|mp3|ogg|m4a|aac|flac)") ? "AUDIO" : "FILE";
            sendFile(type, f.getName(), Files.readAllBytes(f.toPath()));
        } catch (IOException e) {
            showError("Không đọc được tệp: " + e.getMessage());
        }
    }

    private void toggleRecord() {
        if (current == null) return;
        try {
            if (!media.isRecording()) {
                media.startRecording();
                micBtn.setText("⏹");
                micBtn.setBackground(new Color(255, 120, 120));
                micBtn.setToolTipText("Đang ghi âm... bấm để dừng và gửi");
            } else {
                micBtn.setText("🎤");
                micBtn.setBackground(null);
                byte[] wav = media.stopRecording();
                if (wav != null) sendFile("AUDIO", "voice_" + System.currentTimeMillis() + ".wav", wav);
            }
        } catch (Exception e) {
            showError("Lỗi ghi âm: " + e.getMessage());
        }
    }

    private void createGroupDialog() {
        JTextField name = new JTextField();
        List<String> others = new ArrayList<>(allUsers);
        others.remove(me);
        JList<String> list = new JList<>(others.toArray(new String[0]));
        JPanel p = new JPanel(new BorderLayout(0, 6));
        p.add(new JLabel("Tên nhóm (không dấu cách):"), BorderLayout.NORTH);
        JPanel mid = new JPanel(new BorderLayout(0, 6));
        mid.add(name, BorderLayout.NORTH);
        mid.add(new JLabel("Chọn thành viên (Ctrl để chọn nhiều):"), BorderLayout.CENTER);
        p.add(mid, BorderLayout.CENTER);
        JScrollPane sp = new JScrollPane(list);
        sp.setPreferredSize(new Dimension(260, 150));
        p.add(sp, BorderLayout.SOUTH);
        if (JOptionPane.showConfirmDialog(this, p, "Tạo nhóm (Multicast)", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION)
            return;
        sendAsync(new Packet("CREATE_GROUP").put("name", name.getText().trim())
                .put("members", String.join(",", list.getSelectedValuesList())));
    }

    // ================= Hiển thị tin nhắn =================

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>");
    }

    private JLabel textLabel(String text, Color fg) {
        JLabel l = new JLabel();
        l.setFont(FONT);
        l.setForeground(fg);
        FontMetrics fm = l.getFontMetrics(FONT);
        int w = 0;
        for (String line : text.split("\n")) w = Math.max(w, fm.stringWidth(line));
        w = Math.min(340, w + 12);
        l.setText("<html><body style='width:" + w + "px'>" + esc(text) + "</body></html>");
        return l;
    }

    private void fetch(long msgId, Consumer<Packet> cb) {
        long r = rid.incrementAndGet();
        pending.put(r, cb);
        sendAsync(new Packet("GET_FILE").put("id", msgId).put("rid", r));
    }

    private void addBubble(Packet m) {
        long id = m.getLong("id");
        if (!shown.add(id)) return;
        boolean mine = m.get("from").equals(me);
        Color fg = mine ? Color.WHITE : Color.BLACK;
        Bubble b = new Bubble(mine ? BLUE : new Color(228, 230, 235));

        if (!mine && !m.get("kind").equals("PRIVATE")) {
            JLabel n = new JLabel(m.get("from"));
            n.setFont(new Font("Segoe UI", Font.BOLD, 12));
            n.setForeground(new Color(90, 90, 200));
            b.add(n);
        }

        String media = m.get("media"), fname = m.get("fname");
        switch (media) {
            case "IMAGE" -> {
                JLabel img = new JLabel("Đang tải ảnh...");
                img.setForeground(fg);
                img.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                b.add(img);
                fetch(id, p -> {
                    try {
                        BufferedImage bi = ImageIO.read(new ByteArrayInputStream(p.data));
                        if (bi == null) throw new IOException("không đọc được ảnh");
                        img.setText(null);
                        img.setIcon(new ImageIcon(scaleDown(bi, 280, 280)));
                        img.addMouseListener(new java.awt.event.MouseAdapter() {
                            @Override
                            public void mouseClicked(java.awt.event.MouseEvent e) {
                                showFullImage(bi, fname);
                            }
                        });
                        layoutChanged();
                    } catch (IOException e) {
                        img.setText("[ảnh lỗi] " + fname);
                    }
                });
            }
            case "AUDIO" -> {
                JPanel audioBox = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
                audioBox.setOpaque(false);
                JButton play = new JButton("▶ " + (fname.startsWith("voice_") ? "Nghe voice" : fname));
                play.setFont(new Font("Segoe UI", Font.PLAIN, 13));
                play.setFocusable(false);
                play.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                play.addActionListener(e -> fetch(id, p -> playAudio(p.data, fname, play)));

                JButton dl = new JButton("💾 Tải về");
                dl.setFont(new Font("Segoe UI", Font.PLAIN, 13));
                dl.setFocusable(false);
                dl.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                dl.addActionListener(e -> fetch(id, p -> saveFile(fname, p.data)));

                audioBox.add(play);
                audioBox.add(dl);
                b.add(audioBox);
            }
            case "VIDEO" -> {
                JButton v = new JButton("🎬  " + fname + "  (bấm để phát)");
                v.setFont(new Font("Segoe UI", Font.PLAIN, 13));
                v.setFocusable(false);
                v.addActionListener(e -> fetch(id, p -> openTemp(fname, p.data)));
                b.add(v);
            }
            case "FILE" -> {
                JButton f = new JButton("📎  " + fname + "  (bấm để lưu)");
                f.setFont(new Font("Segoe UI", Font.PLAIN, 13));
                f.setFocusable(false);
                f.addActionListener(e -> fetch(id, p -> saveFile(fname, p.data)));
                b.add(f);
            }
            default -> b.add(textLabel(m.get("text"), fg));
        }

        JLabel time = new JLabel(m.get("time"));
        time.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        time.setForeground(mine ? new Color(215, 232, 255) : Color.GRAY);
        time.setAlignmentX(0f);
        b.add(Box.createVerticalStrut(2));
        b.add(time);
        for (Component c : b.getComponents()) if (c instanceof JComponent jc) jc.setAlignmentX(0f);

        JPanel row = new JPanel(new FlowLayout(mine ? FlowLayout.RIGHT : FlowLayout.LEFT, 10, 3)) {
            @Override
            public Dimension getMaximumSize() {
                return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
            }
        };
        row.setOpaque(false);
        row.add(b);
        msgPanel.add(row);
        layoutChanged();
    }

    private void layoutChanged() {
        msgPanel.revalidate();
        msgPanel.repaint();
        SwingUtilities.invokeLater(() -> {
            JScrollBar sb = scroll.getVerticalScrollBar();
            sb.setValue(sb.getMaximum());
        });
    }

    private static Image scaleDown(BufferedImage src, int maxW, int maxH) {
        double s = Math.min(1.0, Math.min((double) maxW / src.getWidth(), (double) maxH / src.getHeight()));
        if (s >= 1.0) return src;
        return src.getScaledInstance((int) (src.getWidth() * s), (int) (src.getHeight() * s), Image.SCALE_SMOOTH);
    }

    private void showFullImage(BufferedImage bi, String name) {
        Dimension scr = Toolkit.getDefaultToolkit().getScreenSize();
        JDialog d = new JDialog(this, name, false);
        d.add(new JScrollPane(new JLabel(new ImageIcon(scaleDown(bi, scr.width - 150, scr.height - 150)))));
        d.pack();
        d.setLocationRelativeTo(this);
        d.setVisible(true);
    }

    private void playAudio(byte[] data, String fname, JButton btn) {
        new Thread(() -> {
            String oldText = btn.getText();
            SwingUtilities.invokeLater(() -> btn.setText("🔊 Đang phát..."));
            try {
                try (ByteArrayInputStream bais = new ByteArrayInputStream(data);
                     AudioInputStream ais = AudioSystem.getAudioInputStream(bais)) {
                    AudioFormat baseFormat = ais.getFormat();
                    AudioFormat targetFormat = new AudioFormat(
                            AudioFormat.Encoding.PCM_SIGNED,
                            baseFormat.getSampleRate(),
                            16,
                            baseFormat.getChannels(),
                            baseFormat.getChannels() * 2,
                            baseFormat.getSampleRate(),
                            false
                    );
                    try (AudioInputStream dais = AudioSystem.getAudioInputStream(targetFormat, ais);
                         SourceDataLine line = AudioSystem.getSourceDataLine(targetFormat)) {
                        line.open(targetFormat);
                        line.start();
                        byte[] buf = new byte[4096];
                        int r;
                        while ((r = dais.read(buf, 0, buf.length)) != -1) {
                            line.write(buf, 0, r);
                        }
                        line.drain();
                        line.stop();
                    }
                }
            } catch (Exception e) {
                // Nếu Java Sound bị lỗi thiết bị, mở bằng trình phát hệ thống (Media Player/VLC...)
                SwingUtilities.invokeLater(() -> openTemp(fname, data));
            } finally {
                SwingUtilities.invokeLater(() -> btn.setText(oldText));
            }
        }).start();
    }

    private void openTemp(String fname, byte[] data) {
        try {
            File f = File.createTempFile("chat_", "_" + fname);
            f.deleteOnExit();
            Files.write(f.toPath(), data);
            Desktop.getDesktop().open(f);
        } catch (Exception e) {
            showError("Không mở được tệp: " + e.getMessage());
        }
    }

    private void saveFile(String fname, byte[] data) {
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new File(fname));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try {
            Files.write(fc.getSelectedFile().toPath(), data);
            JOptionPane.showMessageDialog(this, "Đã lưu: " + fc.getSelectedFile());
        } catch (IOException e) {
            showError("Không lưu được: " + e.getMessage());
        }
    }

    public static JButton createPrimaryButton(String text) {
        JButton b = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (!isEnabled()) {
                    g2.setColor(new Color(170, 195, 225));
                } else if (getModel().isPressed()) {
                    g2.setColor(new Color(0, 105, 217));
                } else if (getModel().isRollover()) {
                    g2.setColor(new Color(24, 144, 255));
                } else {
                    g2.setColor(new Color(0, 132, 255));
                }
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        b.setFont(new Font("Segoe UI", Font.BOLD, 14));
        b.setForeground(Color.WHITE);
        b.setContentAreaFilled(false);
        b.setBorderPainted(false);
        b.setFocusPainted(false);
        b.setOpaque(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }
}
