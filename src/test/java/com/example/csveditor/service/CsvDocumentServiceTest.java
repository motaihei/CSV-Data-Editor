package com.example.csveditor.service;

import com.example.csveditor.domain.CsvDocument;
import com.example.csveditor.app.AppConfig;
import com.example.csveditor.io.CsvIOException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CsvDocumentServiceTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void saveCreatesBackupAndClearsDirtyFlag() throws Exception {
        Path root = temporaryFolder.getRoot().toPath();
        Path appRoot = temporaryFolder.newFolder("appRoot").toPath();
        Path directory = root.resolve("Data01").resolve("Data01_01").resolve("sample002");
        Files.createDirectories(directory);
        Path file = directory.resolve("data.csv");
        Charset cp932 = Charset.forName("windows-31j");
        Files.write(file, Arrays.asList("name,value", "old,1"), cp932);
        CsvDocumentService service = new CsvDocumentService(new AppConfig(cp932, true, appRoot),
                new com.example.csveditor.io.CsvReader(), new com.example.csveditor.io.CsvWriter());
        CsvDocument document = service.load(file, root);
        document.getRows().get(0).set(0, "new");
        document.setDirty(true);

        Path backup = service.save(document);

        assertTrue(Files.exists(backup));
        assertEquals(appRoot.resolve(".backup").resolve(root.getFileName()).resolve("Data01").resolve("Data01_01")
                .resolve("sample002"), backup.getParent());
        assertFalse(Files.exists(root.resolve(".backup")));
        assertTrue(backup.getFileName().toString().matches("data\\.csv\\.\\d{14}\\.bak"));
        assertFalse(document.isDirty());
        List<String> backupLines = Files.readAllLines(backup, cp932);
        assertEquals("name,value", backupLines.get(0));
        assertEquals("old,1", backupLines.get(1));
        CsvDocument saved = service.load(file, root);
        assertEquals("new", saved.getRows().get(0).get(0));
    }

    @Test
    public void saveKeepsOnlyThreeBackupGenerationsPerCsv() throws Exception {
        Path root = temporaryFolder.getRoot().toPath();
        Path appRoot = temporaryFolder.newFolder("appRoot").toPath();
        Path directory = root.resolve("Data01").resolve("Data01_01").resolve("sample002");
        Files.createDirectories(directory);
        Path file = directory.resolve("data.csv");
        Charset cp932 = Charset.forName("windows-31j");
        Files.write(file, Arrays.asList("name,value", "old,1"), cp932);
        Path backupDirectory = appRoot.resolve(".backup").resolve(root.getFileName()).resolve("Data01").resolve("Data01_01")
                .resolve("sample002");
        Files.createDirectories(backupDirectory);
        Files.write(backupDirectory.resolve("data.csv.20000101000000.bak"), Arrays.asList("old backup 1"), cp932);
        Files.write(backupDirectory.resolve("data.csv.20000101000001.bak"), Arrays.asList("old backup 2"), cp932);
        Files.write(backupDirectory.resolve("data.csv.20000101000002.bak"), Arrays.asList("old backup 3"), cp932);
        Files.write(backupDirectory.resolve("other.csv.20000101000000.bak"), Arrays.asList("other backup"), cp932);

        CsvDocumentService service = new CsvDocumentService(new AppConfig(cp932, true, appRoot),
                new com.example.csveditor.io.CsvReader(), new com.example.csveditor.io.CsvWriter());
        CsvDocument document = service.load(file, root);
        document.getRows().get(0).set(0, "new");
        document.setDirty(true);

        Path backup = service.save(document);

        assertTrue(Files.exists(backup));
        List<String> dataBackups = new java.util.ArrayList<String>();
        try (java.nio.file.DirectoryStream<Path> stream = Files.newDirectoryStream(backupDirectory)) {
            for (Path backupFile : stream) {
                String fileName = backupFile.getFileName().toString();
                if (fileName.startsWith("data.csv.") && fileName.endsWith(".bak")) {
                    dataBackups.add(fileName);
                }
            }
        }
        assertEquals(3, dataBackups.size());
        assertFalse(Files.exists(backupDirectory.resolve("data.csv.20000101000000.bak")));
        assertTrue(Files.exists(backupDirectory.resolve("other.csv.20000101000000.bak")));
    }

    @Test(expected = CsvIOException.class)
    public void saveFailureIsReportedAsCsvIOException() throws Exception {
        Path missing = temporaryFolder.getRoot().toPath().resolve("missing").resolve("data.csv");
        CsvDocumentService service = new CsvDocumentService();
        CsvDocument document = service.load(temporaryFolder.newFile("base.csv").toPath(), temporaryFolder.getRoot().toPath());
        CsvDocument broken = new CsvDocument(missing, missing.getFileName(), document.getCharset(), document.getLineSeparator(),
                document.getSchema(), document.getRows());

        service.save(broken);
    }
}
