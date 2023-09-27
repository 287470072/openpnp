package org.openpnp.gui.support;

import org.openpnp.model.Part;
import org.pmw.tinylog.Logger;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;

public class MyItemEditor implements ComboBoxEditor {
    private JTextField editorComponent = new JTextField();

    @Override
    public Component getEditorComponent() {
        return editorComponent;
    }

    @Override
    public void setItem(Object anObject) {
        //editorComponent.setText("1");
        if (anObject instanceof Part) {
            editorComponent.setText(((Part) anObject).getId());
        }

    }

    @Override
    public Object getItem() {
        return editorComponent.getText();
    }

    @Override
    public void selectAll() {
        editorComponent.selectAll();
    }

    @Override
    public void addActionListener(ActionListener l) {
        editorComponent.addActionListener(l);
    }

    @Override
    public void removeActionListener(ActionListener l) {
        editorComponent.removeActionListener(l);
    }
}
