package org.example;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeCellEditor;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.EventObject;

public class CheckBoxNodeEditor extends AbstractCellEditor
        implements TreeCellEditor {
    private final CheckBoxNodeRenderer renderer = new CheckBoxNodeRenderer();
    private JTree tree;
    private CheckBoxTreeNode currentNode;

    @Override
    public Component getTreeCellEditorComponent(JTree tree, Object value,
                                                boolean sel, boolean expanded,
                                                boolean leaf, int row) {
        this.tree = tree;
        if (value instanceof CheckBoxTreeNode node) {
            currentNode = node;
            return renderer.getTreeCellRendererComponent(
                    tree, value, true, expanded, leaf, row, true);
        }
        return null;
    }

    @Override
    public Object getCellEditorValue() {
        return currentNode;
    }

    @Override
    public boolean isCellEditable(EventObject e) {
        if (e instanceof MouseEvent me && me.getSource() instanceof JTree tree) {
            TreePath path = tree.getPathForLocation(me.getX(), me.getY());
            if (path == null) return false;
            return path.getLastPathComponent() instanceof CheckBoxTreeNode;
        }
        return false;
    }

    @Override
    public boolean stopCellEditing() {
        // Toggle the selected state
        boolean newVal = !currentNode.isSelected();
        setSubtreeSelection(currentNode, newVal);

        // Tell Swing that *every* node in this subtree has new data
        DefaultTreeModel model = (DefaultTreeModel) tree.getModel();
        repaintSubtree(model, currentNode);

        return super.stopCellEditing();
    }

    /**
     * Recursively fires nodeChanged() so Swing will repaint each checkbox node.
     */
    private void repaintSubtree(DefaultTreeModel model, CheckBoxTreeNode node) {
        model.nodeChanged(node);
        for (int i = 0; i < node.getChildCount(); i++) {
            CheckBoxTreeNode child = (CheckBoxTreeNode) node.getChildAt(i);
            repaintSubtree(model, child);
        }
    }

    private void setSubtreeSelection(CheckBoxTreeNode node, boolean sel) {
        node.setSelected(sel);
        for (int i = 0; i < node.getChildCount(); i++) {
            CheckBoxTreeNode child = (CheckBoxTreeNode) node.getChildAt(i);
            setSubtreeSelection(child, sel);
        }
    }
}

