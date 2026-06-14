package com.example.csveditor.ui;

import com.example.csveditor.domain.CsvDocument;

import javax.swing.table.AbstractTableModel;
import java.util.List;

/**
 * JTable model dedicated to exactly one CsvDocument.
 */
public final class CsvTableModel extends AbstractTableModel {
    public interface ChangeListener {
        void beforeDocumentChange();
    }

    private final CsvDocument document;
    private ChangeListener changeListener;

    public CsvTableModel(CsvDocument document) {
        if (document == null) {
            throw new IllegalArgumentException("document must not be null");
        }
        this.document = document;
    }

    public CsvDocument getDocument() {
        return document;
    }

    public void setChangeListener(ChangeListener changeListener) {
        this.changeListener = changeListener;
    }

    @Override
    public int getRowCount() {
        return document.getRowCount();
    }

    @Override
    public int getColumnCount() {
        return document.getColumnCount();
    }

    @Override
    public String getColumnName(int column) {
        return document.getSchema().getDisplayHeader(column);
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        return String.class;
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return true;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        return document.getValueAt(rowIndex, columnIndex);
    }

    @Override
    public void setValueAt(Object value, int rowIndex, int columnIndex) {
        String newValue = value == null ? "" : String.valueOf(value);
        String oldValue = document.getValueAt(rowIndex, columnIndex);
        if (newValue.equals(oldValue)) {
            return;
        }
        notifyBeforeDocumentChange();
        document.setValueAt(rowIndex, columnIndex, newValue);
        fireTableCellUpdated(rowIndex, columnIndex);
    }

    public void removeRows(int[] rowIndexes) {
        if (rowIndexes == null || rowIndexes.length == 0) {
            return;
        }
        notifyBeforeDocumentChange();
        java.util.Arrays.sort(rowIndexes);
        for (int i = rowIndexes.length - 1; i >= 0; i--) {
            document.removeRow(rowIndexes[i]);
        }
        fireTableDataChanged();
    }

    public void insertBlankRow(int rowIndex) {
        notifyBeforeDocumentChange();
        document.insertBlankRow(rowIndex);
        fireTableRowsInserted(rowIndex, rowIndex);
    }

    public void insertRow(int rowIndex, List<String> values) {
        notifyBeforeDocumentChange();
        document.insertRow(rowIndex, values);
        fireTableRowsInserted(rowIndex, rowIndex);
    }

    public void replaceRow(int rowIndex, List<String> values) {
        notifyBeforeDocumentChange();
        document.replaceRow(rowIndex, values);
        fireTableRowsUpdated(rowIndex, rowIndex);
    }

    public List<String> copyRow(int rowIndex) {
        return document.copyRow(rowIndex);
    }

    public void restoreRows(List<List<String>> rows) {
        document.setRows(rows);
        document.markDirty();
        fireTableDataChanged();
    }

    public void refreshAll() {
        fireTableStructureChanged();
    }

    private void notifyBeforeDocumentChange() {
        if (changeListener != null) {
            changeListener.beforeDocumentChange();
        }
    }
}
