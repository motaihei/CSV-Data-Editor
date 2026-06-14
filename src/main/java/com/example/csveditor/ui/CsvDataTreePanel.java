package com.example.csveditor.ui;

import com.example.csveditor.domain.DataNode;

import javax.swing.BorderFactory;
import javax.swing.JMenuItem;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * Left-side tree panel for CSV files under the selected root folder.
 */
public class CsvDataTreePanel extends JPanel {

    public interface CsvOpenListener {
        void csvOpenRequested(DataNode node);

        void csvOpenRequested(List<DataNode> nodes);
    }

    private final JTree tree;
    private final JPopupMenu folderPopupMenu;
    private final JMenuItem openDescendantCsvItem;
    private CsvOpenListener csvOpenListener;

    public CsvDataTreePanel() {
        super(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        add(new JLabel("CSV data"), BorderLayout.NORTH);

        DefaultMutableTreeNode placeholder = new DefaultMutableTreeNode("Select a root folder");
        this.tree = new JTree(new DefaultTreeModel(placeholder));
        this.tree.setRootVisible(true);
        this.tree.setShowsRootHandles(true);

        this.folderPopupMenu = new JPopupMenu();
        this.openDescendantCsvItem = new JMenuItem("配下を展開してCSVをすべて開く");
        this.openDescendantCsvItem.addActionListener(new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                openDescendantCsvFiles();
            }
        });
        this.folderPopupMenu.add(openDescendantCsvItem);

        installOpenHandlers();
        add(new JScrollPane(tree), BorderLayout.CENTER);
    }

    public void setCsvOpenListener(CsvOpenListener csvOpenListener) {
        this.csvOpenListener = csvOpenListener;
    }

    public void setRootNode(final DataNode rootNode) {
        runOnEdt(new Runnable() {
            @Override
            public void run() {
                DefaultMutableTreeNode swingRoot = toSwingNode(rootNode);
                tree.setModel(new DefaultTreeModel(swingRoot));
                tree.setRootVisible(true);
                tree.expandPath(new TreePath(swingRoot.getPath()));
            }
        });
    }

    public void clear(String message) {
        final String text = message == null ? "Select a root folder" : message;
        runOnEdt(new Runnable() {
            @Override
            public void run() {
                tree.setModel(new DefaultTreeModel(new DefaultMutableTreeNode(text)));
            }
        });
    }

    private void installOpenHandlers() {
        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                showPopupIfNeeded(event);
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                showPopupIfNeeded(event);
            }

            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(event)) {
                    openSelectedCsv();
                }
            }
        });

        tree.getInputMap(JTree.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "openCsv");
        tree.getActionMap().put("openCsv", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                openSelectedCsv();
            }
        });
    }

    private void showPopupIfNeeded(MouseEvent event) {
        if (!event.isPopupTrigger()) {
            return;
        }

        TreePath path = tree.getPathForLocation(event.getX(), event.getY());
        if (path == null) {
            return;
        }

        tree.setSelectionPath(path);
        DataNode node = getDataNode(path);
        if (node == null || node.isCsvFile()) {
            return;
        }

        openDescendantCsvItem.setEnabled(!collectCsvNodes(node).isEmpty());
        folderPopupMenu.show(tree, event.getX(), event.getY());
    }

    private void openSelectedCsv() {
        TreePath selectionPath = tree.getSelectionPath();
        if (selectionPath == null) {
            return;
        }
        Object component = selectionPath.getLastPathComponent();
        if (!(component instanceof DefaultMutableTreeNode)) {
            return;
        }
        Object userObject = ((DefaultMutableTreeNode) component).getUserObject();
        if (userObject instanceof DataNode) {
            DataNode node = (DataNode) userObject;
            if (node.isCsvFile() && csvOpenListener != null) {
                csvOpenListener.csvOpenRequested(node);
            }
        }
    }

    private void openDescendantCsvFiles() {
        TreePath selectionPath = tree.getSelectionPath();
        if (selectionPath == null || csvOpenListener == null) {
            return;
        }

        DataNode node = getDataNode(selectionPath);
        if (node == null || node.isCsvFile()) {
            return;
        }

        List<DataNode> csvNodes = collectCsvNodes(node);
        if (csvNodes.isEmpty()) {
            return;
        }

        expandDescendants(selectionPath);
        csvOpenListener.csvOpenRequested(csvNodes);
    }

    private DataNode getDataNode(TreePath path) {
        Object component = path.getLastPathComponent();
        if (!(component instanceof DefaultMutableTreeNode)) {
            return null;
        }
        Object userObject = ((DefaultMutableTreeNode) component).getUserObject();
        return userObject instanceof DataNode ? (DataNode) userObject : null;
    }

    private static List<DataNode> collectCsvNodes(DataNode node) {
        List<DataNode> csvNodes = new ArrayList<DataNode>();
        collectCsvNodes(node, csvNodes);
        return csvNodes;
    }

    private static void collectCsvNodes(DataNode node, List<DataNode> csvNodes) {
        if (node.isCsvFile()) {
            csvNodes.add(node);
            return;
        }
        for (DataNode child : node.getChildren()) {
            collectCsvNodes(child, csvNodes);
        }
    }

    private void expandDescendants(TreePath path) {
        tree.expandPath(path);
        Object component = path.getLastPathComponent();
        if (!(component instanceof DefaultMutableTreeNode)) {
            return;
        }

        DefaultMutableTreeNode swingNode = (DefaultMutableTreeNode) component;
        for (int i = 0; i < swingNode.getChildCount(); i++) {
            Object child = swingNode.getChildAt(i);
            expandDescendants(path.pathByAddingChild(child));
        }
    }

    private static DefaultMutableTreeNode toSwingNode(DataNode node) {
        DefaultMutableTreeNode swingNode = new DefaultMutableTreeNode(node);
        for (DataNode child : node.getChildren()) {
            swingNode.add(toSwingNode(child));
        }
        return swingNode;
    }

    private static void runOnEdt(Runnable runnable) {
        if (SwingUtilities.isEventDispatchThread()) {
            runnable.run();
        } else {
            SwingUtilities.invokeLater(runnable);
        }
    }
}
