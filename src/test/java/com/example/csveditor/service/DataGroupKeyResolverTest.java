package com.example.csveditor.service;

import org.junit.Test;

import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;

public class DataGroupKeyResolverTest {

    @Test
    public void defaultConfigUsesSecondDirectoryFromRoot() {
        DataGroupKeyResolver resolver = new DataGroupKeyResolver(DataGroupingConfig.defaultConfig());

        String groupKey = resolver.resolve(Paths.get("Data01", "Data01_01", "sample002", "endpoints.csv"));

        assertEquals("Data01_01", groupKey);
    }

    @Test
    public void pathSegmentPatternUsesConfiguredRegex() {
        DataGroupingConfig config = DataGroupingConfig.defaultConfig();
        config.setActivePattern("pathSegmentPattern");
        DataGroupKeyResolver resolver = new DataGroupKeyResolver(config);

        String groupKey = resolver.resolve(Paths.get("Data01", "Data01_01", "sample002", "endpoints.csv"));

        assertEquals("Data01_01", groupKey);
    }

    @Test
    public void parentDirectoryUsesConfiguredLevelsUpFromFile() {
        DataGroupingConfig config = DataGroupingConfig.defaultConfig();
        config.setActivePattern("parentDirectory");
        DataGroupKeyResolver resolver = new DataGroupKeyResolver(config);

        String groupKey = resolver.resolve(Paths.get("Data01", "Data01_01", "sample002", "endpoints.csv"));

        assertEquals("Data01_01", groupKey);
    }

    @Test
    public void invalidActivePatternFallsBackToLegacyUnderscoreRule() {
        DataGroupingConfig config = DataGroupingConfig.defaultConfig();
        config.setActivePattern("unknown");
        DataGroupKeyResolver resolver = new DataGroupKeyResolver(config);

        String groupKey = resolver.resolve(Paths.get("Data01", "Data01_01", "sample002", "endpoints.csv"));

        assertEquals("Data01_01", groupKey);
    }
}
