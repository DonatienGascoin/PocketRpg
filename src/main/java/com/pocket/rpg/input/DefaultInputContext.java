package com.pocket.rpg.input;

import com.pocket.rpg.config.InputConfig;
import com.pocket.rpg.input.listeners.GamepadListener;
import com.pocket.rpg.input.listeners.KeyListener;
import com.pocket.rpg.input.listeners.MouseListener;
import org.joml.Vector2f;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Default implementation of InputContext that manages input state and axis values.
 *
 * <p>This class coordinates between multiple input sources (keyboard, mouse, gamepad)
 * and provides a unified interface for querying input state. It handles:
 * <ul>
 *   <li>Keyboard axis interpolation with configurable sensitivity and gravity</li>
 *   <li>Gamepad analog axis reading with dead zone support</li>
 *   <li>Composite axes that combine multiple input sources</li>
 *   <li>Mouse delta tracking for camera control</li>
 *   <li>Action system with multiple key bindings</li>
 * </ul>
 *
 * <p>Thread Safety: This class is designed for single-threaded use on the main game loop.
 *
 * @see InputContext
 * @see AxisConfig
 * @see InputAxis
 */
public class DefaultInputContext implements InputContext {

    private final InputConfig config;
    private final KeyListener keyListener;
    private final MouseListener mouseListener;
    private final GamepadListener gamepadListener;

    // Axis state storage
    private final Map<InputAxis, Float> axisValues;
    private final Map<String, Float> customAxisValues;

    // State for composite axis sources (maintains interpolation state per source config)
    private final Map<InputAxis, Map<AxisConfig, Float>> compositeSourceStates;

    /**
     * Creates a new DefaultInputContext with the specified configuration and listeners.
     *
     * @param config the input configuration
     * @param keyListener the keyboard listener
     * @param mouseListener the mouse listener
     * @param gamepadListener the gamepad listener
     * @throws IllegalArgumentException if any parameter is null
     */
    public DefaultInputContext(InputConfig config, KeyListener keyListener,
                               MouseListener mouseListener, GamepadListener gamepadListener) {
        if (config == null) throw new IllegalArgumentException("config cannot be null");
        if (keyListener == null) throw new IllegalArgumentException("keyListener cannot be null");
        if (mouseListener == null) throw new IllegalArgumentException("mouseListener cannot be null");
        if (gamepadListener == null) throw new IllegalArgumentException("gamepadListener cannot be null");

        this.config = config;
        this.keyListener = keyListener;
        this.mouseListener = mouseListener;
        this.gamepadListener = gamepadListener;

        this.axisValues = new HashMap<>();
        this.customAxisValues = new HashMap<>();
        this.compositeSourceStates = new HashMap<>();

        // Initialize all axis values to zero
        for (InputAxis axis : InputAxis.values()) {
            axisValues.put(axis, 0f);

            // Initialize composite source states
            AxisConfig axisConfig = config.getAxisConfig(axis);
            if (axisConfig != null && axisConfig.type() == AxisType.COMPOSITE && axisConfig.sources() != null) {
                Map<AxisConfig, Float> sourceStates = new HashMap<>();
                for (AxisConfig source : axisConfig.sources()) {
                    sourceStates.put(source, 0f);
                }
                compositeSourceStates.put(axis, sourceStates);
            }
        }
    }

    @Override
    public void update(float deltaTime) {
        // Update all axes
        for (InputAxis axis : InputAxis.values()) {
            AxisConfig axisConfig = config.getAxisConfig(axis);
            float newValue = updateAxis(axis, axisConfig, deltaTime);
            axisValues.put(axis, newValue);
        }
    }

    /**
     * Updates a single axis based on its configuration.
     */
    private float updateAxis(InputAxis axis, AxisConfig axisConfig, float deltaTime) {
        if (axisConfig == null) {
            return 0f;
        }

        return switch (axisConfig.type()) {
            case KEYBOARD -> updateKeyboardAxis(axis, axisConfig, deltaTime);
            case MOUSE_DELTA -> updateMouseDeltaAxis(axis, axisConfig);
            case MOUSE_WHEEL -> updateMouseWheelAxis(axisConfig);
            case GAMEPAD -> updateGamepadAxis(axisConfig, deltaTime);
            case COMPOSITE -> updateCompositeAxis(axis, axisConfig, deltaTime);
        };
    }

    /**
     * Updates a keyboard axis with smooth interpolation.
     *
     * <p>Keyboard axes gradually accelerate towards the target value when keys are held,
     * and decelerate back to zero when released. This provides smooth, responsive movement.
     */
    private float updateKeyboardAxis(InputAxis axis, AxisConfig config, float deltaTime) {
        float currentValue = axisValues.getOrDefault(axis, 0f);
        float targetValue = getKeyboardAxisTarget(config);

        return interpolateTowardsTarget(currentValue, targetValue, config, deltaTime);
    }

    /**
     * Gets the target value for a keyboard axis based on key states.
     * Returns -1, 0, or 1.
     */
    private float getKeyboardAxisTarget(AxisConfig config) {
        boolean positive = config.positiveKey() != null && keyListener.isKeyHeld(config.positiveKey());
        boolean negative = config.negativeKey() != null && keyListener.isKeyHeld(config.negativeKey());

        // Check alternative keys
        if (config.altPositiveKey() != null && keyListener.isKeyHeld(config.altPositiveKey())) {
            positive = true;
        }
        if (config.altNegativeKey() != null && keyListener.isKeyHeld(config.altNegativeKey())) {
            negative = true;
        }

        if (positive && negative) {
            return 0f; // Both keys cancel out
        } else if (positive) {
            return 1f;
        } else if (negative) {
            return -1f;
        } else {
            return 0f;
        }
    }

    /**
     * Interpolates current value towards target with smooth acceleration/deceleration.
     */
    private float interpolateTowardsTarget(float currentValue, float targetValue,
                                           AxisConfig config, float deltaTime) {
        // If target is zero, apply gravity (deceleration)
        if (Math.abs(targetValue) < 0.001f) {
            if (Math.abs(currentValue) < 0.001f) {
                return 0f;
            }

            // Apply gravity
            float gravityAmount = config.gravity() * deltaTime;
            if (Math.abs(currentValue) <= gravityAmount) {
                return 0f;
            }

            return currentValue - Math.signum(currentValue) * gravityAmount;
        }

        // If snap is enabled and we're moving in opposite direction, snap to zero first
        if (config.snap() && Math.signum(currentValue) != Math.signum(targetValue) && Math.abs(currentValue) > 0.001f) {
            float gravityAmount = config.gravity() * deltaTime;
            if (Math.abs(currentValue) <= gravityAmount) {
                currentValue = 0f;
            } else {
                return currentValue - Math.signum(currentValue) * gravityAmount;
            }
        }

        // Accelerate towards target
        float sensitivityAmount = config.sensitivity() * deltaTime;
        float delta = targetValue - currentValue;

        if (Math.abs(delta) <= sensitivityAmount) {
            return targetValue;
        }

        return currentValue + Math.signum(delta) * sensitivityAmount;
    }

    /**
     * Updates a mouse delta axis.
     * Returns the mouse movement in the current frame multiplied by sensitivity.
     * CRITICAL: Needs axis parameter to distinguish between MOUSE_X and MOUSE_Y.
     */
    private float updateMouseDeltaAxis(InputAxis axis, AxisConfig config) {
        Vector2f delta = mouseListener.getMouseDelta();

        return switch (axis) {
            case MOUSE_X -> delta.x * config.sensitivity();
            case MOUSE_Y -> delta.y * config.sensitivity();
            default -> 0f;
        };
    }

    /**
     * Updates a mouse wheel axis.
     * Returns the scroll amount multiplied by sensitivity.
     */
    private float updateMouseWheelAxis(AxisConfig config) {
        return mouseListener.getScrollDelta().y * config.sensitivity();
    }

    /**
     * Updates a gamepad axis (either analog stick or button-based).
     */
    private float updateGamepadAxis(AxisConfig config, float deltaTime) {
        // Analog axis
        if (config.gamepadAxis() != null) {
            float rawValue = gamepadListener.getAxis(config.gamepadAxis());
            return rawValue * config.sensitivity();
        }

        // Button-based axis (e.g., D-pad) - needs interpolation
        if (config.positiveButton() != null || config.negativeButton() != null) {
            boolean positive = config.positiveButton() != null && gamepadListener.isButtonHeld(config.positiveButton());
            boolean negative = config.negativeButton() != null && gamepadListener.isButtonHeld(config.negativeButton());

            float targetValue = 0f;
            if (positive && !negative) {
                targetValue = 1f;
            } else if (negative && !positive) {
                targetValue = -1f;
            }

            // For button-based gamepad axes, return instantly (like digital input)
            return targetValue * config.sensitivity();
        }

        return 0f;
    }

    /**
     * Updates a composite axis by evaluating all sources and taking the one with
     * the largest absolute value. Maintains separate interpolation state for each source.
     */
    private float updateCompositeAxis(InputAxis axis, AxisConfig config, float deltaTime) {
        if (config.sources() == null || config.sources().length == 0) {
            return 0f;
        }

        Map<AxisConfig, Float> sourceStates = compositeSourceStates.get(axis);
        if (sourceStates == null) {
            // Initialize if not present (shouldn't happen, but defensive)
            sourceStates = new HashMap<>();
            for (AxisConfig source : config.sources()) {
                sourceStates.put(source, 0f);
            }
            compositeSourceStates.put(axis, sourceStates);
        }

        float maxValue = 0f;
        float maxAbsValue = 0f;

        // Evaluate each source and update its state
        for (AxisConfig source : config.sources()) {
            float currentSourceValue = sourceStates.getOrDefault(source, 0f);
            float newSourceValue = evaluateAndUpdateSource(axis, source, currentSourceValue, deltaTime);
            sourceStates.put(source, newSourceValue);

            float absValue = Math.abs(newSourceValue);

            // Take the source with the largest absolute value
            if (absValue > maxAbsValue) {
                maxAbsValue = absValue;
                maxValue = newSourceValue;
            }
        }

        return maxValue;
    }

    /**
     * Evaluates a single source axis for composite evaluation.
     * This maintains interpolation state for keyboard sources while reading instant values
     * for mouse and gamepad sources.
     * CRITICAL: Needs axis parameter for mouse delta to know X vs Y.
     */
    private float evaluateAndUpdateSource(InputAxis axis, AxisConfig config, float currentValue, float deltaTime) {
        return switch (config.type()) {
            case KEYBOARD -> {
                // Keyboard sources maintain smooth interpolation
                float target = getKeyboardAxisTarget(config);
                yield interpolateTowardsTarget(currentValue, target, config, deltaTime);
            }
            case MOUSE_DELTA -> updateMouseDeltaAxis(axis, config);
            case MOUSE_WHEEL -> updateMouseWheelAxis(config);
            case GAMEPAD -> updateGamepadAxis(config, deltaTime);
            case COMPOSITE -> throw new IllegalStateException("Nested composite axes are not supported");
        };
    }

    @Override
    public void endFrame() {
        keyListener.endFrame();
        mouseListener.endFrame();
        gamepadListener.endFrame();
    }

    @Override
    public void clear() {
        keyListener.clear();
        mouseListener.clear();
        gamepadListener.clear();

        for (InputAxis axis : InputAxis.values()) {
            axisValues.put(axis, 0f);
        }

        // Clear composite source states
        for (Map<AxisConfig, Float> sourceStates : compositeSourceStates.values()) {
            for (AxisConfig source : sourceStates.keySet()) {
                sourceStates.put(source, 0f);
            }
        }

        customAxisValues.clear();
    }

    @Override
    public void destroy() {
        clear();
        compositeSourceStates.clear();
    }

    // ========== Keyboard Input ==========

    @Override
    public boolean getKey(KeyCode key) {
        return keyListener.isKeyHeld(key);
    }

    @Override
    public boolean getKeyDown(KeyCode key) {
        return keyListener.wasKeyPressed(key);
    }

    @Override
    public boolean getKeyUp(KeyCode key) {
        return keyListener.wasKeyReleased(key);
    }

    @Override
    public boolean anyKey() {
        return keyListener.isAnyKeyHeld();
    }

    @Override
    public boolean anyKeyDown() {
        return keyListener.wasAnyKeyPressed();
    }

    // ========== Mouse Input ==========

    @Override
    public boolean getMouseButton(KeyCode button) {
        return mouseListener.isButtonHeld(button);
    }

    @Override
    public boolean getMouseButtonDown(KeyCode button) {
        return mouseListener.wasButtonPressed(button);
    }

    @Override
    public boolean getMouseButtonUp(KeyCode button) {
        return mouseListener.wasButtonReleased(button);
    }

    @Override
    public Vector2f getMousePosition() {
        return mouseListener.getMousePosition();
    }

    @Override
    public Vector2f getMouseDelta() {
        return mouseListener.getMouseDelta();
    }

    @Override
    public float getMouseScrollDelta() {
        return mouseListener.getScrollDelta().y;
    }

    @Override
    public boolean isMouseDragging(KeyCode button) {
        return mouseListener.isDragging(button);
    }

    // ========== Action System ==========

    @Override
    public boolean isActionHeld(InputAction action) {
        List<KeyCode> bindings = config.getBindingForAction(action);
        for (KeyCode key : bindings) {
            if (isKeyOrMouseButton(key) && isHeld(key)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isActionPressed(InputAction action) {
        List<KeyCode> bindings = config.getBindingForAction(action);
        for (KeyCode key : bindings) {
            if (isKeyOrMouseButton(key) && wasPressed(key)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isActionReleased(InputAction action) {
        List<KeyCode> bindings = config.getBindingForAction(action);
        for (KeyCode key : bindings) {
            if (isKeyOrMouseButton(key) && wasReleased(key)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if a KeyCode represents a keyboard key or mouse button.
     */
    private boolean isKeyOrMouseButton(KeyCode key) {
        return key != null && key.ordinal() < KeyCode.UNKNOWN.ordinal();
    }

    /**
     * Checks if a key or mouse button is currently held.
     */
    private boolean isHeld(KeyCode key) {
        if (key.ordinal() >= KeyCode.MOUSE_BUTTON_LEFT.ordinal() &&
                key.ordinal() <= KeyCode.MOUSE_BUTTON_8.ordinal()) {
            return mouseListener.isButtonHeld(key);
        } else {
            return keyListener.isKeyHeld(key);
        }
    }

    /**
     * Checks if a key or mouse button was pressed this frame.
     */
    private boolean wasPressed(KeyCode key) {
        if (key.ordinal() >= KeyCode.MOUSE_BUTTON_LEFT.ordinal() &&
                key.ordinal() <= KeyCode.MOUSE_BUTTON_8.ordinal()) {
            return mouseListener.wasButtonPressed(key);
        } else {
            return keyListener.wasKeyPressed(key);
        }
    }

    /**
     * Checks if a key or mouse button was released this frame.
     */
    private boolean wasReleased(KeyCode key) {
        if (key.ordinal() >= KeyCode.MOUSE_BUTTON_LEFT.ordinal() &&
                key.ordinal() <= KeyCode.MOUSE_BUTTON_8.ordinal()) {
            return mouseListener.wasButtonReleased(key);
        } else {
            return keyListener.wasKeyReleased(key);
        }
    }

    // ========== Axis System ==========

    @Override
    public float getAxis(InputAxis axis) {
        return axisValues.getOrDefault(axis, 0f);
    }

    @Override
    public float getAxis(String axisName) {
        // Try to find as InputAxis enum
        try {
            InputAxis axis = InputAxis.valueOf(axisName.toUpperCase());
            return getAxis(axis);
        } catch (IllegalArgumentException e) {
            // Not a standard axis, check custom axes
            return customAxisValues.getOrDefault(axisName, 0f);
        }
    }

    @Override
    public float getAxisRaw(InputAxis axis) {
        AxisConfig axisConfig = config.getAxisConfig(axis);
        if (axisConfig == null) {
            return 0f;
        }

        return switch (axisConfig.type()) {
            case KEYBOARD -> getKeyboardAxisTarget(axisConfig);
            case MOUSE_DELTA -> {
                Vector2f delta = mouseListener.getMouseDelta();
                yield switch (axis) {
                    case MOUSE_X -> delta.x * axisConfig.sensitivity();
                    case MOUSE_Y -> delta.y * axisConfig.sensitivity();
                    default -> 0f;
                };
            }
            case MOUSE_WHEEL -> mouseListener.getScrollDelta().y * axisConfig.sensitivity();
            case GAMEPAD -> {
                if (axisConfig.gamepadAxis() != null) {
                    yield gamepadListener.getAxis(axisConfig.gamepadAxis());
                } else {
                    boolean positive = axisConfig.positiveButton() != null &&
                            gamepadListener.isButtonHeld(axisConfig.positiveButton());
                    boolean negative = axisConfig.negativeButton() != null &&
                            gamepadListener.isButtonHeld(axisConfig.negativeButton());
                    yield (positive ? 1f : 0f) + (negative ? -1f : 0f);
                }
            }
            case COMPOSITE -> {
                if (axisConfig.sources() == null) yield 0f;

                float maxAbsValue = 0f;
                float resultValue = 0f;

                for (AxisConfig source : axisConfig.sources()) {
                    float sourceValue = getRawSourceValue(axis, source);
                    float absValue = Math.abs(sourceValue);
                    if (absValue > maxAbsValue) {
                        maxAbsValue = absValue;
                        resultValue = sourceValue;
                    }
                }
                yield resultValue;
            }
        };
    }

    @Override
    public float getAxisRaw(String axisName) {
        try {
            InputAxis axis = InputAxis.valueOf(axisName.toUpperCase());
            return getAxisRaw(axis);
        } catch (IllegalArgumentException e) {
            return customAxisValues.getOrDefault(axisName, 0f);
        }
    }

    /**
     * Gets the raw (uninterpolated) value for a source config.
     * CRITICAL: Needs axis parameter for mouse delta.
     */
    private float getRawSourceValue(InputAxis axis, AxisConfig config) {
        return switch (config.type()) {
            case KEYBOARD -> getKeyboardAxisTarget(config);
            case MOUSE_DELTA -> {
                Vector2f delta = mouseListener.getMouseDelta();
                yield switch (axis) {
                    case MOUSE_X -> delta.x * config.sensitivity();
                    case MOUSE_Y -> delta.y * config.sensitivity();
                    default -> 0f;
                };
            }
            case MOUSE_WHEEL -> mouseListener.getScrollDelta().y * config.sensitivity();
            case GAMEPAD -> {
                if (config.gamepadAxis() != null) {
                    yield gamepadListener.getAxis(config.gamepadAxis());
                } else {
                    boolean positive = config.positiveButton() != null &&
                            gamepadListener.isButtonHeld(config.positiveButton());
                    boolean negative = config.negativeButton() != null &&
                            gamepadListener.isButtonHeld(config.negativeButton());
                    yield (positive ? 1f : 0f) + (negative ? -1f : 0f);
                }
            }
            case COMPOSITE -> throw new IllegalStateException("Nested composite axes not supported");
        };
    }

    @Override
    public void setAxisValue(String axisName, float value) {
        customAxisValues.put(axisName, value);
    }
}