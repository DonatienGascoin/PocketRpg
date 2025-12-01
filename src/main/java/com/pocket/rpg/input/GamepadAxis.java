package com.pocket.rpg.input;

/**
 * Platform-independent gamepad axis identifiers.
 * Analog inputs that provide continuous values.
 */
public enum GamepadAxis {
    /**
     * Left stick horizontal axis.
     * Value: -1.0 (left) to 1.0 (right)
     */
    LEFT_STICK_X,

    /**
     * Left stick vertical axis.
     * Value: -1.0 (down) to 1.0 (up)
     */
    LEFT_STICK_Y,

    /**
     * Right stick horizontal axis.
     * Value: -1.0 (left) to 1.0 (right)
     */
    RIGHT_STICK_X,

    /**
     * Right stick vertical axis.
     * Value: -1.0 (down) to 1.0 (up)
     */
    RIGHT_STICK_Y,

    /**
     * Left trigger analog axis.
     * Value: 0.0 (released) to 1.0 (fully pressed)
     */
    LEFT_TRIGGER,

    /**
     * Right trigger analog axis.
     * Value: 0.0 (released) to 1.0 (fully pressed)
     */
    RIGHT_TRIGGER
}