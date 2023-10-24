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

import java.util.Locale;

import org.simpleframework.xml.Attribute;

/**
 * A Location is a an immutable 3D point in X, Y, Z space with a rotation component. The rotation is
 * applied about the Z axis.
 */
public class Location2 {
    /*
     * The fields on this class would be final in a perfect world, but that doesn't work correctly
     * with the XML serialization.
     */

    @Attribute
    private LengthUnit units;

    public Location2(LengthUnit units, double x, double y, double z, double rotation, double rotation2) {
        this.units = units;
        this.x = x;
        this.y = y;
        this.z = z;
        this.rotation = rotation;
        this.rotation2 = rotation2;
    }

    public void setX(double x) {
        this.x = x;
    }

    public void setY(double y) {
        this.y = y;
    }

    public void setZ(double z) {
        this.z = z;
    }

    @Attribute(required = false)
    private double x;
    @Attribute(required = false)
    private double y;
    @Attribute(required = false)
    private double z;
    @Attribute(required = false)
    private double rotation;
    @Attribute(required = false)
    private double rotation2;

    public LengthUnit getUnits() {
        return units;
    }

    public void setUnits(LengthUnit units) {
        this.units = units;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public double getRotation() {
        return rotation;
    }

    public void setRotation(double rotation) {
        this.rotation = rotation;
    }

    public double getRotation2() {
        return rotation2;
    }

    public void setRotation2(double rotation2) {
        this.rotation2 = rotation2;
    }
}
