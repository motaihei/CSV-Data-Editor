package com.example.csveditor.app;

import com.example.csveditor.ui.MainFrame;

import javax.swing.SwingUtilities;

/**
 * Application entry point for the CSV editor.
 */
public final class CsvEditorApp {

    private CsvEditorApp() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                new MainFrame().setVisible(true);
            }
        });
    }
}
