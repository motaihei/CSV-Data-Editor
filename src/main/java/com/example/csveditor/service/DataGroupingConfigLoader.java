package com.example.csveditor.service;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * データグループ判定設定をJSONから読み込む。
 */
public final class DataGroupingConfigLoader {
    private static final Path DEFAULT_CONFIG_PATH = Paths.get("config", "data-grouping.json");

    private DataGroupingConfigLoader() {
    }

    public static DataGroupingConfig loadDefault() {
        return load(DEFAULT_CONFIG_PATH);
    }

    public static DataGroupingConfig load(Path configPath) {
        if (configPath == null || !Files.isRegularFile(configPath)) {
            return DataGroupingConfig.defaultConfig();
        }
        try {
            return new ObjectMapper().readValue(configPath.toFile(), DataGroupingConfig.class);
        } catch (IOException ex) {
            return DataGroupingConfig.defaultConfig();
        }
    }
}
