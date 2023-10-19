package org.openpnp.machine.reference.vision;

import org.openpnp.spi.PnpJobPlanner;

import java.util.List;

public abstract class AbstractPartAlignmentMulti extends AbstractPartSettingsHolder {

    public abstract void findOffsetMulti( List<PnpJobPlanner.PlannedPlacement> pps) throws Exception;
}
