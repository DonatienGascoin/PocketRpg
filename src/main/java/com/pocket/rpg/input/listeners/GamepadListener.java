package com.pocket.rpg.input.listeners;

import com.pocket.rpg.input.GamepadAxis;
import com.pocket.rpg.input.GamepadButton;
import lombok.Getter;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Platform-agnostic gamepad listener.
 * Tracks gamepad button states and analog axis values.
 * <p>
 * Supports multiple gamepads but queries return state of the first connected gamepad
 * (or combined state of all gamepads for simplicity).
 * <p>
 * Single threaded usage assumed.
 */
public class GamepadListener {

    // Button states
    private final Set<GamepadButton> buttonsHeld = EnumSet.noneOf(GamepadButton.class);
    private final Set<GamepadButton> buttonsPressed = EnumSet.noneOf(GamepadButton.class);
    private final Set<GamepadButton> buttonsReleased = EnumSet.noneOf(GamepadButton.class);

    // Analog axis values
    private final Map<GamepadAxis, Float> axisValues = new EnumMap<>(GamepadAxis.class);
    private final Map<GamepadAxis, Float> previousAxisValues = new EnumMap<>(GamepadAxis.class);

    // Dead zone for analog sticks (values below this are treated as zero)
    private static final float DEFAULT_DEAD_ZONE = 0.15f;
    /**
     * -- GETTER --
     *  Gets the current dead zone setting.
     *
     * @return the dead zone value
     */
    @Getter
    private float deadZone = DEFAULT_DEAD_ZONE;

    public GamepadListener() {
        // Initialize all axes to zero
        for (GamepadAxis axis : GamepadAxis.values()) {
            axisValues.put(axis, 0f);
            previousAxisValues.put(axis, 0f);
        }
    }

    // ========================================
    // Button Input (called by backend)
    // ========================================

    /**
     * Called when a gamepad button is pressed.
     *
     * @param button the button that was pressed
     */
    public void onButtonPressed(GamepadButton button) {
        if (button == null || button == GamepadButton.UNKNOWN) {
            return;
        }

        // Only mark as "pressed" if it wasn't already held
        if (!buttonsHeld.contains(button)) {
            buttonsPressed.add(button);
        }

        buttonsHeld.add(button);
    }

    /**
     * Called when a gamepad button is released.
     *
     * @param button the button that was released
     */
    public void onButtonReleased(GamepadButton button) {
        if (button == null || button == GamepadButton.UNKNOWN) {
            return;
        }

        buttonsReleased.add(button);
        buttonsHeld.remove(button);
        buttonsPressed.remove(button); // In case pressed and released same frame
    }

    // ========================================
    // Analog Axis Input (called by backend)
    // ========================================

    /**
     * Called when a gamepad analog axis value changes.
     *
     * @param axis  the axis that changed
     * @param value the new value (-1.0 to 1.0 for sticks, 0.0 to 1.0 for triggers)
     */
    public void onAxisChanged(GamepadAxis axis, float value) {
        if (axis == null) {
            return;
        }

        // Apply dead zone
        if (Math.abs(value) < deadZone) {
            value = 0f;
        }

        previousAxisValues.put(axis, axisValues.get(axis));
        axisValues.put(axis, value);
    }

    /**
     * Clears frame-specific state.
     * Must be called at the end of each frame.
     */
    public void endFrame() {
        buttonsPressed.clear();
        buttonsReleased.clear();

        // Update previous axis values
        for (GamepadAxis axis : GamepadAxis.values()) {
            previousAxisValues.put(axis, axisValues.get(axis));
        }
    }

    // ========================================
    // Button State Queries
    // ========================================

    /**
     * Checks if a button is currently being held down.
     *
     * @param button the button to check
     * @return true if the button is held down
     */
    public boolean isButtonHeld(GamepadButton button) {
        if (button == null || button == GamepadButton.UNKNOWN) {
            return false;
        }
        return buttonsHeld.contains(button);
    }

    /**
     * Checks if a button was pressed this frame (edge detection).
     *
     * @param button the button to check
     * @return true if the button was pressed this frame
     */
    public boolean wasButtonPressed(GamepadButton button) {
        if (button == null || button == GamepadButton.UNKNOWN) {
            return false;
        }
        return buttonsPressed.contains(button);
    }

    /**
     * Checks if a button was released this frame (edge detection).
     *
     * @param button the button to check
     * @return true if the button was released this frame
     */
    public boolean wasButtonReleased(GamepadButton button) {
        if (button == null || button == GamepadButton.UNKNOWN) {
            return false;
        }
        return buttonsReleased.contains(button);
    }

    /**
     * Checks if any button is currently held.
     *
     * @return true if at least one button is held
     */
    public boolean isAnyButtonHeld() {
        return !buttonsHeld.isEmpty();
    }

    /**
     * Checks if any button was pressed this frame.
     *
     * @return true if at least one button was pressed this frame
     */
    public boolean wasAnyButtonPressed() {
        return !buttonsPressed.isEmpty();
    }

    // ========================================
    // Analog Axis Queries
    // ========================================

    /**
     * Gets the current value of a gamepad axis.
     *
     * @param axis the axis to query
     * @return the axis value (-1.0 to 1.0 for sticks, 0.0 to 1.0 for triggers)
     */
    public float getAxis(GamepadAxis axis) {
        return axisValues.getOrDefault(axis, 0f);
    }

    /**
     * Gets the previous frame's value of a gamepad axis.
     * Useful for detecting changes.
     *
     * @param axis the axis to query
     * @return the previous axis value
     */
    public float getPreviousAxis(GamepadAxis axis) {
        return previousAxisValues.getOrDefault(axis, 0f);
    }

    /**
     * Checks if an axis has changed since last frame.
     *
     * @param axis      the axis to check
     * @param threshold minimum change to be considered "changed"
     * @return true if axis changed by more than threshold
     */
    public boolean hasAxisChanged(GamepadAxis axis, float threshold) {
        float current = getAxis(axis);
        float previous = getPreviousAxis(axis);
        return Math.abs(current - previous) > threshold;
    }

    // ========================================
    // Configuration
    // ========================================

    /**
     * Sets the dead zone for analog sticks.
     * Values below this threshold are treated as zero.
     *
     * @param deadZone the dead zone (typically 0.1 to 0.2)
     */
    public void setDeadZone(float deadZone) {
        this.deadZone = Math.max(0f, Math.min(1f, deadZone));
    }

    /**
     * Clears all gamepad states.
     * Useful for scene transitions or when focus is lost.
     */
    public void clear() {
        buttonsHeld.clear();
        buttonsPressed.clear();
        buttonsReleased.clear();

        for (GamepadAxis axis : GamepadAxis.values()) {
            axisValues.put(axis, 0f);
            previousAxisValues.put(axis, 0f);
        }
    }

    // ========================================
    // Convenience Methods
    // ========================================

    /**
     * Gets left stick as a 2D vector.
     *
     * @return [x, y] where x is horizontal (-1 left, +1 right) and y is vertical
     */
    public float[] getLeftStick() {
        return new float[]{
                getAxis(GamepadAxis.LEFT_STICK_X),
                getAxis(GamepadAxis.LEFT_STICK_Y)
        };
    }

    /**
     * Gets right stick as a 2D vector.
     *
     * @return [x, y] where x is horizontal and y is vertical
     */
    public float[] getRightStick() {
        return new float[]{
                getAxis(GamepadAxis.RIGHT_STICK_X),
                getAxis(GamepadAxis.RIGHT_STICK_Y)
        };
    }

    /**
     * Checks if left stick is moved beyond dead zone.
     *
     * @return true if stick is active
     */
    public boolean isLeftStickActive() {
        return Math.abs(getAxis(GamepadAxis.LEFT_STICK_X)) > deadZone ||
                Math.abs(getAxis(GamepadAxis.LEFT_STICK_Y)) > deadZone;
    }

    /**
     * Checks if right stick is moved beyond dead zone.
     *
     * @return true if stick is active
     */
    public boolean isRightStickActive() {
        return Math.abs(getAxis(GamepadAxis.RIGHT_STICK_X)) > deadZone ||
                Math.abs(getAxis(GamepadAxis.RIGHT_STICK_Y)) > deadZone;
    }
}