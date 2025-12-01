package com.pocket.rpg.input;

/**
 * Configuration for a virtual axis (e.g., "Horizontal", "Vertical").
 * Supports primary and alternative key bindings with smooth interpolation.
 *
 * @param positiveKey Getters
 */
public record AxisConfig(KeyCode positiveKey, KeyCode negativeKey, KeyCode altPositiveKey, KeyCode altNegativeKey, float sensitivity,
                         float gravity, float deadZone, boolean snap) {
    

    /**
     * Creates an axis configuration with all parameters.
     *
     * @param positiveKey Key for positive direction
     * @param negativeKey Key for negative direction
     *                    <b>altPositiveKey</b> - No alternative key <br />
     *                    <b>altNegativeKey</b> - No alternative key <br />
     *                    <b>sensitivity</b>    - Default: 1.0 <br />
     *                    <b>gravity</b>        - Default: 3.0 <br />
     *                    <b>deadZone</b>       - Default: 0.001 <br />
     *                    <b>snap</b>           - Default: true
     */
    public AxisConfig(KeyCode positiveKey, KeyCode negativeKey) {
        this(positiveKey, negativeKey, null, null, 1.0f, 3.0f, 0.001f, true);
    }

    public AxisConfig {
    }

    // Builder pattern for fluent API
    public AxisConfig withAltKeys(KeyCode altPositive, KeyCode altNegative) {
        return new AxisConfig(positiveKey, negativeKey, altPositive, altNegative,
                sensitivity, gravity, deadZone, snap);
    }

    public AxisConfig withSensitivity(float sensitivity) {
        return new AxisConfig(positiveKey, negativeKey, altPositiveKey, altNegativeKey,
                sensitivity, gravity, deadZone, snap);
    }

    public AxisConfig withGravity(float gravity) {
        return new AxisConfig(positiveKey, negativeKey, altPositiveKey, altNegativeKey,
                sensitivity, gravity, deadZone, snap);
    }

    public AxisConfig withDeadZone(float deadZone) {
        return new AxisConfig(positiveKey, negativeKey, altPositiveKey, altNegativeKey,
                sensitivity, gravity, deadZone, snap);
    }

    public AxisConfig withSnap(boolean snap) {
        return new AxisConfig(positiveKey, negativeKey, altPositiveKey, altNegativeKey,
                sensitivity, gravity, deadZone, snap);
    }

}