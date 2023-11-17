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

package org.openpnp.gui.components;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.lang.reflect.Method;
import java.util.*;
import java.util.List;
import java.util.Map.Entry;
import java.util.prefs.Preferences;

import javax.swing.*;

import com.github.sarxos.webcam.Webcam;
import com.github.sarxos.webcam.WebcamDevice;
import com.github.sarxos.webcam.WebcamDiscoveryEvent;
import com.github.sarxos.webcam.WebcamDiscoveryListener;
import org.openpnp.ConfigurationListener;
import org.openpnp.capture.CaptureDevice;
import org.openpnp.capture.CaptureFormat;
import org.openpnp.gui.JobPanel;
import org.openpnp.gui.MainFrame;
import org.openpnp.gui.support.CameraItem;
import org.openpnp.machine.reference.camera.OpenPnpCaptureCamera;
import org.openpnp.model.Configuration;
import org.openpnp.spi.Camera;
import org.openpnp.spi.base.AbstractCamera;
import org.openpnp.util.VisionUtils;
import org.pmw.tinylog.Logger;

/**
 * Shows a square grid of cameras or a blown up image from a single camera.
 */
@SuppressWarnings("serial")
public class CameraPanel extends JPanel implements WebcamDiscoveryListener {
    private static final String SHOW_NONE_ITEM = "Show None";
    private static final String SHOW_ALL_ITEM_H = "Show All Horizontal";
    private static final String SHOW_ALL_ITEM_V = "Show All Vertical";

    private Map<Camera, CameraView> cameraViews = new LinkedHashMap<>();

    private List<Webcam> webcams = new ArrayList<>();

    private JComboBox camerasCombo;
    private JPanel camerasPanel;

    private CameraView selectedCameraView;


    private static final String PREF_SELECTED_CAMERA_VIEW = "JobPanel.dividerPosition";
    private Preferences prefs = Preferences.userNodeForPackage(CameraPanel.class);

    public CameraPanel() {
        SwingUtilities.invokeLater(() -> {
            createUi();
        });
        Configuration.get().addListener(new ConfigurationListener.Adapter() {
            @Override
            public void configurationComplete(Configuration configuration) throws Exception {
                SwingUtilities.invokeLater(() -> {
                    for (Camera camera : configuration.getMachine().getAllCameras()) {
                        addCamera(camera);
                    }
                    webcamDiscoveryListener();

                    String selectedCameraView = prefs.get(PREF_SELECTED_CAMERA_VIEW, null);
                    if (selectedCameraView != null) {
                        for (int i = 0; i < camerasCombo.getItemCount(); i++) {
                            Object o = camerasCombo.getItemAt(i);
                            if (o.toString().equals(selectedCameraView)) {
                                camerasCombo.setSelectedItem(o);
                            }
                        }
                    }
                    camerasCombo.addActionListener((event) -> {
                        try {
                            prefs.put(PREF_SELECTED_CAMERA_VIEW,
                                    camerasCombo.getSelectedItem().toString());
                            prefs.flush();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    });
                });
            }
        });
    }

    public void addCamera(Camera camera) {
        CameraView cameraView = new CameraView();
        cameraView.setCamera(camera);
        cameraViews.put(camera, cameraView);
        camerasCombo.addItem(new CameraItem(camera));
        if (cameraViews.size() == 1) {
            // First camera being added, so select it
            camerasCombo.setSelectedIndex(1);
        } else if (cameraViews.size() == 2) {
            // Otherwise this is the second camera so mix in the
            // show all item.
            camerasCombo.insertItemAt(SHOW_ALL_ITEM_H, 1);
            camerasCombo.insertItemAt(SHOW_ALL_ITEM_V, 2);
        }
        if (camera instanceof AbstractCamera) {
            ((AbstractCamera) camera).addPropertyChangeListener("shownInMultiCameraView",
                    new PropertyChangeListener() {

                        @Override
                        public void propertyChange(PropertyChangeEvent evt) {
                            if (evt.getOldValue() == null
                                    || !evt.getOldValue().equals(evt.getNewValue())) {
                                SwingUtilities.invokeLater(() -> relayoutPanel());
                            }
                        }
                    });
        }
        relayoutPanel();
    }


    public void addCamera2(Camera camera) {
        cameraViews.forEach((key, value) -> {
            CameraView cameraView = value;
            if (value.getCamera().getName().equals(camera.getName())) {
                cameraView.setCamera(camera);
            }
        });
/*        CameraView cameraView = new CameraView();
        cameraView.setCamera(camera);
        cameraViews.put(camera, cameraView);
        camerasCombo.addItem(new CameraItem(camera));
        if (camera instanceof AbstractCamera) {
            ((AbstractCamera) camera).addPropertyChangeListener("shownInMultiCameraView",
                    new PropertyChangeListener() {

                        @Override
                        public void propertyChange(PropertyChangeEvent evt) {
                            if (evt.getOldValue() == null
                                    || !evt.getOldValue().equals(evt.getNewValue())) {
                                SwingUtilities.invokeLater(() -> relayoutPanel());
                            }
                        }
                    });
        }*/
        relayoutPanel();
    }

    public void removeCamera(Camera camera) {
        CameraView cameraView = cameraViews.remove(camera);
        if (cameraView == null) {
            return;
        }
        for (int i = 0; i < camerasCombo.getItemCount(); i++) {
            Object o = camerasCombo.getItemAt(i);
            if (o instanceof CameraItem) {
                CameraItem cameraItem = (CameraItem) o;
                if (cameraItem.getCamera() == camera) {
                    camerasCombo.removeItemAt(i);
                    break;
                }
            }
        }
        relayoutPanel();
    }

    private void createUi() {
        camerasPanel = new JPanel();

        camerasCombo = new JComboBox();
        camerasCombo.addActionListener(cameraSelectedAction);

        setLayout(new BorderLayout());

        camerasCombo.addItem(SHOW_NONE_ITEM);

        add(camerasCombo, BorderLayout.NORTH);
        add(camerasPanel);
    }

    /**
     * Make sure the given Camera is visible in the UI. If All Cameras is selected we do nothing,
     * otherwise we select the specified Camera.
     *
     * @param camera
     * @return The CameraView.
     */
    public CameraView ensureCameraVisible(Camera camera) {
        if (camerasCombo.getSelectedItem().equals(SHOW_ALL_ITEM_H) || camerasCombo.getSelectedItem().equals(SHOW_ALL_ITEM_V)) {
            return getCameraView(camera);
        }
        return setSelectedCamera(camera);
    }

    public CameraView setSelectedCamera(Camera camera) {
        if (selectedCameraView != null && selectedCameraView.getCamera() == camera) {
            return selectedCameraView;
        }
        for (int i = 0; i < camerasCombo.getItemCount(); i++) {
            Object o = camerasCombo.getItemAt(i);
            if (o instanceof CameraItem) {
                Camera c = ((CameraItem) o).getCamera();
                if (c == camera) {
                    camerasCombo.setSelectedIndex(i);
                    return selectedCameraView;
                }
            }
        }
        return null;
    }

    public CameraView getCameraView(Camera camera) {
        return cameraViews.get(camera);
    }

    public void relayoutPanel() {
        selectedCameraView = null;
        camerasPanel.removeAll();
        if (camerasCombo.getSelectedItem().equals(SHOW_NONE_ITEM)) {
            camerasPanel.setLayout(new BorderLayout());
            JPanel panel = new JPanel();
            panel.setBackground(Color.black);
            camerasPanel.add(panel);
            selectedCameraView = null;
        } else if (camerasCombo.getSelectedItem().equals(SHOW_ALL_ITEM_H) || camerasCombo.getSelectedItem().equals(SHOW_ALL_ITEM_V)) {
            LinkedHashMap<Camera, CameraView> cameraViews = new LinkedHashMap<>();
            for (Entry<Camera, CameraView> entry : this.cameraViews.entrySet()) {
                if (entry.getKey().isShownInMultiCameraView()) {
                    cameraViews.put(entry.getKey(), entry.getValue());
                }
            }

            int rows = (int) Math.ceil(Math.sqrt(cameraViews.size()));
            if (rows == 0) {
                rows = 1;
            }
            if (camerasCombo.getSelectedItem().equals(SHOW_ALL_ITEM_H)) {
                camerasPanel.setLayout(new GridLayout(rows, 0, 1, 1));
            } else {
                camerasPanel.setLayout(new GridLayout(0, rows, 1, 1));
            }

            for (CameraView cameraView : cameraViews.values()) {
                cameraView.setShowName(true);
                camerasPanel.add(cameraView);
                if (cameraViews.size() == 1) {
                    selectedCameraView = cameraView;
                }
            }
            if (cameraViews.size() > 2) {
                for (int i = 0; i < (rows * rows) - cameraViews.size(); i++) {
                    JPanel panel = new JPanel();
                    panel.setBackground(Color.black);
                    camerasPanel.add(panel);
                }
            }
            selectedCameraView = null;
        } else {
            camerasPanel.setLayout(new BorderLayout());
            Camera camera = ((CameraItem) camerasCombo.getSelectedItem()).getCamera();
            CameraView cameraView = getCameraView(camera);
            cameraView.setShowName(false);
            camerasPanel.add(cameraView);

            selectedCameraView = cameraView;
        }
        revalidate();
        repaint();
    }

    private AbstractAction cameraSelectedAction = new AbstractAction("") {
        @Override
        public void actionPerformed(ActionEvent ev) {
            relayoutPanel();
        }
    };

    public void webcamDiscoveryListener() {
        for (Webcam webcam : Webcam.getWebcams()) {
            Logger.info("Webcam detected: " + webcam.getName());
        }
        Webcam.addDiscoveryListener(this);

        //Logger.info("\n\nPlease connect additional webcams, or disconnect already connected ones. Listening for events...");
    }

    @Override
    public void webcamFound(WebcamDiscoveryEvent event) {

        cameraBind();

        for (Camera camera : Configuration.get().getMachine().getAllCameras()) {
            String webCamera = event.getWebcam().getDevice().getName().substring(0, event.getWebcam().getDevice().getName().length() - 2);
            String cameraName = camera.getName();
            if (cameraName != null & webCamera.equals(cameraName)) {
                addCamera2(camera);
                webcams.clear();
            }

        }
    }

    public void cameraPanelBind() {
        //摄像头自动匹配
        OpenPnpCaptureCamera openPnpCaptureCamera = new OpenPnpCaptureCamera();
        List<CaptureDevice> captureDevices = openPnpCaptureCamera.getCaptureDevices();
        List<Camera> cameras = Configuration.get().getMachine().getAllCameras();


        for (Camera camera1 : cameras) {
            if (camera1 instanceof OpenPnpCaptureCamera) {
                OpenPnpCaptureCamera openPnpCaptureCamera1 = (OpenPnpCaptureCamera) camera1;
                OpenPnpCaptureCamera.CapturePropertyHolder gamma = openPnpCaptureCamera1.getGamma();
                gamma.setValue(100);
                String openPnpCaptureCamera1Name = openPnpCaptureCamera1.getName();
                int startIndex = openPnpCaptureCamera1Name.indexOf('[');
                if (startIndex != -1) {
                    openPnpCaptureCamera1Name = openPnpCaptureCamera1Name.substring(0, startIndex);
                }
                for (CaptureDevice captureDevice : captureDevices) {
                    String captureDeviceName = captureDevice.getName();
                    if (openPnpCaptureCamera1Name.equals(captureDeviceName)) {
                        ((OpenPnpCaptureCamera) camera1).setDevice(captureDevice);

                        //设置分辨率
                        List<CaptureFormat> formats = captureDevice.getFormats();
                        if (camera1.getLooking() == Camera.Looking.Up) {
                            String cameraName = camera1.getName();
                            startIndex = cameraName.indexOf('[');
                            int endIndex = cameraName.indexOf(']', startIndex);
                            if (startIndex != -1 && endIndex != -1) {
                                String pixelNubmer = cameraName.substring(startIndex + 1, endIndex);
                                int cameraIndex = Integer.parseInt(pixelNubmer) - 1;
                                if (cameraIndex < 0) {
                                    cameraIndex = 0;
                                }
                                ((OpenPnpCaptureCamera) camera1).setFormat(formats.get(cameraIndex));
                            }

                        } else {
                            ((OpenPnpCaptureCamera) camera1).setFormat(formats.get(0));

                        }

                        addCamera2(camera1);
                    }
                }
            }
        }


    }

    public void cameraBind() {
        //更换USB口时，摄像头自动匹配
        OpenPnpCaptureCamera openPnpCaptureCamera = new OpenPnpCaptureCamera();
        List<CaptureDevice> captureDevices = openPnpCaptureCamera.getCaptureDevices();
        List<Camera> cameras = Configuration.get().getMachine().getAllCameras();
        for (Camera camera1 : cameras) {
            if (camera1 instanceof OpenPnpCaptureCamera) {
                OpenPnpCaptureCamera openPnpCaptureCamera1 = (OpenPnpCaptureCamera) camera1;
                String openPnpCaptureCamera1Name = openPnpCaptureCamera1.getName();
                for (CaptureDevice captureDevice : captureDevices) {
                    String captureDeviceName = captureDevice.getName();
                    if (openPnpCaptureCamera1Name.equals(captureDeviceName)) {
                        ((OpenPnpCaptureCamera) camera1).setDevice(captureDevice);
                        List<CaptureFormat> formats = captureDevice.getFormats();
                        if (camera1.getLooking() == Camera.Looking.Up) {
                            ((OpenPnpCaptureCamera) camera1).setFormat(formats.get(3));

                        } else {
                            ((OpenPnpCaptureCamera) camera1).setFormat(formats.get(0));

                        }
                    }
                }
            }
        }
    }

    @Override
    public void webcamGone(WebcamDiscoveryEvent event) {
        for (Camera camera : Configuration.get().getMachine().getAllCameras()) {
            String webCamera = event.getWebcam().getDevice().getName().substring(0, event.getWebcam().getDevice().getName().length() - 2);
            String camName = camera.getName();
            String cameraName = camera.getName();
            if (cameraName != null & webCamera.equals(cameraName)) {
                //removeCamera(camera);
            }
        }


        webcams.add(event.getWebcam());
        Logger.info("Webcam disconnected: " + event.getWebcam().getName());
    }
}
