package browser;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
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

public class HistoryDialog extends JDialog {

    private DefaultTableModel tableModel;
    private JTable table;
    private JTextField txtSearch;
    private Consumer<String> onUrlSelected;

    public HistoryDialog(JFrame parent, Consumer<String> onUrlSelected) {
        super(parent, "Lịch sử duyệt web (History - Tương tự Chrome/Cốc Cốc)", false);
        this.onUrlSelected = onUrlSelected;
        setSize(780, 500);
        setLocationRelativeTo(parent);
        setLayout(new BorderLayout(8, 8));

        initUI();
        loadData("");
    }

    private void initUI() {
        // TOP SEARCH BAR
        JPanel topBar = new JPanel(new BorderLayout(8, 8));
        topBar.setBorder(BorderFactory.createEmptyBorder(10, 12, 6, 12));

        JLabel lblTitle = new JLabel("Lịch sử duyệt web");
        lblTitle.setFont(new Font("SansSerif", Font.BOLD, 15));

        JPanel searchPanel = new JPanel(new BorderLayout(6, 0));
        searchPanel.add(new JLabel("Tìm kiếm: "), BorderLayout.WEST);
        txtSearch = new JTextField();
        txtSearch.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) { filter(); }
            @Override
            public void removeUpdate(DocumentEvent e) { filter(); }
            @Override
            public void changedUpdate(DocumentEvent e) { filter(); }
        });
        searchPanel.add(txtSearch, BorderLayout.CENTER);

        topBar.add(lblTitle, BorderLayout.WEST);
        topBar.add(searchPanel, BorderLayout.CENTER);
        add(topBar, BorderLayout.NORTH);

        // TABLE
        tableModel = new DefaultTableModel(new Object[]{"Thời gian", "Phương thức", "Tiêu đề / Trạng thái", "Địa chỉ URL"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        table = new JTable(tableModel);
        table.setRowHeight(24);
        table.getColumnModel().getColumn(0).setPreferredWidth(140);
        table.getColumnModel().getColumn(1).setPreferredWidth(70);
        table.getColumnModel().getColumn(2).setPreferredWidth(200);
        table.getColumnModel().getColumn(3).setPreferredWidth(350);

        // Double click to open URL
        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    openSelectedUrl();
                }
            }
        });

        add(new JScrollPane(table), BorderLayout.CENTER);

        // BOTTOM BAR
        JPanel bottomBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        JButton btnOpen = new JButton("Mở trang này");
        btnOpen.setBackground(new Color(26, 115, 232));
        btnOpen.setForeground(Color.WHITE);
        btnOpen.addActionListener(e -> openSelectedUrl());

        JButton btnClear = new JButton("Xóa toàn bộ lịch sử");
        btnClear.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(this, "Bạn có chắc chắn muốn xóa toàn bộ lịch sử?", "Xác nhận", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                HistoryManager.clear();
                loadData("");
            }
        });

        JButton btnClose = new JButton("Đóng");
        btnClose.addActionListener(e -> setVisible(false));

        bottomBar.add(btnOpen);
        bottomBar.add(btnClear);
        bottomBar.add(btnClose);
        add(bottomBar, BorderLayout.SOUTH);
    }

    private void filter() {
        loadData(txtSearch.getText().trim());
    }

    private void loadData(String query) {
        tableModel.setRowCount(0);
        List<HistoryManager.HistoryEntry> list = HistoryManager.search(query);
        for (HistoryManager.HistoryEntry e : list) {
            tableModel.addRow(new Object[]{e.getTimestamp(), e.getMethod(), e.getTitle(), e.getUrl()});
        }
    }

    private void openSelectedUrl() {
        int row = table.getSelectedRow();
        if (row != -1) {
            String url = (String) tableModel.getValueAt(row, 3);
            if (onUrlSelected != null && url != null) {
                onUrlSelected.accept(url);
                setVisible(false);
            }
        }
    }
}
