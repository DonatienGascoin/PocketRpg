package com.pocket.rpg.inputNew;

import com.pocket.rpg.inputNew.events.*;
import org.joml.Vector2f;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Unity-style input manager with event bus support.
 * Provides both polling API and event-driven input.
 *
 * <h3>Polling API:</h3>
 * <pre>{@code
 * if (InputManager.getKeyDown(KeyCode.SPACE)) {
 *     player.jump();
 * }
 * }</pre>
 *
 * <h3>Event API:</h3>
 * <pre>{@code
 * InputManager.getEventBus().register(ui);
 * }</pre>
 */
public class InputManager {

    private static InputManager instance;

    // Backend for platform-specific key mapping
    private InputBackend backend;

    // Event bus for event-driven input
    private final InputEventBus eventBus = new InputEventBus();

    // Key states
    private final Set<KeyCode> keysDown = new HashSet<>();
    private final Set<KeyCode> keysPressed = new HashSet<>();
    private final Set<KeyCode> keysReleased = new HashSet<>();

    // Mouse state
    private Vector2f mousePosition = new Vector2f();
    private Vector2f lastMousePosition = new Vector2f();
    private Vector2f mouseDelta = new Vector2f();
    private float mouseScrollDelta = 0f;
    private boolean cursorLocked = false;

    // Virtual axes
    private final Map<String, AxisConfig> axisConfigs = new HashMap<>();
    private final Map<String, Float> axisValues = new HashMap<>();
    private final Map<String, Float> previousAxisValues = new HashMap<>();

    // Axis event threshold (how much change before firing event)
    private float axisEventThreshold = 0.01f;

    private InputManager(InputBackend backend) {
        this.backend = backend;
    }

    /**
     * Initializes the input manager with a backend.
     */
    public static void initialize(InputBackend backend) {
        if (instance != null) {
            System.err.println("WARNING: InputManager already initialized");
            return;
        }

        instance = new InputManager(backend);
        instance.registerDefaultAxes();
        System.out.println("InputManager initialized with backend: " + backend.getClass().getSimpleName());
    }

    public static void endFrame() {
        // TODO
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
     * Gets the event bus for registering event listeners.
     */
    public static InputEventBus getEventBus() {
        return get().eventBus;
    }

    /**
     * Convenience method to register an event listener.
     */
    public static void registerListener(InputEventListener listener) {
        getEventBus().register(listener);
    }

    /**
     * Convenience method to unregister an event listener.
     */
    public static void unregisterListener(InputEventListener listener) {
        getEventBus().unregister(listener);
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
    // Backend Event Methods (called by GLFW callbacks)
    // ========================================

    /**
     * Called when a key is pressed.
     */
    public static void onKeyPressed(int backendKeyCode) {
        InputManager manager = get();
        KeyCode key = manager.backend.getKeyCode(backendKeyCode);

        if (key != KeyCode.UNKNOWN) {
            manager.keysPressed.add(key);
            manager.keysDown.add(key);

            // Post key event
            boolean shift = manager.keysDown.contains(KeyCode.LEFT_SHIFT) ||
                    manager.keysDown.contains(KeyCode.RIGHT_SHIFT);
            boolean control = manager.keysDown.contains(KeyCode.LEFT_CONTROL) ||
                    manager.keysDown.contains(KeyCode.RIGHT_CONTROL);
            boolean alt = manager.keysDown.contains(KeyCode.LEFT_ALT) ||
                    manager.keysDown.contains(KeyCode.RIGHT_ALT);

            manager.eventBus.post(new KeyEvent(key, KeyEvent.Action.PRESS, shift, control, alt));
        }
    }

    /**
     * Called when a key is released.
     */
    public static void onKeyReleased(int backendKeyCode) {
        InputManager manager = get();
        KeyCode key = manager.backend.getKeyCode(backendKeyCode);

        if (key != KeyCode.UNKNOWN) {
            manager.keysReleased.add(key);
            manager.keysDown.remove(key);

            // Post key event
            boolean shift = manager.keysDown.contains(KeyCode.LEFT_SHIFT) ||
                    manager.keysDown.contains(KeyCode.RIGHT_SHIFT);
            boolean control = manager.keysDown.contains(KeyCode.LEFT_CONTROL) ||
                    manager.keysDown.contains(KeyCode.RIGHT_CONTROL);
            boolean alt = manager.keysDown.contains(KeyCode.LEFT_ALT) ||
                    manager.keysDown.contains(KeyCode.RIGHT_ALT);

            manager.eventBus.post(new KeyEvent(key, KeyEvent.Action.RELEASE, shift, control, alt));
        }
    }

    /**
     * Called when mouse moves.
     */
    public static void onMouseMoved(double x, double y) {
        InputManager manager = get();
        manager.mousePosition.x = (float) x;
        manager.mousePosition.y = (float) y;
    }

    /**
     * Called when mouse button is pressed.
     */
    public static void onMouseButtonPressed(int backendButtonCode) {
        InputManager manager = get();
        KeyCode button = manager.backend.getKeyCode(backendButtonCode);

        if (button != KeyCode.UNKNOWN) {
            manager.keysPressed.add(button);
            manager.keysDown.add(button);

            // Post mouse button event
            manager.eventBus.post(new MouseButtonEvent(
                    button,
                    MouseButtonEvent.Action.PRESS,
                    manager.mousePosition
            ));
        }
    }

    /**
     * Called when mouse button is released.
     */
    public static void onMouseButtonReleased(int backendButtonCode) {
        InputManager manager = get();
        KeyCode button = manager.backend.getKeyCode(backendButtonCode);

        if (button != KeyCode.UNKNOWN) {
            manager.keysReleased.add(button);
            manager.keysDown.remove(button);

            // Post mouse button event
            manager.eventBus.post(new MouseButtonEvent(
                    button,
                    MouseButtonEvent.Action.RELEASE,
                    manager.mousePosition
            ));
        }
    }

    /**
     * Called when mouse scrolls.
     */
    public static void onMouseScroll(double xOffset, double yOffset) {
        InputManager manager = get();
        manager.mouseScrollDelta = (float) yOffset;

        // Post scroll event
        if (yOffset != 0) {
            manager.eventBus.post(new MouseScrollEvent((float) yOffset));
        }
    }

    // ========================================
    // Update (called once per frame)
    // ========================================

    /**
     * Updates input state and fires events.
     * Call once per frame after polling GLFW events.
     */
    public static void update(float deltaTime) {
        InputManager manager = get();

        // Calculate mouse delta
        manager.mouseDelta.x = manager.mousePosition.x - manager.lastMousePosition.x;
        manager.mouseDelta.y = manager.mousePosition.y - manager.lastMousePosition.y;

        // Post mouse move event if moved
        if (manager.mouseDelta.x != 0 || manager.mouseDelta.y != 0) {
            manager.eventBus.post(new MouseMoveEvent(
                    manager.mousePosition,
                    manager.mouseDelta
            ));
        }

        manager.lastMousePosition.x = manager.mousePosition.x;
        manager.lastMousePosition.y = manager.mousePosition.y;

        // Update all axes and fire axis events
        manager.updateAxes(deltaTime);

        // Clear frame-specific input AFTER events are posted
        manager.keysPressed.clear();
        manager.keysReleased.clear();

        // Reset scroll (one frame only)
        manager.mouseScrollDelta = 0f;
    }

    /**
     * Updates all virtual axes with smooth interpolation and fires axis events.
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
                targetValue = mouseDelta.x * config.sensitivity();
                axisValues.put(axisName, targetValue);

                // Fire axis event if changed significantly
                if (Math.abs(targetValue - previousValue) > axisEventThreshold) {
                    eventBus.post(new AxisEvent(axisName, targetValue, previousValue));
                    previousAxisValues.put(axisName, targetValue);
                }
                continue;
            } else if (axisName.equals("Mouse Y")) {
                targetValue = mouseDelta.y * config.sensitivity();
                axisValues.put(axisName, targetValue);

                // Fire axis event if changed significantly
                if (Math.abs(targetValue - previousValue) > axisEventThreshold) {
                    eventBus.post(new AxisEvent(axisName, targetValue, previousValue));
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
                eventBus.post(new AxisEvent(axisName, newValue, previousValue));
                previousAxisValues.put(axisName, newValue);
            }
        }
    }

    private static float moveTowards(float current, float target, float maxDelta) {
        if (Math.abs(target - current) <= maxDelta) {
            return target;
        }
        return current + Math.signum(target - current) * maxDelta;
    }

    // ========================================
    // Keyboard Input API (Polling)
    // ========================================

    public static boolean getKey(KeyCode key) {
        return get().keysDown.contains(key);
    }

    public static boolean getKeyDown(KeyCode key) {
        return get().keysPressed.contains(key);
    }

    public static boolean getKeyUp(KeyCode key) {
        return get().keysReleased.contains(key);
    }

    public static boolean anyKey() {
        return !get().keysDown.isEmpty();
    }

    public static boolean anyKeyDown() {
        return !get().keysPressed.isEmpty();
    }

    // ========================================
    // Mouse Input API (Polling)
    // ========================================

    public static Vector2f getMousePosition() {
        return new Vector2f(get().mousePosition);
    }

    public static Vector2f getMouseDelta() {
        return new Vector2f(get().mouseDelta);
    }

    public static float getMouseScrollDelta() {
        return get().mouseScrollDelta;
    }

    public static void setCursorLocked(boolean locked) {
        get().cursorLocked = locked;
    }

    public static boolean isCursorLocked() {
        return get().cursorLocked;
    }

    // ========================================
    // Virtual Axes API (Polling)
    // ========================================

    public static float getAxis(String axisName) {
        return get().axisValues.getOrDefault(axisName, 0f);
    }

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

    public static void setAxisValue(String axisName, float value) {
        get().axisValues.put(axisName, value);
    }

    public static void registerAxis(String axisName, AxisConfig config) {
        get().axisConfigs.put(axisName, config);
        get().axisValues.put(axisName, 0f);
        get().previousAxisValues.put(axisName, 0f);
    }

    public static AxisConfig getAxisConfig(String axisName) {
        return get().axisConfigs.get(axisName);
    }

    public static void removeAxis(String axisName) {
        get().axisConfigs.remove(axisName);
        get().axisValues.remove(axisName);
        get().previousAxisValues.remove(axisName);
    }

    public static Set<String> getAxisNames() {
        return new HashSet<>(get().axisConfigs.keySet());
    }

    // ========================================
    // Configuration
    // ========================================

    /**
     * Set the threshold for axis events.
     * Events are only fired when axis value changes by at least this amount.
     */
    public static void setAxisEventThreshold(float threshold) {
        get().axisEventThreshold = threshold;
    }

    /**
     * Enable or disable debug mode for the event bus.
     */
    public static void setDebugMode(boolean debug) {
        get().eventBus.setDebugMode(debug);
    }

    // ========================================
    // Utility Methods
    // ========================================

    public static void clear() {
        InputManager manager = get();
        manager.keysDown.clear();
        manager.keysPressed.clear();
        manager.keysReleased.clear();
        manager.mouseDelta.x = 0;
        manager.mouseDelta.y = 0;
        manager.mouseScrollDelta = 0;

        for (String axis : manager.axisValues.keySet()) {
            manager.axisValues.put(axis, 0f);
            manager.previousAxisValues.put(axis, 0f);
        }
    }

    public static String getKeyName(KeyCode key) {
        return get().backend.getKeyName(key);
    }

    public static Set<KeyCode> getPressedKeys() {
        return new HashSet<>(get().keysDown);
    }

    public static void destroy() {
        if (instance != null) {
            instance.eventBus.clear();
            instance = null;
            System.out.println("InputManager destroyed");
        }
    }
}