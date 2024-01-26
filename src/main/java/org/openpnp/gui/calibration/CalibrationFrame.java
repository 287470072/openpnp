package org.openpnp.gui.calibration;

import com.jgoodies.forms.layout.*;
import org.openpnp.Translations;
import org.openpnp.logging.Logger;
import org.openpnp.machine.reference.ReferenceHead;
import org.openpnp.machine.reference.camera.ReferenceCamera;
import org.openpnp.machine.reference.solutions.VisionSolutions;
import org.openpnp.model.Configuration;
import org.openpnp.model.Length;
import org.openpnp.model.Location;
import org.openpnp.spi.Camera;
import org.openpnp.spi.Head;
import org.openpnp.util.UiUtils;
import org.openpnp.vision.pipeline.CvStage;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class CalibrationFrame extends JFrame {

    private double featureDiameter;

    public CalibrationFrame() {

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
                        FormSpecs.PREF_COLSPEC
                },
                new RowSpec[]{
                        FormSpecs.PREF_ROWSPEC,
                        FormSpecs.RELATED_GAP_ROWSPEC,
                        FormSpecs.PREF_ROWSPEC
                });
        panel.setLayout(layout);

        ImageIcon icon = new ImageIcon(getClass().getClassLoader().getResource("icons/gif/1.gif"));
        Image image = icon.getImage();
        Image newImage = image.getScaledInstance(100, 100, java.awt.Image.SCALE_SMOOTH);
        JLabel gifLabel = new JLabel(icon);
        gifLabel.setPreferredSize(new Dimension(100, 100)); // 设置你想要的大小


        panel.add(gifLabel, "1, 1, fill, default");


        // 中间是一个标签
        JLabel label = new JLabel("Feature diameter");
        panel.add(label, "3, 1, fill, default");

        // 右边是一个编辑框
        SpinnerNumberModel spinnerModel = new SpinnerNumberModel(18, 0, 99, 1);

        JSpinner spinner = new JSpinner(spinnerModel);


        Component mySpinnerEditor = spinner.getEditor();

        JFormattedTextField jftf = ((JSpinner.DefaultEditor) mySpinnerEditor).getTextField();
        jftf.setColumns(2);


        panel.add(spinner, "5, 1");
        spinner.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                // 当数值发生变化时触发此方法
                featureDiameter = Double.parseDouble(spinner.getValue().toString()) ;
                System.out.println("当前值：" + featureDiameter);
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
        JButton calibrateButton = new JButton("开始校准");
        panel.add(calibrateButton, "3, 3, fill, default");

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

        setSize(278, 174);
        setLocationRelativeTo(null); // 居中显示，相对于主窗口


        getContentPane().add(panel);


    }
}
