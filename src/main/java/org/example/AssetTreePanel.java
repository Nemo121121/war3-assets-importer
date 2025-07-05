package org.example;

import javax.swing.*;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.tree.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.*;

public class AssetTreePanel extends JPanel {
    private final JTree assetTree;
    private final DefaultTreeModel treeModel;
    private boolean controlDown = false;
    private boolean isTreeUpdating = false;

    public AssetTreePanel() {
        setLayout(new java.awt.BorderLayout());

        DefaultMutableTreeNode root = new DefaultMutableTreeNode("Model Assets");
        treeModel = new DefaultTreeModel(root);
        assetTree = new JTree(treeModel);
        JScrollPane treeScrollPane = new JScrollPane(assetTree);
        treeScrollPane.setPreferredSize(new java.awt.Dimension(200, 400));

        add(treeScrollPane, java.awt.BorderLayout.CENTER);

        setupExpandCollapseBehavior();
    }

    public void updateTree(List<String> mdxFiles, List<String> blpFiles) {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("Model Assets");
        root.add(buildFolderTree("MDX Files", mdxFiles));
        root.add(buildFolderTree("BLP Files", blpFiles));
        treeModel.setRoot(root);
        treeModel.reload();
    }

    public JTree getTree() {
        return assetTree;
    }

    private void setupExpandCollapseBehavior() {
        assetTree.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                controlDown = e.isControlDown();
            }

            @Override
            public void keyReleased(KeyEvent e) {
                controlDown = e.isControlDown();
            }
        });

        assetTree.addTreeExpansionListener(new TreeExpansionListener() {
            @Override
            public void treeExpanded(TreeExpansionEvent event) {
                if (controlDown && !isTreeUpdating) {
                    isTreeUpdating = true;
                    SwingUtilities.invokeLater(() -> {
                        expandAllChildren(event.getPath(), true);
                        isTreeUpdating = false;
                    });
                }
            }

            @Override
            public void treeCollapsed(TreeExpansionEvent event) {
                if (controlDown && !isTreeUpdating) {
                    isTreeUpdating = true;
                    SwingUtilities.invokeLater(() -> {
                        expandAllChildren(event.getPath(), false);
                        isTreeUpdating = false;
                    });
                }
            }
        });
    }

    private void expandAllChildren(TreePath path, boolean expand) {
        TreeNode node = (TreeNode) path.getLastPathComponent();
        for (int i = 0; i < node.getChildCount(); i++) {
            TreeNode child = node.getChildAt(i);
            TreePath childPath = path.pathByAddingChild(child);
            expandAllChildren(childPath, expand);
        }
        if (expand) {
            assetTree.expandPath(path);
        } else {
            assetTree.collapsePath(path);
        }
    }

    private DefaultMutableTreeNode buildFolderTree(String label, List<String> filePaths) {
        FolderNode rootNode = new FolderNode(label);

        for (String relPath : filePaths) {
            String[] parts = relPath.split("/");
            FolderNode current = rootNode;

            for (int i = 0; i < parts.length; i++) {
                String part = parts[i];
                boolean isLeaf = (i == parts.length - 1);

                FolderNode child = current.getChild(part);
                if (child == null) {
                    child = new FolderNode(part, isLeaf);
                    current.addChild(child);
                }

                current = child;
                if (isLeaf) child.incrementFileCount();
            }
        }

        rootNode.recalculateCounts();
        return rootNode.toTreeNode();
    }

    private static class FolderNode {
        private final String name;
        private final boolean isFile;
        private final Map<String, FolderNode> children = new LinkedHashMap<>();
        private int fileCount = 0;

        public FolderNode(String name, boolean isFile) {
            this.name = name;
            this.isFile = isFile;
        }

        public FolderNode(String name) {
            this(name, false);
        }

        public void addChild(FolderNode child) {
            children.put(child.name, child);
        }

        public FolderNode getChild(String name) {
            return children.get(name);
        }

        public void incrementFileCount() {
            fileCount++;
        }

        public int recalculateCounts() {
            if (isFile) return fileCount;
            fileCount = 0;
            for (FolderNode child : children.values()) {
                fileCount += child.recalculateCounts();
            }
            return fileCount;
        }

        public DefaultMutableTreeNode toTreeNode() {
            String label = isFile ? name : name + " (" + fileCount + ")";
            DefaultMutableTreeNode node = new DefaultMutableTreeNode(label);
            for (FolderNode child : children.values()) {
                node.add(child.toTreeNode());
            }
            return node;
        }
    }
}
