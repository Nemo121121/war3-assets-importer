package org.example;

import javax.swing.tree.DefaultMutableTreeNode;

public class CheckBoxTreeNode extends DefaultMutableTreeNode {
    private boolean selected;

    public CheckBoxTreeNode(Object userObject, boolean selected) {
        super(userObject);
        this.selected = selected;
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean sel) {
        selected = sel;
    }
}

