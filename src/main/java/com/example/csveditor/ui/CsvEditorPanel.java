package com.example.csveditor.ui;

import com.example.csveditor.domain.CsvDocument;
import com.example.csveditor.service.CsvDocumentService;

import javax.swing.BorderFactory;
import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JViewport;
import javax.swing.BoxLayout;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableColumn;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Rectangle;
import java.awt.FontMetrics;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Independent editor card for exactly one CSV document.
 */
public final class CsvEditorPanel extends JPanel {
    public interface CloseHandler {
        void closeRequested(CsvEditorPanel panel);
    }

    public interface CloseRequestListener {
        void closeRequested(CsvEditorPanel panel);
    }

    public interface MoveRequestListener {
        void moveUpRequested(CsvEditorPanel panel);

        void moveDownRequested(CsvEditorPanel panel);
    }

    public interface TableSelectionListener {
        void tableSelectionChanged(CsvEditorPanel panel);
    }

    private final CsvDocumentService documentService;
    private static final int MAX_UNDO_HISTORY = 5;
    private static final Color DIRTY_BACKGROUND = new Color(255, 250, 218);
    private CsvDocument document;
    private CsvTableModel tableModel;
    private JTable table;
    private JScrollPane tableScrollPane;
    private int tableWidth;
    private int tableHeight;
    private boolean tableHeightManuallyResized;
    private int resizeStartX;
    private int resizeStartY;
    private int resizeStartWidth;
    private int resizeStartHeight;
    private boolean resizingWidth;
    private boolean resizingHeight;
    private JLabel titleLabel;
    private JLabel pathLabel;
    private JLabel summaryLabel;
    private JLabel dirtyLabel;
    private JPanel csvContentPanel;
    private JPanel infoPanel;
    private JPanel titlePanel;
    private JPanel titleHeaderPanel;
    private JPanel titleDetailsPanel;
    private JPanel actionsPanel;
    private JPanel tablePanel;
    private JPanel moveButtonsPanel;
    private JButton saveButton;
    private JButton reloadButton;
    private JButton closeButton;
    private JButton openFolderButton;
    private JButton moveUpButton;
    private JButton moveDownButton;
    private JButton collapseButton;
    private CloseHandler closeHandler;
    private MoveRequestListener moveRequestListener;
    private TableSelectionListener tableSelectionListener;
    private List<List<String>> copiedRows;
    private int popupModelRow = -1;
    private List<List<String>> savedRowsSnapshot;
    private Deque<List<List<String>>> undoHistory = new ArrayDeque<List<List<String>>>();
    private Deque<List<List<String>>> redoHistory = new ArrayDeque<List<List<String>>>();
    private boolean restoringHistory;
    private boolean busy;
    private boolean canMoveUp = true;
    private boolean canMoveDown = true;
    private boolean collapsed;
    private boolean suppressSelectionNotification;

    public CsvEditorPanel(CsvDocument document, CsvDocumentService documentService) {
        if (document == null) {
            throw new IllegalArgumentException("document must not be null");
        }
        if (documentService == null) {
            throw new IllegalArgumentException("documentService must not be null");
        }
        this.document = document;
        this.documentService = documentService;
        buildUi();
        savedRowsSnapshot = copyRows(document.copyRows());
        updateLabels();
    }

    public CsvDocument getDocument() {
        return document;
    }

    public boolean isDirty() {
        return document.isDirty();
    }

    public boolean isModified() {
        return isDirty();
    }

    @Override
    public Dimension getMaximumSize() {
        return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
    }

    public void setCloseHandler(CloseHandler closeHandler) {
        this.closeHandler = closeHandler;
    }

    public void setCloseRequestListener(final CloseRequestListener closeRequestListener) {
        if (closeRequestListener == null) {
            this.closeHandler = null;
            return;
        }
        this.closeHandler = new CloseHandler() {
            @Override
            public void closeRequested(CsvEditorPanel panel) {
                closeRequestListener.closeRequested(panel);
            }
        };
    }

    public void setMoveRequestListener(MoveRequestListener moveRequestListener) {
        this.moveRequestListener = moveRequestListener;
    }

    public void setTableSelectionListener(TableSelectionListener tableSelectionListener) {
        this.tableSelectionListener = tableSelectionListener;
    }

    public void clearTableSelection() {
        if (table == null) {
            return;
        }
        suppressSelectionNotification = true;
        try {
            table.clearSelection();
        } finally {
            suppressSelectionNotification = false;
        }
    }

    public void setMoveAvailability(boolean canMoveUp, boolean canMoveDown) {
        this.canMoveUp = canMoveUp;
        this.canMoveDown = canMoveDown;
        updateMoveButtons();
    }

    public boolean saveDocument() {
        if (table.isEditing()) {
            table.getCellEditor().stopCellEditing();
        }
        try {
            setBusy(true);
            documentService.save(document);
            savedRowsSnapshot = copyRows(document.copyRows());
            document.clearDirty();
            tableModel.fireTableDataChanged();
            updateLabels();
            return true;
        } catch (IOException ex) {
            showError("保存に失敗しました。", ex);
            return false;
        } finally {
            setBusy(false);
        }
    }

    public boolean reloadDocumentWithConfirmation() {
        if (document.isDirty()) {
            UnsavedChangeDialogs.Choice choice = UnsavedChangeDialogs.confirmReload(this, document);
            if (choice == UnsavedChangeDialogs.Choice.CANCEL) {
                return false;
            }
            if (choice == UnsavedChangeDialogs.Choice.SAVE && !saveDocument()) {
                return false;
            }
        }
        return reloadDocumentDiscardingChanges();
    }

    public boolean requestClose() {
        if (document.isDirty()) {
            UnsavedChangeDialogs.Choice choice = UnsavedChangeDialogs.confirmClose(this, document);
            if (choice == UnsavedChangeDialogs.Choice.CANCEL) {
                return false;
            }
            if (choice == UnsavedChangeDialogs.Choice.SAVE && !saveDocument()) {
                return false;
            }
        }
        if (closeHandler != null) {
            closeHandler.closeRequested(this);
        }
        return true;
    }

    public boolean confirmForApplicationExit(Component parent) {
        if (!document.isDirty()) {
            return true;
        }
        UnsavedChangeDialogs.Choice choice = UnsavedChangeDialogs.confirmExit(parent, document);
        if (choice == UnsavedChangeDialogs.Choice.CANCEL) {
            return false;
        }
        if (choice == UnsavedChangeDialogs.Choice.SAVE) {
            return saveDocument();
        }
        return true;
    }

    public void applyColumnWidthToAllColumns(int width) {
        int normalizedWidth = Math.max(20, width);
        for (int i = 0; i < table.getColumnModel().getColumnCount(); i++) {
            TableColumn column = table.getColumnModel().getColumn(i);
            column.setPreferredWidth(normalizedWidth);
            column.setWidth(normalizedWidth);
        }
        tableWidth = calculateAutomaticTableWidth();
        updateTableScrollPaneSize();
    }

    public void autoFitAllColumnWidths() {
        for (int i = 0; i < table.getColumnModel().getColumnCount(); i++) {
            int width = calculatePreferredColumnWidth(i);
            TableColumn column = table.getColumnModel().getColumn(i);
            column.setPreferredWidth(width);
            column.setWidth(width);
        }
        tableWidth = calculateAutomaticTableWidth();
        updateTableScrollPaneSize();
    }

    public void refreshResponsiveTableSize() {
        updateTableScrollPaneSize();
    }

    private boolean reloadDocumentDiscardingChanges() {
        try {
            setBusy(true);
            CsvDocument reloaded = documentService.reload(document);
            replaceDocument(reloaded);
            return true;
        } catch (IOException ex) {
            showError("再読み込みに失敗しました。", ex);
            return false;
        } finally {
            setBusy(false);
        }
    }

    private void replaceDocument(CsvDocument reloaded) {
        if (reloaded == null) {
            throw new IllegalStateException("reloaded document must not be null");
        }
        document = reloaded;
        savedRowsSnapshot = copyRows(document.copyRows());
        undoHistory.clear();
        redoHistory.clear();
        tableModel = new CsvTableModel(document);
        attachUndoListener(tableModel);
        attachDirtyListener(tableModel);
        table.setModel(tableModel);
        configureTableColumns();
        tableWidth = calculateAutomaticTableWidth();
        tableHeightManuallyResized = false;
        tableHeight = calculateAutomaticTableHeight();
        updateTableScrollPaneSize();
        updateLabels();
    }

    private void buildUi() {
        setLayout(new BorderLayout(2, 0));
        setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));

        csvContentPanel = new JPanel(new BorderLayout(2, 0));
        csvContentPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(190, 190, 190), 1),
                BorderFactory.createEmptyBorder(4, 6, 4, 6)));

        titleLabel = new JLabel();
        pathLabel = new JLabel();
        summaryLabel = new JLabel();
        dirtyLabel = new JLabel();
        dirtyLabel.setPreferredSize(new Dimension(44, 22));
        dirtyLabel.setMinimumSize(new Dimension(44, 22));

        saveButton = createCompactButton("保存");
        reloadButton = createCompactButton("再読込");
        closeButton = createCompactButton("閉じる");
        openFolderButton = createCompactButton("Explorer");
        moveUpButton = createCompactButton("↑");
        moveDownButton = createCompactButton("↓");
        collapseButton = createCompactButton("▾");
        collapseButton.setToolTipText("このCSVパネルを折りたたみ/展開します。");
        openFolderButton.setToolTipText("このCSVファイルが格納されているフォルダーをエクスプローラーで開きます。");
        moveUpButton.setToolTipText("このCSVを同じデータグループ内で上へ移動します。");
        moveDownButton.setToolTipText("このCSVを同じデータグループ内で下へ移動します。");

        saveButton.addActionListener(e -> runLater(new Runnable() {
            @Override
            public void run() {
                saveDocument();
            }
        }));
        reloadButton.addActionListener(e -> runLater(new Runnable() {
            @Override
            public void run() {
                reloadDocumentWithConfirmation();
            }
        }));
        closeButton.addActionListener(e -> runLater(new Runnable() {
            @Override
            public void run() {
                requestClose();
            }
        }));
        openFolderButton.addActionListener(e -> openContainingFolder());
        moveUpButton.addActionListener(e -> {
            if (moveRequestListener != null) {
                moveRequestListener.moveUpRequested(this);
            }
        });
        moveDownButton.addActionListener(e -> {
            if (moveRequestListener != null) {
                moveRequestListener.moveDownRequested(this);
            }
        });
        collapseButton.addActionListener(e -> toggleCollapsed());

        moveButtonsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        moveButtonsPanel.add(moveUpButton);
        moveButtonsPanel.add(moveDownButton);
        moveButtonsPanel.add(collapseButton);
        moveButtonsPanel.setMaximumSize(new Dimension(120, 28));
        moveButtonsPanel.setAlignmentY(Component.TOP_ALIGNMENT);
        add(moveButtonsPanel, BorderLayout.WEST);

        infoPanel = new JPanel(new BorderLayout(4, 2));
        infoPanel.setPreferredSize(new Dimension(330, 128));
        infoPanel.setMinimumSize(new Dimension(280, 118));

        titleHeaderPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        titleDetailsPanel = new JPanel();
        titleDetailsPanel.setLayout(new BoxLayout(titleDetailsPanel, BoxLayout.Y_AXIS));
        titleDetailsPanel.setOpaque(false);
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        pathLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        summaryLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        titleDetailsPanel.add(titleLabel);
        titleDetailsPanel.add(pathLabel);
        titleDetailsPanel.add(summaryLabel);

        titleHeaderPanel.setVisible(false);
        titlePanel = new JPanel(new BorderLayout(2, 0));
        titlePanel.add(titleHeaderPanel, BorderLayout.NORTH);
        titlePanel.add(titleDetailsPanel, BorderLayout.CENTER);
        infoPanel.add(titlePanel, BorderLayout.NORTH);

        actionsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        actionsPanel.add(dirtyLabel);
        actionsPanel.add(saveButton);
        actionsPanel.add(reloadButton);
        actionsPanel.add(closeButton);
        actionsPanel.add(openFolderButton);
        infoPanel.add(actionsPanel, BorderLayout.SOUTH);
        csvContentPanel.add(infoPanel, BorderLayout.WEST);

        tableModel = new CsvTableModel(document);
        attachUndoListener(tableModel);
        attachDirtyListener(tableModel);
        table = new JTable(tableModel);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.setFillsViewportHeight(false);
        table.setRowHeight(18);
        table.setShowGrid(true);
        table.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, new Color(120, 135, 150)));
        JTableHeader tableHeader = table.getTableHeader();
        tableHeader.setReorderingAllowed(false);
        tableHeader.setBorder(BorderFactory.createMatteBorder(1, 1, 0, 0, new Color(120, 135, 150)));
        configureTableColumns();
        installHeaderAutoResizeHandler();
        installRowPopupMenu();
        installTableSelectionNotification();
        installEditorKeyBindings();

        tableScrollPane = new JScrollPane(table);
        tableScrollPane.setBorder(BorderFactory.createEmptyBorder());
        tableScrollPane.setViewportBorder(null);
        tableScrollPane.setWheelScrollingEnabled(false);
        installTableResizeHandler();
        installTableMouseWheelForwarding();
        tableWidth = calculateAutomaticTableWidth();
        tableHeight = calculateAutomaticTableHeight();
        updateTableScrollPaneSize();
        tableScrollPane.setAlignmentY(Component.TOP_ALIGNMENT);

        tablePanel = new JPanel();
        tablePanel.setLayout(new BoxLayout(tablePanel, BoxLayout.X_AXIS));
        tablePanel.add(tableScrollPane);
        updateTablePanelSize();
        csvContentPanel.add(tablePanel, BorderLayout.CENTER);
        add(csvContentPanel, BorderLayout.CENTER);
        csvContentPanel.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent event) {
                updateTableScrollPaneSize();
            }
        });
        installResponsiveTableResizeHandler();
    }

    private void installTableSelectionNotification() {
        table.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent event) {
                if (event.getValueIsAdjusting() || suppressSelectionNotification || tableSelectionListener == null) {
                    return;
                }
                if (table.getSelectedRowCount() > 0) {
                    tableSelectionListener.tableSelectionChanged(CsvEditorPanel.this);
                }
            }
        });
    }

    private void installRowPopupMenu() {
        final JPopupMenu popupMenu = new JPopupMenu();
        JMenuItem insertRowItem = new JMenuItem("空白行挿入");
        JMenuItem copyRowItem = new JMenuItem("行コピー");
        JMenuItem pasteRowItem = new JMenuItem("コピー行貼付け");
        JMenuItem insertCopiedRowItem = new JMenuItem("コピー行を挿入");
        JMenuItem deleteRowItem = new JMenuItem("行削除");
        insertRowItem.addActionListener(e -> insertBlankRowAbovePopupRow());
        copyRowItem.addActionListener(e -> copyPopupRow());
        pasteRowItem.addActionListener(e -> pasteCopiedRowToPopupRow());
        insertCopiedRowItem.addActionListener(e -> insertCopiedRowAbovePopupRow());
        deleteRowItem.addActionListener(e -> deleteSelectedRows());
        popupMenu.add(insertRowItem);
        popupMenu.addSeparator();
        popupMenu.add(copyRowItem);
        popupMenu.add(pasteRowItem);
        popupMenu.add(insertCopiedRowItem);
        popupMenu.addSeparator();
        popupMenu.add(deleteRowItem);

        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                showRowPopupIfNeeded(event);
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                showRowPopupIfNeeded(event);
            }

            private void showRowPopupIfNeeded(MouseEvent event) {
                if (!event.isPopupTrigger()) {
                    return;
                }
                int row = table.rowAtPoint(event.getPoint());
                if (row < 0) {
                    return;
                }
                if (!table.isRowSelected(row)) {
                    table.setRowSelectionInterval(row, row);
                }
                popupModelRow = table.convertRowIndexToModel(row);
                pasteRowItem.setEnabled(copiedRows != null && !copiedRows.isEmpty());
                insertCopiedRowItem.setEnabled(copiedRows != null && !copiedRows.isEmpty());
                table.requestFocusInWindow();
                popupMenu.show(table, event.getX(), event.getY());
            }
        });
    }

    private void copyPopupRow() {
        if (popupModelRow < 0 || popupModelRow >= tableModel.getRowCount()) {
            return;
        }
        copiedRows = copySelectedModelRows();
    }

    private void pasteCopiedRowToPopupRow() {
        if (copiedRows == null || copiedRows.isEmpty()
                || popupModelRow < 0 || popupModelRow >= tableModel.getRowCount()) {
            return;
        }
        if (table.isEditing()) {
            table.getCellEditor().stopCellEditing();
        }
        int replaced = tableModel.replaceRows(popupModelRow, copyRows(copiedRows));
        if (replaced <= 0) {
            return;
        }
        tableHeight = calculateAutomaticTableHeight();
        updateTableScrollPaneSize();
        updateLabels();
        table.setRowSelectionInterval(popupModelRow, popupModelRow + replaced - 1);
        table.scrollRectToVisible(table.getCellRect(popupModelRow + replaced - 1, 0, true));
        table.requestFocusInWindow();
    }

    private void insertCopiedRowAbovePopupRow() {
        if (copiedRows == null || copiedRows.isEmpty()
                || popupModelRow < 0 || popupModelRow > tableModel.getRowCount()) {
            return;
        }
        insertRowsAt(popupModelRow, copiedRows);
    }

    private void insertBlankRowAbovePopupRow() {
        if (popupModelRow < 0 || popupModelRow > tableModel.getRowCount()) {
            return;
        }
        insertRowAt(popupModelRow, null);
    }

    private void deleteSelectedRows() {
        if (table.isEditing()) {
            table.getCellEditor().stopCellEditing();
        }
        int[] selectedRows = table.getSelectedRows();
        if (selectedRows.length == 0) {
            return;
        }
        int[] modelRows = new int[selectedRows.length];
        for (int i = 0; i < selectedRows.length; i++) {
            modelRows[i] = table.convertRowIndexToModel(selectedRows[i]);
        }
        tableModel.removeRows(modelRows);
        tableHeight = calculateAutomaticTableHeight();
        updateTableScrollPaneSize();
        updateLabels();
    }

    private void insertRowAt(int rowIndex, List<String> values) {
        if (table.isEditing()) {
            table.getCellEditor().stopCellEditing();
        }
        if (values == null) {
            tableModel.insertBlankRow(rowIndex);
        } else {
            tableModel.insertRow(rowIndex, new ArrayList<String>(values));
        }
        tableHeight = calculateAutomaticTableHeight();
        updateTableScrollPaneSize();
        updateLabels();
        table.setRowSelectionInterval(rowIndex, rowIndex);
        table.scrollRectToVisible(table.getCellRect(rowIndex, 0, true));
        table.requestFocusInWindow();
    }

    private void insertRowsAt(int rowIndex, List<List<String>> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        if (table.isEditing()) {
            table.getCellEditor().stopCellEditing();
        }
        tableModel.insertRows(rowIndex, copyRows(rows));
        tableHeight = calculateAutomaticTableHeight();
        updateTableScrollPaneSize();
        updateLabels();
        table.setRowSelectionInterval(rowIndex, rowIndex + rows.size() - 1);
        table.scrollRectToVisible(table.getCellRect(rowIndex + rows.size() - 1, 0, true));
        table.requestFocusInWindow();
    }

    private List<List<String>> copySelectedModelRows() {
        int[] selectedRows = table.getSelectedRows();
        if (selectedRows.length == 0) {
            List<List<String>> rows = new ArrayList<List<String>>();
            rows.add(new ArrayList<String>(tableModel.copyRow(popupModelRow)));
            return rows;
        }
        int[] modelRows = new int[selectedRows.length];
        for (int i = 0; i < selectedRows.length; i++) {
            modelRows[i] = table.convertRowIndexToModel(selectedRows[i]);
        }
        java.util.Arrays.sort(modelRows);
        List<List<String>> rows = new ArrayList<List<String>>();
        for (int modelRow : modelRows) {
            rows.add(new ArrayList<String>(tableModel.copyRow(modelRow)));
        }
        return rows;
    }

    private int calculateTableViewportWidth() {
        int desiredWidth = calculateDesiredTableViewportWidth();
        int availableWidth = calculateAvailableTableViewportWidth();
        if (availableWidth <= 0) {
            return desiredWidth;
        }
        return Math.max(80, Math.min(desiredWidth, availableWidth));
    }

    private int calculateDesiredTableViewportWidth() {
        return clampTableWidth(tableWidth <= 0 ? calculateAutomaticTableWidth() : tableWidth);
    }

    private int calculateAutomaticTableWidth() {
        int width = 0;
        for (int i = 0; i < table.getColumnModel().getColumnCount(); i++) {
            width += table.getColumnModel().getColumn(i).getPreferredWidth();
        }
        return Math.max(80, width + 3);
    }

    private int clampTableWidth(int width) {
        return Math.max(80, Math.min(width, calculateAutomaticTableWidth()));
    }

    private int calculateAvailableTableViewportWidth() {
        if (infoPanel == null) {
            return 0;
        }

        int contentWidth = csvContentPanel == null ? 0 : csvContentPanel.getWidth();
        if (contentWidth > 0) {
            Insets contentInsets = csvContentPanel.getInsets();
            int usedWidth = contentInsets.left + contentInsets.right + infoPanel.getPreferredSize().width + 2;
            return contentWidth - usedWidth;
        }

        if (getWidth() <= 0) {
            return 0;
        }
        Insets insets = getInsets();
        int usedWidth = insets.left + insets.right + infoPanel.getPreferredSize().width + 2;
        if (moveButtonsPanel != null) {
            usedWidth += moveButtonsPanel.getPreferredSize().width + 2;
        }
        return getWidth() - usedWidth;
    }

    private void updateTableScrollPaneSize() {
        if (tableScrollPane == null) {
            return;
        }
        int width = calculateTableViewportWidth();
        int automaticHeight = calculateAutomaticTableHeight();
        int requestedHeight = tableHeightManuallyResized && tableHeight > 0 ? tableHeight : automaticHeight;
        int height = clampTableHeight(requestedHeight);
        if (!tableHeightManuallyResized) {
            tableHeight = height;
        }
        Dimension tableSize = new Dimension(width, height);
        tableScrollPane.setPreferredSize(tableSize);
        tableScrollPane.setMaximumSize(tableSize);
        tableScrollPane.setSize(tableSize);
        tableScrollPane.setMinimumSize(new Dimension(80, calculateMinimumTableHeight()));
        tableScrollPane.revalidate();
        tableScrollPane.repaint();
        updateTablePanelSize();
        if (!collapsed) {
            updateInfoPanelSize(height);
        }
        revalidate();
        repaint();
        Container parent = getParent();
        if (parent != null) {
            parent.revalidate();
            parent.repaint();
        }
    }

    private void updateTablePanelSize() {
        if (tablePanel == null || tableScrollPane == null) {
            return;
        }
        int tablePaneWidth = collapsed ? 0 : tableScrollPane.getPreferredSize().width;
        int panelWidth = tablePaneWidth;
        int panelMaxWidth = collapsed ? panelWidth : calculateDesiredTableViewportWidth();
        int panelHeight = collapsed ? 0 : tableScrollPane.getPreferredSize().height;
        Dimension panelSize = new Dimension(panelWidth, panelHeight);
        Dimension maxPanelSize = new Dimension(panelMaxWidth, panelHeight);
        tablePanel.setPreferredSize(panelSize);
        tablePanel.setMaximumSize(maxPanelSize);
        tablePanel.setSize(panelSize);
    }

    private void toggleCollapsed() {
        collapsed = !collapsed;
        updateCollapsedState();
    }

    private void updateCollapsedState() {
        if (collapseButton != null) {
            collapseButton.setText(collapsed ? "▸" : "▾");
        }
        if (pathLabel != null) {
            pathLabel.setVisible(!collapsed);
        }
        if (summaryLabel != null) {
            summaryLabel.setVisible(!collapsed);
        }
        updateCollapsedTitleLayout();
        if (actionsPanel != null) {
            actionsPanel.setVisible(!collapsed);
        }
        if (tablePanel != null) {
            tablePanel.setVisible(true);
        }
        if (tableScrollPane != null) {
            tableScrollPane.setVisible(!collapsed);
        }
        if (infoPanel != null) {
            updateInfoPanelSize(tableScrollPane == null ? 128 : tableScrollPane.getPreferredSize().height);
        }
        updateTablePanelSize();
        setMaximumSize(new Dimension(Integer.MAX_VALUE, getPreferredSize().height));
        updateLabels();
        revalidate();
        repaint();
        Container parent = getParent();
        if (parent != null) {
            parent.revalidate();
            parent.repaint();
        }
    }

    private void updateInfoPanelSize(int referenceHeight) {
        if (infoPanel == null) {
            return;
        }
        int width = 330;
        int minWidth = 280;
        int height = collapsed ? 30 : Math.max(calculateMinimumInfoPanelHeight(), referenceHeight);
        Dimension size = new Dimension(width, height);
        Dimension minSize = new Dimension(minWidth, collapsed ? 30 : calculateMinimumInfoPanelHeight());
        infoPanel.setPreferredSize(size);
        infoPanel.setMinimumSize(minSize);
    }

    private int calculateMinimumInfoPanelHeight() {
        int titleHeight = titlePanel == null ? 58 : titlePanel.getPreferredSize().height;
        int actionHeight = actionsPanel == null ? 24 : actionsPanel.getPreferredSize().height;
        return titleHeight + actionHeight + 8;
    }

    private void updateCollapsedTitleLayout() {
        if (titleLabel == null || titleHeaderPanel == null || titleDetailsPanel == null) {
            return;
        }
        Container currentParent = titleLabel.getParent();
        if (collapsed) {
            if (currentParent != titleHeaderPanel) {
                if (currentParent != null) {
                    currentParent.remove(titleLabel);
                }
                titleHeaderPanel.add(titleLabel);
            }
            titleHeaderPanel.setVisible(true);
            titleDetailsPanel.setVisible(false);
        } else {
            if (currentParent != titleDetailsPanel) {
                if (currentParent != null) {
                    currentParent.remove(titleLabel);
                }
                titleDetailsPanel.add(titleLabel, 0);
            }
            titleHeaderPanel.setVisible(false);
            titleDetailsPanel.setVisible(true);
        }
    }

    private void openContainingFolder() {
        Path folder = document.getFilePath().toAbsolutePath().normalize().getParent();
        if (folder == null) {
            JOptionPane.showMessageDialog(this,
                    "格納フォルダーを特定できません。",
                    "フォルダーを開けません",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            new ProcessBuilder("explorer.exe", folder.toString()).start();
        } catch (IOException ex) {
            showError("格納フォルダーをエクスプローラーで開けません。", ex);
        }
    }

    private int calculateAutomaticTableHeight() {
        int headerHeight = table.getTableHeader() == null ? 20 : table.getTableHeader().getPreferredSize().height;
        int rowHeight = table.getRowHeight();
        int dataHeight = rowHeight * Math.max(1, table.getRowCount());
        int horizontalScrollBarHeight = table.getColumnModel().getTotalColumnWidth() > calculateTableContentViewportWidth()
                ? tableScrollBarWidth(false)
                : 0;
        int scrollPaneDecorationHeight = calculateScrollPaneDecorationHeight();
        return Math.max(headerHeight + rowHeight,
                headerHeight + dataHeight + horizontalScrollBarHeight + scrollPaneDecorationHeight);
    }

    private int calculateScrollPaneDecorationHeight() {
        if (tableScrollPane == null) {
            return 0;
        }
        int height = 0;
        Insets insets = tableScrollPane.getInsets();
        if (insets != null) {
            height += insets.top + insets.bottom;
        }
        if (tableScrollPane.getViewportBorder() != null) {
            Insets viewportInsets = tableScrollPane.getViewportBorder().getBorderInsets(tableScrollPane);
            if (viewportInsets != null) {
                height += viewportInsets.top + viewportInsets.bottom;
            }
        }
        return height;
    }

    private int calculateTableContentViewportWidth() {
        return Math.max(0, calculateTableViewportWidth());
    }

    private int clampTableHeight(int height) {
        return Math.max(calculateMinimumTableHeight(), Math.min(height, calculateAutomaticTableHeight()));
    }

    private int calculateMinimumTableHeight() {
        return Math.min(94, calculateAutomaticTableHeight());
    }

    private int tableScrollBarWidth(boolean vertical) {
        if (tableScrollPane == null) {
            return 17;
        }
        if (vertical) {
            return tableScrollPane.getVerticalScrollBar() == null
                    ? 17
                    : tableScrollPane.getVerticalScrollBar().getPreferredSize().width;
        }
        return tableScrollPane.getHorizontalScrollBar() == null
                ? 17
                : tableScrollPane.getHorizontalScrollBar().getPreferredSize().height;
    }

    private void installTableResizeHandler() {
        MouseAdapter resizeHandler = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent event) {
                tableScrollPane.setCursor(getResizeCursor(toScrollPanePoint(event)));
            }

            @Override
            public void mouseExited(MouseEvent event) {
                tableScrollPane.setCursor(Cursor.getDefaultCursor());
            }

            @Override
            public void mousePressed(MouseEvent event) {
                Point point = toScrollPanePoint(event);
                if (!isNearResizeEdge(point)) {
                    return;
                }
                resizingWidth = isNearRightResizeEdge(point);
                resizingHeight = isNearBottomResizeEdge(point);
                resizeStartX = event.getXOnScreen();
                resizeStartY = event.getYOnScreen();
                resizeStartWidth = tableScrollPane.getWidth() > 0 ? tableScrollPane.getWidth() : tableWidth;
                resizeStartHeight = tableScrollPane.getHeight() > 0 ? tableScrollPane.getHeight() : tableHeight;
            }

            @Override
            public void mouseDragged(MouseEvent event) {
                if (resizeStartHeight <= 0 && resizeStartWidth <= 0) {
                    return;
                }
                int deltaX = event.getXOnScreen() - resizeStartX;
                int delta = event.getYOnScreen() - resizeStartY;
                if (resizeStartWidth > 0 && resizingWidth) {
                    tableWidth = clampTableWidth(resizeStartWidth + deltaX);
                }
                if (resizeStartHeight > 0 && resizingHeight) {
                    tableHeight = clampTableHeight(resizeStartHeight + delta);
                    tableHeightManuallyResized = true;
                }
                updateTableScrollPaneSize();
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                resizeStartWidth = 0;
                resizeStartHeight = 0;
                resizingWidth = false;
                resizingHeight = false;
            }
        };
        installResizeHandler(tableScrollPane, resizeHandler);
        installResizeHandler(table, resizeHandler);
        JViewport viewport = tableScrollPane.getViewport();
        if (viewport != null) {
            installResizeHandler(viewport, resizeHandler);
        }
    }

    private void installResponsiveTableResizeHandler() {
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent event) {
                updateTableScrollPaneSize();
            }
        });
    }

    private void installTableMouseWheelForwarding() {
        MouseWheelListener wheelListener = new MouseWheelListener() {
            @Override
            public void mouseWheelMoved(MouseWheelEvent event) {
                if (scrollInnerTableIfPossible(event)) {
                    event.consume();
                    return;
                }
                forwardMouseWheelToStackScrollPane(event);
            }
        };
        tableScrollPane.addMouseWheelListener(wheelListener);
        table.addMouseWheelListener(wheelListener);
        JViewport viewport = tableScrollPane.getViewport();
        if (viewport != null) {
            viewport.addMouseWheelListener(wheelListener);
        }
    }

    private boolean scrollInnerTableIfPossible(MouseWheelEvent event) {
        JScrollBar verticalBar = tableScrollPane.getVerticalScrollBar();
        if (verticalBar == null || !verticalBar.isVisible()) {
            return false;
        }
        int direction = event.getWheelRotation() < 0 ? -1 : 1;
        int increment = event.getScrollType() == MouseWheelEvent.WHEEL_BLOCK_SCROLL
                ? verticalBar.getBlockIncrement(direction)
                : verticalBar.getUnitIncrement(direction) * Math.max(1, Math.abs(event.getUnitsToScroll()));
        int current = verticalBar.getValue();
        int maximum = verticalBar.getMaximum() - verticalBar.getVisibleAmount();
        int target = Math.max(verticalBar.getMinimum(), Math.min(maximum, current + direction * Math.abs(increment)));
        if (target == current) {
            return false;
        }
        verticalBar.setValue(target);
        return true;
    }

    private void forwardMouseWheelToStackScrollPane(MouseWheelEvent event) {
        JScrollPane parentScrollPane = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, this);
        if (parentScrollPane == null || parentScrollPane == tableScrollPane) {
            return;
        }
        Point point = SwingUtilities.convertPoint((Component) event.getSource(), event.getPoint(), parentScrollPane);
        MouseWheelEvent forwarded = new MouseWheelEvent(parentScrollPane,
                event.getID(),
                event.getWhen(),
                event.getModifiersEx(),
                point.x,
                point.y,
                event.getXOnScreen(),
                event.getYOnScreen(),
                event.getClickCount(),
                event.isPopupTrigger(),
                event.getScrollType(),
                event.getScrollAmount(),
                event.getWheelRotation(),
                event.getPreciseWheelRotation());
        parentScrollPane.dispatchEvent(forwarded);
        event.consume();
    }

    private void installResizeHandler(Component component, MouseAdapter resizeHandler) {
        component.addMouseListener(resizeHandler);
        component.addMouseMotionListener(resizeHandler);
    }

    private Point toScrollPanePoint(MouseEvent event) {
        return SwingUtilities.convertPoint((Component) event.getSource(), event.getPoint(), tableScrollPane);
    }

    private boolean isNearResizeEdge(Point point) {
        return isNearBottomResizeEdge(point) || isNearRightResizeEdge(point);
    }

    private boolean isNearBottomResizeEdge(Point point) {
        if (point == null || tableScrollPane == null || tableScrollPane.getViewport() == null) {
            return false;
        }
        Rectangle viewportBounds = tableScrollPane.getViewport().getBounds();
        int viewportBottom = viewportBounds.y + viewportBounds.height;
        return point.x >= viewportBounds.x
                && point.x <= viewportBounds.x + viewportBounds.width
                && point.y >= viewportBottom - 6
                && point.y <= viewportBottom + 6;
    }

    private boolean isNearRightResizeEdge(Point point) {
        if (point == null || tableScrollPane == null || tableScrollPane.getViewport() == null) {
            return false;
        }
        Rectangle viewportBounds = tableScrollPane.getViewport().getBounds();
        int viewportRight = viewportBounds.x + viewportBounds.width;
        return point.y >= viewportBounds.y
                && point.y <= viewportBounds.y + viewportBounds.height
                && point.x >= viewportRight - 6
                && point.x <= viewportRight + 6;
    }

    private Cursor getResizeCursor(Point point) {
        boolean bottom = isNearBottomResizeEdge(point);
        boolean right = isNearRightResizeEdge(point);
        if (bottom && right) {
            return Cursor.getPredefinedCursor(Cursor.SE_RESIZE_CURSOR);
        }
        if (right) {
            return Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR);
        }
        if (bottom) {
            return Cursor.getPredefinedCursor(Cursor.S_RESIZE_CURSOR);
        }
        return Cursor.getDefaultCursor();
    }

    private void configureCompactButton(JButton button) {
        button.setMargin(new Insets(0, 4, 0, 4));
        button.setFocusable(false);
        button.setOpaque(false);
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        int width = 44;
        if ("再読込".equals(button.getText())) {
            width = 56;
        } else if ("閉じる".equals(button.getText())) {
            width = 54;
        } else if ("Explorer".equals(button.getText())) {
            width = 72;
        } else if ("↑".equals(button.getText()) || "↓".equals(button.getText())
                || "▾".equals(button.getText()) || "▸".equals(button.getText())) {
            width = 28;
        }
        button.setPreferredSize(new Dimension(width, 22));
        button.setMinimumSize(new Dimension(width, 22));
    }

    private JButton createCompactButton(String text) {
        JButton button = new RoundedButton(text);
        configureCompactButton(button);
        return button;
    }

    private void attachDirtyListener(CsvTableModel model) {
        model.addTableModelListener(new TableModelListener() {
            @Override
            public void tableChanged(TableModelEvent e) {
                updateLabels();
            }
        });
    }

    private void attachUndoListener(CsvTableModel model) {
        model.setChangeListener(new CsvTableModel.ChangeListener() {
            @Override
            public void beforeDocumentChange() {
                rememberUndoSnapshot();
            }
        });
    }

    private void installEditorKeyBindings() {
        table.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(javax.swing.KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK), "undoCsvEdit");
        table.getActionMap().put("undoCsvEdit", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent event) {
                undoEdit();
            }
        });
        table.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(javax.swing.KeyStroke.getKeyStroke(KeyEvent.VK_Y, InputEvent.CTRL_DOWN_MASK), "redoCsvEdit");
        table.getActionMap().put("redoCsvEdit", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent event) {
                redoEdit();
            }
        });
        getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(javax.swing.KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK), "saveFocusedCsv");
        getActionMap().put("saveFocusedCsv", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent event) {
                saveDocument();
            }
        });
        table.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(javax.swing.KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK), "saveFocusedCsv");
        table.getActionMap().put("saveFocusedCsv", getActionMap().get("saveFocusedCsv"));
    }

    private void rememberUndoSnapshot() {
        if (restoringHistory) {
            return;
        }
        undoHistory.addLast(copyRows(document.copyRows()));
        while (undoHistory.size() > MAX_UNDO_HISTORY) {
            undoHistory.removeFirst();
        }
        redoHistory.clear();
    }

    private void undoEdit() {
        if (undoHistory.isEmpty()) {
            return;
        }
        if (table.isEditing()) {
            table.getCellEditor().stopCellEditing();
        }
        redoHistory.addLast(copyRows(document.copyRows()));
        restoreHistorySnapshot(undoHistory.removeLast());
    }

    private void redoEdit() {
        if (redoHistory.isEmpty()) {
            return;
        }
        if (table.isEditing()) {
            table.getCellEditor().stopCellEditing();
        }
        undoHistory.addLast(copyRows(document.copyRows()));
        while (undoHistory.size() > MAX_UNDO_HISTORY) {
            undoHistory.removeFirst();
        }
        restoreHistorySnapshot(redoHistory.removeLast());
    }

    private void restoreHistorySnapshot(List<List<String>> rows) {
        restoringHistory = true;
        try {
            tableModel.restoreRows(copyRows(rows));
        } finally {
            restoringHistory = false;
        }
        tableHeight = calculateAutomaticTableHeight();
        updateTableScrollPaneSize();
        updateLabels();
    }

    private List<List<String>> copyRows(List<List<String>> rows) {
        List<List<String>> copy = new ArrayList<List<String>>();
        if (rows == null) {
            return copy;
        }
        for (List<String> row : rows) {
            copy.add(row == null ? new ArrayList<String>() : new ArrayList<String>(row));
        }
        return copy;
    }

    private void configureTableColumns() {
        for (int i = 0; i < table.getColumnModel().getColumnCount(); i++) {
            int width = calculatePreferredColumnWidth(i);
            table.getColumnModel().getColumn(i).setPreferredWidth(width);
            table.getColumnModel().getColumn(i).setWidth(width);
        }
    }

    private void installHeaderAutoResizeHandler() {
        final JTableHeader header = table.getTableHeader();
        header.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() != 2) {
                    return;
                }
                int column = findHeaderBoundaryColumn(event.getPoint());
                if (column < 0) {
                    return;
                }
                resizeColumnToHeader(column);
            }
        });
    }

    private int findHeaderBoundaryColumn(Point point) {
        int margin = 5;
        for (int i = 0; i < table.getColumnModel().getColumnCount(); i++) {
            Rectangle rect = table.getTableHeader().getHeaderRect(i);
            if (Math.abs(point.x - rect.x) <= margin && i > 0) {
                return i - 1;
            }
            if (Math.abs(point.x - (rect.x + rect.width)) <= margin) {
                return i;
            }
        }
        return -1;
    }

    private void resizeColumnToHeader(int columnIndex) {
        if (columnIndex < 0 || columnIndex >= table.getColumnModel().getColumnCount()) {
            return;
        }
        int width = calculatePreferredColumnWidth(columnIndex);
        TableColumn column = table.getColumnModel().getColumn(columnIndex);
        column.setPreferredWidth(width);
        column.setWidth(width);
        tableWidth = calculateAutomaticTableWidth();
        updateTableScrollPaneSize();
    }

    private int calculatePreferredColumnWidth(int columnIndex) {
        String headerValue = table.getColumnName(columnIndex);
        FontMetrics headerMetrics = table.getTableHeader().getFontMetrics(table.getTableHeader().getFont());
        int preferredWidth = measureTextWidth(headerMetrics, headerValue) + 24;

        FontMetrics cellMetrics = table.getFontMetrics(table.getFont());
        for (int row = 0; row < table.getRowCount(); row++) {
            Object value = table.getValueAt(row, columnIndex);
            preferredWidth = Math.max(preferredWidth, measureTextWidth(cellMetrics, value) + 18);
        }
        return Math.max(110, preferredWidth);
    }

    private int measureTextWidth(FontMetrics metrics, Object value) {
        if (value == null) {
            return 0;
        }
        String text = String.valueOf(value);
        int maxWidth = 0;
        String[] lines = text.split("\\R", -1);
        for (String line : lines) {
            maxWidth = Math.max(maxWidth, metrics.stringWidth(line));
        }
        return maxWidth;
    }

    private void updateLabels() {
        updateDirtyStateFromContent();
        String fileName = document.getFilePath().getFileName() == null
                ? String.valueOf(document.getFilePath())
                : document.getFilePath().getFileName().toString();
        titleLabel.setText(collapsed ? fileName : (document.isDirty() ? "* " : "") + fileName);
        pathLabel.setText(String.valueOf(document.getRelativePath()));
        summaryLabel.setText("列数: " + document.getColumnCount() + " / 行数: " + document.getRowCount());
        dirtyLabel.setText(document.isDirty() ? "未保存" : "保存済");
        saveButton.setEnabled(document.isDirty());
        updatePanelBackground();
    }

    private void updateDirtyStateFromContent() {
        if (savedRowsSnapshot == null) {
            return;
        }
        document.setDirty(!copyRows(document.copyRows()).equals(savedRowsSnapshot));
    }

    private void updatePanelBackground() {
        Color background = document.isDirty() ? DIRTY_BACKGROUND : getDefaultPanelBackground();
        setBackground(background);
        if (csvContentPanel != null) {
            csvContentPanel.setBackground(background);
        }
        if (infoPanel != null) {
            infoPanel.setBackground(background);
        }
        if (titlePanel != null) {
            titlePanel.setBackground(background);
        }
        if (titleHeaderPanel != null) {
            titleHeaderPanel.setBackground(background);
        }
        if (titleDetailsPanel != null) {
            titleDetailsPanel.setBackground(background);
        }
        if (actionsPanel != null) {
            actionsPanel.setBackground(background);
        }
        if (tablePanel != null) {
            tablePanel.setBackground(background);
        }
        if (moveButtonsPanel != null) {
            moveButtonsPanel.setBackground(background);
        }
        repaint();
    }

    private static Color getDefaultPanelBackground() {
        Color background = UIManager.getColor("Panel.background");
        return background == null ? new Color(238, 238, 238) : background;
    }

    private void setBusy(boolean busy) {
        this.busy = busy;
        setCursor(Cursor.getPredefinedCursor(busy ? Cursor.WAIT_CURSOR : Cursor.DEFAULT_CURSOR));
        saveButton.setEnabled(!busy && document.isDirty());
        reloadButton.setEnabled(!busy);
        closeButton.setEnabled(!busy);
        openFolderButton.setEnabled(!busy);
        updateMoveButtons();
    }

    private void updateMoveButtons() {
        moveUpButton.setEnabled(!busy && canMoveUp);
        moveDownButton.setEnabled(!busy && canMoveDown);
    }

    private void showError(String message, Exception ex) {
        JOptionPane.showMessageDialog(this,
                message + "\n" + ex.getMessage(),
                "エラー",
                JOptionPane.ERROR_MESSAGE);
    }

    private void runLater(final Runnable work) {
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                return null;
            }

            @Override
            protected void done() {
                work.run();
            }
        }.execute();
    }

    private static final class RoundedButton extends JButton {
        private static final int ARC = 10;

        private RoundedButton(String text) {
            super(text);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g2 = (Graphics2D) graphics.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color base = isEnabled() ? new Color(235, 242, 250) : new Color(232, 232, 232);
                if (getModel().isPressed()) {
                    base = new Color(198, 216, 235);
                } else if (getModel().isRollover()) {
                    base = new Color(220, 234, 248);
                }
                g2.setColor(base);
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, ARC, ARC);
                g2.setColor(isEnabled() ? new Color(116, 145, 174) : new Color(180, 180, 180));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, ARC, ARC);
            } finally {
                g2.dispose();
            }
            super.paintComponent(graphics);
        }
    }
}
