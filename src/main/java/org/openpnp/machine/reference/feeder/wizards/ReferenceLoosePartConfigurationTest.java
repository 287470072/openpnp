package org.openpnp.machine.reference.feeder.wizards;

import org.openpnp.gui.components.LocationButtonsPanel;
import org.openpnp.gui.support.AbstractConfigurationWizard;
import org.openpnp.gui.support.DoubleConverter;
import org.openpnp.gui.support.IntegerConverter;
import org.openpnp.gui.support.LengthConverter;
import org.openpnp.gui.support.LongConverter;
import org.openpnp.gui.support.NamedConverter;
import org.openpnp.machine.reference.feeder.ReferenceLoosePartFeeder;
import org.openpnp.model.Configuration;
import org.openpnp.spi.Actuator;
import org.openpnp.spi.Head;
import org.pmw.tinylog.Logger;

import com.jgoodies.forms.layout.ColumnSpec;
import com.jgoodies.forms.layout.FormLayout;
import com.jgoodies.forms.layout.FormSpecs;
import com.jgoodies.forms.layout.RowSpec;

import javax.swing.*;
import javax.swing.border.TitledBorder;


@SuppressWarnings("serial")
public class ReferenceLoosePartConfigurationTest extends AbstractConfigurationWizard {
	
	private JTextField leftXValue;
	private JTextField leftYValue;
	
	private JTextField rightXValue;
	private JTextField rightYValue;

    private final ReferenceLoosePartFeeder feeder;

    public ReferenceLoosePartConfigurationTest(ReferenceLoosePartFeeder feeder) {
        super();
        this.feeder = feeder;

        JPanel panel = new JPanel();
        panel.setBorder(new TitledBorder(null, "限制范围(基于Top摄像头的坐标)", TitledBorder.LEADING, TitledBorder.TOP,
                null, null));
        contentPanel.add(panel);
        panel.setLayout(new FormLayout(new ColumnSpec[] {
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
        	new RowSpec[] {
        		FormSpecs.RELATED_GAP_ROWSPEC,
        		FormSpecs.DEFAULT_ROWSPEC,
        		FormSpecs.RELATED_GAP_ROWSPEC,
        		FormSpecs.DEFAULT_ROWSPEC,
        		FormSpecs.RELATED_GAP_ROWSPEC,
        		FormSpecs.DEFAULT_ROWSPEC,
        		FormSpecs.RELATED_GAP_ROWSPEC,
        		FormSpecs.DEFAULT_ROWSPEC,}));
        
        
        JLabel lblActuator = new JLabel("X");
        panel.add(lblActuator, "4, 2, left, default");
        
        leftXValue = new JTextField();
        panel.add(leftXValue, "4, 4");
        leftXValue.setColumns(10);
        
        rightXValue = new JTextField();
        panel.add(rightXValue, "4, 6");
        rightXValue.setColumns(10);
        
        JLabel lblActuatorValue = new JLabel("Y");
        panel.add(lblActuatorValue, "6, 2, left, default");
        
        leftYValue = new JTextField();
        panel.add(leftYValue, "6, 4");
        leftYValue.setColumns(10);
        
        rightYValue = new JTextField();
        panel.add(rightYValue, "6, 6");
        rightYValue.setColumns(10);
        
        
        JLabel lblFeed = new JLabel("左下角坐标");
        panel.add(lblFeed, "2, 4, right, default");
        
        
        JLabel lblPostPick = new JLabel("右上角坐标");
        panel.add(lblPostPick, "2, 6, right, default");
        
        
        LocationButtonsPanel locationButtonsLeft = new LocationButtonsPanel(leftXValue, leftYValue, null, null);
        panel.add(locationButtonsLeft, "10, 4");
        
        LocationButtonsPanel locationButtonsRight = new LocationButtonsPanel(rightXValue, rightYValue,  null,  null);
        panel.add(locationButtonsRight, "10, 6");
    }

    @Override
    public void createBindings() {
    	LengthConverter lengthConverter = new LengthConverter();
        IntegerConverter intConverter = new IntegerConverter();
        LongConverter longConverter = new LongConverter();
        DoubleConverter doubleConverter =
                new DoubleConverter(Configuration.get().getLengthDisplayFormat());
        Head head = null;
        try {
            head = Configuration.get().getMachine().getDefaultHead();
        }
        catch (Exception e) {
            Logger.error(e, "Cannot determine default head of machine.");
        }
        actuatorConverter = (new NamedConverter<>(head.getActuators()));
        addWrappedBinding(feeder, "leftXValue", leftXValue, "text",doubleConverter);
        addWrappedBinding(feeder, "leftYValue", leftYValue, "text", doubleConverter);

        addWrappedBinding(feeder, "rightXValue", rightXValue, "text",doubleConverter);
        addWrappedBinding(feeder, "rightYValue", rightYValue, "text", doubleConverter);
    }
    private NamedConverter<Actuator> actuatorConverter;
}