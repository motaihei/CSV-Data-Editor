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
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
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
        boolean settingsApplied(boolean dataGroupingEnabled, String rowClipboardDelimiterType,
                int autoCollapseRowThreshold, int dataGroupPathSegmentLevel);
    }

    private final JCheckBox dataGroupingCheckBox;
    private final JComboBox<DelimiterOption> rowClipboardDelimiterComboBox;
    private final JSpinner autoCollapseRowThresholdSpinner;
    private final JSpinner dataGroupPathSegmentLevelSpinner;

    public SettingsDialog(JFrame owner, boolean dataGroupingEnabled,
            String rowClipboardDelimiterType,
            int autoCollapseRowThreshold,
            int dataGroupPathSegmentLevel,
            final SettingsApplyListener settingsApplyListener) {
        super(owner, "設定", true);
        this.dataGroupingCheckBox = new JCheckBox("データ単位で枠づけする", dataGroupingEnabled);
        this.rowClipboardDelimiterComboBox = new JComboBox<DelimiterOption>(new DelimiterOption[]{
                new DelimiterOption(ROW_CLIPBOARD_DELIMITER_TAB, "タブ区切り"),
                new DelimiterOption(ROW_CLIPBOARD_DELIMITER_COMMA, "カンマ区切り")
        });
        this.autoCollapseRowThresholdSpinner = new JSpinner(
                new SpinnerNumberModel(Math.max(0, autoCollapseRowThreshold), 0, 1000000, 1));
        this.dataGroupPathSegmentLevelSpinner = new JSpinner(
                new SpinnerNumberModel(Math.max(1, dataGroupPathSegmentLevel), 1, 100, 1));
        selectRowClipboardDelimiter(rowClipboardDelimiterType);

        JButton okButton = new JButton("OK");
        JButton cancelButton = new JButton("キャンセル");
        alignControlHeights(rowClipboardDelimiterComboBox, autoCollapseRowThresholdSpinner,
                dataGroupPathSegmentLevelSpinner, okButton, cancelButton);

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
        settingsPanel.add(new JLabel("データ単位階層"), constraints);

        JPanel dataGroupLevelPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        dataGroupLevelPanel.add(dataGroupPathSegmentLevelSpinner);
        dataGroupLevelPanel.add(new JLabel("階層目。ルート直下は1。"));
        constraints = new GridBagConstraints();
        constraints.anchor = GridBagConstraints.WEST;
        constraints.gridx = 1;
        constraints.gridy = 1;
        constraints.insets = new Insets(10, 10, 0, 0);
        settingsPanel.add(dataGroupLevelPanel, constraints);

        constraints = new GridBagConstraints();
        constraints.anchor = GridBagConstraints.WEST;
        constraints.gridx = 0;
        constraints.gridy = 2;
        constraints.insets = new Insets(10, 0, 0, 0);
        settingsPanel.add(new JLabel("行コピーのクリップボード区切り"), constraints);

        constraints = new GridBagConstraints();
        constraints.anchor = GridBagConstraints.WEST;
        constraints.gridx = 1;
        constraints.gridy = 2;
        constraints.insets = new Insets(10, 10, 0, 0);
        settingsPanel.add(rowClipboardDelimiterComboBox, constraints);

        constraints = new GridBagConstraints();
        constraints.anchor = GridBagConstraints.WEST;
        constraints.gridx = 0;
        constraints.gridy = 3;
        constraints.insets = new Insets(10, 0, 0, 0);
        settingsPanel.add(new JLabel("自動折りたたみ行数"), constraints);

        JPanel thresholdPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        thresholdPanel.add(autoCollapseRowThresholdSpinner);
        thresholdPanel.add(new JLabel("行以上。0で無効。"));
        constraints = new GridBagConstraints();
        constraints.anchor = GridBagConstraints.WEST;
        constraints.gridx = 1;
        constraints.gridy = 3;
        constraints.insets = new Insets(10, 10, 0, 0);
        settingsPanel.add(thresholdPanel, constraints);
        contentPanel.add(settingsPanel, BorderLayout.NORTH);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        okButton.addActionListener(e -> {
            if (settingsApplyListener != null
                    && !settingsApplyListener.settingsApplied(dataGroupingCheckBox.isSelected(),
                    getSelectedRowClipboardDelimiterType(),
                    getAutoCollapseRowThreshold(),
                    getDataGroupPathSegmentLevel())) {
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

    private int getAutoCollapseRowThreshold() {
        Object value = autoCollapseRowThresholdSpinner.getValue();
        if (value instanceof Number) {
            return Math.max(0, ((Number) value).intValue());
        }
        return 0;
    }

    private int getDataGroupPathSegmentLevel() {
        Object value = dataGroupPathSegmentLevelSpinner.getValue();
        if (value instanceof Number) {
            return Math.max(1, ((Number) value).intValue());
        }
        return 1;
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
