package com.example.csveditor.service;

import com.example.csveditor.domain.DataNode;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Scans a root folder into a CSV-only CSV data tree.
 */
public class CsvDataScanService {

    public DataNode scan(List<Path> roots) throws IOException {
        if (roots == null || roots.isEmpty()) {
            throw new IllegalArgumentException("roots must not be empty");
        }
        List<Path> normalizedRoots = normalizeRoots(roots);
        if (normalizedRoots.size() == 1) {
            return scan(normalizedRoots.get(0));
        }

        DataNode rootNode = new DataNode("選択ルート", normalizedRoots.get(0),
                normalizedRoots.get(0).getFileSystem().getPath(""), DataNode.Type.ROOT);
        Set<String> duplicateNames = findDuplicateRootNames(normalizedRoots);
        for (Path root : normalizedRoots) {
            rootNode.addChild(scanRoot(root, duplicateNames.contains(displayName(root))));
        }
        return rootNode;
    }

    public DataNode scan(Path root) throws IOException {
        if (root == null) {
            throw new IllegalArgumentException("root must not be null");
        }
        Path normalizedRoot = root.toAbsolutePath().normalize();
        return scanRoot(normalizedRoot, false);
    }

    private DataNode scanRoot(Path normalizedRoot, boolean includeFullPathInName) throws IOException {
        if (!Files.isDirectory(normalizedRoot)) {
            throw new IllegalArgumentException("root must be a directory: " + normalizedRoot);
        }
        DataNode rootNode = new DataNode(includeFullPathInName ? normalizedRoot.toString() : displayName(normalizedRoot),
                normalizedRoot,
                normalizedRoot.getFileSystem().getPath(""), DataNode.Type.ROOT);
        List<DataNode> children = scanChildren(normalizedRoot, normalizedRoot);
        for (DataNode child : children) {
            rootNode.addChild(child);
        }
        return rootNode;
    }

    public DefaultTreeModel scanAsTreeModel(Path root) throws IOException {
        return new DefaultTreeModel(toMutableTreeNode(scan(root)));
    }

    public DefaultTreeModel scanAsTreeModel(List<Path> roots) throws IOException {
        return new DefaultTreeModel(toMutableTreeNode(scan(roots)));
    }

    public DefaultMutableTreeNode toMutableTreeNode(DataNode node) {
        DefaultMutableTreeNode treeNode = new DefaultMutableTreeNode(node);
        for (DataNode child : node.getChildren()) {
            treeNode.add(toMutableTreeNode(child));
        }
        return treeNode;
    }

    private List<DataNode> scanChildren(Path root, Path directory) throws IOException {
        List<Path> paths = new ArrayList<Path>();
        DirectoryStream<Path> stream = Files.newDirectoryStream(directory);
        try {
            for (Path path : stream) {
                paths.add(path);
            }
        } finally {
            stream.close();
        }

        Collections.sort(paths, new Comparator<Path>() {
            @Override
            public int compare(Path left, Path right) {
                boolean leftDir = Files.isDirectory(left);
                boolean rightDir = Files.isDirectory(right);
                if (leftDir != rightDir) {
                    return leftDir ? -1 : 1;
                }
                return displayName(left).compareToIgnoreCase(displayName(right));
            }
        });

        List<DataNode> nodes = new ArrayList<DataNode>();
        for (Path path : paths) {
            if (Files.isDirectory(path)) {
                DataNode directoryNode = new DataNode(displayName(path), path.toAbsolutePath().normalize(),
                        root.relativize(path.toAbsolutePath().normalize()), DataNode.Type.DIRECTORY);
                List<DataNode> children = scanChildren(root, path);
                for (DataNode child : children) {
                    directoryNode.addChild(child);
                }
                if (directoryNode.hasChildren()) {
                    nodes.add(directoryNode);
                }
            } else if (Files.isRegularFile(path) && isCsv(path)) {
                nodes.add(new DataNode(displayName(path), path.toAbsolutePath().normalize(),
                        root.relativize(path.toAbsolutePath().normalize()), DataNode.Type.CSV_FILE));
            }
        }
        return nodes;
    }

    private static boolean isCsv(Path path) {
        Path fileName = path.getFileName();
        return fileName != null && fileName.toString().toLowerCase(Locale.ROOT).endsWith(".csv");
    }

    private static String displayName(Path path) {
        Path fileName = path.getFileName();
        return fileName == null ? path.toString() : fileName.toString();
    }

    private static List<Path> normalizeRoots(List<Path> roots) {
        List<Path> normalizedRoots = new ArrayList<Path>();
        for (Path root : roots) {
            if (root == null) {
                continue;
            }
            Path normalizedRoot = root.toAbsolutePath().normalize();
            if (!normalizedRoots.contains(normalizedRoot)) {
                normalizedRoots.add(normalizedRoot);
            }
        }
        if (normalizedRoots.isEmpty()) {
            throw new IllegalArgumentException("roots must not be empty");
        }
        return normalizedRoots;
    }

    private static Set<String> findDuplicateRootNames(List<Path> roots) {
        Set<String> seen = new HashSet<String>();
        Set<String> duplicates = new HashSet<String>();
        for (Path root : roots) {
            String name = displayName(root);
            if (!seen.add(name)) {
                duplicates.add(name);
            }
        }
        return duplicates;
    }
}
