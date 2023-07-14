package org.openpnp.gui;

import com.jgoodies.forms.layout.ColumnSpec;
import com.jgoodies.forms.layout.FormLayout;
import com.jgoodies.forms.layout.FormSpecs;
import com.jgoodies.forms.layout.RowSpec;
import org.jdesktop.beansbinding.AutoBinding;
import org.openpnp.Translations;
import org.openpnp.gui.components.LocationButtonsPanel;
import org.openpnp.gui.support.*;
import org.openpnp.model.Configuration;
import org.openpnp.spi.Machine;
import org.openpnp.spi.NozzleTip;
import org.pmw.tinylog.Logger;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;


public class OtherPanel extends JPanel {

    private JTextField s1XValue;

    private JTextField s1YValue;

    private JTextField s1ZValue;

    private JTextField s2XValue;

    private JTextField s2YValue;

    private JTextField s2ZValue;

    private List<NozzleTip> nozzles;


    public OtherPanel() {

        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));


        panelXYZ = new JPanel();
        panelXYZ.setLayout(new FormLayout(new ColumnSpec[]{
                FormSpecs.RELATED_GAP_COLSPEC,
                FormSpecs.DEFAULT_COLSPEC,
                FormSpecs.RELATED_GAP_COLSPEC,
                ColumnSpec.decode("default"),
                FormSpecs.RELATED_GAP_COLSPEC,
                FormSpecs.DEFAULT_COLSPEC,
                FormSpecs.RELATED_GAP_COLSPEC,
                FormSpecs.DEFAULT_COLSPEC,
                FormSpecs.RELATED_GAP_COLSPEC,
                FormSpecs.DEFAULT_COLSPEC,},
                new RowSpec[]{
                        FormSpecs.RELATED_GAP_ROWSPEC,
                        FormSpecs.DEFAULT_ROWSPEC,
                        FormSpecs.RELATED_GAP_ROWSPEC,
                        FormSpecs.DEFAULT_ROWSPEC,
                        FormSpecs.RELATED_GAP_ROWSPEC,
                        FormSpecs.DEFAULT_ROWSPEC,
                        FormSpecs.RELATED_GAP_ROWSPEC,
                        FormSpecs.DEFAULT_ROWSPEC,}));
        JLabel lblActuatorX = new JLabel("X");
        panelXYZ.add(lblActuatorX, "4, 2, left, default");

        JLabel lblActuatorY = new JLabel("Y");
        panelXYZ.add(lblActuatorY, "6, 2, left, default");

        JLabel lblActuatorZ = new JLabel("Z");
        panelXYZ.add(lblActuatorZ, "8, 2, left, default");

        s1XValue = new JTextField();
        panelXYZ.add(s1XValue, "4, 4");
        s1XValue.setColumns(10);

        s1YValue = new JTextField();
        panelXYZ.add(s1YValue, "6, 4");
        s1YValue.setColumns(10);

        s1ZValue = new JTextField();
        panelXYZ.add(s1ZValue, "8, 4");
        s1ZValue.setColumns(10);


        s2XValue = new JTextField();
        panelXYZ.add(s2XValue, "4, 6");
        s2XValue.setColumns(10);

        s2YValue = new JTextField();
        panelXYZ.add(s2YValue, "6, 6");
        s2YValue.setColumns(10);

        s2ZValue = new JTextField();
        panelXYZ.add(s2ZValue, "8, 6");
        s2ZValue.setColumns(10);


        JLabel lblFeed = new JLabel("1号位坐标");
        panelXYZ.add(lblFeed, "2, 4, right, default");


        JLabel lblPostPick = new JLabel("2号位坐标");
        panelXYZ.add(lblPostPick, "2, 6, right, default");


        LocationButtonsPanel locationButtonsLeft = new LocationButtonsPanel(s1XValue, s1YValue, s1ZValue, null);
        panelXYZ.add(locationButtonsLeft, "10, 4");

        LocationButtonsPanel locationButtonsRight = new LocationButtonsPanel(s2XValue, s2YValue, s2ZValue, null);
        panelXYZ.add(locationButtonsRight, "10, 6");

        JButton btnApply = new JButton(applyAction);
        panelXYZ.add(btnApply, "10, 8");
        //contentPanel.add(panelXYZ);
        //createBindings();

    }

    protected Action applyAction = new AbstractAction(Translations.getString(
            "AbstractConfigurationWizard.Action.Apply")) { //$NON-NLS-1$
        @Override
        public void actionPerformed(ActionEvent arg0) {
            List<NozzleTip> tipList = Configuration.get().getMachine().getNozzleTips();

            Logger.trace("1111");
            //saveToModel();
            //wizardContainer.wizardCompleted(AbstractConfigurationWizard.this);
        }
    };

    private JPanel panelXYZ;

}
