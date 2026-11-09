package ui.client;

import common.Attachment;
import common.ParsedMail;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.io.File;
import java.io.FileOutputStream;
import java.util.function.Consumer;

/**
 * Khung hiển thị chi tiết một bức thư (giao diện đọc thư tương tự Gmail):
 * - Tiêu đề lớn
 * - Thông tin người gửi (Avatar, tên hiển thị, địa chỉ email, ngày giờ, người nhận)
 * - Thanh tác vụ: Trả lời (Reply), Chuyển tiếp (Forward), Xóa (Delete), Xem thô (Raw)
 * - Danh sách tệp đính kèm với nút "Tải về"
 * - Nội dung thư (hỗ trợ văn bản thường hoặc định dạng HTML)
 */
public class MailDetailPanel extends JPanel {
    private final JLabel lblSubject = new JLabel();
    private final JLabel lblAvatar = new JLabel();
    private final JLabel lblSenderName = new JLabel();
    private final JLabel lblSenderEmail = new JLabel();
    private final JLabel lblDate = new JLabel();
    private final JLabel lblRecipients = new JLabel();
    private final JEditorPane txtBody = new JEditorPane();
    private final JPanel pnlAttachments = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
    private final JScrollPane scrollAttachments = new JScrollPane(pnlAttachments);
    private final JPanel pnlActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));

    private final JButton btnReply = new JButton("↩ Trả lời");
    private final JButton btnForward = new JButton("↪ Chuyển tiếp");
    private final JButton btnDelete = new JButton("🗑 Xóa");
    private final JButton btnRaw = new JButton("👁 Xem Raw RFC822");

    private ParsedMail currentMail;
    private String currentFolder = "INBOX";
    private String currentUid;
    private String rawMessageContent = "";

    private Consumer<ParsedMail> onReplyHandler;
    private Consumer<ParsedMail> onForwardHandler;
    private Runnable onDeleteHandler;

    public MailDetailPanel() {
        setLayout(new BorderLayout());
        setBackground(Color.WHITE);
        initUi();
        clear();
    }

    private void initUi() {
        // --- 1. HEADER PANEL ---
        JPanel pnlHeader = new JPanel(new BorderLayout(0, 8));
        pnlHeader.setBackground(Color.WHITE);
        pnlHeader.setBorder(new EmptyBorder(16, 20, 12, 20));

        // Dòng tiêu đề thư
        lblSubject.setFont(new Font("Segoe UI", Font.BOLD, 18));
        lblSubject.setForeground(new Color(0x20, 0x21, 0x24));

        // Thanh công cụ trên cùng
        styleButton(btnReply, new Color(0x1A, 0x73, 0xE8));
        styleButton(btnForward, new Color(0x5F, 0x63, 0x68));
        styleButton(btnDelete, new Color(0xD9, 0x30, 0x25));
        styleButton(btnRaw, new Color(0x5F, 0x63, 0x68));

        pnlActions.setOpaque(false);
        pnlActions.add(btnReply);
        pnlActions.add(btnForward);
        pnlActions.add(btnDelete);
        pnlActions.add(btnRaw);

        JPanel pnlTopRow = new JPanel(new BorderLayout());
        pnlTopRow.setOpaque(false);
        pnlTopRow.add(lblSubject, BorderLayout.CENTER);
        pnlTopRow.add(pnlActions, BorderLayout.EAST);
        pnlHeader.add(pnlTopRow, BorderLayout.NORTH);

        // Thông tin người gửi
        JPanel pnlSender = new JPanel(new BorderLayout(12, 0));
        pnlSender.setOpaque(false);

        // Avatar tròn (chữ cái đầu)
        lblAvatar.setPreferredSize(new Dimension(42, 42));
        lblAvatar.setHorizontalAlignment(SwingConstants.CENTER);
        lblAvatar.setVerticalAlignment(SwingConstants.CENTER);
        lblAvatar.setOpaque(true);
        lblAvatar.setBackground(new Color(0xEA, 0x43, 0x35));
        lblAvatar.setForeground(Color.WHITE);
        lblAvatar.setFont(new Font("Segoe UI", Font.BOLD, 18));
        lblAvatar.setBorder(new LineBorder(new Color(0xE0, 0xE0, 0xE0), 1, true));
        pnlSender.add(lblAvatar, BorderLayout.WEST);

        JPanel pnlSenderMeta = new JPanel(new GridLayout(2, 1, 0, 2));
        pnlSenderMeta.setOpaque(false);

        JPanel pnlNameRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        pnlNameRow.setOpaque(false);
        lblSenderName.setFont(new Font("Segoe UI", Font.BOLD, 14));
        lblSenderName.setForeground(new Color(0x20, 0x21, 0x24));
        lblSenderEmail.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblSenderEmail.setForeground(new Color(0x5F, 0x63, 0x68));
        pnlNameRow.add(lblSenderName);
        pnlNameRow.add(lblSenderEmail);

        JPanel pnlToRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        pnlToRow.setOpaque(false);
        lblRecipients.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblRecipients.setForeground(new Color(0x5F, 0x63, 0x68));
        pnlToRow.add(lblRecipients);

        pnlSenderMeta.add(pnlNameRow);
        pnlSenderMeta.add(pnlToRow);
        pnlSender.add(pnlSenderMeta, BorderLayout.CENTER);

        // Ngày giờ góc phải
        lblDate.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblDate.setForeground(new Color(0x5F, 0x63, 0x68));
        pnlSender.add(lblDate, BorderLayout.EAST);

        pnlHeader.add(pnlSender, BorderLayout.CENTER);

        // Đường kẻ phân cách
        JSeparator sep = new JSeparator();
        sep.setForeground(new Color(0xEA, 0xEA, 0xEA));
        pnlHeader.add(sep, BorderLayout.SOUTH);

        add(pnlHeader, BorderLayout.NORTH);

        // --- 2. BODY PANEL ---
        txtBody.setEditable(false);
        txtBody.setContentType("text/plain");
        txtBody.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        txtBody.setBorder(new EmptyBorder(16, 20, 16, 20));
        txtBody.setBackground(Color.WHITE);

        JScrollPane scrollBody = new JScrollPane(txtBody);
        scrollBody.setBorder(null);
        scrollBody.getViewport().setBackground(Color.WHITE);
        add(scrollBody, BorderLayout.CENTER);

        // --- 3. ATTACHMENTS PANEL (DƯỚI CÙNG) ---
        pnlAttachments.setBackground(new Color(0xF8, 0xF9, 0xFA));
        scrollAttachments.setBorder(new CompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(0xEA, 0xEA, 0xEA)),
                new EmptyBorder(6, 12, 6, 12)
        ));
        scrollAttachments.setPreferredSize(new Dimension(10, 80));
        scrollAttachments.getViewport().setBackground(new Color(0xF8, 0xF9, 0xFA));
        add(scrollAttachments, BorderLayout.SOUTH);

        // Sự kiện các nút tác vụ
        btnReply.addActionListener(e -> {
            if (currentMail != null && onReplyHandler != null) onReplyHandler.accept(currentMail);
        });
        btnForward.addActionListener(e -> {
            if (currentMail != null && onForwardHandler != null) onForwardHandler.accept(currentMail);
        });
        btnDelete.addActionListener(e -> {
            if (onDeleteHandler != null) onDeleteHandler.run();
        });
        btnRaw.addActionListener(e -> showRawDialog());
    }

    private void styleButton(JButton btn, Color c) {
        btn.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    /** Xóa trắng giao diện khi không chọn thư nào */
    public void clear() {
        currentMail = null;
        currentUid = null;
        rawMessageContent = "";
        lblSubject.setText("Chọn một thư để xem nội dung");
        lblSubject.setForeground(new Color(0x80, 0x86, 0x8B));
        lblAvatar.setText("");
        lblAvatar.setBackground(new Color(0xBD, 0xC1, 0xC6));
        lblSenderName.setText("");
        lblSenderEmail.setText("");
        lblRecipients.setText("");
        lblDate.setText("");
        txtBody.setContentType("text/plain");
        txtBody.setText("\n\n        Hãy chọn một bức thư từ danh sách bên cạnh để đọc nội dung chi tiết.");
        pnlAttachments.removeAll();
        scrollAttachments.setVisible(false);
        pnlActions.setVisible(false);
        revalidate();
        repaint();
    }

    /** Hiển thị một bức thư đã parse đầy đủ */
    public void showMail(ParsedMail mail, String folder, String uid, String raw) {
        this.currentMail = mail;
        this.currentFolder = folder;
        this.currentUid = uid;
        this.rawMessageContent = raw != null ? raw : "";

        pnlActions.setVisible(true);
        String subject = mail.getSubject();
        lblSubject.setText(subject == null || subject.isBlank() ? "(Không có tiêu đề)" : subject);
        lblSubject.setForeground(new Color(0x20, 0x21, 0x24));

        String from = mail.getFrom();
        String senderEmail = common.MimeUtil.extractEmail(from);
        String senderName = common.MimeUtil.displayName(from);
        if (senderName.isBlank()) senderName = senderEmail;

        lblSenderName.setText(senderName);
        lblSenderEmail.setText("<" + senderEmail + ">");

        // Avatar màu ngẫu nhiên dựa theo tên
        String initial = senderName.length() > 0 ? senderName.substring(0, 1).toUpperCase() : "M";
        lblAvatar.setText(initial);
        int hash = Math.abs(senderEmail.hashCode());
        Color[] avatarColors = {
                new Color(0xEA, 0x43, 0x35), new Color(0x42, 0x85, 0xF4),
                new Color(0x34, 0xA8, 0x53), new Color(0xFB, 0xBC, 0x05),
                new Color(0x67, 0x3A, 0xB7), new Color(0x00, 0x96, 0x88)
        };
        lblAvatar.setBackground(avatarColors[hash % avatarColors.length]);

        lblRecipients.setText("đến: " + mail.getTo() + (mail.getCc().isBlank() ? "" : " | Cc: " + mail.getCc()));
        lblDate.setText(mail.getDate());

        // Nội dung thư (ưu tiên HTML nếu có, nếu không thì Text)
        if (!mail.getHtmlBody().isBlank()) {
            txtBody.setContentType("text/html");
            txtBody.setText(mail.getHtmlBody());
        } else {
            txtBody.setContentType("text/plain");
            txtBody.setText(mail.getDisplayText());
        }
        txtBody.setCaretPosition(0);

        // Tệp đính kèm
        pnlAttachments.removeAll();
        if (mail.getAttachments().isEmpty()) {
            scrollAttachments.setVisible(false);
        } else {
            scrollAttachments.setVisible(true);
            JLabel lblAttachCount = new JLabel("📎 " + mail.getAttachments().size() + " tệp đính kèm: ");
            lblAttachCount.setFont(new Font("Segoe UI", Font.BOLD, 12));
            pnlAttachments.add(lblAttachCount);

            for (Attachment a : mail.getAttachments()) {
                JPanel card = createAttachmentCard(a);
                pnlAttachments.add(card);
            }
        }

        revalidate();
        repaint();
    }

    private JPanel createAttachmentCard(Attachment a) {
        JPanel p = new JPanel(new BorderLayout(6, 0));
        p.setBackground(Color.WHITE);
        p.setBorder(new CompoundBorder(
                new LineBorder(new Color(0xDA, 0xDC, 0xE0), 1, true),
                new EmptyBorder(4, 8, 4, 8)
        ));

        JLabel nameLbl = new JLabel(a.getFileName() + " (" + a.getSizeText() + ")");
        nameLbl.setFont(new Font("Segoe UI", Font.PLAIN, 12));

        JButton btnSave = new JButton("💾 Lưu");
        btnSave.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        btnSave.setMargin(new Insets(2, 6, 2, 6));
        btnSave.addActionListener(e -> saveAttachment(a));

        p.add(nameLbl, BorderLayout.CENTER);
        p.add(btnSave, BorderLayout.EAST);
        return p;
    }

    private void saveAttachment(Attachment a) {
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new File(a.getFileName()));
        if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File target = fc.getSelectedFile();
            try (FileOutputStream fos = new FileOutputStream(target)) {
                fos.write(a.getData());
                JOptionPane.showMessageDialog(this, "Đã lưu tệp tại:\n" + target.getAbsolutePath(),
                        "Lưu thành công", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Không thể lưu tệp: " + ex.getMessage(),
                        "Lỗi", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void showRawDialog() {
        JDialog dlg = new JDialog(SwingUtilities.getWindowAncestor(this), "Nội dung gốc (RFC 822 / MIME)", Dialog.ModalityType.MODELESS);
        dlg.setSize(750, 550);
        dlg.setLocationRelativeTo(this);
        JTextArea ta = new JTextArea(rawMessageContent);
        ta.setFont(new Font("Consolas", Font.PLAIN, 12));
        ta.setEditable(false);
        ta.setCaretPosition(0);
        dlg.add(new JScrollPane(ta));
        dlg.setVisible(true);
    }

    public void setOnReply(Consumer<ParsedMail> c) { this.onReplyHandler = c; }
    public void setOnForward(Consumer<ParsedMail> c) { this.onForwardHandler = c; }
    public void setOnDelete(Runnable r) { this.onDeleteHandler = r; }

    public ParsedMail getCurrentMail() { return currentMail; }
    public String getCurrentUid() { return currentUid; }
}
