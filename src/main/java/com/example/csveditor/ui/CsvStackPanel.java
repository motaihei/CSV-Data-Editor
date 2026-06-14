package com.example.csveditor.ui;

import com.example.csveditor.domain.CsvDocument;
import com.example.csveditor.service.CsvDocumentService;
import com.example.csveditor.service.DataGroupKeyResolver;
import com.example.csveditor.service.DataGroupingConfigLoader;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Rectangle;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Scrollable vertical stack of independent CSV editor panels.
 */
public class CsvStackPanel extends JScrollPane {
    public interface SessionChangeListener {
        void sessionChanged();
    }

    private final JPanel contentPanel;
    private final Map<Path, CsvEditorPanel> panelsByPath;
    private final Set<String> collapsedGroupKeys;
    private final CsvDocumentService documentService;
    private final DataGroupKeyResolver groupKeyResolver;
    private SessionChangeListener sessionChangeListener;

    public CsvStackPanel(CsvDocumentService documentService) {
        this(documentService, new DataGroupKeyResolver(DataGroupingConfigLoader.loadDefault()));
    }

    public CsvStackPanel(CsvDocumentService documentService, DataGroupKeyResolver groupKeyResolver) {
        this.documentService = documentService;
        this.groupKeyResolver = groupKeyResolver == null
                ? new DataGroupKeyResolver(DataGroupingConfigLoader.loadDefault())
                : groupKeyResolver;
        this.contentPanel = new JPanel();
        this.contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        this.contentPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        this.panelsByPath = new LinkedHashMap<Path, CsvEditorPanel>();
        this.collapsedGroupKeys = new HashSet<String>();
        setViewportView(contentPanel);
        getVerticalScrollBar().setUnitIncrement(16);
        getHorizontalScrollBar().setUnitIncrement(16);
    }

    public void setSessionChangeListener(SessionChangeListener sessionChangeListener) {
        this.sessionChangeListener = sessionChangeListener;
    }

    public void addOrFocusDocument(final CsvDocument document) {
        runOnEdt(new Runnable() {
            @Override
            public void run() {
                Path key = normalize(document.getFilePath());
                CsvEditorPanel existing = panelsByPath.get(key);
                if (existing != null) {
                    scrollToPanel(existing);
                    existing.requestFocusInWindow();
                    return;
                }

                final CsvEditorPanel panel = new CsvEditorPanel(document, documentService);
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

                panelsByPath.put(key, panel);
                rebuildContentPanel();
                notifySessionChanged();
                scrollToPanel(panel);
            }
        });
    }

    public boolean hasOpenDocument(Path filePath) {
        return panelsByPath.containsKey(normalize(filePath));
    }

    public void focusDocument(Path filePath) {
        CsvEditorPanel panel = panelsByPath.get(normalize(filePath));
        if (panel != null) {
            scrollToPanel(panel);
            panel.requestFocusInWindow();
        }
    }

    public List<CsvEditorPanel> getOpenPanels() {
        return new ArrayList<CsvEditorPanel>(panelsByPath.values());
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

    public boolean requestCloseAll() {
        List<CsvEditorPanel> openPanels = getOpenPanels();
        for (CsvEditorPanel panel : openPanels) {
            if (!panel.confirmForApplicationExit(this)) {
                return false;
            }
        }
        for (CsvEditorPanel panel : openPanels) {
            closePanel(panel);
        }
        return true;
    }

    private void closePanel(CsvEditorPanel panel) {
        panelsByPath.remove(normalize(panel.getDocument().getFilePath()));
        rebuildContentPanel();
        notifySessionChanged();
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

        Collections.swap(entries, currentIndex, groupIndexes.get(targetGroupIndex).intValue());

        panelsByPath.clear();
        for (Map.Entry<Path, CsvEditorPanel> entry : entries) {
            panelsByPath.put(entry.getKey(), entry.getValue());
        }
        rebuildContentPanel();
        notifySessionChanged();
        scrollToPanel(panel);
    }

    private void rebuildContentPanel() {
        contentPanel.removeAll();
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

        for (Map.Entry<String, List<CsvEditorPanel>> entry : groupedPanels.entrySet()) {
            String groupKey = entry.getKey();
            JPanel groupPanel = createGroupPanel(groupKey);
            JPanel bodyPanel = createGroupBodyPanel();
            List<CsvEditorPanel> groupPanels = entry.getValue();
            for (int i = 0; i < groupPanels.size(); i++) {
                CsvEditorPanel panel = groupPanels.get(i);
                panel.setMoveAvailability(i > 0, i < groupPanels.size() - 1);
                if (!collapsedGroupKeys.contains(groupKey)) {
                    bodyPanel.add(panel);
                    bodyPanel.add(Box.createVerticalStrut(6));
                }
            }
            if (!collapsedGroupKeys.contains(groupKey)) {
                groupPanel.add(bodyPanel, BorderLayout.CENTER);
            }
            contentPanel.add(groupPanel);
            contentPanel.add(Box.createVerticalStrut(8));
        }
        revalidate();
        repaint();
    }

    private JPanel createGroupPanel(String groupKey) {
        JPanel groupPanel = new JPanel() {
            @Override
            public Dimension getMaximumSize() {
                return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
            }
        };
        groupPanel.setLayout(new BorderLayout(0, 4));
        groupPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        groupPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color.BLACK, 1),
                BorderFactory.createEmptyBorder(4, 4, 0, 4)));
        groupPanel.add(createGroupHeaderPanel(groupKey), BorderLayout.NORTH);
        return groupPanel;
    }

    private JPanel createGroupBodyPanel() {
        JPanel bodyPanel = new JPanel();
        bodyPanel.setLayout(new BoxLayout(bodyPanel, BoxLayout.Y_AXIS));
        bodyPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        return bodyPanel;
    }

    private JPanel createGroupHeaderPanel(final String groupKey) {
        JPanel headerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        JLabel titleLabel = new JLabel(groupKey);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD));
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

        headerPanel.add(titleLabel);
        headerPanel.add(collapseButton);
        headerPanel.add(closeGroupButton);
        return headerPanel;
    }

    private JButton createGroupHeaderButton(String text) {
        JButton button = new JButton(text);
        button.setFocusable(false);
        boolean closeAllButton = "すべて閉じる".equals(text);
        button.setMargin(closeAllButton ? new java.awt.Insets(0, 4, 0, 4) : new java.awt.Insets(0, 0, 1, 0));
        if (!closeAllButton) {
            button.setFont(button.getFont().deriveFont(Font.BOLD, 13f));
        }
        button.setPreferredSize(new Dimension(closeAllButton ? 92 : 34, 24));
        button.setMinimumSize(button.getPreferredSize());
        return button;
    }

    private String getGroupKey(CsvEditorPanel panel) {
        return groupKeyResolver.resolve(panel.getDocument().getRelativePath());
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
}
