package org.openpnp.util;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.*;

import com.google.zxing.*;
import com.google.zxing.qrcode.QRCodeReader;
import org.apache.commons.io.IOUtils;
import org.openpnp.machine.reference.camera.OpenPnpCaptureCamera;
import org.openpnp.machine.reference.vision.AbstractPartAlignmentMulti;
import org.openpnp.machine.reference.vision.ReferenceBottomVision;
import org.openpnp.machine.reference.vision.ReferenceFiducialLocator;
import org.openpnp.model.*;
import org.openpnp.model.Footprint.Pad;
import org.openpnp.model.Package;
import org.openpnp.spi.*;
import org.openpnp.spi.PartAlignment.PartAlignmentOffset;
import org.openpnp.vision.pipeline.CvPipeline;

import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import org.pmw.tinylog.Logger;

import javax.imageio.ImageIO;

public class VisionUtils {
    final public static String PIPELINE_RESULTS_NAME = "results";
    final public static String PIPELINE_CONTROL_PROPERTY_NAME = "propertyName";

    private static final EnumMap<DecodeHintType, Object> hints = new EnumMap<DecodeHintType, Object>(DecodeHintType.class);

    static {
        //设置解析二维码后信息的字符集
        hints.put(DecodeHintType.CHARACTER_SET, "UTF-8");


    }

    /**
     * Given pixel coordinates within the frame of the Camera's image, get the offsets from Camera
     * center to the coordinates in Camera space and units. The resulting value is the distance the
     * Camera can be moved to be centered over the pixel coordinates.
     * <p>
     * Example: If the x, y coordinates describe a position above and to the left of the center of
     * the camera the offsets will be -,+.
     * <p>
     * If the coordinates position are below and to the right of center the offsets will be +, -.
     * <p>
     * Calling camera.getLocation().add(getPixelCenterOffsets(...) will give you the location of x,
     * y with respect to the center of the camera.
     *
     * @param camera
     * @param x
     * @param y
     * @return
     */
    public static Location getPixelCenterOffsets(Camera camera, double x, double y) {
        double imageWidth;
        double imageHeight;
        Serial serial = Configuration.get().getSerial();
        if (serial != null & serial.isCertification() && camera.getLooking() == Camera.Looking.Up && camera.isTwoCamera()) {
            imageWidth = (double) camera.getWidth() / 2;
            imageHeight = camera.getHeight();
        } else {
            imageWidth = camera.getWidth();
            imageHeight = camera.getHeight();
        }

        // Calculate the difference between the center of the image to the
        // center of the match.
        double offsetX = x - (imageWidth / 2);
        double offsetY = y - (imageHeight / 2);
        //Logger.trace("imageWidth:" + imageWidth + "|imageHeight:" + imageHeight + "|x:" + x + "|y:" + y + "|offsetX:" + offsetX + "|offsetY:" + offsetY);

        return getPixelOffsets(camera, offsetX, offsetY);
    }

    public static Location getPixelCenterOffsets2(Camera camera, double x, double y) {
        double imageWidth;
        double imageHeight;

        imageWidth = camera.getWidth();
        imageHeight = camera.getHeight();


        // Calculate the difference between the center of the image to the
        // center of the match.
        double offsetX = x - (imageWidth / 2);
        double offsetY = y - (imageHeight / 2);

        return getPixelOffsets(camera, offsetX, offsetY);
    }

    /**
     * Given pixel offset coordinates, get the same offsets in Camera space and units.
     *
     * @param camera
     * @param offsetX
     * @param offsetY
     * @return
     */
    public static Location getPixelOffsets(Camera camera, double offsetX, double offsetY) {
        // Convert pixels to units
        Location unitsPerPixel = camera.getUnitsPerPixelAtZ();
        offsetX *= unitsPerPixel.getX();
        offsetY *= unitsPerPixel.getY();
        // Convert to right-handed.
        return new Location(unitsPerPixel.getUnits(), offsetX, -offsetY, 0, 0);
    }

    /**
     * Get the Location of a set of pixel coordinates referenced to the center of the given camera.
     * This is a helper method that simply adds the offsets from
     * {@link VisionUtils#getPixelCenterOffsets(Camera, double, double)} to the Camera's current
     * location.
     *
     * @param camera
     * @param x
     * @param y
     * @return
     */
    public static Location getPixelLocation(Camera camera, double x, double y) {
        return camera.getLocation().add(getPixelCenterOffsets(camera, x, y));
    }

    /**
     * Same as getPixelLocation() but including the tool specific calibration offset.
     *
     * @param camera
     * @param tool
     * @param x
     * @param y
     * @return
     */
    public static Location getPixelLocation(Camera camera, HeadMountable tool, double x, double y) {
        return camera.getLocation(tool).add(getPixelCenterOffsets(camera, x, y));
    }

    /**
     * Get an angle in the OpenPNP coordinate system from an angle in the camera pixel
     * coordinate system.
     * The angle needs to be sign reversed to reflect the fact that the Z and Y axis are sign reversed.
     * OpenPNP uses a coordinate system with Z pointing towards the viewer, Y pointing up. OpenCV
     * however uses one with Z pointing away from the viewer, Y pointing downwards. Right-handed
     * rotation must be sign-reversed.
     * See {@link VisionUtils#getPixelCenterOffsets(Camera, double, double)}.
     *
     * @param camera
     * @param angle
     * @return
     */
    public static double getPixelAngle(Camera camera, double angle) {
        return -angle;
    }

    public static List<Location> sortLocationsByDistance(final Location origin,
                                                         List<Location> locations) {
        // sort the results by distance from center ascending
        Collections.sort(locations, new Comparator<Location>() {
            public int compare(Location o1, Location o2) {
                Double o1d = origin.getLinearDistanceTo(o1);
                Double o2d = origin.getLinearDistanceTo(o2);
                return o1d.compareTo(o2d);
            }
        });
        return locations;
    }

    public static Camera getBottomVisionCamera() {
        for (Camera camera : Configuration.get().getMachine().getCameras()) {
            if (camera.getLooking() == Camera.Looking.Up) {
                return camera;
            }
        }
        //throw new Exception("No up-looking camera found on the machine to use for bottom vision.");
        return null;
    }

    public static Camera getTopVisionCamera() throws Exception {
        List<Camera> camers = Configuration.get().getMachine().getHeads().get(0).getCameras();
        for (Camera camera : camers) {
            if (camera.getLooking() == Camera.Looking.Down) {
                return camera;
            }

        }
        throw new Exception("No up-looking camera found on the machine to use for top vision.");
    }

    public static double toPixels(Length length, Camera camera) {
        // convert inputs to the same units
        Location unitsPerPixel = camera.getUnitsPerPixelAtZ();
        length = length.convertToUnits(unitsPerPixel.getUnits());

        // we average the units per pixel because circles can't be ovals
        double avgUnitsPerPixel = (unitsPerPixel.getX() + unitsPerPixel.getY()) / 2;

        // convert it all to pixels
        return length.getValue() / avgUnitsPerPixel;
    }

    public static double toPixels(Area area, Camera camera) {
        // convert inputs to the same units
        Location unitsPerPixel = camera.getUnitsPerPixel();
        area = area.convertToUnits(AreaUnit.fromLengthUnit(unitsPerPixel.getUnits()));

        // convert it all to pixels
        return area.getValue() / (unitsPerPixel.getX() * unitsPerPixel.getY());
    }

    /**
     * Get a location in camera pixels. This is the reverse transformation of getPixelLocation().
     *
     * @param location
     * @param camera
     * @return
     */
    public static Point getLocationPixels(Camera camera, Location location) {
        return getLocationPixels(camera, null, location);
    }

    /**
     * Get a location in camera pixels. This is the reverse transformation of getPixelLocation(tool).
     * This overload includes the tool specific calibration offset.
     *
     * @param camera
     * @param tool
     * @param location
     * @return
     */
    public static Point getLocationPixels(Camera camera, HeadMountable tool, Location location) {
        Point center = getLocationPixelCenterOffsets(camera, tool, location);
        return new Point(center.getX() + camera.getWidth() / 2, center.getY() + camera.getHeight() / 2);
    }

    /**
     * Get a location camera center offsets in pixels.
     *
     * @param location
     * @param camera
     * @return
     */
    public static Point getLocationPixelCenterOffsets(Camera camera, Location location) {
        return getLocationPixelCenterOffsets(camera, null, location);
    }

    /**
     * Get a location camera center offsets in pixels.
     * This overload includes the tool specific calibration offset.
     *
     * @param camera
     * @param tool
     * @param location
     * @return
     */
    public static Point getLocationPixelCenterOffsets(Camera camera, HeadMountable tool, Location location) {
        // get the units per pixel scale 
        Location unitsPerPixel = camera.getUnitsPerPixelAtZ();
        // convert inputs to the same units, center on camera and scale
        IniAppConfig config = new IniAppConfig();
        String cameraNum = config.getProperty("Calibration", "cameraNum");
        Location cameraLocation = camera.getLocation(tool);
        if (cameraNum.equals("Multi") && location.getX() > 30) {
            List<Nozzle> nozzles = Configuration.get().getMachine().getHeads().get(0).getNozzles();
            Nozzle n1 = nozzles.get(0);
            Nozzle n2 = nozzles.get(1);

            Location n2Offest = n2.getHeadOffsets();
            Location n1Offset = n1.getHeadOffsets();
            cameraLocation.setX(cameraLocation.getX() + n2Offest.getX() - n1Offset.getX());
            cameraLocation.setY(cameraLocation.getY() + n2Offest.getY() - n1Offset.getY());

        }

        location = location.convertToUnits(unitsPerPixel.getUnits())
                        .subtract(cameraLocation)
                        .multiply(1. / unitsPerPixel.getX(), -1. / unitsPerPixel.getY(), 0., 0.);
        // relative center of camera in pixels
        return new Point(location.getX(), location.getY());
    }

    /**
     * Using the given camera, try to find a QR code and return it's text. This is just a wrapper
     * for the generic scanBarcode(Camera) function. This one was added before the other and I don't
     * want to remove it in case people are using it, but it does the same thing.
     *
     * @param camera
     * @return
     * @throws Exception
     */
    public static String readQrCode(Camera camera) throws Exception {
        return scanBarcode(camera);
    }

    /*    */

    /**
     * Using the given camera, try to find any supported barcode and return it's text.
     *
     * @param camera
     * @return
     * @throws Exception
     *//*
    public static String scanBarcode(Camera camera) throws Exception {
        BufferedImage image = camera.lightSettleAndCapture();
        BinaryBitmap binaryBitmap = new BinaryBitmap(new HybridBinarizer(
                new BufferedImageLuminanceSource(image)));
        try {
            Result qrCodeResult = new MultiFormatReader().decode(binaryBitmap);
            return qrCodeResult.getText();
        } catch (Exception e) {
            return null;
        }
    }*/
    public static String scanBarcode(Camera camera) throws Exception {


        try {


            //将读取二维码图片的流转为图片对象
            BufferedImage image = camera.lightSettleAndCapture();
            File file = new File("3.png");
            image = ImageIO.read(file);
/*            // 将图像转换为黑白
            image = convertToGrayscale(image);
            image = smoothImage(image);
            image = medianFilter(image);
            image = binarizeImage(image);

            File outputFile = new File("22.png");

            ImageIO.write(image, "png", outputFile); // 或者 "png" for PNG format*/

            BufferedImageLuminanceSource source = new BufferedImageLuminanceSource(image);
            Binarizer binarizer = new HybridBinarizer(source);
            BinaryBitmap binaryBitmap = new BinaryBitmap(binarizer);
            QRCodeReader reader = new QRCodeReader();
            Result result = reader.decode(binaryBitmap, hints);
            //返回二维码中的文本内容
            String content = result.getText();
            System.out.println("二维码解析成功");
            return content;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }

    }

    private static BufferedImage convertToGrayscale(BufferedImage image) {
        BufferedImage result = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_BYTE_GRAY);

        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                Color color = new Color(image.getRGB(x, y));
                int grayValue = (int) (0.299 * color.getRed() + 0.587 * color.getGreen() + 0.114 * color.getBlue());
                int newRGB = new Color(grayValue, grayValue, grayValue).getRGB();
                result.setRGB(x, y, newRGB);
            }
        }

        return result;
    }

    private static BufferedImage smoothImage(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();

        BufferedImage smoothedImage = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);

        for (int y = 1; y < height - 1; y++) {
            for (int x = 1; x < width - 1; x++) {
                // 进行平均滤波
                int sum = 0;
                for (int i = -1; i <= 1; i++) {
                    for (int j = -1; j <= 1; j++) {
                        sum += new Color(image.getRGB(x + i, y + j)).getRed();
                    }
                }
                int average = sum / 9;
                int newRGB = new Color(average, average, average).getRGB();
                smoothedImage.setRGB(x, y, newRGB);
            }
        }

        return smoothedImage;
    }

    private static BufferedImage medianFilter(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();

        BufferedImage denoisedImage = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);

        for (int y = 1; y < height - 1; y++) {
            for (int x = 1; x < width - 1; x++) {
                // 获取3x3邻域内的像素值
                int[] values = new int[9];
                int index = 0;
                for (int i = -1; i <= 1; i++) {
                    for (int j = -1; j <= 1; j++) {
                        values[index++] = new Color(image.getRGB(x + i, y + j)).getRed();
                    }
                }

                // 对邻域内的像素值进行排序
                java.util.Arrays.sort(values);

                // 取中值作为新的像素值
                int medianValue = values[4];
                int newRGB = new Color(medianValue, medianValue, medianValue).getRGB();
                denoisedImage.setRGB(x, y, newRGB);
            }
        }

        return denoisedImage;
    }

    private static BufferedImage binarizeImage(BufferedImage image) {
        // 阈值设置，根据具体情况调整
        int threshold = 128;

        BufferedImage result = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_BYTE_BINARY);

        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                Color color = new Color(image.getRGB(x, y));
                int grayValue = color.getRed();

                // 根据阈值进行二值化
                int newRGB = (grayValue < threshold) ? Color.BLACK.getRGB() : Color.WHITE.getRGB();
                result.setRGB(x, y, newRGB);
            }
        }

        return result;
    }

    public static List<PnpJobPlanner.PlannedPlacement> findPartAlignmentOffsetsMulti(List<PnpJobPlanner.PlannedPlacement> pps, PartAlignment p) throws Exception {
        return p.findOffsetsMulti(pps);
    }


    public static PartAlignment.PartAlignmentOffset findPartAlignmentOffsets(PartAlignment p, Part part, BoardLocation boardLocation, Placement placement, Nozzle nozzle) throws Exception {
        // 创建一个存储全局变量的Map，用于在脚本中访问零件和喷嘴等信息
        Map<String, Object> globals = new HashMap<>();
        globals.put("part", part);
        globals.put("nozzle", nozzle);

        // 在视觉脚本中触发"Vision.PartAlignment.Before"事件，传递全局变量
        Configuration.get().getScripting().on("Vision.PartAlignment.Before", globals);

        // 初始化偏移量为null
        PartAlignmentOffset offsets = null;
        try {
            // 调用PartAlignment对象的findOffsets方法，计算零件对齐的偏移量

            offsets = p.findOffsets(part, boardLocation, placement, nozzle);
            // 返回计算得到的偏移量
            return offsets;
        } finally {
            // 将偏移量存储在全局变量中，以便在脚本中访问
            globals.put("offsets", offsets);
            // 在视觉脚本中触发"Vision.PartAlignment.After"事件，传递全局变量
            Configuration.get().getScripting().on("Vision.PartAlignment.After", globals);
        }
    }


    /**
     * Compute an RGB histogram over the provided image.
     *
     * @param image
     * @return the histogram as long[channel][value] with channel 0=Red 1=Green 2=Blue and value 0...255.
     */
    public static long[][] computeImageHistogram(BufferedImage image) {
        long[][] histogram = new long[3][256];
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int rgb = image.getRGB(x, y);
                int r = (rgb >> 16) & 0xff;
                int g = (rgb >> 8) & 0xff;
                int b = (rgb >> 0) & 0xff;
                histogram[0][r]++;
                histogram[1][g]++;
                histogram[2][b]++;
            }
        }
        return histogram;
    }

    /**
     * Compute an HSV histogram over the provided image.
     *
     * @param image
     * @return the histogram as long[channel][value] with channel 0=Hue 1=Saturation 2=Value and value 0...255.
     */
    public static long[][] computeImageHistogramHsv(BufferedImage image) {
        long[][] histogram = new long[3][256];
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int rgb = image.getRGB(x, y);
                int r = (rgb >> 16) & 0xff;
                int g = (rgb >> 8) & 0xff;
                int b = (rgb >> 0) & 0xff;
                float[] hsb = Color.RGBtoHSB(r, g, b, null);
                int h = (int) (hsb[0] * 255.999);
                int s = (int) (hsb[1] * 255.999);
                int v = (int) (hsb[2] * 255.999);
                histogram[0][h]++;
                histogram[1][s]++;
                histogram[2][v]++;
            }
        }
        return histogram;
    }

    /**
     * Ready the FIDUCIAL-HOME, either by returning the existing part or creating a new
     * part with the given circular fiducialDiameter.
     *
     * @param fiducialDiameter
     * @param overwrite        If the FIDUCIAL-HOME already exists, overwrite it and make sure it has the given fiducialDiameter
     *                         and circular shape.
     * @return The FIDUCIAL-HOME part.
     * @throws Exception
     */
    static public Part readyHomingFiducialWithDiameter(Length fiducialDiameter, boolean overwrite) throws Exception {
        Configuration configuration = Configuration.get();
        Part part = configuration.getPart("FIDUCIAL-HOME");
        if (part == null) {
            part = new Part("FIDUCIAL-HOME");
            configuration.addPart(part);
        } else if (!overwrite) {
            return part;
        }
        org.openpnp.model.Package pkg = configuration.getPackage("FIDUCIAL-HOME");
        if (pkg == null) {
            pkg = new org.openpnp.model.Package("FIDUCIAL-HOME");
            configuration.addPackage(pkg);
        }
        part.setPackage(pkg);
        Footprint footprint = new Footprint();
        footprint.setUnits(fiducialDiameter.getUnits());
        Pad pad = new Pad();
        pad.setName("FID");
        pad.setWidth(fiducialDiameter.getValue());
        pad.setHeight(fiducialDiameter.getValue());
        pad.setRoundness(100.0);
        footprint.addPad(pad);
        pkg.setFootprint(footprint);
        ReferenceFiducialLocator fiducialLocator = ReferenceFiducialLocator.getDefault();
        String xmlPipeline = IOUtils.toString(ReferenceBottomVision.class
                .getResource("ReferenceFiducialLocator-DefaultPipeline.xml"));
        CvPipeline pipeline = new CvPipeline(xmlPipeline);
        FiducialVisionSettings visionSettings = fiducialLocator.getInheritedVisionSettings(part);
        if (pipeline.equals(visionSettings.getPipeline())) {
            // Already the right vision settings.
        } else {
            if (visionSettings.getUsedFiducialVisionIn().size() == 1
                    && visionSettings.getUsedFiducialVisionIn().get(0) == part) {
                // Already a special setting on the part. Modify it.
            } else {
                // Needs new settings (likely means the default was changed by the user).
                FiducialVisionSettings newSettings = new FiducialVisionSettings();
                newSettings.setValues(visionSettings);
                newSettings.setName(part.getShortName());
                part.setFiducialVisionSettings(newSettings);
                Configuration.get().addVisionSettings(newSettings);
                visionSettings = newSettings;
            }
            visionSettings.setPipeline(pipeline);
        }
        return part;
    }
}
