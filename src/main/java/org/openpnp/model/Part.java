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

package org.openpnp.model;

import org.openpnp.ConfigurationListener;
import org.openpnp.machine.reference.vision.AbstractPartSettingsHolder;
import org.openpnp.spi.Feeder;
import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.core.Persist;

/**
 * A Part is a single part that can be picked and placed. It has a graphical outline, is retrieved
 * from one or more Feeders and is placed at a Placement as part of a Job. Parts can be used across
 * many boards and should generally represent a single part in the real world.
 */
public class Part extends AbstractPartSettingsHolder {
    @Attribute
    private String id;
    @Attribute(required = false)
    private String name;

    @Attribute
    private LengthUnit heightUnits = LengthUnit.Millimeters;
    @Attribute
    private double height;

    private Package packag;

    @Attribute(required = false)
    private String packageId;

    @Attribute(required = false)
    private double speed = 1.0;
    
    @Attribute(required = false)
    private int pickRetryCount = 0;



    @SuppressWarnings("unused")
    private Part() {
        this(null);
    }

    public Part(String id) {
        this.id = id;
        Configuration.get().addListener(new ConfigurationListener.Adapter() {
            @Override
            public void configurationLoaded(Configuration configuration) {
                packag = configuration.getPackage(packageId);
            }
        });
    }

    @Persist
    private void persist() {
        packageId = (packag == null ? null : packag.getId());
    }

    @Override
    public String getId() {
        return id;
    }

    /**
     * Warning: This should never be called once the Part is added to the configuration. It
     * should only be used when creating a new part.
     * 
     * @param id
     */
    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        Object oldValue = this.name;
        this.name = name;
        firePropertyChange("name", oldValue, name);
    }

    public double getSpeed() {
        return speed;
    }

    public void setSpeed(double speed) {
        Object oldValue = this.speed;
        this.speed = speed;
        firePropertyChange("speed", oldValue, speed);
    }

    public Length getHeight() {
        return new Length(height, heightUnits);
    }

    public void setHeight(Length height) {
        Object oldValue = getHeight();
        if (height == null) {
            this.height = 0;
            this.heightUnits = null;
        }
        else {
            this.height = height.getValue();
            this.heightUnits = height.getUnits();
        }
        firePropertyChange("height", oldValue, getHeight());
    }

    public Package getPackage() {
        return packag;
    }

    public void setPackage(Package packag) {
        Object oldValue = this.packag;
        this.packag = packag;
        firePropertyChange("package", oldValue, packag);
    }
    
    public int getPickRetryCount() {
        return pickRetryCount;
    }

    public void setPickRetryCount(int pickRetryCount) {
        this.pickRetryCount = pickRetryCount;
        firePropertyChange("pickRetryCount", null, pickRetryCount);
    }

    @Override
    public String toString() {
        return String.format("id %s, name %s, heightUnits %s, height %f, packageId (%s)", id, name,
                heightUnits, height, packageId);
    }

    public boolean isPartHeightUnknown() {
        return getHeight().getValue() <= 0.0;
    }

    public int getPlacementCount() {
        int n = 0;
        for (Board board : Configuration.get().getBoards()) {
            for (Placement placement : board.getPlacements()) {
                if (placement.getPart() == this) {
                    n++;
                }
            }
        }
        return n;
    }

    public void setPlacementCount(int placementCount) {
        // Pseudo-setter just used to fire the property change (no matter what is passed).
        firePropertyChange("placementCount", null, getPlacementCount());
    }

    public int getAssignedFeeders() {
        int n = 0;
        for (Feeder feeder : Configuration.get().getMachine().getFeeders()) {
            if (feeder.getPart() == this) {
                n++;
            }
        }
        return n;
    }

    public void setAssignedFeeders(int assignedFeeders) {
        // Pseudo-setter just used to fire the property change (no matter what is passed).
        firePropertyChange("assignedFeeders", null, getAssignedFeeders());
    }
}
