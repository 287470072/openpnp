package org.openpnp.gui.calibration;

import com.jgoodies.forms.layout.ColumnSpec;
import com.jgoodies.forms.layout.FormLayout;
import com.jgoodies.forms.layout.FormSpecs;
import com.jgoodies.forms.layout.RowSpec;
import org.openpnp.Translations;
import org.openpnp.machine.reference.ReferenceHead;
import org.openpnp.machine.reference.ReferenceNozzleTip;
import org.openpnp.machine.reference.camera.ReferenceCamera;
import org.openpnp.machine.reference.solutions.VisionSolutions;
import org.openpnp.model.Configuration;
import org.openpnp.model.Length;
import org.openpnp.model.Location;
import org.openpnp.spi.Camera;
import org.openpnp.spi.Head;
import org.openpnp.spi.Nozzle;
import org.openpnp.util.SwingUtil;
import org.openpnp.util.UiUtils;
import org.openpnp.vision.pipeline.CvStage;
import org.pmw.tinylog.Logger;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class BottomCameraCalibrationFrame extends JFrame {

    private double featureDiameter;

    private ReferenceNozzleTip referenceNozzleTip = null;

    private Length oldVisionDiameter = null;

    public BottomCameraCalibrationFrame() {

        createUi();
    }

    public void createUi() {
        setTitle(Translations.getString("JogControlsPanel.bottomCameraCalibrate.Text"));

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

        ImageIcon icon = new ImageIcon(getClass().getClassLoader().getResource("icons/gif/bottomcameracalibreation.jpg"));
        Image image = icon.getImage();
        JLabel gifLabel = new JLabel();
        gifLabel.setIcon(SwingUtil.createAutoAdjustIcon(image, true));
        gifLabel.setPreferredSize(new Dimension(360 / 2, 422 / 2)); // 设置你想要的大小


        panel.add(gifLabel, "1, 1, fill, default");
        
        JLabel descryption = new JLabel("<html><body>1>:移动十字光标对准校准板的标记点<br/><br/>" +
                "2>:点击数字的加减键修改数字使绿色框套住标记点<br/><br/>" +
                "3>:点击校准<br/></body></html>");
        panel.add(descryption, "3, 1, 3, 1, fill, default");


        // 中间是一个标签
        JLabel label = new JLabel("Feature diameter");
        panel.add(label, "1, 3, fill, default");

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

                        for (Camera camera : Configuration.get().getMachine().getCameras()) {
                            if (camera instanceof ReferenceCamera && camera.getLooking() == Camera.Looking.Up) {
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

        setSize(350, 290);
        setLocationRelativeTo(null); // 居中显示，相对于主窗口
        getContentPane().add(panel);
        
        
                // 第二行布局
                JButton calibrateButton = new JButton("开始校准");
                panel.add(calibrateButton, "5, 3, fill, default");
                
                        calibrateButton.addActionListener(new ActionListener() {
                            @Override
                            public void actionPerformed(ActionEvent e) {
                                UiUtils.submitUiMachineTask(
                                        () -> {
                                            Head head = Configuration.get().getMachine().getDefaultHead();
                                            Nozzle n1 = head.getNozzles().get(0);
                                            referenceNozzleTip = (n1.getNozzleTip() instanceof ReferenceNozzleTip) ?
                                                    (ReferenceNozzleTip) n1.getNozzleTip() : null;
                                            if (referenceNozzleTip == null) {
                                                throw new Exception("The nozzle " + n1.getName() + " has no nozzle tip loaded.");
                                            }
                                            oldVisionDiameter = referenceNozzleTip.getCalibration().getCalibrationTipDiameter();
                                            VisionSolutions myUntils = new VisionSolutions();
                                            for (Camera camera : Configuration.get().getMachine().getCameras()) {
                                                if (camera instanceof ReferenceCamera && camera.getLooking() == Camera.Looking.Up) {
                
                                                    final Location oldCameraOffsets = ((ReferenceCamera) camera).getHeadOffsets();
                
                                                    // Perform preliminary camera calibration.
                                                    Length visionDiameter = myUntils.autoCalibrateCamera((ReferenceCamera) camera, n1, Double.valueOf(featureDiameter), "Camera Positional Calibration", false);
                                                    // Get the nozzle location.
                                                    Location nozzleLocation = myUntils.centerInOnSubjectLocation((ReferenceCamera) camera, n1, visionDiameter, "Camera Positional Calibration", false);
                                                    // Determine the camera offsets, the nozzle now shows the true offset.
                                                    Location headOffsets = nozzleLocation;
                                                    ((ReferenceCamera) camera).setHeadOffsets(nozzleLocation);
                                                    Logger.info("Set camera " + camera.getName() + " offsets to " + headOffsets
                                                            + " (previously " + oldCameraOffsets + ")");
                                                    referenceNozzleTip.getCalibration().setCalibrationTipDiameter(visionDiameter);
                                                    Logger.info("Set nozzle tip " + referenceNozzleTip.getName() + " vision diameter to " + visionDiameter + " (previously " + oldVisionDiameter + ")");
                
                                                }
                                            }
                                        });
                            }
                
                        });


    }
}
