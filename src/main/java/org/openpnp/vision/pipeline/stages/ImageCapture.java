package org.openpnp.vision.pipeline.stages;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.openpnp.spi.Actuator;
import org.openpnp.spi.Camera;
import org.openpnp.spi.Camera.SettleOption;
import org.openpnp.util.OpenCvUtils;
import org.openpnp.vision.FluentCv.ColorSpace;
import org.openpnp.vision.pipeline.*;
import org.openpnp.vision.pipeline.ui.PipelinePropertySheetTable;
import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.core.Commit;

import java.awt.image.BufferedImage;
import java.util.List;


@Stage(
        category = "Image Processing",
        description = "Capture an image from the pipeline camera.")

public class ImageCapture extends CvStage {
    @Attribute(required = false)
    @Property(description = "Use the default camera lighting.")
    private boolean defaultLight = true;

    @Element(required = false)
    @Property(description = "Light actuator value or profile, if default camera lighting is disabled.")
    private Object light = null;

    @Deprecated
    @Attribute(required = false)
    private Boolean settleFirst = false;

    @Attribute(required = false)
    @Property(description = "Wait for the camera to settle before capturing an image.")
    private SettleOption settleOption;

    @Attribute(required = false)
    @Property(description = "Number of camera images to average.")
    private int count = 1;

    @Commit
    void commit() {
        if (settleFirst != null) {
            settleOption = SettleOption.Settle;
            settleFirst = null;
        }
    }

    public boolean isDefaultLight() {
        return defaultLight;
    }

    public void setDefaultLight(boolean defaultLight) {
        this.defaultLight = defaultLight;
    }

    public Object getLight() {
        return light;
    }

    public void setLight(Object light) {
        this.light = light;
    }

    public SettleOption getSettleOption() {
        return settleOption;
    }

    public void setSettleOption(SettleOption settleOption) {
        this.settleOption = settleOption;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        if (count > 0) {
            this.count = count;
        } else {
            this.count = 1;
        }
    }

    @Override
    public Result process(CvPipeline pipeline) throws Exception {
        Camera camera = (Camera) pipeline.getProperty("camera");
        if (camera == null) {
            throw new Exception("No Camera set on pipeline.");
        }
        try {
            camera.actuateLightBeforeCapture((defaultLight ? null : getLight()));
            try {
                BufferedImage bufferedImage;
                boolean needSettle = true;
                if (camera.getLooking() == Camera.Looking.Up && pipeline.getProperty("needSettle") != null) {
                    needSettle = (boolean) pipeline.getProperty("needSettle");
                }
                if (needSettle) {
                    bufferedImage = camera.settleAndCapture(settleOption);
                } else {
                    bufferedImage = camera.capture();
                }

                //BufferedImage bufferedImage = camera.settleAndCapture(settleOption);

                // Remember the last captured image. This specifically records the native camera image,
                // i.e. it does not apply averaging (we want an unaltered raw image for analysis purposes).
                pipeline.setLastCapturedImage(bufferedImage);
                Mat image = OpenCvUtils.toMat(bufferedImage);
                if (count <= 1) {
                    return new Result(image, ColorSpace.Bgr);
                } else {
                    // Perform averaging in channel type double.
                    image.convertTo(image, CvType.CV_64F);
                    Mat avgImage = image;
                    double beta = 1.0 / count;
                    Core.addWeighted(avgImage, 0, image, beta, 0, avgImage); // avgImage = image/count
                    for (int i = 1; i < count; i++) {
                        image = OpenCvUtils.toMat(camera.capture());
                        image.convertTo(image, CvType.CV_64F);
                        Core.addWeighted(avgImage, 1, image, beta, 0, avgImage); // avgImage = avgImag + image/count
                        // Release the additional image.
                        image.release();
                    }
                    avgImage.convertTo(avgImage, CvType.CV_8U);
                    return new Result(avgImage, ColorSpace.Bgr);
                }
            } finally {
                // Always switch off the light.
                camera.actuateLightAfterCapture();
            }
        } catch (
                Exception e) {
            // These machine exceptions are terminal to the pipeline.
            throw new TerminalException(e);
        }

    }

    @Override
    public void customizePropertySheet(PipelinePropertySheetTable table, CvPipeline pipeline) {
        super.customizePropertySheet(table, pipeline);
        Camera camera = (Camera) pipeline.getProperty("camera");
        if (camera != null) {
            Actuator actuator = camera.getLightActuator();
            String propertyName = "light";
            table.customizeActuatorProperty(propertyName, actuator);
        }
    }
}
