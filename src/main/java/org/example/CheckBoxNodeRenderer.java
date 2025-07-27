package org.example;

import javax.swing.*;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreeCellRenderer;
import java.awt.*;

public class CheckBoxNodeRenderer implements TreeCellRenderer {
    private final JCheckBox check = new JCheckBox();
    private final DefaultTreeCellRenderer defaultRenderer = new DefaultTreeCellRenderer();

    @Override
    public Component getTreeCellRendererComponent(JTree tree, Object value,
                                                  boolean sel, boolean expanded,
                                                  boolean leaf, int row, boolean hasFocus) {
        Component c = defaultRenderer.getTreeCellRendererComponent(
                tree, value, sel, expanded, leaf, row, hasFocus);

        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);

        if (value instanceof CheckBoxTreeNode node) {
            check.setSelected(node.isSelected());
            check.setOpaque(false);
            panel.add(check, BorderLayout.WEST);
            panel.add(c, BorderLayout.CENTER);
            return panel;
        }
        // fallback
        return c;
    }
}

