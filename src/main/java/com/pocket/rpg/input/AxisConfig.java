package com.pocket.rpg.input;

/**
 * Configuration for a virtual axis (e.g., "Horizontal", "Vertical").
 * Supports different axis types with appropriate parameters.
 */
public record AxisConfig(
        AxisType type,
        KeyCode positiveKey,
        KeyCode negativeKey,
        KeyCode altPositiveKey,
        KeyCode altNegativeKey,
        GamepadAxis gamepadAxis,      // NEW: For GAMEPAD type
        GamepadButton positiveButton, // NEW: For gamepad button axes
        GamepadButton negativeButton, // NEW: For gamepad button axes
        AxisConfig[] sources,         // NEW: For COMPOSITE type
        float sensitivity,
        float gravity,
        float deadZone,
        boolean snap
) {

    /**
     * Creates a keyboard axis configuration with primary keys only.
     * Uses default values: sensitivity=1.0, gravity=3.0, deadZone=0.001, snap=true
     *
     * @param positiveKey Key for positive direction (e.g., D, RIGHT)
     * @param negativeKey Key for negative direction (e.g., A, LEFT)
     */
    public AxisConfig(KeyCode positiveKey, KeyCode negativeKey) {
        this(AxisType.KEYBOARD, positiveKey, negativeKey, null, null,
                null, null, null, null,
                1.0f, 3.0f, 0.001f, true);
    }

    /**
     * Creates a gamepad analog axis configuration.
     *
     * @param gamepadAxis   The gamepad axis to read from
     * @param sensitivity   Scale factor (default: 1.0)
     * @param deadZone      Dead zone threshold (default: 0.15)
     * @return Configured gamepad axis
     */
    public static AxisConfig gamepad(GamepadAxis gamepadAxis, float sensitivity, float deadZone) {
        return new AxisConfig(AxisType.GAMEPAD, null, null, null, null,
                gamepadAxis, null, null, null,
                sensitivity, 0f, deadZone, false);
    }

    /**
     * Creates a gamepad analog axis with default settings.
     *
     * @param gamepadAxis The gamepad axis to read from
     * @return Configured gamepad axis
     */
    public static AxisConfig gamepad(GamepadAxis gamepadAxis) {
        return gamepad(gamepadAxis, 1.0f, 0.15f);
    }

    /**
     * Creates a gamepad button-based axis (like D-pad).
     *
     * @param positiveButton Button for positive direction
     * @param negativeButton Button for negative direction
     * @return Configured gamepad button axis
     */
    public static AxisConfig gamepadButtons(GamepadButton positiveButton, GamepadButton negativeButton) {
        return new AxisConfig(AxisType.GAMEPAD, null, null, null, null,
                null, positiveButton, negativeButton, null,
                1.0f, 3.0f, 0.001f, true);
    }

    /**
     * Creates a mouse delta axis configuration.
     * Mouse axes ignore gravity, deadZone, and snap.
     *
     * @param sensitivity Scale factor for mouse movement (1.0 = 1:1 mapping)
     * @return Configured mouse delta axis
     */
    public static AxisConfig mouseDelta(float sensitivity) {
        return new AxisConfig(AxisType.MOUSE_DELTA, null, null, null, null,
                null, null, null, null,
                sensitivity, 0f, 0f, false);
    }

    /**
     * Creates a mouse wheel axis configuration.
     *
     * @param sensitivity Scale factor for scroll wheel (1.0 = 1:1 mapping)
     * @return Configured mouse wheel axis
     */
    public static AxisConfig mouseWheel(float sensitivity) {
        return new AxisConfig(AxisType.MOUSE_WHEEL, null, null, null, null,
                null, null, null, null,
                sensitivity, 0f, 0f, false);
    }

    /**
     * Creates a composite axis that combines multiple sources.
     * The axis with the largest absolute value is used.
     *
     * @param sources The input sources to combine (keyboard, gamepad, etc.)
     * @return Configured composite axis
     */
    public static AxisConfig composite(AxisConfig... sources) {
        if (sources == null || sources.length == 0) {
            throw new IllegalArgumentException("Composite axis must have at least one source");
        }
        return new AxisConfig(AxisType.COMPOSITE, null, null, null, null,
                null, null, null, sources,
                1.0f, 0f, 0f, false);
    }

    /**
     * Canonical constructor with validation.
     */
    public AxisConfig {
        if (type == null) {
            throw new IllegalArgumentException("Axis type cannot be null");
        }

        switch (type) {
            case KEYBOARD -> {
                if (positiveKey == null && negativeKey == null &&
                        altPositiveKey == null && altNegativeKey == null) {
                    throw new IllegalArgumentException(
                            "Keyboard axis must have at least one key binding");
                }
                if (sensitivity <= 0) {
                    throw new IllegalArgumentException("Sensitivity must be positive");
                }
                if (gravity < 0) {
                    throw new IllegalArgumentException("Gravity cannot be negative");
                }
            }
            case GAMEPAD -> {
                if (gamepadAxis == null && positiveButton == null && negativeButton == null) {
                    throw new IllegalArgumentException(
                            "Gamepad axis must specify either an analog axis or button bindings");
                }
            }
            case COMPOSITE -> {
                if (sources == null || sources.length == 0) {
                    throw new IllegalArgumentException(
                            "Composite axis must have at least one source");
                }
            }
        }
    }

    // Builder methods updated to preserve all fields
    public AxisConfig withAltKeys(KeyCode altPositive, KeyCode altNegative) {
        return new AxisConfig(type, positiveKey, negativeKey, altPositive, altNegative,
                gamepadAxis, positiveButton, negativeButton, sources,
                sensitivity, gravity, deadZone, snap);
    }

    public AxisConfig withSensitivity(float sensitivity) {
        return new AxisConfig(type, positiveKey, negativeKey, altPositiveKey, altNegativeKey,
                gamepadAxis, positiveButton, negativeButton, sources,
                sensitivity, gravity, deadZone, snap);
    }

    public AxisConfig withGravity(float gravity) {
        return new AxisConfig(type, positiveKey, negativeKey, altPositiveKey, altNegativeKey,
                gamepadAxis, positiveButton, negativeButton, sources,
                sensitivity, gravity, deadZone, snap);
    }

    public AxisConfig withDeadZone(float deadZone) {
        return new AxisConfig(type, positiveKey, negativeKey, altPositiveKey, altNegativeKey,
                gamepadAxis, positiveButton, negativeButton, sources,
                sensitivity, gravity, deadZone, snap);
    }

    public AxisConfig withSnap(boolean snap) {
        return new AxisConfig(type, positiveKey, negativeKey, altPositiveKey, altNegativeKey,
                gamepadAxis, positiveButton, negativeButton, sources,
                sensitivity, gravity, deadZone, snap);
    }
}