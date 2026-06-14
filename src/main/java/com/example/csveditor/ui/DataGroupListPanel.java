package com.example.csveditor.ui;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

/**
 * Middle-side list panel for currently open CSV data groups.
 */
public class DataGroupListPanel extends JPanel {
    public interface GroupSelectionListener {
        void groupSelected(String groupKey);
    }

    public interface GroupCloseListener {
        void groupCloseRequested(String groupKey);
    }

    private final DefaultListModel<String> listModel;
    private final JList<String> groupList;
    private final JPopupMenu listPopupMenu;
    private final JMenuItem closeGroupItem;
    private GroupSelectionListener groupSelectionListener;
    private GroupCloseListener groupCloseListener;
    private boolean updating;

    public DataGroupListPanel() {
        super(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        setPreferredSize(new Dimension(170, 120));

        add(new JLabel("Open data"), BorderLayout.NORTH);

        this.listModel = new DefaultListModel<String>();
        this.groupList = new JList<String>(listModel);
        this.groupList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        this.groupList.setFixedCellHeight(24);

        this.listPopupMenu = new JPopupMenu();
        this.closeGroupItem = new JMenuItem("このデータ単位を閉じる");
        this.closeGroupItem.addActionListener(new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                closeSelectedGroup();
            }
        });
        this.listPopupMenu.add(closeGroupItem);

        this.groupList.addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent event) {
                if (!event.getValueIsAdjusting() && !updating && groupSelectionListener != null) {
                    String selectedValue = groupList.getSelectedValue();
                    if (selectedValue != null) {
                        groupSelectionListener.groupSelected(selectedValue);
                    }
                }
            }
        });
        this.groupList.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                showPopupIfNeeded(event);
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                showPopupIfNeeded(event);
            }
        });
        add(new JScrollPane(groupList), BorderLayout.CENTER);
    }

    public void setGroupSelectionListener(GroupSelectionListener groupSelectionListener) {
        this.groupSelectionListener = groupSelectionListener;
    }

    public void setGroupCloseListener(GroupCloseListener groupCloseListener) {
        this.groupCloseListener = groupCloseListener;
    }

    public void setGroupKeys(final List<String> groupKeys) {
        runOnEdt(new Runnable() {
            @Override
            public void run() {
                String selectedValue = groupList.getSelectedValue();
                updating = true;
                try {
                    listModel.clear();
                    if (groupKeys != null) {
                        for (String groupKey : groupKeys) {
                            listModel.addElement(groupKey);
                        }
                    }
                    if (selectedValue != null && listModel.contains(selectedValue)) {
                        groupList.setSelectedValue(selectedValue, false);
                    } else {
                        groupList.clearSelection();
                    }
                } finally {
                    updating = false;
                }
            }
        });
    }

    private void showPopupIfNeeded(MouseEvent event) {
        if (!event.isPopupTrigger()) {
            return;
        }
        int index = groupList.locationToIndex(event.getPoint());
        if (index < 0 || !groupList.getCellBounds(index, index).contains(event.getPoint())) {
            return;
        }
        groupList.setSelectedIndex(index);
        closeGroupItem.setEnabled(groupCloseListener != null);
        listPopupMenu.show(groupList, event.getX(), event.getY());
    }

    private void closeSelectedGroup() {
        if (groupCloseListener == null) {
            return;
        }
        String selectedValue = groupList.getSelectedValue();
        if (selectedValue != null) {
            groupCloseListener.groupCloseRequested(selectedValue);
        }
    }

    private static void runOnEdt(Runnable runnable) {
        if (SwingUtilities.isEventDispatchThread()) {
            runnable.run();
        } else {
            SwingUtilities.invokeLater(runnable);
        }
    }
}
