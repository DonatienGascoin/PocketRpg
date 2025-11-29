package com.pocket.rpg.inputNew;

import org.joml.Vector2f;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Unity-style input manager for LWJGL/GLFW.
 * Provides a static API for querying input state and virtual axes.
 * <p>
 * Features:
 * - Frame-perfect input detection (pressed vs held vs released)
 * - Smooth virtual axes with configurable interpolation
 * - Mouse position, delta, and scroll support
 * - Backend-agnostic design
 * <p>
 * Usage:
 * <pre>
 * // Initialize once at startup
 * InputManager.initialize(new LWJGLInputBackend());
 *
 * // In game loop, call once per frame
 * InputManager.update(deltaTime);
 *
 * // Query input in game code
 * if (InputManager.getKeyDown(KeyCode.SPACE)) {
 *     player.jump();
 * }
 *
 * float h = InputManager.getAxis("Horizontal");
 * float v = InputManager.getAxis("Vertical");
 * player.move(h, v);
 * </pre>
 */
public class InputManager {

    private static InputManager instance;

    // Backend for platform-specific key mapping
    private InputBackend backend;

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

    private InputManager(InputBackend backend) {
        this.backend = backend;
        registerDefaultAxes();
    }

    /**
     * Initializes the input manager with a backend.
     * Must be called before using any input functions.
     *
     * @param backend The input backend (e.g., LWJGLInputBackend)
     */
    public static void initialize(InputBackend backend) {
        if (instance != null) {
            System.err.println("WARNING: InputManager already initialized");
            return;
        }

        instance = new InputManager(backend);
        System.out.println("InputManager initialized with backend: " + backend.getClass().getSimpleName());
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
        // Horizontal: D/A + Right/Left arrows
        registerAxis("Horizontal", new AxisConfig(KeyCode.D, KeyCode.A)
                .withAltKeys(KeyCode.RIGHT, KeyCode.LEFT));

        // Vertical: W/S + Up/Down arrows
        registerAxis("Vertical", new AxisConfig(KeyCode.W, KeyCode.S)
                .withAltKeys(KeyCode.UP, KeyCode.DOWN));

        // Mouse axes (special handling in update)
        registerAxis("Mouse X", new AxisConfig(null, null));
        registerAxis("Mouse Y", new AxisConfig(null, null));

        System.out.println("Registered default axes: Horizontal, Vertical, Mouse X, Mouse Y");
    }

    // ========================================
    // Backend Event Methods (called by GLFW callbacks)
    // ========================================

    /**
     * Called when a key is pressed.
     * Should be called from GLFW key callback.
     */
    public static void onKeyPressed(int backendKeyCode) {
        InputManager manager = get();
        KeyCode key = manager.backend.mapKeyCode(backendKeyCode);

        if (key != KeyCode.UNKNOWN) {
            manager.keysPressed.add(key);
            manager.keysDown.add(key);
        }
    }

    /**
     * Called when a key is released.
     * Should be called from GLFW key callback.
     */
    public static void onKeyReleased(int backendKeyCode) {
        InputManager manager = get();
        KeyCode key = manager.backend.mapKeyCode(backendKeyCode);

        if (key != KeyCode.UNKNOWN) {
            manager.keysReleased.add(key);
            manager.keysDown.remove(key);
        }
    }

    /**
     * Called when mouse moves.
     * Should be called from GLFW cursor position callback.
     */
    public static void onMouseMoved(double x, double y) {
        InputManager manager = get();
        manager.mousePosition.x = (float) x;
        manager.mousePosition.y = (float) y;
    }

    /**
     * Called when mouse scrolls.
     * Should be called from GLFW scroll callback.
     */
    public static void onMouseScroll(double xOffset, double yOffset) {
        InputManager manager = get();
        manager.mouseScrollDelta = (float) yOffset;
    }

    // ========================================
    // Update (called once per frame)
    // ========================================

    /**
     * Updates input state. Call once per frame after polling GLFW events.
     *
     * @param deltaTime Time since last frame in seconds
     */
    public static void update(float deltaTime) {
        InputManager manager = get();

        // Clear frame-specific input
        manager.keysPressed.clear();
        manager.keysReleased.clear();

        // Calculate mouse delta
        manager.mouseDelta.x = manager.mousePosition.x - manager.lastMousePosition.x;
        manager.mouseDelta.y = manager.mousePosition.y - manager.lastMousePosition.y;
        manager.lastMousePosition.x = manager.mousePosition.x;
        manager.lastMousePosition.y = manager.mousePosition.y;

        // Update all axes
        manager.updateAxes(deltaTime);

        // Reset scroll (one frame only)
        manager.mouseScrollDelta = 0f;
    }

    /**
     * Updates all virtual axes with smooth interpolation.
     */
    private void updateAxes(float deltaTime) {
        for (Map.Entry<String, AxisConfig> entry : axisConfigs.entrySet()) {
            String axisName = entry.getKey();
            AxisConfig config = entry.getValue();

            float currentValue = axisValues.getOrDefault(axisName, 0f);
            float targetValue;

            // Special handling for mouse axes
            if (axisName.equals("Mouse X")) {
                targetValue = mouseDelta.x * config.sensitivity();
                axisValues.put(axisName, targetValue);
                continue;
            } else if (axisName.equals("Mouse Y")) {
                targetValue = mouseDelta.y * config.sensitivity();
                axisValues.put(axisName, targetValue);
                continue;
            }

            // Keyboard axis logic
            targetValue = 0f;

            // Check primary keys
            if (config.positiveKey() != null && getKey(config.positiveKey())) {
                targetValue += 1f;
            }
            if (config.negativeKey() != null && getKey(config.negativeKey())) {
                targetValue -= 1f;
            }

            // Check alternative keys
            if (config.altPositiveKey() != null && getKey(config.altPositiveKey())) {
                targetValue += 1f;
            }
            if (config.altNegativeKey() != null && getKey(config.altNegativeKey())) {
                targetValue -= 1f;
            }

            // Clamp to [-1, 1]
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
        }
    }

    /**
     * Moves a value towards target at specified speed.
     */
    private static float moveTowards(float current, float target, float maxDelta) {
        if (Math.abs(target - current) <= maxDelta) {
            return target;
        }
        return current + Math.signum(target - current) * maxDelta;
    }

    // ========================================
    // Keyboard Input API
    // ========================================

    /**
     * Returns true while the key is held down.
     */
    public static boolean getKey(KeyCode key) {
        return get().keysDown.contains(key);
    }

    /**
     * Returns true during the frame the key was pressed.
     */
    public static boolean getKeyDown(KeyCode key) {
        return get().keysPressed.contains(key);
    }

    /**
     * Returns true during the frame the key was released.
     */
    public static boolean getKeyUp(KeyCode key) {
        return get().keysReleased.contains(key);
    }

    /**
     * Returns true if any key is currently held down.
     */
    public static boolean anyKey() {
        return !get().keysDown.isEmpty();
    }

    /**
     * Returns true if any key was pressed this frame.
     */
    public static boolean anyKeyDown() {
        return !get().keysPressed.isEmpty();
    }

    // ========================================
    // Mouse Input API
    // ========================================

    /**
     * Gets the current mouse position in screen coordinates.
     */
    public static Vector2f getMousePosition() {
        return new Vector2f(get().mousePosition);
    }

    /**
     * Gets the mouse movement since last frame.
     */
    public static Vector2f getMouseDelta() {
        return new Vector2f(get().mouseDelta);
    }

    /**
     * Gets the mouse scroll delta (vertical only).
     */
    public static float getMouseScrollDelta() {
        return get().mouseScrollDelta;
    }

    /**
     * Sets whether the cursor should be locked/hidden.
     */
    public static void setCursorLocked(boolean locked) {
        get().cursorLocked = locked;
        // Note: Actual cursor locking should be done in GLFW
        // This is just for tracking state
    }

    /**
     * Gets whether the cursor is currently locked.
     */
    public static boolean isCursorLocked() {
        return get().cursorLocked;
    }

    // ========================================
    // Virtual Axes API
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

        if (config == null) {
            return 0f;
        }

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
        get().axisConfigs.put(axisName, config);
        get().axisValues.put(axisName, 0f);
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
        get().axisConfigs.remove(axisName);
        get().axisValues.remove(axisName);
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
        manager.keysDown.clear();
        manager.keysPressed.clear();
        manager.keysReleased.clear();
        manager.mouseDelta.x = 0;
        manager.mouseDelta.y = 0;
        manager.mouseScrollDelta = 0;

        for (String axis : manager.axisValues.keySet()) {
            manager.axisValues.put(axis, 0f);
        }
    }

    /**
     * Gets a human-readable name for a key.
     */
    public static String getKeyName(KeyCode key) {
        return get().backend.getKeyName(key);
    }

    /**
     * Gets all currently pressed keys.
     */
    public static Set<KeyCode> getPressedKeys() {
        return new HashSet<>(get().keysDown);
    }

    /**
     * Destroys the input manager.
     */
    public static void destroy() {
        if (instance != null) {
            instance = null;
            System.out.println("InputManager destroyed");
        }
    }
}