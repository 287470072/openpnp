package org.openpnp.gui.calibration;

import com.jgoodies.forms.layout.*;
import org.openpnp.Translations;
import org.openpnp.machine.reference.camera.ReferenceCamera;
import org.openpnp.machine.reference.solutions.VisionSolutions;
import org.openpnp.model.Configuration;
import org.openpnp.spi.Camera;
import org.openpnp.spi.Head;
import org.openpnp.util.UiUtils;
import org.openpnp.vision.pipeline.CvStage;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;

public class CalibrationFrame extends JFrame {

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
        JSpinner spinner = new JSpinner();

        Component mySpinnerEditor = spinner.getEditor();

        JFormattedTextField jftf = ((JSpinner.DefaultEditor) mySpinnerEditor).getTextField();
        jftf.setColumns(2);


        panel.add(spinner, "5, 1");
        spinner.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                // 当数值发生变化时触发此方法
                int value = (int) spinner.getValue();
                System.out.println("当前值：" + value);
                UiUtils.submitUiMachineTask(() -> {
                    try {
                        VisionSolutions myUntils = new VisionSolutions();
                        // This show a diagnostic detection image in the camera view.

                        Head head = Configuration.get().getMachine().getDefaultHead();

                        for (Camera camera : head.getCameras()) {
                            if (camera instanceof ReferenceCamera && camera.getLooking() == Camera.Looking.Down) {
                                myUntils.getSubjectPixelLocation((ReferenceCamera) camera, null, new CvStage.Result.Circle(0, 0, value), 0.05,
                                        "Diameter " + (int) value + " px - Score {score} ", null, true);

                            }
                        }
                    } catch (Exception ee) {
                        Toolkit.getDefaultToolkit().beep();
                    }
                });
            }
        });

        // 第二行布局
        JButton newButton = new JButton("新按钮");
        panel.add(newButton, "3, 3, fill, default");

        setSize(278, 174);
        setLocationRelativeTo(null); // 居中显示，相对于主窗口


        getContentPane().add(panel);


    }
}
