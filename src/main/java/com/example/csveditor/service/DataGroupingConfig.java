package com.example.csveditor.service;

/**
 * JSONから読み込むデータグループ判定設定。
 */
public class DataGroupingConfig {
    private String activePattern;
    private Patterns patterns;

    public String getActivePattern() {
        return activePattern;
    }

    public void setActivePattern(String activePattern) {
        this.activePattern = activePattern;
    }

    public Patterns getPatterns() {
        return patterns;
    }

    public void setPatterns(Patterns patterns) {
        this.patterns = patterns;
    }

    public static DataGroupingConfig defaultConfig() {
        DataGroupingConfig config = new DataGroupingConfig();
        config.activePattern = "pathSegmentIndex";
        config.patterns = new Patterns();
        config.patterns.pathSegmentPattern = new PathSegmentPattern();
        config.patterns.pathSegmentPattern.regex = ".*_.*";
        config.patterns.pathSegmentPattern.searchTarget = "directoriesOnly";
        config.patterns.pathSegmentPattern.fallback = "firstDirectory";
        config.patterns.pathSegmentIndex = new PathSegmentIndex();
        config.patterns.pathSegmentIndex.index = 1;
        config.patterns.pathSegmentIndex.fallback = "firstDirectory";
        config.patterns.parentDirectory = new ParentDirectory();
        config.patterns.parentDirectory.levelsUpFromFile = 2;
        config.patterns.parentDirectory.fallback = "firstDirectory";
        return config;
    }

    public static class Patterns {
        private PathSegmentPattern pathSegmentPattern;
        private PathSegmentIndex pathSegmentIndex;
        private ParentDirectory parentDirectory;

        public PathSegmentPattern getPathSegmentPattern() {
            return pathSegmentPattern;
        }

        public void setPathSegmentPattern(PathSegmentPattern pathSegmentPattern) {
            this.pathSegmentPattern = pathSegmentPattern;
        }

        public PathSegmentIndex getPathSegmentIndex() {
            return pathSegmentIndex;
        }

        public void setPathSegmentIndex(PathSegmentIndex pathSegmentIndex) {
            this.pathSegmentIndex = pathSegmentIndex;
        }

        public ParentDirectory getParentDirectory() {
            return parentDirectory;
        }

        public void setParentDirectory(ParentDirectory parentDirectory) {
            this.parentDirectory = parentDirectory;
        }
    }

    public static class PathSegmentPattern {
        private String regex;
        private String searchTarget;
        private String fallback;

        public String getRegex() {
            return regex;
        }

        public void setRegex(String regex) {
            this.regex = regex;
        }

        public String getSearchTarget() {
            return searchTarget;
        }

        public void setSearchTarget(String searchTarget) {
            this.searchTarget = searchTarget;
        }

        public String getFallback() {
            return fallback;
        }

        public void setFallback(String fallback) {
            this.fallback = fallback;
        }
    }

    public static class PathSegmentIndex {
        private int index;
        private String fallback;

        public int getIndex() {
            return index;
        }

        public void setIndex(int index) {
            this.index = index;
        }

        public String getFallback() {
            return fallback;
        }

        public void setFallback(String fallback) {
            this.fallback = fallback;
        }
    }

    public static class ParentDirectory {
        private int levelsUpFromFile;
        private String fallback;

        public int getLevelsUpFromFile() {
            return levelsUpFromFile;
        }

        public void setLevelsUpFromFile(int levelsUpFromFile) {
            this.levelsUpFromFile = levelsUpFromFile;
        }

        public String getFallback() {
            return fallback;
        }

        public void setFallback(String fallback) {
            this.fallback = fallback;
        }
    }
}
