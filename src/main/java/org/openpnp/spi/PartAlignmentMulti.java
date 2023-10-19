package org.openpnp.spi;

import org.openpnp.model.*;

import java.util.List;

public interface PartAlignmentMulti extends PartSettingsHolder, Named, Solutions.Subject, PropertySheetHolder{


    List<PnpJobPlanner.PlannedPlacement> findOffsetsMulti(List<PnpJobPlanner.PlannedPlacement> pps) throws Exception;

}
