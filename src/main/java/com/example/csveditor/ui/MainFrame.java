package com.example.csveditor.ui;

import com.example.csveditor.domain.CsvDocument;
import com.example.csveditor.domain.DataNode;
import com.example.csveditor.service.CsvDocumentService;
import com.example.csveditor.service.CsvDataScanService;

import javax.swing.JButton;
import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.JToolBar;
import javax.swing.SwingWorker;
import javax.imageio.ImageIO;
import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Dimension;
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
    private final CsvStackPanel csvStackPanel;
    private final StatusBar statusBar;
    private final CsvDataScanService scanService;
    private final CsvDocumentService documentService;
    private final Preferences preferences;
    private JTextField columnWidthField;
    private Path rootDirectory;
    private boolean restoringSession;
    private boolean suppressSessionSaving;
    private static final String LAST_ROOT_FOLDER_KEY = "lastRootFolder";
    private static final String SESSION_ROOT_FOLDER_KEY = "sessionRootFolder";
    private static final String SESSION_OPEN_FILES_KEY = "sessionOpenFiles";
    private static final String SESSION_COLLAPSED_GROUPS_KEY = "sessionCollapsedGroups";

    public MainFrame() {
        super("CSV Data Editor");
        this.scanService = new CsvDataScanService();
        this.documentService = new CsvDocumentService();
        this.treePanel = new CsvDataTreePanel();
        this.csvStackPanel = new CsvStackPanel(documentService);
        this.statusBar = new StatusBar();
        this.preferences = Preferences.userNodeForPackage(MainFrame.class);
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

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, treePanel, csvStackPanel);
        splitPane.setResizeWeight(0.0);
        splitPane.setDividerLocation(300);
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

        toolBar.addSeparator();
        columnWidthField = new JTextField("110");
        Dimension columnWidthFieldSize = new Dimension(48, 24);
        columnWidthField.setPreferredSize(columnWidthFieldSize);
        columnWidthField.setMinimumSize(columnWidthFieldSize);
        columnWidthField.setMaximumSize(columnWidthFieldSize);
        columnWidthField.setToolTipText("表示中のCSVテーブルへ適用する列幅をpxで指定します。");
        columnWidthField.addActionListener(e -> applyColumnWidthToOpenTables());
        toolBar.add(columnWidthField);

        JButton applyColumnWidthButton = new JButton("列幅を全設定");
        applyColumnWidthButton.setToolTipText("表示中のCSVテーブルすべての列幅を指定値に変更します。");
        applyColumnWidthButton.addActionListener(e -> applyColumnWidthToOpenTables());
        toolBar.add(applyColumnWidthButton);

        JButton autoFitColumnWidthButton = new JButton("列幅を全自動調整");
        autoFitColumnWidthButton.setToolTipText("表示中のCSVテーブルすべての列幅を列名とセル値に合わせて自動調整します。");
        autoFitColumnWidthButton.addActionListener(e -> autoFitColumnWidthsForOpenTables());
        toolBar.add(autoFitColumnWidthButton);

        return toolBar;
    }

    private void installListeners() {
        treePanel.setCsvOpenListener(new CsvDataTreePanel.CsvOpenListener() {
            @Override
            public void csvOpenRequested(DataNode node) {
                openCsv(node);
            }
        });
        csvStackPanel.setSessionChangeListener(new CsvStackPanel.SessionChangeListener() {
            @Override
            public void sessionChanged() {
                if (!restoringSession && !suppressSessionSaving) {
                    saveOpenSession();
                }
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
        preferences.put(SESSION_OPEN_FILES_KEY, joinLines(csvStackPanel.getOpenRelativePaths()));
        preferences.put(SESSION_COLLAPSED_GROUPS_KEY, joinLines(new ArrayList<String>(csvStackPanel.getCollapsedGroupKeys())));
    }

    private void restoreOpenSession(final Path selectedRoot) {
        String sessionRoot = preferences.get(SESSION_ROOT_FOLDER_KEY, null);
        if (sessionRoot == null || !selectedRoot.toString().equals(Paths.get(sessionRoot).toAbsolutePath().normalize().toString())) {
            return;
        }
        final List<String> relativePaths = splitLines(preferences.get(SESSION_OPEN_FILES_KEY, ""));
        final Set<String> collapsedGroups = new HashSet<String>(splitLines(preferences.get(SESSION_COLLAPSED_GROUPS_KEY, "")));
        if (relativePaths.isEmpty() && collapsedGroups.isEmpty()) {
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
                    csvStackPanel.setCollapsedGroupKeys(collapsedGroups);
                    for (CsvDocument document : get()) {
                        csvStackPanel.addOrFocusDocument(document);
                    }
                    csvStackPanel.setCollapsedGroupKeys(collapsedGroups);
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
