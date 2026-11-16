package browser;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;

/**
 * Giao diện Lịch sử duyệt web kiểu Google Chrome (chrome://history - Ctrl+H).
 */
public class HistoryDialog extends JDialog {

    private DefaultTableModel tableModel;
    private JTable table;
    private JTextField txtSearch;
    private JComboBox<String> cbMethodFilter;
    private JLabel lblCount;
    private final Consumer<String> onUrlSelected;

    public HistoryDialog(JFrame parent, Consumer<String> onUrlSelected) {
        super(parent, "Lịch sử duyệt web - Google Chrome History (Ctrl + H)", false);
        this.onUrlSelected = onUrlSelected;
        setSize(860, 520);
        setLocationRelativeTo(parent);
        setLayout(new BorderLayout(0, 0));

        initUI();
        filter();
    }

    private void initUI() {
        // HEADER CHROME HISTORY
        JPanel topBar = new JPanel(new BorderLayout(12, 8));
        topBar.setBackground(new Color(248, 249, 250));
        topBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(218, 220, 224)),
                BorderFactory.createEmptyBorder(12, 16, 12, 16)
        ));

        JLabel lblTitle = new JLabel("🕒 Lịch sử duyệt web (Chrome History)");
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 16));
        lblTitle.setForeground(new Color(32, 33, 36));

        JPanel searchPanel = new JPanel(new BorderLayout(8, 0));
        searchPanel.setOpaque(false);

        txtSearch = new JTextField();
        txtSearch.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        txtSearch.setToolTipText("Tìm kiếm lịch sử theo tiêu đề trang, URL, hoặc thời gian...");
        txtSearch.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(203, 213, 225), 1, true),
                BorderFactory.createEmptyBorder(6, 10, 6, 10)
        ));
        txtSearch.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) { filter(); }
            @Override
            public void removeUpdate(DocumentEvent e) { filter(); }
            @Override
            public void changedUpdate(DocumentEvent e) { filter(); }
        });

        cbMethodFilter = new JComboBox<>(new String[]{"Tất cả", "GET", "HEAD", "POST"});
        cbMethodFilter.setFont(new Font("Segoe UI", Font.BOLD, 12));
        cbMethodFilter.addActionListener(e -> filter());

        JPanel filterRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        filterRight.setOpaque(false);
        filterRight.add(new JLabel("Phương thức:"));
        filterRight.add(cbMethodFilter);

        searchPanel.add(new JLabel("🔍 Tìm trong lịch sử: "), BorderLayout.WEST);
        searchPanel.add(txtSearch, BorderLayout.CENTER);
        searchPanel.add(filterRight, BorderLayout.EAST);

        topBar.add(lblTitle, BorderLayout.WEST);
        topBar.add(searchPanel, BorderLayout.CENTER);
        add(topBar, BorderLayout.NORTH);

        // BẢNG LỊCH SỬ
        tableModel = new DefaultTableModel(new Object[]{
                "Thời gian truy cập", "HTTP Method", "Tiêu đề trang / Kết quả", "Địa chỉ URL"
        }, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        table = new JTable(tableModel);
        table.setRowHeight(28);
        table.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        table.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        table.getColumnModel().getColumn(0).setPreferredWidth(145);
        table.getColumnModel().getColumn(1).setPreferredWidth(90);
        table.getColumnModel().getColumn(2).setPreferredWidth(235);
        table.getColumnModel().getColumn(3).setPreferredWidth(360);

        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    openSelectedUrl();
                }
            }
        });

        add(new JScrollPane(table), BorderLayout.CENTER);

        // THANH CÔNG CỤ DƯỚI
        JPanel bottomBar = new JPanel(new BorderLayout(10, 0));
        bottomBar.setBackground(new Color(248, 249, 250));
        bottomBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(218, 220, 224)),
                BorderFactory.createEmptyBorder(8, 14, 8, 14)
        ));

        lblCount = new JLabel("Tổng số: 0 bản ghi");
        lblCount.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblCount.setForeground(new Color(95, 99, 104));

        JPanel btnGroup = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        btnGroup.setOpaque(false);

        JButton btnOpen = UiUtil.createColoredButton(" Mở URL đã chọn ", new Color(26, 115, 232), Color.WHITE, 6);
        btnOpen.addActionListener(e -> openSelectedUrl());

        JButton btnRefresh = UiUtil.createOutlineButton("Làm mới", new Color(203, 213, 225), new Color(30, 41, 59));
        btnRefresh.addActionListener(e -> filter());

        JButton btnClear = UiUtil.createColoredButton(" Xóa toàn bộ lịch sử ", new Color(220, 38, 38), Color.WHITE, 6);
        btnClear.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(this,
                    "Bạn có chắc chắn muốn xóa toàn bộ lịch sử duyệt web?",
                    "Xác nhận xóa lịch sử", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                HistoryManager.clear();
                filter();
            }
        });

        JButton btnClose = UiUtil.createOutlineButton("Đóng", new Color(203, 213, 225), new Color(30, 41, 59));
        btnClose.addActionListener(e -> setVisible(false));

        btnGroup.add(btnOpen);
        btnGroup.add(btnRefresh);
        btnGroup.add(btnClear);
        btnGroup.add(btnClose);

        bottomBar.add(lblCount, BorderLayout.WEST);
        bottomBar.add(btnGroup, BorderLayout.EAST);
        add(bottomBar, BorderLayout.SOUTH);
    }

    private void filter() {
        String query = txtSearch.getText().trim();
        String method = (String) cbMethodFilter.getSelectedItem();
        tableModel.setRowCount(0);
        List<HistoryManager.HistoryEntry> list = HistoryManager.search(query, method);
        for (HistoryManager.HistoryEntry e : list) {
            tableModel.addRow(new Object[]{e.getTimestamp(), e.getMethod(), e.getTitle(), e.getUrl()});
        }
        lblCount.setText("Hiển thị: " + list.size() + " mục lịch sử (Nhấp đúp vào dòng bất kỳ để mở lại URL)");
    }

    private void openSelectedUrl() {
        int row = table.getSelectedRow();
        if (row != -1) {
            String url = (String) tableModel.getValueAt(row, 3);
            if (onUrlSelected != null && url != null) {
                onUrlSelected.accept(url);
                setVisible(false);
            }
        } else {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn một mục trong bảng lịch sử để mở.");
        }
    }
}
