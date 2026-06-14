package com.example.csveditor.io;

import com.example.csveditor.domain.CsvDocument;
import com.example.csveditor.domain.CsvSchema;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class CsvReader {
    private static final String DEFAULT_LINE_SEPARATOR = "\r\n";

    public CsvDocument read(Path filePath, Path rootPath, Charset charset) throws CsvIOException {
        try {
            List<List<String>> records = readRecords(filePath, charset);
            String lineSeparator = detectLineSeparator(filePath);
            List<String> header = records.isEmpty() ? new ArrayList<String>() : records.get(0);
            List<List<String>> dataRows = new ArrayList<List<String>>();
            int maxColumns = header.size();
            for (int i = 1; i < records.size(); i++) {
                List<String> row = records.get(i);
                dataRows.add(row);
                maxColumns = Math.max(maxColumns, row.size());
            }
            CsvSchema schema = new CsvSchema(header, maxColumns);
            return new CsvDocument(filePath.toAbsolutePath().normalize(), relativize(rootPath, filePath),
                    charset, lineSeparator, schema, dataRows);
        } catch (IOException e) {
            throw new CsvIOException("CSVの読み込みに失敗しました: " + filePath, e);
        }
    }

    private List<List<String>> readRecords(Path filePath, Charset charset) throws IOException {
        List<List<String>> records = new ArrayList<List<String>>();
        CSVFormat format = CSVFormat.DEFAULT;
        try (BufferedReader reader = Files.newBufferedReader(filePath, charset);
             CSVParser parser = new CSVParser(reader, format)) {
            for (CSVRecord record : parser) {
                List<String> row = new ArrayList<String>();
                for (String value : record) {
                    row.add(value == null ? "" : value);
                }
                records.add(row);
            }
        }
        return records;
    }

    private static Path relativize(Path rootPath, Path filePath) {
        if (rootPath == null) {
            return filePath.getFileName();
        }
        Path normalizedRoot = rootPath.toAbsolutePath().normalize();
        Path normalizedFile = filePath.toAbsolutePath().normalize();
        try {
            return normalizedRoot.relativize(normalizedFile);
        } catch (IllegalArgumentException e) {
            return normalizedFile.getFileName();
        }
    }

    private static String detectLineSeparator(Path filePath) throws IOException {
        byte[] bytes = Files.readAllBytes(filePath);
        for (int i = 0; i < bytes.length; i++) {
            if (bytes[i] == '\r') {
                if (i + 1 < bytes.length && bytes[i + 1] == '\n') {
                    return "\r\n";
                }
                return "\r";
            }
            if (bytes[i] == '\n') {
                return "\n";
            }
        }
        return DEFAULT_LINE_SEPARATOR;
    }
}
