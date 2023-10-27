package org.openpnp.machine.reference.vision;

import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.jfree.chart.plot.PiePlot;
import org.jfree.chart.plot.PlotRenderingInfo;
import org.opencv.core.Point;
import org.opencv.core.RotatedRect;
import org.opencv.core.Size;
import org.openpnp.ConfigurationListener;
import org.openpnp.gui.JobPanel;
import org.openpnp.gui.MainFrame;
import org.openpnp.gui.support.DoubleConverter;
import org.openpnp.gui.support.LengthConverter;
import org.openpnp.gui.support.PropertySheetWizardAdapter;
import org.openpnp.machine.reference.ReferenceNozzleTip;
import org.openpnp.machine.reference.ReferenceNozzleTipCalibration;
import org.openpnp.machine.reference.ReferenceNozzleTipCalibration.BackgroundCalibrationMethod;
import org.openpnp.machine.reference.vision.wizards.BottomVisionSettingsConfigurationWizard;
import org.openpnp.machine.reference.vision.wizards.ReferenceBottomVisionConfigurationWizard;
import org.openpnp.model.*;
import org.openpnp.model.Package;
import org.openpnp.model.VisionCompositing.Composite;
import org.openpnp.model.VisionCompositing.Shot;
import org.openpnp.spi.*;
import org.openpnp.util.MovableUtils;
import org.openpnp.util.OpenCvUtils;
import org.openpnp.util.UiUtils;
import org.openpnp.util.Utils2D;
import org.openpnp.util.VisionUtils;
import org.openpnp.vision.pipeline.CvPipeline;
import org.openpnp.vision.pipeline.CvPipeline.PipelineShot;
import org.openpnp.vision.pipeline.CvStage;
import org.openpnp.vision.pipeline.CvStage.Result;
import org.openpnp.vision.pipeline.stages.AffineWarp;
import org.pmw.tinylog.Logger;
import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementMap;
import org.simpleframework.xml.Root;

public class ReferenceBottomVision extends AbstractPartAlignment {

    @Deprecated
    @Element(required = false)
    protected CvPipeline pipeline;

    @Attribute(required = false)
    protected boolean enabled = false;

    @Attribute(required = false)
    protected boolean preRotate = false;

    @Attribute(required = false)
    protected int maxVisionPasses = 3;

    @Element(required = false)
    protected Length maxLinearOffset = new Length(1, LengthUnit.Millimeters);

    @Attribute(required = false)
    protected double maxAngularOffset = 10;

    @Attribute(required = false)
    protected double testAlignmentAngle = 0.0;

    @Attribute(required = false)
    @Deprecated
    private Integer edgeDetectionPixels = null;

    @Deprecated
    @ElementMap(required = false)
    protected Map<String, PartSettings> partSettingsByPartId = null;

    public ReferenceBottomVision() {
        Configuration.get().addListener(new ConfigurationListener.Adapter() {
            @Override
            public void configurationComplete(Configuration configuration) throws Exception {
                migratePartSettings(configuration);
                if (bottomVisionSettings == null) {
                    // Recovery mode, take any setting.
                    for (AbstractVisionSettings settings : configuration.getVisionSettings()) {
                        if (settings instanceof BottomVisionSettings) {
                            bottomVisionSettings = (BottomVisionSettings) settings;
                            break;
                        }
                    }
                }
            }
        });
    }

    @Override
    public List<PnpJobPlanner.PlannedPlacement> findOffsetsMulti(List<PnpJobPlanner.PlannedPlacement> pps) throws Exception {
        List<PnpJobPlanner.PlannedPlacement> offsets = findOffsetsPreRotateMulti(pps);

        offsets.forEach(p->{
            if(p.nozzle.isAligningRotationMode()){
                double rotOff = p.nozzle.getRotationModeOffset() != null ? p.nozzle.getRotationModeOffset() : 0;
                p.nozzle.setRotationModeOffset(rotOff + p.alignmentOffsets.getLocation().getRotation());

            }
        });
        return offsets;
    }


    @Override
    public PartAlignmentOffset findOffsets(Part part, BoardLocation boardLocation,
                                           Placement placement, Nozzle nozzle) throws Exception {
        // 获取零件的继承的视觉设置
        BottomVisionSettings bottomVisionSettings = getInheritedVisionSettings(part);

        // 如果没有启用或零件的继承视觉设置没有启用，则返回默认的对齐偏移量
        if (!isEnabled() || !bottomVisionSettings.isEnabled()) {
            return new PartAlignmentOffset(new Location(LengthUnit.Millimeters), false);
        }

        // 检查吸嘴上是否都有零件
        if (part == null || nozzle.getPart() == null) {
            throw new Exception("吸嘴上无零件，请点击停止任务后重新开始。");
        }
        // 检查零件和吸嘴的零件是否匹配
        if (part != nozzle.getPart()) {
            throw new Exception("Part mismatch with part on nozzle.");
        }

        // 获取底部视觉相机
        Camera camera = VisionUtils.getBottomVisionCamera();
        // 根据预旋转模式开关来判断具体的执行逻辑
        PartAlignmentOffset offsets;
        if ((bottomVisionSettings.getPreRotateUsage() == PreRotateUsage.Default && preRotate)
                || (bottomVisionSettings.getPreRotateUsage() == PreRotateUsage.AlwaysOn)) {
            offsets = findOffsetsPreRotate(part, boardLocation, placement, nozzle, camera, bottomVisionSettings);
        } else {
            offsets = findOffsetsPostRotate(part, boardLocation, placement, nozzle, camera, bottomVisionSettings);
        }
        // 如果吸嘴正在对齐旋转模式，则将旋转偏移量添加到旋转模式中，而不是在放置时调整它。这有利于在DRO、十字准线等处显示与零件旋转对齐的旋转。
        if (nozzle.isAligningRotationMode()) {
            double rotOff = nozzle.getRotationModeOffset() != null ? nozzle.getRotationModeOffset() : 0;
            nozzle.setRotationModeOffset(rotOff + offsets.getLocation().getRotation());
            Location newOffsets = offsets.getLocation().derive(null, null, null, 0.);
            offsets = new PartAlignmentOffset(newOffsets, offsets.getPreRotated());
        }
        // 返回对齐偏移量
        return offsets;
    }


    public Location getCameraLocationAtPartHeight(Part part, Camera camera, Nozzle nozzle, double angle) throws Exception {
        if (part == null) {
            // No part height accounted for.
            return camera.getLocation(nozzle)
                    .derive(null, null, null, angle);
        }
        if (part.isPartHeightUnknown()) {
            if (camera.getFocusProvider() != null
                    && nozzle.getNozzleTip() != null) {
                NozzleTip nt = nozzle.getNozzleTip();

                Location locationNominal = camera.getLocation(nozzle)
                        .derive(null, null, null, angle);
                BottomVisionSettings bottomVisionSettings = ReferenceBottomVision.getDefault()
                        .getInheritedVisionSettings(part);
                Composite composite = part.getPackage().getVisionCompositing().new Composite(part.getPackage(), bottomVisionSettings, nozzle, nt,
                        camera, locationNominal);
                Length partHeight = new Length(0, LengthUnit.Millimeters);
                int weight = 0;
                for (Shot shot : composite.getShotsTravel()) {
                    Location location1 = composite.getShotLocation(shot);
                    Location location0 = location1.add(new Location(nt.getMaxPartHeight().getUnits(),
                            0, 0, nt.getMaxPartHeight().getValue(), 0));
                    Location focus = camera.getFocusProvider().autoFocus(camera, nozzle, nt.getMaxPartDiameter()
                            .add(nt.getMaxPickTolerance().multiply(2.0)), location0, location1);
                    partHeight = partHeight.add(focus.getLengthZ().subtract(location1.getLengthZ()));
                    weight++;
                }
                partHeight = partHeight.divide(weight);
                if (partHeight.convertToUnits(LengthUnit.Millimeters).getValue() <= 0.001) {
                    throw new Exception("Auto focus part height determination failed. Camera seems to have focused on nozzle tip.");
                }
                Logger.info("Part " + part.getId() + " height set to " + partHeight + " by camera focus provider.");
                part.setHeight(partHeight);
            }
            if (part.isPartHeightUnknown()) {
                throw new Exception("Part height unknown and camera " + camera.getName() + " does not support part height sensing.");
            }
        }
        return camera.getLocation(nozzle)
                .add(new Location(part.getHeight()
                        .getUnits(),
                        0.0, 0.0, part.getHeight()
                        .getValue(),
                        0.0))
                .derive(null, null, null, angle);
    }

    private Location getShotLocation2(Part part, Camera camera, Nozzle nozzle, Location wantedLocation, Location adjustedNozzleLocation) {
        try {

            BottomVisionSettings bottomVisionSettings = getInheritedVisionSettings(part);


            // 从Package对象中获取VisionCompositing对象
            VisionCompositing visionCompositing = part.getPackage().getVisionCompositing();
            // 创建一个Composite对象，用于合成视觉信息
            VisionCompositing.Composite composite = visionCompositing.new Composite(
                    part.getPackage(), bottomVisionSettings, nozzle, nozzle.getNozzleTip(), camera, wantedLocation);

            Shot shot = composite.getCompositeShots().get(0);
            Location shotLocation = composite.getShotLocation(shot)
                    .addWithRotation(adjustedNozzleLocation.subtractWithRotation(wantedLocation));
            return shotLocation;
        } catch (Exception exception) {

        }
        return null;
    }

    private List<PnpJobPlanner.PlannedPlacement> findOffsetsPreRotateMulti(List<PnpJobPlanner.PlannedPlacement> pps) throws Exception {
        Camera camera = VisionUtils.getBottomVisionCamera();
        //需要贴的元件有两个的时候
        if (pps.size() > 1) {

            PnpJobPlanner.PlannedPlacement n1P = pps.get(0);
            PnpJobPlanner.PlannedPlacement n2P = pps.get(1);
            final Nozzle n1 = n1P.nozzle;
            final Nozzle n2 = n2P.nozzle;
            final PnpJobProcessor.JobPlacement jobPlacementN1 = n1P.jobPlacement;
            final PnpJobProcessor.JobPlacement jobPlacementN2 = n2P.jobPlacement;

            final Placement placementN1 = jobPlacementN1.getPlacement();
            final Placement placementN2 = jobPlacementN2.getPlacement();

            final BoardLocation boardLocationN1 = jobPlacementN1.getBoardLocation();
            final BoardLocation boardLocationN2 = jobPlacementN2.getBoardLocation();

            final Part partN1 = placementN1.getPart();
            final Part partN2 = placementN2.getPart();

            // 获取所需的旋转角度，首先使用放置位置的旋转角度，如果存在板位置，则使用修正后的位置的角度
            double wantedAngleN1 = placementN1.getLocation().getRotation();
            double wantedAngleN2 = placementN2.getLocation().getRotation();
            if (boardLocationN1 != null) {
                wantedAngleN1 = Utils2D.calculateBoardPlacementLocation(boardLocationN1, placementN1.getLocation())
                        .getRotation();
            }
            if (boardLocationN2 != null) {
                wantedAngleN2 = Utils2D.calculateBoardPlacementLocation(boardLocationN2, placementN2.getLocation())
                        .getRotation();
            }
            // 规范化旋转角度为-180°到180°之间的范围
            wantedAngleN1 = Utils2D.angleNorm(wantedAngleN1, 180.);
            wantedAngleN2 = Utils2D.angleNorm(wantedAngleN2, 180.);
            // 获取所需的位置，包括零件高度和旋转角度

            Location wantedLocationN1 = getCameraLocationAtPartHeight(partN1, camera, n1, wantedAngleN1);

            Location wantedLocationN2 = getCameraLocationAtPartHeight(partN2, camera, n2, wantedAngleN2);
            // 初始化吸嘴位置和中心位置
            Location locationN1 = wantedLocationN1;
            Location locationN2 = wantedLocationN2;

            final Location center = new Location(maxLinearOffset.getUnits());
            // 获取零件的继承的视觉设置
            Location shotLocationN1 = getShotLocation2(partN1, camera, n1, wantedLocationN1, locationN1);
            Location shotLocationN2 = getShotLocation2(partN2, camera, n2, wantedLocationN2, locationN2);

            //n1.moveTo(shotLocationN1);
            //n2.moveTo(shotLocationN2);
            n1.moveToTogether(shotLocationN1, shotLocationN2, n1, n2);
            BottomVisionSettings bottomVisionSettings = getInheritedVisionSettings(partN1);

            try (CvPipeline pipeline = bottomVisionSettings.getPipeline()) {
                // 初始化偏移量，用于迭代计算
                Location offsets1 = new Location(locationN1.getUnits());
                pipeline.setProperty("needSettle", true);

                // 尝试多次获取零件的正确位置
                for (int pass = 0; ; ) {

                    // 处理管道并获取结果的旋转矩形
                    RotatedRect rect = processPipelineAndGetResultMulti(pipeline, camera, partN1, n1,
                            wantedLocationN1, locationN1, bottomVisionSettings);

                    // 记录调试信息，包括底部视觉部件的ID和识别的矩形信息
                    Logger.debug("Bottom vision part {} result rect {}", partN1.getId(), rect);

                    // 创建偏移量对象，表示相机中心到定位零件的物理距离
                    offsets1 = VisionUtils.getPixelCenterOffsets(camera, rect.center.x, rect.center.y);

                    // 计算角度偏移量
                    double angleOffset = VisionUtils.getPixelAngle(camera, rect.angle) - wantedAngleN1;

                    // 大多数OpenCV管道只能告诉我们识别到的矩形的角度位于0°到90°的范围内，
                    // 因此需要规范化角度范围为-45°到+45°。参见angleNorm()。
                    if (bottomVisionSettings.getMaxRotation() == MaxRotation.Adjust) {
                        angleOffset = Utils2D.angleNorm(angleOffset);
                    } else {
                        // 旋转超过180°在一个方向上没有意义
                        angleOffset = Utils2D.angleNorm(angleOffset, 180);
                    }

                    // 当后续旋转喷嘴以补偿角度偏移时，X、Y偏移也会发生变化，因此需要补偿
                    offsets1 = offsets1.rotateXy(-angleOffset)
                            .derive(null, null, null, angleOffset);
                    locationN1 = locationN1.subtractWithRotation(offsets1);

                    if (++pass >= maxVisionPasses) {
                        // 达到最大尝试次数，结束循环
                        break;
                    }

                    // 检查中心和角的偏移是否在允许的范围内，如果不在范围内，则继续尝试
                    Point corners[] = new Point[4];
                    rect.points(corners);
                    Location corner = VisionUtils.getPixelCenterOffsets(camera, corners[0].x, corners[0].y)
                            .convertToUnits(maxLinearOffset.getUnits());
                    Location cornerWithAngularOffset = corner.rotateXy(angleOffset);
                    partSizeCheck(partN1, bottomVisionSettings, rect, camera);

                    if (center.getLinearDistanceTo(offsets1) > getMaxLinearOffset().getValue()) {
                        Logger.debug("Offsets too large {} : center offset {} > {}",
                                offsets1, center.getLinearDistanceTo(offsets1), getMaxLinearOffset().getValue());
                    } else if (corner.getLinearDistanceTo(cornerWithAngularOffset) > getMaxLinearOffset().getValue()) {
                        Logger.debug("Offsets too large {} : corner offset {} > {}",
                                offsets1, corner.getLinearDistanceTo(cornerWithAngularOffset), getMaxLinearOffset().getValue());
                    } else if (Math.abs(angleOffset) > getMaxAngularOffset()) {
                        Logger.debug("Offsets too large {} : angle offset {} > {}",
                                offsets1, Math.abs(angleOffset), getMaxAngularOffset());
                    } else {
                        // 找到足够好的位置修正，结束循环
                        break;
                    }

                    // 位置修正不足，尝试使用修正后的位置再次计算
                }

                // 记录偏移量已接受
                Logger.debug("Offsets accepted {}", offsets1);

                // 计算所有尝试的累积偏移量
                offsets1 = wantedLocationN1.subtractWithRotation(locationN1);

                // 减去视觉中心偏移
                offsets1 = offsets1.subtract(bottomVisionSettings.getVisionOffset().rotateXy(wantedAngleN1));

                // 显示处理结果，包括图像、零件、偏移量、相机和喷嘴信息
                displayResult(OpenCvUtils.toBufferedImage(pipeline.getWorkingImage()), partN1, offsets1, camera, n1);

                // 检查偏移量是否符合要求
                offsetsCheck(partN1, n1, offsets1);
                n1P.alignmentOffsets = new PartAlignment.PartAlignmentOffset(offsets1, true);
            }

            BottomVisionSettings bottomVisionSettings2 = getInheritedVisionSettings(partN2);

            try (CvPipeline pipeline = bottomVisionSettings2.getPipeline()) {
                Location offsets2 = new Location(locationN2.getUnits());
                pipeline.setProperty("needSettle", false);

                // 尝试多次获取零件的正确位置
                for (int pass = 0; ; ) {
                    // 处理管道并获取结果的旋转矩形
                    RotatedRect rect = processPipelineAndGetResultMulti(pipeline, camera, partN2, n2,
                            wantedLocationN2, locationN2, bottomVisionSettings2);

                    // 记录调试信息，包括底部视觉部件的ID和识别的矩形信息
                    Logger.debug("Bottom vision part {} result rect {}", partN2.getId(), rect);

                    // 创建偏移量对象，表示相机中心到定位零件的物理距离
                    offsets2 = VisionUtils.getPixelCenterOffsets(camera, rect.center.x, rect.center.y);

                    // 计算角度偏移量
                    double angleOffset = VisionUtils.getPixelAngle(camera, rect.angle) - wantedAngleN2;

                    // 大多数OpenCV管道只能告诉我们识别到的矩形的角度位于0°到90°的范围内，
                    // 因此需要规范化角度范围为-45°到+45°。参见angleNorm()。
                    if (bottomVisionSettings2.getMaxRotation() == MaxRotation.Adjust) {
                        angleOffset = Utils2D.angleNorm(angleOffset);
                    } else {
                        // 旋转超过180°在一个方向上没有意义
                        angleOffset = Utils2D.angleNorm(angleOffset, 180);
                    }

                    // 当后续旋转喷嘴以补偿角度偏移时，X、Y偏移也会发生变化，因此需要补偿
                    offsets2 = offsets2.rotateXy(-angleOffset)
                            .derive(null, null, null, angleOffset);
                    locationN2 = locationN2.subtractWithRotation(offsets2);

                    if (++pass >= maxVisionPasses) {
                        // 达到最大尝试次数，结束循环
                        break;
                    }
                    break;

   /*                 // 检查中心和角的偏移是否在允许的范围内，如果不在范围内，则继续尝试
                    Point corners[] = new Point[4];
                    rect.points(corners);
                    Location corner = VisionUtils.getPixelCenterOffsets(camera, corners[0].x, corners[0].y)
                            .convertToUnits(maxLinearOffset.getUnits());
                    Location cornerWithAngularOffset = corner.rotateXy(angleOffset);
                    partSizeCheck(partN2, bottomVisionSettings2, rect, camera);

                    if (center.getLinearDistanceTo(offsets2) > getMaxLinearOffset().getValue()) {
                        Logger.debug("Offsets too large {} : center offset {} > {}",
                                offsets2, center.getLinearDistanceTo(offsets2), getMaxLinearOffset().getValue());
                    } else if (corner.getLinearDistanceTo(cornerWithAngularOffset) > getMaxLinearOffset().getValue()) {
                        Logger.debug("Offsets too large {} : corner offset {} > {}",
                                offsets2, corner.getLinearDistanceTo(cornerWithAngularOffset), getMaxLinearOffset().getValue());
                    } else if (Math.abs(angleOffset) > getMaxAngularOffset()) {
                        Logger.debug("Offsets too large {} : angle offset {} > {}",
                                offsets2, Math.abs(angleOffset), getMaxAngularOffset());
                    } else {
                        // 找到足够好的位置修正，结束循环
                        break;
                    }
*/
                    // 位置修正不足，尝试使用修正后的位置再次计算
                }

                // 记录偏移量已接受
                Logger.debug("Offsets accepted {}", offsets2);

                // 计算所有尝试的累积偏移量
                offsets2 = wantedLocationN2.subtractWithRotation(locationN2);

                // 减去视觉中心偏移
                offsets2 = offsets2.subtract(bottomVisionSettings2.getVisionOffset().rotateXy(wantedAngleN2));

                // 显示处理结果，包括图像、零件、偏移量、相机和喷嘴信息
                displayResult(OpenCvUtils.toBufferedImage(pipeline.getWorkingImage()), partN2, offsets2, camera, n2);

                // 检查偏移量是否符合要求
                offsetsCheck(partN2, n2, offsets2);
                n2P.alignmentOffsets = new PartAlignment.PartAlignmentOffset(offsets2, true);
            }
        } else {
            Nozzle n = pps.get(0).nozzle;
            //只有N1有元件的时候
            if (n.getName().equals("N1")) {
                // TODO 直接计算shotlocation，然后底部视觉处理.
                PnpJobPlanner.PlannedPlacement n1P = pps.get(0);
                final Nozzle n1 = n1P.nozzle;
                final PnpJobProcessor.JobPlacement jobPlacementN1 = n1P.jobPlacement;
                final Placement placementN1 = jobPlacementN1.getPlacement();
                final BoardLocation boardLocationN1 = jobPlacementN1.getBoardLocation();
                final Part partN1 = placementN1.getPart();
                // 获取所需的旋转角度，首先使用放置位置的旋转角度，如果存在板位置，则使用修正后的位置的角度
                double wantedAngleN1 = placementN1.getLocation().getRotation();
                if (boardLocationN1 != null) {
                    wantedAngleN1 = Utils2D.calculateBoardPlacementLocation(boardLocationN1, placementN1.getLocation())
                            .getRotation();
                }
                // 规范化旋转角度为-180°到180°之间的范围
                wantedAngleN1 = Utils2D.angleNorm(wantedAngleN1, 180.);
                // 获取所需的位置，包括零件高度和旋转角度

                Location wantedLocationN1 = getCameraLocationAtPartHeight(partN1, camera, n1, wantedAngleN1);
                // 初始化吸嘴位置和中心位置
                Location locationN1 = wantedLocationN1;
                final Location center = new Location(maxLinearOffset.getUnits());
                // 获取零件的继承的视觉设置
                Location shotLocationN1 = getShotLocation2(partN1, camera, n1, wantedLocationN1, locationN1);
                n1.moveTo(shotLocationN1);
                try (CvPipeline pipeline = bottomVisionSettings.getPipeline()) {
                    // 初始化偏移量，用于迭代计算
                    Location offsets1 = new Location(locationN1.getUnits());
                    // 尝试多次获取零件的正确位置
                    for (int pass = 0; ; ) {

                        // 处理管道并获取结果的旋转矩形
                        RotatedRect rect = processPipelineAndGetResultMulti(pipeline, camera, partN1, n1,
                                wantedLocationN1, locationN1, bottomVisionSettings);

                        // 记录调试信息，包括底部视觉部件的ID和识别的矩形信息
                        Logger.debug("Bottom vision part {} result rect {}", partN1.getId(), rect);

                        // 创建偏移量对象，表示相机中心到定位零件的物理距离
                        offsets1 = VisionUtils.getPixelCenterOffsets(camera, rect.center.x, rect.center.y);

                        // 计算角度偏移量
                        double angleOffset = VisionUtils.getPixelAngle(camera, rect.angle) - wantedAngleN1;

                        // 大多数OpenCV管道只能告诉我们识别到的矩形的角度位于0°到90°的范围内，
                        // 因此需要规范化角度范围为-45°到+45°。参见angleNorm()。
                        if (bottomVisionSettings.getMaxRotation() == MaxRotation.Adjust) {
                            angleOffset = Utils2D.angleNorm(angleOffset);
                        } else {
                            // 旋转超过180°在一个方向上没有意义
                            angleOffset = Utils2D.angleNorm(angleOffset, 180);
                        }

                        // 当后续旋转喷嘴以补偿角度偏移时，X、Y偏移也会发生变化，因此需要补偿
                        offsets1 = offsets1.rotateXy(-angleOffset)
                                .derive(null, null, null, angleOffset);
                        locationN1 = locationN1.subtractWithRotation(offsets1);

                        if (++pass >= maxVisionPasses) {
                            // 达到最大尝试次数，结束循环
                            break;
                        }

                        // 检查中心和角的偏移是否在允许的范围内，如果不在范围内，则继续尝试
                        Point corners[] = new Point[4];
                        rect.points(corners);
                        Location corner = VisionUtils.getPixelCenterOffsets(camera, corners[0].x, corners[0].y)
                                .convertToUnits(maxLinearOffset.getUnits());
                        Location cornerWithAngularOffset = corner.rotateXy(angleOffset);
                        partSizeCheck(partN1, bottomVisionSettings, rect, camera);

                        if (center.getLinearDistanceTo(offsets1) > getMaxLinearOffset().getValue()) {
                            Logger.debug("Offsets too large {} : center offset {} > {}",
                                    offsets1, center.getLinearDistanceTo(offsets1), getMaxLinearOffset().getValue());
                        } else if (corner.getLinearDistanceTo(cornerWithAngularOffset) > getMaxLinearOffset().getValue()) {
                            Logger.debug("Offsets too large {} : corner offset {} > {}",
                                    offsets1, corner.getLinearDistanceTo(cornerWithAngularOffset), getMaxLinearOffset().getValue());
                        } else if (Math.abs(angleOffset) > getMaxAngularOffset()) {
                            Logger.debug("Offsets too large {} : angle offset {} > {}",
                                    offsets1, Math.abs(angleOffset), getMaxAngularOffset());
                        } else {
                            // 找到足够好的位置修正，结束循环
                            break;
                        }

                        // 位置修正不足，尝试使用修正后的位置再次计算
                    }

                    // 记录偏移量已接受
                    Logger.debug("Offsets accepted {}", offsets1);

                    // 计算所有尝试的累积偏移量
                    offsets1 = wantedLocationN1.subtractWithRotation(locationN1);

                    // 减去视觉中心偏移
                    offsets1 = offsets1.subtract(bottomVisionSettings.getVisionOffset().rotateXy(wantedAngleN1));

                    // 显示处理结果，包括图像、零件、偏移量、相机和喷嘴信息
                    displayResult(OpenCvUtils.toBufferedImage(pipeline.getWorkingImage()), partN1, offsets1, camera, n1);

                    // 检查偏移量是否符合要求
                    offsetsCheck(partN1, n1, offsets1);
                    n1P.alignmentOffsets = new PartAlignment.PartAlignmentOffset(offsets1, true);
                }
            }
            //只有N2有元件的时候
            else if (n.getName().equals("N2")) {
                //TODO 计算shotlocation，再加上偏移量，然后再底部视觉处理
                PnpJobPlanner.PlannedPlacement n2P = pps.get(0);
                final Nozzle n2 = n2P.nozzle;
                final PnpJobProcessor.JobPlacement jobPlacementN2 = n2P.jobPlacement;
                final Placement placementN2 = jobPlacementN2.getPlacement();
                final BoardLocation boardLocationN2 = jobPlacementN2.getBoardLocation();
                final Part partN2 = placementN2.getPart();
                // 获取所需的旋转角度，首先使用放置位置的旋转角度，如果存在板位置，则使用修正后的位置的角度
                double wantedAngleN2 = placementN2.getLocation().getRotation();
                if (boardLocationN2 != null) {
                    wantedAngleN2 = Utils2D.calculateBoardPlacementLocation(boardLocationN2, placementN2.getLocation())
                            .getRotation();
                }
                // 规范化旋转角度为-180°到180°之间的范围
                wantedAngleN2 = Utils2D.angleNorm(wantedAngleN2, 180.);

                // 获取所需的位置，包括零件高度和旋转角度
                Location wantedLocationN2 = getCameraLocationAtPartHeight(partN2, camera, n2, wantedAngleN2);

                // 初始化吸嘴位置和中心位置
                Location locationN2 = wantedLocationN2;

                final Location center = new Location(maxLinearOffset.getUnits());
                // 获取零件的继承的视觉设置
                Location shotLocationN2 = getShotLocation2(partN2, camera, n2, wantedLocationN2, locationN2);

                List<Nozzle> nozzles = Configuration.get().getMachine().getHeads().get(0).getNozzles();

                Location n2Offest = nozzles.get(1).getHeadOffsets();
                Location n1Offset = nozzles.get(0).getHeadOffsets();
                Location shotLocationNew = shotLocationN2;
                shotLocationNew.setX(shotLocationNew.getX() + n2Offest.getX() - n1Offset.getX());
                shotLocationNew.setY(shotLocationNew.getY() + n2Offest.getY() - n1Offset.getY());


                n2.moveTo(shotLocationNew);
                try (CvPipeline pipeline = bottomVisionSettings.getPipeline()) {
                    // 初始化偏移量，用于迭代计算
                    Location offsets2 = new Location(locationN2.getUnits());
                    // 尝试多次获取零件的正确位置
                    for (int pass = 0; ; ) {

                        // 处理管道并获取结果的旋转矩形
                        RotatedRect rect = processPipelineAndGetResultMulti(pipeline, camera, partN2, n2,
                                wantedLocationN2, locationN2, bottomVisionSettings);

                        // 记录调试信息，包括底部视觉部件的ID和识别的矩形信息
                        Logger.debug("Bottom vision part {} result rect {}", partN2.getId(), rect);

                        // 创建偏移量对象，表示相机中心到定位零件的物理距离
                        offsets2 = VisionUtils.getPixelCenterOffsets(camera, rect.center.x, rect.center.y);

                        // 计算角度偏移量
                        double angleOffset = VisionUtils.getPixelAngle(camera, rect.angle) - wantedAngleN2;

                        // 大多数OpenCV管道只能告诉我们识别到的矩形的角度位于0°到90°的范围内，
                        // 因此需要规范化角度范围为-45°到+45°。参见angleNorm()。
                        if (bottomVisionSettings.getMaxRotation() == MaxRotation.Adjust) {
                            angleOffset = Utils2D.angleNorm(angleOffset);
                        } else {
                            // 旋转超过180°在一个方向上没有意义
                            angleOffset = Utils2D.angleNorm(angleOffset, 180);
                        }

                        // 当后续旋转喷嘴以补偿角度偏移时，X、Y偏移也会发生变化，因此需要补偿
                        offsets2 = offsets2.rotateXy(-angleOffset)
                                .derive(null, null, null, angleOffset);
                        locationN2 = locationN2.subtractWithRotation(offsets2);

                        if (++pass >= maxVisionPasses) {
                            // 达到最大尝试次数，结束循环
                            break;
                        }

                        // 检查中心和角的偏移是否在允许的范围内，如果不在范围内，则继续尝试
                        Point corners[] = new Point[4];
                        rect.points(corners);
                        Location corner = VisionUtils.getPixelCenterOffsets(camera, corners[0].x, corners[0].y)
                                .convertToUnits(maxLinearOffset.getUnits());
                        Location cornerWithAngularOffset = corner.rotateXy(angleOffset);
                        partSizeCheck(partN2, bottomVisionSettings, rect, camera);

                        if (center.getLinearDistanceTo(offsets2) > getMaxLinearOffset().getValue()) {
                            Logger.debug("Offsets too large {} : center offset {} > {}",
                                    offsets2, center.getLinearDistanceTo(offsets2), getMaxLinearOffset().getValue());
                        } else if (corner.getLinearDistanceTo(cornerWithAngularOffset) > getMaxLinearOffset().getValue()) {
                            Logger.debug("Offsets too large {} : corner offset {} > {}",
                                    offsets2, corner.getLinearDistanceTo(cornerWithAngularOffset), getMaxLinearOffset().getValue());
                        } else if (Math.abs(angleOffset) > getMaxAngularOffset()) {
                            Logger.debug("Offsets too large {} : angle offset {} > {}",
                                    offsets2, Math.abs(angleOffset), getMaxAngularOffset());
                        } else {
                            // 找到足够好的位置修正，结束循环
                            break;
                        }

                        // 位置修正不足，尝试使用修正后的位置再次计算
                    }

                    // 记录偏移量已接受
                    Logger.debug("Offsets accepted {}", offsets2);

                    // 计算所有尝试的累积偏移量
                    offsets2 = wantedLocationN2.subtractWithRotation(locationN2);

                    // 减去视觉中心偏移
                    offsets2 = offsets2.subtract(bottomVisionSettings.getVisionOffset().rotateXy(wantedAngleN2));

                    // 显示处理结果，包括图像、零件、偏移量、相机和喷嘴信息
                    displayResult(OpenCvUtils.toBufferedImage(pipeline.getWorkingImage()), partN2, offsets2, camera, n2);

                    // 检查偏移量是否符合要求
                    offsetsCheck(partN2, n2, offsets2);
                    n2P.alignmentOffsets = new PartAlignment.PartAlignmentOffset(offsets2, true);
                }

            }

        }
        return pps;

    }

    private PartAlignmentOffset findOffsetsPreRotate(Part part, BoardLocation boardLocation,
                                                     Placement placement, Nozzle nozzle, Camera camera, BottomVisionSettings bottomVisionSettings)
            throws Exception {
        // 获取所需的旋转角度，首先使用放置位置的旋转角度，如果存在板位置，则使用修正后的位置的角度
        double wantedAngle = placement.getLocation().getRotation();
        if (boardLocation != null) {
            wantedAngle = Utils2D.calculateBoardPlacementLocation(boardLocation, placement.getLocation())
                    .getRotation();
        }
        // 规范化旋转角度为-180°到180°之间的范围
        wantedAngle = Utils2D.angleNorm(wantedAngle, 180.);

        // 获取所需的位置，包括零件高度和旋转角度
        Location wantedLocation = getCameraLocationAtPartHeight(part, camera, nozzle, wantedAngle);

        // 初始化吸嘴位置和中心位置
        Location nozzleLocation = wantedLocation;
        final Location center = new Location(maxLinearOffset.getUnits());

        // 使用try-with-resources语句创建一个CvPipeline对象
        try (CvPipeline pipeline = bottomVisionSettings.getPipeline()) {

            // 初始化偏移量，用于迭代计算
            Location offsets = new Location(nozzleLocation.getUnits());

            // 尝试多次获取零件的正确位置
            for (int pass = 0; ; ) {
                // 处理管道并获取结果的旋转矩形
                RotatedRect rect = processPipelineAndGetResult(pipeline, camera, part, nozzle,
                        wantedLocation, nozzleLocation, bottomVisionSettings);

                // 记录调试信息，包括底部视觉部件的ID和识别的矩形信息
                Logger.debug("Bottom vision part {} result rect {}", part.getId(), rect);

                // 创建偏移量对象，表示相机中心到定位零件的物理距离
                offsets = VisionUtils.getPixelCenterOffsets(camera, rect.center.x, rect.center.y);

                // 计算角度偏移量
                double angleOffset = VisionUtils.getPixelAngle(camera, rect.angle) - wantedAngle;

                // 大多数OpenCV管道只能告诉我们识别到的矩形的角度位于0°到90°的范围内，
                // 因此需要规范化角度范围为-45°到+45°。参见angleNorm()。
                if (bottomVisionSettings.getMaxRotation() == MaxRotation.Adjust) {
                    angleOffset = Utils2D.angleNorm(angleOffset);
                } else {
                    // 旋转超过180°在一个方向上没有意义
                    angleOffset = Utils2D.angleNorm(angleOffset, 180);
                }

                // 当后续旋转喷嘴以补偿角度偏移时，X、Y偏移也会发生变化，因此需要补偿
                offsets = offsets.rotateXy(-angleOffset)
                        .derive(null, null, null, angleOffset);
                nozzleLocation = nozzleLocation.subtractWithRotation(offsets);

                if (++pass >= maxVisionPasses) {
                    // 达到最大尝试次数，结束循环
                    break;
                }

                // 检查中心和角的偏移是否在允许的范围内，如果不在范围内，则继续尝试
                Point corners[] = new Point[4];
                rect.points(corners);
                Location corner = VisionUtils.getPixelCenterOffsets(camera, corners[0].x, corners[0].y)
                        .convertToUnits(maxLinearOffset.getUnits());
                Location cornerWithAngularOffset = corner.rotateXy(angleOffset);
                partSizeCheck(part, bottomVisionSettings, rect, camera);

                if (center.getLinearDistanceTo(offsets) > getMaxLinearOffset().getValue()) {
                    Logger.debug("Offsets too large {} : center offset {} > {}",
                            offsets, center.getLinearDistanceTo(offsets), getMaxLinearOffset().getValue());
                } else if (corner.getLinearDistanceTo(cornerWithAngularOffset) > getMaxLinearOffset().getValue()) {
                    Logger.debug("Offsets too large {} : corner offset {} > {}",
                            offsets, corner.getLinearDistanceTo(cornerWithAngularOffset), getMaxLinearOffset().getValue());
                } else if (Math.abs(angleOffset) > getMaxAngularOffset()) {
                    Logger.debug("Offsets too large {} : angle offset {} > {}",
                            offsets, Math.abs(angleOffset), getMaxAngularOffset());
                } else {
                    // 找到足够好的位置修正，结束循环
                    break;
                }

                // 位置修正不足，尝试使用修正后的位置再次计算
            }

            // 记录偏移量已接受
            Logger.debug("Offsets accepted {}", offsets);

            // 计算所有尝试的累积偏移量
            offsets = wantedLocation.subtractWithRotation(nozzleLocation);

            // 减去视觉中心偏移
            offsets = offsets.subtract(bottomVisionSettings.getVisionOffset().rotateXy(wantedAngle));

            // 显示处理结果，包括图像、零件、偏移量、相机和喷嘴信息
            displayResult(OpenCvUtils.toBufferedImage(pipeline.getWorkingImage()), part, offsets, camera, nozzle);

            // 检查偏移量是否符合要求
            offsetsCheck(part, nozzle, offsets);

            // 返回零件对齐偏移量对象
            return new PartAlignment.PartAlignmentOffset(offsets, true);
        }
    }


    private PartAlignmentOffset findOffsetsPostRotate(Part part, BoardLocation boardLocation,
                                                      Placement placement, Nozzle nozzle, Camera camera, BottomVisionSettings bottomVisionSettings)
            throws Exception {
        // 创建一个位置，其X、Y坐标与相机的X、Y坐标相同，Z坐标为零件高度，并且旋转角度为0（除非启用了预旋转）
        Location wantedLocation = getCameraLocationAtPartHeight(part, camera, nozzle, 0.);

        // 使用try-with-resources语句创建一个CvPipeline对象
        try (CvPipeline pipeline = bottomVisionSettings.getPipeline()) {
            // 处理管道并获取结果的旋转矩形
            RotatedRect rect = processPipelineAndGetResult(pipeline, camera, part, nozzle, wantedLocation, wantedLocation, bottomVisionSettings);

            // 记录调试信息，包括底部视觉部件的ID和识别的矩形信息
            Logger.debug("Bottom vision part {} result rect {}", part.getId(), rect);

            // 创建偏移量对象。这是从相机中心到定位零件的物理距离。
            Location offsets = VisionUtils.getPixelCenterOffsets(camera, rect.center.x, rect.center.y);

            // 获取识别矩形的角度偏移量
            double angleOffset = VisionUtils.getPixelAngle(camera, rect.angle);
            // 大多数OpenCV管道只能告诉我们识别到的矩形的角度位于0°到90°的范围内，
            // 因为它无法区分矩形的哪一边是哪一边。我们可以假设零件的旋转不会超过+/-45º。
            // 因此，我们将角度范围从0°到90°更改为-45°到+45°。参见angleNorm()：
            if (bottomVisionSettings.getMaxRotation() == MaxRotation.Adjust) {
                angleOffset = Utils2D.angleNorm(angleOffset);
            } else {
                // 在一个方向上旋转超过180°没有意义
                angleOffset = Utils2D.angleNorm(angleOffset, 180);
            }

            // 检查零件的大小是否符合要求
            partSizeCheck(part, bottomVisionSettings, rect, camera);

            // 在偏移量上设置角度偏移量
            offsets = offsets.derive(null, null, null, angleOffset);

            // 减去视觉中心偏移
            offsets = offsets.subtract(bottomVisionSettings.getVisionOffset().rotateXy(offsets.getRotation()));

            // 显示处理结果，包括图像、零件、偏移量、相机和喷嘴信息
            displayResult(OpenCvUtils.toBufferedImage(pipeline.getWorkingImage()), part, offsets, camera, nozzle);

            // 检查偏移量是否符合要求
            offsetsCheck(part, nozzle, offsets);

            // 返回零件对齐偏移量对象
            return new PartAlignmentOffset(offsets, false);
        }
    }


    protected void offsetsCheck(Part part, Nozzle nozzle, Location offsets) throws Exception {
        if (nozzle.getNozzleTip() != null) {
            NozzleTip nt = nozzle.getNozzleTip();
            Length offsetsLength = offsets.getLinearLengthTo(Location.origin);
            Length maxPickTolerance = nt.getMaxPickTolerance();
            if (offsetsLength.compareTo(maxPickTolerance) > 0) {
                LengthConverter lengthConverter = new LengthConverter();
                throw new Exception("Part " + part.getId() + " bottom vision offsets length " + lengthConverter.convertForward(offsetsLength)
                        + " larger than the allowed Max. Pick Tolerance " + lengthConverter.convertForward(maxPickTolerance) + " set on nozzle tip "
                        + nt.getName() + ".");
            }
        }
    }

    private boolean partSizeCheck(Part part, BottomVisionSettings bottomVisionSettings, RotatedRect partRect, Camera camera) throws Exception {
        // Check if this test needs to be done
        Location partSize = bottomVisionSettings.getPartCheckSize(part, false);
        if (partSize == null) {
            return true;
        }

        // Make sure width is the longest dimension
        if (partSize.getY() > partSize.getX()) {
            partSize = new Location(partSize.getUnits(), partSize.getY(), partSize.getX(), 0, 0);
        }

        double pxWidth = VisionUtils.toPixels(partSize.getLengthX(), camera);
        double pxLength = VisionUtils.toPixels(partSize.getLengthY(), camera);

        // Make sure width is the longest dimension
        Size measuredSize = partRect.size;
        if (measuredSize.height > measuredSize.width) {
            double mLength = measuredSize.height;
            double mWidth = measuredSize.width;
            measuredSize.height = mWidth;
            measuredSize.width = mLength;
        }

        double widthTolerance = pxWidth * 0.01 * (double) bottomVisionSettings.getCheckSizeTolerancePercent();
        double heightTolerance = pxLength * 0.01 * (double) bottomVisionSettings.getCheckSizeTolerancePercent();
        double pxMaxWidth = pxWidth + widthTolerance;
        double pxMinWidth = pxWidth - widthTolerance;
        double pxMaxLength = pxLength + heightTolerance;
        double pxMinLength = pxLength - heightTolerance;
        boolean ret;
        Location upp = camera.getUnitsPerPixelAtZ();
        LengthConverter lengthConverter = new LengthConverter();
        String measuredWidth = lengthConverter.convertForward(upp.getLengthX().multiply(measuredSize.width));
        String measuredLength = lengthConverter.convertForward(upp.getLengthY().multiply(measuredSize.height));
        String nominalWidth = lengthConverter.convertForward(partSize.getLengthX());
        String nominalLength = lengthConverter.convertForward(partSize.getLengthY());
        String msg;
        if (measuredSize.width > pxMaxWidth) {
            msg = String.format("Part %s width too large: nominal %s, limit %s, measured %s", part.getId(),
                    nominalWidth, lengthConverter.convertForward(upp.getLengthX().multiply(pxMaxWidth)),
                    measuredWidth);
            ret = false;
        } else if (measuredSize.width < pxMinWidth) {
            msg = String.format("Part %s width too small: nominal %s, limit %s, measured %s", part.getId(),
                    nominalWidth, lengthConverter.convertForward(upp.getLengthX().multiply(pxMinWidth)),
                    measuredWidth);
            ret = false;
        } else if (measuredSize.height > pxMaxLength) {
            msg = String.format("Part %s length too large: nominal %s, limit %s, measured %s", part.getId(),
                    nominalLength, lengthConverter.convertForward(upp.getLengthY().multiply(pxMaxLength)),
                    measuredLength);
            ret = false;
        } else if (measuredSize.height < pxMinLength) {
            msg = String.format("Part %s length too small: nominal %s, limit %s, measured %s", part.getId(),
                    nominalLength, lengthConverter.convertForward(upp.getLengthY().multiply(pxMinLength)),
                    measuredLength);
            ret = false;
        } else {
            msg = String.format("Part %s size ok. Width %s, Length %s", part.getId(),
                    measuredWidth, measuredLength);
            ret = true;
        }
        Logger.debug(msg);
        if (!ret) {
            throw new Exception(msg);
        }
        return true;
    }

    @Override
    public void displayResult(BufferedImage image, Part part, Location offsets, Camera camera, Nozzle nozzle) {
        String s = part.getId();
        if (offsets != null) {
            LengthConverter lengthConverter = new LengthConverter();
            DoubleConverter doubleConverter = new DoubleConverter(Configuration.get().getLengthDisplayFormat());
            s += "  |  X:" + lengthConverter.convertForward(offsets.getLengthX()) + " "
                    + "Y:" + lengthConverter.convertForward(offsets.getLengthY()) + " "
                    + "C:" + doubleConverter.convertForward(offsets.getRotation())
                    + " Δ:" + lengthConverter.convertForward(offsets.getLinearLengthTo(Location.origin));
        }
        Logger.debug("Alignment result: {}", s);
        MainFrame mainFrame = MainFrame.get();
        if (mainFrame != null) {
            try {
                mainFrame
                        .getCameraViews()
                        .getCameraView(camera)
                        .showFilteredImage(image, s,
                                2000);
                // Also make sure the right nozzle is selected for correct cross-hair rotation.
                MovableUtils.fireTargetedUserAction(nozzle);
            } catch (Exception e) {
                // Throw away, just means we're running outside of the UI.
            }
        }
    }

    public void preparePipelineMulti(CvPipeline pipeline, Map<String, Object> pipelineParameterAssignments,
                                     Camera camera, Package pkg, Nozzle nozzle, NozzleTip nozzleTip, Location wantedLocation,
                                     Location adjustedNozzleLocation, BottomVisionSettings bottomVisionSettings) throws Exception {
// 从Package对象中获取VisionCompositing对象
        VisionCompositing visionCompositing = pkg.getVisionCompositing();
        // 创建一个Composite对象，用于合成视觉信息
        VisionCompositing.Composite composite = visionCompositing.new Composite(
                pkg, bottomVisionSettings, nozzle, nozzleTip, camera, wantedLocation);
        // 如果视觉合成方法被强制启用且合成解决方案无效，则抛出异常
        if (visionCompositing.getCompositingMethod().isEnforced()
                && composite.getCompositingSolution().isInvalid()) {
            throw new Exception("Vision Compositing has not found a valid solution for package " + pkg.getId() + ". "
                    + "Status: " + composite.getCompositingSolution() + ", " + composite.getDiagnostics() + ". "
                    + "For more diagnostic information go to the Vision Compositing tab on package " + pkg.getId() + ". ");
        }
        // 重置可重用的CvPipeline
        pipeline.resetReusedPipeline();
        // 遍历Composite对象中的ShotsTravel列表
        for (Shot shot : composite.getShotsTravel()) {
            // 获取相机的像素单元大小
            Location upp = camera.getUnitsPerPixelAtZ();
            // 设置CvPipeline中的属性
            pipeline.setProperty("camera", camera);
            Length samplingSize = new Length(0.1, LengthUnit.Millimeters); // Default, if no setting on nozzle tip.
            // Set the footprint.
            pipeline.setProperty("footprint", composite.getFootprint());
            pipeline.setProperty("footprint.rotation", wantedLocation.getRotation());
            pipeline.setProperty("footprint.xOffset", new Length(shot.getX(), composite.getUnits()));
            pipeline.setProperty("footprint.yOffset", new Length(shot.getY(), composite.getUnits()));
            // Crop to fit into the mask.
            // TODO: make the template circular.
            // 裁剪以适应掩模
            Length maxDim = new Length(Math.sqrt(2) * shot.getMaxMaskRadius(), composite.getUnits())
                    .subtract(nozzleTip.getMaxPickTolerance().multiply(1.2));
            pipeline.setProperty("footprint.maxWidth", maxDim);
            pipeline.setProperty("footprint.maxHeight", maxDim);
            // Set alignment parameters.
            // 设置对齐参数
            pipeline.setProperty("MinAreaRect.center", wantedLocation);
            pipeline.setProperty("MinAreaRect.expectedAngle", wantedLocation.getRotation());
            pipeline.setProperty("DetectRectlinearSymmetry.center", wantedLocation);
            pipeline.setProperty("DetectRectlinearSymmetry.expectedAngle", wantedLocation.getRotation());
            // Set the background removal properties.
            // 设置背景去除属性
            pipeline.setProperty("DetectRectlinearSymmetry.searchDistance", nozzleTip.getMaxPickTolerance()
                    .multiply(1.2)); // Allow for some tolerance, we will check the result later.
            pipeline.setProperty("MaskCircle.diameter", new Length(shot.getMaxMaskRadius() * 2, composite.getUnits()));
            // 如果喷嘴是ReferenceNozzleTip类型，考虑背景校准
            if (nozzleTip instanceof ReferenceNozzleTip) {
                ReferenceNozzleTipCalibration calibration = ((ReferenceNozzleTip) nozzleTip).getCalibration();
                if (calibration != null
                        && calibration.getBackgroundCalibrationMethod() != BackgroundCalibrationMethod.None) {
                    samplingSize = calibration.getMinimumDetailSize().multiply(0.5);
                    pipeline.setProperty("MaskHsv.hueMin",
                            Math.max(0, calibration.getBackgroundMinHue() - calibration.getBackgroundTolHue()));
                    pipeline.setProperty("MaskHsv.hueMax",
                            Math.min(255, calibration.getBackgroundMaxHue() + calibration.getBackgroundTolHue()));
                    pipeline.setProperty("MaskHsv.saturationMin",
                            Math.max(0, calibration.getBackgroundMinSaturation() - calibration.getBackgroundTolSaturation()));
                    pipeline.setProperty("MaskHsv.saturationMax", 255);
                    // no need to restrict to this: Math.min(255, calibration.getBackgroundMaxSaturation() + calibration.getBackgroundTolSaturation()));
                    pipeline.setProperty("MaskHsv.valueMin", 0);
                    // no need to restrict to this: Math.max(0, calibration.getBackgroundMinValue() - calibration.getBackgroundTolValue()));
                    pipeline.setProperty("MaskHsv.valueMax",
                            Math.min(255, calibration.getBackgroundMaxValue() + calibration.getBackgroundTolValue()));
                }
            }
            // 如果采样大小小于2个像素，则将其设置为至少为2个像素，否则子采样将成本过高
            if (samplingSize.compareTo(upp.getLengthX().multiply(2)) < 0) {
                // We want the sampling size to at least be 2 pixels, otherwise subSampling will be too costly.
                // This means: a camera with less than 4 pixels per smallest contact size, is likely to cause problems
                // but that's to be expected anyways.
                samplingSize = upp.getLengthX().multiply(2);
            }
            pipeline.setProperty("BlurGaussian.kernelSize", samplingSize);
            pipeline.setProperty("DetectRectlinearSymmetry.subSampling", samplingSize);
            // Add a margin for edge detection.
            // 为边缘检测添加边缘
            pipeline.setProperty("DetectRectlinearSymmetry.maxWidth",
                    new Length(shot.getWidth(), composite.getUnits())
                            .add(samplingSize.multiply(2)));
            pipeline.setProperty("DetectRectlinearSymmetry.maxHeight",
                    new Length(shot.getHeight(), composite.getUnits())
                            .add(samplingSize.multiply(2)));
            // 如果合成解决方案是高级的，则设置更多属性
            if (composite.getCompositingSolution().isAdvanced()) {
                pipeline.setProperty("MinAreaRect.leftEdge", shot.hasLeftEdge());
                pipeline.setProperty("MinAreaRect.rightEdge", shot.hasRightEdge());
                pipeline.setProperty("MinAreaRect.topEdge", shot.hasTopEdge());
                pipeline.setProperty("MinAreaRect.bottomEdge", shot.hasBottomEdge());
                pipeline.setProperty("MinAreaRect.searchAngle", Math.toDegrees(Math.atan2(composite.getTolerance(), composite.getMaxCornerRadius())));
            }
            // 将pipelineParameterAssignments中的属性添加到CvPipeline中
            pipeline.addProperties(pipelineParameterAssignments);

            // Get the shot location, but adjusted by the adjustedNozzleLocation.
            // 获取校准后的Shot位置
            Location shotLocation = composite.getShotLocation(shot)
                    .addWithRotation(adjustedNozzleLocation.subtractWithRotation(wantedLocation));
            // 创建PipelineShot对象并实现其apply、processResult和processCompositeResult方法
            pipeline.new PipelineShot() {
                @Override
                public void apply() {
                    UiUtils.messageBoxOnException(() -> {

                        Set<NozzleTip> pkgNozzles = pkg.getCompatibleNozzleTips();
                        List<Nozzle> nozzles = Configuration.get().getMachine().getHeads().get(0).getNozzles();
                        if (nozzles.size() == 2 && camera.getWidth() > 2000 && pkgNozzles.size() > 1) {
                            if (nozzle.equals(nozzles.get(0))) {
                                if (nozzle.getLocation().getLinearLengthTo(camera.getLocation())
                                        .compareTo(camera.getRoamingRadius()) > 0) {
                                    // Nozzle is not yet in camera roaming radius. Move at safe Z.
                                    // 喷嘴还不在相机漫游半径内。以安全的Z轴移
                                    //MovableUtils.moveToLocationAtSafeZ(nozzle, shotLocation);
                                    //nozzle.moveToTogether(shotLocation, shotLocation.getRotation(), shotLocation.getRotation());

                                } else {
                                    //nozzle.moveToTogether(shotLocation, shotLocation.getRotation(), shotLocation.getRotation());
                                    //nozzle.moveTo(shotLocation);
                                }
                            }
                        } else {
                            if (nozzle.equals(nozzles.get(0))) {
                                if (nozzle.getLocation().getLinearLengthTo(camera.getLocation())
                                        .compareTo(camera.getRoamingRadius()) > 0) {
                                    // Nozzle is not yet in camera roaming radius. Move at safe Z.
                                    // 喷嘴还不在相机漫游半径内。以安全的Z轴移
                                    MovableUtils.moveToLocationAtSafeZ(nozzle, shotLocation);
                                } else {
                                    nozzle.moveTo(shotLocation);
                                }
                            } else if (nozzle.equals(nozzles.get(1))) {
                                Location n2Offest = nozzles.get(1).getHeadOffsets();
                                Location n1Offset = nozzles.get(0).getHeadOffsets();
                                Location shotLocationNew = shotLocation;
                                shotLocationNew.setX(shotLocationNew.getX() + n2Offest.getX() - n1Offset.getX());
                                shotLocationNew.setY(shotLocationNew.getY() + n2Offest.getY() - n1Offset.getY());
                                nozzle.moveTo(shotLocationNew);

                            }
                        }

                        super.apply();
                    });
                }

                @Override
                public void processResult(Result result) {
                    // 积累Shot检测结果
                    composite.accumulateShotDetection(shot, (RotatedRect) result.model);
                }

                @Override
                public Result processCompositeResult() {
                    // 解释Composite结果
                    composite.interpret();
                    return new Result(null, composite.getDetectedRotatedRect());
                }
            };
        }
    }

    public void preparePipeline(CvPipeline pipeline, Map<String, Object> pipelineParameterAssignments,
                                Camera camera, Package pkg, Nozzle nozzle, NozzleTip nozzleTip, Location wantedLocation,
                                Location adjustedNozzleLocation, BottomVisionSettings bottomVisionSettings) throws Exception {
        // 从Package对象中获取VisionCompositing对象
        VisionCompositing visionCompositing = pkg.getVisionCompositing();
        // 创建一个Composite对象，用于合成视觉信息
        VisionCompositing.Composite composite = visionCompositing.new Composite(
                pkg, bottomVisionSettings, nozzle, nozzleTip, camera, wantedLocation);
        // 如果视觉合成方法被强制启用且合成解决方案无效，则抛出异常
        if (visionCompositing.getCompositingMethod().isEnforced()
                && composite.getCompositingSolution().isInvalid()) {
            throw new Exception("Vision Compositing has not found a valid solution for package " + pkg.getId() + ". "
                    + "Status: " + composite.getCompositingSolution() + ", " + composite.getDiagnostics() + ". "
                    + "For more diagnostic information go to the Vision Compositing tab on package " + pkg.getId() + ". ");
        }
        // 重置可重用的CvPipeline
        pipeline.resetReusedPipeline();
        // 遍历Composite对象中的ShotsTravel列表
        for (Shot shot : composite.getShotsTravel()) {
            // 获取相机的像素单元大小
            Location upp = camera.getUnitsPerPixelAtZ();
            // 设置CvPipeline中的属性
            pipeline.setProperty("camera", camera);
            Length samplingSize = new Length(0.1, LengthUnit.Millimeters); // Default, if no setting on nozzle tip.
            // Set the footprint.
            pipeline.setProperty("footprint", composite.getFootprint());
            pipeline.setProperty("footprint.rotation", wantedLocation.getRotation());
            pipeline.setProperty("footprint.xOffset", new Length(shot.getX(), composite.getUnits()));
            pipeline.setProperty("footprint.yOffset", new Length(shot.getY(), composite.getUnits()));
            // Crop to fit into the mask.
            // TODO: make the template circular.
            // 裁剪以适应掩模
            Length maxDim = new Length(Math.sqrt(2) * shot.getMaxMaskRadius(), composite.getUnits())
                    .subtract(nozzleTip.getMaxPickTolerance().multiply(1.2));
            pipeline.setProperty("footprint.maxWidth", maxDim);
            pipeline.setProperty("footprint.maxHeight", maxDim);
            // Set alignment parameters.
            // 设置对齐参数
            pipeline.setProperty("MinAreaRect.center", wantedLocation);
            pipeline.setProperty("MinAreaRect.expectedAngle", wantedLocation.getRotation());
            pipeline.setProperty("DetectRectlinearSymmetry.center", wantedLocation);
            pipeline.setProperty("DetectRectlinearSymmetry.expectedAngle", wantedLocation.getRotation());
            // Set the background removal properties.
            // 设置背景去除属性
            pipeline.setProperty("DetectRectlinearSymmetry.searchDistance", nozzleTip.getMaxPickTolerance()
                    .multiply(1.2)); // Allow for some tolerance, we will check the result later.
            pipeline.setProperty("MaskCircle.diameter", new Length(shot.getMaxMaskRadius() * 2, composite.getUnits()));
            // 如果喷嘴是ReferenceNozzleTip类型，考虑背景校准
            if (nozzleTip instanceof ReferenceNozzleTip) {
                ReferenceNozzleTipCalibration calibration = ((ReferenceNozzleTip) nozzleTip).getCalibration();
                if (calibration != null
                        && calibration.getBackgroundCalibrationMethod() != BackgroundCalibrationMethod.None) {
                    samplingSize = calibration.getMinimumDetailSize().multiply(0.5);
                    pipeline.setProperty("MaskHsv.hueMin",
                            Math.max(0, calibration.getBackgroundMinHue() - calibration.getBackgroundTolHue()));
                    pipeline.setProperty("MaskHsv.hueMax",
                            Math.min(255, calibration.getBackgroundMaxHue() + calibration.getBackgroundTolHue()));
                    pipeline.setProperty("MaskHsv.saturationMin",
                            Math.max(0, calibration.getBackgroundMinSaturation() - calibration.getBackgroundTolSaturation()));
                    pipeline.setProperty("MaskHsv.saturationMax", 255);
                    // no need to restrict to this: Math.min(255, calibration.getBackgroundMaxSaturation() + calibration.getBackgroundTolSaturation()));
                    pipeline.setProperty("MaskHsv.valueMin", 0);
                    // no need to restrict to this: Math.max(0, calibration.getBackgroundMinValue() - calibration.getBackgroundTolValue()));
                    pipeline.setProperty("MaskHsv.valueMax",
                            Math.min(255, calibration.getBackgroundMaxValue() + calibration.getBackgroundTolValue()));
                }
            }
            // 如果采样大小小于2个像素，则将其设置为至少为2个像素，否则子采样将成本过高
            if (samplingSize.compareTo(upp.getLengthX().multiply(2)) < 0) {
                // We want the sampling size to at least be 2 pixels, otherwise subSampling will be too costly.
                // This means: a camera with less than 4 pixels per smallest contact size, is likely to cause problems
                // but that's to be expected anyways.
                samplingSize = upp.getLengthX().multiply(2);
            }
            pipeline.setProperty("BlurGaussian.kernelSize", samplingSize);
            pipeline.setProperty("DetectRectlinearSymmetry.subSampling", samplingSize);
            // Add a margin for edge detection.
            // 为边缘检测添加边缘
            pipeline.setProperty("DetectRectlinearSymmetry.maxWidth",
                    new Length(shot.getWidth(), composite.getUnits())
                            .add(samplingSize.multiply(2)));
            pipeline.setProperty("DetectRectlinearSymmetry.maxHeight",
                    new Length(shot.getHeight(), composite.getUnits())
                            .add(samplingSize.multiply(2)));
            // 如果合成解决方案是高级的，则设置更多属性
            if (composite.getCompositingSolution().isAdvanced()) {
                pipeline.setProperty("MinAreaRect.leftEdge", shot.hasLeftEdge());
                pipeline.setProperty("MinAreaRect.rightEdge", shot.hasRightEdge());
                pipeline.setProperty("MinAreaRect.topEdge", shot.hasTopEdge());
                pipeline.setProperty("MinAreaRect.bottomEdge", shot.hasBottomEdge());
                pipeline.setProperty("MinAreaRect.searchAngle", Math.toDegrees(Math.atan2(composite.getTolerance(), composite.getMaxCornerRadius())));
            }
            // 将pipelineParameterAssignments中的属性添加到CvPipeline中
            pipeline.addProperties(pipelineParameterAssignments);

            // Get the shot location, but adjusted by the adjustedNozzleLocation.
            // 获取校准后的Shot位置
            Location shotLocation = composite.getShotLocation(shot)
                    .addWithRotation(adjustedNozzleLocation.subtractWithRotation(wantedLocation));
            // 创建PipelineShot对象并实现其apply、processResult和processCompositeResult方法
            pipeline.new PipelineShot() {
                @Override
                public void apply() {
                    UiUtils.messageBoxOnException(() -> {

                        Set<NozzleTip> pkgNozzles = pkg.getCompatibleNozzleTips();
                        List<Nozzle> nozzles = Configuration.get().getMachine().getHeads().get(0).getNozzles();
                        if (nozzles.size() == 2 && camera.getWidth() > 2000 && pkgNozzles.size() > 1) {
                            if (nozzle.equals(nozzles.get(0))) {
                                if (nozzle.getLocation().getLinearLengthTo(camera.getLocation())
                                        .compareTo(camera.getRoamingRadius()) > 0) {
                                    // Nozzle is not yet in camera roaming radius. Move at safe Z.
                                    // 喷嘴还不在相机漫游半径内。以安全的Z轴移
                                    //MovableUtils.moveToLocationAtSafeZ(nozzle, shotLocation);
                                    //nozzle.moveToTogether(shotLocation, shotLocation.getRotation(), shotLocation.getRotation());

                                } else {
                                    //nozzle.moveToTogether(shotLocation, shotLocation.getRotation(), shotLocation.getRotation());
                                    //nozzle.moveTo(shotLocation);
                                }
                            }
                        } else {
                            if (nozzle.equals(nozzles.get(0))) {
                                if (nozzle.getLocation().getLinearLengthTo(camera.getLocation())
                                        .compareTo(camera.getRoamingRadius()) > 0) {
                                    // Nozzle is not yet in camera roaming radius. Move at safe Z.
                                    // 喷嘴还不在相机漫游半径内。以安全的Z轴移
                                    MovableUtils.moveToLocationAtSafeZ(nozzle, shotLocation);
                                } else {
                                    nozzle.moveTo(shotLocation);
                                }
                            } else if (nozzle.equals(nozzles.get(1))) {
                                Location n2Offest = nozzles.get(1).getHeadOffsets();
                                Location n1Offset = nozzles.get(0).getHeadOffsets();
                                Location shotLocationNew = shotLocation;
                                shotLocationNew.setX(shotLocationNew.getX() + n2Offest.getX() - n1Offset.getX());
                                shotLocationNew.setY(shotLocationNew.getY() + n2Offest.getY() - n1Offset.getY());
                                nozzle.moveTo(shotLocationNew);

                            }
                        }

                        super.apply();
                    });
                }

                @Override
                public void processResult(Result result) {
                    // 积累Shot检测结果
                    composite.accumulateShotDetection(shot, (RotatedRect) result.model);
                }

                @Override
                public Result processCompositeResult() {
                    // 解释Composite结果
                    composite.interpret();
                    return new Result(null, composite.getDetectedRotatedRect());
                }
            };
        }
    }

    private RotatedRect processPipelineAndGetResultMulti(CvPipeline pipeline, Camera camera,
                                                         Part part, Nozzle nozzle, Location wantedLocation, Location adjustedNozzleLocation, BottomVisionSettings bottomVisionSettings) throws Exception {
        // 准备并配置视觉管道，以进行零件识别
        preparePipelineMulti(pipeline, bottomVisionSettings.getPipelineParameterAssignments(), camera, part.getPackage(),
                nozzle, nozzle.getNozzleTip(), wantedLocation, adjustedNozzleLocation, bottomVisionSettings);
        for (PipelineShot pipelineShot : pipeline.getPipelineShots()) {

            Nozzle n1 = Configuration.get().getMachine().getHeads().get(0).getNozzles()
                    .stream()
                    .findFirst()
                    .orElse(null);
            AffineWarp affineWarp = new AffineWarp();
            List<CvStage> stages = pipeline.getStages();
            for (int i = 0; i < stages.size(); i++) {
                if (stages.get(i) instanceof AffineWarp) {
                    pipeline.remove(stages.get(i));
                }
            }
            if (camera.getWidth() > 2000) {
                if (nozzle == n1 && camera.getLooking() == Camera.Looking.Up) {
                    //左半边
                    //Location test = VisionUtils.getPixelLocation(camera, -20.250438, 5.852280);
                    Location unitsPerPixel = camera.getUnitsPerPixel();

                    Location lefUpLocation = unitsPerPixel.multiply(0 - camera.getWidth() / 2, 0 + camera.getHeight() / 2, 0, 0);
                    Location rightUpLocation = unitsPerPixel.multiply(0, 0 + camera.getHeight() / 2, 0, 0);
                    Location leftDownLocation = unitsPerPixel.multiply(0 - camera.getWidth() / 2, -camera.getHeight() + camera.getHeight() / 2, 0, 0);
                    affineWarp.setX0(lefUpLocation.getX());
                    affineWarp.setY0(lefUpLocation.getY());
                    affineWarp.setX1(rightUpLocation.getX());
                    affineWarp.setY1(rightUpLocation.getY());
                    affineWarp.setX2(leftDownLocation.getX());
                    affineWarp.setY2(leftDownLocation.getY());
                } else {
                    //右半边
                    Location unitsPerPixel = camera.getUnitsPerPixelAtZ().convertToUnits(LengthUnit.Millimeters);
                    Location lefUpLocation = unitsPerPixel.multiply(camera.getWidth() / 2 - camera.getWidth() / 2, 0 + camera.getHeight() / 2, 0, 0);
                    Location rightUpLocation = unitsPerPixel.multiply(camera.getWidth() - camera.getWidth() / 2, 0 + camera.getHeight() / 2, 0, 0);
                    Location leftDownLocation = unitsPerPixel.multiply(camera.getWidth() / 2 - camera.getWidth() / 2, -camera.getHeight() + camera.getHeight() / 2, 0, 0);

                    Location n1Offset = n1.getHeadOffsets();
                    Location n2Offset = Configuration.get().getMachine().getHeads().get(0).getNozzles().get(1).getHeadOffsets();
                    double n2N1OffsetX = n2Offset.getX() - n1Offset.getX();
                    double n2N1OffsetY = n2Offset.getY() - n1Offset.getY();


                    Location leftCenteLocation = unitsPerPixel.convertToUnits(LengthUnit.Millimeters).multiply(camera.getWidth() / 4 - camera.getWidth() / 2, -camera.getHeight() / 2 + camera.getHeight(), 0, 0);
                    Location rightCenteLocation = unitsPerPixel.convertToUnits(LengthUnit.Millimeters).multiply(camera.getWidth() * 3 / 4 - camera.getWidth() / 2, -camera.getHeight() / 2 + camera.getHeight(), 0, 0);
                    double leftRightOffsetX = rightCenteLocation.getX() - leftCenteLocation.getX();
                    double leftRightOffsetY = rightCenteLocation.getY() - leftCenteLocation.getY();


                    double cameraNozzelOffsetX, cameraNozzelOffsetY;
                    leftRightOffsetX = 29.75;
/*
                    if (n2N1OffsetX > leftRightOffsetX) {
                        cameraNozzelOffsetX = (n2N1OffsetX - leftRightOffsetX);
                    } else {
                        cameraNozzelOffsetX = (leftRightOffsetX - n2N1OffsetX);
                    }
*/
                    cameraNozzelOffsetX = n2N1OffsetX - leftRightOffsetX;
                    cameraNozzelOffsetY = n2N1OffsetY * 2;


                    affineWarp.setX0(lefUpLocation.getX() + cameraNozzelOffsetX);
                    affineWarp.setY0(lefUpLocation.getY() + cameraNozzelOffsetY);
                    affineWarp.setX1(rightUpLocation.getX() + cameraNozzelOffsetX);
                    affineWarp.setY1(rightUpLocation.getY() + cameraNozzelOffsetY);
                    affineWarp.setX2(leftDownLocation.getX() + cameraNozzelOffsetX);
                    affineWarp.setY2(leftDownLocation.getY() + cameraNozzelOffsetY);
                }
                pipeline.insert(affineWarp, 3);
                pipeline.insert(affineWarp, pipeline.getStages().size() - 2);
            }
            // 处理管道，执行图像处理操作
            pipeline.process();

            // 获取管道处理的结果
            Result result = pipeline.getResult(VisionUtils.PIPELINE_RESULTS_NAME);

            // 如果找不到名为"results"的结果，则回退到旧名称"result"
            if (result == null) {
                result = pipeline.getResult("result");
            }

            // 如果结果仍然为null，则抛出异常
            if (result == null) {
                throw new Exception(String.format(
                        "ReferenceBottomVision (%s): Pipeline error. Pipeline must contain a result named '%s'.",
                        part.getId(), VisionUtils.PIPELINE_RESULTS_NAME));
            }

            // 如果结果模型为null，则抛出异常
            if (result.model == null) {
                throw new Exception(String.format(
                        "ReferenceBottomVision (%s): No result found.",
                        part.getId()));
            }

            // 检查结果模型是否为正确的类型（RotatedRect）
            if (!(result.model instanceof RotatedRect)) {
                throw new Exception(String.format(
                        "ReferenceBottomVision (%s): Incorrect pipeline result type (%s). Expected RotatedRect.",
                        part.getId(), result.model.getClass().getSimpleName()));
            }

            // 处理管道阶段的结果
            pipelineShot.processResult(result);

            // 显示管道阶段的处理结果
            displayResult(OpenCvUtils.toBufferedImage(pipeline.getWorkingImage()), part, null, camera, nozzle);

        }

        return (RotatedRect) pipeline.getCurrentPipelineShot().processCompositeResult().getModel();
    }


    private RotatedRect processPipelineAndGetResult(CvPipeline pipeline, Camera camera,
                                                    Part part, Nozzle nozzle, Location wantedLocation, Location adjustedNozzleLocation, BottomVisionSettings bottomVisionSettings) throws Exception {
        // 准备并配置视觉管道，以进行零件识别
        preparePipeline(pipeline, bottomVisionSettings.getPipelineParameterAssignments(), camera, part.getPackage(),
                nozzle, nozzle.getNozzleTip(), wantedLocation, adjustedNozzleLocation, bottomVisionSettings);

        // 遍历管道中的每个阶段
        for (PipelineShot pipelineShot : pipeline.getPipelineShots()) {
            // 应用管道阶段操作
            pipelineShot.apply();

            Nozzle n1 = Configuration.get().getMachine().getHeads().get(0).getNozzles()
                    .stream()
                    .findFirst()
                    .orElse(null);
            AffineWarp affineWarp = new AffineWarp();
            List<CvStage> stages = pipeline.getStages();
            for (int i = 0; i < stages.size(); i++) {
                if (stages.get(i) instanceof AffineWarp) {
                    pipeline.remove(stages.get(i));
                }
            }
            if (camera.getWidth() > 2000) {
                if (nozzle == n1 && camera.getLooking() == Camera.Looking.Up) {
                    //左半边
                    //Location test = VisionUtils.getPixelLocation(camera, -20.250438, 5.852280);
                    Location unitsPerPixel = camera.getUnitsPerPixel();

                    Location lefUpLocation = unitsPerPixel.multiply(0 - camera.getWidth() / 2, 0 + camera.getHeight() / 2, 0, 0);
                    Location rightUpLocation = unitsPerPixel.multiply(0, 0 + camera.getHeight() / 2, 0, 0);
                    Location leftDownLocation = unitsPerPixel.multiply(0 - camera.getWidth() / 2, -camera.getHeight() + camera.getHeight() / 2, 0, 0);
                    affineWarp.setX0(lefUpLocation.getX());
                    affineWarp.setY0(lefUpLocation.getY());
                    affineWarp.setX1(rightUpLocation.getX());
                    affineWarp.setY1(rightUpLocation.getY());
                    affineWarp.setX2(leftDownLocation.getX());
                    affineWarp.setY2(leftDownLocation.getY());
                } else {
                    //右半边
                    Location unitsPerPixel = camera.getUnitsPerPixelAtZ().convertToUnits(LengthUnit.Millimeters);
                    Location lefUpLocation = unitsPerPixel.multiply(camera.getWidth() / 2 - camera.getWidth() / 2, 0 + camera.getHeight() / 2, 0, 0);
                    Location rightUpLocation = unitsPerPixel.multiply(camera.getWidth() - camera.getWidth() / 2, 0 + camera.getHeight() / 2, 0, 0);
                    Location leftDownLocation = unitsPerPixel.multiply(camera.getWidth() / 2 - camera.getWidth() / 2, -camera.getHeight() + camera.getHeight() / 2, 0, 0);

                    Location n1Offset = n1.getHeadOffsets();
                    Location n2Offset = Configuration.get().getMachine().getHeads().get(0).getNozzles().get(1).getHeadOffsets();
                    double n2N1OffsetX = n2Offset.getX() - n1Offset.getX();
                    double n2N1OffsetY = n2Offset.getY() - n1Offset.getY();


                    Location leftCenteLocation = unitsPerPixel.convertToUnits(LengthUnit.Millimeters).multiply(camera.getWidth() / 4 - camera.getWidth() / 2, -camera.getHeight() / 2 + camera.getHeight(), 0, 0);
                    Location rightCenteLocation = unitsPerPixel.convertToUnits(LengthUnit.Millimeters).multiply(camera.getWidth() * 3 / 4 - camera.getWidth() / 2, -camera.getHeight() / 2 + camera.getHeight(), 0, 0);
                    double leftRightOffsetX = rightCenteLocation.getX() - leftCenteLocation.getX();
                    double leftRightOffsetY = rightCenteLocation.getY() - leftCenteLocation.getY();


                    double cameraNozzelOffsetX, cameraNozzelOffsetY;
                    leftRightOffsetX = 29.75;
/*
                    if (n2N1OffsetX > leftRightOffsetX) {
                        cameraNozzelOffsetX = (n2N1OffsetX - leftRightOffsetX);
                    } else {
                        cameraNozzelOffsetX = (leftRightOffsetX - n2N1OffsetX);
                    }
*/
                    cameraNozzelOffsetX = n2N1OffsetX - leftRightOffsetX;
                    cameraNozzelOffsetY = n2N1OffsetY * 2;


                    affineWarp.setX0(lefUpLocation.getX() + cameraNozzelOffsetX);
                    affineWarp.setY0(lefUpLocation.getY() + cameraNozzelOffsetY);
                    affineWarp.setX1(rightUpLocation.getX() + cameraNozzelOffsetX);
                    affineWarp.setY1(rightUpLocation.getY() + cameraNozzelOffsetY);
                    affineWarp.setX2(leftDownLocation.getX() + cameraNozzelOffsetX);
                    affineWarp.setY2(leftDownLocation.getY() + cameraNozzelOffsetY);
                }
                pipeline.insert(affineWarp, 3);
                pipeline.insert(affineWarp, pipeline.getStages().size() - 2);
            }
            // 处理管道，执行图像处理操作
            pipeline.process();

            // 获取管道处理的结果
            Result result = pipeline.getResult(VisionUtils.PIPELINE_RESULTS_NAME);

            // 如果找不到名为"results"的结果，则回退到旧名称"result"
            if (result == null) {
                result = pipeline.getResult("result");
            }

            // 如果结果仍然为null，则抛出异常
            if (result == null) {
                throw new Exception(String.format(
                        "ReferenceBottomVision (%s): Pipeline error. Pipeline must contain a result named '%s'.",
                        part.getId(), VisionUtils.PIPELINE_RESULTS_NAME));
            }

            // 如果结果模型为null，则抛出异常
            if (result.model == null) {
                throw new Exception(String.format(
                        "ReferenceBottomVision (%s): No result found.",
                        part.getId()));
            }

            // 检查结果模型是否为正确的类型（RotatedRect）
            if (!(result.model instanceof RotatedRect)) {
                throw new Exception(String.format(
                        "ReferenceBottomVision (%s): Incorrect pipeline result type (%s). Expected RotatedRect.",
                        part.getId(), result.model.getClass().getSimpleName()));
            }

            // 处理管道阶段的结果
            pipelineShot.processResult(result);

            // 显示管道阶段的处理结果
            displayResult(OpenCvUtils.toBufferedImage(pipeline.getWorkingImage()), part, null, camera, nozzle);
        }

        // 返回最后一个阶段的处理结果，即RotatedRect
        return (RotatedRect) pipeline.getCurrentPipelineShot().processCompositeResult().getModel();
    }


    @Override
    public boolean canHandle(PartSettingsHolder settingsHolder, boolean allowDisabled) {
        BottomVisionSettings visionSettings = getInheritedVisionSettings(settingsHolder);
        if (visionSettings != null) {
            boolean isEnabled = (enabled && visionSettings.isEnabled());
            if (!allowDisabled) {
                Logger.trace("{}.canHandle({}) => {}, {}", this.getClass().getSimpleName(),
                        settingsHolder == null ? "" : settingsHolder.getId(), visionSettings, isEnabled ? "enabled" : "disabled");
            }
            return allowDisabled || isEnabled;
        }
        return false;
    }

    private BottomVisionSettings createBottomVisionSettings(String id, String name, CvPipeline pipeline) {
        BottomVisionSettings bottomVisionSettings;
        try {
            bottomVisionSettings = new BottomVisionSettings(id);
            bottomVisionSettings.setName(name);
            bottomVisionSettings.setEnabled(true);
            bottomVisionSettings.setPipeline(pipeline);
            return bottomVisionSettings;
        } catch (Exception e) {
            throw new Error(e);
        }
    }

    @Override
    public String getId() {
        return null;
    }

    @Override
    public String getName() {
        return null;
    }

    @Override
    public void setName(String name) {
    }

    @Override
    public String getShortName() {
        return getPropertySheetHolderTitle();
    }

    @Override
    public void setBottomVisionSettings(BottomVisionSettings visionSettings) {
        if (visionSettings == null) {
            return; // do not allow null
        }
        super.setBottomVisionSettings(visionSettings);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isPreRotate() {
        return preRotate;
    }

    public void setPreRotate(boolean preRotate) {
        this.preRotate = preRotate;
    }

    public int getMaxVisionPasses() {
        return maxVisionPasses;
    }

    public void setMaxVisionPasses(int maxVisionPasses) {
        this.maxVisionPasses = maxVisionPasses;
    }

    public Length getMaxLinearOffset() {
        return maxLinearOffset;
    }

    public void setMaxLinearOffset(Length maxLinearOffset) {
        this.maxLinearOffset = maxLinearOffset;
    }

    public double getMaxAngularOffset() {
        return maxAngularOffset;
    }

    public void setMaxAngularOffset(double maxAngularOffset) {
        this.maxAngularOffset = maxAngularOffset;
    }

    public double getTestAlignmentAngle() {
        return testAlignmentAngle;
    }

    public void setTestAlignmentAngle(double testAlignmentAngle) {
        Object oldValue = this.testAlignmentAngle;
        this.testAlignmentAngle = testAlignmentAngle;
        firePropertyChange("testAlignmentAngle", oldValue, testAlignmentAngle);
    }

    @Override
    public String getPropertySheetHolderTitle() {
        return "Bottom Vision";
    }

    public static CvPipeline createStockPipeline(String variant) {
        try {
            String xml = IOUtils.toString(ReferenceBottomVision.class
                    .getResource("ReferenceBottomVision-" + variant + "Pipeline.xml"));
            return new CvPipeline(xml);
        } catch (Exception e) {
            throw new Error(e);
        }
    }

    @Override
    public PropertySheetHolder[] getChildPropertySheetHolders() {
        return null;
    }

    @Override
    public PropertySheet[] getPropertySheets() {
        return new PropertySheet[]{
                new PropertySheetWizardAdapter(new ReferenceBottomVisionConfigurationWizard(this)),
                new PropertySheetWizardAdapter(new BottomVisionSettingsConfigurationWizard(getBottomVisionSettings(), this))};
    }


    public enum PreRotateUsage {
        Default, AlwaysOn, AlwaysOff
    }

    public enum PartSizeCheckMethod {
        Disabled, BodySize, PadExtents
    }

    public enum MaxRotation {
        Adjust, Full
    }

    @Deprecated
    @Root
    public static class PartSettings extends AbstractModelObject {

        @Deprecated
        @Attribute
        protected boolean enabled = true;
        @Deprecated
        @Attribute(required = false)
        protected PreRotateUsage preRotateUsage = PreRotateUsage.Default;

        @Deprecated
        @Attribute(required = false)
        protected PartSizeCheckMethod checkPartSizeMethod = PartSizeCheckMethod.Disabled;

        @Deprecated
        @Attribute(required = false)
        protected int checkSizeTolerancePercent = 20;

        @Deprecated
        @Attribute(required = false)
        protected MaxRotation maxRotation = MaxRotation.Adjust;

        @Deprecated
        @Element(required = false)
        protected Location visionOffset = new Location(LengthUnit.Millimeters);

        @Deprecated
        @Element
        protected CvPipeline pipeline;

        @Deprecated
        public PartSettings() {
        }

        @Deprecated
        public boolean isEnabled() {
            return enabled;
        }

        @Deprecated
        public PreRotateUsage getPreRotateUsage() {
            return preRotateUsage;
        }

        @Deprecated
        public CvPipeline getPipeline() {
            return pipeline;
        }

        @Deprecated
        public void setPipeline(CvPipeline pipeline) {
            this.pipeline = pipeline;
        }

        @Deprecated
        public MaxRotation getMaxRotation() {
            return maxRotation;
        }

        public PartSizeCheckMethod getCheckPartSizeMethod() {
            return checkPartSizeMethod;
        }

        public int getCheckSizeTolerancePercent() {
            return checkSizeTolerancePercent;
        }

        public Location getVisionOffset() {
            return visionOffset;
        }
    }

    protected void migratePartSettings(Configuration configuration) {
        if (partSettingsByPartId == null) {
            AbstractVisionSettings stockVisionSettings = configuration.getVisionSettings(AbstractVisionSettings.STOCK_BOTTOM_ID);
            if (stockVisionSettings == null) {
                // Fresh configuration: need to migrate the stock and default settings, even if no partSettingsById are present.
                partSettingsByPartId = new HashMap<>();
            } else {
                // Reassign the stock pipeline.
                stockVisionSettings.setPipeline(createStockPipeline("Default"));
                // Create other stock pipelines.
                createStockVisionSettings(configuration, AbstractVisionSettings.STOCK_BOTTOM_RECTLINEAR_ID, "Rectlinear", "- Rectlinear Symmetry Bottom Vision Settings -");
                createStockVisionSettings(configuration, AbstractVisionSettings.STOCK_BOTTOM_BODY_ID, "Body", "- Whole Part Body Bottom Vision Settings -");
                return;
            }
        }

        HashMap<String, BottomVisionSettings> bottomVisionSettingsHashMap = new HashMap<>();
        // Create the factory stock settings.
        BottomVisionSettings stockBottomVisionSettings = createStockBottomVisionSettings();
        configuration.addVisionSettings(stockBottomVisionSettings);
        BottomVisionSettings rectlinearBottomVisionSettings = createRectlinearBottomVisionSettings();
        configuration.addVisionSettings(rectlinearBottomVisionSettings);
        PartSettings equivalentPartSettings = new PartSettings();
        equivalentPartSettings.setPipeline(stockBottomVisionSettings.getPipeline());
        bottomVisionSettingsHashMap.put(AbstractVisionSettings.createSettingsFingerprint(equivalentPartSettings), stockBottomVisionSettings);
        // Migrate the default settings.
        BottomVisionSettings defaultBottomVisionSettings = new BottomVisionSettings(AbstractVisionSettings.DEFAULT_BOTTOM_ID);
        defaultBottomVisionSettings.setName("- Default Machine Bottom Vision -");
        defaultBottomVisionSettings.setEnabled(enabled);
        configuration.addVisionSettings(defaultBottomVisionSettings);
        if (pipeline != null) {
            defaultBottomVisionSettings.setPipeline(pipeline);
            pipeline = null;
        } else {
            defaultBottomVisionSettings.setPipeline(stockBottomVisionSettings.getPipeline());
        }
        setBottomVisionSettings(defaultBottomVisionSettings);
        equivalentPartSettings.setPipeline(defaultBottomVisionSettings.getPipeline());
        bottomVisionSettingsHashMap.put(AbstractVisionSettings.createSettingsFingerprint(equivalentPartSettings), defaultBottomVisionSettings);
        for (Part part : configuration.getParts()) {
            part.setBottomVisionSettings(null);
        }
        for (org.openpnp.model.Package pkg : configuration.getPackages()) {
            pkg.setBottomVisionSettings(null);
        }
        partSettingsByPartId.forEach((partId, partSettings) -> {
            if (partSettings == null) {
                return;
            }

            try {
                Part part = configuration.getPart(partId);
                if (part != null) {
                    String serializedHash = AbstractVisionSettings.createSettingsFingerprint(partSettings);
                    BottomVisionSettings bottomVisionSettings = bottomVisionSettingsHashMap.get(serializedHash);
                    if (bottomVisionSettings == null) {
                        bottomVisionSettings = new BottomVisionSettings(partSettings);
                        bottomVisionSettings.setName("");
                        bottomVisionSettingsHashMap.put(serializedHash, bottomVisionSettings);

                        configuration.addVisionSettings(bottomVisionSettings);
                    }

                    part.setBottomVisionSettings((bottomVisionSettings != defaultBottomVisionSettings) ? bottomVisionSettings : null);
                    Logger.info("Part " + partId + " BottomVisionSettings migrated.");
                } else {
                    Logger.warn("Part " + partId + " BottomVisionSettings with no part.");
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        partSettingsByPartId = null;

        optimizeVisionSettings(configuration);
    }

    public AbstractVisionSettings createStockVisionSettings(Configuration configuration, String id, String tag, String description) {
        // Add the vision settings if missing.
        AbstractVisionSettings visionSettings = configuration.getVisionSettings(id);
        if (visionSettings == null) {
            visionSettings = createBottomVisionSettings(id, description, createStockPipeline(tag));
            configuration.addVisionSettings(visionSettings);
        } else {
            // Reassign the stock pipeline.
            visionSettings.setPipeline(createStockPipeline(tag));
        }
        return visionSettings;
    }

    protected BottomVisionSettings createRectlinearBottomVisionSettings() {
        return createBottomVisionSettings(AbstractVisionSettings.STOCK_BOTTOM_RECTLINEAR_ID,
                "- Rectlinear Symmetry Bottom Vision Settings -", createStockPipeline("Rectlinear"));
    }

    protected BottomVisionSettings createStockBottomVisionSettings() {
        return createBottomVisionSettings(AbstractVisionSettings.STOCK_BOTTOM_ID,
                "- Stock Bottom Vision Settings -", createStockPipeline("Default"));
    }

    public void optimizeVisionSettings(Configuration configuration) {
        // Remove any duplicate settings.
        HashMap<String, AbstractVisionSettings> bottomVisionSettingsHashMap = new HashMap<>();
        BottomVisionSettings defaultVisionSettings = getBottomVisionSettings();
        // Make it dominant in case it is identical to stock.
        bottomVisionSettingsHashMap.put(AbstractVisionSettings.createSettingsFingerprint(defaultVisionSettings), defaultVisionSettings);
        for (AbstractVisionSettings visionSettings : configuration.getVisionSettings()) {
            if (visionSettings instanceof BottomVisionSettings) {
                String serializedHash = AbstractVisionSettings.createSettingsFingerprint(visionSettings);
                AbstractVisionSettings firstVisionSettings = bottomVisionSettingsHashMap.get(serializedHash);
                if (firstVisionSettings == null) {
                    bottomVisionSettingsHashMap.put(serializedHash, visionSettings);
                } else if (visionSettings != defaultVisionSettings
                        && !visionSettings.isStockSetting()) {
                    // Duplicate, remove any references.
                    for (PartSettingsHolder holder : visionSettings.getUsedBottomVisionIn()) {
                        holder.setBottomVisionSettings((BottomVisionSettings) firstVisionSettings);
                    }
                    if (visionSettings.getUsedBottomVisionIn().size() == 0) {
                        if (firstVisionSettings != defaultVisionSettings
                                && !firstVisionSettings.isStockSetting()) {
                            firstVisionSettings.setName(firstVisionSettings.getName() + " + " + visionSettings.getName());
                        }
                        configuration.removeVisionSettings(visionSettings);
                    }
                }
            }
        }

        // Per package, search the most common settings on parts, and make them inherited package setting.
        for (org.openpnp.model.Package pkg : configuration.getPackages()) {
            HashMap<String, Integer> histogram = new HashMap<>();
            BottomVisionSettings mostFrequentVisionSettings = null;
            int highestFrequency = 0;
            BottomVisionSettings packageVisionSettings = AbstractPartAlignment.getInheritedVisionSettings(pkg, true);
            for (Part part : configuration.getParts()) {
                if (part.getPackage() == pkg) {
                    BottomVisionSettings visionSettings = AbstractPartAlignment.getInheritedVisionSettings(part, true);
                    String id = visionSettings != null ? visionSettings.getId() : "";
                    Integer frequency = histogram.get(id);
                    frequency = (frequency != null ? frequency + 1 : 1);
                    histogram.put(id, frequency);
                    if (highestFrequency < frequency) {
                        highestFrequency = frequency;
                        mostFrequentVisionSettings = visionSettings;
                    }
                }
            }
            if (mostFrequentVisionSettings != null) {
                if (mostFrequentVisionSettings == defaultVisionSettings) {
                    pkg.setBottomVisionSettings(null);
                } else {
                    pkg.setBottomVisionSettings(mostFrequentVisionSettings);
                }
                for (Part part : configuration.getParts()) {
                    if (part.getPackage() == pkg) {
                        if (part.getBottomVisionSettings() == mostFrequentVisionSettings) {
                            // Parts inherit from package now.
                            part.setBottomVisionSettings(null);
                        } else if (part.getBottomVisionSettings() == null
                                && packageVisionSettings != mostFrequentVisionSettings) {
                            // Former package settings were inherited, now we must freeze them.
                            part.setBottomVisionSettings(packageVisionSettings);
                        }
                    }
                }
                if (mostFrequentVisionSettings != defaultVisionSettings
                        && !mostFrequentVisionSettings.isStockSetting()
                        && !mostFrequentVisionSettings.getName().isEmpty()
                        && mostFrequentVisionSettings.getUsedBottomVisionIn().size() == 1) {
                    // If these part settings are now unique to the package, name them so.
                    mostFrequentVisionSettings.setName(pkg.getShortName());
                }
            }
        }

        // Set missing names by usage.
        AbstractVisionSettings.ListConverter listConverter = new AbstractVisionSettings.ListConverter(false);
        int various = 0;
        for (AbstractVisionSettings visionSettings : configuration.getVisionSettings()) {
            if (visionSettings instanceof BottomVisionSettings) {
                List<PartSettingsHolder> usedIn = visionSettings.getUsedBottomVisionIn();
                if (!visionSettings.isStockSetting()
                        && visionSettings != defaultVisionSettings
                        && usedIn.isEmpty()) {
                    configuration.removeVisionSettings(visionSettings);
                } else if (visionSettings.getName().isEmpty()) {
                    if (usedIn.size() <= 3) {
                        visionSettings.setName(listConverter.convertForward(usedIn));
                    } else {
                        various++;
                        visionSettings.setName("Migrated " + various);
                    }
                }
            }
        }
    }

    public static ReferenceBottomVision getDefault() {
        return (ReferenceBottomVision) Configuration.get().getMachine().getPartAlignments().get(0);
    }
}