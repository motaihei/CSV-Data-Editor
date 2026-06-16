package com.example.csveditor.ui;

import com.example.csveditor.domain.CsvDocument;
import com.example.csveditor.service.CsvDocumentService;
import com.example.csveditor.service.DataGroupKeyResolver;
import com.example.csveditor.service.DataGroupingConfig;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.Scrollable;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.JViewport;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Scrollable vertical stack of independent CSV editor panels.
 */
public class CsvStackPanel extends JScrollPane {
    public interface SessionChangeListener {
        void sessionChanged();
    }

    public interface ActiveGroupChangeListener {
        void activeGroupChanged(String groupKey);
    }

    private static final Color DISPLAYED_GROUP_BORDER = new Color(70, 142, 104);
    private static final Color DISPLAYED_GROUP_ACCENT = new Color(34, 120, 78);
    private final JPanel contentPanel;
    private final Map<Path, CsvEditorPanel> panelsByPath;
    private final Map<String, JPanel> groupSectionsByKey;
    private final Set<String> collapsedGroupKeys;
    private final CsvDocumentService documentService;
    private DataGroupKeyResolver groupKeyResolver;
    private SessionChangeListener sessionChangeListener;
    private ActiveGroupChangeListener activeGroupChangeListener;
    private boolean dataGroupingEnabled = true;
    private boolean rebuildingContentPanel;
    private boolean suppressVisibleGroupTracking;
    private String rowClipboardDelimiter = "\t";
    private String activeGroupKey;
    private String csvFileNameFilter = "";
    private int autoCollapseRowThreshold;
    private int dataGroupPathSegmentLevel = 2;

    public CsvStackPanel(CsvDocumentService documentService) {
        this(documentService, null);
    }

    public CsvStackPanel(CsvDocumentService documentService, DataGroupKeyResolver groupKeyResolver) {
        this.documentService = documentService;
        this.groupKeyResolver = groupKeyResolver == null
                ? createGroupKeyResolver(dataGroupPathSegmentLevel)
                : groupKeyResolver;
        this.contentPanel = new ScrollableContentPanel();
        this.contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        this.contentPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        this.panelsByPath = new LinkedHashMap<Path, CsvEditorPanel>();
        this.groupSectionsByKey = new LinkedHashMap<String, JPanel>();
        this.collapsedGroupKeys = new HashSet<String>();
        setViewportView(contentPanel);
        getVerticalScrollBar().setUnitIncrement(16);
        getHorizontalScrollBar().setUnitIncrement(16);
        installResponsiveTableRefreshHandler();
        installVisibleGroupTracking();
    }

    public void setSessionChangeListener(SessionChangeListener sessionChangeListener) {
        this.sessionChangeListener = sessionChangeListener;
    }

    public void setActiveGroupChangeListener(ActiveGroupChangeListener activeGroupChangeListener) {
        this.activeGroupChangeListener = activeGroupChangeListener;
    }

    public void addOrFocusDocument(final CsvDocument document) {
        addOrFocusDocuments(Collections.singletonList(document));
    }

    public void addOrFocusDocuments(final List<CsvDocument> documents) {
        if (documents == null || documents.isEmpty()) {
            return;
        }
        runOnEdt(new Runnable() {
            @Override
            public void run() {
                CsvEditorPanel targetPanel = null;
                boolean changed = false;
                for (CsvDocument document : documents) {
                    if (document == null) {
                        continue;
                    }
                    Path key = normalize(document.getFilePath());
                    CsvEditorPanel existing = panelsByPath.get(key);
                    if (existing != null) {
                        targetPanel = existing;
                        continue;
                    }

                    CsvEditorPanel panel = createEditorPanel(document);
                    panelsByPath.put(key, panel);
                    targetPanel = panel;
                    changed = true;
                }
                if (changed) {
                    rebuildContentPanel();
                    notifySessionChanged();
                }
                if (targetPanel != null) {
                    setActiveGroupKey(getGroupKey(targetPanel));
                    scrollToPanel(targetPanel);
                    targetPanel.requestFocusInWindow();
                }
            }
        });
    }

    private CsvEditorPanel createEditorPanel(final CsvDocument document) {
        final CsvEditorPanel panel = new CsvEditorPanel(document, documentService);
        if (shouldAutoCollapse(document)) {
            panel.setCollapsed(true);
        }
        panel.setRowClipboardDelimiter(rowClipboardDelimiter);
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, panel.getPreferredSize().height));
        panel.setCloseRequestListener(new CsvEditorPanel.CloseRequestListener() {
            @Override
            public void closeRequested(CsvEditorPanel requestedPanel) {
                closePanel(requestedPanel);
            }
        });
        panel.setMoveRequestListener(new CsvEditorPanel.MoveRequestListener() {
            @Override
            public void moveUpRequested(CsvEditorPanel requestedPanel) {
                movePanel(requestedPanel, -1);
            }

            @Override
            public void moveDownRequested(CsvEditorPanel requestedPanel) {
                movePanel(requestedPanel, 1);
            }
        });
        panel.setTableSelectionListener(new CsvEditorPanel.TableSelectionListener() {
            @Override
            public void tableSelectionChanged(CsvEditorPanel selectedPanel) {
                setActiveGroupKey(getGroupKey(selectedPanel));
                clearOtherTableSelections(selectedPanel);
            }
        });
        return panel;
    }

    public boolean hasOpenDocument(Path filePath) {
        return panelsByPath.containsKey(normalize(filePath));
    }

    public void focusDocument(Path filePath) {
        CsvEditorPanel panel = panelsByPath.get(normalize(filePath));
        if (panel != null) {
            setActiveGroupKey(getGroupKey(panel));
            scrollToPanel(panel);
            panel.requestFocusInWindow();
        }
    }

    public List<CsvEditorPanel> getOpenPanels() {
        return new ArrayList<CsvEditorPanel>(panelsByPath.values());
    }

    public void setCsvFileNameFilter(String filterText) {
        String normalizedFilter = normalizeFilterText(filterText);
        if (normalizedFilter.equals(csvFileNameFilter)) {
            return;
        }
        csvFileNameFilter = normalizedFilter;
        rebuildContentPanel();
    }

    public List<String> getOpenRelativePaths() {
        List<String> relativePaths = new ArrayList<String>();
        for (CsvEditorPanel panel : panelsByPath.values()) {
            relativePaths.add(panel.getDocument().getRelativePath().toString());
        }
        return relativePaths;
    }

    public Set<String> getCollapsedGroupKeys() {
        return new HashSet<String>(collapsedGroupKeys);
    }

    public boolean isDataGroupingEnabled() {
        return dataGroupingEnabled;
    }

    public void setDataGroupingEnabled(boolean dataGroupingEnabled) {
        if (this.dataGroupingEnabled == dataGroupingEnabled) {
            return;
        }
        this.dataGroupingEnabled = dataGroupingEnabled;
        if (!dataGroupingEnabled) {
            setActiveGroupKey(null);
        }
        rebuildContentPanel();
        notifySessionChanged();
    }

    public void setRowClipboardDelimiter(String rowClipboardDelimiter) {
        if (rowClipboardDelimiter == null || rowClipboardDelimiter.length() == 0) {
            this.rowClipboardDelimiter = "\t";
        } else {
            this.rowClipboardDelimiter = rowClipboardDelimiter;
        }
        for (CsvEditorPanel panel : panelsByPath.values()) {
            panel.setRowClipboardDelimiter(this.rowClipboardDelimiter);
        }
    }

    public void setAutoCollapseRowThreshold(int autoCollapseRowThreshold) {
        this.autoCollapseRowThreshold = Math.max(0, autoCollapseRowThreshold);
    }

    public void setDataGroupPathSegmentLevel(int dataGroupPathSegmentLevel) {
        int normalizedLevel = Math.max(1, dataGroupPathSegmentLevel);
        if (this.dataGroupPathSegmentLevel == normalizedLevel) {
            return;
        }
        this.dataGroupPathSegmentLevel = normalizedLevel;
        this.groupKeyResolver = createGroupKeyResolver(normalizedLevel);
        collapsedGroupKeys.clear();
        setActiveGroupKey(findFirstOpenGroupKey());
        rebuildContentPanel();
        notifySessionChanged();
    }

    public List<String> getOpenGroupKeys() {
        if (!dataGroupingEnabled) {
            return new ArrayList<String>();
        }
        List<String> groupKeys = new ArrayList<String>();
        for (CsvEditorPanel panel : panelsByPath.values()) {
            String groupKey = getGroupKey(panel);
            if (!groupKeys.contains(groupKey)) {
                groupKeys.add(groupKey);
            }
        }
        return groupKeys;
    }

    public String getActiveGroupKey() {
        return activeGroupKey;
    }

    public void focusGroup(final String groupKey) {
        if (!dataGroupingEnabled || groupKey == null) {
            return;
        }
        runOnEdt(new Runnable() {
            @Override
            public void run() {
                suppressVisibleGroupTracking = true;
                setActiveGroupKey(groupKey);
                JPanel sectionPanel = groupSectionsByKey.get(groupKey);
                if (sectionPanel != null) {
                    contentPanel.revalidate();
                    contentPanel.doLayout();
                    JViewport viewport = getViewport();
                    int maxY = Math.max(0,
                            contentPanel.getHeight() - viewport.getExtentSize().height);
                    int targetY = Math.max(0, Math.min(sectionPanel.getY(), maxY));
                    viewport.setViewPosition(new Point(0, targetY));
                    viewport.repaint();
                }
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        setActiveGroupKey(groupKey);
                        suppressVisibleGroupTracking = false;
                    }
                });
            }
        });
    }

    public void setCollapsedGroupKeys(Set<String> groupKeys) {
        collapsedGroupKeys.clear();
        if (groupKeys != null) {
            collapsedGroupKeys.addAll(groupKeys);
        }
        rebuildContentPanel();
    }

    public List<CsvEditorPanel> getModifiedPanels() {
        List<CsvEditorPanel> modifiedPanels = new ArrayList<CsvEditorPanel>();
        for (CsvEditorPanel panel : panelsByPath.values()) {
            if (panel.isModified()) {
                modifiedPanels.add(panel);
            }
        }
        return modifiedPanels;
    }

    public void saveAll() throws IOException {
        for (CsvEditorPanel panel : getOpenPanels()) {
            if (!panel.saveDocument()) {
                throw new IOException("Failed to save: " + panel.getDocument().getFilePath());
            }
        }
    }

    public void applyColumnWidthToOpenPanels(int width) {
        for (CsvEditorPanel panel : getOpenPanels()) {
            panel.applyColumnWidthToAllColumns(width);
        }
    }

    public void autoFitColumnWidthsForOpenPanels() {
        for (CsvEditorPanel panel : getOpenPanels()) {
            panel.autoFitAllColumnWidths();
        }
    }

    public void setAllOpenPanelsCollapsed(boolean collapsed) {
        for (CsvEditorPanel panel : getOpenPanels()) {
            panel.setCollapsed(collapsed);
        }
        contentPanel.revalidate();
        contentPanel.repaint();
    }

    public boolean requestCloseAll() {
        if (!confirmCloseAllForApplicationExit()) {
            return false;
        }
        closeAllConfirmed();
        return true;
    }

    public boolean confirmCloseAllForApplicationExit() {
        for (CsvEditorPanel panel : getOpenPanels()) {
            if (!panel.confirmForApplicationExit(this)) {
                return false;
            }
        }
        return true;
    }

    public void closeAllConfirmed() {
        if (panelsByPath.isEmpty()) {
            return;
        }
        panelsByPath.clear();
        if (activeGroupKey != null) {
            activeGroupKey = null;
            notifyActiveGroupChanged();
        }
        rebuildContentPanel();
        notifySessionChanged();
    }

    public void requestCloseGroup(String groupKey) {
        if (dataGroupingEnabled) {
            closeGroup(groupKey);
        }
    }

    private void closePanel(CsvEditorPanel panel) {
        String removedGroupKey = getGroupKey(panel);
        panelsByPath.remove(normalize(panel.getDocument().getFilePath()));
        if (removedGroupKey.equals(activeGroupKey) && !hasGroupKey(removedGroupKey)) {
            setActiveGroupKey(findFirstOpenGroupKey());
        }
        rebuildContentPanel();
        notifySessionChanged();
    }

    private void clearOtherTableSelections(CsvEditorPanel selectedPanel) {
        for (CsvEditorPanel panel : panelsByPath.values()) {
            if (panel != selectedPanel) {
                panel.clearTableSelection();
            }
        }
    }

    private void closeGroup(String groupKey) {
        List<CsvEditorPanel> groupPanels = new ArrayList<CsvEditorPanel>();
        for (CsvEditorPanel panel : panelsByPath.values()) {
            if (groupKey.equals(getGroupKey(panel))) {
                groupPanels.add(panel);
            }
        }
        for (CsvEditorPanel panel : groupPanels) {
            if (!panel.requestClose()) {
                return;
            }
        }
        collapsedGroupKeys.remove(groupKey);
        if (groupKey.equals(activeGroupKey)) {
            setActiveGroupKey(findFirstOpenGroupKey());
        }
        rebuildContentPanel();
        notifySessionChanged();
    }

    private void movePanel(CsvEditorPanel panel, int direction) {
        Path key = normalize(panel.getDocument().getFilePath());
        List<Map.Entry<Path, CsvEditorPanel>> entries =
                new ArrayList<Map.Entry<Path, CsvEditorPanel>>(panelsByPath.entrySet());
        int currentIndex = -1;
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).getKey().equals(key)) {
                currentIndex = i;
                break;
            }
        }
        if (currentIndex < 0) {
            return;
        }

        int targetIndex;
        if (dataGroupingEnabled) {
            String groupKey = getGroupKey(panel);
            List<Integer> groupIndexes = new ArrayList<Integer>();
            int currentGroupIndex = -1;
            for (int i = 0; i < entries.size(); i++) {
                if (groupKey.equals(getGroupKey(entries.get(i).getValue()))) {
                    if (i == currentIndex) {
                        currentGroupIndex = groupIndexes.size();
                    }
                    groupIndexes.add(Integer.valueOf(i));
                }
            }

            int targetGroupIndex = currentGroupIndex + direction;
            if (currentGroupIndex < 0 || targetGroupIndex < 0 || targetGroupIndex >= groupIndexes.size()) {
                return;
            }
            targetIndex = groupIndexes.get(targetGroupIndex).intValue();
        } else {
            targetIndex = currentIndex + direction;
            if (targetIndex < 0 || targetIndex >= entries.size()) {
                return;
            }
        }

        Collections.swap(entries, currentIndex, targetIndex);

        panelsByPath.clear();
        for (Map.Entry<Path, CsvEditorPanel> entry : entries) {
            panelsByPath.put(entry.getKey(), entry.getValue());
        }
        rebuildContentPanel();
        notifySessionChanged();
        setActiveGroupKey(getGroupKey(panel));
        scrollToPanel(panel);
    }

    private void rebuildContentPanel() {
        rebuildingContentPanel = true;
        contentPanel.removeAll();
        groupSectionsByKey.clear();

        try {
            if (!dataGroupingEnabled) {
                setActiveGroupKey(null);
                List<CsvEditorPanel> panels = new ArrayList<CsvEditorPanel>(panelsByPath.values());
                List<CsvEditorPanel> visiblePanels = filterPanelsByFileName(panels);
                for (int i = 0; i < visiblePanels.size(); i++) {
                    CsvEditorPanel panel = visiblePanels.get(i);
                    panel.setMoveAvailability(i > 0, i < visiblePanels.size() - 1);
                    contentPanel.add(panel);
                    contentPanel.add(Box.createVerticalStrut(6));
                }
                revalidate();
                repaint();
                refreshOpenTableSizesLater();
                return;
            }

            Map<String, List<CsvEditorPanel>> groupedPanels = new LinkedHashMap<String, List<CsvEditorPanel>>();
            for (CsvEditorPanel panel : panelsByPath.values()) {
                String groupKey = getGroupKey(panel);
                List<CsvEditorPanel> group = groupedPanels.get(groupKey);
                if (group == null) {
                    group = new ArrayList<CsvEditorPanel>();
                    groupedPanels.put(groupKey, group);
                }
                group.add(panel);
            }
            if (activeGroupKey == null || !groupedPanels.containsKey(activeGroupKey)) {
                activeGroupKey = groupedPanels.isEmpty() ? null : groupedPanels.keySet().iterator().next();
                notifyActiveGroupChanged();
            }

            for (Map.Entry<String, List<CsvEditorPanel>> entry : groupedPanels.entrySet()) {
                String groupKey = entry.getKey();
                boolean active = groupKey.equals(activeGroupKey);
                JPanel groupPanel = createGroupPanel(groupKey, active);
                JPanel bodyPanel = createGroupBodyPanel(active);
                List<CsvEditorPanel> groupPanels = filterPanelsByFileName(entry.getValue());
                if (isCsvFileNameFilterActive() && groupPanels.isEmpty()) {
                    continue;
                }
                for (int i = 0; i < groupPanels.size(); i++) {
                    CsvEditorPanel panel = groupPanels.get(i);
                    panel.setMoveAvailability(i > 0, i < groupPanels.size() - 1);
                    if (isCsvFileNameFilterActive() || !collapsedGroupKeys.contains(groupKey)) {
                        bodyPanel.add(panel);
                        bodyPanel.add(Box.createVerticalStrut(6));
                    }
                }
                if (isCsvFileNameFilterActive() || !collapsedGroupKeys.contains(groupKey)) {
                    groupPanel.add(bodyPanel, BorderLayout.CENTER);
                }
                JPanel groupSectionPanel = createGroupSectionPanel(groupPanel, active);
                groupSectionsByKey.put(groupKey, groupSectionPanel);
                contentPanel.add(groupSectionPanel);
                contentPanel.add(Box.createVerticalStrut(8));
            }
            revalidate();
            repaint();
            refreshOpenTableSizesLater();
        } finally {
            rebuildingContentPanel = false;
        }
    }

    private List<CsvEditorPanel> filterPanelsByFileName(List<CsvEditorPanel> panels) {
        List<CsvEditorPanel> filteredPanels = new ArrayList<CsvEditorPanel>();
        for (CsvEditorPanel panel : panels) {
            if (matchesCsvFileNameFilter(panel)) {
                filteredPanels.add(panel);
            }
        }
        return filteredPanels;
    }

    private boolean matchesCsvFileNameFilter(CsvEditorPanel panel) {
        if (!isCsvFileNameFilterActive()) {
            return true;
        }
        String fileName = panel.getDocument().getFileName();
        return fileName != null
                && fileName.toLowerCase(Locale.ROOT).contains(csvFileNameFilter);
    }

    private boolean isCsvFileNameFilterActive() {
        return csvFileNameFilter.length() > 0;
    }

    private static String normalizeFilterText(String filterText) {
        return filterText == null ? "" : filterText.trim().toLowerCase(Locale.ROOT);
    }

    public void refreshOpenTableSizes() {
        for (CsvEditorPanel panel : panelsByPath.values()) {
            panel.refreshResponsiveTableSize();
        }
        contentPanel.revalidate();
        contentPanel.repaint();
        revalidate();
        repaint();
    }

    public void refreshOpenTableSizesLater() {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                refreshOpenTableSizes();
            }
        });
    }

    private JPanel createGroupSectionPanel(JPanel groupPanel, boolean active) {
        JPanel sectionPanel = new JPanel(new BorderLayout(0, 0)) {
            @Override
            public Dimension getMaximumSize() {
                return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
            }
        };
        sectionPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        sectionPanel.setBackground(getPanelBackground());
        sectionPanel.add(groupPanel, BorderLayout.CENTER);
        return sectionPanel;
    }

    private JPanel createGroupPanel(String groupKey, boolean active) {
        JPanel groupPanel = new JPanel() {
            @Override
            public Dimension getMaximumSize() {
                return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
            }
        };
        groupPanel.setLayout(new BorderLayout(0, 4));
        groupPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        groupPanel.setBackground(getPanelBackground());
        groupPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, active ? 6 : 1, 1, 1,
                        active ? DISPLAYED_GROUP_ACCENT : getInactiveGroupBorder()),
                BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(active ? DISPLAYED_GROUP_BORDER : getInactiveGroupBorder(), 1),
                        BorderFactory.createEmptyBorder(4, 4, 0, 4))));
        groupPanel.add(createGroupHeaderPanel(groupKey, active), BorderLayout.NORTH);
        return groupPanel;
    }

    private JPanel createGroupBodyPanel(boolean active) {
        JPanel bodyPanel = new JPanel();
        bodyPanel.setLayout(new BoxLayout(bodyPanel, BoxLayout.Y_AXIS));
        bodyPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        bodyPanel.setBackground(getPanelBackground());
        return bodyPanel;
    }

    private JPanel createGroupHeaderPanel(final String groupKey, boolean active) {
        JPanel headerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        headerPanel.setBackground(getPanelBackground());
        JLabel groupNameLabel = createGroupHeaderLabel(groupKey, active);
        final JButton collapseButton = createGroupHeaderButton(
                collapsedGroupKeys.contains(groupKey) ? "▸" : "▾");
        collapseButton.setToolTipText("このデータグループを折りたたみ/展開します。");
        collapseButton.addActionListener(e -> {
            if (collapsedGroupKeys.contains(groupKey)) {
                collapsedGroupKeys.remove(groupKey);
            } else {
                collapsedGroupKeys.add(groupKey);
            }
            rebuildContentPanel();
            notifySessionChanged();
        });

        JButton closeGroupButton = createGroupHeaderButton("すべて閉じる");
        closeGroupButton.setToolTipText("このデータグループ内のCSVをすべて閉じます。");
        closeGroupButton.addActionListener(e -> closeGroup(groupKey));

        headerPanel.add(collapseButton);
        headerPanel.add(groupNameLabel);
        headerPanel.add(closeGroupButton);
        return headerPanel;
    }

    private JLabel createGroupHeaderLabel(String groupKey, boolean active) {
        final int maxLabelWidth = 180;
        JLabel label = new JLabel(groupKey == null ? "" : groupKey);
        label.setToolTipText(groupKey);
        label.setHorizontalAlignment(JLabel.LEFT);
        label.setFont(label.getFont().deriveFont(active ? Font.BOLD : Font.PLAIN, 12f));
        label.setForeground(active ? DISPLAYED_GROUP_ACCENT : UIManager.getColor("Label.foreground"));
        Dimension preferredSize = label.getPreferredSize();
        int labelWidth = Math.min(maxLabelWidth, preferredSize.width);
        Dimension labelSize = new Dimension(labelWidth, 24);
        label.setMinimumSize(new Dimension(0, 24));
        label.setPreferredSize(labelSize);
        label.setMaximumSize(labelSize);
        return label;
    }

    private JButton createGroupHeaderButton(String text) {
        JButton button = new JButton(text);
        button.setFocusable(false);
        boolean closeAllButton = "すべて閉じる".equals(text);
        button.setMargin(closeAllButton ? new java.awt.Insets(0, 4, 0, 4) : new java.awt.Insets(0, 0, 1, 0));
        if (!closeAllButton) {
            button.setFont(button.getFont().deriveFont(Font.BOLD, 12f));
        }
        button.setPreferredSize(new Dimension(closeAllButton ? 92 : 28, closeAllButton ? 24 : 20));
        button.setMinimumSize(button.getPreferredSize());
        return button;
    }

    private String getGroupKey(CsvEditorPanel panel) {
        CsvDocument document = panel.getDocument();
        Path relativePath = document.getRelativePath();
        Path relativeGroupPath = getRelativeGroupPath(relativePath);
        String shortGroupKey;
        if (relativeGroupPath == null) {
            shortGroupKey = groupKeyResolver.resolve(relativePath);
        } else {
            shortGroupKey = relativeGroupPath.toString();
        }
        if (!hasDuplicateGroupKey(panel, shortGroupKey)) {
            return shortGroupKey;
        }
        return rootDisplayName(document) + "\\" + shortGroupKey;
    }

    private Path getRelativeGroupPath(Path relativePath) {
        if (relativePath == null || relativePath.getNameCount() <= 1) {
            return null;
        }
        int directoryCount = relativePath.getNameCount() - 1;
        int groupNameCount = Math.min(dataGroupPathSegmentLevel, directoryCount);
        if (groupNameCount <= 0) {
            return null;
        }
        return relativePath.subpath(0, groupNameCount);
    }

    private boolean hasDuplicateGroupKey(CsvEditorPanel targetPanel, String shortGroupKey) {
        Path targetGroupPath = getGroupAbsolutePath(targetPanel.getDocument());
        for (CsvEditorPanel panel : panelsByPath.values()) {
            if (panel == targetPanel) {
                continue;
            }
            String otherShortGroupKey = getShortGroupKey(panel.getDocument());
            if (!shortGroupKey.equals(otherShortGroupKey)) {
                continue;
            }
            Path otherGroupPath = getGroupAbsolutePath(panel.getDocument());
            if (targetGroupPath == null || otherGroupPath == null || !targetGroupPath.equals(otherGroupPath)) {
                return true;
            }
        }
        return false;
    }

    private String getShortGroupKey(CsvDocument document) {
        Path relativePath = document.getRelativePath();
        Path relativeGroupPath = getRelativeGroupPath(relativePath);
        return relativeGroupPath == null ? groupKeyResolver.resolve(relativePath) : relativeGroupPath.toString();
    }

    private Path getGroupAbsolutePath(CsvDocument document) {
        Path rootPath = getRootPath(document);
        if (rootPath == null) {
            return null;
        }
        Path relativeGroupPath = getRelativeGroupPath(document.getRelativePath());
        return relativeGroupPath == null
                ? rootPath.toAbsolutePath().normalize()
                : rootPath.resolve(relativeGroupPath).toAbsolutePath().normalize();
    }

    private static String rootDisplayName(CsvDocument document) {
        Path rootPath = getRootPath(document);
        if (rootPath == null) {
            return "root";
        }
        Path fileName = rootPath.getFileName();
        return fileName == null ? rootPath.toString() : fileName.toString();
    }

    private static Path getRootPath(CsvDocument document) {
        if (document == null || document.getFilePath() == null) {
            return null;
        }
        Path rootPath = document.getFilePath().toAbsolutePath().normalize();
        Path relativePath = document.getRelativePath();
        int levels = relativePath == null ? 1 : relativePath.getNameCount();
        for (int i = 0; i < levels && rootPath != null; i++) {
            rootPath = rootPath.getParent();
        }
        return rootPath;
    }

    private boolean shouldAutoCollapse(CsvDocument document) {
        return autoCollapseRowThreshold > 0
                && document != null
                && document.getRowCount() >= autoCollapseRowThreshold;
    }

    private static DataGroupKeyResolver createGroupKeyResolver(int dataGroupPathSegmentLevel) {
        return new DataGroupKeyResolver(
                DataGroupingConfig.pathSegmentLevelConfig(Math.max(1, dataGroupPathSegmentLevel)));
    }

    private void setActiveGroupKey(String groupKey) {
        if (groupKey != null && !dataGroupingEnabled) {
            groupKey = null;
        }
        if (groupKey == null ? activeGroupKey == null : groupKey.equals(activeGroupKey)) {
            return;
        }
        activeGroupKey = groupKey;
        rebuildActiveGroupHighlight();
        notifyActiveGroupChanged();
    }

    private void rebuildActiveGroupHighlight() {
        if (dataGroupingEnabled && !rebuildingContentPanel) {
            rebuildContentPanel();
        }
    }

    private void notifyActiveGroupChanged() {
        if (activeGroupChangeListener != null) {
            activeGroupChangeListener.activeGroupChanged(activeGroupKey);
        }
    }

    private boolean hasGroupKey(String groupKey) {
        if (groupKey == null) {
            return false;
        }
        for (CsvEditorPanel panel : panelsByPath.values()) {
            if (groupKey.equals(getGroupKey(panel))) {
                return true;
            }
        }
        return false;
    }

    private String findFirstOpenGroupKey() {
        for (CsvEditorPanel panel : panelsByPath.values()) {
            return getGroupKey(panel);
        }
        return null;
    }

    private void updateActiveGroupFromViewport() {
        if (suppressVisibleGroupTracking || !dataGroupingEnabled || groupSectionsByKey.isEmpty()) {
            return;
        }
        JViewport viewport = getViewport();
        int viewportTop = viewport.getViewPosition().y;
        int viewportBottom = viewportTop + viewport.getExtentSize().height;
        String bestGroupKey = null;
        int bestOverlap = -1;
        for (Map.Entry<String, JPanel> entry : groupSectionsByKey.entrySet()) {
            JPanel sectionPanel = entry.getValue();
            int sectionTop = sectionPanel.getY();
            int sectionBottom = sectionTop + sectionPanel.getHeight();
            int overlap = Math.min(viewportBottom, sectionBottom) - Math.max(viewportTop, sectionTop);
            if (overlap > bestOverlap) {
                bestOverlap = overlap;
                bestGroupKey = entry.getKey();
            }
        }
        if (bestGroupKey != null && bestOverlap > 0) {
            setActiveGroupKey(bestGroupKey);
        }
    }

    private static Color getPanelBackground() {
        Color background = UIManager.getColor("Panel.background");
        return background == null ? new Color(238, 238, 238) : background;
    }

    private static Color getInactiveGroupBorder() {
        return Color.BLACK;
    }

    private void notifySessionChanged() {
        if (sessionChangeListener != null) {
            sessionChangeListener.sessionChanged();
        }
    }

    private void scrollToPanel(final CsvEditorPanel panel) {
        runOnEdt(new Runnable() {
            @Override
            public void run() {
                panel.scrollRectToVisible(new Rectangle(0, 0,
                        Math.max(1, panel.getWidth()),
                        Math.max(1, panel.getHeight())));
            }
        });
    }

    private void installResponsiveTableRefreshHandler() {
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent event) {
                refreshOpenTableSizesLater();
            }
        });
        getViewport().addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent event) {
                refreshOpenTableSizesLater();
            }
        });
    }

    private void installVisibleGroupTracking() {
        getVerticalScrollBar().addAdjustmentListener(event -> {
            if (!event.getValueIsAdjusting()) {
                updateActiveGroupFromViewport();
            }
        });
        getViewport().addChangeListener(event -> updateActiveGroupFromViewport());
    }

    private static Path normalize(Path path) {
        return path.toAbsolutePath().normalize();
    }

    private static void runOnEdt(Runnable runnable) {
        if (SwingUtilities.isEventDispatchThread()) {
            runnable.run();
        } else {
            SwingUtilities.invokeLater(runnable);
        }
    }

    private static final class ScrollableContentPanel extends JPanel implements Scrollable {
        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return Math.max(16, visibleRect.height - 16);
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
}
