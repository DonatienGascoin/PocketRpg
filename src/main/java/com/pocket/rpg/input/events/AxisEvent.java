package com.pocket.rpg.input.events;

import lombok.Getter;

/**
 * Event fired when a virtual axis value changes significantly.
 * This allows listeners to react to axis changes without polling.
 * TODO: Needed ?
 */
@Getter
public class AxisEvent extends InputEvent {
    private final String axisName;
    private final float value;
    private final float previousValue;

    public AxisEvent(String axisName, float value, float previousValue) {
        super();
        this.axisName = axisName;
        this.value = value;
        this.previousValue = previousValue;
    }

    public float getDelta() { return value - previousValue; }

    /**
     * Check if the axis just became active (crossed threshold).
     */
    public boolean justActivated(float threshold) {
        return Math.abs(previousValue) < threshold && Math.abs(value) >= threshold;
    }

    /**
     * Check if the axis just became inactive.
     */
    public boolean justDeactivated(float threshold) {
        return Math.abs(previousValue) >= threshold && Math.abs(value) < threshold;
    }

    @Override
    public String toString() {
        return String.format("AxisEvent{axis='%s', value=%.2f, prev=%.2f}",
                axisName, value, previousValue);
    }
}