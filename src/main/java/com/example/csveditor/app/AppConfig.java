package com.example.csveditor.app;

import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class AppConfig {
    public static final String DEFAULT_CHARSET_NAME = "windows-31j";

    private final Charset csvCharset;
    private final boolean firstRowHeader;
    private final Path applicationRootPath;

    public AppConfig() {
        this(Charset.forName(DEFAULT_CHARSET_NAME), true, resolveApplicationRootPath());
    }

    public AppConfig(Charset csvCharset, boolean firstRowHeader) {
        this(csvCharset, firstRowHeader, resolveApplicationRootPath());
    }

    public AppConfig(Charset csvCharset, boolean firstRowHeader, Path applicationRootPath) {
        this.csvCharset = csvCharset;
        this.firstRowHeader = firstRowHeader;
        this.applicationRootPath = applicationRootPath == null
                ? resolveApplicationRootPath()
                : applicationRootPath.toAbsolutePath().normalize();
    }

    public Charset getCsvCharset() {
        return csvCharset;
    }

    public boolean isFirstRowHeader() {
        return firstRowHeader;
    }

    public Path getApplicationRootPath() {
        return applicationRootPath;
    }

    private static Path resolveApplicationRootPath() {
        Path workingDirectory = Paths.get("").toAbsolutePath().normalize();
        try {
            Path location = Paths.get(AppConfig.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI()).toAbsolutePath().normalize();
            if (Files.isRegularFile(location)) {
                Path parent = location.getParent();
                if (parent != null && "app".equalsIgnoreCase(parent.getFileName().toString())
                        && parent.getParent() != null) {
                    return parent.getParent();
                }
                return parent == null ? workingDirectory : parent;
            }
            if (Files.isDirectory(location)
                    && location.getFileName() != null
                    && "classes".equalsIgnoreCase(location.getFileName().toString())
                    && location.getParent() != null
                    && location.getParent().getFileName() != null
                    && "target".equalsIgnoreCase(location.getParent().getFileName().toString())
                    && location.getParent().getParent() != null) {
                return location.getParent().getParent();
            }
        } catch (URISyntaxException | SecurityException ignored) {
            // 実行環境から配置場所を取得できない場合は作業ディレクトリを使用する。
        }
        return workingDirectory;
    }
}
