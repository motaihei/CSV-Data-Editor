package com.example.csveditor.ui;

import com.example.csveditor.domain.DataNode;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
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

/**
 * Left-side tree panel for CSV files under the selected root folder.
 */
public class CsvDataTreePanel extends JPanel {

    public interface CsvOpenListener {
        void csvOpenRequested(DataNode node);
    }

    private final JTree tree;
    private CsvOpenListener csvOpenListener;

    public CsvDataTreePanel() {
        super(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        add(new JLabel("CSV data"), BorderLayout.NORTH);

        DefaultMutableTreeNode placeholder = new DefaultMutableTreeNode("Select a root folder");
        this.tree = new JTree(new DefaultTreeModel(placeholder));
        this.tree.setRootVisible(true);
        this.tree.setShowsRootHandles(true);
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
