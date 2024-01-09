package org.openpnp.util;

import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;
import org.openpnp.gui.MainFrame;
import org.openpnp.machine.reference.camera.AbstractSettlingCamera;
import org.openpnp.machine.reference.camera.AutoFocusProvider;
import org.openpnp.machine.reference.camera.ReferenceCamera;
import org.openpnp.model.Configuration;
import org.openpnp.model.Length;
import org.openpnp.model.LengthUnit;
import org.openpnp.model.Location;
import org.openpnp.spi.HeadMountable;
import org.openpnp.spi.Nozzle;
import org.openpnp.vision.pipeline.CvStage;
import org.openpnp.vision.pipeline.stages.DetectCircularSymmetry;
import org.pmw.tinylog.Logger;

import javax.swing.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;

public class MyUntils {

    private long zeroKnowledgeSettleTimeMs = 600;

    private double zeroKnowledgeDisplacementRatio = 0.2;

    private double zeroKnowledgeDisplacementMm = 1;

    private Location zeroKnowledgeBacklashOffsets = new Location(LengthUnit.Millimeters, -1, -1, 0.0, -5);

    private double zeroKnowledgeAutoFocusDepthMm = 2.0;

    private BufferedImage retainedImage;

    private double maxCameraRelativeFiducialAreaDiameter = 0.2;

    private double fiducialMargin = 1.1;

    private int zeroKnowledgeRunoutCompensationShots = 2;

    private double zeroKnowledgeBacklashSpeed = 0.2;

    private int subSampling = 8;

    private int superSampling = 4;

    private DetectCircularSymmetry.SymmetryScore symmetryScore = DetectCircularSymmetry.SymmetryScore.OverallVarianceVsRingVarianceSum;

    private double minSymmetry = 1.5;

    protected long diagnosticsMilliseconds = 4000;

    private int zeroKnowledgeFiducialLocatorPasses = 3;



    public Length autoCalibrateCamera(ReferenceCamera camera, HeadMountable movable, Double expectedDiameter, String diagnostics, boolean secondary)
            throws Exception {
        if (camera.getAdvancedCalibration().isOverridingOldTransformsAndDistortionCorrectionSettings()) {
            throw new Exception("Preliminary camera " + camera.getName() + " calibration cannot be performed, "
                    + "because the Advanced Camera Calibration is already active.");
        }
        Location initialLocation = movable.getLocation();
        // Temporarily set very conservative settling (calibration can happen before or after Camera Settling has been configured).
        AbstractSettlingCamera.SettleMethod oldSettleMethod = camera.getSettleMethod();
        long oldSettleTime = camera.getSettleTimeMs();
        camera.setSettleMethod(AbstractSettlingCamera.SettleMethod.FixedTime);
        camera.setSettleTimeMs(Math.max(oldSettleTime, zeroKnowledgeSettleTimeMs));
        try {
            if (!secondary) {
                // Reset camera transforms.
                camera.setFlipX(false);
                camera.setFlipY(false);
                camera.setRotation(0);
            }

            CvStage.Result.Circle expectedOffsetsAndDiameter;
            if (expectedDiameter == null) {
                // Detect the diameter.
                expectedOffsetsAndDiameter = getSubjectPixelLocation(camera, movable, null, zeroKnowledgeDisplacementRatio, diagnostics, null, false);
            } else {
                expectedOffsetsAndDiameter = new CvStage.Result.Circle(0, 0, expectedDiameter);
                // Detect the true diameter.
                expectedOffsetsAndDiameter = getSubjectPixelLocation(camera, movable, expectedOffsetsAndDiameter, zeroKnowledgeDisplacementRatio, diagnostics, null, false);
            }
            // Center offset 0, 0 expected.
            expectedOffsetsAndDiameter.setX(0);
            expectedOffsetsAndDiameter.setY(0);

            // Perform zero knowledge calibration motion pattern.
            double displacementAbsMm = zeroKnowledgeDisplacementMm;
            Location unitsPerPixel = new Location(null);
            Length featureDiameter = null;
            for (int pass = 0; pass < 3; pass++) {
                double displacementMm = displacementAbsMm;
                if (movable == camera) {
                    // We're moving the camera, so the displacement of the subject in the camera view is seen reversed
                    // e.g. when we move the camera 1mm to the right, the subject as seen in the camera view goes
                    // 1mm to the left.
                    displacementMm = -displacementMm;
                }
                // else: we are moving the camera subject and displacement is seen as is.

                // X Axis
                Location originLocationX = initialLocation.add(new Location(LengthUnit.Millimeters,
                        -displacementMm * 0.5, 0, 0, 0));
                zeroKnowledgeMoveTo(movable, originLocationX, pass == 0);
                if (featureDiameter != null) {
                    expectedOffsetsAndDiameter = getExpectedOffsetsAndDiameter(camera, movable, initialLocation, featureDiameter, secondary);
                }
                CvStage.Result.Circle originX = getSubjectPixelLocation(camera, movable, expectedOffsetsAndDiameter, zeroKnowledgeDisplacementRatio, diagnostics, null, false);
                Location displacedXLocation = originLocationX.add(
                        new Location(LengthUnit.Millimeters, displacementMm, 0, 0, 0));
                zeroKnowledgeMoveTo(movable, displacedXLocation, false);
                if (featureDiameter != null) {
                    expectedOffsetsAndDiameter = getExpectedOffsetsAndDiameter(camera, movable, initialLocation, featureDiameter, secondary);
                }
                CvStage.Result.Circle displacedX = getSubjectPixelLocation(camera, movable, expectedOffsetsAndDiameter, zeroKnowledgeDisplacementRatio, diagnostics, null, false);
                // Note: pixel coordinate system has flipped Y.
                double dxX = displacedX.x - originX.x;
                double dyX = -(displacedX.y - originX.y);

                // Y Axis
                Location originLocationY = initialLocation.add(new Location(LengthUnit.Millimeters,
                        0, -displacementMm * 0.5, 0, 0));
                zeroKnowledgeMoveTo(movable, originLocationY, false);
                if (featureDiameter != null) {
                    expectedOffsetsAndDiameter = getExpectedOffsetsAndDiameter(camera, movable, initialLocation, featureDiameter, secondary);
                }
                CvStage.Result.Circle originY = getSubjectPixelLocation(camera, movable, expectedOffsetsAndDiameter, zeroKnowledgeDisplacementRatio, diagnostics, null, false);
                Location displacedYLocation = originLocationY.add(
                        new Location(LengthUnit.Millimeters, 0, displacementMm, 0, 0));
                zeroKnowledgeMoveTo(movable, displacedYLocation, false);
                if (featureDiameter != null) {
                    expectedOffsetsAndDiameter = getExpectedOffsetsAndDiameter(camera, movable, initialLocation, featureDiameter, secondary);
                }
                CvStage.Result.Circle displacedY = getSubjectPixelLocation(camera, movable, expectedOffsetsAndDiameter, zeroKnowledgeDisplacementRatio, diagnostics, null, false);
                // Note: pixel coordinate system has flipped Y.
                double dxY = displacedY.x - originY.x;
                double dyY = -(displacedY.y - originY.y);

                // Compute or confirm camera transform
                boolean confirmed = false;
                if (Math.abs(dxX) > Math.abs(dyX)) {
                    // Landscape orientation.
                    if (dxX > 0 && dyY > 0) {
                        // 0°
                        confirmed = true;
                    } else if (dxX > 0 && dyY < 0) {
                        // Mirrored on X axis
                        camera.setFlipX(true);
                    } else if (dxX < 0 && dyY < 0) {
                        // 180°
                        camera.setFlipX(true);
                        camera.setFlipY(true);
                    } else if (dxX < 0 && dyY > 0) {
                        // Mirrored on Y axis
                        camera.setFlipY(true);
                    }
                    unitsPerPixel = new Location(LengthUnit.Millimeters,
                            displacementAbsMm / Math.abs(dxX),
                            displacementAbsMm / Math.abs(dyY),
                            0,
                            0);
                } else {
                    // Portrait orientation.
                    if (dxX > 0 && dyY > 0) {
                        // 0°
                        confirmed = true;
                    } else if (dxY > 0 && dyX < 0) {
                        // 90°
                        camera.setRotation(90);
                    } else if (dxY > 0 && dyX > 0) {
                        // 90°, mirrored on Y axis
                        camera.setRotation(90);
                        camera.setFlipY(true);
                    } else if (dxY < 0 && dyX > 0) {
                        // 270°
                        camera.setRotation(270);
                    } else if (dxY < 0 && dyX < 0) {
                        // 90°, mirrored on X axis
                        camera.setRotation(90);
                        camera.setFlipX(true);
                    }
                    unitsPerPixel = new Location(LengthUnit.Millimeters,
                            displacementAbsMm / Math.abs(dyX),
                            displacementAbsMm / Math.abs(dxY),
                            0,
                            0);
                }
                // Settings after this pass.
                featureDiameter = new Length(expectedOffsetsAndDiameter.getDiameter() * unitsPerPixel.getX(), unitsPerPixel.getUnits());
                if (secondary) {
                    // Keep Z (for now).
                    if (camera.getUnitsPerPixelSecondary() != null) {
                        unitsPerPixel = unitsPerPixel.derive(camera.getUnitsPerPixelSecondary(), false, false, true, false);
                    }
                    camera.setUnitsPerPixelSecondary(unitsPerPixel);
                    camera.setCameraSecondaryZ(camera.getCameraPhysicalLocation().getLengthZ());
                } else {
                    // Keep Z (for now).
                    unitsPerPixel = unitsPerPixel.derive(camera.getUnitsPerPixelPrimary(), false, false, true, false);
                    camera.setUnitsPerPixelPrimary(unitsPerPixel);
                    camera.setCameraPrimaryZ(camera.getCameraPhysicalLocation().getLengthZ());
                }

                if (pass == 0 && movable != camera && zeroKnowledgeAutoFocusDepthMm != 0) {
                    // Auto-focus and set Z of camera.
                    Location location0 = initialLocation.add(new Location(LengthUnit.Millimeters, 0, 0, zeroKnowledgeAutoFocusDepthMm, 0));
                    Location location1 = initialLocation.add(new Location(LengthUnit.Millimeters, 0, 0, -zeroKnowledgeAutoFocusDepthMm, 0));
                    initialLocation = new AutoFocusProvider().autoFocus(camera, movable, featureDiameter.multiply(4), location0, location1);
                    Location cameraHeadOffsetsNew = camera.getHeadOffsets().derive(initialLocation, false, false, true, false);
                    Logger.info("Setting camera " + camera.getName() + " Z to " + cameraHeadOffsetsNew.getLengthZ() + " (previously " + camera.getHeadOffsets().getLengthZ());
                    camera.setHeadOffsets(cameraHeadOffsetsNew);
                }

                if (pass > 0) {
                    if (!confirmed) {
                        throw new Exception("The camera rotation/mirroring detected earlier was not confirmed.");
                    }
                    // Fine-adjust rotation
                    double angle = Math.toDegrees(Math.atan2(dyX, dxX));
                    if (camera.isFlipX() ^ camera.isFlipY()) {
                        angle = -angle;
                    }
                    camera.setRotation(camera.getRotation() - angle);
                }
                // make sure to record a new image at rotation to reset width and height on the camera.
                camera.lightSettleAndCapture();

                // Next displacement nearer to edge on the smaller of camera width, height.
                displacementAbsMm = Math.min(camera.getWidth(), camera.getHeight()) * (pass + 1) * 0.25
                        * unitsPerPixel.convertToUnits(LengthUnit.Millimeters).getX();
            }
            return featureDiameter;
        } finally {
            // Restore the camera location
            zeroKnowledgeMoveTo(movable, initialLocation, true);
            // Restore settling.
            camera.setSettleMethod(oldSettleMethod);
            camera.setSettleTimeMs(oldSettleTime);
        }
    }


    /**
     * Before we know anything about the camera, we cannot use pipelines, so we use the DetectCircularSymmetry stage
     * directly. We can get a pixel locations for now.
     *
     * @param camera
     * @param movable
     * @param expectedOffsetAndDiameter
     * @param extraSearchRange          Specifies an extra search range, relative to the camera view size (minimum of width, height).
     * @param diagnostics
     * @param scoreRange
     * @param rough                     TODO
     * @return The match as a Circle.
     * @throws Exception
     */
    public CvStage.Result.Circle getSubjectPixelLocation(ReferenceCamera camera, HeadMountable movable, CvStage.Result.Circle expectedOffsetAndDiameter, double extraSearchRange,
                                                         String diagnostics, DetectCircularSymmetry.ScoreRange scoreRange, boolean rough) throws Exception {
        if (scoreRange == null) {
            scoreRange = new DetectCircularSymmetry.ScoreRange();
        }
        BufferedImage bufferedImage = (retainedImage != null ?
                retainedImage : camera.lightSettleAndCapture());
        Mat image = OpenCvUtils.toMat(bufferedImage);
        try {
            int subjectAreaDiameter = (int) (Math.min(image.cols(), image.rows())
                    * maxCameraRelativeFiducialAreaDiameter);
            int expectedDiameter = (expectedOffsetAndDiameter != null ?
                    (int) expectedOffsetAndDiameter.getDiameter()
                    : subjectAreaDiameter / 2);
            int maxDiameter = (int) (expectedDiameter * fiducialMargin + 1);
            int minDiameter = (int) (expectedOffsetAndDiameter != null ?
                    expectedDiameter / fiducialMargin - 1
                    : 7);
            int searchDiameter = (int) (Math.max(subjectAreaDiameter / 2, maxDiameter * fiducialMargin)
                    + Math.min(image.cols(), image.rows()) * extraSearchRange);
            int expectedX = bufferedImage.getWidth() / 2 + (int) (expectedOffsetAndDiameter != null ? expectedOffsetAndDiameter.getX() : 0);
            int expectedY = bufferedImage.getHeight() / 2 + (int) (expectedOffsetAndDiameter != null ? expectedOffsetAndDiameter.getY() : 0);

            CvStage.Result.Circle result = null;
            if (movable instanceof Nozzle) {
                // A nozzle can have runout, we average rotated shots.
                Location l = movable.getLocation();
                double x = 0;
                double y = 0;
                int n = 0;
                int da = 360 / zeroKnowledgeRunoutCompensationShots;
                int angle0 = 0;
                int angle1 = 360 - da;
                if (movable instanceof Nozzle
                        && ((Nozzle) movable).getRotationMode() == Nozzle.RotationMode.LimitedArticulation) {
                    // Make sure it is compliant.
                    zeroKnowledgeRunoutCompensationShots = Math.min(zeroKnowledgeRunoutCompensationShots, 2);
                    da = 360 / zeroKnowledgeRunoutCompensationShots;
                    angle0 = 0;
                    angle1 = 360 - da;
                    Location l0 = l.derive(new Location(l.getUnits(), 0, 0, 0, angle0), false, false, false, true);
                    Location l1 = l.derive(new Location(l.getUnits(), 0, 0, 0, angle1), false, false, false, true);
                    ((Nozzle) movable).prepareForPickAndPlaceArticulation(l0, l1);
                }
                for (int angle = angle0; angle <= angle1; angle += da) {
                    l = l.derive(new Location(l.getUnits(), 0, 0, 0, angle), false, false, false, true);
                    movable.moveTo(l);
                    bufferedImage = camera.lightSettleAndCapture();
                    image.release();
                    image = OpenCvUtils.toMat(bufferedImage);
                    result = getPixelLocationShot(camera, diagnostics, image, minDiameter,
                            maxDiameter, searchDiameter, expectedX, expectedY,
                            n == 0 ? scoreRange : new DetectCircularSymmetry.ScoreRange(), rough);
                    // Accumulate
                    x += result.getX();
                    y += result.getY();
                    ++n;
                }
                // Average
                result.setX(x / n);
                result.setY(y / n);
            } else {
                // Fiducial can be detected by one shot.
                result = getPixelLocationShot(camera, diagnostics, image, minDiameter,
                        maxDiameter, searchDiameter, expectedX, expectedY, scoreRange, rough);
            }
            return result;
        } finally {
            image.release();
        }
    }

    /**
     * Moves the head-mountable to a location at safe Z using a conservative backlash-compensation scheme. Used to get precise
     * camera and camera subject positioning before proper backlash compensation can be configured and calibrated.
     *
     * @param hm
     * @param location
     * @param safeZ
     * @throws Exception
     */
    public void zeroKnowledgeMoveTo(HeadMountable hm, Location location, boolean safeZ) throws Exception {
        Location backlashCompensatedLocation = location.subtract(zeroKnowledgeBacklashOffsets);
        if (safeZ) {
            MovableUtils.moveToLocationAtSafeZ(hm, backlashCompensatedLocation);
        } else {
            hm.moveTo(backlashCompensatedLocation);
        }
        hm.moveTo(location, zeroKnowledgeBacklashSpeed);
    }

    public CvStage.Result.Circle getExpectedOffsetsAndDiameter(ReferenceCamera camera, HeadMountable movable,
                                                               Location location, Length expectedDiameter, boolean secondary) {
        CvStage.Result.Circle expectedOffsetAndDiameter = null;
        if (expectedDiameter != null) {
            // Diameter given, try to calulcate by camera UPP.
            Location l = (camera != movable ?
                    movable.getLocation().subtract(location)
                    : location.subtract(movable.getLocation()));
            // Get the right units per pixel.
            Location unitsPerPixel = secondary ? camera.getUnitsPerPixelSecondary() : camera.getUnitsPerPixelPrimary();
            expectedOffsetAndDiameter = new CvStage.Result.Circle(
                    l.getLengthX().divide(unitsPerPixel.getLengthX()),
                    -l.getLengthY().divide(unitsPerPixel.getLengthY()),
                    expectedDiameter.divide(unitsPerPixel.getLengthX()));
        }
        return expectedOffsetAndDiameter;
    }

    private CvStage.Result.Circle getPixelLocationShot(ReferenceCamera camera, String diagnostics, Mat image,
                                                       int minDiameter, int maxDiameter, int searchDiameter, int expectedX, int expectedY, DetectCircularSymmetry.ScoreRange scoreRange, boolean rough)
            throws Exception, IOException {
        List<CvStage.Result.Circle> results = DetectCircularSymmetry.findCircularSymmetry(image,
                expectedX, expectedY,
                minDiameter, maxDiameter, searchDiameter, searchDiameter, searchDiameter, 1,
                minSymmetry, 0.0, subSampling, rough ? 1 : superSampling, symmetryScore, diagnostics != null, false, scoreRange);
        if (diagnostics != null) {
            if (LogUtils.isDebugEnabled()) {
                File file = Configuration.get().createResourceFile(getClass(), "loc_", ".png");
                Imgcodecs.imwrite(file.getAbsolutePath(), image);
            }
            final BufferedImage diagnosticImage = OpenCvUtils.toBufferedImage(image);
            SwingUtilities.invokeLater(() -> {
                MainFrame.get()
                        .getCameraViews()
                        .getCameraView(camera)
                        .showFilteredImage(diagnosticImage,
                                diagnostics.replace("{score}", String.format("%.2f", scoreRange.finalScore)),
                                diagnosticsMilliseconds);
            });
        }
        if (results.size() < 1) {
            throw new Exception("Subject not found.");
        }
        CvStage.Result.Circle result = results.get(0);
        return result;
    }

    public Location centerInOnSubjectLocation(ReferenceCamera camera, HeadMountable movable, Length subjectDiameter, String diagnostics, boolean secondary)
            throws Exception {
        Location location = movable.getLocation();
        CvStage.Result.Circle expectedOffsetsAndDiameter =
                getExpectedOffsetsAndDiameter(camera, movable, location, subjectDiameter, secondary);
        for (int pass = 0; pass < zeroKnowledgeFiducialLocatorPasses ; pass++) {
            // Note, we cannot use the VisionUtils functionality yet, need to do it ourselves.
            CvStage.Result.Circle detected = getSubjectPixelLocation(camera, movable, expectedOffsetsAndDiameter, 0, diagnostics, null, false);
            // Calculate the difference between the center of the image to the center of the match.
            double offsetX = detected.x - ((double) camera.getWidth() / 2);
            double offsetY = ((double) camera.getHeight() / 2) - detected.y;
            // And convert pixels to primary or secondary units
            Location unitsPerPixel = secondary ?  camera.getUnitsPerPixelSecondary() : camera.getUnitsPerPixelPrimary();
            offsetX *= unitsPerPixel.getX();
            offsetY *= unitsPerPixel.getY();
            Location offset = new Location(unitsPerPixel.getUnits(), offsetX, offsetY, 0, 0);
            Location subjectLocation = camera.getLocation().add(offset);
            if (movable == camera) {
                // When the camera is the movable, we can simply move it to the detected location.
                location = subjectLocation;
            }
            else {
                // When the camera is not the movable, i.e. the movable is the subject, then we need to move
                // the subject to the camera.
                Location offsets = camera.getLocation().subtract(subjectLocation);
                location = location.add(offsets);
            }
            zeroKnowledgeMoveTo(movable, location, pass == 0);
        }
        return location;
    }
}
