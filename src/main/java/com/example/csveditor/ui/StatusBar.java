package com.example.csveditor.ui;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;

/**
 * Simple status bar for long-running operation feedback.
 */
public class StatusBar extends JPanel {

    private final JLabel messageLabel;

    public StatusBar() {
        super(new BorderLayout());
        this.messageLabel = new JLabel("Ready");
        setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        add(messageLabel, BorderLayout.CENTER);
    }

    public void setMessage(final String message) {
        runOnEdt(new Runnable() {
            @Override
            public void run() {
                messageLabel.setText(message == null || message.trim().length() == 0 ? "Ready" : message);
            }
        });
    }

    public void showLoading(String message) {
        setMessage(message == null ? "Loading..." : message);
    }

    public void showSaving(String message) {
        setMessage(message == null ? "Saving..." : message);
    }

    public void showError(String message) {
        setMessage(message == null ? "Error" : "Error: " + message);
    }

    private static void runOnEdt(Runnable runnable) {
        if (SwingUtilities.isEventDispatchThread()) {
            runnable.run();
        } else {
            SwingUtilities.invokeLater(runnable);
        }
    }
}
