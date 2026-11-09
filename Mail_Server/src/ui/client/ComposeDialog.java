package ui.client;

import client.MailClientService;
import common.Attachment;
import common.MimeUtil;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Cửa sổ "Thư mới" giống Gmail: Đến / Cc / Bcc / Tiêu đề / Nội dung / Đính kèm.
 */
public class ComposeDialog extends JDialog {
    private static final long MAX_ATTACH = 15L * 1024 * 1024;
    private final MailClientService service;
    private final JTextField toField = new JTextField();
    private final JTextField ccField = new JTextField();
    private final JTextField bccField = new JTextField();
    private final JTextField subjectField = new JTextField();
    private final JTextArea bodyArea = new JTextArea();
    private final DefaultListModel<Attachment> attachModel = new DefaultListModel<>();
    private final JButton sendBtn = new JButton("Gửi");
    private String inReplyTo;
    private Runnable onSent = () -> {};

    public ComposeDialog(Frame owner, MailClientService service) {
        super(owner, "Thư mới", false);
        this.service = service;
        setSize(760, 620);
        setLocationRelativeTo(owner);
        buildUi();
    }

    private void buildUi() {
        JPanel root = new JPanel(new BorderLayout());
        JLabel title = new JLabel("  Thư mới");
        title.setOpaque(true);
        title.setBackground(new Color(0xF2, 0xF6, 0xFC));
        title.setFont(title.getFont().deriveFont(Font.BOLD, 14f));
        title.setPreferredSize(new Dimension(10, 36));
        root.add(title, BorderLayout.NORTH);

        JPanel fields = new JPanel(new GridBagLayout());
        fields.setBorder(new EmptyBorder(6, 10, 6, 10));
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(3, 3, 3, 3);
        g.fill = GridBagConstraints.HORIZONTAL;
        addRow(fields, g, 0, "Đến:", toField);
        addRow(fields, g, 1, "Cc:", ccField);
        addRow(fields, g, 2, "Bcc:", bccField);
        addRow(fields, g, 3, "Tiêu đề:", subjectField);
        JLabel hint = new JLabel("Nhiều địa chỉ cách nhau bằng dấu phẩy. Nội bộ: ten@" + service.getDomain()
                + "   |   Gmail: ten@gmail.com");
        hint.setForeground(Color.GRAY);
        g.gridx = 1; g.gridy = 4;
        fields.add(hint, g);

        bodyArea.setLineWrap(true);
        bodyArea.setWrapStyleWord(true);
        bodyArea.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        bodyArea.setBorder(new EmptyBorder(8, 8, 8, 8));

        JPanel center = new JPanel(new BorderLayout());
        center.add(fields, BorderLayout.NORTH);
        center.add(new JScrollPane(bodyArea), BorderLayout.CENTER);

        JList<Attachment> attachList = new JList<>(attachModel);
        attachList.setVisibleRowCount(3);
        JScrollPane attachScroll = new JScrollPane(attachList);
        attachScroll.setPreferredSize(new Dimension(10, 70));
        attachScroll.setBorder(BorderFactory.createTitledBorder("Tệp đính kèm"));
        center.add(attachScroll, BorderLayout.SOUTH);
        root.add(center, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        sendBtn.setBackground(new Color(0x0B, 0x57, 0xD0));
        sendBtn.setForeground(Color.WHITE);
        sendBtn.setFont(sendBtn.getFont().deriveFont(Font.BOLD, 13f));
        sendBtn.setPreferredSize(new Dimension(100, 34));
        JButton attachBtn = new JButton("📎 Đính kèm tệp");
        JButton removeBtn = new JButton("Bỏ tệp đã chọn");
        JButton discardBtn = new JButton("Hủy");
        bottom.add(sendBtn);
        bottom.add(attachBtn);
        bottom.add(removeBtn);
        bottom.add(discardBtn);
        root.add(bottom, BorderLayout.SOUTH);
        setContentPane(root);

        attachBtn.addActionListener(e -> chooseAttachments());
        removeBtn.addActionListener(e -> {
            for (Attachment a : attachList.getSelectedValuesList()) attachModel.removeElement(a);
        });
        discardBtn.addActionListener(e -> dispose());
        sendBtn.addActionListener(e -> send());
    }

    private void addRow(JPanel p, GridBagConstraints g, int row, String label, JComponent field) {
        g.gridx = 0; g.gridy = row; g.weightx = 0;
        p.add(new JLabel(label), g);
        g.gridx = 1; g.weightx = 1;
        p.add(field, g);
    }

    private void chooseAttachments() {
        JFileChooser fc = new JFileChooser();
        fc.setMultiSelectionEnabled(true);
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        for (File f : fc.getSelectedFiles()) {
            try {
                if (f.length() > MAX_ATTACH) {
                    JOptionPane.showMessageDialog(this, f.getName() + " vượt quá 15 MB");
                    continue;
                }
                String type = Files.probeContentType(f.toPath());
                attachModel.addElement(new Attachment(f.getName(), type, Files.readAllBytes(f.toPath())));
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Không đọc được tệp: " + ex.getMessage());
            }
        }
    }

    private void send() {
        List<String> to = MimeUtil.splitAddresses(toField.getText());
        List<String> cc = MimeUtil.splitAddresses(ccField.getText());
        List<String> bcc = MimeUtil.splitAddresses(bccField.getText());
        if (to.isEmpty() && cc.isEmpty() && bcc.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Hãy nhập ít nhất một người nhận.");
            return;
        }
        for (List<String> l : List.of(to, cc, bcc))
            for (String a : l)
                if (!MimeUtil.isValidEmail(MimeUtil.extractEmail(a))) {
                    JOptionPane.showMessageDialog(this, "Địa chỉ không hợp lệ: " + a);
                    return;
                }
        if (subjectField.getText().isBlank()
                && JOptionPane.showConfirmDialog(this, "Gửi thư không có tiêu đề?", "Xác nhận",
                JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;

        List<Attachment> atts = new ArrayList<>();
        for (int i = 0; i < attachModel.size(); i++) atts.add(attachModel.get(i));
        sendBtn.setEnabled(false);
        sendBtn.setText("Đang gửi...");
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                return service.send(to, cc, bcc, subjectField.getText(), bodyArea.getText(), atts, inReplyTo);
            }

            @Override
            protected void done() {
                try {
                    get();
                    JOptionPane.showMessageDialog(ComposeDialog.this, "Đã gửi thư thành công!", "Gửi thư",
                            JOptionPane.INFORMATION_MESSAGE);
                    onSent.run();
                    dispose();
                } catch (Exception ex) {
                    Throwable c = ex.getCause() == null ? ex : ex.getCause();
                    JOptionPane.showMessageDialog(ComposeDialog.this, "Gửi thất bại:\n" + c.getMessage(),
                            "Lỗi SMTP", JOptionPane.ERROR_MESSAGE);
                    sendBtn.setEnabled(true);
                    sendBtn.setText("Gửi");
                }
            }
        }.execute();
    }

    // ---- điền sẵn cho Trả lời / Chuyển tiếp ----
    public void prefill(String to, String cc, String subject, String body, String inReplyTo, List<Attachment> atts) {
        toField.setText(to == null ? "" : to);
        ccField.setText(cc == null ? "" : cc);
        subjectField.setText(subject == null ? "" : subject);
        bodyArea.setText(body == null ? "" : body);
        bodyArea.setCaretPosition(0);
        this.inReplyTo = inReplyTo;
        if (atts != null) atts.forEach(attachModel::addElement);
        setTitle(subject == null || subject.isBlank() ? "Thư mới" : subject);
    }

    public void setOnSent(Runnable r) { onSent = r == null ? () -> {} : r; }
}
