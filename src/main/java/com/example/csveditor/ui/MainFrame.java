package com.example.csveditor.ui;

import com.example.csveditor.domain.CsvDocument;
import com.example.csveditor.domain.DataNode;
import com.example.csveditor.service.CsvDocumentService;
import com.example.csveditor.service.CsvDataScanService;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.AbstractAction;
import javax.swing.Box;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.JToolBar;
import javax.swing.SwingWorker;
import javax.imageio.ImageIO;
import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.prefs.Preferences;

/**
 * Main application window and high-level UI coordination.
 */
public class MainFrame extends JFrame {

    private final CsvDataTreePanel treePanel;
    private final DataGroupListPanel dataGroupListPanel;
    private final CsvStackPanel csvStackPanel;
    private final StatusBar statusBar;
    private final CsvDataScanService scanService;
    private final CsvDocumentService documentService;
    private final Preferences preferences;
    private JSplitPane leftSplitPane;
    private JTextField columnWidthField;
    private Path rootDirectory;
    private boolean restoringSession;
    private boolean suppressSessionSaving;
    private static final String LAST_ROOT_FOLDER_KEY = "lastRootFolder";
    private static final String SESSION_ROOT_FOLDER_KEY = "sessionRootFolder";
    private static final String SESSION_OPEN_FILES_KEY = "sessionOpenFiles";
    private static final String SESSION_COLLAPSED_GROUPS_KEY = "sessionCollapsedGroups";
    private static final String SESSION_DATA_GROUPING_ENABLED_KEY = "sessionDataGroupingEnabled";
    private static final String DATA_GROUPING_ENABLED_KEY = "dataGroupingEnabled";
    private static final String ROW_CLIPBOARD_DELIMITER_TYPE_KEY = "rowClipboardDelimiterType";
    private static final String GROUPED_MODE_SUFFIX = ".grouped";
    private static final String FLAT_MODE_SUFFIX = ".flat";

    public MainFrame() {
        super("CSV Data Editor");
        this.scanService = new CsvDataScanService();
        this.documentService = new CsvDocumentService();
        this.treePanel = new CsvDataTreePanel();
        this.dataGroupListPanel = new DataGroupListPanel();
        this.csvStackPanel = new CsvStackPanel(documentService);
        this.statusBar = new StatusBar();
        this.preferences = Preferences.userNodeForPackage(MainFrame.class);
        this.csvStackPanel.setDataGroupingEnabled(preferences.getBoolean(DATA_GROUPING_ENABLED_KEY, true));
        this.csvStackPanel.setRowClipboardDelimiter(getRowClipboardDelimiter());
        buildUi();
        installListeners();
        installGlobalKeyBindings();
        loadLastRootFolder();
    }

    private void buildUi() {
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        List<Image> icons = loadApplicationIcons();
        if (!icons.isEmpty()) {
            setIconImages(icons);
        }
        setLayout(new BorderLayout());
        setJMenuBar(createMenuBar());
        add(createToolBar(), BorderLayout.NORTH);

        leftSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, treePanel, dataGroupListPanel);
        leftSplitPane.setResizeWeight(0.0);
        leftSplitPane.setDividerLocation(300);
        updateDataGroupListVisibility();

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftSplitPane, csvStackPanel);
        splitPane.setResizeWeight(0.0);
        splitPane.setDividerLocation(474);
        add(splitPane, BorderLayout.CENTER);
        add(statusBar, BorderLayout.SOUTH);

        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        int startupHeight = (int) Math.round(screenSize.height * 0.875d);
        int startupWidth = (int) Math.round(startupHeight * 16d / 9d);
        setSize(Math.min(startupWidth, screenSize.width), startupHeight);
        setLocationRelativeTo(null);
    }

    private JMenuBar createMenuBar() {
        JMenuBar menuBar = new JMenuBar();
        JMenu fileMenu = new JMenu("File");

        JMenuItem openRootItem = new JMenuItem("Open Root Folder...");
        openRootItem.addActionListener(e -> chooseRootFolder());
        fileMenu.add(openRootItem);

        JMenuItem saveAllItem = new JMenuItem("Save All");
        saveAllItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(
                KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
        saveAllItem.addActionListener(e -> saveAll());
        fileMenu.add(saveAllItem);

        JMenuItem settingsItem = new JMenuItem("Settings...");
        settingsItem.addActionListener(e -> showSettingsDialog());
        fileMenu.add(settingsItem);

        fileMenu.addSeparator();
        JMenuItem exitItem = new JMenuItem("Exit");
        exitItem.addActionListener(e -> closeWindow());
        fileMenu.add(exitItem);

        menuBar.add(fileMenu);
        return menuBar;
    }

    private JToolBar createToolBar() {
        JToolBar toolBar = new JToolBar();
        toolBar.setFloatable(false);

        JButton openRootButton = new JButton("Open Root");
        openRootButton.addActionListener(e -> chooseRootFolder());
        toolBar.add(openRootButton);

        JButton saveAllButton = new JButton("Save All");
        saveAllButton.addActionListener(e -> saveAll());
        toolBar.add(saveAllButton);

        toolBar.add(Box.createHorizontalGlue());

        columnWidthField = new JTextField("110");
        Dimension columnWidthFieldSize = new Dimension(48, 24);
        columnWidthField.setPreferredSize(columnWidthFieldSize);
        columnWidthField.setMinimumSize(columnWidthFieldSize);
        columnWidthField.setMaximumSize(columnWidthFieldSize);
        columnWidthField.setToolTipText("表示中のCSVテーブルへ適用する列幅をpxで指定します。");
        columnWidthField.addActionListener(e -> applyColumnWidthToOpenTables());

        JButton applyColumnWidthButton = new JButton("列幅を全設定");
        applyColumnWidthButton.setToolTipText("表示中のCSVテーブルすべての列幅を指定値に変更します。");
        applyColumnWidthButton.addActionListener(e -> applyColumnWidthToOpenTables());

        JButton autoFitColumnWidthButton = new JButton("列幅を全自動調整");
        autoFitColumnWidthButton.setToolTipText("表示中のCSVテーブルすべての列幅を列名とセル値に合わせて自動調整します。");
        autoFitColumnWidthButton.addActionListener(e -> autoFitColumnWidthsForOpenTables());
        toolBar.add(createToolBarGroup(autoFitColumnWidthButton, applyColumnWidthButton, columnWidthField));
        toolBar.addSeparator(new Dimension(10, 0));

        JButton closeAllCsvButton = new JButton("すべて閉じる");
        closeAllCsvButton.setToolTipText("表示中のCSVパネルをすべて閉じます。");
        closeAllCsvButton.addActionListener(e -> closeAllOpenCsvPanels());
        toolBar.add(createToolBarGroup(closeAllCsvButton));
        toolBar.addSeparator(new Dimension(10, 0));

        JButton settingsButton = new JButton("設定");
        settingsButton.addActionListener(e -> showSettingsDialog());
        toolBar.add(createToolBarGroup(settingsButton));

        return toolBar;
    }

    private static JPanel createToolBarGroup(JComponent... components) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        for (JComponent component : components) {
            panel.add(component);
        }
        panel.setMaximumSize(panel.getPreferredSize());
        return panel;
    }

    private void installListeners() {
        treePanel.setCsvOpenListener(new CsvDataTreePanel.CsvOpenListener() {
            @Override
            public void csvOpenRequested(DataNode node) {
                openCsv(node);
            }

            @Override
            public void csvOpenRequested(List<DataNode> nodes) {
                openCsvFiles(nodes);
            }
        });
        csvStackPanel.setSessionChangeListener(new CsvStackPanel.SessionChangeListener() {
            @Override
            public void sessionChanged() {
                updateOpenGroupList();
                if (!restoringSession && !suppressSessionSaving) {
                    saveOpenSession();
                }
            }
        });
        csvStackPanel.setActiveGroupChangeListener(new CsvStackPanel.ActiveGroupChangeListener() {
            @Override
            public void activeGroupChanged(String groupKey) {
                dataGroupListPanel.setSelectedGroupKey(groupKey);
            }
        });
        dataGroupListPanel.setGroupSelectionListener(new DataGroupListPanel.GroupSelectionListener() {
            @Override
            public void groupSelected(String groupKey) {
                csvStackPanel.focusGroup(groupKey);
            }
        });
        dataGroupListPanel.setGroupCloseListener(new DataGroupListPanel.GroupCloseListener() {
            @Override
            public void groupCloseRequested(String groupKey) {
                csvStackPanel.requestCloseGroup(groupKey);
            }
        });

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                closeWindow();
            }
        });
    }

    private void installGlobalKeyBindings() {
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(javax.swing.KeyStroke.getKeyStroke(
                        KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK),
                        "saveAllCsvFiles");
        getRootPane().getActionMap().put("saveAllCsvFiles", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent event) {
                saveAll();
            }
        });
    }

    private void chooseRootFolder() {
        JFileChooser chooser = new JFileChooser(rootDirectory == null ? null : rootDirectory.toFile());
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("Select root folder");
        int result = chooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFolder = chooser.getSelectedFile();
            Path selectedRoot = selectedFolder.toPath().toAbsolutePath().normalize();
            if (rootDirectory != null
                    && !rootDirectory.equals(selectedRoot)
                    && !csvStackPanel.getOpenPanels().isEmpty()
                    && !csvStackPanel.requestCloseAll()) {
                return;
            }
            loadRootFolder(selectedFolder);
        }
    }

    private void loadRootFolder(File rootFolder) {
        final Path selectedRoot = rootFolder.toPath().toAbsolutePath().normalize();
        statusBar.showLoading("Loading folder: " + selectedRoot);
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

        SwingWorker<DataNode, Void> worker = new SwingWorker<DataNode, Void>() {
            @Override
            protected DataNode doInBackground() throws Exception {
                return scanService.scan(selectedRoot);
            }

            @Override
            protected void done() {
                setCursor(Cursor.getDefaultCursor());
                try {
                    DataNode rootNode = get();
                    rootDirectory = selectedRoot;
                    saveLastRootFolder(selectedRoot);
                    treePanel.setRootNode(rootNode);
                    statusBar.setMessage("Loaded: " + selectedRoot);
                    restoreOpenSession(selectedRoot);
                } catch (Exception ex) {
                    treePanel.clear("Failed to load folder");
                    statusBar.showError(ex.getMessage());
                    showError("Failed to load folder.", ex);
                }
            }
        };
        worker.execute();
    }

    private void loadLastRootFolder() {
        String lastRootFolder = preferences.get(LAST_ROOT_FOLDER_KEY, null);
        if (lastRootFolder == null || lastRootFolder.trim().length() == 0) {
            return;
        }
        Path lastRoot = Paths.get(lastRootFolder).toAbsolutePath().normalize();
        if (Files.isDirectory(lastRoot)) {
            loadRootFolder(lastRoot.toFile());
        } else {
            statusBar.setMessage("Last root folder was not found: " + lastRoot);
        }
    }

    private void saveLastRootFolder(Path selectedRoot) {
        preferences.put(LAST_ROOT_FOLDER_KEY, selectedRoot.toAbsolutePath().normalize().toString());
    }

    private void saveOpenSession() {
        if (rootDirectory == null) {
            return;
        }
        preferences.put(SESSION_ROOT_FOLDER_KEY, rootDirectory.toAbsolutePath().normalize().toString());
        boolean dataGroupingEnabled = csvStackPanel.isDataGroupingEnabled();
        preferences.put(modeSessionKey(SESSION_OPEN_FILES_KEY, dataGroupingEnabled),
                joinLines(csvStackPanel.getOpenRelativePaths()));
        preferences.put(modeSessionKey(SESSION_COLLAPSED_GROUPS_KEY, dataGroupingEnabled),
                joinLines(new ArrayList<String>(csvStackPanel.getCollapsedGroupKeys())));
        preferences.putBoolean(SESSION_DATA_GROUPING_ENABLED_KEY, csvStackPanel.isDataGroupingEnabled());
    }

    private void restoreOpenSession(final Path selectedRoot) {
        String sessionRoot = preferences.get(SESSION_ROOT_FOLDER_KEY, null);
        if (sessionRoot == null || !selectedRoot.toString().equals(Paths.get(sessionRoot).toAbsolutePath().normalize().toString())) {
            return;
        }
        final boolean sessionDataGroupingEnabled = preferences.getBoolean(
                SESSION_DATA_GROUPING_ENABLED_KEY,
                preferences.getBoolean(DATA_GROUPING_ENABLED_KEY, true));
        final List<String> relativePaths = splitLines(getModeSessionValue(
                SESSION_OPEN_FILES_KEY, sessionDataGroupingEnabled));
        final Set<String> collapsedGroups = new HashSet<String>(splitLines(getModeSessionValue(
                SESSION_COLLAPSED_GROUPS_KEY, sessionDataGroupingEnabled)));
        if (relativePaths.isEmpty() && collapsedGroups.isEmpty()) {
            csvStackPanel.setDataGroupingEnabled(sessionDataGroupingEnabled);
            updateOpenGroupList();
            return;
        }
        statusBar.showLoading("Restoring previous open CSV files...");
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        SwingWorker<List<CsvDocument>, Void> worker = new SwingWorker<List<CsvDocument>, Void>() {
            @Override
            protected List<CsvDocument> doInBackground() {
                List<CsvDocument> documents = new ArrayList<CsvDocument>();
                for (String relativePath : relativePaths) {
                    Path csvPath = selectedRoot.resolve(relativePath).toAbsolutePath().normalize();
                    if (!csvPath.startsWith(selectedRoot)
                            || !Files.isRegularFile(csvPath)
                            || !csvPath.getFileName().toString().toLowerCase().endsWith(".csv")) {
                        continue;
                    }
                    try {
                        documents.add(documentService.open(selectedRoot, csvPath));
                    } catch (Exception ignored) {
                        // Deleted or unreadable CSV files are skipped during session restore.
                    }
                }
                return documents;
            }

            @Override
            protected void done() {
                setCursor(Cursor.getDefaultCursor());
                restoringSession = true;
                try {
                    csvStackPanel.setDataGroupingEnabled(sessionDataGroupingEnabled);
                    csvStackPanel.setCollapsedGroupKeys(collapsedGroups);
                    for (CsvDocument document : get()) {
                        csvStackPanel.addOrFocusDocument(document);
                    }
                    csvStackPanel.setCollapsedGroupKeys(collapsedGroups);
                    updateOpenGroupList();
                    statusBar.setMessage("Restored previous open CSV files.");
                } catch (Exception ex) {
                    statusBar.showError(ex.getMessage());
                } finally {
                    restoringSession = false;
                }
            }
        };
        worker.execute();
    }

    private static String joinLines(List<String> values) {
        StringBuilder builder = new StringBuilder();
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value == null || value.length() == 0) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(value);
        }
        return builder.toString();
    }

    private static List<String> splitLines(String value) {
        List<String> lines = new ArrayList<String>();
        if (value == null || value.trim().length() == 0) {
            return lines;
        }
        String[] parts = value.split("\\r?\\n");
        for (String part : parts) {
            if (part != null && part.trim().length() > 0) {
                lines.add(part.trim());
            }
        }
        return lines;
    }

    private void openCsv(final DataNode node) {
        final Path csvPath = node.getPath().toAbsolutePath().normalize();
        if (csvStackPanel.hasOpenDocument(csvPath)) {
            csvStackPanel.focusDocument(csvPath);
            statusBar.setMessage("Already open: " + node.getRelativePath());
            return;
        }

        statusBar.showLoading("Opening CSV: " + node.getRelativePath());
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        SwingWorker<CsvDocument, Void> worker = new SwingWorker<CsvDocument, Void>() {
            @Override
            protected CsvDocument doInBackground() throws Exception {
                return documentService.open(rootDirectory, csvPath);
            }

            @Override
            protected void done() {
                setCursor(Cursor.getDefaultCursor());
                try {
                    CsvDocument document = get();
                    csvStackPanel.addOrFocusDocument(document);
                    statusBar.setMessage("Opened: " + document.getRelativePath());
                } catch (Exception ex) {
                    statusBar.showError(ex.getMessage());
                    showError("Failed to open CSV.", ex);
                }
            }
        };
        worker.execute();
    }

    private void openCsvFiles(List<DataNode> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return;
        }
        for (DataNode node : nodes) {
            openCsv(node);
        }
    }

    private void updateOpenGroupList() {
        updateDataGroupListVisibility();
        dataGroupListPanel.setGroupKeys(csvStackPanel.getOpenGroupKeys());
        dataGroupListPanel.setSelectedGroupKey(csvStackPanel.getActiveGroupKey());
    }

    private void updateDataGroupListVisibility() {
        if (leftSplitPane == null) {
            return;
        }
        boolean visible = csvStackPanel.isDataGroupingEnabled();
        dataGroupListPanel.setVisible(visible);
        leftSplitPane.setDividerSize(visible ? 10 : 0);
        leftSplitPane.setDividerLocation(visible ? 300 : 0);
        leftSplitPane.revalidate();
        leftSplitPane.repaint();
    }

    private void showSettingsDialog() {
        SettingsDialog dialog = new SettingsDialog(this, csvStackPanel.isDataGroupingEnabled(),
                preferences.get(ROW_CLIPBOARD_DELIMITER_TYPE_KEY,
                        SettingsDialog.ROW_CLIPBOARD_DELIMITER_TAB),
                new SettingsDialog.SettingsApplyListener() {
                    @Override
                    public boolean settingsApplied(boolean dataGroupingEnabled, String rowClipboardDelimiterType) {
                        return applySettings(dataGroupingEnabled, rowClipboardDelimiterType);
                    }
                });
        dialog.setVisible(true);
    }

    private boolean applySettings(boolean dataGroupingEnabled, String rowClipboardDelimiterType) {
        if (csvStackPanel.isDataGroupingEnabled() == dataGroupingEnabled) {
            saveSettings(dataGroupingEnabled, rowClipboardDelimiterType);
            csvStackPanel.setRowClipboardDelimiter(getRowClipboardDelimiter(rowClipboardDelimiterType));
            saveOpenSession();
            return true;
        }

        saveOpenSession();
        boolean previousSuppressSessionSaving = suppressSessionSaving;
        suppressSessionSaving = true;
        try {
            if (!csvStackPanel.requestCloseAll()) {
                return false;
            }
            saveSettings(dataGroupingEnabled, rowClipboardDelimiterType);
            csvStackPanel.setDataGroupingEnabled(dataGroupingEnabled);
            csvStackPanel.setRowClipboardDelimiter(getRowClipboardDelimiter(rowClipboardDelimiterType));
            updateOpenGroupList();
        } finally {
            suppressSessionSaving = previousSuppressSessionSaving;
        }

        if (rootDirectory != null) {
            restoreOpenSession(rootDirectory);
        }
        return true;
    }

    private void saveSettings(boolean dataGroupingEnabled, String rowClipboardDelimiterType) {
        preferences.putBoolean(DATA_GROUPING_ENABLED_KEY, dataGroupingEnabled);
        preferences.putBoolean(SESSION_DATA_GROUPING_ENABLED_KEY, dataGroupingEnabled);
        preferences.put(ROW_CLIPBOARD_DELIMITER_TYPE_KEY, normalizeRowClipboardDelimiterType(rowClipboardDelimiterType));
    }

    private String getRowClipboardDelimiter() {
        return getRowClipboardDelimiter(preferences.get(ROW_CLIPBOARD_DELIMITER_TYPE_KEY,
                SettingsDialog.ROW_CLIPBOARD_DELIMITER_TAB));
    }

    private static String getRowClipboardDelimiter(String rowClipboardDelimiterType) {
        if (SettingsDialog.ROW_CLIPBOARD_DELIMITER_COMMA.equals(rowClipboardDelimiterType)) {
            return ",";
        }
        return "\t";
    }

    private static String normalizeRowClipboardDelimiterType(String rowClipboardDelimiterType) {
        if (SettingsDialog.ROW_CLIPBOARD_DELIMITER_COMMA.equals(rowClipboardDelimiterType)) {
            return SettingsDialog.ROW_CLIPBOARD_DELIMITER_COMMA;
        }
        return SettingsDialog.ROW_CLIPBOARD_DELIMITER_TAB;
    }

    private String getModeSessionValue(String baseKey, boolean dataGroupingEnabled) {
        return preferences.get(modeSessionKey(baseKey, dataGroupingEnabled), "");
    }

    private static String modeSessionKey(String baseKey, boolean dataGroupingEnabled) {
        return baseKey + (dataGroupingEnabled ? GROUPED_MODE_SUFFIX : FLAT_MODE_SUFFIX);
    }

    private void saveAll() {
        statusBar.showSaving("Saving all open CSV files...");
        try {
            csvStackPanel.saveAll();
            statusBar.setMessage("Saved all open CSV files.");
        } catch (Exception ex) {
            statusBar.showError(ex.getMessage());
            showError("Failed to save all CSV files.", ex);
        }
    }

    private void applyColumnWidthToOpenTables() {
        String value = columnWidthField == null ? "" : columnWidthField.getText();
        int width;
        try {
            width = Integer.parseInt(value.trim());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "列幅には20以上の整数を入力してください。",
                    "列幅の指定エラー",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (width < 20) {
            JOptionPane.showMessageDialog(this,
                    "列幅には20以上の整数を入力してください。",
                    "列幅の指定エラー",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        csvStackPanel.applyColumnWidthToOpenPanels(width);
        statusBar.setMessage("Applied column width to open CSV tables: " + width);
    }

    private void autoFitColumnWidthsForOpenTables() {
        csvStackPanel.autoFitColumnWidthsForOpenPanels();
        statusBar.setMessage("Auto-fitted column widths for open CSV tables.");
    }

    private void closeAllOpenCsvPanels() {
        if (csvStackPanel.requestCloseAll()) {
            saveOpenSession();
            updateOpenGroupList();
            statusBar.setMessage("Closed all open CSV panels.");
        }
    }

    private void closeWindow() {
        saveOpenSession();
        suppressSessionSaving = true;
        if (csvStackPanel.requestCloseAll()) {
            dispose();
        } else {
            suppressSessionSaving = false;
        }
    }

    private void showError(String message, Exception ex) {
        JOptionPane.showMessageDialog(this,
                message + System.lineSeparator() + ex.getMessage(),
                "Error",
                JOptionPane.ERROR_MESSAGE);
    }

    private List<Image> loadApplicationIcons() {
        int[] sizes = new int[]{16, 32, 48, 64, 128};
        List<Image> icons = new ArrayList<Image>();
        for (int size : sizes) {
            URL resource = MainFrame.class.getResource("/com/example/csveditor/icons/app-" + size + ".png");
            if (resource == null) {
                continue;
            }
            try {
                icons.add(ImageIO.read(resource));
            } catch (Exception ignored) {
                // Missing icon should not prevent the editor from starting.
            }
        }
        return icons;
    }
}
