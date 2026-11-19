package ui;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.regex.Pattern;
import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.RowFilter;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.Scrollable;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;
import model.inventory.ItemStatus;
import service.PharmacyScreenService;
import service.PharmacyScreenService.Metric;
import service.PharmacyScreenService.Screen;
import service.PharmacyScreenService.ScreenData;
import service.AuthenticationService;
import service.AuthenticationService.UserSession;
import controller.PharmacyScreenController;
import model.account.UserRole;

/** Desktop workspace for inventory, account review, and pharmacy audit screens. */
public final class PharmacyAppFrame extends JFrame {

    private static final Color CANVAS = new Color(0xF4, 0xEF, 0xED);
    private static final Color WHITE = Color.WHITE;
    private static final Color MAROON = new Color(0x7A, 0x12, 0x16);
    private static final Color TEXT = new Color(0x29, 0x23, 0x22);
    private static final Color MUTED = new Color(0x82, 0x79, 0x76);
    private static final Color BORDER = new Color(0xE6, 0xDE, 0xDB);
    private static final Color SOFT = new Color(0xFA, 0xF7, 0xF6);
    private static final Color SKELETON = new Color(0xE9, 0xE5, 0xE3);
    private static final Font UI_FONT = new Font("Segoe UI", Font.PLAIN, 13);

    private final PharmacyScreenController controller = new PharmacyScreenController();
    private final AuthenticationService authenticationService = new AuthenticationService();
    private final Map<Screen, NavButton> navButtons = new EnumMap<>(Screen.class);
    private final Map<String, CartLine> cartItems = new LinkedHashMap<>();
    private final JPanel pageHost = new ViewportPanel();
    private final JTextField searchField = new JTextField();
    private Screen currentScreen = Screen.DASHBOARD;
    private ScreenData currentData;
    private UserSession currentUser;
    private final boolean demoMode;
    private JPanel cartLinesPanel;
    private JLabel cartTotalLabel;
    private JLabel cartCountLabel;
    private boolean searchListenerAdded;
    private JTable currentTable;
    private TableRowSorter<DefaultTableModel> currentSorter;
    private long requestGeneration;
    private boolean workspaceBuilt;

    public PharmacyAppFrame() {
        this(false);
    }

    public PharmacyAppFrame(boolean demoMode) {
        super("AIMMs Pharmacy System");
        this.demoMode = demoMode;
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1440, 940);
        setMinimumSize(new Dimension(1180, 740));
        setLocationRelativeTo(null);
        getContentPane().setBackground(CANVAS);
        setContentPane(buildLoginPanel());
    }

    public void loadInitialData() {
        setContentPane(buildLoginPanel());
        revalidate();
        repaint();
    }

    private JPanel buildLoginPanel() {
        JPanel background = new JPanel(new GridBagLayout());
        background.setBackground(CANVAS);
        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(WHITE);
        card.setPreferredSize(new Dimension(900, 540));
        card.setBorder(BorderFactory.createLineBorder(BORDER));

        JPanel welcome = new JPanel();
        welcome.setBackground(MAROON);
        welcome.setPreferredSize(new Dimension(330, 540));
        welcome.setBorder(BorderFactory.createEmptyBorder(44, 38, 38, 38));
        welcome.setLayout(new BoxLayout(welcome, BoxLayout.Y_AXIS));
        JLabel logo = new JLabel("Rx", SwingConstants.CENTER);
        logo.setOpaque(true);
        logo.setBackground(WHITE);
        logo.setForeground(MAROON);
        logo.setFont(new Font("Segoe UI", Font.BOLD, 16));
        logo.setPreferredSize(new Dimension(46, 46));
        logo.setMaximumSize(new Dimension(46, 46));
        logo.setAlignmentX(Component.LEFT_ALIGNMENT);
        welcome.add(logo);
        JLabel brand = new JLabel("AIMMs Pharmacy");
        brand.setFont(new Font("Segoe UI", Font.BOLD, 14));
        brand.setForeground(WHITE);
        brand.setBorder(BorderFactory.createEmptyBorder(17, 0, 0, 0));
        brand.setAlignmentX(Component.LEFT_ALIGNMENT);
        welcome.add(brand);
        JLabel tagline = new JLabel("COUNTER SYSTEM");
        tagline.setFont(new Font("Segoe UI", Font.BOLD, 9));
        tagline.setForeground(new Color(0xF1, 0xD8, 0xD7));
        tagline.setAlignmentX(Component.LEFT_ALIGNMENT);
        welcome.add(Box.createVerticalStrut(4));
        welcome.add(tagline);
        JLabel headline = new JLabel("<html>Good care starts<br>with a clear counter.</html>");
        headline.setFont(new Font("Segoe UI", Font.BOLD, 27));
        headline.setForeground(WHITE);
        headline.setAlignmentX(Component.LEFT_ALIGNMENT);
        headline.setBorder(BorderFactory.createEmptyBorder(43, 0, 0, 0));
        welcome.add(headline);
        JLabel description = new JLabel("<html>Manage medicine, prescriptions, and sales<br>in one pharmacy workspace.</html>");
        description.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        description.setForeground(new Color(0xF5, 0xE7, 0xE6));
        description.setAlignmentX(Component.LEFT_ALIGNMENT);
        description.setBorder(BorderFactory.createEmptyBorder(13, 0, 0, 0));
        welcome.add(description);
        welcome.add(Box.createVerticalGlue());
        JLabel privacy = new JLabel("Staff access · Pharmacy records");
        privacy.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        privacy.setForeground(new Color(0xF1, 0xD8, 0xD7));
        privacy.setAlignmentX(Component.LEFT_ALIGNMENT);
        welcome.add(privacy);
        card.add(welcome, BorderLayout.WEST);

        JPanel form = new JPanel();
        form.setBackground(WHITE);
        form.setBorder(BorderFactory.createEmptyBorder(46, 48, 38, 48));
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        JLabel eyebrow = new JLabel("STAFF WORKSPACE");
        eyebrow.setFont(new Font("Segoe UI", Font.BOLD, 10));
        eyebrow.setForeground(MAROON);
        JLabel title = new JLabel("Sign in");
        title.setFont(new Font("Segoe UI", Font.BOLD, 25));
        title.setForeground(TEXT);
        title.setBorder(BorderFactory.createEmptyBorder(7, 0, 5, 0));
        JLabel instruction = new JLabel("Use your administrator or pharmacist account.");
        instruction.setFont(UI_FONT);
        instruction.setForeground(MUTED);
        form.add(eyebrow);
        form.add(title);
        form.add(instruction);
        form.add(Box.createVerticalStrut(27));

        JLabel usernameLabel = new JLabel("Username");
        styleFormLabel(usernameLabel);
        JTextField username = new JTextField();
        styleLoginField(username);
        username.getAccessibleContext().setAccessibleName("Username");
        JLabel passwordLabel = new JLabel("Password");
        styleFormLabel(passwordLabel);
        passwordLabel.setBorder(BorderFactory.createEmptyBorder(17, 0, 7, 0));
        JPasswordField password = new JPasswordField();
        styleLoginField(password);
        password.getAccessibleContext().setAccessibleName("Password");
        JCheckBox showPassword = new JCheckBox("Show password");
        showPassword.setBackground(WHITE);
        showPassword.setForeground(MUTED);
        showPassword.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        char echo = password.getEchoChar();
        showPassword.addActionListener(event -> password.setEchoChar(showPassword.isSelected() ? '\0' : echo));
        form.add(usernameLabel);
        form.add(Box.createVerticalStrut(7));
        form.add(username);
        form.add(passwordLabel);
        form.add(password);
        showPassword.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
        form.add(showPassword);

        JLabel message = new JLabel(" ");
        message.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        message.setForeground(new Color(0xA7, 0x38, 0x35));
        message.setBorder(BorderFactory.createEmptyBorder(6, 0, 3, 0));
        form.add(message);
        JButton signIn = primaryButton("Sign in");
        signIn.setAlignmentX(Component.LEFT_ALIGNMENT);
        signIn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        signIn.addActionListener(event -> {
            signIn.setEnabled(false);
            signIn.setText("Signing in…");
            message.setForeground(MUTED);
            message.setText("Checking your staff account…");
            String enteredUsername = username.getText();
            char[] enteredPassword = password.getPassword();
            new SwingWorker<UserSession, Void>() {
                @Override
                protected UserSession doInBackground() {
                    return authenticationService.authenticate(enteredUsername, enteredPassword);
                }

                @Override
                protected void done() {
                    try {
                        enterWorkspace(get());
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        showLoginError(message, password, "Sign in was interrupted. Please try again.");
                    } catch (ExecutionException ex) {
                        Throwable cause = ex.getCause();
                        if (cause instanceof AuthenticationService.AuthenticationException authError) {
                            showLoginError(message, password, authError.getMessage());
                        } else {
                            showLoginError(message, password,
                                    "Staff records could not be read. Check the account CSV files.");
                        }
                    } finally {
                        signIn.setEnabled(true);
                        signIn.setText("Sign in");
                    }
                }
            }.execute();
        });
        form.add(Box.createVerticalStrut(8));
        form.add(signIn);
        form.add(Box.createVerticalStrut(19));
        JLabel note = new JLabel("<html>Access is based on your approved staff account.<br>Need access? Contact a pharmacy administrator.</html>");
        note.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        note.setForeground(MUTED);
        form.add(note);
        if (demoMode) {
            JLabel demo = new JLabel("<html><b>Demo access</b><br>Admin: admin · Pharmacist: pharmacist<br>Password for both: AIMMsDemo1!</html>");
            demo.setFont(new Font("Segoe UI", Font.PLAIN, 10));
            demo.setForeground(new Color(0x68, 0x5D, 0x59));
            demo.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(BORDER), BorderFactory.createEmptyBorder(10, 12, 10, 12)));
            demo.setAlignmentX(Component.LEFT_ALIGNMENT);
            demo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 70));
            form.add(Box.createVerticalStrut(18));
            form.add(demo);
        }
        card.add(form, BorderLayout.CENTER);
        background.add(card);
        getRootPane().setDefaultButton(signIn);
        return background;
    }

    private void showLoginError(JLabel message, JPasswordField password, String text) {
        message.setForeground(new Color(0xA7, 0x38, 0x35));
        message.setText(text);
        password.setText("");
        password.requestFocusInWindow();
    }

    private void enterWorkspace(UserSession user) {
        currentUser = user;
        currentScreen = currentUser.role() == UserRole.ADMIN ? Screen.DASHBOARD : Screen.PHARMACIST_HOME;
        cartItems.clear();
        searchField.setText("");
        navButtons.clear();
        workspaceBuilt = false;
        buildWorkspace();
        openScreen(currentScreen);
    }

    private void styleFormLabel(JLabel label) {
        label.setFont(new Font("Segoe UI", Font.BOLD, 11));
        label.setForeground(TEXT);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
    }

    private void styleLoginField(JTextField field) {
        field.setFont(UI_FONT);
        field.setForeground(TEXT);
        field.setBackground(SOFT);
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER), BorderFactory.createEmptyBorder(10, 12, 10, 12)));
        field.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        field.setAlignmentX(Component.LEFT_ALIGNMENT);
    }

    private void signOut() {
        requestGeneration++;
        currentUser = null;
        currentData = null;
        currentTable = null;
        currentSorter = null;
        cartItems.clear();
        workspaceBuilt = false;
        navButtons.clear();
        setContentPane(buildLoginPanel());
        revalidate();
        repaint();
    }

    private void buildWorkspace() {
        JPanel root = new JPanel(new BorderLayout(0, 0));
        root.setBackground(CANVAS);
        root.add(buildSidebar(), BorderLayout.WEST);

        JPanel workspace = new JPanel(new BorderLayout(0, 0));
        workspace.setOpaque(false);
        workspace.add(buildTopBar(), BorderLayout.NORTH);

        pageHost.setOpaque(false);
        JScrollPane pageScroll = new JScrollPane(pageHost);
        pageScroll.setBorder(BorderFactory.createEmptyBorder());
        pageScroll.getViewport().setBackground(CANVAS);
        pageScroll.getVerticalScrollBar().setUnitIncrement(18);
        pageScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        workspace.add(pageScroll, BorderLayout.CENTER);
        root.add(workspace, BorderLayout.CENTER);

        setContentPane(root);
        workspaceBuilt = true;
        revalidate();
        repaint();
    }

    private JPanel buildSidebar() {
        JPanel sidebar = new JPanel(new BorderLayout());
        sidebar.setBackground(WHITE);
        sidebar.setPreferredSize(new Dimension(244, 0));
        sidebar.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, BORDER));

        JPanel brand = new JPanel(new BorderLayout(10, 0));
        brand.setBackground(WHITE);
        brand.setBorder(BorderFactory.createEmptyBorder(20, 18, 18, 16));
        JLabel logo = new JLabel("Rx", SwingConstants.CENTER);
        logo.setOpaque(true);
        logo.setBackground(MAROON);
        logo.setForeground(WHITE);
        logo.setFont(new Font("Segoe UI", Font.BOLD, 14));
        logo.setPreferredSize(new Dimension(38, 38));
        logo.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        JPanel brandText = new JPanel();
        brandText.setOpaque(false);
        brandText.setLayout(new BoxLayout(brandText, BoxLayout.Y_AXIS));
        JLabel appName = new JLabel("AIMMs Pharmacy");
        appName.setFont(new Font("Segoe UI", Font.BOLD, 13));
        appName.setForeground(MAROON);
        JLabel workspaceLabel = new JLabel("COUNTER SYSTEM");
        workspaceLabel.setFont(new Font("Segoe UI", Font.BOLD, 9));
        workspaceLabel.setForeground(MUTED);
        brandText.add(appName);
        brandText.add(Box.createVerticalStrut(3));
        brandText.add(workspaceLabel);
        brand.add(logo, BorderLayout.WEST);
        brand.add(brandText, BorderLayout.CENTER);
        sidebar.add(brand, BorderLayout.NORTH);

        JPanel menu = new JPanel();
        menu.setBackground(WHITE);
        menu.setLayout(new BoxLayout(menu, BoxLayout.Y_AXIS));
        menu.setBorder(BorderFactory.createEmptyBorder(2, 14, 16, 14));
        addSection(menu, currentUser != null && currentUser.role() == UserRole.PHARMACIST
                ? "COUNTER WORKSPACE" : "MAIN OPERATIONS");
        if (currentUser != null && currentUser.role() == UserRole.PHARMACIST) {
            addNav(menu, Screen.PHARMACIST_HOME, "Dashboard", "D");
            addNav(menu, Screen.COUNTER_SALE, "Counter sale", "S");
            addNav(menu, Screen.INVENTORY_ITEMS, "Inventory items", "I");
            addNav(menu, Screen.STOCK_BATCHES, "Stock batches", "B");
            addNav(menu, Screen.RESTOCK_REQUESTS, "Restock requests", "R");
            addNav(menu, Screen.PRESCRIPTIONS, "Prescriptions", "Rx");
            addNav(menu, Screen.TRANSACTIONS, "Transactions", "T");
            menu.add(Box.createVerticalStrut(16));
            addSection(menu, "ACCOUNTS & LOGS");
            addNav(menu, Screen.CUSTOMERS, "Customers", "C");
            addNav(menu, Screen.AUDIT_LOGS, "Audit & inventory logs", "L");
        } else {
            addNav(menu, Screen.DASHBOARD, "Dashboard", "D");
            addNav(menu, Screen.INVENTORY_ITEMS, "Inventory items", "I");
            addNav(menu, Screen.STOCK_BATCHES, "Stock batches", "B");
            addNav(menu, Screen.RESTOCK_REQUESTS, "Restock requests", "R");
            addNav(menu, Screen.PRESCRIPTIONS, "Prescriptions", "Rx");
            addNav(menu, Screen.TRANSACTIONS, "Transactions", "T");
            menu.add(Box.createVerticalStrut(16));
            addSection(menu, "ACCOUNTS & LOGS");
            addNav(menu, Screen.CUSTOMERS, "Customers", "C");
            addNav(menu, Screen.PHARMACISTS, "Pharmacist accounts", "P");
            addNav(menu, Screen.ADMINS, "Admin accounts", "A");
            addNav(menu, Screen.AUDIT_LOGS, "Audit & inventory logs", "L");
        }

        JScrollPane menuScroll = new JScrollPane(menu);
        menuScroll.setBorder(BorderFactory.createEmptyBorder());
        menuScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        menuScroll.getVerticalScrollBar().setUnitIncrement(14);
        menuScroll.getViewport().setBackground(WHITE);
        sidebar.add(menuScroll, BorderLayout.CENTER);

        JPanel profile = new JPanel(new BorderLayout(10, 0));
        profile.setBackground(SOFT);
        profile.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        JLabel avatar = new JLabel("AD", SwingConstants.CENTER);
        avatar.setOpaque(true);
        avatar.setBackground(new Color(0xF0, 0xDE, 0xDD));
        avatar.setForeground(MAROON);
        avatar.setFont(new Font("Segoe UI", Font.BOLD, 10));
        avatar.setPreferredSize(new Dimension(32, 32));
        JPanel profileLabels = new JPanel();
        profileLabels.setOpaque(false);
        profileLabels.setLayout(new BoxLayout(profileLabels, BoxLayout.Y_AXIS));
        String accountName = currentUser == null ? "Staff" : currentUser.fullName();
        String roleName = currentUser == null ? "Pharmacy staff" : roleLabel(currentUser.role());
        JLabel preview = new JLabel(accountName);
        preview.setFont(new Font("Segoe UI", Font.BOLD, 12));
        preview.setForeground(TEXT);
        JLabel csvData = new JLabel(roleName);
        csvData.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        csvData.setForeground(MUTED);
        profileLabels.add(preview);
        profileLabels.add(Box.createVerticalStrut(2));
        profileLabels.add(csvData);
        profile.add(avatar, BorderLayout.WEST);
        profile.add(profileLabels, BorderLayout.CENTER);
        JButton signOut = new JButton("Sign out");
        signOut.setFont(new Font("Segoe UI", Font.BOLD, 9));
        signOut.setForeground(MAROON);
        signOut.setBackground(WHITE);
        signOut.setBorder(BorderFactory.createEmptyBorder(6, 4, 6, 2));
        signOut.setFocusPainted(true);
        signOut.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        signOut.addActionListener(event -> signOut());
        profile.add(signOut, BorderLayout.EAST);
        JPanel profileWrap = new JPanel(new BorderLayout());
        profileWrap.setBackground(WHITE);
        profileWrap.setBorder(BorderFactory.createEmptyBorder(10, 14, 14, 14));
        profileWrap.add(profile, BorderLayout.CENTER);
        sidebar.add(profileWrap, BorderLayout.SOUTH);
        return sidebar;
    }

    private void addSection(JPanel menu, String title) {
        JLabel section = new JLabel(title);
        section.setFont(new Font("Segoe UI", Font.BOLD, 9));
        section.setForeground(MUTED);
        section.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 0));
        section.setAlignmentX(Component.LEFT_ALIGNMENT);
        menu.add(section);
    }

    private void addNav(JPanel menu, Screen screen, String label, String glyph) {
        NavButton button = new NavButton(label, glyph);
        button.setAlignmentX(Component.LEFT_ALIGNMENT);
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        button.setPreferredSize(new Dimension(212, 36));
        button.setActive(screen == currentScreen);
        button.addActionListener(event -> openScreen(screen));
        navButtons.put(screen, button);
        menu.add(button);
        menu.add(Box.createVerticalStrut(4));
    }

    private JPanel buildTopBar() {
        JPanel topBar = new JPanel(new BorderLayout(16, 0));
        topBar.setBackground(WHITE);
        topBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER),
                BorderFactory.createEmptyBorder(15, 26, 15, 24)));

        JPanel search = new JPanel(new BorderLayout(9, 0));
        search.setBackground(SOFT);
        search.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0xEF, 0xE9, 0xE6)),
                BorderFactory.createEmptyBorder(0, 12, 0, 12)));
        search.setPreferredSize(new Dimension(390, 38));
        JLabel searchIcon = new JLabel("⌕");
        searchIcon.setForeground(MUTED);
        searchIcon.setFont(new Font("Segoe UI Symbol", Font.PLAIN, 18));
        searchField.setBorder(BorderFactory.createEmptyBorder());
        searchField.setOpaque(false);
        searchField.setForeground(TEXT);
        searchField.setFont(UI_FONT);
        searchField.setToolTipText("Search rows on the current screen");
        searchField.setText("");
        if (!searchListenerAdded) {
            searchField.getDocument().addDocumentListener(new DocumentListener() {
                @Override public void insertUpdate(DocumentEvent event) { applySearch(); }
                @Override public void removeUpdate(DocumentEvent event) { applySearch(); }
                @Override public void changedUpdate(DocumentEvent event) { applySearch(); }
            });
            searchListenerAdded = true;
        }
        search.add(searchIcon, BorderLayout.WEST);
        search.add(searchField, BorderLayout.CENTER);
        topBar.add(search, BorderLayout.WEST);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        right.setOpaque(false);
        JLabel role = new JLabel(currentUser == null ? "Pharmacy staff" : roleLabel(currentUser.role()));
        role.setFont(new Font("Segoe UI", Font.BOLD, 11));
        role.setForeground(TEXT);
        role.setOpaque(true);
        role.setBackground(CANVAS);
        role.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        JLabel avatar = new JLabel(initials(currentUser == null ? "Staff" : currentUser.fullName()), SwingConstants.CENTER);
        avatar.setOpaque(true);
        avatar.setBackground(new Color(0xF0, 0xDE, 0xDD));
        avatar.setForeground(MAROON);
        avatar.setFont(new Font("Segoe UI", Font.BOLD, 10));
        avatar.setPreferredSize(new Dimension(32, 32));
        right.add(role);
        right.add(avatar);
        topBar.add(right, BorderLayout.EAST);
        return topBar;
    }

    private void openScreen(Screen screen) {
        currentScreen = screen;
        currentData = null;
        navButtons.forEach((key, button) -> button.setActive(key == screen));
        currentTable = null;
        currentSorter = null;
        showPageComponent(buildLoadingPage(screen));

        long generation = ++requestGeneration;
        SwingWorker<ScreenData, Void> worker = new SwingWorker<>() {
            @Override
            protected ScreenData doInBackground() {
                return controller.load(screen);
            }

            @Override
            protected void done() {
                if (generation != requestGeneration) {
                    return;
                }
                try {
                    renderPage(screen, get());
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    renderError(screen, "Loading was interrupted. Try again.");
                } catch (ExecutionException ex) {
                    renderError(screen, readableError(ex.getCause()));
                }
            }
        };
        worker.execute();
    }

    private void renderPage(Screen screen, ScreenData data) {
        if (!workspaceBuilt) {
            buildWorkspace();
        }
        currentScreen = screen;
        currentData = data;
        currentTable = null;
        currentSorter = null;
        if (screen == Screen.COUNTER_SALE) {
            showPageComponent(buildCounterSalePage(data));
            applySearch();
            return;
        }
        JPanel page = new JPanel(new GridBagLayout());
        page.setOpaque(false);
        page.setBorder(BorderFactory.createEmptyBorder(24, 28, 30, 28));
        GridBagConstraints pageConstraints = new GridBagConstraints();
        pageConstraints.gridx = 0;
        pageConstraints.gridy = 0;
        pageConstraints.weightx = 1;
        pageConstraints.fill = GridBagConstraints.HORIZONTAL;
        pageConstraints.anchor = GridBagConstraints.FIRST_LINE_START;
        pageConstraints.insets = new Insets(0, 0, 18, 0);
        page.add(buildPageHeading(screen), pageConstraints);
        if (!data.metrics().isEmpty()) {
            pageConstraints.gridy++;
            page.add(buildMetricRow(data.metrics()), pageConstraints);
        }
        pageConstraints.gridy++;
        pageConstraints.weighty = 1;
        pageConstraints.fill = GridBagConstraints.BOTH;
        pageConstraints.insets = new Insets(0, 0, 0, 0);
        page.add(buildTableCard(screen, data), pageConstraints);
        showPageComponent(page);
        applySearch();
    }

    private JPanel buildPageHeading(Screen screen) {
        JPanel heading = new JPanel(new BorderLayout(16, 0));
        heading.setOpaque(false);
        JPanel labels = new JPanel();
        labels.setOpaque(false);
        labels.setLayout(new BoxLayout(labels, BoxLayout.Y_AXIS));
        JLabel title = new JLabel(screenTitle(screen));
        title.setFont(new Font("Segoe UI", Font.BOLD, 25));
        title.setForeground(TEXT);
        JLabel subtitle = new JLabel(screenSubtitle(screen));
        subtitle.setFont(UI_FONT);
        subtitle.setForeground(MUTED);
        subtitle.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));
        labels.add(title);
        labels.add(subtitle);
        heading.add(labels, BorderLayout.CENTER);

        if (screen == Screen.INVENTORY_ITEMS) {
            JButton add = primaryButton("＋ Add medicine");
            add.addActionListener(event -> openAddMedicineDialog());
            heading.add(add, BorderLayout.EAST);
        } else if (screen == Screen.PHARMACISTS) {
            JButton review = secondaryButton("Review selected");
            review.addActionListener(event -> openSelectedPharmacist());
            heading.add(review, BorderLayout.EAST);
        } else if (screen == Screen.PHARMACIST_HOME) {
            JButton sale = primaryButton("＋ New sale");
            sale.addActionListener(event -> openScreen(Screen.COUNTER_SALE));
            heading.add(sale, BorderLayout.EAST);
        } else if (screen == Screen.RESTOCK_REQUESTS && currentUser != null
                && currentUser.role() == UserRole.ADMIN) {
            JButton approve = primaryButton("Approve / receive selected");
            approve.addActionListener(event -> approveSelectedRestockRequest());
            heading.add(approve, BorderLayout.EAST);
        } else if (screen == Screen.RESTOCK_REQUESTS && currentUser != null
                && currentUser.role() == UserRole.PHARMACIST) {
            JButton request = primaryButton("＋ New request");
            request.addActionListener(event -> openRestockRequestPreview());
            heading.add(request, BorderLayout.EAST);
        }
        return heading;
    }

    private JPanel buildCounterSalePage(ScreenData data) {
        JPanel page = new JPanel(new GridBagLayout());
        page.setOpaque(false);
        page.setBorder(BorderFactory.createEmptyBorder(24, 28, 30, 28));
        GridBagConstraints pageConstraints = new GridBagConstraints();
        pageConstraints.gridx = 0;
        pageConstraints.gridy = 0;
        pageConstraints.weightx = 1;
        pageConstraints.fill = GridBagConstraints.HORIZONTAL;
        pageConstraints.anchor = GridBagConstraints.FIRST_LINE_START;
        pageConstraints.insets = new Insets(0, 0, 18, 0);
        page.add(buildPageHeading(Screen.COUNTER_SALE), pageConstraints);

        JPanel columns = new JPanel(new GridLayout(1, 2, 16, 0));
        columns.setOpaque(false);
        int columnHeight = Math.max(480, getHeight() - 285);
        columns.setPreferredSize(new Dimension(0, columnHeight));
        columns.setMinimumSize(new Dimension(0, columnHeight));
        columns.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        JPanel catalogCard = cardPanel(new BorderLayout(0, 12));
        catalogCard.setBorder(BorderFactory.createEmptyBorder(18, 18, 16, 18));
        JPanel catalogHeader = new JPanel();
        catalogHeader.setOpaque(false);
        catalogHeader.setLayout(new BoxLayout(catalogHeader, BoxLayout.Y_AXIS));
        JLabel catalogTitle = new JLabel("Find medicine");
        catalogTitle.setFont(new Font("Segoe UI", Font.BOLD, 15));
        catalogTitle.setForeground(TEXT);
        JLabel catalogNote = new JLabel("Select an item to add it to this sale.");
        catalogNote.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        catalogNote.setForeground(MUTED);
        catalogNote.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
        catalogHeader.add(catalogTitle);
        catalogHeader.add(catalogNote);
        catalogCard.add(catalogHeader, BorderLayout.NORTH);

        JTable catalog = buildTable(data);
        catalog.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        catalog.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        JScrollPane catalogScroll = new JScrollPane(catalog);
        catalogScroll.setBorder(BorderFactory.createEmptyBorder());
        catalogScroll.getViewport().setBackground(WHITE);
        catalogCard.add(catalogScroll, BorderLayout.CENTER);
        JButton addItem = primaryButton("Add selected medicine");
        addItem.addActionListener(event -> addSelectedToSale(catalog));
        JPanel catalogFooter = new JPanel(new BorderLayout());
        catalogFooter.setOpaque(false);
        JLabel fefoNote = new JLabel("Eligible batches are allocated by earliest expiry.");
        fefoNote.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        fefoNote.setForeground(MUTED);
        catalogFooter.add(fefoNote, BorderLayout.NORTH);
        JPanel addWrap = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 10));
        addWrap.setOpaque(false);
        addWrap.add(addItem);
        catalogFooter.add(addWrap, BorderLayout.SOUTH);
        catalogCard.add(catalogFooter, BorderLayout.SOUTH);
        columns.add(catalogCard);

        JPanel cartCard = cardPanel(new BorderLayout(0, 12));
        cartCard.setBorder(BorderFactory.createEmptyBorder(18, 18, 16, 18));
        JPanel cartHeader = new JPanel(new BorderLayout(0, 12));
        cartHeader.setOpaque(false);
        JPanel cartTitleStack = new JPanel();
        cartTitleStack.setOpaque(false);
        cartTitleStack.setLayout(new BoxLayout(cartTitleStack, BoxLayout.Y_AXIS));
        JLabel saleTitle = new JLabel("Current sale");
        saleTitle.setFont(new Font("Segoe UI", Font.BOLD, 15));
        saleTitle.setForeground(TEXT);
        cartCountLabel = new JLabel("0 items");
        cartCountLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        cartCountLabel.setForeground(MUTED);
        cartTitleStack.add(saleTitle);
        cartTitleStack.add(Box.createVerticalStrut(4));
        cartTitleStack.add(cartCountLabel);
        cartHeader.add(cartTitleStack, BorderLayout.NORTH);
        JTextField customer = new JTextField();
        customer.setFont(UI_FONT);
        customer.setForeground(TEXT);
        customer.setToolTipText("Optional active customer ID to associate with this sale");
        customer.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER), BorderFactory.createEmptyBorder(8, 10, 8, 10)));
        JPanel customerField = new JPanel(new BorderLayout(0, 5));
        customerField.setOpaque(false);
        JLabel customerLabel = new JLabel("Customer ID · optional");
        customerLabel.setFont(new Font("Segoe UI", Font.BOLD, 10));
        customerLabel.setForeground(MUTED);
        customerField.add(customerLabel, BorderLayout.NORTH);
        customerField.add(customer, BorderLayout.CENTER);
        cartHeader.add(customerField, BorderLayout.SOUTH);
        cartCard.add(cartHeader, BorderLayout.NORTH);

        cartLinesPanel = new JPanel();
        cartLinesPanel.setOpaque(false);
        cartLinesPanel.setLayout(new BoxLayout(cartLinesPanel, BoxLayout.Y_AXIS));
        JScrollPane cartScroll = new JScrollPane(cartLinesPanel);
        cartScroll.setBorder(BorderFactory.createEmptyBorder());
        cartScroll.getViewport().setBackground(WHITE);
        cartScroll.getVerticalScrollBar().setUnitIncrement(14);
        cartCard.add(cartScroll, BorderLayout.CENTER);

        JPanel cartFooter = new JPanel();
        cartFooter.setOpaque(false);
        cartFooter.setLayout(new BoxLayout(cartFooter, BoxLayout.Y_AXIS));
        JPanel totalRow = new JPanel(new BorderLayout());
        totalRow.setOpaque(false);
        JLabel totalCaption = new JLabel("Estimated total");
        totalCaption.setFont(new Font("Segoe UI", Font.BOLD, 12));
        totalCaption.setForeground(TEXT);
        cartTotalLabel = new JLabel("₱ 0.00");
        cartTotalLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
        cartTotalLabel.setForeground(TEXT);
        totalRow.add(totalCaption, BorderLayout.WEST);
        totalRow.add(cartTotalLabel, BorderLayout.EAST);
        cartFooter.add(totalRow);
        JLabel previewNotice = new JLabel("<html>Completion saves transaction, stock, and audit records<br>to this computer's runtime CSV files.</html>");
        previewNotice.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        previewNotice.setForeground(MUTED);
        previewNotice.setBorder(BorderFactory.createEmptyBorder(7, 0, 10, 0));
        cartFooter.add(previewNotice);
        JButton reviewSale = primaryButton("Review and complete sale");
        reviewSale.setAlignmentX(Component.LEFT_ALIGNMENT);
        reviewSale.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        reviewSale.addActionListener(event -> showSaleReview(customer.getText()));
        cartFooter.add(reviewSale);
        cartCard.add(cartFooter, BorderLayout.SOUTH);
        columns.add(cartCard);
        pageConstraints.gridy = 1;
        pageConstraints.weighty = 1;
        pageConstraints.fill = GridBagConstraints.BOTH;
        pageConstraints.insets = new Insets(0, 0, 0, 0);
        page.add(columns, pageConstraints);
        renderCart();
        return page;
    }

    private void addSelectedToSale(JTable catalog) {
        if (catalog.getSelectedRow() < 0 || currentData == null) {
            JOptionPane.showMessageDialog(this, "Select a medicine first.", "Current sale",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        int modelRow = catalog.convertRowIndexToModel(catalog.getSelectedRow());
        List<String> row = currentData.rows().get(modelRow);
        String itemID = row.get(2);
        int available = Integer.parseInt(row.get(3));
        if (available < 1) {
            JOptionPane.showMessageDialog(this, "This medicine has no eligible stock.", "Current sale",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        CartLine existing = cartItems.get(itemID);
        if (existing != null && existing.quantity() >= existing.available()) {
            JOptionPane.showMessageDialog(this, "The selected quantity is greater than available stock.",
                    "Current sale", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        int quantity = existing == null ? 1 : existing.quantity() + 1;
        cartItems.put(itemID, new CartLine(itemID, row.get(0), row.get(1), new BigDecimal(row.get(4).replace("₱ ", "")),
                row.get(5), available, quantity));
        renderCart();
    }

    private void renderCart() {
        if (cartLinesPanel == null || cartTotalLabel == null || cartCountLabel == null) {
            return;
        }
        cartLinesPanel.removeAll();
        int quantityTotal = 0;
        BigDecimal total = BigDecimal.ZERO;
        for (CartLine line : cartItems.values()) {
            quantityTotal += line.quantity();
            total = total.add(line.unitPrice().multiply(BigDecimal.valueOf(line.quantity())));
            JPanel row = new JPanel(new BorderLayout(8, 8));
            row.setBackground(SOFT);
            row.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(BORDER), BorderFactory.createEmptyBorder(10, 11, 10, 10)));
            row.setAlignmentX(Component.LEFT_ALIGNMENT);
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 78));
            JPanel detail = new JPanel();
            detail.setOpaque(false);
            detail.setLayout(new BoxLayout(detail, BoxLayout.Y_AXIS));
            JLabel name = new JLabel(line.name());
            name.setFont(new Font("Segoe UI", Font.BOLD, 11));
            name.setForeground(TEXT);
            JLabel price = new JLabel("₱ " + line.unitPrice().setScale(2).toPlainString()
                    + " each · " + line.type());
            price.setFont(new Font("Segoe UI", Font.PLAIN, 10));
            price.setForeground(MUTED);
            detail.add(name);
            detail.add(Box.createVerticalStrut(4));
            detail.add(price);
            row.add(detail, BorderLayout.CENTER);

            JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
            controls.setOpaque(false);
            JButton minus = smallButton("−");
            minus.getAccessibleContext().setAccessibleName("Decrease " + line.name() + " quantity");
            minus.addActionListener(event -> changeCartQuantity(line.itemID(), -1));
            JLabel quantity = new JLabel(Integer.toString(line.quantity()), SwingConstants.CENTER);
            quantity.setFont(new Font("Segoe UI", Font.BOLD, 11));
            quantity.setForeground(TEXT);
            quantity.setPreferredSize(new Dimension(24, 28));
            JButton plus = smallButton("+");
            plus.getAccessibleContext().setAccessibleName("Increase " + line.name() + " quantity");
            plus.setEnabled(line.quantity() < line.available());
            plus.addActionListener(event -> changeCartQuantity(line.itemID(), 1));
            controls.add(minus);
            controls.add(quantity);
            controls.add(plus);
            row.add(controls, BorderLayout.EAST);
            cartLinesPanel.add(row);
            cartLinesPanel.add(Box.createVerticalStrut(8));
        }
        if (cartItems.isEmpty()) {
            JLabel empty = new JLabel("Your sale is empty. Add an item from the catalog.");
            empty.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            empty.setForeground(MUTED);
            empty.setBorder(BorderFactory.createEmptyBorder(18, 2, 0, 2));
            cartLinesPanel.add(empty);
        }
        cartCountLabel.setText(quantityTotal + (quantityTotal == 1 ? " item" : " items"));
        cartTotalLabel.setText("₱ " + total.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString());
        cartLinesPanel.revalidate();
        cartLinesPanel.repaint();
    }

    private void changeCartQuantity(String itemID, int delta) {
        CartLine line = cartItems.get(itemID);
        if (line == null) {
            return;
        }
        int quantity = line.quantity() + delta;
        if (quantity <= 0) {
            cartItems.remove(itemID);
        } else if (quantity <= line.available()) {
            cartItems.put(itemID, new CartLine(line.itemID(), line.name(), line.category(), line.unitPrice(),
                    line.type(), line.available(), quantity));
        }
        renderCart();
    }

    private JButton smallButton(String label) {
        JButton button = new JButton(label);
        button.setFont(new Font("Segoe UI", Font.BOLD, 12));
        button.setForeground(MAROON);
        button.setBackground(WHITE);
        button.setBorder(BorderFactory.createLineBorder(BORDER));
        button.setFocusPainted(true);
        button.setPreferredSize(new Dimension(28, 28));
        return button;
    }

    private void showSaleReview(String customerID) {
        if (cartItems.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Add at least one medicine to review the sale.", "Review sale",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        StringBuilder summary = new StringBuilder("Complete this sale?");
        if (customerID != null && !customerID.isBlank()) {
            summary.append("\nCustomer: ").append(customerID.trim());
        }
        for (CartLine line : cartItems.values()) {
            summary.append("\n").append(line.quantity()).append(" × ").append(line.name());
        }
        summary.append("\n\nTotal: ").append(cartTotalLabel.getText())
                .append("\n\nThis saves the sale and deducts eligible stock.");
        if (JOptionPane.showConfirmDialog(this, summary.toString(), "Confirm sale",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.OK_OPTION) return;
        try {
            List<PharmacyScreenService.SaleLine> lines = cartItems.values().stream()
                    .map(line -> new PharmacyScreenService.SaleLine(line.itemID(), line.quantity())).toList();
            String transactionID = controller.completeSale(customerID, lines, currentUser.userID(), currentUser.role().name());
            cartItems.clear();
            renderCart();
            JOptionPane.showMessageDialog(this, "Sale saved as " + transactionID + ". Stock and audit CSVs were updated.",
                    "Sale completed", JOptionPane.INFORMATION_MESSAGE);
            openScreen(Screen.COUNTER_SALE);
        } catch (RuntimeException ex) {
            JOptionPane.showMessageDialog(this, actionErrorMessage(ex), "Sale not completed", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void openRestockRequestPreview() {
        JDialog dialog = new JDialog(this, "New restock request", true);
        dialog.setLayout(new BorderLayout());
        JPanel content = new JPanel(new BorderLayout(0, 16));
        content.setBackground(WHITE);
        content.setBorder(BorderFactory.createEmptyBorder(22, 24, 18, 24));
        JLabel title = new JLabel("Request more stock");
        title.setFont(new Font("Segoe UI", Font.BOLD, 17));
        title.setForeground(TEXT);
        content.add(title, BorderLayout.NORTH);
        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        JComboBox<ItemOption> medicine = new JComboBox<>();
        for (List<String> item : controller.load(Screen.INVENTORY_ITEMS).rows()) {
            medicine.addItem(new ItemOption(item.get(2), item.get(0)));
        }
        JTextField quantity = new JTextField(22);
        JTextField reason = new JTextField(22);
        addFormRow(form, 0, "Medicine", medicine);
        addFormRow(form, 1, "Quantity requested", quantity);
        addFormRow(form, 2, "Reason", reason);
        content.add(form, BorderLayout.CENTER);
        JLabel note = new JLabel("The request will be saved to the runtime CSV file.");
        note.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        note.setForeground(MUTED);
        JButton close = secondaryButton("Close");
        JButton save = primaryButton("Save request");
        JPanel footer = new JPanel(new BorderLayout(0, 10));
        footer.setOpaque(false);
        footer.add(note, BorderLayout.NORTH);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        buttons.add(close);
        buttons.add(save);
        footer.add(buttons, BorderLayout.SOUTH);
        content.add(footer, BorderLayout.SOUTH);
        close.addActionListener(event -> dialog.dispose());
        save.addActionListener(event -> {
            try {
                ItemOption selected = (ItemOption) medicine.getSelectedItem();
                int requested = Integer.parseInt(quantity.getText().trim());
                if (selected == null || requested <= 0) {
                    throw new IllegalArgumentException("Choose a medicine and quantity greater than zero.");
                }
                String requestID = controller.createRestockRequest(selected.itemID(), requested,
                        reason.getText(), currentUser.userID());
                dialog.dispose();
                JOptionPane.showMessageDialog(this, "Restock request " + requestID + " was saved.",
                        "Request submitted", JOptionPane.INFORMATION_MESSAGE);
                openScreen(Screen.RESTOCK_REQUESTS);
            } catch (RuntimeException ex) {
                JOptionPane.showMessageDialog(dialog, actionErrorMessage(ex), "Check request details", JOptionPane.WARNING_MESSAGE);
            }
        });
        dialog.setContentPane(content);
        dialog.pack();
        dialog.setMinimumSize(new Dimension(480, dialog.getHeight()));
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }
    private JPanel buildMetricRow(List<Metric> metrics) {
        int columns = metrics.size() >= 4 ? 4 : Math.max(1, metrics.size());
        int rows = (int) Math.ceil((double) metrics.size() / columns);
        int rowHeight = rows * 102 + Math.max(0, rows - 1) * 14;
        JPanel row = new JPanel(new GridLayout(rows, columns, 14, 14));
        row.setOpaque(false);
        row.setPreferredSize(new Dimension(0, rowHeight));
        row.setMinimumSize(new Dimension(0, rowHeight));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, rowHeight));
        for (Metric metric : metrics) {
            JPanel card = cardPanel(new BorderLayout(0, 6));
            card.setBorder(BorderFactory.createEmptyBorder(15, 17, 13, 17));
            JLabel label = new JLabel(metric.label());
            label.setFont(new Font("Segoe UI", Font.BOLD, 10));
            label.setForeground(MUTED);
            JPanel center = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
            center.setOpaque(false);
            JLabel value = new JLabel(metric.value());
            value.setFont(new Font("Segoe UI", Font.BOLD, 25));
            value.setForeground(TEXT);
            JLabel note = new JLabel(metric.note());
            note.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            note.setForeground(MUTED);
            center.add(value);
            center.add(note);
            card.add(label, BorderLayout.NORTH);
            card.add(center, BorderLayout.CENTER);
            row.add(card);
        }
        return row;
    }

    private JPanel buildTableCard(Screen screen, ScreenData data) {
        JPanel card = cardPanel(new BorderLayout(0, 12));
        card.setBorder(BorderFactory.createEmptyBorder(18, 18, 14, 18));
        int cardHeight = data.rows().isEmpty() ? 360 : 520;
        card.setPreferredSize(new Dimension(0, cardHeight));
        card.setMinimumSize(new Dimension(0, 310));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        JPanel heading = new JPanel(new BorderLayout());
        heading.setOpaque(false);
        JPanel copy = new JPanel();
        copy.setOpaque(false);
        copy.setLayout(new BoxLayout(copy, BoxLayout.Y_AXIS));
        JLabel title = new JLabel(tableTitle(screen));
        title.setFont(new Font("Segoe UI", Font.BOLD, 15));
        title.setForeground(TEXT);
        JLabel subtitle = new JLabel(tableSubtitle(screen, data));
        subtitle.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        subtitle.setForeground(MUTED);
        subtitle.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
        copy.add(title);
        copy.add(subtitle);
        heading.add(copy, BorderLayout.WEST);
        card.add(heading, BorderLayout.NORTH);

        if (data.rows().isEmpty()) {
            card.add(buildEmptyState(data.emptyTitle(), data.emptyMessage()), BorderLayout.CENTER);
        } else {
            JTable table = buildTable(data);
            currentTable = table;
            JScrollPane tableScroll = new JScrollPane(table);
            tableScroll.setBorder(BorderFactory.createEmptyBorder());
            tableScroll.getViewport().setBackground(WHITE);
            tableScroll.getVerticalScrollBar().setUnitIncrement(16);
            card.add(tableScroll, BorderLayout.CENTER);
        }
        return card;
    }

    private JTable buildTable(ScreenData data) {
        String[] columns = data.columns().toArray(String[]::new);
        DefaultTableModel model = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        for (List<String> row : data.rows()) {
            model.addRow(row.toArray());
        }
        JTable table = new JTable(model);
        table.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        table.setForeground(TEXT);
        table.setBackground(WHITE);
        table.setSelectionBackground(new Color(0xF8, 0xEE, 0xEE));
        table.setSelectionForeground(TEXT);
        table.setGridColor(new Color(0xEE, 0xE8, 0xE5));
        table.setRowHeight(42);
        table.setShowVerticalLines(false);
        table.setIntercellSpacing(new Dimension(0, 1));
        table.setFillsViewportHeight(true);
        table.getTableHeader().setReorderingAllowed(false);
        table.getTableHeader().setBackground(SOFT);
        table.getTableHeader().setForeground(MUTED);
        table.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 10));
        table.getTableHeader().setPreferredSize(new Dimension(100, 38));
        table.setDefaultRenderer(Object.class, new StatusCellRenderer());
        TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(model);
        table.setRowSorter(sorter);
        currentSorter = sorter;
        return table;
    }

    private JPanel buildEmptyState(String title, String message) {
        JPanel empty = new JPanel(new GridBagLayout());
        empty.setOpaque(false);
        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        JLabel icon = new JLabel("○", SwingConstants.CENTER);
        icon.setAlignmentX(Component.CENTER_ALIGNMENT);
        icon.setFont(new Font("Segoe UI", Font.PLAIN, 28));
        icon.setForeground(new Color(0xC8, 0xBF, 0xBC));
        JLabel titleLabel = new JLabel(title, SwingConstants.CENTER);
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        titleLabel.setForeground(TEXT);
        JLabel messageLabel = new JLabel(message, SwingConstants.CENTER);
        messageLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        messageLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        messageLabel.setForeground(MUTED);
        messageLabel.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));
        content.add(icon);
        content.add(Box.createVerticalStrut(8));
        content.add(titleLabel);
        content.add(messageLabel);
        empty.add(content);
        return empty;
    }

    private JPanel buildLoadingPage(Screen screen) {
        JPanel page = new JPanel();
        page.setOpaque(false);
        page.setLayout(new BoxLayout(page, BoxLayout.Y_AXIS));
        page.setBorder(BorderFactory.createEmptyBorder(24, 28, 30, 28));
        JLabel title = new JLabel(screenTitle(screen));
        title.setFont(new Font("Segoe UI", Font.BOLD, 25));
        title.setForeground(TEXT);
        JLabel subtitle = new JLabel("Loading pharmacy records...");
        subtitle.setFont(UI_FONT);
        subtitle.setForeground(MUTED);
        page.add(title);
        page.add(Box.createVerticalStrut(7));
        page.add(subtitle);
        page.add(Box.createVerticalStrut(20));

        JPanel metrics = new JPanel(new GridLayout(2, 2, 14, 12));
        metrics.setOpaque(false);
        metrics.setAlignmentX(Component.LEFT_ALIGNMENT);
        metrics.setPreferredSize(new Dimension(0, 216));
        metrics.setMinimumSize(new Dimension(0, 216));
        metrics.setMaximumSize(new Dimension(Integer.MAX_VALUE, 216));
        for (int i = 0; i < 4; i++) {
            JPanel card = cardPanel(new BorderLayout(0, 10));
            card.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
            card.add(skeletonBlock(118, 9), BorderLayout.NORTH);
            card.add(skeletonBlock(72, 24), BorderLayout.CENTER);
            metrics.add(card);
        }
        page.add(metrics);
        page.add(Box.createVerticalStrut(18));

        JPanel tableCard = cardPanel(new BorderLayout(0, 15));
        tableCard.setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));
        JPanel heading = new JPanel();
        heading.setOpaque(false);
        heading.setLayout(new BoxLayout(heading, BoxLayout.Y_AXIS));
        heading.add(skeletonBlock(185, 15));
        heading.add(Box.createVerticalStrut(8));
        heading.add(skeletonBlock(290, 9));
        tableCard.add(heading, BorderLayout.NORTH);
        JPanel rows = new JPanel(new GridLayout(6, 1, 0, 0));
        rows.setOpaque(false);
        for (int i = 0; i < 6; i++) {
            JPanel row = new JPanel(new BorderLayout(22, 0));
            row.setOpaque(false);
            row.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(0xF0, 0xEB, 0xE9)),
                    BorderFactory.createEmptyBorder(12, 4, 12, 4)));
            row.add(skeletonBlock(190 + (i % 3) * 14, 12), BorderLayout.WEST);
            row.add(skeletonBlock(90 + (i % 2) * 20, 12), BorderLayout.CENTER);
            row.add(skeletonBlock(72, 20), BorderLayout.EAST);
            rows.add(row);
        }
        tableCard.add(rows, BorderLayout.CENTER);
        page.add(tableCard);
        return page;
    }

    private JPanel buildStartupPanel(String error) {
        JPanel background = new JPanel(new GridBagLayout());
        background.setBackground(CANVAS);
        JPanel card = cardPanel(new BorderLayout(0, 18));
        card.setPreferredSize(new Dimension(520, 370));
        card.setBorder(BorderFactory.createEmptyBorder(34, 42, 34, 42));

        JPanel brand = new JPanel(new FlowLayout(FlowLayout.CENTER, 13, 0));
        brand.setOpaque(false);
        JLabel logo = new JLabel("Rx", SwingConstants.CENTER);
        logo.setOpaque(true);
        logo.setBackground(MAROON);
        logo.setForeground(WHITE);
        logo.setFont(new Font("Segoe UI", Font.BOLD, 17));
        logo.setPreferredSize(new Dimension(46, 46));
        JPanel nameStack = new JPanel();
        nameStack.setOpaque(false);
        nameStack.setLayout(new BoxLayout(nameStack, BoxLayout.Y_AXIS));
        JLabel name = new JLabel("AIMMs Pharmacy");
        name.setFont(new Font("Segoe UI", Font.BOLD, 20));
        name.setForeground(TEXT);
        JLabel sub = new JLabel("Preparing your workspace");
        sub.setFont(UI_FONT);
        sub.setForeground(MUTED);
        nameStack.add(name);
        nameStack.add(Box.createVerticalStrut(3));
        nameStack.add(sub);
        brand.add(logo);
        brand.add(nameStack);
        card.add(brand, BorderLayout.NORTH);

        JPanel status = new JPanel();
        status.setOpaque(false);
        status.setLayout(new BoxLayout(status, BoxLayout.Y_AXIS));
        JLabel loading = new JLabel(error == null ? "Loading pharmacy data..." : "Pharmacy data could not be loaded");
        loading.setFont(new Font("Segoe UI", Font.BOLD, 14));
        loading.setForeground(TEXT);
        loading.setAlignmentX(Component.CENTER_ALIGNMENT);
        status.add(loading);
        status.add(Box.createVerticalStrut(14));
        if (error == null) {
            JProgressBar progress = new JProgressBar();
            progress.setIndeterminate(true);
            progress.setForeground(MAROON);
            progress.setBackground(new Color(0xE9, 0xE5, 0xE3));
            progress.setBorderPainted(false);
            progress.setMaximumSize(new Dimension(390, 8));
            status.add(progress);
            status.add(Box.createVerticalStrut(9));
            JLabel message = new JLabel("Reading the records needed for your dashboard");
            message.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            message.setForeground(MUTED);
            message.setAlignmentX(Component.CENTER_ALIGNMENT);
            status.add(message);
        } else {
            JLabel message = new JLabel("Check the CSV file format, then retry.");
            message.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            message.setForeground(MUTED);
            message.setAlignmentX(Component.CENTER_ALIGNMENT);
            status.add(message);
            status.add(Box.createVerticalStrut(12));
            JButton retry = primaryButton("Retry");
            retry.setAlignmentX(Component.CENTER_ALIGNMENT);
            retry.addActionListener(event -> loadInitialData());
            status.add(retry);
        }
        card.add(status, BorderLayout.CENTER);
        JLabel privacy = new JLabel("Your pharmacy records stay in the configured CSV workspace.", SwingConstants.CENTER);
        privacy.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        privacy.setForeground(MUTED);
        card.add(privacy, BorderLayout.SOUTH);
        background.add(card);
        return background;
    }

    private void renderError(Screen screen, String message) {
        JPanel page = new JPanel(new BorderLayout());
        page.setOpaque(false);
        page.setBorder(BorderFactory.createEmptyBorder(24, 28, 30, 28));
        JPanel stack = new JPanel();
        stack.setOpaque(false);
        stack.setLayout(new BoxLayout(stack, BoxLayout.Y_AXIS));
        JLabel title = new JLabel(screenTitle(screen));
        title.setFont(new Font("Segoe UI", Font.BOLD, 25));
        title.setForeground(TEXT);
        stack.add(title);
        stack.add(Box.createVerticalStrut(18));
        JPanel errorCard = cardPanel(new BorderLayout(12, 12));
        errorCard.setBorder(BorderFactory.createEmptyBorder(22, 24, 22, 24));
        JLabel errorTitle = new JLabel("We couldn't load these records");
        errorTitle.setFont(new Font("Segoe UI", Font.BOLD, 15));
        errorTitle.setForeground(TEXT);
        JLabel detail = new JLabel("<html>" + escapeHtml(message)
                + "<br>Check the CSV header and rows, then try again.</html>");
        detail.setFont(UI_FONT);
        detail.setForeground(MUTED);
        JButton retry = primaryButton("Retry");
        retry.addActionListener(event -> openScreen(screen));
        errorCard.add(errorTitle, BorderLayout.NORTH);
        errorCard.add(detail, BorderLayout.CENTER);
        errorCard.add(retry, BorderLayout.EAST);
        stack.add(errorCard);
        page.add(stack, BorderLayout.NORTH);
        showPageComponent(page);
    }

    private void showPageComponent(JPanel component) {
        pageHost.removeAll();
        pageHost.add(component, "page");
        ((CardLayout) pageHost.getLayout()).show(pageHost, "page");
        pageHost.revalidate();
        pageHost.repaint();
    }

    private void applySearch() {
        if (currentSorter == null) {
            return;
        }
        String value = searchField.getText().trim();
        if (value.isEmpty()) {
            currentSorter.setRowFilter(null);
        } else {
            currentSorter.setRowFilter(RowFilter.regexFilter("(?i)" + Pattern.quote(value)));
        }
    }

    private void openAddMedicineDialog() {
        JDialog dialog = new JDialog(this, "Add medicine", true);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        JPanel content = new JPanel(new BorderLayout(0, 16));
        content.setBackground(WHITE);
        content.setBorder(BorderFactory.createEmptyBorder(24, 26, 20, 26));
        JLabel title = new JLabel("Add medicine");
        title.setFont(new Font("Segoe UI", Font.BOLD, 19));
        title.setForeground(TEXT);
        JLabel subtitle = new JLabel("Add the product details to the catalog.");
        subtitle.setFont(UI_FONT);
        subtitle.setForeground(MUTED);
        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.add(title);
        header.add(Box.createVerticalStrut(5));
        header.add(subtitle);
        content.add(header, BorderLayout.NORTH);

        JTextField nameField = new JTextField(24);
        JTextField categoryField = new JTextField(24);
        JTextField priceField = new JTextField(24);
        JCheckBox prescription = new JCheckBox("Prescription required");
        prescription.setBackground(WHITE);
        prescription.setForeground(TEXT);
        prescription.setFont(UI_FONT);
        JComboBox<ItemStatus> statusField = new JComboBox<>(ItemStatus.values());
        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        addFormRow(form, 0, "Medicine name", nameField);
        addFormRow(form, 1, "Category", categoryField);
        addFormRow(form, 2, "Unit price (₱)", priceField);
        addFormRow(form, 3, "Status", statusField);
        GridBagConstraints check = new GridBagConstraints();
        check.gridx = 1;
        check.gridy = 4;
        check.anchor = GridBagConstraints.WEST;
        check.insets = new Insets(8, 0, 0, 0);
        form.add(prescription, check);
        content.add(form, BorderLayout.CENTER);

        JLabel error = new JLabel(" ");
        error.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        error.setForeground(new Color(0xA7, 0x38, 0x35));
        JButton cancel = secondaryButton("Cancel");
        JButton save = primaryButton("Save medicine");
        cancel.addActionListener(event -> dialog.dispose());
        save.addActionListener(event -> {
            try {
                BigDecimal price = new BigDecimal(priceField.getText().trim());
                controller.createItem(nameField.getText(), categoryField.getText(), price,
                        prescription.isSelected(), (ItemStatus) statusField.getSelectedItem());
                dialog.dispose();
                searchField.setText("");
                openScreen(Screen.INVENTORY_ITEMS);
            } catch (NumberFormatException ex) {
                error.setText("Enter a valid price, such as 12.50.");
            } catch (RuntimeException ex) {
                error.setText(ex.getMessage() == null ? "Could not save this medicine." : ex.getMessage());
            }
        });
        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        footer.add(error, BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        actions.add(cancel);
        actions.add(save);
        footer.add(actions, BorderLayout.SOUTH);
        content.add(footer, BorderLayout.SOUTH);
        dialog.setContentPane(content);
        dialog.pack();
        dialog.setMinimumSize(new Dimension(480, dialog.getHeight()));
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private void addFormRow(JPanel form, int row, String labelText, Component input) {
        GridBagConstraints label = new GridBagConstraints();
        label.gridx = 0;
        label.gridy = row;
        label.anchor = GridBagConstraints.WEST;
        label.insets = new Insets(6, 0, 6, 16);
        JLabel caption = new JLabel(labelText);
        caption.setFont(new Font("Segoe UI", Font.BOLD, 11));
        caption.setForeground(TEXT);
        form.add(caption, label);

        GridBagConstraints field = new GridBagConstraints();
        field.gridx = 1;
        field.gridy = row;
        field.weightx = 1;
        field.fill = GridBagConstraints.HORIZONTAL;
        field.insets = new Insets(6, 0, 6, 0);
        input.setFont(UI_FONT);
        form.add(input, field);
    }

    private void openSelectedPharmacist() {
        if (currentTable == null || currentTable.getSelectedRow() < 0 || currentData == null) {
            javax.swing.JOptionPane.showMessageDialog(this, "Select a pharmacist account to review.",
                    "Pharmacist accounts", javax.swing.JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        int modelRow = currentTable.convertRowIndexToModel(currentTable.getSelectedRow());
        List<String> row = currentData.rows().get(modelRow);
        String details = "Pharmacist: " + row.get(1)
                + "\nUsername: " + row.get(2)
                + "\nStatus: " + row.get(3)
                + "\nRegistered: " + row.get(4)
                + "\nApproved date: " + row.get(5);
        javax.swing.JOptionPane.showMessageDialog(this, details,
                "Review pharmacist account", javax.swing.JOptionPane.INFORMATION_MESSAGE);
    }

    private void approveSelectedRestockRequest() {
        if (currentUser == null || currentUser.role() != UserRole.ADMIN) {
            JOptionPane.showMessageDialog(this, "Sign in as an administrator to approve restock requests.",
                    "Administrator action", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (currentTable == null || currentTable.getSelectedRow() < 0 || currentData == null) {
            JOptionPane.showMessageDialog(this, "Select a restock request first.",
                    "Restock requests", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        int modelRow = currentTable.convertRowIndexToModel(currentTable.getSelectedRow());
        List<String> row = currentData.rows().get(modelRow);
        String requestID = row.get(0);
        int remaining;
        try {
            remaining = Integer.parseInt(row.get(2)) - Integer.parseInt(row.get(3));
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "The request quantity is invalid.",
                    "Restock request", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (remaining <= 0 || !("Requested".equals(row.get(6)) || "Approved".equals(row.get(6))
                || "Partially Received".equals(row.get(6)))) {
            JOptionPane.showMessageDialog(this, "Only pending or partially received requests can be processed.",
                    "Restock request", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        JDialog dialog = new JDialog(this, "Approve and receive restock", true);
        JPanel content = new JPanel(new BorderLayout(0, 14));
        content.setBackground(WHITE);
        content.setBorder(BorderFactory.createEmptyBorder(20, 22, 18, 22));
        JLabel intro = new JLabel("<html><b>" + escapeHtml(row.get(1)) + "</b><br>Request "
                + escapeHtml(requestID) + " · " + remaining + " unit(s) remaining</html>");
        intro.setFont(UI_FONT);
        intro.setForeground(TEXT);
        content.add(intro, BorderLayout.NORTH);
        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        JTextField quantity = new JTextField(Integer.toString(remaining), 18);
        JTextField lotNumber = new JTextField(18);
        JTextField expiration = new JTextField(18);
        expiration.setToolTipText("Use ISO format: yyyy-MM-dd");
        addFormRow(form, 0, "Quantity received now", quantity);
        addFormRow(form, 1, "Supplier lot number", lotNumber);
        addFormRow(form, 2, "Expiration date (yyyy-MM-dd)", expiration);
        content.add(form, BorderLayout.CENTER);
        JLabel note = new JLabel("Enter delivered stock only. Saving creates a batch and updates available inventory.");
        note.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        note.setForeground(MUTED);
        JButton cancel = secondaryButton("Cancel");
        JButton save = primaryButton("Approve and add stock");
        JPanel footer = new JPanel(new BorderLayout(0, 10));
        footer.setOpaque(false);
        footer.add(note, BorderLayout.NORTH);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        actions.add(cancel);
        actions.add(save);
        footer.add(actions, BorderLayout.SOUTH);
        content.add(footer, BorderLayout.SOUTH);
        cancel.addActionListener(event -> dialog.dispose());
        save.addActionListener(event -> {
            try {
                int receivedNow = Integer.parseInt(quantity.getText().trim());
                java.time.LocalDate expirationDate = java.time.LocalDate.parse(expiration.getText().trim());
                if (receivedNow <= 0 || receivedNow > remaining) {
                    throw new IllegalArgumentException("Quantity must be between 1 and " + remaining + ".");
                }
                String batchID = controller.approveRestockRequest(requestID, currentUser.userID(),
                        receivedNow, lotNumber.getText(), expirationDate);
                dialog.dispose();
                JOptionPane.showMessageDialog(this,
                        receivedNow + " unit(s) were added as batch " + batchID + ". The pharmacist inventory now reflects the receipt.",
                        "Restock approved and received", JOptionPane.INFORMATION_MESSAGE);
                openScreen(Screen.RESTOCK_REQUESTS);
            } catch (RuntimeException ex) {
                JOptionPane.showMessageDialog(dialog, actionErrorMessage(ex),
                        "Restock not saved", JOptionPane.WARNING_MESSAGE);
            }
        });
        dialog.setContentPane(content);
        dialog.pack();
        dialog.setMinimumSize(new Dimension(520, dialog.getHeight()));
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private JPanel cardPanel(BorderLayout layout) {
        JPanel panel = new JPanel(layout);
        panel.setBackground(WHITE);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                BorderFactory.createEmptyBorder(0, 0, 0, 0)));
        return panel;
    }

    private JButton primaryButton(String label) {
        JButton button = new JButton(label);
        button.setFont(new Font("Segoe UI", Font.BOLD, 12));
        button.setBackground(MAROON);
        button.setForeground(WHITE);
        button.setOpaque(true);
        button.setContentAreaFilled(true);
        button.setBorderPainted(false);
        button.setFocusPainted(true);
        button.setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));
        button.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        return button;
    }

    private JButton secondaryButton(String label) {
        JButton button = new JButton(label);
        button.setFont(new Font("Segoe UI", Font.BOLD, 12));
        button.setBackground(WHITE);
        button.setForeground(MAROON);
        button.setOpaque(true);
        button.setContentAreaFilled(true);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0xDE, 0xD4, 0xD0)),
                BorderFactory.createEmptyBorder(8, 13, 8, 13)));
        button.setFocusPainted(true);
        button.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        return button;
    }

    private JPanel skeletonBlock(int width, int height) {
        JPanel block = new JPanel();
        block.setBackground(SKELETON);
        block.setPreferredSize(new Dimension(width, height));
        block.setMaximumSize(new Dimension(width, height));
        return block;
    }

    private String screenTitle(Screen screen) {
        return switch (screen) {
            case DASHBOARD -> "Admin overview";
            case PHARMACIST_HOME -> "Pharmacist dashboard";
            case COUNTER_SALE -> "Counter sale";
            case INVENTORY_ITEMS -> "Inventory items";
            case STOCK_BATCHES -> "Stock batches";
            case RESTOCK_REQUESTS -> "Restock requests";
            case PRESCRIPTIONS -> "Prescriptions";
            case TRANSACTIONS -> "Transactions";
            case CUSTOMERS -> "Customers";
            case PHARMACISTS -> "Pharmacist accounts";
            case ADMINS -> "Admin accounts";
            case AUDIT_LOGS -> "Audit & inventory logs";
        };
    }

    private String screenSubtitle(Screen screen) {
        return switch (screen) {
            case DASHBOARD -> "Review pharmacist access and monitor recent pharmacy activity.";
            case PHARMACIST_HOME -> "Manage the counter, review prescriptions, and keep stock moving.";
            case COUNTER_SALE -> "Build a sale from eligible stock and review it before checkout.";
            case INVENTORY_ITEMS -> "Manage the medicine catalog and keep product details current.";
            case STOCK_BATCHES -> "Track lot numbers, expiration dates, and sellable quantity using FEFO.";
            case RESTOCK_REQUESTS -> "Follow requested quantities and receipt status.";
            case PRESCRIPTIONS -> "Review prescription records received from external prescribers.";
            case TRANSACTIONS -> "Review completed and cancelled sales without changing their history.";
            case CUSTOMERS -> "View customer account records without exposing password data.";
            case PHARMACISTS -> "Review account status and administrator approval dates.";
            case ADMINS -> "View administrator accounts set up for this pharmacy.";
            case AUDIT_LOGS -> "Trace inventory, prescription, transaction, and balance activity.";
        };
    }

    private String tableTitle(Screen screen) {
        return switch (screen) {
            case DASHBOARD -> "Recent activity";
            case PHARMACIST_HOME -> "Recent activity";
            case COUNTER_SALE -> "Medicine catalog";
            case INVENTORY_ITEMS -> "Medicine catalog";
            case STOCK_BATCHES -> "Batch inventory";
            case RESTOCK_REQUESTS -> "Request list";
            case PRESCRIPTIONS -> "Prescription records";
            case TRANSACTIONS -> "Transaction history";
            case CUSTOMERS -> "Customer accounts";
            case PHARMACISTS -> "Pharmacist directory";
            case ADMINS -> "Administrator directory";
            case AUDIT_LOGS -> "Activity history";
        };
    }

    private String tableSubtitle(Screen screen, ScreenData data) {
        return switch (screen) {
            case DASHBOARD -> "Actions are displayed from the recorded CSV history.";
            case PHARMACIST_HOME -> "Your workspace activity is read from the pharmacy CSV history.";
            case COUNTER_SALE -> "Build a sale from eligible stock, then save the transaction and stock updates.";
            case INVENTORY_ITEMS -> data.rows().size() + " catalog records · Search with the field above.";
            case STOCK_BATCHES -> "Sorted by earliest expiration date. Blocked batches remain visible.";
            case RESTOCK_REQUESTS -> "Receipt updates are read from the configured pharmacy records.";
            case PRESCRIPTIONS -> "Prescription source documents are not displayed or stored in the repository.";
            case TRANSACTIONS -> "Completed transactions remain preserved in the history.";
            case CUSTOMERS -> "Account balances are current values; balance history appears in audit logs.";
            case PHARMACISTS -> "Pending accounts remain visible until their status is updated.";
            case ADMINS -> "Admin accounts are created through the manual setup process.";
            case AUDIT_LOGS -> "Read-only history with actor, time, action, reason, outcome, and related record.";
        };
    }

    private String readableError(Throwable error) {
        if (error == null || error.getMessage() == null || error.getMessage().isBlank()) {
            return "The CSV data could not be read.";
        }
        return error.getMessage();
    }

    private String actionErrorMessage(Throwable error) {
        Throwable cause = error;
        while (cause != null && (cause.getMessage() == null || cause.getMessage().isBlank())) {
            if (cause.getCause() == null) break;
            cause = cause.getCause();
        }
        if (cause == null) return "The action could not be saved. Please try again.";
        String message = cause.getMessage();
        return message == null || message.isBlank()
                ? "The action could not be saved (" + cause.getClass().getSimpleName() + "). Please try again."
                : message;
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "Unknown CSV error";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private String roleLabel(UserRole role) {
        return role == UserRole.ADMIN ? "Administrator view" : "Pharmacist view";
    }

    private String initials(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return "ST";
        }
        String[] parts = fullName.trim().split("\\s+");
        String first = parts[0].substring(0, 1);
        String last = parts.length > 1 ? parts[parts.length - 1].substring(0, 1) : "";
        return (first + last).toUpperCase();
    }

    private final class NavButton extends JButton {
        private boolean active;

        NavButton(String label, String glyph) {
            super(label, new NavGlyph(glyph));
            setFont(new Font("Segoe UI", Font.BOLD, 12));
            setHorizontalAlignment(SwingConstants.LEFT);
            setIconTextGap(12);
            setBorder(BorderFactory.createEmptyBorder(7, 10, 7, 10));
            setFocusPainted(true);
            setContentAreaFilled(false);
            setOpaque(false);
            setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        }

        void setActive(boolean value) {
            active = value;
            setForeground(active ? WHITE : TEXT);
            repaint();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g2 = (Graphics2D) graphics.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (active) {
                g2.setColor(MAROON);
            } else if (getModel().isRollover()) {
                g2.setColor(new Color(0xF8, 0xF3, 0xF1));
            } else {
                g2.setColor(WHITE);
            }
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 9, 9);
            g2.dispose();
            setForeground(active ? WHITE : TEXT);
            super.paintComponent(graphics);
        }
    }

    private static final class ViewportPanel extends JPanel implements Scrollable {
        ViewportPanel() {
            super(new CardLayout());
            setOpaque(false);
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return new Dimension(900, 700);
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 18;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return Math.max(36, visibleRect.height - 36);
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }

    private static final class NavGlyph implements Icon {
        private final String glyph;

        NavGlyph(String glyph) {
            this.glyph = glyph;
        }

        @Override
        public void paintIcon(Component component, Graphics graphics, int x, int y) {
            Color foreground = component instanceof AbstractButton button ? button.getForeground() : TEXT;
            graphics.setColor(foreground);
            graphics.setFont(new Font("Segoe UI", Font.BOLD, glyph.length() > 1 ? 10 : 12));
            graphics.drawString(glyph, x + (glyph.length() > 1 ? 0 : 2), y + 13);
        }

        @Override public int getIconWidth() { return 18; }
        @Override public int getIconHeight() { return 18; }
    }

    private static final class StatusCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                boolean focused, int row, int column) {
            super.getTableCellRendererComponent(table, value, selected, focused, row, column);
            String text = value == null ? "" : value.toString();
            String heading = table.getColumnName(column).toUpperCase();
            boolean status = heading.contains("STATUS") || heading.contains("OUTCOME") || heading.contains("SELLABILITY");
            setBorder(BorderFactory.createEmptyBorder(0, 9, 0, 9));
            setHorizontalAlignment(SwingConstants.LEFT);
            if (selected) {
                setBackground(table.getSelectionBackground());
                setForeground(table.getSelectionForeground());
            } else if (status) {
                setOpaque(true);
                String normalized = text.toLowerCase();
                if (normalized.equals("active") || normalized.equals("approved") || normalized.equals("success")
                        || normalized.equals("sellable") || normalized.equals("completed")
                        || normalized.equals("received") || normalized.equals("verified")
                        || normalized.equals("dispensed")) {
                    setBackground(new Color(0xED, 0xF5, 0xF0));
                    setForeground(new Color(0x34, 0x75, 0x53));
                } else if (normalized.contains("pending") || normalized.contains("requested")
                        || normalized.contains("partial")) {
                    setBackground(new Color(0xFF, 0xF3, 0xDF));
                    setForeground(new Color(0x94, 0x65, 0x1D));
                } else if (normalized.contains("not sellable") || normalized.contains("expired")
                        || normalized.contains("quarantined") || normalized.contains("rejected")
                        || normalized.contains("inactive") || normalized.contains("depleted")
                        || normalized.contains("pulled") || normalized.contains("cancelled")) {
                    setBackground(new Color(0xF3, 0xEE, 0xEE));
                    setForeground(new Color(0x78, 0x53, 0x53));
                } else {
                    setBackground(WHITE);
                    setForeground(TEXT);
                }
            } else {
                setOpaque(true);
                setBackground(WHITE);
                setForeground(TEXT);
            }
            setFont(new Font("Segoe UI", Font.PLAIN, 12));
            return this;
        }
    }

    private record CartLine(
            String itemID,
            String name,
            String category,
            BigDecimal unitPrice,
            String type,
            int available,
            int quantity) { }

    private record ItemOption(String itemID, String name) {
        @Override public String toString() { return name + " · " + itemID; }
    }
}
