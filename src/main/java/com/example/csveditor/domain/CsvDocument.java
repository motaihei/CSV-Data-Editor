package com.example.csveditor.domain;

import com.example.csveditor.app.AppConfig;

import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * CSV 1ファイル分の独立した文書モデル。
 */
public class CsvDocument {
    private final Path filePath;
    private final Path relativePath;
    private final Charset charset;
    private final String lineSeparator;
    private CsvSchema schema;
    private List<List<String>> rows;
    private boolean dirty;

    public CsvDocument(Path filePath, Path relativePath) {
        this(filePath, relativePath, Charset.forName(AppConfig.DEFAULT_CHARSET_NAME), "\r\n",
                new CsvSchema(Collections.<String>emptyList(), 0), Collections.<List<String>>emptyList());
    }

    public CsvDocument(Path filePath, Path relativePath, Charset charset, String lineSeparator,
                       CsvSchema schema, List<List<String>> rows) {
        this.filePath = filePath;
        this.relativePath = relativePath;
        this.charset = charset;
        this.lineSeparator = lineSeparator == null ? "\r\n" : lineSeparator;
        this.schema = schema;
        this.rows = normalizeRows(rows, schema.getColumnCount());
        this.dirty = false;
    }

    public Path getFilePath() {
        return filePath;
    }

    public Path getRelativePath() {
        return relativePath;
    }

    public String getFileName() {
        Path fileName = filePath.getFileName();
        return fileName == null ? filePath.toString() : fileName.toString();
    }

    public Charset getCharset() {
        return charset;
    }

    public String getLineSeparator() {
        return lineSeparator;
    }

    public CsvSchema getSchema() {
        return schema;
    }

    public void setSchema(CsvSchema schema) {
        this.schema = schema;
        this.rows = normalizeRows(this.rows, schema.getColumnCount());
    }

    public List<List<String>> getRows() {
        return rows;
    }

    public void setRows(List<List<String>> rows) {
        this.rows = normalizeRows(rows, schema.getColumnCount());
    }

    public int getRowCount() {
        return rows.size();
    }

    public int getColumnCount() {
        return schema.getColumnCount();
    }

    public String getValueAt(int rowIndex, int columnIndex) {
        if (rowIndex < 0 || rowIndex >= rows.size()) {
            return "";
        }
        List<String> row = rows.get(rowIndex);
        if (columnIndex < 0 || columnIndex >= row.size()) {
            return "";
        }
        String value = row.get(columnIndex);
        return value == null ? "" : value;
    }

    public void setValueAt(int rowIndex, int columnIndex, String value) {
        if (rowIndex < 0 || rowIndex >= rows.size()) {
            throw new IndexOutOfBoundsException("rowIndex: " + rowIndex);
        }
        if (columnIndex < 0 || columnIndex >= schema.getColumnCount()) {
            throw new IndexOutOfBoundsException("columnIndex: " + columnIndex);
        }
        rows.get(rowIndex).set(columnIndex, value == null ? "" : value);
        markDirty();
    }

    public void removeRow(int rowIndex) {
        if (rowIndex < 0 || rowIndex >= rows.size()) {
            throw new IndexOutOfBoundsException("rowIndex: " + rowIndex);
        }
        rows.remove(rowIndex);
        markDirty();
    }

    public void insertBlankRow(int rowIndex) {
        insertRow(rowIndex, Collections.<String>emptyList());
    }

    public void insertRow(int rowIndex, List<String> values) {
        if (rowIndex < 0 || rowIndex > rows.size()) {
            throw new IndexOutOfBoundsException("rowIndex: " + rowIndex);
        }
        List<List<String>> source = new ArrayList<List<String>>();
        source.add(values == null ? Collections.<String>emptyList() : values);
        rows.add(rowIndex, normalizeRows(source, schema.getColumnCount()).get(0));
        markDirty();
    }

    public void replaceRow(int rowIndex, List<String> values) {
        if (rowIndex < 0 || rowIndex >= rows.size()) {
            throw new IndexOutOfBoundsException("rowIndex: " + rowIndex);
        }
        List<List<String>> source = new ArrayList<List<String>>();
        source.add(values == null ? Collections.<String>emptyList() : values);
        rows.set(rowIndex, normalizeRows(source, schema.getColumnCount()).get(0));
        markDirty();
    }

    public List<String> copyRow(int rowIndex) {
        if (rowIndex < 0 || rowIndex >= rows.size()) {
            throw new IndexOutOfBoundsException("rowIndex: " + rowIndex);
        }
        return new ArrayList<String>(rows.get(rowIndex));
    }

    public boolean isDirty() {
        return dirty;
    }

    public void setDirty(boolean dirty) {
        this.dirty = dirty;
    }

    public void markDirty() {
        this.dirty = true;
    }

    public void clearDirty() {
        this.dirty = false;
    }

    public boolean isModified() {
        return dirty;
    }

    public void setModified(boolean modified) {
        this.dirty = modified;
    }

    public List<List<String>> copyRows() {
        List<List<String>> copy = new ArrayList<List<String>>();
        for (List<String> row : rows) {
            copy.add(new ArrayList<String>(row));
        }
        return Collections.unmodifiableList(copy);
    }

    private static List<List<String>> normalizeRows(List<List<String>> sourceRows, int columnCount) {
        List<List<String>> normalized = new ArrayList<List<String>>();
        if (sourceRows == null) {
            return normalized;
        }
        for (List<String> row : sourceRows) {
            List<String> copy = new ArrayList<String>();
            if (row != null) {
                copy.addAll(row);
            }
            while (copy.size() < columnCount) {
                copy.add("");
            }
            while (copy.size() > columnCount) {
                copy.remove(copy.size() - 1);
            }
            normalized.add(copy);
        }
        return normalized;
    }
}
