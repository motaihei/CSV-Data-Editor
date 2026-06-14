package com.example.csveditor.ui;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

/**
 * Modal settings dialog for application-wide display behavior.
 */
public class SettingsDialog extends JDialog {
    public static final String ROW_CLIPBOARD_DELIMITER_TAB = "tab";
    public static final String ROW_CLIPBOARD_DELIMITER_COMMA = "comma";

    public interface SettingsApplyListener {
        boolean settingsApplied(boolean dataGroupingEnabled, String rowClipboardDelimiterType);
    }

    private final JCheckBox dataGroupingCheckBox;
    private final JComboBox<DelimiterOption> rowClipboardDelimiterComboBox;

    public SettingsDialog(JFrame owner, boolean dataGroupingEnabled,
            String rowClipboardDelimiterType,
            final SettingsApplyListener settingsApplyListener) {
        super(owner, "設定", true);
        this.dataGroupingCheckBox = new JCheckBox("データ単位で枠づけする", dataGroupingEnabled);
        this.rowClipboardDelimiterComboBox = new JComboBox<DelimiterOption>(new DelimiterOption[]{
                new DelimiterOption(ROW_CLIPBOARD_DELIMITER_TAB, "タブ区切り"),
                new DelimiterOption(ROW_CLIPBOARD_DELIMITER_COMMA, "カンマ区切り")
        });
        selectRowClipboardDelimiter(rowClipboardDelimiterType);

        JButton okButton = new JButton("OK");
        JButton cancelButton = new JButton("キャンセル");
        alignControlHeights(rowClipboardDelimiterComboBox, okButton, cancelButton);

        JPanel contentPanel = new JPanel(new BorderLayout(0, 14));
        contentPanel.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));

        JPanel settingsPanel = new JPanel(new GridBagLayout());
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.anchor = GridBagConstraints.WEST;
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.gridwidth = 2;
        constraints.insets = new Insets(0, 0, 0, 0);
        settingsPanel.add(dataGroupingCheckBox, constraints);

        constraints = new GridBagConstraints();
        constraints.anchor = GridBagConstraints.WEST;
        constraints.gridx = 0;
        constraints.gridy = 1;
        constraints.insets = new Insets(10, 0, 0, 0);
        settingsPanel.add(new JLabel("行コピーのクリップボード区切り"), constraints);

        constraints = new GridBagConstraints();
        constraints.anchor = GridBagConstraints.WEST;
        constraints.gridx = 1;
        constraints.gridy = 1;
        constraints.insets = new Insets(10, 10, 0, 0);
        settingsPanel.add(rowClipboardDelimiterComboBox, constraints);
        contentPanel.add(settingsPanel, BorderLayout.NORTH);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        okButton.addActionListener(e -> {
            if (settingsApplyListener != null
                    && !settingsApplyListener.settingsApplied(dataGroupingCheckBox.isSelected(),
                    getSelectedRowClipboardDelimiterType())) {
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

    private static void alignControlHeights(JComponent... components) {
        int height = 0;
        for (JComponent component : components) {
            height = Math.max(height, component.getPreferredSize().height);
        }
        for (JComponent component : components) {
            Dimension preferredSize = component.getPreferredSize();
            Dimension size = new Dimension(preferredSize.width, height);
            component.setPreferredSize(size);
            component.setMinimumSize(size);
        }
    }

    private void selectRowClipboardDelimiter(String rowClipboardDelimiterType) {
        for (int i = 0; i < rowClipboardDelimiterComboBox.getItemCount(); i++) {
            DelimiterOption option = rowClipboardDelimiterComboBox.getItemAt(i);
            if (option.value.equals(rowClipboardDelimiterType)) {
                rowClipboardDelimiterComboBox.setSelectedIndex(i);
                return;
            }
        }
        rowClipboardDelimiterComboBox.setSelectedIndex(0);
    }

    private String getSelectedRowClipboardDelimiterType() {
        DelimiterOption option = (DelimiterOption) rowClipboardDelimiterComboBox.getSelectedItem();
        return option == null ? ROW_CLIPBOARD_DELIMITER_TAB : option.value;
    }

    private static final class DelimiterOption {
        private final String value;
        private final String label;

        private DelimiterOption(String value, String label) {
            this.value = value;
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }
}
