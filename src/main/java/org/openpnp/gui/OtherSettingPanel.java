package org.openpnp.gui;

import com.jgoodies.forms.layout.ColumnSpec;
import com.jgoodies.forms.layout.FormLayout;
import com.jgoodies.forms.layout.FormSpecs;
import com.jgoodies.forms.layout.RowSpec;
import org.jdesktop.beansbinding.AutoBinding;
import org.jdesktop.beansbinding.BeanProperty;
import org.jdesktop.beansbinding.Bindings;
import org.openpnp.Translations;
import org.openpnp.gui.components.ComponentDecorators;
import org.openpnp.model.Part;

import javax.swing.*;
import javax.swing.border.TitledBorder;

public class OtherSettingPanel  extends JPanel {


    private JPanel pickConditionsPanel;
    private JLabel lblNewLabel;
    private JTextField pickRetryCount;



    private void createUi() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

        pickConditionsPanel = new JPanel();
        pickConditionsPanel.setBorder(new TitledBorder(null, Translations.getString(
                "PartSettingsPanel.pickConditionsPanel.Border.title"), //$NON-NLS-1$
                TitledBorder.LEADING, TitledBorder.TOP, null));
        add(pickConditionsPanel);
        pickConditionsPanel.setLayout(new FormLayout(new ColumnSpec[] {
                FormSpecs.RELATED_GAP_COLSPEC,
                FormSpecs.DEFAULT_COLSPEC,
                FormSpecs.RELATED_GAP_COLSPEC,
                ColumnSpec.decode("default:grow"),},
                new RowSpec[] {
                        FormSpecs.RELATED_GAP_ROWSPEC,
                        FormSpecs.DEFAULT_ROWSPEC,}));

        lblNewLabel = new JLabel(Translations.getString(
                "PartSettingsPanel.pickConditionsPanel.pickRetryCountLabel.text")); //$NON-NLS-1$
        pickConditionsPanel.add(lblNewLabel, "2, 2, right, default");

        pickRetryCount = new JTextField();
        pickConditionsPanel.add(pickRetryCount, "4, 2, left, default");
        pickRetryCount.setColumns(10);
    }

    protected void initDataBindings() {
    }
}
