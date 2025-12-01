package com.pocket.rpg.input;

import com.pocket.rpg.config.InputConfig;
import com.pocket.rpg.input.listeners.KeyListener;
import com.pocket.rpg.input.listeners.MouseListener;
import org.joml.Vector2f;

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

    // Virtual axes
    private final Map<String, AxisConfig> axisConfigs = new HashMap<>();
    private final Map<String, Float> axisValues = new HashMap<>();
    private final Map<String, Float> previousAxisValues = new HashMap<>();

    private static final float AXIS_EVENT_THRESHOLD = 0.01f;

    public DefaultInputContext(InputConfig config, KeyListener keyListener, MouseListener mouseListener) {
        this.inputConfig = config;
        this.keyListener = keyListener;
        this.mouseListener = mouseListener;
        loadAxesFromConfig();
    }

    private void loadAxesFromConfig() {
        for (InputAxis axis : InputAxis.values()) {
            registerAxis(axis.name(), inputConfig.getAxisConfig(axis));
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
    }

    @Override
    public void clear() {
        keyListener.clear();
        mouseListener.clear();

        for (String axis : axisValues.keySet()) {
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
    // Axis Implementation
    // ========================================

    @Override
    public float getAxis(InputAxis axis) {
        return getAxis(axis.name());
    }

    @Override
    public float getAxis(String axisName) {
        return axisValues.getOrDefault(axisName, 0f);
    }

    @Override
    public float getAxisRaw(InputAxis axis) {
        return getAxisRaw(axis.name());
    }

    @Override
    public float getAxisRaw(String axisName) {
        AxisConfig config = axisConfigs.get(axisName);
        if (config == null) return 0f;

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

    @Override
    public void setAxisValue(String axisName, float value) {
        axisValues.put(axisName, value);
    }

    private void updateAxes(float deltaTime) {
        for (Map.Entry<String, AxisConfig> entry : axisConfigs.entrySet()) {
            String axisName = entry.getKey();
            AxisConfig config = entry.getValue();

            float currentValue = axisValues.getOrDefault(axisName, 0f);
            float previousValue = previousAxisValues.getOrDefault(axisName, 0f);
            float targetValue;

            // Special handling for mouse axes
            if (axisName.equals("Mouse X")) {
                targetValue = mouseListener.getMouseDelta().x * config.sensitivity();
                axisValues.put(axisName, targetValue);
                if (Math.abs(targetValue - previousValue) > AXIS_EVENT_THRESHOLD) {
                    previousAxisValues.put(axisName, targetValue);
                }
                continue;
            } else if (axisName.equals("Mouse Y")) {
                targetValue = mouseListener.getMouseDelta().y * config.sensitivity();
                axisValues.put(axisName, targetValue);
                if (Math.abs(targetValue - previousValue) > AXIS_EVENT_THRESHOLD) {
                    previousAxisValues.put(axisName, targetValue);
                }
                continue;
            }

            // Keyboard axis logic
            targetValue = 0f;

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

            axisValues.put(axisName, newValue);

            if (Math.abs(newValue - previousValue) > AXIS_EVENT_THRESHOLD) {
                previousAxisValues.put(axisName, newValue);
            }
        }
    }

    private void registerAxis(String axisName, AxisConfig config) {
        axisConfigs.put(axisName, config);
        axisValues.put(axisName, 0f);
        previousAxisValues.put(axisName, 0f);
    }

    private static float moveTowards(float current, float target, float maxDelta) {
        if (Math.abs(target - current) <= maxDelta) {
            return target;
        }
        return current + Math.signum(target - current) * maxDelta;
    }
}