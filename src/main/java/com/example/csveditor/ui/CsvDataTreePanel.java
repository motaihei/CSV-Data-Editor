package com.example.csveditor.ui;

import com.example.csveditor.domain.DataNode;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JMenuItem;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Left-side tree panel for CSV files under the selected root folder.
 */
public class CsvDataTreePanel extends JPanel {
    private static final String SYNTHETIC_MULTI_ROOT_NAME = "選択ルート";
    private static final Color ROOT_FOLDER_ICON_COLOR = new Color(36, 143, 154);
    private static final Color ROOT_FOLDER_ICON_BORDER_COLOR = new Color(20, 95, 105);

    public interface CsvOpenListener {
        void csvOpenRequested(DataNode node);

        void csvOpenRequested(List<DataNode> nodes);
    }

    public interface RootFolderRegistrationListener {
        void rootFolderUnregisterRequested(DataNode node);
    }

    private final JTree tree;
    private final JPopupMenu folderPopupMenu;
    private final JPopupMenu csvFilePopupMenu;
    private final JMenuItem expandTreeItem;
    private final JMenuItem openDescendantCsvItem;
    private final JMenuItem copyCsvFileNameItem;
    private final JMenuItem unregisterRootFolderItem;
    private final JSeparator unregisterRootFolderSeparator;
    private final Set<Path> registeredRootPaths;
    private CsvOpenListener csvOpenListener;
    private RootFolderRegistrationListener rootFolderRegistrationListener;

    public CsvDataTreePanel() {
        super(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        add(new JLabel("CSV data"), BorderLayout.NORTH);

        DefaultMutableTreeNode placeholder = new DefaultMutableTreeNode("Select a root folder");
        this.tree = new JTree(new DefaultTreeModel(placeholder));
        this.tree.setRootVisible(true);
        this.tree.setShowsRootHandles(false);
        this.tree.setCellRenderer(new RootAwareTreeCellRenderer());
        this.registeredRootPaths = new HashSet<Path>();

        this.folderPopupMenu = new JPopupMenu();
        this.csvFilePopupMenu = new JPopupMenu();
        this.expandTreeItem = new JMenuItem("ツリーを展開");
        this.expandTreeItem.addActionListener(new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                expandSelectedTreeOnly();
            }
        });
        this.openDescendantCsvItem = new JMenuItem("ツリーを展開してファイルを開く");
        this.openDescendantCsvItem.addActionListener(new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                openDescendantCsvFiles();
            }
        });
        this.copyCsvFileNameItem = new JMenuItem("ファイル名をコピー");
        this.copyCsvFileNameItem.addActionListener(new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                copySelectedCsvFileName();
            }
        });
        this.unregisterRootFolderItem = new JMenuItem("ルートフォルダーの登録を解除");
        this.unregisterRootFolderSeparator = new JSeparator();
        this.unregisterRootFolderItem.addActionListener(new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                unregisterSelectedRootFolder();
            }
        });
        this.folderPopupMenu.add(expandTreeItem);
        this.folderPopupMenu.addSeparator();
        this.folderPopupMenu.add(openDescendantCsvItem);
        this.folderPopupMenu.add(unregisterRootFolderSeparator);
        this.folderPopupMenu.add(unregisterRootFolderItem);
        this.csvFilePopupMenu.add(copyCsvFileNameItem);

        installOpenHandlers();
        add(new JScrollPane(tree), BorderLayout.CENTER);
    }

    public void setCsvOpenListener(CsvOpenListener csvOpenListener) {
        this.csvOpenListener = csvOpenListener;
    }

    public void setRootFolderRegistrationListener(RootFolderRegistrationListener rootFolderRegistrationListener) {
        this.rootFolderRegistrationListener = rootFolderRegistrationListener;
    }

    public void setRegisteredRootPaths(List<Path> rootPaths) {
        registeredRootPaths.clear();
        if (rootPaths == null) {
            return;
        }
        for (Path rootPath : rootPaths) {
            if (rootPath != null) {
                registeredRootPaths.add(rootPath.toAbsolutePath().normalize());
            }
        }
    }

    public void setRootNode(final DataNode rootNode) {
        runOnEdt(new Runnable() {
            @Override
            public void run() {
                DefaultMutableTreeNode swingRoot = toSwingNode(rootNode);
                boolean syntheticMultiRootNode = isSyntheticMultiRootNode(rootNode);
                tree.setModel(new DefaultTreeModel(swingRoot));
                tree.setRootVisible(!syntheticMultiRootNode);
                tree.setShowsRootHandles(false);
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
        if (node == null) {
            return;
        }
        if (node.isCsvFile()) {
            csvFilePopupMenu.show(tree, event.getX(), event.getY());
            return;
        }

        openDescendantCsvItem.setEnabled(!collectCsvNodes(node).isEmpty());
        boolean registeredRootSelection = isRegisteredRootSelection(path, node);
        unregisterRootFolderSeparator.setVisible(registeredRootSelection);
        unregisterRootFolderItem.setVisible(registeredRootSelection);
        folderPopupMenu.show(tree, event.getX(), event.getY());
    }

    private void expandSelectedTreeOnly() {
        TreePath selectionPath = tree.getSelectionPath();
        if (selectionPath == null) {
            return;
        }

        expandDescendants(selectionPath);
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

    private void copySelectedCsvFileName() {
        TreePath selectionPath = tree.getSelectionPath();
        if (selectionPath == null) {
            return;
        }

        DataNode node = getDataNode(selectionPath);
        if (node == null || !node.isCsvFile()) {
            return;
        }

        Path fileName = node.getPath().getFileName();
        String text = fileName == null ? node.getPath().toString() : fileName.toString();
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
    }

    private void unregisterSelectedRootFolder() {
        TreePath selectionPath = tree.getSelectionPath();
        if (selectionPath == null || rootFolderRegistrationListener == null) {
            return;
        }

        DataNode node = getDataNode(selectionPath);
        if (node == null || !isRegisteredRootSelection(selectionPath, node)) {
            return;
        }

        rootFolderRegistrationListener.rootFolderUnregisterRequested(node);
    }

    private boolean isRegisteredRootSelection(TreePath path, DataNode node) {
        if (path == null || node == null || !node.isDirectory()) {
            return false;
        }
        Path nodePath = node.getPath().toAbsolutePath().normalize();
        if (!registeredRootPaths.contains(nodePath)) {
            return false;
        }
        int pathCount = path.getPathCount();
        if (registeredRootPaths.size() <= 1) {
            return pathCount == 1;
        }
        return pathCount == 2;
    }

    private boolean isRegisteredRootTreePath(TreePath path) {
        if (path == null) {
            return false;
        }
        DataNode node = getDataNode(path);
        return isRegisteredRootSelection(path, node);
    }

    private boolean isSyntheticMultiRootNode(DataNode node) {
        return node != null
                && registeredRootPaths.size() > 1
                && SYNTHETIC_MULTI_ROOT_NAME.equals(node.getDisplayName());
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

    private final class RootAwareTreeCellRenderer extends DefaultTreeCellRenderer {
        private final Icon rootFolderIcon = new RootFolderIcon();

        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected,
                boolean expanded, boolean leaf, int row, boolean hasFocus) {
            Component component = super.getTreeCellRendererComponent(
                    tree, value, selected, expanded, leaf, row, hasFocus);
            TreePath path = tree.getPathForRow(row);
            if (isRegisteredRootTreePath(path)) {
                setIcon(rootFolderIcon);
            }
            return component;
        }
    }

    private static final class RootFolderIcon implements Icon {
        private static final int WIDTH = 16;
        private static final int HEIGHT = 16;

        @Override
        public int getIconWidth() {
            return WIDTH;
        }

        @Override
        public int getIconHeight() {
            return HEIGHT;
        }

        @Override
        public void paintIcon(Component component, Graphics graphics, int x, int y) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Polygon tab = new Polygon();
                tab.addPoint(x + 1, y + 5);
                tab.addPoint(x + 5, y + 5);
                tab.addPoint(x + 7, y + 7);
                tab.addPoint(x + 15, y + 7);
                tab.addPoint(x + 15, y + 13);
                tab.addPoint(x + 1, y + 13);
                g.setColor(ROOT_FOLDER_ICON_COLOR);
                g.fillPolygon(tab);
                g.setColor(ROOT_FOLDER_ICON_BORDER_COLOR);
                g.drawPolygon(tab);
                g.drawLine(x + 2, y + 8, x + 14, y + 8);
            } finally {
                g.dispose();
            }
        }
    }
}
