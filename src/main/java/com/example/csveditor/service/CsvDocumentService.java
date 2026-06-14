package com.example.csveditor.service;

import com.example.csveditor.app.AppConfig;
import com.example.csveditor.domain.CsvDocument;
import com.example.csveditor.io.CsvIOException;
import com.example.csveditor.io.CsvReader;
import com.example.csveditor.io.CsvWriter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.DirectoryStream;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

/**
 * CSV文書の読み込み、保存、再読み込み、安全保存を扱うサービス。
 */
public class CsvDocumentService {
    private static final String BACKUP_DIRECTORY_NAME = ".backup";
    private static final int MAX_BACKUP_GENERATIONS_PER_CSV = 3;
    private final AppConfig appConfig;
    private final CsvReader csvReader;
    private final CsvWriter csvWriter;

    public CsvDocumentService() {
        this(new AppConfig(), new CsvReader(), new CsvWriter());
    }

    public CsvDocumentService(AppConfig appConfig, CsvReader csvReader, CsvWriter csvWriter) {
        this.appConfig = appConfig;
        this.csvReader = csvReader;
        this.csvWriter = csvWriter;
    }

    public CsvDocument open(Path rootPath, Path filePath) throws CsvIOException {
        return load(filePath, rootPath);
    }

    public CsvDocument load(Path filePath, Path rootPath) throws CsvIOException {
        return csvReader.read(filePath, rootPath, appConfig.getCsvCharset());
    }

    public CsvDocument reload(CsvDocument document) throws CsvIOException {
        return load(document.getFilePath(), document.getFilePath().getParent());
    }

    public CsvDocument reload(CsvDocument document, Path rootPath) throws CsvIOException {
        return load(document.getFilePath(), rootPath);
    }

    public Path save(CsvDocument document) throws CsvIOException {
        Path filePath = document.getFilePath();
        Path directory = filePath.getParent();
        if (directory == null) {
            directory = filePath.toAbsolutePath().normalize().getParent();
        }
        if (directory == null) {
            throw new CsvIOException("保存先フォルダーを特定できません: " + filePath);
        }

        Path backupPath = createBackup(document);
        Path tempPath = null;
        try {
            tempPath = Files.createTempFile(directory, filePath.getFileName().toString() + ".", ".tmp");
            csvWriter.write(document, tempPath);
            try {
                Files.move(tempPath, filePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicMoveFailure) {
                Files.move(tempPath, filePath, StandardCopyOption.REPLACE_EXISTING);
            }
            document.clearDirty();
            return backupPath;
        } catch (IOException e) {
            throw new CsvIOException("CSVの安全保存に失敗しました: " + filePath, e);
        } finally {
            if (tempPath != null) {
                try {
                    Files.deleteIfExists(tempPath);
                } catch (IOException ignored) {
                    // 一時ファイル削除失敗は保存失敗の主原因ではないため握りつぶす。
                }
            }
        }
    }

    private Path createBackup(CsvDocument document) throws CsvIOException {
        Path backupPath = createBackupPath(document);
        try {
            Files.createDirectories(backupPath.getParent());
            Files.copy(document.getFilePath(), backupPath, StandardCopyOption.COPY_ATTRIBUTES);
            enforceBackupGenerationLimit(document.getFilePath(), backupPath.getParent());
            return backupPath;
        } catch (IOException e) {
            throw new CsvIOException("バックアップ作成に失敗しました: " + backupPath, e);
        }
    }

    private Path createBackupPath(CsvDocument document) {
        Path filePath = document.getFilePath();
        Path backupDirectory = resolveBackupDirectory(document);
        Path backupPath;
        do {
            String timestamp = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
            backupPath = backupDirectory.resolve(filePath.getFileName().toString() + "." + timestamp + ".bak");
            if (!Files.exists(backupPath)) {
                return backupPath;
            }
            try {
                Thread.sleep(1000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return backupPath;
            }
        } while (true);
    }

    private Path resolveBackupDirectory(CsvDocument document) {
        Path relativePath = document.getRelativePath();
        Path selectedRootPath = resolveSelectedRootPath(document.getFilePath(), relativePath);
        String selectedRootName = selectedRootPath.getFileName() == null
                ? "root"
                : selectedRootPath.getFileName().toString();
        Path backupDirectory = appConfig.getApplicationRootPath()
                .resolve(BACKUP_DIRECTORY_NAME)
                .resolve(selectedRootName);
        if (relativePath == null || relativePath.getParent() == null) {
            return backupDirectory;
        }
        return backupDirectory.resolve(relativePath.getParent());
    }

    private Path resolveSelectedRootPath(Path filePath, Path relativePath) {
        Path rootPath = filePath.toAbsolutePath().normalize();
        int levels = relativePath == null ? 1 : relativePath.getNameCount();
        for (int i = 0; i < levels && rootPath != null; i++) {
            rootPath = rootPath.getParent();
        }
        if (rootPath == null) {
            Path parent = filePath.toAbsolutePath().normalize().getParent();
            return parent == null ? filePath.toAbsolutePath().normalize() : parent;
        }
        return rootPath;
    }

    private void enforceBackupGenerationLimit(Path filePath, Path backupDirectory) throws IOException {
        final String prefix = filePath.getFileName().toString() + ".";
        final String suffix = ".bak";
        List<Path> backups = new ArrayList<Path>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(backupDirectory)) {
            for (Path backup : stream) {
                String fileName = backup.getFileName().toString();
                if (fileName.startsWith(prefix) && fileName.endsWith(suffix)) {
                    backups.add(backup);
                }
            }
        }
        Collections.sort(backups, new Comparator<Path>() {
            @Override
            public int compare(Path left, Path right) {
                return right.getFileName().toString().compareTo(left.getFileName().toString());
            }
        });
        for (int i = MAX_BACKUP_GENERATIONS_PER_CSV; i < backups.size(); i++) {
            Files.deleteIfExists(backups.get(i));
        }
    }
}
