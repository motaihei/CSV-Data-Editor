package com.example.csveditor.app;

import com.example.csveditor.ui.MainFrame;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.io.IOException;

/**
 * Application entry point for the CSV editor.
 */
public final class CsvEditorApp {

    private CsvEditorApp() {
    }

    public static void main(String[] args) {
        final SingleInstanceLock instanceLock;
        try {
            instanceLock = SingleInstanceLock.acquire();
        } catch (IOException exception) {
            showStartupError(exception);
            return;
        }
        if (instanceLock == null) {
            showAlreadyRunningMessage();
            return;
        }
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                instanceLock.closeQuietly();
            }
        }, "csv-editor-lock-release"));

        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                new MainFrame().setVisible(true);
            }
        });
    }

    private static void showAlreadyRunningMessage() {
        JOptionPane.showMessageDialog(null,
                "すでに起動しています。",
                "CSV Data Editor",
                JOptionPane.INFORMATION_MESSAGE);
    }

    private static void showStartupError(IOException exception) {
        JOptionPane.showMessageDialog(null,
                "起動状態の確認に失敗しました。\n" + exception.getMessage(),
                "CSV Data Editor",
                JOptionPane.ERROR_MESSAGE);
    }
}
