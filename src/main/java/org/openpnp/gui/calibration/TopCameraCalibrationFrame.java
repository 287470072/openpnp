package org.openpnp.gui.calibration;

import com.jgoodies.forms.layout.*;
import org.openpnp.Translations;
import org.openpnp.machine.reference.ReferenceHead;
import org.openpnp.machine.reference.camera.ReferenceCamera;
import org.openpnp.machine.reference.solutions.VisionSolutions;
import org.openpnp.model.Configuration;
import org.openpnp.model.Length;
import org.openpnp.model.Location;
import org.openpnp.spi.Camera;
import org.openpnp.spi.Head;
import org.openpnp.util.SwingUtil;
import org.openpnp.util.UiUtils;
import org.openpnp.vision.pipeline.CvStage;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class TopCameraCalibrationFrame extends JFrame {

    private double featureDiameter;

    public TopCameraCalibrationFrame() {

        createUi();
    }

    public void createUi() {
        setTitle(Translations.getString("JogControlsPanel.topCameraCalibrate.Text"));

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
                        FormSpecs.PREF_COLSPEC,
                },
                new RowSpec[]{
                        FormSpecs.PREF_ROWSPEC,
                        FormSpecs.RELATED_GAP_ROWSPEC,
                        FormSpecs.PREF_ROWSPEC
                });
        panel.setLayout(layout);

        ImageIcon icon = new ImageIcon(getClass().getClassLoader().getResource("icons/gif/topcameracalibreation.gif"));
        Image image = icon.getImage();
        JLabel gifLabel = new JLabel();
        gifLabel.setIcon(SwingUtil.createAutoAdjustIcon(image, true));
        gifLabel.setPreferredSize(new Dimension(360 / 2, 422 / 2)); // 设置你想要的大小


        panel.add(gifLabel, "1, 1, fill, default");

        JLabel descryption = new JLabel(Translations.getString("TopCamera.Calibration.Frame.Descryption"));
        panel.add(descryption, "3, 1, 3, 1, fill, default");


        // 中间是一个标签
        JLabel label = new JLabel("Feature diameter");
        panel.add(label, "1, 3, center, default");

        // 右边是一个编辑框
        SpinnerNumberModel spinnerModel = new SpinnerNumberModel(18, 0, 999, 1);

        JSpinner spinner = new JSpinner(spinnerModel);


        Component mySpinnerEditor = spinner.getEditor();

        JFormattedTextField jftf = ((JSpinner.DefaultEditor) mySpinnerEditor).getTextField();
        jftf.setColumns(2);


        panel.add(spinner, "3, 3");
        spinner.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                // 当数值发生变化时触发此方法
                featureDiameter = Double.parseDouble(spinner.getValue().toString());
                UiUtils.submitUiMachineTask(() -> {
                    try {
                        VisionSolutions myUntils = new VisionSolutions();
                        // This show a diagnostic detection image in the camera view.

                        Head head = Configuration.get().getMachine().getDefaultHead();

                        for (Camera camera : head.getCameras()) {
                            if (camera instanceof ReferenceCamera && camera.getLooking() == Camera.Looking.Down) {
                                myUntils.getSubjectPixelLocation((ReferenceCamera) camera, null, new CvStage.Result.Circle(0, 0, featureDiameter), 0.05,
                                        "Diameter " + (int) featureDiameter + " px - Score {score} ", null, true);

                            }
                        }
                    } catch (Exception ee) {
                        Toolkit.getDefaultToolkit().beep();
                    }
                });
            }
        });

        // 第二行布局
        JButton calibrateButton = new JButton(Translations.getString("TopCamera.Calibration.Frame.Start"));
        panel.add(calibrateButton, "5, 3, fill, default");

        calibrateButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {

                UiUtils.submitUiMachineTask(() -> {
                    try {
                        Head head = Configuration.get().getMachine().getDefaultHead();
                        VisionSolutions myUntils = new VisionSolutions();

                        for (Camera camera : head.getCameras()) {
                            if (camera instanceof ReferenceCamera && camera.getLooking() == Camera.Looking.Down) {
                                Length fiducialDiameter = myUntils.autoCalibrateCamera((ReferenceCamera) camera, camera, featureDiameter, "Primary Fiducial & Camera Calibration", false);
                                Location fiducialLocation = myUntils.centerInOnSubjectLocation((ReferenceCamera) camera, camera, fiducialDiameter, "Primary Fiducial & Camera Calibration", false);
                                // Store it.
                                ((ReferenceHead) head).setCalibrationPrimaryFiducialLocation(fiducialLocation);
                                ((ReferenceHead) head).setCalibrationPrimaryFiducialDiameter(fiducialDiameter);
                            }
                        }
                    } catch (Exception ee) {
                        Toolkit.getDefaultToolkit().beep();
                    }
                });
            }
        });

        setSize(350, 290);
        setLocationRelativeTo(null); // 居中显示，相对于主窗口


        getContentPane().add(panel);


    }
}
