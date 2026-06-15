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
    private boolean transposed;

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

    public boolean isTransposed() {
        return transposed;
    }

    public void setTransposed(boolean transposed) {
        if (this.transposed == transposed) {
            return;
        }
        this.transposed = transposed;
        fireTableStructureChanged();
    }

    @Override
    public int getRowCount() {
        return transposed ? document.getColumnCount() : document.getRowCount();
    }

    @Override
    public int getColumnCount() {
        return transposed ? document.getRowCount() + 1 : document.getColumnCount();
    }

    @Override
    public String getColumnName(int column) {
        if (transposed) {
            return column == 0 ? "項目" : "#" + column;
        }
        return document.getSchema().getDisplayHeader(column);
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        return String.class;
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return !transposed || columnIndex > 0;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        if (transposed) {
            if (columnIndex == 0) {
                return document.getSchema().getDisplayHeader(rowIndex);
            }
            return document.getValueAt(columnIndex - 1, rowIndex);
        }
        return document.getValueAt(rowIndex, columnIndex);
    }

    @Override
    public void setValueAt(Object value, int rowIndex, int columnIndex) {
        if (transposed) {
            if (columnIndex <= 0) {
                return;
            }
            setDocumentValue(value, columnIndex - 1, rowIndex);
            fireTableCellUpdated(rowIndex, columnIndex);
            return;
        }
        setDocumentValue(value, rowIndex, columnIndex);
        fireTableCellUpdated(rowIndex, columnIndex);
    }

    private void setDocumentValue(Object value, int rowIndex, int columnIndex) {
        String newValue = value == null ? "" : String.valueOf(value);
        String oldValue = document.getValueAt(rowIndex, columnIndex);
        if (newValue.equals(oldValue)) {
            return;
        }
        notifyBeforeDocumentChange();
        document.setValueAt(rowIndex, columnIndex, newValue);
    }

    public void removeRows(int[] rowIndexes) {
        if (transposed) {
            return;
        }
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
        if (transposed) {
            return;
        }
        notifyBeforeDocumentChange();
        document.insertBlankRow(rowIndex);
        fireTableRowsInserted(rowIndex, rowIndex);
    }

    public void insertRow(int rowIndex, List<String> values) {
        if (transposed) {
            return;
        }
        notifyBeforeDocumentChange();
        document.insertRow(rowIndex, values);
        fireTableRowsInserted(rowIndex, rowIndex);
    }

    public void insertRows(int rowIndex, List<List<String>> rows) {
        if (transposed) {
            return;
        }
        if (rows == null || rows.isEmpty()) {
            return;
        }
        notifyBeforeDocumentChange();
        for (int i = 0; i < rows.size(); i++) {
            document.insertRow(rowIndex + i, rows.get(i));
        }
        fireTableRowsInserted(rowIndex, rowIndex + rows.size() - 1);
    }

    public void replaceRow(int rowIndex, List<String> values) {
        if (transposed) {
            return;
        }
        notifyBeforeDocumentChange();
        document.replaceRow(rowIndex, values);
        fireTableRowsUpdated(rowIndex, rowIndex);
    }

    public int replaceRows(int rowIndex, List<List<String>> rows) {
        if (transposed) {
            return 0;
        }
        if (rows == null || rows.isEmpty() || rowIndex < 0 || rowIndex >= document.getRowCount()) {
            return 0;
        }
        notifyBeforeDocumentChange();
        int replaced = Math.min(rows.size(), document.getRowCount() - rowIndex);
        for (int i = 0; i < replaced; i++) {
            document.replaceRow(rowIndex + i, rows.get(i));
        }
        fireTableRowsUpdated(rowIndex, rowIndex + replaced - 1);
        return replaced;
    }

    public List<String> copyRow(int rowIndex) {
        if (transposed) {
            java.util.ArrayList<String> row = new java.util.ArrayList<String>();
            for (int column = 0; column < getColumnCount(); column++) {
                Object value = getValueAt(rowIndex, column);
                row.add(value == null ? "" : String.valueOf(value));
            }
            return row;
        }
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
