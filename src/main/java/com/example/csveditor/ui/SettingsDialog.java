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
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * Modal settings dialog for application-wide display behavior.
 */
public class SettingsDialog extends JDialog {
    public static final String ROW_CLIPBOARD_DELIMITER_TAB = "tab";
    public static final String ROW_CLIPBOARD_DELIMITER_COMMA = "comma";
    private static final String DATA_GROUPING_CHECKBOX_TEXT = "データ単位で枠を表示する";
    private static final String DATA_GROUP_LEVEL_LABEL = "データ単位階層";
    private static final String DATA_GROUP_LEVEL_DESCRIPTION = "階層目。ルート直下は1。";
    private static final String ROW_DELIMITER_LABEL = "行コピー時の区切り文字";
    private static final String AUTO_COLLAPSE_THRESHOLD_LABEL = "自動折りたたみ行数";
    private static final String AUTO_COLLAPSE_THRESHOLD_DESCRIPTION = "行以上。0で無効。";

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
        this.dataGroupingCheckBox = new JCheckBox(DATA_GROUPING_CHECKBOX_TEXT, dataGroupingEnabled);
        this.rowClipboardDelimiterComboBox = new JComboBox<DelimiterOption>(new DelimiterOption[]{
                new DelimiterOption(ROW_CLIPBOARD_DELIMITER_TAB, "タブ区切り"),
                new DelimiterOption(ROW_CLIPBOARD_DELIMITER_COMMA, "カンマ区切り")
        });
        this.autoCollapseRowThresholdSpinner = new JSpinner(
                new SpinnerNumberModel(Math.max(0, autoCollapseRowThreshold), 0, 1000000, 1));
        this.dataGroupPathSegmentLevelSpinner = new JSpinner(
                new SpinnerNumberModel(Math.max(1, dataGroupPathSegmentLevel), 1, 100, 1));
        selectRowClipboardDelimiter(rowClipboardDelimiterType);

        setControlWidth(rowClipboardDelimiterComboBox, 164);
        setControlWidth(autoCollapseRowThresholdSpinner, 92);
        setControlWidth(dataGroupPathSegmentLevelSpinner, 92);

        JButton okButton = new JButton("OK");
        JButton cancelButton = new JButton("キャンセル");
        alignControlHeights(rowClipboardDelimiterComboBox, autoCollapseRowThresholdSpinner,
                dataGroupPathSegmentLevelSpinner);
        setUniformButtonSize(okButton, cancelButton);

        JPanel contentPanel = new JPanel(new BorderLayout(0, 18));
        contentPanel.setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));

        JPanel settingsPanel = new JPanel(new GridBagLayout());
        addOptionRow(settingsPanel, 0, dataGroupingCheckBox);
        addFormRow(settingsPanel, 1, DATA_GROUP_LEVEL_LABEL, dataGroupPathSegmentLevelSpinner,
                DATA_GROUP_LEVEL_DESCRIPTION, 10);
        addFormRow(settingsPanel, 2, ROW_DELIMITER_LABEL, rowClipboardDelimiterComboBox, "", 10);
        addFormRow(settingsPanel, 3, AUTO_COLLAPSE_THRESHOLD_LABEL, autoCollapseRowThresholdSpinner,
                AUTO_COLLAPSE_THRESHOLD_DESCRIPTION, 0);
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
        getRootPane().setDefaultButton(okButton);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                okButton.requestFocusInWindow();
            }
        });
        pack();
        setResizable(false);
        setLocationRelativeTo(owner);
    }

    private static void addOptionRow(JPanel panel, int row, JComponent component) {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.anchor = GridBagConstraints.WEST;
        constraints.gridx = 0;
        constraints.gridy = row;
        constraints.gridwidth = 3;
        constraints.insets = new Insets(0, 0, 16, 0);
        panel.add(component, constraints);
    }

    private static void addFormRow(JPanel panel, int row, String labelText,
            JComponent inputComponent, String descriptionText, int bottomInset) {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.anchor = GridBagConstraints.WEST;
        constraints.gridx = 0;
        constraints.gridy = row;
        constraints.insets = new Insets(0, 0, bottomInset, 0);
        panel.add(new JLabel(labelText), constraints);

        constraints = new GridBagConstraints();
        constraints.anchor = GridBagConstraints.WEST;
        constraints.gridx = 1;
        constraints.gridy = row;
        constraints.insets = new Insets(0, 16, bottomInset, 0);
        panel.add(inputComponent, constraints);

        constraints = new GridBagConstraints();
        constraints.anchor = GridBagConstraints.WEST;
        constraints.gridx = 2;
        constraints.gridy = row;
        constraints.weightx = 1.0;
        constraints.insets = new Insets(0, 18, bottomInset, 0);
        panel.add(new JLabel(descriptionText), constraints);
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

    private static void setControlWidth(JComponent component, int width) {
        Dimension preferredSize = component.getPreferredSize();
        Dimension size = new Dimension(width, preferredSize.height);
        component.setPreferredSize(size);
        component.setMinimumSize(size);
    }

    private static void setUniformButtonSize(JButton... buttons) {
        int width = 0;
        int height = 0;
        for (JButton button : buttons) {
            Dimension preferredSize = button.getPreferredSize();
            width = Math.max(width, preferredSize.width);
            height = Math.max(height, preferredSize.height);
        }
        Dimension size = new Dimension(width, height);
        for (JButton button : buttons) {
            button.setPreferredSize(size);
            button.setMinimumSize(size);
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
