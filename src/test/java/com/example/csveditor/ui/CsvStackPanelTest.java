package com.example.csveditor.ui;

import com.example.csveditor.domain.CsvDocument;
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

    private static void runOnEdt(Runnable runnable) throws Exception {
        if (SwingUtilities.isEventDispatchThread()) {
            runnable.run();
        } else {
            SwingUtilities.invokeAndWait(runnable);
        }
    }
}
