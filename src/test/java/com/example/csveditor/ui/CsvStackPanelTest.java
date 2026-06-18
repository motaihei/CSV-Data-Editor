package com.example.csveditor.ui;

import com.example.csveditor.domain.CsvDocument;
import com.example.csveditor.domain.CsvSchema;
import com.example.csveditor.service.CsvDocumentService;
import com.example.csveditor.service.DataGroupKeyResolver;
import com.example.csveditor.service.DataGroupingConfig;
import org.junit.Test;

import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CsvStackPanelTest {

    @Test
    public void filtersOpenPanelsByCsvFileNameIgnoringCase() throws Exception {
        final CsvStackPanel stackPanel = createStackPanel(false);
        runOnEdt(new Runnable() {
            @Override
            public void run() {
                stackPanel.addOrFocusDocuments(Arrays.asList(
                        document("GroupA", "Report.csv"),
                        document("GroupA", "summary.csv"),
                        document("GroupB", "report-extra.csv")));
                stackPanel.setCsvFileNameFilter("REPORT");
            }
        });

        assertEquals(Arrays.asList("Report.csv", "report-extra.csv"), getDisplayedFileNames(stackPanel));

        runOnEdt(new Runnable() {
            @Override
            public void run() {
                stackPanel.setCsvFileNameFilter("");
            }
        });

        assertEquals(Arrays.asList("Report.csv", "summary.csv", "report-extra.csv"),
                getDisplayedFileNames(stackPanel));
    }

    @Test
    public void groupedFilterSearchesAllOpenGroups() throws Exception {
        final CsvStackPanel stackPanel = createStackPanel(true);
        runOnEdt(new Runnable() {
            @Override
            public void run() {
                stackPanel.addOrFocusDocuments(Arrays.asList(
                        document("GroupA", "target.csv"),
                        document("GroupA", "other.csv"),
                        document("GroupB", "target.csv")));
                stackPanel.focusGroup("GroupA");
                stackPanel.setCsvFileNameFilter("target");
            }
        });

        assertEquals(Arrays.asList("target.csv", "target.csv"), getDisplayedFileNames(stackPanel));
        assertEquals("GroupA", stackPanel.getActiveGroupKey());

        runOnEdt(new Runnable() {
            @Override
            public void run() {
                stackPanel.setCsvFileNameFilter("");
            }
        });

        assertEquals(Arrays.asList("target.csv", "other.csv", "target.csv"),
                getDisplayedFileNames(stackPanel));
    }

    @Test
    public void closesOnlyPanelsUnderUnregisteredRoot() throws Exception {
        final CsvStackPanel stackPanel = createStackPanel(true);
        final Path firstRoot = Paths.get("C:\\data\\RootA");
        final Path secondRoot = Paths.get("C:\\data\\RootB");
        runOnEdt(new Runnable() {
            @Override
            public void run() {
                stackPanel.addOrFocusDocuments(Arrays.asList(
                        document(firstRoot, "GroupA", "first.csv"),
                        document(secondRoot, "GroupB", "second.csv"),
                        document(firstRoot, "GroupC", "third.csv")));
                stackPanel.requestClosePanelsUnderRoots(Collections.singleton(firstRoot));
            }
        });

        assertEquals(Arrays.asList("second.csv"), getDisplayedFileNames(stackPanel));
        assertEquals(Arrays.asList("GroupB\\second.csv"), stackPanel.getOpenRelativePaths());
        assertEquals(Arrays.asList("GroupB"), stackPanel.getOpenGroupKeys());
    }

    @Test
    public void synchronizesHorizontalScrollWhenDisplayedFileNamesAndColumnCountsMatch() throws Exception {
        final CsvStackPanel stackPanel = createStackPanel(true);
        runOnEdt(new Runnable() {
            @Override
            public void run() {
                stackPanel.setSize(420, 600);
                stackPanel.addOrFocusDocuments(Arrays.asList(
                        document("GroupA", "target.csv", 8),
                        document("GroupA", "other.csv", 5),
                        document("GroupB", "target.csv", 8)));
                stackPanel.setCsvFileNameFilter("target.csv");
                stackPanel.applyColumnWidthToOpenPanels(160);
                stackPanel.refreshOpenTableSizes();
                stackPanel.doLayout();
            }
        });

        final List<CsvEditorPanel> displayedPanels = getDisplayedPanels(stackPanel);
        assertEquals(2, displayedPanels.size());
        assertTrue(displayedPanels.get(0).getTableHorizontalScrollMaximumValue() > 0);

        runOnEdt(new Runnable() {
            @Override
            public void run() {
                displayedPanels.get(0).getTableHorizontalScrollBar().setValue(180);
            }
        });

        assertEquals(displayedPanels.get(0).getTableHorizontalScrollValue(),
                displayedPanels.get(1).getTableHorizontalScrollValue());
    }

    @Test
    public void doesNotSynchronizeHorizontalScrollWhenDisplayedColumnCountsDiffer() throws Exception {
        final CsvStackPanel stackPanel = createStackPanel(true);
        runOnEdt(new Runnable() {
            @Override
            public void run() {
                stackPanel.setSize(420, 600);
                stackPanel.addOrFocusDocuments(Arrays.asList(
                        document("GroupA", "target.csv", 8),
                        document("GroupB", "target.csv", 5)));
                stackPanel.setCsvFileNameFilter("target.csv");
                stackPanel.applyColumnWidthToOpenPanels(160);
                stackPanel.refreshOpenTableSizes();
                stackPanel.doLayout();
            }
        });

        final List<CsvEditorPanel> displayedPanels = getDisplayedPanels(stackPanel);
        assertEquals(2, displayedPanels.size());
        assertTrue(displayedPanels.get(0).getTableHorizontalScrollMaximumValue() > 0);

        runOnEdt(new Runnable() {
            @Override
            public void run() {
                displayedPanels.get(0).getTableHorizontalScrollBar().setValue(180);
            }
        });

        assertEquals(0, displayedPanels.get(1).getTableHorizontalScrollValue());
    }

    @Test
    public void synchronizesColumnWidthWhenDisplayedFileNamesAndColumnCountsMatch() throws Exception {
        final CsvStackPanel stackPanel = createStackPanel(true);
        runOnEdt(new Runnable() {
            @Override
            public void run() {
                stackPanel.setSize(420, 600);
                stackPanel.addOrFocusDocuments(Arrays.asList(
                        document("GroupA", "target.csv", 8),
                        document("GroupA", "other.csv", 5),
                        document("GroupB", "target.csv", 8)));
                stackPanel.setCsvFileNameFilter("target.csv");
                stackPanel.refreshOpenTableSizes();
                stackPanel.doLayout();
            }
        });

        final List<CsvEditorPanel> displayedPanels = getDisplayedPanels(stackPanel);
        assertEquals(2, displayedPanels.size());

        runOnEdt(new Runnable() {
            @Override
            public void run() {
                displayedPanels.get(0).getTableColumn(2).setPreferredWidth(240);
                displayedPanels.get(0).getTableColumn(2).setWidth(240);
            }
        });

        assertEquals(240, displayedPanels.get(0).getTableColumnWidth(2));
        assertEquals(240, displayedPanels.get(1).getTableColumnWidth(2));
    }

    @Test
    public void doesNotSynchronizeColumnWidthWhenDisplayedColumnCountsDiffer() throws Exception {
        final CsvStackPanel stackPanel = createStackPanel(true);
        runOnEdt(new Runnable() {
            @Override
            public void run() {
                stackPanel.setSize(420, 600);
                stackPanel.addOrFocusDocuments(Arrays.asList(
                        document("GroupA", "target.csv", 8),
                        document("GroupB", "target.csv", 5)));
                stackPanel.setCsvFileNameFilter("target.csv");
                stackPanel.refreshOpenTableSizes();
                stackPanel.doLayout();
            }
        });

        final List<CsvEditorPanel> displayedPanels = getDisplayedPanels(stackPanel);
        assertEquals(2, displayedPanels.size());
        int originalWidth = displayedPanels.get(1).getTableColumnWidth(2);

        runOnEdt(new Runnable() {
            @Override
            public void run() {
                displayedPanels.get(0).getTableColumn(2).setPreferredWidth(240);
                displayedPanels.get(0).getTableColumn(2).setWidth(240);
            }
        });

        assertEquals(240, displayedPanels.get(0).getTableColumnWidth(2));
        assertEquals(originalWidth, displayedPanels.get(1).getTableColumnWidth(2));
    }

    private static CsvStackPanel createStackPanel(boolean dataGroupingEnabled) {
        CsvStackPanel stackPanel = new CsvStackPanel(new CsvDocumentService(),
                new DataGroupKeyResolver(DataGroupingConfig.pathSegmentLevelConfig(1)));
        stackPanel.setDataGroupingEnabled(dataGroupingEnabled);
        return stackPanel;
    }

    private static CsvDocument document(String groupName, String fileName) {
        Path relativePath = Paths.get(groupName, fileName);
        return new CsvDocument(Paths.get("C:\\data").resolve(relativePath), relativePath);
    }

    private static CsvDocument document(Path rootPath, String groupName, String fileName) {
        Path relativePath = Paths.get(groupName, fileName);
        return new CsvDocument(rootPath.resolve(relativePath), relativePath);
    }

    private static CsvDocument document(String groupName, String fileName, int columnCount) {
        Path relativePath = Paths.get(groupName, fileName);
        List<String> headers = new ArrayList<String>();
        List<String> row = new ArrayList<String>();
        for (int i = 0; i < columnCount; i++) {
            headers.add("column_" + i);
            row.add("value_" + i);
        }
        return new CsvDocument(Paths.get("C:\\data").resolve(relativePath), relativePath,
                java.nio.charset.Charset.forName("Windows-31J"), "\r\n",
                new CsvSchema(headers, columnCount), Collections.singletonList(row));
    }

    private static List<String> getDisplayedFileNames(CsvStackPanel stackPanel) throws Exception {
        final List<String> fileNames = new ArrayList<String>();
        runOnEdt(new Runnable() {
            @Override
            public void run() {
                Component view = stackPanel.getViewport().getView();
                collectDisplayedFileNames(view, fileNames);
            }
        });
        return fileNames;
    }

    private static List<CsvEditorPanel> getDisplayedPanels(CsvStackPanel stackPanel) throws Exception {
        final List<CsvEditorPanel> panels = new ArrayList<CsvEditorPanel>();
        runOnEdt(new Runnable() {
            @Override
            public void run() {
                Component view = stackPanel.getViewport().getView();
                collectDisplayedPanels(view, panels);
            }
        });
        return panels;
    }

    private static void collectDisplayedFileNames(Component component, List<String> fileNames) {
        if (component instanceof CsvEditorPanel) {
            fileNames.add(((CsvEditorPanel) component).getDocument().getFileName());
            return;
        }
        if (!(component instanceof Container) || component instanceof JViewport) {
            return;
        }
        Component[] children = ((Container) component).getComponents();
        for (Component child : children) {
            collectDisplayedFileNames(child, fileNames);
        }
    }

    private static void collectDisplayedPanels(Component component, List<CsvEditorPanel> panels) {
        if (component instanceof CsvEditorPanel) {
            panels.add((CsvEditorPanel) component);
            return;
        }
        if (!(component instanceof Container) || component instanceof JViewport) {
            return;
        }
        Component[] children = ((Container) component).getComponents();
        for (Component child : children) {
            collectDisplayedPanels(child, panels);
        }
    }

    private static void runOnEdt(Runnable runnable) throws Exception {
        if (SwingUtilities.isEventDispatchThread()) {
            runnable.run();
        } else {
            SwingUtilities.invokeAndWait(runnable);
        }
    }
}
