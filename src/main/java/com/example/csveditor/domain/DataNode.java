package com.example.csveditor.domain;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Node model displayed by the CSV data tree.
 */
public class DataNode {

    public enum Type {
        ROOT,
        DIRECTORY,
        CSV_FILE
    }

    private final String displayName;
    private final Path path;
    private final Path relativePath;
    private final Type type;
    private final List<DataNode> children;

    public DataNode(String displayName, Path path, Path relativePath, Type type) {
        this.displayName = displayName;
        this.path = path.toAbsolutePath().normalize();
        this.relativePath = relativePath == null ? null : relativePath.normalize();
        this.type = type;
        this.children = new ArrayList<DataNode>();
    }

    public static DataNode directory(String displayName, Path path, Path relativePath,
            List<DataNode> children) {
        DataNode node = new DataNode(displayName, path, relativePath, Type.DIRECTORY);
        for (DataNode child : children) {
            node.addChild(child);
        }
        return node;
    }

    public static DataNode csvFile(String displayName, Path path, Path relativePath) {
        return new DataNode(displayName, path, relativePath, Type.CSV_FILE);
    }

    public String getDisplayName() {
        return displayName;
    }

    public Path getPath() {
        return path;
    }

    public Path getAbsolutePath() {
        return path;
    }

    public Path getNormalizedAbsolutePath() {
        return path;
    }

    public Path getRelativePath() {
        return relativePath;
    }

    public Type getType() {
        return type;
    }

    public boolean isCsvFile() {
        return type == Type.CSV_FILE;
    }

    public boolean isDirectory() {
        return type == Type.ROOT || type == Type.DIRECTORY;
    }

    public void addChild(DataNode child) {
        children.add(child);
    }

    public List<DataNode> getChildren() {
        return Collections.unmodifiableList(children);
    }

    public boolean hasChildren() {
        return !children.isEmpty();
    }

    @Override
    public String toString() {
        return displayName;
    }
}
