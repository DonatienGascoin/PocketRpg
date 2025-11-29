package com.pocket.rpg.inputNew;

/**
 * Configuration for a virtual axis (e.g., "Horizontal", "Vertical").
 * Supports primary and alternative key bindings with smooth interpolation.
 *
 * @param positiveKey Getters
 */
public record AxisConfig(KeyCode positiveKey, KeyCode negativeKey, KeyCode altPositiveKey, KeyCode altNegativeKey, float sensitivity,
                         float gravity, float deadZone, boolean snap) {
    /**
     * Creates an axis configuration with primary keys only.
     *
     * @param positiveKey Key for positive direction
     * @param negativeKey Key for negative direction
     */
    public AxisConfig(KeyCode positiveKey, KeyCode negativeKey) {
        this(positiveKey, negativeKey, null, null, 1.0f, 3.0f, 0.001f, true);
    }

    /**
     * Creates an axis configuration with all parameters.
     *
     * @param positiveKey    Key for positive direction
     * @param negativeKey    Key for negative direction
     * @param altPositiveKey Alternative key for positive direction
     * @param altNegativeKey Alternative key for negative direction
     * @param sensitivity    Speed of value increase (default: 1.0)
     * @param gravity        Speed of value decrease (default: 3.0)
     * @param deadZone       Minimum threshold (default: 0.001)
     * @param snap           Instant direction reversal (default: true)
     */
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