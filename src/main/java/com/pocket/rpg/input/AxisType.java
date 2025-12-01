package com.pocket.rpg.input;

/**
 * Defines the type of input source for a virtual axis.
 * Different types have different update behaviors.
 */
public enum AxisType {
    /**
     * Keyboard-based axis with smooth interpolation.
     * Gradually accelerates to target value based on sensitivity.
     * Gradually decelerates to zero based on gravity.
     */
    KEYBOARD,

    /**
     * Mouse delta-based axis (instant, no interpolation).
     * Value is set directly from mouse movement each frame.
     */
    MOUSE_DELTA,

    /**
     * Mouse scroll wheel axis (instant, no interpolation).
     * Value is set directly from scroll wheel delta each frame.
     */
    MOUSE_WHEEL,

    /**
     * Gamepad analog axis (instant, with dead zone).
     * Value is read directly from gamepad stick or trigger.
     */
    GAMEPAD,

    /**
     * Composite axis that combines multiple input sources.
     * Takes the input with the largest absolute value.
     * Useful for axes that respond to both keyboard and gamepad.
     */
    COMPOSITE
}