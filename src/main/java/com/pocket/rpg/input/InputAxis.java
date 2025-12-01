package com.pocket.rpg.input;

/**
 * Strongly-typed identifiers for virtual input axes.
 * Axes provide continuous values from -1.0 to 1.0.
 */
public enum InputAxis {
    /**
     * Horizontal movement axis.
     * Responds to: A/D keys, arrow keys, left stick, D-pad
     */
    HORIZONTAL,

    /**
     * Vertical movement axis.
     * Responds to: W/S keys, arrow keys, left stick, D-pad
     */
    VERTICAL,

    /**
     * Camera horizontal (look left/right).
     * Responds to: Mouse X, right stick X
     */
    MOUSE_X,

    /**
     * Camera vertical (look up/down).
     * Responds to: Mouse Y, right stick Y
     */
    MOUSE_Y,

    /**
     * Mouse scroll wheel delta.
     */
    MOUSE_WHEEL,

    /**
     * Right stick horizontal (gamepad only).
     * For games that need separate right stick control.
     */
    RIGHT_STICK_X,

    /**
     * Right stick vertical (gamepad only).
     */
    RIGHT_STICK_Y;

    /**
     * Provides default configuration for this axis.
     * These are fallback defaults used when no config file exists.
     *
     * @return Default axis configuration
     */
    public AxisConfig getDefaultConfig() {
        return switch (this) {
            case HORIZONTAL -> AxisConfig.composite(
                    // Keyboard input
                    new AxisConfig(KeyCode.D, KeyCode.A)
                            .withAltKeys(KeyCode.RIGHT, KeyCode.LEFT),
                    // Gamepad left stick
                    AxisConfig.gamepad(GamepadAxis.LEFT_STICK_X),
                    // Gamepad D-pad
                    AxisConfig.gamepadButtons(GamepadButton.DPAD_RIGHT, GamepadButton.DPAD_LEFT)
            );

            case VERTICAL -> AxisConfig.composite(
                    // Keyboard input
                    new AxisConfig(KeyCode.W, KeyCode.S)
                            .withAltKeys(KeyCode.UP, KeyCode.DOWN),
                    // Gamepad left stick
                    AxisConfig.gamepad(GamepadAxis.LEFT_STICK_Y),
                    // Gamepad D-pad
                    AxisConfig.gamepadButtons(GamepadButton.DPAD_UP, GamepadButton.DPAD_DOWN)
            );

            case MOUSE_X -> AxisConfig.composite(
                    AxisConfig.mouseDelta(1.0f),
                    AxisConfig.gamepad(GamepadAxis.RIGHT_STICK_X)
                            .withSensitivity(3.0f) // Gamepad needs higher sensitivity for camera
            );

            case MOUSE_Y -> AxisConfig.composite(
                    AxisConfig.mouseDelta(1.0f),
                    AxisConfig.gamepad(GamepadAxis.RIGHT_STICK_Y)
                            .withSensitivity(3.0f)
            );

            case MOUSE_WHEEL -> AxisConfig.mouseWheel(1.0f);

            case RIGHT_STICK_X -> AxisConfig.gamepad(GamepadAxis.RIGHT_STICK_X);

            case RIGHT_STICK_Y -> AxisConfig.gamepad(GamepadAxis.RIGHT_STICK_Y);
        };
    }
}