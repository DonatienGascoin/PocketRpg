package com.pocket.rpg.input;

import com.pocket.rpg.config.InputConfig;
import com.pocket.rpg.input.listeners.GamepadListener;
import com.pocket.rpg.input.listeners.KeyListener;
import com.pocket.rpg.input.listeners.MouseListener;
import org.joml.Vector2f;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Real implementation of InputContext that uses actual keyboard/mouse listeners.
 * This is the implementation used during normal gameplay.
 */
public class DefaultInputContext implements InputContext {

    private final InputConfig inputConfig;
    private final KeyListener keyListener;
    private final MouseListener mouseListener;
    private final GamepadListener gamepadListener;

    // Virtual axes
    private final Map<InputAxis, AxisConfig> axisConfigs = new HashMap<>();
    private final Map<InputAxis, Float> axisValues = new HashMap<>();
    private final Map<InputAxis, Float> previousAxisValues = new HashMap<>();

    // Temp storage for composite axis evaluation
    private final Map<InputAxis, Map<AxisConfig, Float>> compositeSourceValues = new EnumMap<>(InputAxis.class);


    private static final float AXIS_EVENT_THRESHOLD = 0.01f;

    public DefaultInputContext(InputConfig config, KeyListener keyListener, MouseListener mouseListener, GamepadListener gamepadListener) {
        this.inputConfig = config;
        this.keyListener = keyListener;
        this.mouseListener = mouseListener;
        this.gamepadListener = gamepadListener;
        loadAxesFromConfig();
    }

    private void loadAxesFromConfig() {
        for (InputAxis axis : InputAxis.values()) {
            AxisConfig config = inputConfig.getAxisConfig(axis);
            if (config != null) {
                registerAxis(axis, config);
            }
        }
    }

    @Override
    public void update(float deltaTime) {
        updateAxes(deltaTime);
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

        for (InputAxis axis : axisValues.keySet()) {
            axisValues.put(axis, 0f);
            previousAxisValues.put(axis, 0f);
        }
    }

    @Override
    public void destroy() {
        clear();
        axisConfigs.clear();
        axisValues.clear();
        previousAxisValues.clear();
    }

    // ========================================
    // Keyboard Implementation
    // ========================================

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

    // ========================================
    // Action Implementation
    // ========================================

    @Override
    public boolean isActionHeld(InputAction action) {
        List<KeyCode> bindings = inputConfig.getBindingForAction(action);
        for (KeyCode key : bindings) {
            if (getKey(key)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isActionPressed(InputAction action) {
        List<KeyCode> bindings = inputConfig.getBindingForAction(action);
        for (KeyCode key : bindings) {
            if (getKeyDown(key)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isActionReleased(InputAction action) {
        List<KeyCode> bindings = inputConfig.getBindingForAction(action);
        for (KeyCode key : bindings) {
            if (getKeyUp(key)) {
                return true;
            }
        }
        return false;
    }

    // ========================================
    // Mouse Implementation
    // ========================================

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
        return (float) mouseListener.getScrollY();
    }

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
    public boolean isMouseDragging(KeyCode button) {
        return mouseListener.isDragging(button);
    }

    // ========================================
    // Axis Implementation (UPDATED)
    // ========================================

    @Override
    public float getAxis(InputAxis axis) {
        return axisValues.getOrDefault(axis, 0f);
    }

    @Override
    public float getAxis(String axisName) {
        try {
            InputAxis axis = InputAxis.valueOf(axisName.toUpperCase());
            return getAxis(axis);
        } catch (IllegalArgumentException e) {
            return 0f;
        }
    }

    @Override
    public float getAxisRaw(InputAxis axis) {
        return getAxisRaw(axis.name());
    }

    @Override
    public float getAxisRaw(String axisName) {
        try {
            InputAxis axis = InputAxis.valueOf(axisName.toUpperCase());
            AxisConfig config = axisConfigs.get(axis);
            if (config == null) return 0f;

            return switch (config.type()) {
                case KEYBOARD -> getKeyboardAxisRaw(config);
                case GAMEPAD -> getGamepadAxisRaw(config);
                case COMPOSITE -> getCompositeAxisRaw(config);
                default -> getAxis(axis); // Mouse axes same as getAxis
            };
        } catch (IllegalArgumentException e) {
            return 0f;
        }
    }

    @Override
    public void setAxisValue(String axisName, float value) {
        try {
            InputAxis axis = InputAxis.valueOf(axisName.toUpperCase());
            axisValues.put(axis, value);
        } catch (IllegalArgumentException e) {
            // Ignore unknown axis
        }
    }

    private void updateAxes(float deltaTime) {
        for (Map.Entry<InputAxis, AxisConfig> entry : axisConfigs.entrySet()) {
            InputAxis axis = entry.getKey();
            AxisConfig config = entry.getValue();

            float currentValue = axisValues.getOrDefault(axis, 0f);
            float previousValue = previousAxisValues.getOrDefault(axis, 0f);
            float newValue;

            // Dispatch based on axis type
            newValue = switch (config.type()) {
                case KEYBOARD -> updateKeyboardAxis(config, currentValue, deltaTime);
                case MOUSE_DELTA -> updateMouseDeltaAxis(axis, config);
                case MOUSE_WHEEL -> updateMouseWheelAxis(config);
                case GAMEPAD -> updateGamepadAxis(config, currentValue, deltaTime);
                case COMPOSITE -> updateCompositeAxis(axis, config, currentValue, deltaTime);
            };

            axisValues.put(axis, newValue);

            if (Math.abs(newValue - previousValue) > AXIS_EVENT_THRESHOLD) {
                previousAxisValues.put(axis, newValue);
            }
        }
    }

    // ========================================
    // Axis Update Methods (UPDATED)
    // ========================================

    private float updateKeyboardAxis(AxisConfig config, float currentValue, float deltaTime) {
        float targetValue = 0f;

        if (config.positiveKey() != null && getKey(config.positiveKey())) {
            targetValue += 1f;
        }
        if (config.negativeKey() != null && getKey(config.negativeKey())) {
            targetValue -= 1f;
        }
        if (config.altPositiveKey() != null && getKey(config.altPositiveKey())) {
            targetValue += 1f;
        }
        if (config.altNegativeKey() != null && getKey(config.altNegativeKey())) {
            targetValue -= 1f;
        }

        targetValue = Math.max(-1f, Math.min(1f, targetValue));

        // Snap: instant zero when reversing direction
        if (config.snap() && Math.signum(currentValue) != Math.signum(targetValue)) {
            currentValue = 0f;
        }

        // Smooth interpolation
        float speed = (targetValue != 0f) ? config.sensitivity() : config.gravity();
        float newValue = moveTowards(currentValue, targetValue, speed * deltaTime);

        // Apply dead zone
        if (Math.abs(newValue) < config.deadZone()) {
            newValue = 0f;
        }

        return newValue;
    }

    private float updateMouseDeltaAxis(InputAxis axis, AxisConfig config) {
        Vector2f delta = mouseListener.getMouseDelta();

        return switch (axis) {
            case MOUSE_X -> delta.x * config.sensitivity();
            case MOUSE_Y -> delta.y * config.sensitivity();
            default -> 0f;
        };
    }

    private float updateMouseWheelAxis(AxisConfig config) {
        return (float) mouseListener.getScrollY() * config.sensitivity();
    }

    private float updateGamepadAxis(AxisConfig config, float currentValue, float deltaTime) {
        float value = 0f;

        // Analog axis
        if (config.gamepadAxis() != null) {
            value = gamepadListener.getAxis(config.gamepadAxis()) * config.sensitivity();
        }
        // Button-based axis (like D-pad)
        else if (config.positiveButton() != null || config.negativeButton() != null) {
            float targetValue = 0f;

            if (config.positiveButton() != null && gamepadListener.isButtonHeld(config.positiveButton())) {
                targetValue += 1f;
            }
            if (config.negativeButton() != null && gamepadListener.isButtonHeld(config.negativeButton())) {
                targetValue -= 1f;
            }

            // Apply interpolation like keyboard
            if (config.snap() && Math.signum(currentValue) != Math.signum(targetValue)) {
                currentValue = 0f;
            }

            float speed = (targetValue != 0f) ? config.sensitivity() : config.gravity();
            value = moveTowards(currentValue, targetValue, speed * deltaTime);
        }

        // Apply dead zone
        if (Math.abs(value) < config.deadZone()) {
            value = 0f;
        }

        return value;
    }

    private float updateCompositeAxis(InputAxis axis, AxisConfig config,
                                      float currentValue, float deltaTime) {
        if (config.sources() == null || config.sources().length == 0) {
            return 0f;
        }

        // Evaluate all source axes
        Map<AxisConfig, Float> sourceValues = compositeSourceValues.computeIfAbsent(
                axis, k -> new EnumMap<>(InputAxis.class).entrySet().stream()
                        .collect(java.util.stream.Collectors.toMap(
                                Map.Entry::getKey,
                                e -> 0f
                        ))
        );

        float maxAbsValue = 0f;
        float resultValue = 0f;

        for (AxisConfig source : config.sources()) {
            float sourceValue = evaluateSourceAxis(source, currentValue, deltaTime);

            // Take the source with largest absolute value
            if (Math.abs(sourceValue) > Math.abs(maxAbsValue)) {
                maxAbsValue = sourceValue;
                resultValue = sourceValue;
            }
        }

        return resultValue;
    }

    private float evaluateSourceAxis(AxisConfig config, float currentValue, float deltaTime) {
        return switch (config.type()) {
            case KEYBOARD -> updateKeyboardAxis(config, currentValue, deltaTime);
            case MOUSE_DELTA -> {
                Vector2f delta = mouseListener.getMouseDelta();
                yield delta.x * config.sensitivity(); // Simplified for composite
            }
            case MOUSE_WHEEL -> (float) mouseListener.getScrollY() * config.sensitivity();
            case GAMEPAD -> updateGamepadAxis(config, currentValue, deltaTime);
            case COMPOSITE -> updateCompositeAxis(null, config, currentValue, deltaTime);
        };
    }

    // ========================================
    // Raw Axis Queries (UPDATED)
    // ========================================

    private float getKeyboardAxisRaw(AxisConfig config) {
        float value = 0f;

        if (config.positiveKey() != null && getKey(config.positiveKey())) {
            value += 1f;
        }
        if (config.negativeKey() != null && getKey(config.negativeKey())) {
            value -= 1f;
        }
        if (config.altPositiveKey() != null && getKey(config.altPositiveKey())) {
            value += 1f;
        }
        if (config.altNegativeKey() != null && getKey(config.altNegativeKey())) {
            value -= 1f;
        }

        return Math.max(-1f, Math.min(1f, value));
    }

    private float getGamepadAxisRaw(AxisConfig config) {
        if (config.gamepadAxis() != null) {
            return gamepadListener.getAxis(config.gamepadAxis());
        } else if (config.positiveButton() != null || config.negativeButton() != null) {
            float value = 0f;
            if (config.positiveButton() != null && gamepadListener.isButtonHeld(config.positiveButton())) {
                value += 1f;
            }
            if (config.negativeButton() != null && gamepadListener.isButtonHeld(config.negativeButton())) {
                value -= 1f;
            }
            return value;
        }
        return 0f;
    }

    private float getCompositeAxisRaw(AxisConfig config) {
        if (config.sources() == null) return 0f;

        float maxAbsValue = 0f;
        float resultValue = 0f;

        for (AxisConfig source : config.sources()) {
            float sourceValue = switch (source.type()) {
                case KEYBOARD -> getKeyboardAxisRaw(source);
                case GAMEPAD -> getGamepadAxisRaw(source);
                default -> 0f;
            };

            if (Math.abs(sourceValue) > Math.abs(maxAbsValue)) {
                maxAbsValue = sourceValue;
                resultValue = sourceValue;
            }
        }

        return resultValue;
    }

    private void registerAxis(InputAxis axis, AxisConfig config) {
        axisConfigs.put(axis, config);
        axisValues.put(axis, 0f);
        previousAxisValues.put(axis, 0f);
    }

    private static float moveTowards(float current, float target, float maxDelta) {
        if (Math.abs(target - current) <= maxDelta) {
            return target;
        }
        return current + Math.signum(target - current) * maxDelta;
    }
}