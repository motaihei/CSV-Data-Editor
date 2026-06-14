package com.example.csveditor.service;

import com.example.csveditor.domain.DataNode;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class CsvDataScanServiceTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private final CsvDataScanService service = new CsvDataScanService();

    @Test
    public void excludesNonCsvFiles() throws Exception {
        File root = temporaryFolder.newFolder("data-root");
        write(root.toPath().resolve("user.csv"));
        write(root.toPath().resolve("setting.xml"));
        write(root.toPath().resolve("memo.txt"));

        DataNode node = service.scan(root.toPath());

        assertEquals(1, node.getChildren().size());
        assertEquals("user.csv", node.getChildren().get(0).getDisplayName());
    }

    @Test
    public void excludesBranchesWithoutCsvFiles() throws Exception {
        File root = temporaryFolder.newFolder("data-root");
        Path rootPath = root.toPath();
        Files.createDirectories(rootPath.resolve("Data001").resolve("input"));
        Files.createDirectories(rootPath.resolve("Data001").resolve("empty"));
        Files.createDirectories(rootPath.resolve("Data002").resolve("onlyXml"));
        write(rootPath.resolve("Data001").resolve("input").resolve("user.csv"));
        write(rootPath.resolve("Data002").resolve("onlyXml").resolve("setting.xml"));

        DataNode node = service.scan(rootPath);

        assertEquals(1, node.getChildren().size());
        DataNode data001 = node.getChildren().get(0);
        assertEquals("Data001", data001.getDisplayName());
        assertEquals(1, data001.getChildren().size());
        assertEquals("input", data001.getChildren().get(0).getDisplayName());
    }

    @Test
    public void identifiesSameNamesByNormalizedAbsolutePath() throws Exception {
        File root = temporaryFolder.newFolder("data-root");
        Path rootPath = root.toPath();
        write(rootPath.resolve("Data001").resolve("input").resolve("user.csv"));
        write(rootPath.resolve("Data002").resolve("input").resolve("user.csv"));

        DataNode node = service.scan(rootPath);
        DataNode first = findCsv(node, "Data001/input/user.csv");
        DataNode second = findCsv(node, "Data002/input/user.csv");

        assertEquals("user.csv", first.getDisplayName());
        assertEquals("user.csv", second.getDisplayName());
        assertNotEquals(first.getNormalizedAbsolutePath(), second.getNormalizedAbsolutePath());
        assertTrue(first.getNormalizedAbsolutePath().isAbsolute());
        assertTrue(second.getNormalizedAbsolutePath().isAbsolute());
    }

    @Test
    public void keepsNestedStructureAndAcceptsUppercaseCsvExtension() throws Exception {
        File root = temporaryFolder.newFolder("data-root");
        Path rootPath = root.toPath();
        write(rootPath.resolve("Data001").resolve("expected").resolve("deep").resolve("RESULT.CSV"));

        DataNode node = service.scan(rootPath);

        assertEquals("Data001", node.getChildren().get(0).getDisplayName());
        DataNode expected = node.getChildren().get(0).getChildren().get(0);
        assertEquals("expected", expected.getDisplayName());
        DataNode deep = expected.getChildren().get(0);
        assertEquals("deep", deep.getDisplayName());
        assertEquals("RESULT.CSV", deep.getChildren().get(0).getDisplayName());
    }

    @Test
    public void createsSwingTreeModelWithDataNodeUserObjects() throws Exception {
        File root = temporaryFolder.newFolder("data-root");
        write(root.toPath().resolve("Data001").resolve("input").resolve("user.csv"));

        DefaultTreeModel model = service.scanAsTreeModel(root.toPath());
        DefaultMutableTreeNode rootNode = (DefaultMutableTreeNode) model.getRoot();
        DefaultMutableTreeNode dataNode = (DefaultMutableTreeNode) rootNode.getChildAt(0);

        assertTrue(rootNode.getUserObject() instanceof DataNode);
        assertTrue(dataNode.getUserObject() instanceof DataNode);
        assertFalse(((DataNode) dataNode.getUserObject()).getChildren().isEmpty());
    }

    private DataNode findCsv(DataNode root, String relativePath) {
        DataNode found = findCsvOrNull(root, relativePath);
        if (found == null) {
            throw new AssertionError("CSV not found: " + relativePath);
        }
        return found;
    }

    private DataNode findCsvOrNull(DataNode root, String relativePath) {
        String normalized = relativePath.replace('/', File.separatorChar);
        if (root.isCsvFile() && root.getRelativePath().toString().equals(normalized)) {
            return root;
        }
        List<DataNode> children = root.getChildren();
        for (DataNode child : children) {
            DataNode found = findCsvOrNull(child, relativePath);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private void write(Path path) throws IOException {
        Files.createDirectories(path.getParent());
        Files.write(path, "header\r\nvalue\r\n".getBytes(StandardCharsets.UTF_8));
    }
}
