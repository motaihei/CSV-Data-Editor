package com.example.csveditor.ui;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;

/**
 * Modal settings dialog for application-wide display behavior.
 */
public class SettingsDialog extends JDialog {
    public interface SettingsApplyListener {
        boolean settingsApplied(boolean dataGroupingEnabled);
    }

    private final JCheckBox dataGroupingCheckBox;

    public SettingsDialog(JFrame owner, boolean dataGroupingEnabled,
            final SettingsApplyListener settingsApplyListener) {
        super(owner, "設定", true);
        this.dataGroupingCheckBox = new JCheckBox("データ単位で枠づけする", dataGroupingEnabled);

        JPanel contentPanel = new JPanel(new BorderLayout(0, 12));
        contentPanel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        contentPanel.add(dataGroupingCheckBox, BorderLayout.NORTH);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        JButton okButton = new JButton("OK");
        JButton cancelButton = new JButton("キャンセル");
        okButton.addActionListener(e -> {
            if (settingsApplyListener != null
                    && !settingsApplyListener.settingsApplied(dataGroupingCheckBox.isSelected())) {
                return;
            }
            dispose();
        });
        cancelButton.addActionListener(e -> dispose());
        buttonPanel.add(okButton);
        buttonPanel.add(cancelButton);
        contentPanel.add(buttonPanel, BorderLayout.SOUTH);

        setContentPane(contentPanel);
        pack();
        setResizable(false);
        setLocationRelativeTo(owner);
    }
}
