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
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.JToolBar;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.imageio.ImageIO;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.event.InputEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
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
    private JPanel loadingOverlayPanel;
    private JLabel loadingOverlayLabel;
    private JProgressBar loadingOverlayProgressBar;
    private int loadingOverlayDepth;
    private final List<Path> rootDirectories;
    private boolean restoringSession;
    private boolean suppressSessionSaving;
    private static final String LAST_ROOT_FOLDER_KEY = "lastRootFolder";
    private static final String LAST_ROOT_FOLDERS_KEY = "lastRootFolders";
    private static final String SESSION_ROOT_FOLDER_KEY = "sessionRootFolder";
    private static final String SESSION_OPEN_FILES_KEY = "sessionOpenFiles";
    private static final String SESSION_COLLAPSED_GROUPS_KEY = "sessionCollapsedGroups";
    private static final String SESSION_DATA_GROUPING_ENABLED_KEY = "sessionDataGroupingEnabled";
    private static final String DATA_GROUPING_ENABLED_KEY = "dataGroupingEnabled";
    private static final String ROW_CLIPBOARD_DELIMITER_TYPE_KEY = "rowClipboardDelimiterType";
    private static final String AUTO_COLLAPSE_ROW_THRESHOLD_KEY = "autoCollapseRowThreshold";
    private static final String DATA_GROUP_PATH_SEGMENT_LEVEL_KEY = "dataGroupPathSegmentLevel";
    private static final String GROUPED_MODE_SUFFIX = ".grouped";
    private static final String FLAT_MODE_SUFFIX = ".flat";
    private static final int PREFERENCE_VALUE_CHUNK_SIZE = 6000;
    private static final String PREFERENCE_CHUNK_COUNT_SUFFIX = ".chunkCount";

    public MainFrame() {
        super("CSV Data Editor");
        this.scanService = new CsvDataScanService();
        this.documentService = new CsvDocumentService();
        this.treePanel = new CsvDataTreePanel();
        this.dataGroupListPanel = new DataGroupListPanel();
        this.csvStackPanel = new CsvStackPanel(documentService);
        this.statusBar = new StatusBar();
        this.preferences = Preferences.userNodeForPackage(MainFrame.class);
        this.rootDirectories = new ArrayList<Path>();
        this.csvStackPanel.setDataGroupingEnabled(preferences.getBoolean(DATA_GROUPING_ENABLED_KEY, true));
        this.csvStackPanel.setRowClipboardDelimiter(getRowClipboardDelimiter());
        this.csvStackPanel.setAutoCollapseRowThreshold(getAutoCollapseRowThreshold());
        this.csvStackPanel.setDataGroupPathSegmentLevel(getDataGroupPathSegmentLevel());
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
        installLoadingOverlay();
    }

    private void installLoadingOverlay() {
        loadingOverlayPanel = new JPanel(new GridBagLayout()) {
            @Override
            protected void paintComponent(Graphics graphics) {
                super.paintComponent(graphics);
                graphics.setColor(new Color(0, 0, 0, 72));
                graphics.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        loadingOverlayPanel.setOpaque(false);
        loadingOverlayPanel.setFocusTraversalKeysEnabled(false);
        loadingOverlayPanel.addMouseListener(new MouseAdapter() {
        });
        loadingOverlayPanel.addMouseMotionListener(new MouseAdapter() {
        });
        loadingOverlayPanel.addKeyListener(new KeyAdapter() {
        });

        JPanel messagePanel = new JPanel(new BorderLayout(10, 8));
        messagePanel.setBackground(new Color(250, 250, 250));
        messagePanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(120, 150, 170)),
                BorderFactory.createEmptyBorder(14, 18, 14, 18)));

        loadingOverlayLabel = new JLabel("読み込み中...");
        loadingOverlayProgressBar = new JProgressBar();
        loadingOverlayProgressBar.setIndeterminate(true);
        loadingOverlayProgressBar.setPreferredSize(new Dimension(240, 18));
        messagePanel.add(loadingOverlayLabel, BorderLayout.NORTH);
        messagePanel.add(loadingOverlayProgressBar, BorderLayout.CENTER);

        loadingOverlayPanel.add(messagePanel, new GridBagConstraints());
        loadingOverlayPanel.setVisible(false);
        setGlassPane(loadingOverlayPanel);
    }

    private void beginBlockingWork(final String message) {
        Runnable task = new Runnable() {
            @Override
            public void run() {
                loadingOverlayDepth++;
                if (loadingOverlayLabel != null) {
                    loadingOverlayLabel.setText(message == null ? "読み込み中..." : message);
                }
                if (loadingOverlayPanel != null) {
                    loadingOverlayPanel.setVisible(true);
                    loadingOverlayPanel.requestFocusInWindow();
                }
                setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            }
        };
        if (SwingUtilities.isEventDispatchThread()) {
            task.run();
        } else {
            SwingUtilities.invokeLater(task);
        }
    }

    private void endBlockingWork() {
        Runnable task = new Runnable() {
            @Override
            public void run() {
                if (loadingOverlayDepth > 0) {
                    loadingOverlayDepth--;
                }
                if (loadingOverlayDepth == 0) {
                    if (loadingOverlayPanel != null) {
                        loadingOverlayPanel.setVisible(false);
                    }
                    setCursor(Cursor.getDefaultCursor());
                }
            }
        };
        if (SwingUtilities.isEventDispatchThread()) {
            task.run();
        } else {
            SwingUtilities.invokeLater(task);
        }
    }

    private JMenuBar createMenuBar() {
        JMenuBar menuBar = new JMenuBar();
        JMenu fileMenu = new JMenu("File");

        JMenuItem openRootItem = new JMenuItem("Add Root Folder...");
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

        JButton openRootButton = new JButton("Add Root");
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
        treePanel.setRootFolderRegistrationListener(new CsvDataTreePanel.RootFolderRegistrationListener() {
            @Override
            public void rootFolderUnregisterRequested(DataNode node) {
                unregisterRootFolder(node);
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
        Path initialRoot = rootDirectories.isEmpty() ? null : rootDirectories.get(rootDirectories.size() - 1);
        JFileChooser chooser = new JFileChooser(initialRoot == null ? null : initialRoot.toFile());
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setMultiSelectionEnabled(true);
        chooser.setDialogTitle("Select root folders");
        int result = chooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            List<Path> selectedRoots = normalizeSelectedRootFolders(chooser);
            if (selectedRoots.isEmpty()) {
                return;
            }
            List<Path> mergedRoots = mergeRootDirectories(rootDirectories, selectedRoots);
            if (mergedRoots.equals(rootDirectories)) {
                statusBar.setMessage("Root folder is already loaded.");
                return;
            }
            loadRootFolders(mergedRoots);
        }
    }

    private void loadRootFolder(File rootFolder) {
        List<Path> roots = new ArrayList<Path>();
        roots.add(rootFolder.toPath().toAbsolutePath().normalize());
        loadRootFolders(roots);
    }

    private void loadRootFolders(final List<Path> selectedRoots) {
        statusBar.showLoading("Loading folders: " + joinDisplayPaths(selectedRoots));
        beginBlockingWork("フォルダーを読み込んでいます...");

        SwingWorker<DataNode, Void> worker = new SwingWorker<DataNode, Void>() {
            @Override
            protected DataNode doInBackground() throws Exception {
                return scanService.scan(selectedRoots);
            }

            @Override
            protected void done() {
                try {
                    DataNode rootNode = get();
                    rootDirectories.clear();
                    rootDirectories.addAll(selectedRoots);
                    saveLastRootFolders(selectedRoots);
                    treePanel.setRegisteredRootPaths(rootDirectories);
                    treePanel.setRootNode(rootNode);
                    statusBar.setMessage("Loaded folders: " + joinDisplayPaths(selectedRoots));
                    restoreOpenSession(selectedRoots);
                } catch (Exception ex) {
                    treePanel.clear("Failed to load folder");
                    statusBar.showError(ex.getMessage());
                    showError("Failed to load folder.", ex);
                } finally {
                    endBlockingWork();
                }
            }
        };
        worker.execute();
    }

    private void loadLastRootFolder() {
        String lastRootFolders = getPreferenceValue(LAST_ROOT_FOLDERS_KEY, null);
        List<String> folderValues = splitLines(lastRootFolders);
        if (folderValues.isEmpty()) {
            String lastRootFolder = preferences.get(LAST_ROOT_FOLDER_KEY, null);
            if (lastRootFolder != null && lastRootFolder.trim().length() > 0) {
                folderValues.add(lastRootFolder.trim());
            }
        }
        if (folderValues.isEmpty()) {
            return;
        }
        List<Path> existingRoots = new ArrayList<Path>();
        List<Path> missingRoots = new ArrayList<Path>();
        for (String folderValue : folderValues) {
            Path lastRoot = Paths.get(folderValue).toAbsolutePath().normalize();
            if (Files.isDirectory(lastRoot)) {
                existingRoots.add(lastRoot);
            } else {
                missingRoots.add(lastRoot);
            }
        }
        if (!existingRoots.isEmpty()) {
            loadRootFolders(existingRoots);
        } else if (!missingRoots.isEmpty()) {
            statusBar.setMessage("Last root folders were not found: " + joinDisplayPaths(missingRoots));
        }
    }

    private void saveLastRootFolders(List<Path> selectedRoots) {
        putPreferenceValue(LAST_ROOT_FOLDERS_KEY, joinPaths(selectedRoots));
        if (selectedRoots != null && !selectedRoots.isEmpty()) {
            preferences.put(LAST_ROOT_FOLDER_KEY, selectedRoots.get(selectedRoots.size() - 1)
                    .toAbsolutePath().normalize().toString());
        }
    }

    private void saveOpenSession() {
        if (rootDirectories.isEmpty()) {
            return;
        }
        putPreferenceValue(SESSION_ROOT_FOLDER_KEY, joinPaths(rootDirectories));
        boolean dataGroupingEnabled = csvStackPanel.isDataGroupingEnabled();
        putPreferenceValue(modeSessionKey(SESSION_OPEN_FILES_KEY, dataGroupingEnabled),
                joinLines(getOpenSessionEntries()));
        putPreferenceValue(modeSessionKey(SESSION_COLLAPSED_GROUPS_KEY, dataGroupingEnabled),
                joinLines(new ArrayList<String>(csvStackPanel.getCollapsedGroupKeys())));
        preferences.putBoolean(SESSION_DATA_GROUPING_ENABLED_KEY, csvStackPanel.isDataGroupingEnabled());
    }

    private void unregisterRootFolder(DataNode node) {
        if (node == null) {
            return;
        }
        Path targetRoot = node.getPath().toAbsolutePath().normalize();
        List<Path> remainingRoots = new ArrayList<Path>();
        boolean removed = false;
        for (Path rootDirectory : rootDirectories) {
            Path normalizedRoot = rootDirectory.toAbsolutePath().normalize();
            if (normalizedRoot.equals(targetRoot)) {
                removed = true;
            } else {
                remainingRoots.add(normalizedRoot);
            }
        }
        if (!removed) {
            statusBar.setMessage("Only registered root folders can be removed from the list.");
            return;
        }
        if (remainingRoots.isEmpty()) {
            rootDirectories.clear();
            clearSavedRootFolders();
            treePanel.setRegisteredRootPaths(rootDirectories);
            treePanel.clear("Select a root folder");
            statusBar.setMessage("Removed root folder from the list: " + targetRoot);
            saveOpenSession();
            return;
        }

        loadRootFolders(remainingRoots);
        statusBar.setMessage("Removing root folder from the list: " + targetRoot);
    }

    private void clearSavedRootFolders() {
        putPreferenceValue(LAST_ROOT_FOLDERS_KEY, "");
        preferences.remove(LAST_ROOT_FOLDER_KEY);
        putPreferenceValue(SESSION_ROOT_FOLDER_KEY, "");
    }

    private void restoreOpenSession(final List<Path> selectedRoots) {
        String sessionRoot = getPreferenceValue(SESSION_ROOT_FOLDER_KEY, null);
        if (sessionRoot == null || !samePathList(selectedRoots, parseRootPaths(sessionRoot))) {
            return;
        }
        final boolean sessionDataGroupingEnabled = preferences.getBoolean(
                SESSION_DATA_GROUPING_ENABLED_KEY,
                preferences.getBoolean(DATA_GROUPING_ENABLED_KEY, true));
        final List<String> sessionEntries = splitLines(getModeSessionValue(
                SESSION_OPEN_FILES_KEY, sessionDataGroupingEnabled));
        final Set<String> collapsedGroups = new HashSet<String>(splitLines(getModeSessionValue(
                SESSION_COLLAPSED_GROUPS_KEY, sessionDataGroupingEnabled)));
        if (sessionEntries.isEmpty() && collapsedGroups.isEmpty()) {
            csvStackPanel.setDataGroupingEnabled(sessionDataGroupingEnabled);
            updateOpenGroupList();
            return;
        }
        statusBar.showLoading("Restoring previous open CSV files...");
        beginBlockingWork("前回開いていたCSVを復元しています...");
        SwingWorker<List<CsvDocument>, Void> worker = new SwingWorker<List<CsvDocument>, Void>() {
            @Override
            protected List<CsvDocument> doInBackground() {
                List<CsvDocument> documents = new ArrayList<CsvDocument>();
                for (String sessionEntry : sessionEntries) {
                    SessionFileEntry entry = parseSessionFileEntry(sessionEntry);
                    if (entry == null || entry.rootIndex < 0 || entry.rootIndex >= selectedRoots.size()) {
                        continue;
                    }
                    Path selectedRoot = selectedRoots.get(entry.rootIndex);
                    String relativePath = entry.relativePath;
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
                    endBlockingWork();
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

    private List<String> getOpenSessionEntries() {
        List<String> entries = new ArrayList<String>();
        for (CsvEditorPanel panel : csvStackPanel.getOpenPanels()) {
            Path filePath = panel.getDocument().getFilePath();
            int rootIndex = findRootIndexForPath(filePath);
            if (rootIndex < 0) {
                continue;
            }
            entries.add(rootIndex + "\t" + panel.getDocument().getRelativePath().toString());
        }
        return entries;
    }

    private Path resolveRootForPath(Path filePath) {
        int rootIndex = findRootIndexForPath(filePath);
        if (rootIndex >= 0) {
            return rootDirectories.get(rootIndex);
        }
        throw new IllegalStateException("CSV file is outside the selected root folders: " + filePath);
    }

    private int findRootIndexForPath(Path filePath) {
        if (filePath == null) {
            return -1;
        }
        Path normalizedFilePath = filePath.toAbsolutePath().normalize();
        int bestIndex = -1;
        int bestNameCount = -1;
        for (int i = 0; i < rootDirectories.size(); i++) {
            Path root = rootDirectories.get(i).toAbsolutePath().normalize();
            if (normalizedFilePath.startsWith(root) && root.getNameCount() > bestNameCount) {
                bestIndex = i;
                bestNameCount = root.getNameCount();
            }
        }
        return bestIndex;
    }

    private static SessionFileEntry parseSessionFileEntry(String value) {
        if (value == null || value.trim().length() == 0) {
            return null;
        }
        int separatorIndex = value.indexOf('\t');
        if (separatorIndex < 0) {
            return new SessionFileEntry(0, value.trim());
        }
        try {
            int rootIndex = Integer.parseInt(value.substring(0, separatorIndex).trim());
            String relativePath = value.substring(separatorIndex + 1).trim();
            if (relativePath.length() == 0) {
                return null;
            }
            return new SessionFileEntry(rootIndex, relativePath);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static List<Path> normalizeSelectedRootFolders(JFileChooser chooser) {
        List<Path> roots = new ArrayList<Path>();
        File[] selectedFiles = chooser.getSelectedFiles();
        if (selectedFiles == null || selectedFiles.length == 0) {
            File selectedFile = chooser.getSelectedFile();
            if (selectedFile != null) {
                selectedFiles = new File[]{selectedFile};
            }
        }
        if (selectedFiles == null) {
            return roots;
        }
        for (File selectedFile : selectedFiles) {
            if (selectedFile == null) {
                continue;
            }
            Path root = selectedFile.toPath().toAbsolutePath().normalize();
            if (!roots.contains(root)) {
                roots.add(root);
            }
        }
        return roots;
    }

    private static List<Path> mergeRootDirectories(List<Path> currentRoots, List<Path> selectedRoots) {
        List<Path> mergedRoots = new ArrayList<Path>();
        if (currentRoots != null) {
            for (Path root : currentRoots) {
                Path normalizedRoot = root.toAbsolutePath().normalize();
                if (!mergedRoots.contains(normalizedRoot)) {
                    mergedRoots.add(normalizedRoot);
                }
            }
        }
        if (selectedRoots != null) {
            for (Path root : selectedRoots) {
                Path normalizedRoot = root.toAbsolutePath().normalize();
                if (!mergedRoots.contains(normalizedRoot)) {
                    mergedRoots.add(normalizedRoot);
                }
            }
        }
        return mergedRoots;
    }

    private static String joinPaths(List<Path> paths) {
        List<String> values = new ArrayList<String>();
        if (paths != null) {
            for (Path path : paths) {
                if (path != null) {
                    values.add(path.toAbsolutePath().normalize().toString());
                }
            }
        }
        return joinLines(values);
    }

    private static String joinDisplayPaths(List<Path> paths) {
        if (paths == null || paths.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (Path path : paths) {
            if (path == null) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(path.toAbsolutePath().normalize());
        }
        return builder.toString();
    }

    private static List<Path> parseRootPaths(String value) {
        List<Path> roots = new ArrayList<Path>();
        for (String line : splitLines(value)) {
            Path root = Paths.get(line).toAbsolutePath().normalize();
            if (!roots.contains(root)) {
                roots.add(root);
            }
        }
        return roots;
    }

    private static boolean samePathList(List<Path> left, List<Path> right) {
        if (left == null || right == null || left.size() != right.size()) {
            return false;
        }
        for (int i = 0; i < left.size(); i++) {
            Path leftPath = left.get(i).toAbsolutePath().normalize();
            Path rightPath = right.get(i).toAbsolutePath().normalize();
            if (!leftPath.equals(rightPath)) {
                return false;
            }
        }
        return true;
    }

    private void openCsv(final DataNode node) {
        final Path csvPath = node.getPath().toAbsolutePath().normalize();
        if (csvStackPanel.hasOpenDocument(csvPath)) {
            csvStackPanel.focusDocument(csvPath);
            statusBar.setMessage("Already open: " + node.getRelativePath());
            return;
        }

        statusBar.showLoading("Opening CSV: " + node.getRelativePath());
        beginBlockingWork("CSVを開いています...");
        SwingWorker<CsvDocument, Void> worker = new SwingWorker<CsvDocument, Void>() {
            @Override
            protected CsvDocument doInBackground() throws Exception {
                return documentService.open(resolveRootForPath(csvPath), csvPath);
            }

            @Override
            protected void done() {
                try {
                    CsvDocument document = get();
                    csvStackPanel.addOrFocusDocument(document);
                    statusBar.setMessage("Opened: " + document.getRelativePath());
                } catch (Exception ex) {
                    statusBar.showError(ex.getMessage());
                    showError("Failed to open CSV.", ex);
                } finally {
                    endBlockingWork();
                }
            }
        };
        worker.execute();
    }

    private void openCsvFiles(List<DataNode> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return;
        }
        final List<DataNode> nodesToOpen = new ArrayList<DataNode>();
        for (DataNode node : nodes) {
            if (node == null) {
                continue;
            }
            Path csvPath = node.getPath().toAbsolutePath().normalize();
            if (csvStackPanel.hasOpenDocument(csvPath)) {
                csvStackPanel.focusDocument(csvPath);
            } else {
                nodesToOpen.add(node);
            }
        }
        if (nodesToOpen.isEmpty()) {
            statusBar.setMessage("Selected CSV files are already open.");
            return;
        }

        statusBar.showLoading("Opening CSV files: " + nodesToOpen.size());
        beginBlockingWork("CSVを開いています... " + nodesToOpen.size() + "件");
        SwingWorker<OpenCsvBatchResult, Void> worker = new SwingWorker<OpenCsvBatchResult, Void>() {
            @Override
            protected OpenCsvBatchResult doInBackground() {
                OpenCsvBatchResult result = new OpenCsvBatchResult();
                for (DataNode node : nodesToOpen) {
                    Path csvPath = node.getPath().toAbsolutePath().normalize();
                    try {
                        result.documents.add(documentService.open(resolveRootForPath(csvPath), csvPath));
                    } catch (Exception ex) {
                        result.failedPaths.add(node.getRelativePath().toString());
                    }
                }
                return result;
            }

            @Override
            protected void done() {
                try {
                    OpenCsvBatchResult result = get();
                    for (CsvDocument document : result.documents) {
                        csvStackPanel.addOrFocusDocument(document);
                    }
                    if (result.failedPaths.isEmpty()) {
                        statusBar.setMessage("Opened CSV files: " + result.documents.size());
                    } else {
                        statusBar.showError("Opened " + result.documents.size()
                                + " CSV files. Failed: " + result.failedPaths.size());
                        showError("Failed to open some CSV files.",
                                new Exception(joinLines(result.failedPaths)));
                    }
                } catch (Exception ex) {
                    statusBar.showError(ex.getMessage());
                    showError("Failed to open CSV files.", ex);
                } finally {
                    endBlockingWork();
                }
            }
        };
        worker.execute();
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
                getAutoCollapseRowThreshold(),
                getDataGroupPathSegmentLevel(),
                new SettingsDialog.SettingsApplyListener() {
                    @Override
                    public boolean settingsApplied(boolean dataGroupingEnabled, String rowClipboardDelimiterType,
                            int autoCollapseRowThreshold, int dataGroupPathSegmentLevel) {
                        return applySettings(dataGroupingEnabled, rowClipboardDelimiterType,
                                autoCollapseRowThreshold, dataGroupPathSegmentLevel);
                    }
                });
        dialog.setVisible(true);
    }

    private boolean applySettings(boolean dataGroupingEnabled, String rowClipboardDelimiterType,
            int autoCollapseRowThreshold, int dataGroupPathSegmentLevel) {
        if (csvStackPanel.isDataGroupingEnabled() == dataGroupingEnabled) {
            saveSettings(dataGroupingEnabled, rowClipboardDelimiterType, autoCollapseRowThreshold,
                    dataGroupPathSegmentLevel);
            csvStackPanel.setRowClipboardDelimiter(getRowClipboardDelimiter(rowClipboardDelimiterType));
            csvStackPanel.setAutoCollapseRowThreshold(autoCollapseRowThreshold);
            csvStackPanel.setDataGroupPathSegmentLevel(dataGroupPathSegmentLevel);
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
            saveSettings(dataGroupingEnabled, rowClipboardDelimiterType, autoCollapseRowThreshold,
                    dataGroupPathSegmentLevel);
            csvStackPanel.setDataGroupingEnabled(dataGroupingEnabled);
            csvStackPanel.setRowClipboardDelimiter(getRowClipboardDelimiter(rowClipboardDelimiterType));
            csvStackPanel.setAutoCollapseRowThreshold(autoCollapseRowThreshold);
            csvStackPanel.setDataGroupPathSegmentLevel(dataGroupPathSegmentLevel);
            updateOpenGroupList();
        } finally {
            suppressSessionSaving = previousSuppressSessionSaving;
        }

        if (!rootDirectories.isEmpty()) {
            restoreOpenSession(rootDirectories);
        }
        return true;
    }

    private void saveSettings(boolean dataGroupingEnabled, String rowClipboardDelimiterType,
            int autoCollapseRowThreshold, int dataGroupPathSegmentLevel) {
        preferences.putBoolean(DATA_GROUPING_ENABLED_KEY, dataGroupingEnabled);
        preferences.putBoolean(SESSION_DATA_GROUPING_ENABLED_KEY, dataGroupingEnabled);
        preferences.put(ROW_CLIPBOARD_DELIMITER_TYPE_KEY, normalizeRowClipboardDelimiterType(rowClipboardDelimiterType));
        preferences.putInt(AUTO_COLLAPSE_ROW_THRESHOLD_KEY, Math.max(0, autoCollapseRowThreshold));
        preferences.putInt(DATA_GROUP_PATH_SEGMENT_LEVEL_KEY, Math.max(1, dataGroupPathSegmentLevel));
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

    private int getAutoCollapseRowThreshold() {
        return Math.max(0, preferences.getInt(AUTO_COLLAPSE_ROW_THRESHOLD_KEY, 0));
    }

    private int getDataGroupPathSegmentLevel() {
        return Math.max(1, preferences.getInt(DATA_GROUP_PATH_SEGMENT_LEVEL_KEY, 2));
    }

    private String getModeSessionValue(String baseKey, boolean dataGroupingEnabled) {
        return getPreferenceValue(modeSessionKey(baseKey, dataGroupingEnabled), "");
    }

    private void putPreferenceValue(String key, String value) {
        String normalizedValue = value == null ? "" : value;
        int previousChunkCount = preferences.getInt(chunkCountKey(key), 0);
        if (normalizedValue.length() <= PREFERENCE_VALUE_CHUNK_SIZE) {
            preferences.put(key, normalizedValue);
            preferences.remove(chunkCountKey(key));
            removePreferenceChunks(key, previousChunkCount);
            return;
        }

        preferences.remove(key);
        List<String> chunks = splitPreferenceValue(normalizedValue);
        preferences.putInt(chunkCountKey(key), chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            preferences.put(chunkKey(key, i), chunks.get(i));
        }
        removePreferenceChunks(key, previousChunkCount, chunks.size());
    }

    private String getPreferenceValue(String key, String defaultValue) {
        int chunkCount = preferences.getInt(chunkCountKey(key), -1);
        if (chunkCount <= 0) {
            return preferences.get(key, defaultValue);
        }

        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < chunkCount; i++) {
            String chunk = preferences.get(chunkKey(key, i), null);
            if (chunk == null) {
                return preferences.get(key, defaultValue);
            }
            builder.append(chunk);
        }
        return builder.toString();
    }

    static List<String> splitPreferenceValue(String value) {
        List<String> chunks = new ArrayList<String>();
        String normalizedValue = value == null ? "" : value;
        if (normalizedValue.length() == 0) {
            chunks.add("");
            return chunks;
        }
        for (int start = 0; start < normalizedValue.length(); start += PREFERENCE_VALUE_CHUNK_SIZE) {
            int end = Math.min(normalizedValue.length(), start + PREFERENCE_VALUE_CHUNK_SIZE);
            chunks.add(normalizedValue.substring(start, end));
        }
        return chunks;
    }

    private void removePreferenceChunks(String key, int chunkCount) {
        removePreferenceChunks(key, chunkCount, 0);
    }

    private void removePreferenceChunks(String key, int chunkCount, int startIndex) {
        for (int i = Math.max(0, startIndex); i < chunkCount; i++) {
            preferences.remove(chunkKey(key, i));
        }
    }

    private static String chunkCountKey(String key) {
        return key + PREFERENCE_CHUNK_COUNT_SUFFIX;
    }

    private static String chunkKey(String key, int index) {
        return key + "." + index;
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

    private static final class SessionFileEntry {
        private final int rootIndex;
        private final String relativePath;

        private SessionFileEntry(int rootIndex, String relativePath) {
            this.rootIndex = rootIndex;
            this.relativePath = relativePath;
        }
    }

    private static final class OpenCsvBatchResult {
        private final List<CsvDocument> documents = new ArrayList<CsvDocument>();
        private final List<String> failedPaths = new ArrayList<String>();
    }
}
