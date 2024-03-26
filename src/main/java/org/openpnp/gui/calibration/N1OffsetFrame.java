package org.openpnp.gui.calibration;

import com.jgoodies.forms.layout.ColumnSpec;
import com.jgoodies.forms.layout.FormLayout;
import com.jgoodies.forms.layout.FormSpecs;
import com.jgoodies.forms.layout.RowSpec;
import org.openpnp.Translations;
import org.openpnp.machine.reference.ReferenceHead;
import org.openpnp.machine.reference.ReferenceMachine;
import org.openpnp.machine.reference.ReferenceNozzle;
import org.openpnp.machine.reference.camera.ReferenceCamera;
import org.openpnp.machine.reference.solutions.CalibrationSolutions;
import org.openpnp.machine.reference.solutions.VisionSolutions;
import org.openpnp.model.Configuration;
import org.openpnp.model.Length;
import org.openpnp.model.LengthUnit;
import org.openpnp.model.Location;
import org.openpnp.spi.Camera;
import org.openpnp.spi.Head;
import org.openpnp.spi.Nozzle;
import org.openpnp.util.SwingUtil;
import org.openpnp.util.UiUtils;
import org.openpnp.vision.pipeline.CvStage;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import org.openpnp.vision.pipeline.CvStage.Result.Circle;
import org.pmw.tinylog.Logger;


public class N1OffsetFrame extends JFrame {

    private double featureDiameter;

    private Location oldNozzleOffsets = null;


    public N1OffsetFrame() {

        createUi();
    }

    public boolean isSolvedSecondaryXY(ReferenceHead head) {
        Location fiducialLocation = head.getCalibrationSecondaryFiducialLocation();
        return fiducialLocation.getLengthX().isInitialized()
                && fiducialLocation.getLengthY().isInitialized()
                && head.getCalibrationSecondaryFiducialDiameter() != null
                && head.getCalibrationSecondaryFiducialDiameter().isInitialized();
    }

    public void createUi() {
        setTitle(Translations.getString("JogControlsPanel.nozzleN1OffsetCalibrateAction.Text"));

        setResizable(false);
        setAlwaysOnTop(true);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        JPanel panel = new JPanel();

        FormLayout layout = new FormLayout(
                new ColumnSpec[]{
                        FormSpecs.PREF_COLSPEC,
                        FormSpecs.RELATED_GAP_COLSPEC,
                        FormSpecs.PREF_COLSPEC,
                        FormSpecs.RELATED_GAP_COLSPEC,
                        FormSpecs.PREF_COLSPEC
                },
                new RowSpec[]{
                        FormSpecs.PREF_ROWSPEC,
                        FormSpecs.RELATED_GAP_ROWSPEC,
                        FormSpecs.PREF_ROWSPEC
                });
        panel.setLayout(layout);

        ImageIcon icon = new ImageIcon(getClass().getClassLoader().getResource("icons/gif/n1offset.gif"));
        Image image = icon.getImage();
        JLabel gifLabel = new JLabel();
        gifLabel.setIcon(SwingUtil.createAutoAdjustIcon(image, true));
        gifLabel.setPreferredSize(new Dimension(360 / 2, 422 / 2)); // 设置你想要的大小


        panel.add(gifLabel, "1, 1, fill, default");


        JLabel descryption = new JLabel("<html><body>1>:移动吸嘴N1到标记点的中心，并且正好接触到PCB<br/><br/>\n" +
                "2>:点击校准<br/><br/></body></html>");
        panel.add(descryption, "3, 1, 3, 1, fill, default");

        // 右边是一个编辑框
        SpinnerNumberModel spinnerModel = new SpinnerNumberModel(18, 0, 999, 1);

        JSpinner spinner = new JSpinner(spinnerModel);


        Component mySpinnerEditor = spinner.getEditor();

        JFormattedTextField jftf = ((JSpinner.DefaultEditor) mySpinnerEditor).getTextField();
        jftf.setColumns(2);


        setSize(350, 290);
        setLocationRelativeTo(null); // 居中显示，相对于主窗口


        getContentPane().add(panel);


        // 中间是一个标签
        JLabel label = new JLabel("Feature diameter");
        //panel.add(label, "1, 3, center, center");

        // 第二行布局
        JButton calibrateButton = new JButton("开始校准");
        panel.add(calibrateButton, "5, 3, fill, default");

        calibrateButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                UiUtils.submitUiMachineTask(() -> {
                    double fiducialsMinimumZOffsetMm = 2;

                    ReferenceNozzle nozzle = (ReferenceNozzle) Configuration.get().getMachine().getHeads().get(0).getNozzles().get(0);
                    ReferenceHead head = (ReferenceHead) Configuration.get().getMachine().getHeads().get(0);
                    ReferenceNozzle defaultNozzle = (ReferenceNozzle) head.getDefaultNozzle();
                    boolean primary = (nozzle == defaultNozzle && isSolvedSecondaryXY(head)) ? true : false;
                    String qualifier = primary ? "primary" : "secondary";

                    ReferenceCamera defaultCamera = (ReferenceCamera) head.getDefaultCamera();

                    Location headOffsetsBefore = nozzle.getHeadOffsets();
                    if (primary) {
                        // Reset any former head offset to zero.
                        nozzle.setHeadOffsets(Location.origin);
                    }
                    // Get the pure nozzle location.
                    Location nozzleLocation = nozzle.getLocation();
                    if (nozzle == defaultNozzle) {
                        // This is the reference nozzle, set the Z of the fiducial.
                        if (nozzle.getSafeZ().compareTo(nozzleLocation.getLengthZ()) <= 0) {
                            throw new Exception("The calibration " + qualifier + " fidcuial Z must be lower than Safe Z.");
                        } else if (!primary
                                && Math.abs(head.getCalibrationPrimaryFiducialLocation().getLengthZ()
                                .subtract(nozzleLocation.getLengthZ())
                                .convertToUnits(LengthUnit.Millimeters).getValue()) < fiducialsMinimumZOffsetMm) {
                            throw new Exception("Primary and secondary calibration fidcuial Z must be more than "
                                    + fiducialsMinimumZOffsetMm + "\u00A0mm apart.");
                        }
                        if (primary) {
                            head.setCalibrationPrimaryFiducialLocation(head.getCalibrationPrimaryFiducialLocation()
                                    .derive(nozzleLocation, false, false, true, false));
                        } else {
                            head.setCalibrationSecondaryFiducialLocation(head.getCalibrationSecondaryFiducialLocation()
                                    .derive(nozzleLocation, false, false, true, false));
                        }
                        if (primary) {
                            Location upp = defaultCamera.getUnitsPerPixelPrimary()
                                    .derive(nozzleLocation, false, false, true, false);
                            defaultCamera.setUnitsPerPixelPrimary(upp);
                            defaultCamera.setDefaultZ(nozzleLocation.getLengthZ());
                        } else {
                            Location upp = defaultCamera.getUnitsPerPixelSecondary()
                                    .derive(nozzleLocation, false, false, true, false);
                            defaultCamera.setUnitsPerPixelSecondary(upp);
                            defaultCamera.setEnableUnitsPerPixel3D(true);
                        }
                    }
                    if (primary) {
                        // Determine the nozzle head offset.
                        // Note 1: Remember, we reset the head offset to zero above, so the nozzle now shows the true offset.
                        // Note 2: The Z fiducial location Z was set to the default nozzle location Z (see above), so the Z offset will
                        // be 0 for the default nozzle, but equalize Z for any other nozzle.
                        Location headOffsets = head.getCalibrationPrimaryFiducialLocation().subtract(nozzleLocation);
                        if (headOffsetsBefore.getLinearLengthTo(headOffsets)
                                .compareTo(head.getCalibrationPrimaryFiducialDiameter().multiply(0.5)) < 0) {
                            // Offsets that are too close (inside the fiducial) are not updated. They might already have been calibrated and we want
                            // to keep them so i.e. these rough nozzle-aimed offsets are likely worse.
                            Logger.info("Not setting nozzle " + nozzle.getName() + " head offsets to rough " + headOffsets + " as these are close to "
                                    + "existing offsets " + headOffsetsBefore + " and existing offsets might already have been calibrated.");
                            nozzle.setHeadOffsets(headOffsetsBefore);
                        } else {
                            nozzle.setHeadOffsets(headOffsets);
                            Logger.info("Set nozzle " + nozzle.getName() + " head offsets to " + headOffsets + " (previously " + headOffsetsBefore + ")");
                            nozzle.adjustHeadOffsetsDependencies(headOffsetsBefore, headOffsets);
                        }
                    }
                    return true;
                });
            }
        });


    }
}
