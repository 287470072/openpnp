package org.openpnp.machine.reference.vision;

import org.openpnp.spi.PnpJobPlanner;
import org.pmw.tinylog.Logger;

import java.util.List;

public class ReferenceBottomMultiVision extends AbstractPartAlignmentMulti{



    @Override
    public String getId() {
        return null;
    }


    @Override
    public void findOffsetMulti(List<PnpJobPlanner.PlannedPlacement> pps) throws Exception {
        Logger.trace("ReferenceBottomMultiVision输出！");
    }
}
