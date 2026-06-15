package com.example.csveditor.ui;

import com.example.csveditor.domain.CsvDocument;
import com.example.csveditor.domain.CsvSchema;
import org.junit.Test;

import java.nio.charset.Charset;
import java.nio.file.Paths;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CsvTableModelTest {

    @Test
    public void removeRowsDeletesSelectedRowsAndMarksDocumentDirty() {
        CsvDocument document = new CsvDocument(
                Paths.get("sample.csv"),
                Paths.get("Data01_01", "sample003", "sample.csv"),
                Charset.forName("windows-31j"),
                "\r\n",
                new CsvSchema(Arrays.asList("id", "name"), 2),
                Arrays.asList(
                        Arrays.asList("1", "one"),
                        Arrays.asList("2", "two"),
                        Arrays.asList("3", "three")));
        CsvTableModel model = new CsvTableModel(document);

        model.removeRows(new int[]{1});

        assertEquals(2, model.getRowCount());
        assertEquals("1", model.getValueAt(0, 0));
        assertEquals("3", model.getValueAt(1, 0));
        assertTrue(document.isDirty());
    }

    @Test
    public void insertBlankRowAddsEmptyRowAtRequestedBoundaryAndMarksDocumentDirty() {
        CsvDocument document = new CsvDocument(
                Paths.get("sample.csv"),
                Paths.get("Data01_01", "sample003", "sample.csv"),
                Charset.forName("windows-31j"),
                "\r\n",
                new CsvSchema(Arrays.asList("id", "name"), 2),
                Arrays.asList(
                        Arrays.asList("1", "one"),
                        Arrays.asList("2", "two")));
        CsvTableModel model = new CsvTableModel(document);

        model.insertBlankRow(1);

        assertEquals(3, model.getRowCount());
        assertEquals("1", model.getValueAt(0, 0));
        assertEquals("", model.getValueAt(1, 0));
        assertEquals("", model.getValueAt(1, 1));
        assertEquals("2", model.getValueAt(2, 0));
        assertTrue(document.isDirty());
    }

    @Test
    public void copyRowAndReplaceRowPastesValuesToRequestedRow() {
        CsvDocument document = new CsvDocument(
                Paths.get("sample.csv"),
                Paths.get("Data01_01", "sample003", "sample.csv"),
                Charset.forName("windows-31j"),
                "\r\n",
                new CsvSchema(Arrays.asList("id", "name"), 2),
                Arrays.asList(
                        Arrays.asList("1", "one"),
                        Arrays.asList("2", "two"),
                        Arrays.asList("3", "three")));
        CsvTableModel model = new CsvTableModel(document);

        model.replaceRow(1, model.copyRow(2));

        assertEquals(3, model.getRowCount());
        assertEquals("1", model.getValueAt(0, 0));
        assertEquals("3", model.getValueAt(1, 0));
        assertEquals("three", model.getValueAt(1, 1));
        assertEquals("3", model.getValueAt(2, 0));
        assertTrue(document.isDirty());
    }

    @Test
    public void transposedViewShowsHeadersAsRowsAndUpdatesOriginalCells() {
        CsvDocument document = new CsvDocument(
                Paths.get("sample.csv"),
                Paths.get("Data01_01", "sample003", "sample.csv"),
                Charset.forName("windows-31j"),
                "\r\n",
                new CsvSchema(Arrays.asList("id", "name"), 2),
                Arrays.asList(
                        Arrays.asList("1", "one"),
                        Arrays.asList("2", "two")));
        CsvTableModel model = new CsvTableModel(document);

        model.setTransposed(true);

        assertEquals(2, model.getRowCount());
        assertEquals(3, model.getColumnCount());
        assertEquals("項目", model.getColumnName(0));
        assertEquals("#1", model.getColumnName(1));
        assertEquals("id", model.getValueAt(0, 0));
        assertEquals("name", model.getValueAt(1, 0));
        assertEquals("one", model.getValueAt(1, 1));
        assertEquals("two", model.getValueAt(1, 2));

        model.setValueAt("updated", 1, 2);

        assertEquals("updated", document.getValueAt(1, 1));
        assertTrue(document.isDirty());
    }
}
