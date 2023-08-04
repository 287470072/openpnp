/*
 * Copyright (C) 2011 Jason von Nieda <jason@vonnieda.org>
 *
 * This file is part of OpenPnP.
 *
 * OpenPnP is free software: you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * OpenPnP is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with OpenPnP. If not, see
 * <http://www.gnu.org/licenses/>.
 *
 * For more information about OpenPnP visit http://openpnp.org
 */

package org.openpnp.gui;

import java.awt.Component;
import java.awt.Container;
import java.awt.FlowLayout;
import java.awt.FocusTraversalPolicy;
import java.awt.Font;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.beans.PropertyChangeListener;
import java.util.*;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import org.json.JSONArray;
import org.json.JSONObject;
import org.openpnp.ConfigurationListener;
import org.openpnp.Translations;
import org.openpnp.gui.components.LocationButtonsPanel;
import org.openpnp.gui.support.*;
import org.openpnp.machine.reference.ReferenceMachine;
import org.openpnp.machine.reference.solutions.VisionSolutions;
import org.openpnp.model.*;
import org.openpnp.model.Motion.MotionOption;
import org.openpnp.spi.*;
import org.openpnp.spi.Nozzle.RotationMode;
import org.openpnp.spi.base.AbstractNozzle;
import org.openpnp.util.*;

import com.jgoodies.forms.layout.ColumnSpec;
import com.jgoodies.forms.layout.FormLayout;
import com.jgoodies.forms.layout.FormSpecs;
import com.jgoodies.forms.layout.RowSpec;
import org.pmw.tinylog.Logger;

/**
 * Contains controls, DROs and status for the machine. Controls: C right / left, X + / -, Y + / -, Z
 * + / -, stop, pause, slider for jog increment DROs: X, Y, Z, C Radio buttons to select mm or inch.
 *
 * @author jason
 */
public class JogControlsPanel extends JPanel {
    private final MachineControlsPanel machineControlsPanel;
    private final Configuration configuration;
    private JPanel panelActuators;
    private JSlider sliderIncrements;
    private JCheckBox boardProtectionOverrideCheck;

    private JTextField s1XValue;

    private JTextField s1YValue;

    private JTextField s1ZValue;

    private JTextField s2XValue;

    private JTextField s2YValue;

    private JTextField s2ZValue;

    private JTextField s3XValue;

    private JTextField s3YValue;

    private JTextField s3ZValue;

    private JTextField s4XValue;

    private JTextField s4YValue;

    private JTextField s4ZValue;

    private JTextField s5XValue;

    private JTextField s5YValue;

    private JTextField s5ZValue;

    private JButton btnApply;
    private JButton btnReset;


    /**
     * Create the panel.
     */
    public JogControlsPanel(Configuration configuration,
                            MachineControlsPanel machineControlsPanel) {
        this.machineControlsPanel = machineControlsPanel;
        this.configuration = configuration;

        createUi();

        configuration.addListener(configurationListener);
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        xPlusAction.setEnabled(enabled);
        xMinusAction.setEnabled(enabled);
        yPlusAction.setEnabled(enabled);
        yMinusAction.setEnabled(enabled);
        zPlusAction.setEnabled(enabled);
        zMinusAction.setEnabled(enabled);
        cPlusAction.setEnabled(enabled);
        cMinusAction.setEnabled(enabled);
        discardAction.setEnabled(enabled);
        safezAction.setEnabled(enabled);
        xyParkAction.setEnabled(enabled);
        zParkAction.setEnabled(enabled);
        cParkAction.setEnabled(enabled);
        for (Component c : panelActuators.getComponents()) {
            c.setEnabled(enabled);
        }
    }

    private void setUnits(LengthUnit units) {
        if (units == LengthUnit.Millimeters) {
            Hashtable<Integer, JLabel> incrementsLabels = new Hashtable<>();
            incrementsLabels.put(1, new JLabel("0.01")); //$NON-NLS-1$
            incrementsLabels.put(2, new JLabel("0.1")); //$NON-NLS-1$
            incrementsLabels.put(3, new JLabel("1.0")); //$NON-NLS-1$
            incrementsLabels.put(4, new JLabel("10")); //$NON-NLS-1$
            incrementsLabels.put(5, new JLabel("100")); //$NON-NLS-1$
            sliderIncrements.setLabelTable(incrementsLabels);
        } else if (units == LengthUnit.Inches) {
            Hashtable<Integer, JLabel> incrementsLabels = new Hashtable<>();
            incrementsLabels.put(1, new JLabel("0.001")); //$NON-NLS-1$
            incrementsLabels.put(2, new JLabel("0.01")); //$NON-NLS-1$
            incrementsLabels.put(3, new JLabel("0.1")); //$NON-NLS-1$
            incrementsLabels.put(4, new JLabel("1.0")); //$NON-NLS-1$
            incrementsLabels.put(5, new JLabel("10.0")); //$NON-NLS-1$
            sliderIncrements.setLabelTable(incrementsLabels);
        } else {
            throw new Error("setUnits() not implemented for " + units); //$NON-NLS-1$
        }
        machineControlsPanel.updateDros();
    }

    public double getJogIncrement() {
        if (configuration.getSystemUnits() == LengthUnit.Millimeters) {
            return 0.01 * Math.pow(10, sliderIncrements.getValue() - 1);
        } else if (configuration.getSystemUnits() == LengthUnit.Inches) {
            return 0.001 * Math.pow(10, sliderIncrements.getValue() - 1);
        } else {
            throw new Error(
                    "getJogIncrement() not implemented for " + configuration.getSystemUnits()); //$NON-NLS-1$
        }
    }

    public boolean getBoardProtectionOverrideEnabled() {
        return boardProtectionOverrideCheck.isSelected();
    }

    private void jog(final int x, final int y, final int z, final int c) {
        UiUtils.submitUiMachineTask(() -> {
            HeadMountable tool = machineControlsPanel.getSelectedTool();
            jogTool(x, y, z, c, tool);
        });
    }

    public void jogTool(final int x, final int y, final int z, final int c, HeadMountable tool)
            throws Exception {
        Location l = tool.getLocation()
                .convertToUnits(Configuration.get()
                        .getSystemUnits());
        double xPos = l.getX();
        double yPos = l.getY();
        double zPos = l.getZ();
        double cPos = l.getRotation();

        double jogIncrement =
                new Length(getJogIncrement(), configuration.getSystemUnits()).getValue();

        if (x > 0) {
            xPos += jogIncrement;
        } else if (x < 0) {
            xPos -= jogIncrement;
        }

        if (y > 0) {
            yPos += jogIncrement;
        } else if (y < 0) {
            yPos -= jogIncrement;
        }

        if (z > 0) {
            zPos += jogIncrement;
        } else if (z < 0) {
            zPos -= jogIncrement;
        }

        if (c > 0) {
            cPos += jogIncrement;
        } else if (c < 0) {
            cPos -= jogIncrement;
        }

        Location targetLocation = new Location(l.getUnits(), xPos, yPos, zPos, cPos);
        if (!this.getBoardProtectionOverrideEnabled()) {
            /* check board location before movement */
            List<BoardLocation> boardLocations = machineControlsPanel.getJobPanel()
                    .getJob()
                    .getBoardLocations();
            for (BoardLocation boardLocation : boardLocations) {
                if (!boardLocation.isEnabled()) {
                    continue;
                }
                boolean safe = nozzleLocationIsSafe(boardLocation.getGlobalLocation(),
                        boardLocation.getBoard()
                                .getDimensions(),
                        targetLocation, new Length(1.0, l.getUnits()));
                if (!safe) {
                    throw new Exception(
                            "Nozzle would crash into board: " + boardLocation.toString() + "\n" + //$NON-NLS-1$ //$NON-NLS-2$
                                    "To disable the board protection go to the \"Safety\" tab in the \"Machine Controls\" panel."); //$NON-NLS-1$
                }
            }
        }

        tool.moveTo(targetLocation, MotionOption.JogMotion);

        MovableUtils.fireTargetedUserAction(tool, true);
    }

    private boolean nozzleLocationIsSafe(Location origin, Location dimension, Location nozzle,
                                         Length safeDistance) {
        double distance = safeDistance.convertToUnits(nozzle.getUnits())
                .getValue();
        Location originConverted = origin.convertToUnits(nozzle.getUnits());
        Location dimensionConverted = dimension.convertToUnits(dimension.getUnits());
        double boardUpperZ = originConverted.getZ();
        boolean containsXY = pointWithinRectangle(originConverted, dimensionConverted, nozzle);
        boolean containsZ = nozzle.getZ() <= (boardUpperZ + distance);
        return !(containsXY && containsZ);
    }

    private boolean pointWithinRectangle(Location origin, Location dimension, Location point) {
        double rotation = Math.toRadians(origin.getRotation());
        double ay = origin.getY() + Math.sin(rotation) * dimension.getX();
        double ax = origin.getX() + Math.cos(rotation) * dimension.getX();
        Location a = new Location(dimension.getUnits(), ax, ay, 0.0, 0.0);

        double cx = origin.getX() - Math.cos(Math.PI / 2 - rotation) * dimension.getY();
        double cy = origin.getY() + Math.sin(Math.PI / 2 - rotation) * dimension.getY();
        Location c = new Location(dimension.getUnits(), cx, cy, 0.0, 0.0);

        double bx = ax + cx - origin.getX();
        double by = ay + cy - origin.getY();
        Location b = new Location(dimension.getUnits(), bx, by, 0.0, 0.0);

        return pointWithinTriangle(origin, b, a, point) || pointWithinTriangle(origin, c, b, point);
    }

    private boolean pointWithinTriangle(Location p1, Location p2, Location p3, Location p) {
        double alpha = ((p2.getY() - p3.getY()) * (p.getX() - p3.getX())
                + (p3.getX() - p2.getX()) * (p.getY() - p3.getY()))
                / ((p2.getY() - p3.getY()) * (p1.getX() - p3.getX())
                + (p3.getX() - p2.getX()) * (p1.getY() - p3.getY()));
        double beta = ((p3.getY() - p1.getY()) * (p.getX() - p3.getX())
                + (p1.getX() - p3.getX()) * (p.getY() - p3.getY()))
                / ((p2.getY() - p3.getY()) * (p1.getX() - p3.getX())
                + (p3.getX() - p2.getX()) * (p1.getY() - p3.getY()));
        double gamma = 1.0 - alpha - beta;

        return (alpha > 0.0) && (beta > 0.0) && (gamma > 0.0);
    }

    private void createUi() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

        setFocusTraversalPolicy(focusPolicy);
        setFocusTraversalPolicyProvider(true);

        JTabbedPane tabbedPane_1 = new JTabbedPane(JTabbedPane.TOP);
        add(tabbedPane_1);

        JPanel panelControls = new JPanel();
        //tabbedPane_1.addTab("Jog", null, panelControls, null); //$NON-NLS-1$
        tabbedPane_1.addTab(Translations.getString("JogControlsPanel.Tab.Jog"), //$NON-NLS-1$
                null, panelControls, null);
        panelControls.setLayout(new FormLayout(
                new ColumnSpec[]{FormSpecs.RELATED_GAP_COLSPEC, FormSpecs.DEFAULT_COLSPEC,
                        FormSpecs.RELATED_GAP_COLSPEC, FormSpecs.DEFAULT_COLSPEC,
                        FormSpecs.RELATED_GAP_COLSPEC, FormSpecs.DEFAULT_COLSPEC,
                        FormSpecs.RELATED_GAP_COLSPEC, FormSpecs.DEFAULT_COLSPEC,
                        FormSpecs.RELATED_GAP_COLSPEC, FormSpecs.DEFAULT_COLSPEC,
                        FormSpecs.RELATED_GAP_COLSPEC, FormSpecs.DEFAULT_COLSPEC,
                        FormSpecs.RELATED_GAP_COLSPEC, FormSpecs.DEFAULT_COLSPEC,
                        FormSpecs.RELATED_GAP_COLSPEC, FormSpecs.DEFAULT_COLSPEC,
                        FormSpecs.RELATED_GAP_COLSPEC, FormSpecs.DEFAULT_COLSPEC,
                        FormSpecs.RELATED_GAP_COLSPEC, FormSpecs.DEFAULT_COLSPEC,
                        FormSpecs.RELATED_GAP_COLSPEC, FormSpecs.DEFAULT_COLSPEC,
                        FormSpecs.RELATED_GAP_COLSPEC, FormSpecs.DEFAULT_COLSPEC,},
                new RowSpec[]{FormSpecs.RELATED_GAP_ROWSPEC, FormSpecs.DEFAULT_ROWSPEC,
                        FormSpecs.RELATED_GAP_ROWSPEC, FormSpecs.DEFAULT_ROWSPEC,
                        FormSpecs.RELATED_GAP_ROWSPEC, FormSpecs.DEFAULT_ROWSPEC,
                        FormSpecs.RELATED_GAP_ROWSPEC, FormSpecs.DEFAULT_ROWSPEC,
                        FormSpecs.RELATED_GAP_ROWSPEC, FormSpecs.DEFAULT_ROWSPEC,
                        FormSpecs.RELATED_GAP_ROWSPEC, FormSpecs.DEFAULT_ROWSPEC,}));

        JButton homeButton = new JButton(machineControlsPanel.homeAction);
        // We set this Icon explicitly as a WindowBuilder helper. WindowBuilder can't find the
        // homeAction referenced above so the icon doesn't render in the viewer. We set it here
        // so the dialog looks right while editing.
        homeButton.setIcon(Icons.home);
        homeButton.setHideActionText(true);
        homeButton.setToolTipText(Translations.getString("JogControlsPanel.homeButton.toolTipText")); //$NON-NLS-1$ //$NON-NLS-2$
        panelControls.add(homeButton, "2, 2"); //$NON-NLS-1$

        JLabel lblXy = new JLabel("X/Y"); //$NON-NLS-1$
        lblXy.setFont(new Font("Lucida Grande", Font.PLAIN, 22)); //$NON-NLS-1$
        lblXy.setHorizontalAlignment(SwingConstants.CENTER);
        panelControls.add(lblXy, "8, 2, fill, default"); //$NON-NLS-1$

        JLabel lblZ = new JLabel("Z"); //$NON-NLS-1$
        lblZ.setHorizontalAlignment(SwingConstants.CENTER);
        lblZ.setFont(new Font("Lucida Grande", Font.PLAIN, 22)); //$NON-NLS-1$
        panelControls.add(lblZ, "14, 2"); //$NON-NLS-1$

        JLabel lblDistance = new JLabel("<html>" + Translations.getString("JogControlsPanel.Label.Distance") + "<br>[" + configuration.getSystemUnits().getShortName() + "/deg]</html>"); //$NON-NLS-1$
        lblDistance.setFont(new Font("Lucida Grande", Font.PLAIN, 10)); //$NON-NLS-1$
        panelControls.add(lblDistance, "18, 2, center, center"); //$NON-NLS-1$

        JLabel lblSpeed = new JLabel("<html>" + Translations.getString("JogControlsPanel.Label.Speed") + "<br>[%]</html>"); //$NON-NLS-1$
        lblSpeed.setFont(new Font("Lucida Grande", Font.PLAIN, 10)); //$NON-NLS-1$
        panelControls.add(lblSpeed, "20, 2, center, center"); //$NON-NLS-1$

        sliderIncrements = new JSlider();
        panelControls.add(sliderIncrements, "18, 3, 1, 10"); //$NON-NLS-1$
        sliderIncrements.setOrientation(SwingConstants.VERTICAL);
        sliderIncrements.setMajorTickSpacing(1);
        sliderIncrements.setValue(1);
        sliderIncrements.setSnapToTicks(true);
        sliderIncrements.setPaintLabels(true);
        sliderIncrements.setMinimum(1);
        sliderIncrements.setMaximum(5);

        JButton yPlusButton = new JButton(yPlusAction);
        yPlusButton.setHideActionText(true);
        panelControls.add(yPlusButton, "8, 4"); //$NON-NLS-1$

        JButton zUpButton = new JButton(zPlusAction);
        zUpButton.setHideActionText(true);
        panelControls.add(zUpButton, "14, 4"); //$NON-NLS-1$

        speedSlider = new JSlider();
        speedSlider.setValue(100);
        speedSlider.setPaintTicks(true);
        speedSlider.setMinorTickSpacing(1);
        speedSlider.setMajorTickSpacing(25);
        speedSlider.setSnapToTicks(true);
        speedSlider.setPaintLabels(true);
        speedSlider.setOrientation(SwingConstants.VERTICAL);
        panelControls.add(speedSlider, "20, 4, 1, 9"); //$NON-NLS-1$
        speedSlider.addChangeListener(new ChangeListener() {
            int oldValue = 100;

            @Override
            public void stateChanged(ChangeEvent e) {
                Machine machine = Configuration.get().getMachine();
                int minSpeedSlider = (int) Math.ceil(machine.getMotionPlanner().getMinimumSpeed() * 100);
                if (speedSlider.getValue() > 0 && speedSlider.getValue() < minSpeedSlider) {
                    if (oldValue > speedSlider.getValue()) {
                        // Snap to zero.
                        speedSlider.setValue(0);
                    } else {
                        // Snap to minium.
                        speedSlider.setValue(minSpeedSlider);
                    }
                }
                oldValue = speedSlider.getValue();
                machine.setSpeed(speedSlider.getValue() * 0.01);
            }
        });

        JButton positionNozzleBtn = new JButton(machineControlsPanel.targetToolAction);
        positionNozzleBtn.setIcon(Icons.centerTool);
        positionNozzleBtn.setHideActionText(true);
        positionNozzleBtn.setToolTipText(Translations.getString("JogControlsPanel.Action.positionSelectedNozzle")); //$NON-NLS-1$
        panelControls.add(positionNozzleBtn, "22, 4"); //$NON-NLS-1$

        JButton buttonStartStop = new JButton(machineControlsPanel.startStopMachineAction);
        buttonStartStop.setIcon(Icons.powerOn);
        panelControls.add(buttonStartStop, "2, 6"); //$NON-NLS-1$
        buttonStartStop.setHideActionText(true);

        JButton xMinusButton = new JButton(xMinusAction);
        xMinusButton.setHideActionText(true);
        panelControls.add(xMinusButton, "6, 6"); //$NON-NLS-1$

        JButton homeXyButton = new JButton(xyParkAction);
        homeXyButton.setHideActionText(true);
        panelControls.add(homeXyButton, "8, 6"); //$NON-NLS-1$

        JButton xPlusButton = new JButton(xPlusAction);
        xPlusButton.setHideActionText(true);
        panelControls.add(xPlusButton, "10, 6"); //$NON-NLS-1$

        JButton homeZButton = new JButton(zParkAction);
        homeZButton.setHideActionText(true);
        panelControls.add(homeZButton, "14, 6"); //$NON-NLS-1$

        JButton yMinusButton = new JButton(yMinusAction);
        yMinusButton.setHideActionText(true);
        panelControls.add(yMinusButton, "8, 8"); //$NON-NLS-1$

        JButton zDownButton = new JButton(zMinusAction);
        zDownButton.setHideActionText(true);
        panelControls.add(zDownButton, "14, 8"); //$NON-NLS-1$

        JButton positionCameraBtn = new JButton(machineControlsPanel.targetCameraAction);
        positionCameraBtn.setIcon(Icons.centerCamera);
        positionCameraBtn.setHideActionText(true);
        positionCameraBtn.setToolTipText(Translations.getString("JogControlsPanel.Action.positionCamera")); //$NON-NLS-1$
        panelControls.add(positionCameraBtn, "22, 8"); //$NON-NLS-1$

        JLabel lblC = new JLabel("C"); //$NON-NLS-1$
        lblC.setHorizontalAlignment(SwingConstants.CENTER);
        lblC.setFont(new Font("Lucida Grande", Font.PLAIN, 22)); //$NON-NLS-1$
        panelControls.add(lblC, "4, 12"); //$NON-NLS-1$

        JButton counterclockwiseButton = new JButton(cPlusAction);
        counterclockwiseButton.setHideActionText(true);
        panelControls.add(counterclockwiseButton, "6, 12"); //$NON-NLS-1$

        JButton homeCButton = new JButton(cParkAction);
        homeCButton.setHideActionText(true);
        panelControls.add(homeCButton, "8, 12"); //$NON-NLS-1$

        JButton clockwiseButton = new JButton(cMinusAction);
        clockwiseButton.setHideActionText(true);
        panelControls.add(clockwiseButton, "10, 12"); //$NON-NLS-1$

        JPanel panelSpecial = new JPanel();
        tabbedPane_1.addTab(Translations.getString("JogControlsPanel.Tab.Special"), null, panelSpecial, null); //$NON-NLS-1$
        FlowLayout flowLayout_1 = (FlowLayout) panelSpecial.getLayout();
        flowLayout_1.setAlignment(FlowLayout.LEFT);

        JButton btnSafeZ = new JButton(safezAction);
        panelSpecial.add(btnSafeZ);

        JButton btnDiscard = new JButton(discardAction);
        panelSpecial.add(btnDiscard);

        JButton btnRecycle = new JButton(recycleAction);
        recycleAction.setEnabled(false);
        btnRecycle.setToolTipText(Translations.getString("JogControlsPanel.btnRecycle.toolTipText")); //$NON-NLS-1$
        btnRecycle.setText(Translations.getString("JogControlsPanel.btnRecycle.text")); //$NON-NLS-1$
        panelSpecial.add(btnRecycle);

        panelActuators = new JPanel();
        tabbedPane_1.addTab(Translations.getString("JogControlsPanel.Tab.Actuators"), //$NON-NLS-1$
                null, panelActuators, null);
        panelActuators.setLayout(new WrapLayout(WrapLayout.LEFT));

        JPanel panelSafety = new JPanel();
        tabbedPane_1.addTab(Translations.getString("JogControlsPanel.Tab.Safety"), //$NON-NLS-1$
                null, panelSafety, null);
        panelSafety.setLayout(new FlowLayout(FlowLayout.LEFT, 5, 5));

        boardProtectionOverrideCheck = new JCheckBox(
                Translations.getString("JogControlsPanel.Label.OverrideBoardProtection")); //$NON-NLS-1$
        boardProtectionOverrideCheck.setToolTipText(
                Translations.getString("JogControlsPanel.Label.OverrideBoardProtection.Description")); //$NON-NLS-1$
        panelSafety.add(boardProtectionOverrideCheck, "1, 1"); //$NON-NLS-1$


        JPanel panelXYZ = new JPanel();

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
        s1XValue.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent documentEvent) {
                btnApply.setEnabled(true);
                btnReset.setEnabled(true);
            }

            @Override
            public void removeUpdate(DocumentEvent documentEvent) {
                btnApply.setEnabled(true);
                btnReset.setEnabled(true);
            }

            @Override
            public void changedUpdate(DocumentEvent documentEvent) {

                btnApply.setEnabled(true);
                btnReset.setEnabled(true);
            }
        });

        s1YValue = new JTextField();
        panelXYZ.add(s1YValue, "6, 4");
        s1YValue.setColumns(10);
        s1YValue.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent documentEvent) {
                btnApply.setEnabled(true);
                btnReset.setEnabled(true);
            }

            @Override
            public void removeUpdate(DocumentEvent documentEvent) {
                btnApply.setEnabled(true);
                btnReset.setEnabled(true);
            }

            @Override
            public void changedUpdate(DocumentEvent documentEvent) {
                btnApply.setEnabled(true);
                btnReset.setEnabled(true);
            }
        });

        s1ZValue = new JTextField();
        panelXYZ.add(s1ZValue, "8, 4");
        s1ZValue.setColumns(10);
        s1ZValue.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent documentEvent) {
                btnApply.setEnabled(true);
                btnReset.setEnabled(true);
            }

            @Override
            public void removeUpdate(DocumentEvent documentEvent) {
                btnApply.setEnabled(true);
                btnReset.setEnabled(true);
            }

            @Override
            public void changedUpdate(DocumentEvent documentEvent) {
                btnApply.setEnabled(true);
                btnReset.setEnabled(true);
            }
        });


        s5XValue = new JTextField();
        panelXYZ.add(s5XValue, "4, 6");
        s5XValue.setColumns(10);
        s5XValue.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent documentEvent) {
                btnApply.setEnabled(true);
                btnReset.setEnabled(true);
            }

            @Override
            public void removeUpdate(DocumentEvent documentEvent) {
                btnApply.setEnabled(true);
                btnReset.setEnabled(true);
            }

            @Override
            public void changedUpdate(DocumentEvent documentEvent) {
                btnApply.setEnabled(true);
                btnReset.setEnabled(true);
            }
        });

        s5YValue = new JTextField();
        panelXYZ.add(s5YValue, "6, 6");
        s5YValue.setColumns(10);

        s5YValue.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent documentEvent) {
                btnApply.setEnabled(true);
                btnReset.setEnabled(true);
            }

            @Override
            public void removeUpdate(DocumentEvent documentEvent) {
                btnApply.setEnabled(true);
                btnReset.setEnabled(true);
            }

            @Override
            public void changedUpdate(DocumentEvent documentEvent) {
                btnApply.setEnabled(true);
                btnReset.setEnabled(true);
            }
        });

        s5ZValue = new JTextField();
        panelXYZ.add(s5ZValue, "8, 6");
        s5ZValue.setColumns(10);

        s5ZValue.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent documentEvent) {
                btnApply.setEnabled(true);
                btnReset.setEnabled(true);
            }

            @Override
            public void removeUpdate(DocumentEvent documentEvent) {
                btnApply.setEnabled(true);
                btnReset.setEnabled(true);
            }

            @Override
            public void changedUpdate(DocumentEvent documentEvent) {
                btnApply.setEnabled(true);
                btnReset.setEnabled(true);
            }
        });


        JLabel lblFeed = new JLabel("1号位坐标");
        panelXYZ.add(lblFeed, "2, 4, right, default");


        JLabel lblPostPick = new JLabel("5号位坐标");
        panelXYZ.add(lblPostPick, "2, 6, right, default");


        LocationButtonsPanel locationButtonsLeft = new LocationButtonsPanel(s1XValue, s1YValue, s1ZValue, null);
        panelXYZ.add(locationButtonsLeft, "10, 4");

        LocationButtonsPanel locationButtonsRight = new LocationButtonsPanel(s5XValue, s5YValue, s5ZValue, null);
        panelXYZ.add(locationButtonsRight, "10, 6");

        JPanel panelActions = new JPanel();
        panelActions.setLayout(new FlowLayout(FlowLayout.RIGHT));

        btnReset = new JButton(resetAction);
        panelActions.add(btnReset);

        btnApply = new JButton(applyAction);
        panelActions.add(btnApply);

        panelXYZ.add(panelActions, "10, 8");


        tabbedPane_1.addTab("吸嘴更换设置", //$NON-NLS-1$
                null, panelXYZ, null);


        JPanel panelCalibrate = new JPanel();

        JPanel panelAction = new JPanel();
        panelAction.setLayout(new FlowLayout(FlowLayout.RIGHT));

        //顶部相机校正
        JButton topCameraCalibrateBtn = new JButton(topCameraCalibrate);
        panelAction.add(topCameraCalibrateBtn);

        //吸嘴偏移量填写
        JButton nozzleOffsetBtn = new JButton(nozzleOffseAction);
        panelAction.add(nozzleOffsetBtn);

        //吸嘴偏移量教校正
        JButton nozzleN1OffsetCalibrateBtn = new JButton(nozzleN1OffsetCalibrateAction);
        panelAction.add(nozzleN1OffsetCalibrateBtn);

        //吸嘴偏移量教校正
        JButton nozzleN2OffsetCalibrateBtn = new JButton(nozzleN2OffsetCalibrateAction);
        panelAction.add(nozzleN2OffsetCalibrateBtn);

        //底部相机校正
        JButton bottomCameraCalibrateBtn = new JButton(bottomCameraCalibrate);
        panelAction.add(bottomCameraCalibrateBtn);


        panelCalibrate.add(panelAction);

        tabbedPane_1.addTab("一键校准设置", //$NON-NLS-1$
                null, panelCalibrate, null);


/*        JPanel panelXYZ = new OtherPanel();

        tabbedPane_1.addTab("吸嘴更换设置", //$NON-NLS-1$
                null, panelXYZ, null);*/
    }

    protected Action applyAction = new AbstractAction(Translations.getString(
            "AbstractConfigurationWizard.Action.Apply")) { //$NON-NLS-1$
        @Override
        public void actionPerformed(ActionEvent arg0) {


            List<NozzleTip> nozzles = configuration.getMachine().getNozzleTips();
            if (nozzles.size() < 5) {
                Logger.warn("请添加5个吸嘴！");
                return;
            }

            boolean oneToFive = true;
            double valueDiff = 0;
            if (Double.parseDouble(s1XValue.getText()) > Double.parseDouble(s5XValue.getText())) {
                oneToFive = true;
            } else {
                oneToFive = false;
            }

            if (oneToFive) {
                valueDiff = -(Double.parseDouble(s1XValue.getText()) - Double.parseDouble(s5XValue.getText())) / 4;
            } else {
                valueDiff = (Double.parseDouble(s1XValue.getText()) - Double.parseDouble(s5XValue.getText())) / 4;
            }


            Location nT1 = nozzles.get(0).getChangerMidLocation2();
            nT1.setX(Double.parseDouble(s1XValue.getText()));
            nT1.setY(Double.parseDouble(s1YValue.getText()));
            nT1.setZ(Double.parseDouble(s1ZValue.getText()));

            Location nT1First = nozzles.get(0).getChangerStartLocation();
            nT1First.setX(Double.parseDouble(s1XValue.getText()));
            nT1First.setY(Double.parseDouble(s1YValue.getText()) - 20.00);
            nT1First.setZ(-5.00);

            Location nT1Mid = nozzles.get(0).getChangerMidLocation();
            nT1Mid.setX(Double.parseDouble(s1XValue.getText()));
            nT1Mid.setY(Double.parseDouble(s1YValue.getText()));
            nT1Mid.setZ(-5.00);

            Location nT1End = nozzles.get(0).getChangerEndLocation();
            nT1End.setX(Double.parseDouble(s1XValue.getText()));
            nT1End.setY(Double.parseDouble(s1YValue.getText()) - 20.00);
            nT1End.setZ(Double.parseDouble(s1ZValue.getText()));


            Location nT2 = nozzles.get(1).getChangerMidLocation2();

            nT2.setX(Double.parseDouble(s1XValue.getText()) + valueDiff);
            nT2.setY(Double.parseDouble(s1YValue.getText()));
            nT2.setZ(Double.parseDouble(s1ZValue.getText()));


            Location nT2First = nozzles.get(1).getChangerStartLocation();
            nT2First.setX(Double.parseDouble(s1XValue.getText()) + valueDiff);
            nT2First.setY(Double.parseDouble(s1YValue.getText()) - 20.00);
            nT2First.setZ(-5.00);

            Location nT2Mid = nozzles.get(1).getChangerMidLocation();
            nT2Mid.setX(Double.parseDouble(s1XValue.getText()) + valueDiff);
            nT2Mid.setY(Double.parseDouble(s1YValue.getText()));
            nT2Mid.setZ(-5.00);

            Location nT2End = nozzles.get(1).getChangerEndLocation();
            nT2End.setX(Double.parseDouble(s1XValue.getText()) + valueDiff);
            nT2End.setY(Double.parseDouble(s1YValue.getText()) - 20.00);
            nT2End.setZ(Double.parseDouble(s1ZValue.getText()));


            Location nT3 = nozzles.get(2).getChangerMidLocation2();

            nT3.setX(Double.parseDouble(s1XValue.getText()) + 2 * valueDiff);
            nT3.setY(Double.parseDouble(s1YValue.getText()));
            nT3.setZ(Double.parseDouble(s1ZValue.getText()));

            Location nT3First = nozzles.get(2).getChangerStartLocation();
            nT3First.setX(Double.parseDouble(s1XValue.getText()) + 2 * valueDiff);
            nT3First.setY(Double.parseDouble(s1YValue.getText()) - 20.00);
            nT3First.setZ(-5.00);

            Location nT3Mid = nozzles.get(2).getChangerMidLocation();
            nT3Mid.setX(Double.parseDouble(s1XValue.getText()) + 2 * valueDiff);
            nT3Mid.setY(Double.parseDouble(s1YValue.getText()));
            nT3Mid.setZ(-5.00);

            Location nT3End = nozzles.get(2).getChangerEndLocation();
            nT3End.setX(Double.parseDouble(s1XValue.getText()) + 2 * valueDiff);
            nT3End.setY(Double.parseDouble(s1YValue.getText()) - 20.00);
            nT3End.setZ(Double.parseDouble(s1ZValue.getText()));


            Location nT4 = nozzles.get(3).getChangerMidLocation2();

            nT4.setX(Double.parseDouble(s1XValue.getText()) + 3 * valueDiff);
            nT4.setY(Double.parseDouble(s5YValue.getText()));
            nT4.setZ(Double.parseDouble(s5ZValue.getText()));


            Location nT4First = nozzles.get(3).getChangerStartLocation();
            nT4First.setX(Double.parseDouble(s1XValue.getText()) + 3 * valueDiff);
            nT4First.setY(Double.parseDouble(s5YValue.getText()) - 20.00);
            nT4First.setZ(-5.00);

            Location nT4Mid = nozzles.get(3).getChangerMidLocation();
            nT4Mid.setX(Double.parseDouble(s1XValue.getText()) + 3 * valueDiff);
            nT4Mid.setY(Double.parseDouble(s5YValue.getText()));
            nT4Mid.setZ(-5.00);

            Location nT4End = nozzles.get(3).getChangerEndLocation();
            nT4End.setX(Double.parseDouble(s1XValue.getText()) + 3 * valueDiff);
            nT4End.setY(Double.parseDouble(s5YValue.getText()) - 20.00);
            nT4End.setZ(Double.parseDouble(s5ZValue.getText()));

            Location nT5 = nozzles.get(4).getChangerMidLocation2();

            nT5.setX(Double.parseDouble(s5XValue.getText()));
            nT5.setY(Double.parseDouble(s5YValue.getText()));
            nT5.setZ(Double.parseDouble(s5ZValue.getText()));

            Location nT5First = nozzles.get(4).getChangerStartLocation();
            nT5First.setX(Double.parseDouble(s5XValue.getText()));
            nT5First.setY(Double.parseDouble(s5YValue.getText()) - 20.00);
            nT5First.setZ(-5.00);

            Location nT5Mid = nozzles.get(4).getChangerMidLocation();
            nT5Mid.setX(Double.parseDouble(s5XValue.getText()));
            nT5Mid.setY(Double.parseDouble(s5YValue.getText()));
            nT5Mid.setZ(-5.00);

            Location nT5End = nozzles.get(4).getChangerEndLocation();
            nT5End.setX(Double.parseDouble(s5XValue.getText()));
            nT5End.setY(Double.parseDouble(s5YValue.getText()) - 20.00);
            nT5End.setZ(Double.parseDouble(s5ZValue.getText()));

            btnApply.setEnabled(false);
            btnReset.setEnabled(false);
        }
    };

    protected Action topCameraCalibrate = new AbstractAction("顶部相机校正") {
        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            UiUtils.messageBoxOnException(() -> {

                ReferenceMachine machine = (ReferenceMachine) configuration.getMachine();

                List<Solutions.Issue> issues = machine.getAllSolutions().getIssues();

                for (Solutions.Issue issue : issues) {
                    if (issue.getIssue().equals("Primary calibration fiducial position and initial camera calibration.")) {
                        issue.setStateCall(Solutions.State.Solved);
                    }
                }
            });
        }
    };

    protected Action nozzleOffseAction = new AbstractAction("吸嘴偏移") {
        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            UiUtils.messageBoxOnException(() -> {
                Camera topCamera = VisionUtils.getTopVisionCamera();
                //移动相机到二维码上方
                Location offsetLocation = new Location();
                offsetLocation = topCamera.getLocation();
                offsetLocation.setX(-60);
                offsetLocation.setY(15);
                MovableUtils.moveToLocationAtSafeZ(topCamera, offsetLocation);
                MovableUtils.fireTargetedUserAction(topCamera);
                //识别二维码
                String qrString = VisionUtils.readQrCode(topCamera);
                if (qrString != null) {
                    JSONObject jsonObject = new JSONObject(qrString);
                    JSONArray pixelData = jsonObject.getJSONArray("data");
                    for (Object obj : pixelData) {

                        JSONObject temp = (JSONObject) obj;
                        String name = temp.get("name").toString();
                        List<Nozzle> nozzles = configuration.getMachine().getHeads().get(0).getNozzles();
                        for (Nozzle nozzle : nozzles) {
                            if (nozzle.getName().equals(name)) {
                                //填充到nozzle offset配置中
                                Location offset = nozzle.getHeadOffsets();
                                offset.setX(Double.parseDouble(temp.get("x").toString()));
                                offset.setY(Double.parseDouble(temp.get("y").toString()));
                                offset.setZ(Double.parseDouble(temp.get("z").toString()));
                            }
                        }
                    }
                    MessageBoxes.infoBox("消息", "偏移坐标写入成功！");
                } else {
                    throw new Exception("二维码识别失败，请重新尝试!");
                }
            });
        }
    };

    protected Action nozzleN1OffsetCalibrateAction = new AbstractAction("N1吸嘴校正") {
        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            UiUtils.messageBoxOnException(() -> {

                ReferenceMachine machine = (ReferenceMachine) configuration.getMachine();
                List<Solutions.Issue> issues = machine.getSolutions().getIssues();

                for (Solutions.Issue issue : issues) {
                    if (issue.getIssue().equals("Nozzle N1 offsets for the primary fiducial.")) {
                        issue.setStateCall(Solutions.State.Solved);
                    }
                }
            });
        }
    };

    protected Action nozzleN2OffsetCalibrateAction = new AbstractAction("N2吸嘴校正") {
        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            UiUtils.messageBoxOnException(() -> {

                ReferenceMachine machine = (ReferenceMachine) configuration.getMachine();
                List<Solutions.Issue> issues = machine.getSolutions().getIssues();

                for (Solutions.Issue issue : issues) {
                    if (issue.getIssue().equals("Nozzle N1 offsets for the primary fiducial.")) {
                        issue.setStateCall(Solutions.State.Solved);
                    }
                }
            });
        }
    };

    protected Action bottomCameraCalibrate = new AbstractAction("底部相机校正") {
        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            UiUtils.messageBoxOnException(() -> {

                ReferenceMachine machine = (ReferenceMachine) configuration.getMachine();
                List<Solutions.Issue> issues = machine.getSolutions().getIssues();

                for (Solutions.Issue issue : issues) {
                    if (issue.getIssue().equals("Primary calibration fiducial position and initial camera calibration.")) {
                        issue.setStateCall(Solutions.State.Solved);
                    }
                }
            });
        }
    };


    protected Action resetAction = new AbstractAction(Translations.getString(
            "AbstractConfigurationWizard.Action.Reset")) { //$NON-NLS-1$
        @Override
        public void actionPerformed(ActionEvent arg0) {
            s1XValue.setText("");
            s1YValue.setText("");
            s1ZValue.setText("");
            s5XValue.setText("");
            s5YValue.setText("");
            s5ZValue.setText("");

        }
    };


    private FocusTraversalPolicy focusPolicy = new FocusTraversalPolicy() {
        @Override
        public Component getComponentAfter(Container aContainer, Component aComponent) {
            return sliderIncrements;
        }

        @Override
        public Component getComponentBefore(Container aContainer, Component aComponent) {
            return sliderIncrements;
        }

        @Override
        public Component getDefaultComponent(Container aContainer) {
            return sliderIncrements;
        }

        @Override
        public Component getFirstComponent(Container aContainer) {
            return sliderIncrements;
        }

        @Override
        public Component getInitialComponent(Window window) {
            return sliderIncrements;
        }

        @Override
        public Component getLastComponent(Container aContainer) {
            return sliderIncrements;
        }
    };

    public double getSpeed() {
        return speedSlider.getValue() * 0.01D;
    }

    @SuppressWarnings("serial")
    public Action yPlusAction = new AbstractAction("Y+", Icons.arrowUp) { //$NON-NLS-1$
        @Override
        public void actionPerformed(ActionEvent arg0) {
            jog(0, 1, 0, 0);
        }
    };

    @SuppressWarnings("serial")
    public Action yMinusAction = new AbstractAction("Y-", Icons.arrowDown) { //$NON-NLS-1$
        @Override
        public void actionPerformed(ActionEvent arg0) {
            jog(0, -1, 0, 0);
        }
    };

    @SuppressWarnings("serial")
    public Action xPlusAction = new AbstractAction("X+", Icons.arrowRight) { //$NON-NLS-1$
        @Override
        public void actionPerformed(ActionEvent arg0) {
            jog(1, 0, 0, 0);
        }
    };

    @SuppressWarnings("serial")
    public Action xMinusAction = new AbstractAction("X-", Icons.arrowLeft) { //$NON-NLS-1$
        @Override
        public void actionPerformed(ActionEvent arg0) {
            jog(-1, 0, 0, 0);
        }
    };

    @SuppressWarnings("serial")
    public Action zPlusAction = new AbstractAction("Z+", Icons.arrowUp) { //$NON-NLS-1$
        @Override
        public void actionPerformed(ActionEvent arg0) {
            jog(0, 0, 1, 0);
        }
    };

    @SuppressWarnings("serial")
    public Action zMinusAction = new AbstractAction("Z-", Icons.arrowDown) { //$NON-NLS-1$
        @Override
        public void actionPerformed(ActionEvent arg0) {
            jog(0, 0, -1, 0);
        }
    };

    @SuppressWarnings("serial")
    public Action cPlusAction = new AbstractAction("C+", Icons.rotateCounterclockwise) { //$NON-NLS-1$
        @Override
        public void actionPerformed(ActionEvent arg0) {
            jog(0, 0, 0, 1);
        }
    };

    @SuppressWarnings("serial")
    public Action cMinusAction = new AbstractAction("C-", Icons.rotateClockwise) { //$NON-NLS-1$
        @Override
        public void actionPerformed(ActionEvent arg0) {
            jog(0, 0, 0, -1);
        }
    };

    @SuppressWarnings("serial")
    public Action xyParkAction = new AbstractAction(Translations.getString("JogControlsPanel.Action.ParkXY"), Icons.park) { //$NON-NLS-1$
        @Override
        public void actionPerformed(ActionEvent arg0) {
            UiUtils.submitUiMachineTask(() -> {
                Head head = machineControlsPanel.getSelectedTool().getHead();
                if (head == null) {
                    head = Configuration.get()
                            .getMachine()
                            .getDefaultHead();
                }
                MovableUtils.park(head);
                MovableUtils.fireTargetedUserAction(head.getDefaultHeadMountable());
            });
        }
    };

    @SuppressWarnings("serial")
    public Action zParkAction = new AbstractAction(Translations.getString("JogControlsPanel.Action.ParkZ"), Icons.park) { //$NON-NLS-1$
        @Override
        public void actionPerformed(ActionEvent arg0) {
            UiUtils.submitUiMachineTask(() -> {
                HeadMountable hm = machineControlsPanel.getSelectedTool();
                // Note, we don't just moveToSafeZ(), because this will just sit still, if we're already in the Safe Z Zone.
                // instead we explicitly move to the Safe Z coordinate i.e. the lower bound of the Safe Z Zone, applicable
                // for this hm.
                Location location = hm.getLocation();
                Length safeZLength = hm.getSafeZ();
                double safeZ = (safeZLength != null ? safeZLength.convertToUnits(location.getUnits()).getValue() : Double.NaN);
                location = location.derive(null, null, safeZ, null);
                if (Configuration.get().getMachine().isSafeZPark()) {
                    // All other head-mountables must also be moved to safe Z.
                    hm.getHead().moveToSafeZ();
                }
                hm.moveTo(location);
                MovableUtils.fireTargetedUserAction(hm);
            });
        }
    };

    @SuppressWarnings("serial")
    public Action cParkAction = new AbstractAction(Translations.getString("JogControlsPanel.Action.ParkC"), Icons.park) { //$NON-NLS-1$
        @Override
        public void actionPerformed(ActionEvent arg0) {
            UiUtils.submitUiMachineTask(() -> {
                HeadMountable hm = machineControlsPanel.getSelectedTool();
                Location location = hm.getLocation();
                double parkAngle = 0;
                if (hm instanceof AbstractNozzle) {
                    AbstractNozzle nozzle = (AbstractNozzle) hm;
                    if (nozzle.getRotationMode() == RotationMode.LimitedArticulation) {
                        if (nozzle.getPart() == null) {
                            // Make sure any lingering rotation offset is reset.
                            nozzle.setRotationModeOffset(null);
                        }
                        // Limited axis, select a 90° step position within the limits.
                        double[] limits = nozzle.getRotationModeLimits();
                        parkAngle = Math.round((limits[0] + limits[1]) / 2 / 90) * 90;
                        if (parkAngle < limits[0] || parkAngle > limits[1]) {
                            // Rounded mid-point outside limits? Can this ever happen? If yes, fall back to exact mid-point.
                            parkAngle = (limits[1] + limits[0]) / 2;
                        }
                    }
                }
                location = location.derive(null, null, null, parkAngle);
                hm.moveTo(location);
                MovableUtils.fireTargetedUserAction(hm, true);
            });
        }
    };

    @SuppressWarnings("serial")
    public Action safezAction = new AbstractAction(Translations.getString("JogControlsPanel.Action.HeadSafeZ")) { //$NON-NLS-1$
        @Override
        public void actionPerformed(ActionEvent arg0) {
            UiUtils.submitUiMachineTask(() -> {
                HeadMountable hm = machineControlsPanel.getSelectedTool();
                Head head = hm.getHead();
                head.moveToSafeZ();
                MovableUtils.fireTargetedUserAction(hm);
            });
        }
    };

    @SuppressWarnings("serial")
    public Action discardAction = new AbstractAction(Translations.getString("JogControlsPanel.Action.Discard")) { //$NON-NLS-1$
        @Override
        public void actionPerformed(ActionEvent arg0) {
            UiUtils.submitUiMachineTask(() -> {
                Nozzle nozzle = machineControlsPanel.getSelectedNozzle();
                Cycles.discard(nozzle);
            });
        }
    };

    @SuppressWarnings("serial")
    public Action recycleAction = new AbstractAction(Translations.getString("JogControlsPanel.Action.Recycle")) { //$NON-NLS-1$
        @Override
        public void actionPerformed(ActionEvent arg0) {
            UiUtils.submitUiMachineTask(() -> {
                Nozzle nozzle = machineControlsPanel.getSelectedNozzle();
                Part part = nozzle.getPart();

                // just make sure a part is there
                if (part == null) {
                    throw new Exception("No Part on the current nozzle!");
                }

                // go through the feeders
                for (Feeder feeder : Configuration.get().getMachine().getFeeders()) {
                    if (part.equals(feeder.getPart()) && feeder.isEnabled() && feeder.canTakeBackPart()) {
                        feeder.takeBackPart(nozzle);
                        break;
                    }
                }
            });
        }
    };


    @SuppressWarnings("serial")
    public Action raiseIncrementAction = new AbstractAction(Translations.getString("JogControlsPanel.Action.RaiseJogIncrement")) { //$NON-NLS-1$
        @Override
        public void actionPerformed(ActionEvent arg0) {
            sliderIncrements.setValue(
                    Math.min(sliderIncrements.getMaximum(), sliderIncrements.getValue() + 1));
        }
    };

    @SuppressWarnings("serial")
    public Action lowerIncrementAction = new AbstractAction(Translations.getString("JogControlsPanel.Action.LowerJogIncrement")) { //$NON-NLS-1$
        @Override
        public void actionPerformed(ActionEvent arg0) {
            sliderIncrements.setValue(
                    Math.max(sliderIncrements.getMinimum(), sliderIncrements.getValue() - 1));
        }
    };

    @SuppressWarnings("serial")
    public Action setIncrement1Action = new AbstractAction(Translations.getString("JogControlsPanel.Action.FirstJogIncrement")) { //$NON-NLS-1$
        @Override
        public void actionPerformed(ActionEvent arg0) {
            sliderIncrements.setValue(1);
        }
    };
    @SuppressWarnings("serial")
    public Action setIncrement2Action = new AbstractAction(Translations.getString("JogControlsPanel.Action.SecondJogIncrement")) { //$NON-NLS-1$
        @Override
        public void actionPerformed(ActionEvent arg0) {
            sliderIncrements.setValue(2);
        }
    };
    @SuppressWarnings("serial")
    public Action setIncrement3Action = new AbstractAction(Translations.getString("JogControlsPanel.Action.ThirdJogIncrement")) { //$NON-NLS-1$
        @Override
        public void actionPerformed(ActionEvent arg0) {
            sliderIncrements.setValue(3);
        }
    };
    @SuppressWarnings("serial")
    public Action setIncrement4Action = new AbstractAction(Translations.getString("JogControlsPanel.Action.FourthJogIncrement")) { //$NON-NLS-1$
        @Override
        public void actionPerformed(ActionEvent arg0) {
            sliderIncrements.setValue(4);
        }
    };
    @SuppressWarnings("serial")
    public Action setIncrement5Action = new AbstractAction(Translations.getString("JogControlsPanel.Action.FifthJogIncrement")) { //$NON-NLS-1$
        @Override
        public void actionPerformed(ActionEvent arg0) {
            sliderIncrements.setValue(5);
        }
    };


    private void addActuator(Actuator actuator) {
        String name = actuator.getHead() == null ? actuator.getName() : actuator.getHead()
                .getName()
                + ":" + actuator.getName(); //$NON-NLS-1$
        JButton actuatorButton = new JButton(name);
        actuatorButton.addActionListener((e) -> {
            ActuatorControlDialog dlg = new ActuatorControlDialog(actuator);
            dlg.pack();
            dlg.revalidate();
            dlg.setLocationRelativeTo(JogControlsPanel.this);
            dlg.setVisible(true);
        });
        BeanUtils.addPropertyChangeListener(actuator, "name", e -> { //$NON-NLS-1$
            actuatorButton.setText(
                    actuator.getHead() == null ? actuator.getName() : actuator.getHead()
                            .getName()
                            + ":" + actuator.getName()); //$NON-NLS-1$
        });
        panelActuators.add(actuatorButton);
        actuatorButtons.put(actuator, actuatorButton);
    }

    private void removeActuator(Actuator actuator) {
        panelActuators.remove(actuatorButtons.remove(actuator));
    }

    private ConfigurationListener configurationListener = new ConfigurationListener.Adapter() {
        @Override
        public void configurationComplete(Configuration configuration) throws Exception {
            setUnits(configuration.getSystemUnits());
            speedSlider.setValue((int) (configuration.getMachine()
                    .getSpeed()
                    * 100));

            panelActuators.removeAll();

            Machine machine = Configuration.get()
                    .getMachine();

            for (Actuator actuator : machine.getActuators()) {
                addActuator(actuator);
            }
            for (final Head head : machine.getHeads()) {
                for (Actuator actuator : head.getActuators()) {
                    addActuator(actuator);
                }
            }


            PropertyChangeListener listener = (e) -> {
                if (e.getOldValue() == null && e.getNewValue() != null) {
                    Actuator actuator = (Actuator) e.getNewValue();
                    addActuator(actuator);
                } else if (e.getOldValue() != null && e.getNewValue() == null) {
                    removeActuator((Actuator) e.getOldValue());
                }
            };

            BeanUtils.addPropertyChangeListener(machine, "actuators", listener); //$NON-NLS-1$
            for (Head head : machine.getHeads()) {
                BeanUtils.addPropertyChangeListener(head, "actuators", listener); //$NON-NLS-1$
            }


            setEnabled(machineControlsPanel.isEnabled());

            // add property listener for recycle button
            // enable recycle only if part on current head
            PropertyChangeListener recyclePropertyListener = (e) -> {
                Nozzle selectedNozzle = machineControlsPanel.getSelectedNozzle();
                if (selectedNozzle != null) {
                    boolean canTakeBack = false;
                    Part part = selectedNozzle.getPart();
                    if (part != null) {
                        for (Feeder feeder : Configuration.get().getMachine().getFeeders()) {
                            if (feeder.isEnabled()
                                    && feeder.getPart() == part
                                    && feeder.canTakeBackPart()) {
                                canTakeBack = true;
                            }
                        }
                    }
                    recycleAction.setEnabled(canTakeBack);
                }
            };
            // add to all nozzles
            for (Head head : Configuration.get().getMachine().getHeads()) {
                for (Nozzle nozzle : head.getNozzles()) {
                    if (nozzle instanceof AbstractNozzle) {
                        AbstractNozzle aNozzle = (AbstractNozzle) nozzle;
                        aNozzle.addPropertyChangeListener("part", recyclePropertyListener);
                    }
                }
            }
            // add to the currently selected tool, so we get a notification if that changed, maybe other part on the nozzle
            machineControlsPanel.addPropertyChangeListener("selectedTool", recyclePropertyListener);
        }
    };

    private Map<Actuator, JButton> actuatorButtons = new HashMap<>();
    private JSlider speedSlider;
}
