package com.example.csveditor.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CsvSchema {
    private final List<String> headerValues;
    private final List<String> displayColumnNames;
    private final int columnCount;

    public CsvSchema(List<String> headerValues, int columnCount) {
        this.headerValues = immutableCopy(pad(headerValues, columnCount));
        this.columnCount = columnCount;
        this.displayColumnNames = immutableCopy(createDisplayColumnNames(headerValues, columnCount));
    }

    public List<String> getHeaderValues() {
        return headerValues;
    }

    public List<String> getDisplayColumnNames() {
        return displayColumnNames;
    }

    public String getDisplayHeader(int column) {
        if (column < 0 || column >= displayColumnNames.size()) {
            return "";
        }
        return displayColumnNames.get(column);
    }

    public int getColumnCount() {
        return columnCount;
    }

    private static List<String> createDisplayColumnNames(List<String> originalHeaders, int columnCount) {
        List<String> names = new ArrayList<String>();
        Map<String, Integer> counts = new LinkedHashMap<String, Integer>();
        for (int i = 0; i < columnCount; i++) {
            String raw = i < originalHeaders.size() ? originalHeaders.get(i) : "";
            String base = raw == null || raw.length() == 0 ? "Column " + (i + 1) : raw;
            Integer previous = counts.get(base);
            int current = previous == null ? 1 : previous.intValue() + 1;
            counts.put(base, Integer.valueOf(current));
            names.add(current == 1 ? base : base + " (" + current + ")");
        }
        return names;
    }

    private static List<String> pad(List<String> values, int size) {
        List<String> padded = new ArrayList<String>();
        if (values != null) {
            padded.addAll(values);
        }
        while (padded.size() < size) {
            padded.add("");
        }
        return padded;
    }

    private static List<String> immutableCopy(List<String> values) {
        return Collections.unmodifiableList(new ArrayList<String>(values));
    }
}
