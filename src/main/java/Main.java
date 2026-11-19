import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class Main extends JFrame {

    // AIMMs pharmacy dashboard palette
    private static final Color BG_TAUPE = new Color(0xD5, 0xCB, 0xCB);
    private static final Color CARD_WHITE = Color.WHITE;
    private static final Color ACCENT_MAROON = new Color(0x7A, 0x12, 0x16);
    private static final Color CHART_RED = new Color(0xE0, 0x3E, 0x3E);
    private static final Color SEARCH_GRAY = new Color(0xEE, 0xEE, 0xEE);
    private static final Color TEXT_DARK = new Color(0x2B, 0x2B, 0x2B);
    private static final Color TEXT_MUTED = new Color(0x7A, 0x7A, 0x7A);
    private static final Color BORDER_LIGHT = new Color(0xC8, 0xC8, 0xC8);

    private final List<SidebarButton> navButtons = new ArrayList<>();
    private final JLabel activeViewTitle = new JLabel("Inventory Items (itemsDB.csv)");
    private final JTextArea dataPreviewArea = new JTextArea();
    private JPanel sidebarPanel;
    private boolean sidebarCollapsed = false;

    public Main() {
        setTitle("AIMMs Pharmacy System");
        setSize(1280, 760);
        setMinimumSize(new Dimension(1080, 680));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout(18, 0));
        root.setBackground(BG_TAUPE);
        root.setBorder(new EmptyBorder(18, 18, 18, 18));

        root.add(buildSidebar(), BorderLayout.WEST);
        root.add(buildRightWorkspace(), BorderLayout.CENTER);

        setContentPane(root);
        loadCsvPreview("data/inventory/itemsDB.csv", "Inventory Items");
    }

    // ==================== LEFT FLOATING SIDEBAR ====================
    private JPanel buildSidebar() {
        sidebarPanel = new RoundedPanel(22, CARD_WHITE, true);
        sidebarPanel.setPreferredSize(new Dimension(225, 0));
        sidebarPanel.setLayout(new BorderLayout());
        sidebarPanel.setBorder(new EmptyBorder(18, 14, 18, 14));

        // Brand Header + Collapse Button
        JPanel brandHeader = new JPanel(new BorderLayout(8, 0));
        brandHeader.setOpaque(false);

        JLabel logoBadge = new JLabel("Rx", SwingConstants.CENTER);
        logoBadge.setPreferredSize(new Dimension(34, 34));
        logoBadge.setFont(new Font("Segoe UI", Font.BOLD, 14));
        logoBadge.setForeground(ACCENT_MAROON);
        logoBadge.setBorder(BorderFactory.createLineBorder(ACCENT_MAROON, 1, true));

        JPanel titleStack = new JPanel(new GridLayout(2, 1));
        titleStack.setOpaque(false);
        JLabel appTitle = new JLabel("AIMMs Pharmacy");
        appTitle.setFont(new Font("Segoe UI", Font.BOLD, 12));
        appTitle.setForeground(ACCENT_MAROON);

        JLabel subTitle = new JLabel("Developed by AIMMs");
        subTitle.setFont(new Font("Segoe UI", Font.ITALIC, 10));
        subTitle.setForeground(ACCENT_MAROON);
        titleStack.add(appTitle);
        titleStack.add(subTitle);

        JButton collapseBtn = new JButton("<");
        collapseBtn.setFont(new Font("Segoe UI", Font.BOLD, 11));
        collapseBtn.setFocusPainted(false);
        collapseBtn.setBackground(CARD_WHITE);
        collapseBtn.setBorder(BorderFactory.createLineBorder(BORDER_LIGHT, 1, true));
        collapseBtn.setPreferredSize(new Dimension(24, 24));
        collapseBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        collapseBtn.addActionListener(e -> toggleSidebar(collapseBtn, titleStack));

        brandHeader.add(logoBadge, BorderLayout.WEST);
        brandHeader.add(titleStack, BorderLayout.CENTER);
        brandHeader.add(collapseBtn, BorderLayout.EAST);

        // Navigation Menu Groups (2 groups of 5 buttons matching wireframe)
        JPanel menuContainer = new JPanel();
        menuContainer.setOpaque(false);
        menuContainer.setLayout(new BoxLayout(menuContainer, BoxLayout.Y_AXIS));
        menuContainer.setBorder(new EmptyBorder(18, 0, 0, 0));

        menuContainer.add(createSectionLabel("MAIN OPERATIONS"));
        menuContainer.add(Box.createVerticalStrut(6));

        addNavItem(menuContainer, "Dashboard Overview", "data/transactions/transactionsDB.csv", false);
        addNavItem(menuContainer, "Inventory Items", "data/inventory/itemsDB.csv", true); // Active Maroon
        addNavItem(menuContainer, "Stock Batches", "data/inventory/batchDB.csv", false);
        addNavItem(menuContainer, "Prescriptions", "data/prescriptions/prescriptionsDB.csv", false);
        addNavItem(menuContainer, "Transactions", "data/transactions/transactionsDB.csv", false);

        menuContainer.add(Box.createVerticalStrut(16));
        menuContainer.add(createSectionLabel("ACCOUNTS & LOGS"));
        menuContainer.add(Box.createVerticalStrut(6));

        addNavItem(menuContainer, "Customers", "data/accounts/customerDB.csv", false);
        addNavItem(menuContainer, "Restock Requests", "data/inventory/restockRequests.csv", false);
        addNavItem(menuContainer, "Pharmacists", "data/accounts/pharmacistDB.csv", false);
        addNavItem(menuContainer, "Admins", "data/accounts/adminDB.csv", false);
        addNavItem(menuContainer, "Audit & Inventory Logs", "data/logs/inventoryLogs.csv", false);

        sidebarPanel.add(brandHeader, BorderLayout.NORTH);
        sidebarPanel.add(menuContainer, BorderLayout.CENTER);
        return sidebarPanel;
    }

    private void toggleSidebar(JButton btn, JPanel titleStack) {
        sidebarCollapsed = !sidebarCollapsed;
        sidebarPanel.setPreferredSize(new Dimension(sidebarCollapsed ? 68 : 225, 0));
        titleStack.setVisible(!sidebarCollapsed);
        btn.setText(sidebarCollapsed ? ">" : "<");
        for (SidebarButton nav : navButtons) {
            nav.setCollapsed(sidebarCollapsed);
        }
        sidebarPanel.revalidate();
        sidebarPanel.repaint();
    }

    private JLabel createSectionLabel(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(new Font("Segoe UI", Font.BOLD, 9));
        lbl.setForeground(TEXT_MUTED);
        lbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        return lbl;
    }

    private void addNavItem(JPanel container, String label, String csvPath, boolean active) {
        SidebarButton btn = new SidebarButton(label, active);
        btn.addActionListener(e -> {
            for (SidebarButton b : navButtons) b.setActive(false);
            btn.setActive(true);
            loadCsvPreview(csvPath, label);
        });
        navButtons.add(btn);
        container.add(btn);
        container.add(Box.createVerticalStrut(7));
    }

    // ==================== RIGHT WORKSPACE (TOP BAR + BENTO GRID) ====================
    private JPanel buildRightWorkspace() {
        JPanel workspace = new JPanel(new BorderLayout(0, 16));
        workspace.setOpaque(false);

        workspace.add(buildTopHeader(), BorderLayout.NORTH);
        workspace.add(buildBentoDashboard(), BorderLayout.CENTER);
        return workspace;
    }

    private JPanel buildTopHeader() {
        JPanel topBar = new JPanel(new BorderLayout(20, 0));
        topBar.setOpaque(false);

        // Pill Search Bar
        RoundedPanel searchPill = new RoundedPanel(24, SEARCH_GRAY, false);
        searchPill.setPreferredSize(new Dimension(380, 36));
        searchPill.setLayout(new BorderLayout(8, 0));
        searchPill.setBorder(new EmptyBorder(4, 14, 4, 14));

        JLabel searchIcon = new JLabel("Q");
        searchIcon.setFont(new Font("Segoe UI", Font.BOLD, 13));
        searchIcon.setForeground(TEXT_MUTED);

        JTextField searchField = new JTextField("Search medicines, batches, or prescriptions...");
        searchField.setBorder(null);
        searchField.setOpaque(false);
        searchField.setForeground(TEXT_MUTED);
        searchField.setFont(new Font("Segoe UI", Font.PLAIN, 12));

        searchPill.add(searchIcon, BorderLayout.WEST);
        searchPill.add(searchField, BorderLayout.CENTER);

        // Right Status Indicators & Notification Icons
        JPanel rightControls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 16, 4));
        rightControls.setOpaque(false);

        rightControls.add(makeStatusChip("Role: Pharmacist"));
        rightControls.add(makeStatusChip("Shift: Active"));
        rightControls.add(makeBadgeIcon("Mail", "3"));
        rightControls.add(makeBadgeIcon("Alerts", "1"));
        rightControls.add(makeBadgeIcon("Profile", "OK"));

        topBar.add(searchPill, BorderLayout.WEST);
        topBar.add(rightControls, BorderLayout.EAST);
        return topBar;
    }

    private JLabel makeStatusChip(String text) {
        JLabel chip = new JLabel(text);
        chip.setFont(new Font("Segoe UI", Font.BOLD, 12));
        chip.setForeground(TEXT_DARK);
        chip.setBorder(BorderFactory.createMatteBorder(0, 0, 2, 0, TEXT_DARK));
        return chip;
    }

    private JLabel makeBadgeIcon(String label, String badge) {
        JLabel icon = new JLabel(label + " [" + badge + "]");
        icon.setFont(new Font("Segoe UI", Font.BOLD, 12));
        icon.setForeground(ACCENT_MAROON);
        return icon;
    }

    // ==================== BENTO GRID LAYOUT ====================
    private JPanel buildBentoDashboard() {
        JPanel grid = new JPanel(new GridBagLayout());
        grid.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(0, 0, 14, 14);

        // Left Column Group (56% width): 3 KPI cards -> Wide Data Card -> Analytics Chart Card
        JPanel leftColumn = new JPanel(new BorderLayout(0, 14));
        leftColumn.setOpaque(false);

        // Top 3 KPI Cards
        JPanel kpiRow = new JPanel(new GridLayout(1, 3, 14, 0));
        kpiRow.setOpaque(false);
        kpiRow.setPreferredSize(new Dimension(0, 145));
        kpiRow.add(createKpiCard("Total Medicines", "142 Items", "+6 added this week"));
        kpiRow.add(createKpiCard("Prescription Reviews", "18 Records", "4 need pharmacist review"));
        kpiRow.add(createKpiCard("Today's Sales", "₱ 14,850", "32 transactions"));

        // Middle Wide Card (Tracked CSV Template Inspector)
        RoundedPanel middleWideCard = new RoundedPanel(20, CARD_WHITE, true);
        middleWideCard.setLayout(new BorderLayout(8, 8));
        middleWideCard.setBorder(new EmptyBorder(14, 16, 14, 16));

        activeViewTitle.setFont(new Font("Segoe UI", Font.BOLD, 14));
        activeViewTitle.setForeground(ACCENT_MAROON);

        dataPreviewArea.setEditable(false);
        dataPreviewArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        dataPreviewArea.setBackground(new Color(0xFA, 0xF8, 0xF8));
        dataPreviewArea.setBorder(new EmptyBorder(8, 8, 8, 8));

        middleWideCard.add(activeViewTitle, BorderLayout.NORTH);
        middleWideCard.add(new JScrollPane(dataPreviewArea), BorderLayout.CENTER);

        // Bottom Wide Card (Dispensing & Stock Trend Chart)
        RoundedPanel chartCard = new RoundedPanel(20, CARD_WHITE, true);
        chartCard.setPreferredSize(new Dimension(0, 165));
        chartCard.setLayout(new BorderLayout());
        chartCard.setBorder(new EmptyBorder(12, 16, 12, 16));
        JLabel chartTitle = new JLabel("Dispensing & Inventory Movement");
        chartTitle.setFont(new Font("Segoe UI", Font.BOLD, 12));
        chartTitle.setForeground(TEXT_DARK);
        chartCard.add(chartTitle, BorderLayout.NORTH);
        chartCard.add(new LineChartPanel(), BorderLayout.CENTER);

        leftColumn.add(kpiRow, BorderLayout.NORTH);
        leftColumn.add(middleWideCard, BorderLayout.CENTER);
        leftColumn.add(chartCard, BorderLayout.SOUTH);

        // Right Two Columns (44% width split evenly into 4 tall cards)
        JPanel rightTwoCols = new JPanel(new GridLayout(2, 2, 14, 14));
        rightTwoCols.setOpaque(false);
        rightTwoCols.add(createListCard("Expiring Batches", new String[]{
            "Batch #B-104 — Amoxicillin 500mg (14 days)",
            "Batch #B-109 — Cetirizine 10mg (21 days)",
            "Batch #B-112 — Losartan 50mg (30 days)"
        }));
        rightTwoCols.add(createListCard("Low Stock Alerts", new String[]{
            "Paracetamol 500mg — 12 boxes left",
            "Salbutamol Inhaler — 4 units left",
            "Metformin 500mg — 9 boxes left",
            "Omeprazole 20mg — 7 boxes left"
        }));
        rightTwoCols.add(createListCard("Recent Prescriptions", new String[]{
            "RX-2026-089 | External Rx -> Verified",
            "RX-2026-090 | External Rx -> Received",
            "RX-2026-091 | External Rx -> Partially dispensed",
            "RX-2026-092 | External Rx -> Needs clarification"
        }));
        rightTwoCols.add(createListCard("System Audit Logs", new String[]{
            "[08:15] Pharmacist01 logged in",
            "[08:22] Stock updated: batchDB.csv",
            "[08:40] Txn #TX-401 completed",
            "[09:05] Admin approved pharmacist account"
        }));

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0.56;
        gbc.weighty = 1.0;
        grid.add(leftColumn, gbc);

        gbc.gridx = 1;
        gbc.weightx = 0.44;
        gbc.insets = new Insets(0, 0, 14, 0);
        grid.add(rightTwoCols, gbc);

        return grid;
    }

    private JPanel createKpiCard(String title, String mainValue, String subNote) {
        RoundedPanel card = new RoundedPanel(20, CARD_WHITE, true);
        card.setLayout(new BorderLayout(0, 8));
        card.setBorder(new EmptyBorder(14, 16, 14, 16));

        JLabel titleLbl = new JLabel(title);
        titleLbl.setFont(new Font("Segoe UI", Font.BOLD, 12));
        titleLbl.setForeground(TEXT_MUTED);

        JLabel valueLbl = new JLabel(mainValue);
        valueLbl.setFont(new Font("Segoe UI", Font.BOLD, 20));
        valueLbl.setForeground(TEXT_DARK);

        JLabel noteLbl = new JLabel(subNote);
        noteLbl.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        noteLbl.setForeground(ACCENT_MAROON);

        card.add(titleLbl, BorderLayout.NORTH);
        card.add(valueLbl, BorderLayout.CENTER);
        card.add(noteLbl, BorderLayout.SOUTH);
        return card;
    }

    private JPanel createListCard(String heading, String[] items) {
        RoundedPanel card = new RoundedPanel(20, CARD_WHITE, true);
        card.setLayout(new BorderLayout(0, 10));
        card.setBorder(new EmptyBorder(16, 16, 16, 16));

        JLabel titleLbl = new JLabel(heading);
        titleLbl.setFont(new Font("Segoe UI", Font.BOLD, 13));
        titleLbl.setForeground(TEXT_DARK);

        JPanel listBox = new JPanel();
        listBox.setOpaque(false);
        listBox.setLayout(new BoxLayout(listBox, BoxLayout.Y_AXIS));
        for (String item : items) {
            JLabel row = new JLabel("• " + item);
            row.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            row.setForeground(TEXT_DARK);
            row.setBorder(new EmptyBorder(4, 0, 6, 0));
            listBox.add(row);
        }

        card.add(titleLbl, BorderLayout.NORTH);
        card.add(listBox, BorderLayout.CENTER);
        return card;
    }

    private void loadCsvPreview(String relativePath, String sectionName) {
        activeViewTitle.setText(sectionName + " — (" + relativePath + ")");
        try {
            File file = new File(relativePath);
            if (!file.exists()) {
                dataPreviewArea.setText("File not found: " + file.getAbsolutePath());
                return;
            }
            List<String> lines = Files.readAllLines(Path.of(relativePath));
            if (lines.isEmpty()) {
                dataPreviewArea.setText("(CSV file is currently empty)");
            } else {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < lines.size(); i++) {
                    sb.append(String.format("[%02d] %s%n", i, lines.get(i)));
                }
                dataPreviewArea.setText(sb.toString());
            }
        } catch (Exception ex) {
            dataPreviewArea.setText("Error reading CSV: " + ex.getMessage());
        }
    }

    // ==================== CUSTOM ROUNDED PANEL WITH SHADOW ====================
    static class RoundedPanel extends JPanel {
        private final int radius;
        private final Color bgColor;
        private final boolean shadow;

        RoundedPanel(int radius, Color bgColor, boolean shadow) {
            this.radius = radius;
            this.bgColor = bgColor;
            this.shadow = shadow;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int offset = shadow ? 4 : 0;
            if (shadow) {
                g2.setColor(new Color(0, 0, 0, 26));
                g2.fill(new RoundRectangle2D.Float(3, 4, getWidth() - 4, getHeight() - 4, radius, radius));
            }
            g2.setColor(bgColor);
            g2.fill(new RoundRectangle2D.Float(0, 0, getWidth() - offset, getHeight() - offset, radius, radius));
            g2.dispose();
            super.paintComponent(g);
        }
    }

    // ==================== CUSTOM SIDEBAR BUTTON ====================
    static class SidebarButton extends JButton {
        private boolean active;
        private final String fullLabel;

        SidebarButton(String text, boolean active) {
            super(text);
            this.fullLabel = text;
            this.active = active;
            setFont(new Font("Segoe UI", Font.BOLD, 11));
            setHorizontalAlignment(SwingConstants.LEFT);
            setFocusPainted(false);
            setContentAreaFilled(false);
            setBorder(new EmptyBorder(7, 10, 7, 10));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) { repaint(); }
                @Override
                public void mouseExited(MouseEvent e) { repaint(); }
            });
        }

        void setActive(boolean active) {
            this.active = active;
            repaint();
        }

        void setCollapsed(boolean collapsed) {
            setText(collapsed ? fullLabel.substring(0, 2).toUpperCase() : fullLabel);
            setHorizontalAlignment(collapsed ? SwingConstants.CENTER : SwingConstants.LEFT);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (active) {
                g2.setColor(ACCENT_MAROON);
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
                setForeground(Color.WHITE);
            } else {
                g2.setColor(getModel().isRollover() ? new Color(0xF5, 0xEE, 0xEE) : Color.WHITE);
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
                g2.setColor(BORDER_LIGHT);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
                setForeground(TEXT_DARK);
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }

    // ==================== CUSTOM RED AREA LINE CHART ====================
    static class LineChartPanel extends JPanel {
        private final double[] points = {0.82, 0.88, 0.92, 0.42, 0.55, 0.84, 0.28, 0.26, 0.66, 0.68, 0.55, 0.60, 0.48, 0.34, 0.52, 0.25};

        LineChartPanel() {
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth() - 20;
            int h = getHeight() - 16;
            int startX = 10;
            int startY = 8;
            double stepX = (double) w / (points.length - 1);

            Path2D linePath = new Path2D.Double();
            Path2D areaPath = new Path2D.Double();

            for (int i = 0; i < points.length; i++) {
                double x = startX + i * stepX;
                double y = startY + h - (points[i] * h);
                if (i == 0) {
                    linePath.moveTo(x, y);
                    areaPath.moveTo(x, startY + h);
                    areaPath.lineTo(x, y);
                } else {
                    linePath.lineTo(x, y);
                    areaPath.lineTo(x, y);
                }
            }
            areaPath.lineTo(startX + w, startY + h);
            areaPath.closePath();

            g2.setColor(new Color(0xE0, 0x3E, 0x3E, 32));
            g2.fill(areaPath);

            g2.setColor(CHART_RED);
            g2.setStroke(new BasicStroke(2.0f));
            g2.draw(linePath);
            g2.dispose();
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new Main().setVisible(true));
    }
}
