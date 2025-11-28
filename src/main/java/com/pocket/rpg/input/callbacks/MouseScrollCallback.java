package com.pocket.rpg.input.callbacks;

public interface MouseScrollCallback {
    /**
     * Will be called when a scrolling device is used, such as a mouse wheel or scrolling area of a touchpad.
     *
     * @param xOffset the scroll offset along the x-axis
     * @param yOffset the scroll offset along the y-axis
     */
    void invoke(double xOffset, double yOffset);
}
