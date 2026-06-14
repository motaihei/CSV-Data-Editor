package com.example.csveditor.domain;

import org.junit.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class DataNodeTest {

    @Test
    public void normalizesAbsolutePathInConstructor() {
        Path source = Paths.get("root").resolve("..").resolve("root").resolve("Data001").resolve("..")
                .resolve("Data001").resolve("input").resolve("user.csv");

        DataNode node = DataNode.csvFile("user.csv", source, Paths.get("Data001", ".", "input", "..",
                "input", "user.csv"));

        assertTrue(node.getAbsolutePath().isAbsolute());
        assertEquals(source.toAbsolutePath().normalize(), node.getAbsolutePath());
        assertEquals(source.toAbsolutePath().normalize(), node.getNormalizedAbsolutePath());
    }

    @Test
    public void normalizesNonNullRelativePathAndAllowsNull() {
        Path absolutePath = Paths.get("root").resolve("Data001");
        Path relativePath = Paths.get("Data001").resolve(".").resolve("input").resolve("..");

        DataNode directory = DataNode.directory("Data001", absolutePath, relativePath,
                Collections.<DataNode>emptyList());
        DataNode root = DataNode.directory("root", absolutePath, null,
                Collections.<DataNode>emptyList());

        assertEquals(relativePath.normalize(), directory.getRelativePath());
        assertNull(root.getRelativePath());
    }
}
