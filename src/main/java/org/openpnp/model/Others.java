package org.openpnp.model;

import org.simpleframework.xml.Attribute;

public class Others {
    @Attribute
    private double positionm1XValue;

    @Attribute
    private double positionm1YValue;

    @Attribute
    private double positionm1ZValue;

    public double getPositionm1XValue() {
        return positionm1XValue;
    }

    public void setPositionm1XValue(double positionm1XValue) {
        this.positionm1XValue = positionm1XValue;
    }

    public double getPositionm1YValue() {
        return positionm1YValue;
    }

    public void setPositionm1YValue(double positionm1YValue) {
        this.positionm1YValue = positionm1YValue;
    }

    public double getPositionm1ZValue() {
        return positionm1ZValue;
    }

    public void setPositionm1ZValue(double positionm1ZValue) {
        this.positionm1ZValue = positionm1ZValue;
    }
}
