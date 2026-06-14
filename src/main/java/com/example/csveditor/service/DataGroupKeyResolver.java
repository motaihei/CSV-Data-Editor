package com.example.csveditor.service;

import java.nio.file.Path;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * CSV相対パスからデータグループ名を解決する。
 */
public class DataGroupKeyResolver {
    private final DataGroupingConfig config;

    public DataGroupKeyResolver(DataGroupingConfig config) {
        this.config = config == null ? DataGroupingConfig.defaultConfig() : config;
    }

    public String resolve(Path relativePath) {
        if (relativePath == null || relativePath.getNameCount() == 0) {
            return "未分類";
        }
        String resolved = resolveByActivePattern(relativePath);
        if (isBlank(resolved)) {
            return legacyUnderscoreFallback(relativePath);
        }
        return resolved;
    }

    private String resolveByActivePattern(Path relativePath) {
        String activePattern = config.getActivePattern();
        DataGroupingConfig.Patterns patterns = config.getPatterns();
        if (patterns == null || isBlank(activePattern)) {
            return null;
        }
        if ("pathSegmentPattern".equals(activePattern)) {
            DataGroupingConfig.PathSegmentPattern pattern = patterns.getPathSegmentPattern();
            return pattern == null ? null : resolvePathSegmentPattern(relativePath, pattern);
        }
        if ("pathSegmentIndex".equals(activePattern)) {
            DataGroupingConfig.PathSegmentIndex pattern = patterns.getPathSegmentIndex();
            return pattern == null ? null : resolvePathSegmentIndex(relativePath, pattern);
        }
        if ("parentDirectory".equals(activePattern)) {
            DataGroupingConfig.ParentDirectory pattern = patterns.getParentDirectory();
            return pattern == null ? null : resolveParentDirectory(relativePath, pattern);
        }
        return null;
    }

    private String resolvePathSegmentPattern(Path relativePath, DataGroupingConfig.PathSegmentPattern setting) {
        if (isBlank(setting.getRegex())) {
            return applyFallback(relativePath, setting.getFallback());
        }
        Pattern pattern;
        try {
            pattern = Pattern.compile(setting.getRegex());
        } catch (PatternSyntaxException ex) {
            return legacyUnderscoreFallback(relativePath);
        }
        int endExclusive = includeFileName(setting.getSearchTarget()) ? relativePath.getNameCount() : relativePath.getNameCount() - 1;
        for (int i = 0; i < endExclusive; i++) {
            String name = relativePath.getName(i).toString();
            if (pattern.matcher(name).matches()) {
                return name;
            }
        }
        return applyFallback(relativePath, setting.getFallback());
    }

    private String resolvePathSegmentIndex(Path relativePath, DataGroupingConfig.PathSegmentIndex setting) {
        int directoryCount = Math.max(0, relativePath.getNameCount() - 1);
        int index = setting.getIndex();
        if (index >= 0 && index < directoryCount) {
            return relativePath.getName(index).toString();
        }
        return applyFallback(relativePath, setting.getFallback());
    }

    private String resolveParentDirectory(Path relativePath, DataGroupingConfig.ParentDirectory setting) {
        int levelsUpFromFile = setting.getLevelsUpFromFile();
        int index = relativePath.getNameCount() - 1 - levelsUpFromFile;
        if (levelsUpFromFile > 0 && index >= 0 && index < relativePath.getNameCount() - 1) {
            return relativePath.getName(index).toString();
        }
        return applyFallback(relativePath, setting.getFallback());
    }

    private String applyFallback(Path relativePath, String fallback) {
        if ("firstDirectory".equals(fallback)) {
            return firstDirectory(relativePath);
        }
        if ("legacyUnderscore".equals(fallback)) {
            return legacyUnderscoreFallback(relativePath);
        }
        return legacyUnderscoreFallback(relativePath);
    }

    private String legacyUnderscoreFallback(Path relativePath) {
        for (int i = 0; i < relativePath.getNameCount() - 1; i++) {
            String name = relativePath.getName(i).toString();
            if (name.indexOf('_') >= 0) {
                return name;
            }
        }
        return firstDirectory(relativePath);
    }

    private String firstDirectory(Path relativePath) {
        return relativePath.getNameCount() <= 1 ? "未分類" : relativePath.getName(0).toString();
    }

    private boolean includeFileName(String searchTarget) {
        return "allSegments".equals(searchTarget);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
