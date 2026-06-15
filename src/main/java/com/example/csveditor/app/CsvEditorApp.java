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
        if (isShutdownRequest(args)) {
            System.exit(ShutdownRequestServer.requestExistingShutdown());
            return;
        }

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
                final MainFrame frame = new MainFrame();
                installShutdownRequestServer(frame);
                frame.setVisible(true);
            }
        });
    }

    private static boolean isShutdownRequest(String[] args) {
        return args != null && args.length == 1 && ShutdownRequestServer.SHUTDOWN_ARGUMENT.equals(args[0]);
    }

    private static void installShutdownRequestServer(final MainFrame frame) {
        try {
            final ShutdownRequestServer shutdownServer = ShutdownRequestServer.start(new Runnable() {
                @Override
                public void run() {
                    frame.requestCloseWindow();
                }
            });
            Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
                @Override
                public void run() {
                    shutdownServer.closeQuietly();
                }
            }, "csv-editor-shutdown-server-close"));
        } catch (IOException ignored) {
            // The legacy CloseMainWindow launcher fallback can still request shutdown.
        }
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
