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

import java.awt.Color;
import java.awt.EventQueue;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Locale;
import java.util.prefs.Preferences;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.TitledBorder;

import org.jdesktop.beansbinding.AutoBinding.UpdateStrategy;
import org.jdesktop.swingx.JXCollapsiblePane;
import org.openpnp.ConfigurationListener;
import org.openpnp.Translations;
import org.openpnp.gui.support.ActuatorItem;
import org.openpnp.gui.support.CameraItem;
import org.openpnp.gui.support.HeadMountableItem;
import org.openpnp.gui.support.Icons;
import org.openpnp.gui.support.NozzleItem;
import org.openpnp.machine.reference.axis.ReferenceVirtualAxis;
import org.openpnp.machine.reference.driver.AbstractReferenceDriver;
import org.openpnp.machine.reference.driver.SerialPortCommunications;
import org.openpnp.model.Configuration;
import org.openpnp.model.Length;
import org.openpnp.model.LengthUnit;
import org.openpnp.model.Location;
import org.openpnp.spi.*;
import org.openpnp.util.BeanUtils;
import org.openpnp.util.MovableUtils;
import org.openpnp.util.UiUtils;
import org.pmw.tinylog.Logger;

import com.jgoodies.forms.layout.ColumnSpec;
import com.jgoodies.forms.layout.FormLayout;
import com.jgoodies.forms.layout.FormSpecs;
import com.jgoodies.forms.layout.RowSpec;

public class MachineControlsPanel extends JPanel {
    private final Configuration configuration;
    private final JobPanel jobPanel;

    private static final String PREF_JOG_CONTROLS_EXPANDED =
            "MachineControlsPanel.jogControlsExpanded"; //$NON-NLS-1$
    private static final boolean PREF_JOG_CONTROLS_EXPANDED_DEF = true;
    private Preferences prefs = Preferences.userNodeForPackage(MachineControlsPanel.class);

    private HeadMountable selectedTool;
    private HeadMountable lastSelectedNonCamera;

    private JComboBox comboBoxHeadMountable;

    private JogControlsPanel jogControlsPanel;

    private Location markLocation = null;

    private Color droNormalColor = new Color(0xBDFFBE);
    private Color droSavedColor = new Color(0x90cce0);

    /**
     * Create the panel.
     */
    public MachineControlsPanel(Configuration configuration, JobPanel jobPanel) {
        setBorder(new TitledBorder(null, Translations.getString("MachineControls.Label"), TitledBorder.LEADING, TitledBorder.TOP, //$NON-NLS-1$
                null, null));
        this.configuration = configuration;
        this.jobPanel = jobPanel;

        createUi();

        configuration.addListener(configurationListener);
    }

    public Nozzle getSelectedNozzle() {
        if (selectedTool instanceof Nozzle) {
            return (Nozzle) selectedTool;
        }
        if (lastSelectedNonCamera instanceof Nozzle) {
            return (Nozzle) lastSelectedNonCamera;
        }
        try {
            return configuration.getMachine().getDefaultHead().getDefaultNozzle();
        } catch (Exception e) {
            return null;
        }
    }


    /**
     * Currently returns the selected Nozzle. Intended to eventually return either the selected
     * Nozzle or PasteDispenser.
     *
     * @return
     */
    public HeadMountable getSelectedTool() {
        return selectedTool;
    }

    public void setSelectedTool(HeadMountable hm) {
        HeadMountable oldValue = selectedTool;
        selectedTool = hm;
        if (!(hm instanceof Camera)) {
            lastSelectedNonCamera = hm;
        }
        for (int i = 0; i < comboBoxHeadMountable.getItemCount(); i++) {
            HeadMountableItem item = (HeadMountableItem) comboBoxHeadMountable.getItemAt(i);
            if (item.getItem() == hm) {
                comboBoxHeadMountable.setSelectedItem(item);
                break;
            }
        }
        updateDros();
        enableToolActions();
        if (oldValue != hm) {
            firePropertyChange("selectedTool", oldValue, hm);
        }
    }

    private void enableToolActions() {
        Camera camera = null;
        try {
            Head head = selectedTool.getHead();
            if (head == null) {
                head = Configuration.get().getMachine().getDefaultHead();
            }
            camera = head.getDefaultCamera();
        } catch (Exception e) {
        }
        boolean enabled = Configuration.get().getMachine().isEnabled();
        homeAction.setEnabled(enabled);
        jogControlsPanel.setEnabled(enabled);
        targetCameraAction.setEnabled(enabled && selectedTool != camera);
        targetToolAction.setEnabled(enabled && selectedTool == camera);
    }

    public JogControlsPanel getJogControlsPanel() {
        return jogControlsPanel;
    }

    public JobPanel getJobPanel() {
        return jobPanel;
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        enableToolActions();
    }

    public Location getCurrentLocation() {
        if (selectedTool == null) {
            return null;
        }

        Location l = selectedTool.getLocation();
        l = l.convertToUnits(configuration.getSystemUnits());

        return l;
    }

    public void updateDros() {
        Location l = getCurrentLocation();
        if (l == null) {
            return;
        }

        if (markLocation != null) {
            l = l.subtract(markLocation);
        }

        double x, y, z, c;

        x = l.getX();
        y = l.getY();
        z = l.getZ();
        c = l.getRotation();

        MainFrame.get().getDroLabel()
                .setText(String.format("X:%-9s Y:%-9s Z:%-9s C:%-9s", //$NON-NLS-1$
                        String.format(Locale.US, configuration.getLengthDisplayFormat(), x),
                        String.format(Locale.US, configuration.getLengthDisplayFormat(), y),
                        String.format(Locale.US, configuration.getLengthDisplayFormat(), z),
                        String.format(Locale.US, configuration.getLengthDisplayFormat(), c)));
    }

    private void createUi() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

        JXCollapsiblePane collapsePane = new JXCollapsiblePane();

        JButton collapseButton =
                new JButton(collapsePane.getActionMap().get(JXCollapsiblePane.TOGGLE_ACTION));
        collapseButton.setBorderPainted(false);
        collapseButton.setHideActionText(true);
        collapseButton.setText(""); //$NON-NLS-1$

        // get the built-in toggle action
        Action collapseAction = collapseButton.getAction();
        // use the collapse/expand icons from the JTree UI
        collapseAction.putValue(JXCollapsiblePane.COLLAPSE_ICON,
                UIManager.getIcon("Tree.expandedIcon")); //$NON-NLS-1$
        collapseAction.putValue(JXCollapsiblePane.EXPAND_ICON,
                UIManager.getIcon("Tree.collapsedIcon")); //$NON-NLS-1$

        jogControlsPanel = new JogControlsPanel(configuration, this);

        JPanel panel = new JPanel();
        add(panel);
        panel.setLayout(new FormLayout(
                new ColumnSpec[]{FormSpecs.RELATED_GAP_COLSPEC, FormSpecs.DEFAULT_COLSPEC,
                        FormSpecs.RELATED_GAP_COLSPEC, ColumnSpec.decode("default:grow"),}, //$NON-NLS-1$
                new RowSpec[]{FormSpecs.RELATED_GAP_ROWSPEC, FormSpecs.DEFAULT_ROWSPEC,}));

        comboBoxHeadMountable = new JComboBox();
        comboBoxHeadMountable.setMaximumRowCount(20);
        comboBoxHeadMountable.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                HeadMountableItem selectedItem =
                        (HeadMountableItem) comboBoxHeadMountable.getSelectedItem();
                setSelectedTool(selectedItem.getItem());
            }
        });

        panel.add(collapseButton, "2, 2"); //$NON-NLS-1$
        panel.add(comboBoxHeadMountable, "4, 2, fill, default"); //$NON-NLS-1$
        collapsePane.add(jogControlsPanel);
        add(collapsePane);

        collapsePane.setCollapsed(
                !prefs.getBoolean(PREF_JOG_CONTROLS_EXPANDED, PREF_JOG_CONTROLS_EXPANDED_DEF));

        collapsePane.addPropertyChangeListener("collapsed", e -> { //$NON-NLS-1$
            prefs.putBoolean(PREF_JOG_CONTROLS_EXPANDED, !collapsePane.isCollapsed());
        });
    }

    @SuppressWarnings("serial")
    public Action startStopMachineAction = new AbstractAction(Translations.getString("MachineControls.Action.Stop"), Icons.powerOn) { //$NON-NLS-1$
        {

            putValue(ACCELERATOR_KEY, KeyStroke.getKeyStroke('E',
                    Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
        }

        @Override
        public void actionPerformed(ActionEvent arg0) {
            setEnabled(false);
            final Machine machine = Configuration.get().getMachine();
            bindSerial();
            final boolean enable = !machine.isEnabled();
            Runnable task = () -> {
                try {
                    machine.setEnabled(enable);
                } catch (Exception t1) {
                    UiUtils.showError(t1);
                }
                // TODO STOPSHIP move setEnabled into a binding.
                SwingUtilities.invokeLater(() -> setEnabled(true));
            };
            if (machine.isBusy() && !enable) {
                // Note: We specifically bypass the machine submit so that this runs immediately 
                // as an emergency stop.
                // That's not really thread safe tho, so it's better than nothing, but not much.
                Thread thread = new Thread(task);
                thread.setDaemon(true);
                thread.start();
            } else {
                // Not an emergency stop. Run as regular machine task.  
                UiUtils.submitUiMachineTask(() -> {
                            task.run();
                            return null;
                        },
                        (result) -> {
                        },
                        (t) -> UiUtils.showError(t),
                        true); // Allow for disabled machine.
            }
        }
    };

    //根据COM口名称自动绑定GcodeDriver
    public void bindSerial() {
        List<Driver> drivers = configuration.getMachine().getDrivers();
        String[] portNames = SerialPortCommunications.getPortNames();
        for (Driver driver : drivers) {
            String driverName = driver.getName();
            for (String portName : portNames) {
                if (driverName.equals(portName)) {
                    AbstractReferenceDriver referenceDriver = (AbstractReferenceDriver) driver;
                    referenceDriver.setPortName(portName);

                }
            }
        }
    }

    public class HomeAction extends AbstractAction {
        public HomeAction() {
            super(Translations.getString("MachineControls.Action.Home"), Icons.home); //$NON-NLS-1$
            putValue(ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_BACK_QUOTE,
                    Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
        }

        public void setHomed(boolean homed) {
            putValue(Action.SMALL_ICON, homed ? Icons.home : Icons.homeWarning);
        }

        @Override
        public void actionPerformed(ActionEvent arg0) {
            UiUtils.submitUiMachineTask(() -> {
                Machine machine = Configuration.get().getMachine();
                machine.home();
            });
        }
    }

    public HomeAction homeAction = new HomeAction();

    @SuppressWarnings("serial")
    public Action targetToolAction = new AbstractAction(null, Icons.centerTool) {
        @Override
        public void actionPerformed(ActionEvent arg0) {
            UiUtils.submitUiMachineTask(() -> {
                HeadMountable tool = getSelectedTool();
                Camera camera = tool.getHead().getDefaultCamera();
                if (tool == camera) {
                    tool = getLastSelectedNonCamera();
                }
                MovableUtils.moveToLocationAtSafeZ(tool, camera.getLocation(tool));
                MovableUtils.fireTargetedUserAction(tool);
            });
        }
    };

    @SuppressWarnings("serial")
    public Action targetCameraAction = new AbstractAction(null, Icons.centerCamera) {
        @Override
        public void actionPerformed(ActionEvent arg0) {
            UiUtils.submitUiMachineTask(() -> {
                HeadMountable tool = getSelectedTool();
                Camera camera = tool.getHead().getDefaultCamera();
                if (tool == camera) {
                    tool = getLastSelectedNonCamera();
                }
                MovableUtils.moveToLocationAtSafeZ(camera, tool.getLocation());
                MovableUtils.fireTargetedUserAction(camera);
            });
        }
    };

    private void updateStartStopButton(boolean enabled) {
        startStopMachineAction.putValue(Action.NAME, enabled ? Translations.getString("MachineControls.Action.Stop") : Translations.getString("MachineControls.Action.Start")); //$NON-NLS-1$ //$NON-NLS-2$
        startStopMachineAction.putValue(Action.SMALL_ICON,
                enabled ? Icons.powerOff : Icons.powerOn);
    }

    private HeadMountable getLastSelectedNonCamera() {
        if (lastSelectedNonCamera == null) {
            try {
                lastSelectedNonCamera = getSelectedTool().getHead().getDefaultNozzle();
            } catch (Exception e) {
            }
        }
        return lastSelectedNonCamera;
    }

    private void setLastSelectedNonCamera(HeadMountable lastSelectedNonCamera) {
        this.lastSelectedNonCamera = lastSelectedNonCamera;
    }

    private MachineListener machineListener = new MachineListener.Adapter() {
        private Location lastUserActionLocation;

        @Override
        public void machineHeadActivity(Machine machine, Head head) {
            EventQueue.invokeLater(() -> updateDros());
            EventQueue.invokeLater(() -> comboBoxHeadMountable.repaint());
        }

        @Override
        public void machineEnabled(Machine machine) {
            updateStartStopButton(machine.isEnabled());
            setEnabled(true);
            EventQueue.invokeLater(() -> updateDros());
        }

        @Override
        public void machineEnableFailed(Machine machine, String reason) {
            updateStartStopButton(machine.isEnabled());
        }

        @Override
        public void machineDisabled(Machine machine, String reason) {
            updateStartStopButton(machine.isEnabled());
            setEnabled(false);
        }

        @Override
        public void machineDisableFailed(Machine machine, String reason) {
            updateStartStopButton(machine.isEnabled());
        }

        @Override
        public void machineTargetedUserAction(Machine machine, HeadMountable hm, boolean jogging) {
            if (hm != null
                    && hm.getHead() != null) { // Do this only if this is a true HeadMountable 
                // i.e. not for bottom cameras or Machine actuators.

                if (MovableUtils.isInSafeZZone(hm)) {
                    lastUserActionLocation = null;
                } else if (lastUserActionLocation == null || getSelectedTool() != hm || !jogging) {
                    lastUserActionLocation = hm.getLocation().convertToUnits(LengthUnit.Millimeters);
                } else {
                    // Jogging the same selected tool. Apply auto-Safe Z.
                    if (hm.getAxisZ() instanceof ReferenceVirtualAxis) {
                        Length distance = lastUserActionLocation.getLinearLengthTo(hm.getLocation());
                        if (distance.compareTo(machine.getUnsafeZRoamingDistance()) > 0) {
                            // Distance is too large to retain virtual Z. Make it safe.
                            Logger.debug(hm.getName() + " exceeded roaming distance at non-safe Z, going to safe Z. "
                                    + "Last user action at " + lastUserActionLocation + " roamed to " + hm.getLocation() + " distance " + distance + "mm.");
                            UiUtils.submitUiMachineTask(() -> hm.moveToSafeZ());
                            lastUserActionLocation = null;
                        }
                    }
                }
                if (machine.isAutoToolSelect()) {
                    SwingUtilities.invokeLater(() -> {
                        if (getSelectedTool() != hm) {
                            setSelectedTool(hm);
                        }
                    });
                }
            }
        }
    };

    private ConfigurationListener configurationListener = new ConfigurationListener.Adapter() {
        @Override
        public void configurationComplete(Configuration configuration) {
            SwingUtilities.invokeLater(() -> {
                MainFrame.get().getDroLabel().setBackground(droNormalColor);
            });
            MainFrame.get().getDroLabel().addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    SwingUtilities.invokeLater(() -> {
                        if (markLocation == null) {
                            markLocation = getCurrentLocation();
                            MainFrame.get().getDroLabel().setBackground(droSavedColor);
                        } else {
                            markLocation = null;
                            MainFrame.get().getDroLabel().setBackground(droNormalColor);
                        }
                        updateDros();
                    });
                }
            });

            Machine machine = configuration.getMachine();
            if (machine != null) {
                machine.removeListener(machineListener);
            }

            for (Head head : machine.getHeads()) {
                for (Nozzle nozzle : head.getNozzles()) {
                    comboBoxHeadMountable.addItem(new NozzleItem(nozzle));
                }

                for (Camera camera : head.getCameras()) {
                    comboBoxHeadMountable.addItem(new CameraItem(camera));
                }

                for (Actuator actuator : head.getActuators()) {
                    comboBoxHeadMountable.addItem(new ActuatorItem(actuator));
                }
            }

            setSelectedTool(((HeadMountableItem) comboBoxHeadMountable.getItemAt(0)).getItem());

            machine.addListener(machineListener);

            updateStartStopButton(machine.isEnabled());

            setEnabled(machine.isEnabled());

            BeanUtils.bind(UpdateStrategy.READ, machine, "homed", homeAction, "homed");

            for (Head head : machine.getHeads()) {

                BeanUtils.addPropertyChangeListener(head, "nozzles", (e) -> { //$NON-NLS-1$
                    if (e.getOldValue() == getLastSelectedNonCamera()) {
                        setLastSelectedNonCamera(null);
                    }
                    if (e.getOldValue() == null && e.getNewValue() != null) {
                        Nozzle nozzle = (Nozzle) e.getNewValue();
                        comboBoxHeadMountable.addItem(new NozzleItem(nozzle));
                    } else if (e.getOldValue() != null && e.getNewValue() == null) {
                        for (int i = 0; i < comboBoxHeadMountable.getItemCount(); i++) {
                            HeadMountableItem item = (HeadMountableItem) comboBoxHeadMountable.getItemAt(i);
                            if (item.getItem() == e.getOldValue()) {
                                comboBoxHeadMountable.removeItemAt(i);
                            }
                        }
                    }
                });

                BeanUtils.addPropertyChangeListener(head, "cameras", (e) -> { //$NON-NLS-1$
                    if (e.getOldValue() == null && e.getNewValue() != null) {
                        Camera camera = (Camera) e.getNewValue();
                        comboBoxHeadMountable.addItem(new CameraItem(camera));
                    } else if (e.getOldValue() != null && e.getNewValue() == null) {
                        for (int i = 0; i < comboBoxHeadMountable.getItemCount(); i++) {
                            HeadMountableItem item =
                                    (HeadMountableItem) comboBoxHeadMountable.getItemAt(i);
                            if (item.getItem() == e.getOldValue()) {
                                comboBoxHeadMountable.removeItemAt(i);
                            }
                        }
                    }
                });

                BeanUtils.addPropertyChangeListener(head, "actuators", (e) -> { //$NON-NLS-1$
                    if (e.getOldValue() == getLastSelectedNonCamera()) {
                        setLastSelectedNonCamera(null);
                    }
                    if (e.getOldValue() == null && e.getNewValue() != null) {
                        Actuator actuator = (Actuator) e.getNewValue();
                        comboBoxHeadMountable.addItem(new ActuatorItem(actuator));
                    } else if (e.getOldValue() != null && e.getNewValue() == null) {
                        for (int i = 0; i < comboBoxHeadMountable.getItemCount(); i++) {
                            HeadMountableItem item =
                                    (HeadMountableItem) comboBoxHeadMountable.getItemAt(i);
                            if (item.getItem() == e.getOldValue()) {
                                comboBoxHeadMountable.removeItemAt(i);
                            }
                        }
                    }
                });
            }

        }
    };
}
