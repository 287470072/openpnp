package org.openpnp.vision.pipeline.stages;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.openpnp.logging.Logger;
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
        // 获取管道中设置的相机对象
        Camera camera = (Camera) pipeline.getProperty("camera");

        // 检查相机是否为null，如果为null则抛出异常
        if (camera == null) {
            throw new Exception("No Camera set on pipeline.");
        }

        try {
            // 在图像捕获之前执行光照控制（如果默认光照未启用则设置特定光照）
            camera.actuateLightBeforeCapture((defaultLight ? null : getLight()));

            try {
                BufferedImage bufferedImage;
                boolean needSettle = true;

                // 如果相机的视角为向上并且管道中设置了"needSettle"属性，则获取"needSettle"属性的值
                if (camera.getLooking() == Camera.Looking.Up && pipeline.getProperty("needSettle") != null) {
                    needSettle = (boolean) pipeline.getProperty("needSettle");
                }

                // 根据需要执行相机的稳定和捕获操作，或者直接捕获图像
                if (needSettle) {

                    bufferedImage = camera.settleAndCapture(settleOption);
                } else {
                    bufferedImage = camera.capture();
                }

                // 记录最后捕获的图像，这将记录原始相机图像而不应用平均（用于分析目的）
                pipeline.setLastCapturedImage(bufferedImage);
                Mat image = OpenCvUtils.toMat(bufferedImage);

                // 如果只有一帧图像，直接返回结果
                if (count <= 1) {
                    return new Result(image, ColorSpace.Bgr);
                } else {
                    // 对图像进行平均处理，通道类型为double
                    image.convertTo(image, CvType.CV_64F);
                    Mat avgImage = image;
                    double beta = 1.0 / count;
                    Core.addWeighted(avgImage, 0, image, beta, 0, avgImage); // avgImage = image/count

                    // 循环进行图像平均处理
                    for (int i = 1; i < count; i++) {
                        image = OpenCvUtils.toMat(camera.capture());
                        image.convertTo(image, CvType.CV_64F);
                        Core.addWeighted(avgImage, 1, image, beta, 0, avgImage); // avgImage = avgImag + image/count
                        // 释放额外的图像资源
                        image.release();
                    }

                    // 将平均图像转换回CV_8U类型
                    avgImage.convertTo(avgImage, CvType.CV_8U);

                    // 返回平均后的结果
                    return new Result(avgImage, ColorSpace.Bgr);
                }
            } finally {
                // 捕获图像后，始终关闭光照
                camera.actuateLightAfterCapture();
            }
        } catch (Exception e) {
            // 处理异常，将其包装为终止异常并抛出
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
