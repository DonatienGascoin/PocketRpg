package com.pocket.rpg.input;

import com.pocket.rpg.input.listeners.KeyListener;
import com.pocket.rpg.input.listeners.MouseListener;
import org.joml.Vector2f;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Unity-style input manager with event bus support.
 *
 * <h3>Polling API:</h3>
 * <pre>{@code
 * if (InputManager.getKeyDown(KeyCode.SPACE)) {
 *     player.jump();
 * }
 * }</pre>
 */
public class InputManager {

    private static InputManager instance;

    // Listeners for state tracking
    private final KeyListener keyListener;
    private final MouseListener mouseListener;

    // Virtual axes
    private final Map<String, AxisConfig> axisConfigs = new HashMap<>();
    private final Map<String, Float> axisValues = new HashMap<>();
    private final Map<String, Float> previousAxisValues = new HashMap<>();

    // Axis event threshold (how much change before firing event)
    private float axisEventThreshold = 0.01f; // TODO, Needed ? AxisConfig ?

    private InputManager(KeyListener keyListener, MouseListener mouseListener) {
        this.keyListener = keyListener;
        this.mouseListener = mouseListener;
    }

    /**
     * Initializes the input manager with a backend.
     */
    public static void initialize(KeyListener keyListener, MouseListener mouseListener) {
        if (instance != null) {
            System.err.println("WARNING: InputManager already initialized");
            return;
        }

        instance = new InputManager(keyListener, mouseListener);
        instance.registerDefaultAxes();
        System.out.println("InputManager initialized");
    }

    /**
     * Gets the InputManager instance.
     */
    private static InputManager get() {
        if (instance == null) {
            throw new IllegalStateException("InputManager not initialized. Call InputManager.initialize() first.");
        }
        return instance;
    }

    /**
     * Registers default axes (Horizontal, Vertical, Mouse X, Mouse Y).
     */
    private void registerDefaultAxes() {
        registerAxis("Horizontal", new AxisConfig(KeyCode.D, KeyCode.A)
                .withAltKeys(KeyCode.RIGHT, KeyCode.LEFT));

        registerAxis("Vertical", new AxisConfig(KeyCode.W, KeyCode.S)
                .withAltKeys(KeyCode.UP, KeyCode.DOWN));

        registerAxis("Mouse X", new AxisConfig(null, null));
        registerAxis("Mouse Y", new AxisConfig(null, null));

        System.out.println("Registered default axes: Horizontal, Vertical, Mouse X, Mouse Y");
    }

    // ========================================
    // Update (called once per frame)
    // ========================================

    /**
     * Updates input state and fires events.
     * Call once per frame after polling backend events.
     */
    public static void update(float deltaTime) {
        // Update all axes and fire axis events
        get().updateAxes(deltaTime);
    }

    /**
     * Clears frame-specific state in listeners.
     * Called automatically by update().
     */
    public static void endFrame() {
        InputManager manager = get();
        manager.keyListener.endFrame();
        manager.mouseListener.endFrame();
    }

    /**
     * Updates all virtual axes with smooth interpolation and fires axis events.
     * TODO: Needed ?
     */
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

                // Fire axis event if changed significantly
                if (Math.abs(targetValue - previousValue) > axisEventThreshold) {
                    previousAxisValues.put(axisName, targetValue);
                }
                continue;
            } else if (axisName.equals("Mouse Y")) {
                targetValue = mouseListener.getMouseDelta().y * config.sensitivity();
                axisValues.put(axisName, targetValue);

                // Fire axis event if changed significantly
                if (Math.abs(targetValue - previousValue) > axisEventThreshold) {
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

            // Fire axis event if changed significantly
            if (Math.abs(newValue - previousValue) > axisEventThreshold) {
                previousAxisValues.put(axisName, newValue);
            }
        }
    }

    // TODO: Needed ?
    private static float moveTowards(float current, float target, float maxDelta) {
        if (Math.abs(target - current) <= maxDelta) {
            return target;
        }
        return current + Math.signum(target - current) * maxDelta;
    }

    // ========================================
    // Keyboard Input API (Polling)
    // ========================================

    /**
     * Returns true while the key is held down.
     */
    public static boolean getKey(KeyCode key) {
        return get().keyListener.isKeyHeld(key);
    }

    /**
     * Returns true during the frame the key was pressed.
     */
    public static boolean getKeyDown(KeyCode key) {
        return get().keyListener.wasKeyPressed(key);
    }

    /**
     * Returns true during the frame the key was released.
     */
    public static boolean getKeyUp(KeyCode key) {
        return get().keyListener.wasKeyReleased(key);
    }

    /**
     * Returns true if any key is currently held down.
     */
    public static boolean anyKey() {
        return get().keyListener.isAnyKeyHeld();
    }

    /**
     * Returns true if any key was pressed this frame.
     */
    public static boolean anyKeyDown() {
        return get().keyListener.wasAnyKeyPressed();
    }

    // ========================================
    // Mouse Input API (Polling)
    // ========================================

    /**
     * Gets the current mouse position in screen coordinates.
     */
    public static Vector2f getMousePosition() {
        return get().mouseListener.getMousePosition();
    }

    /**
     * Gets the mouse movement since last frame.
     */
    public static Vector2f getMouseDelta() {
        return get().mouseListener.getMouseDelta();
    }

    /**
     * Gets the mouse scroll delta (vertical only).
     */
    public static float getMouseScrollDelta() {
        return (float) get().mouseListener.getScrollY();
    }

    /**
     * Returns true while the mouse button is held down.
     */
    public static boolean getMouseButton(KeyCode button) {
        return get().mouseListener.isButtonHeld(button);
    }

    /**
     * Returns true during the frame the mouse button was pressed.
     */
    public static boolean getMouseButtonDown(KeyCode button) {
        return get().mouseListener.wasButtonPressed(button);
    }

    /**
     * Returns true during the frame the mouse button was released.
     */
    public static boolean getMouseButtonUp(KeyCode button) {
        return get().mouseListener.wasButtonReleased(button);
    }

    /**
     * Checks if the mouse is being dragged with a specific button.
     */
    public static boolean isMouseDragging(KeyCode button) {
        return get().mouseListener.isDragging(button);
    }

    // ========================================
    // Virtual Axes API (Polling)
    // ========================================

    /**
     * Gets the value of a virtual axis with smooth interpolation.
     * Returns a value between -1 and 1.
     */
    public static float getAxis(String axisName) {
        return get().axisValues.getOrDefault(axisName, 0f);
    }

    /**
     * Gets the raw value of a virtual axis without smoothing.
     * Returns -1, 0, or 1.
     */
    public static float getAxisRaw(String axisName) {
        InputManager manager = get();
        AxisConfig config = manager.axisConfigs.get(axisName);

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

    /**
     * Sets the value of a virtual axis manually (e.g., for gamepad input).
     */
    public static void setAxisValue(String axisName, float value) {
        get().axisValues.put(axisName, value);
    }

    /**
     * Registers or updates a virtual axis configuration.
     */
    public static void registerAxis(String axisName, AxisConfig config) {
        InputManager manager = get();
        manager.axisConfigs.put(axisName, config);
        manager.axisValues.put(axisName, 0f);
        manager.previousAxisValues.put(axisName, 0f);
    }

    /**
     * Gets the configuration for a virtual axis.
     */
    public static AxisConfig getAxisConfig(String axisName) {
        return get().axisConfigs.get(axisName);
    }

    /**
     * Removes a virtual axis.
     */
    public static void removeAxis(String axisName) {
        InputManager manager = get();
        manager.axisConfigs.remove(axisName);
        manager.axisValues.remove(axisName);
        manager.previousAxisValues.remove(axisName);
    }

    /**
     * Gets all registered axis names.
     */
    public static Set<String> getAxisNames() {
        return new HashSet<>(get().axisConfigs.keySet());
    }

    // ========================================
    // Utility Methods
    // ========================================

    /**
     * Clears all input state. Useful for scene transitions.
     */
    public static void clear() {
        InputManager manager = get();
        manager.keyListener.clear();
        manager.mouseListener.clear();

        for (String axis : manager.axisValues.keySet()) {
            manager.axisValues.put(axis, 0f);
            manager.previousAxisValues.put(axis, 0f);
        }
    }

    /**
     * Destroys the input manager.
     */
    public static void destroy() {
        if (instance != null) {
            instance.keyListener.clear();
            instance.mouseListener.clear();
            instance = null;
            System.out.println("InputManager destroyed");
        }
    }
}