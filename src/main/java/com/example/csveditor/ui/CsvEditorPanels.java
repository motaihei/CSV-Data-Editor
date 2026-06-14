package com.example.csveditor.ui;

import java.awt.Component;
import java.util.Collection;

/**
 * Helper methods for operations spanning multiple CSV editor panels.
 */
public final class CsvEditorPanels {
    private CsvEditorPanels() {
    }

    public static boolean saveAll(Collection<CsvEditorPanel> panels) {
        if (panels == null) {
            return true;
        }
        for (CsvEditorPanel panel : panels) {
            if (panel != null && panel.isDirty() && !panel.saveDocument()) {
                return false;
            }
        }
        return true;
    }

    public static boolean confirmAllForApplicationExit(Component parent, Collection<CsvEditorPanel> panels) {
        if (panels == null) {
            return true;
        }
        for (CsvEditorPanel panel : panels) {
            if (panel != null && !panel.confirmForApplicationExit(parent)) {
                return false;
            }
        }
        return true;
    }
}
