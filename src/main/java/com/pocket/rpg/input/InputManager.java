package com.pocket.rpg.input;

import org.joml.Vector2f;

import static org.lwjgl.glfw.GLFW.*;

/**
 * Static facade for the input system.
 * Provides simple access to keyboard, mouse, and gamepad input.
 *
 * Usage:
 *   InputManager.initialize(windowHandle);
 *
 *   // In game loop
 *   InputManager.poll();
 *
 *   // In components
 *   if (InputManager.isActionPressed(InputAction.JUMP)) {
 *       jump();
 *   }
 */
public class InputManager {
    private static InputManager instance;

    private KeyListener keyListener;
    private MouseListener mouseListener;
    private GamepadListener gamepadListener;
    private InputConfig config;

    private InputManager() {}

    /**
     * Initialize the InputManager with GLFW window handle.
     * Call this after GLFW window creation.
     */
    public static void initialize(long windowHandle) {
        instance = new InputManager();
        instance.config = new InputConfig();
        instance.keyListener = new KeyListener(windowHandle, instance.config);
        instance.mouseListener = new MouseListener(windowHandle, instance.config);

        // Try to initialize gamepad
        if (glfwJoystickPresent(GLFW_JOYSTICK_1)) {
            instance.gamepadListener = new GamepadListener(GLFW_JOYSTICK_1, instance.config);
            System.out.println("Gamepad connected: " + instance.gamepadListener.getName());
        }

        System.out.println("InputManager initialized");
    }

    /**
     * Poll all input devices - call once per frame BEFORE game update.
     */
    public static void poll() {
        if (instance == null) {
            System.err.println("ERROR: InputManager not initialized!");
            return;
        }

        instance.keyListener.poll();
        instance.mouseListener.poll();

        if (instance.gamepadListener != null) {
            instance.gamepadListener.poll();
        }
    }

    // ======================================================================
    // ACTION API - Works across all input types
    // ======================================================================

    /**
     * Returns true only on the frame the action was pressed.
     * Use this for single-press actions like jumping or shooting.
     */
    public static boolean isActionPressedThisFrame(InputAction action) {
        if (instance == null) return false;

        switch (action.getInputType()) {
            case KEYBOARD:
                return instance.keyListener.isActionPressedThisFrame(action);
            case MOUSE:
                return instance.mouseListener.isActionPressedThisFrame(action);
            case GAMEPAD:
                return instance.gamepadListener != null &&
                        instance.gamepadListener.isActionPressedThisFrame(action);
            default:
                return false;
        }
    }

    /**
     * Returns true while the action is held down.
     * Use this for continuous actions like moving or aiming.
     */
    public static boolean isActionPressed(InputAction action) {
        if (instance == null) return false;

        switch (action.getInputType()) {
            case KEYBOARD:
                return instance.keyListener.isActionPressed(action);
            case MOUSE:
                return instance.mouseListener.isActionPressed(action);
            case GAMEPAD:
                return instance.gamepadListener != null &&
                        instance.gamepadListener.isActionPressed(action);
            default:
                return false;
        }
    }

    /**
     * Returns true only on the frame the action was released.
     * Use this for charge-up mechanics or detecting release events.
     */
    public static boolean isActionReleased(InputAction action) {
        if (instance == null) return false;

        switch (action.getInputType()) {
            case KEYBOARD:
                return instance.keyListener.isActionReleased(action);
            case MOUSE:
                return instance.mouseListener.isActionReleased(action);
            case GAMEPAD:
                return instance.gamepadListener != null &&
                        instance.gamepadListener.isActionReleased(action);
            default:
                return false;
        }
    }

    // ======================================================================
    // AXIS API - Simplified interface
    // ======================================================================

    /**
     * Get horizontal axis value (-1 = left, 1 = right, 0 = neutral).
     */
    public static float getHorizontal() {
        return getAxis(InputAxis.HORIZONTAL);
    }

    /**
     * Get vertical axis value (-1 = down, 1 = up, 0 = neutral).
     */
    public static float getVertical() {
        return getAxis(InputAxis.VERTICAL);
    }

    /**
     * Get movement axis as Vector2f (x = horizontal, y = vertical).
     * Convenient for 2D movement.
     */
    public static Vector2f getMovementAxis() {
        return new Vector2f(getHorizontal(), getVertical());
    }

    /**
     * Get named axis value.
     */
    public static float getAxis(InputAxis axis) {
        if (instance == null) return 0f;

        // Action-based axes (keyboard)
        if (axis.isActionBased()) {
            return instance.keyListener.getAxis(
                    axis.getNegativeAction(),
                    axis.getPositiveAction()
            );
        }

        // Gamepad axes
        if (axis.isGamepadAxis() && instance.gamepadListener != null) {
            switch (axis) {
                case GAMEPAD_LEFT_STICK_HORIZONTAL:
                    return instance.gamepadListener.getAxisRaw(GamepadListener.AXIS_LEFT_X);
                case GAMEPAD_LEFT_STICK_VERTICAL:
                    return -instance.gamepadListener.getAxisRaw(GamepadListener.AXIS_LEFT_Y);
                case GAMEPAD_RIGHT_STICK_HORIZONTAL:
                    return instance.gamepadListener.getAxisRaw(GamepadListener.AXIS_RIGHT_X);
                case GAMEPAD_RIGHT_STICK_VERTICAL:
                    return -instance.gamepadListener.getAxisRaw(GamepadListener.AXIS_RIGHT_Y);
            }
        }

        return 0f;
    }

    // ======================================================================
    // RAW KEYBOARD API (for special cases)
    // ======================================================================

    public static boolean isKeyPressedThisFrame(int key) {
        if (instance == null) return false;
        return instance.keyListener.isKeyPressedThisFrame(key);
    }

    public static boolean isKeyPressed(int key) {
        if (instance == null) return false;
        return instance.keyListener.isKeyPressed(key);
    }

    public static boolean isKeyReleased(int key) {
        if (instance == null) return false;
        return instance.keyListener.isKeyReleased(key);
    }

    // ======================================================================
    // MOUSE API
    // ======================================================================

    public static Vector2f getMousePosition() {
        if (instance == null) return new Vector2f();
        return instance.mouseListener.getScreenPosition();
    }

    public static Vector2f getMouseDelta() {
        if (instance == null) return new Vector2f();
        return instance.mouseListener.getMouseDelta();
    }

    public static Vector2f getScrollDelta() {
        if (instance == null) return new Vector2f();
        return instance.mouseListener.getScrollDelta();
    }

    public static boolean isMouseButtonPressedThisFrame(int button) {
        if (instance == null) return false;
        return instance.mouseListener.isButtonPressedThisFrame(button);
    }

    public static boolean isMouseButtonPressed(int button) {
        if (instance == null) return false;
        return instance.mouseListener.isButtonPressed(button);
    }

    public static boolean isMouseButtonReleased(int button) {
        if (instance == null) return false;
        return instance.mouseListener.isButtonReleased(button);
    }

    // ======================================================================
    // GAMEPAD API
    // ======================================================================

    public static boolean isGamepadConnected() {
        return instance != null &&
                instance.gamepadListener != null &&
                instance.gamepadListener.isConnected();
    }

    public static float getGamepadAxis(int axisIndex) {
        if (instance == null || instance.gamepadListener == null) return 0f;
        return instance.gamepadListener.getAxisRaw(axisIndex);
    }

    public static boolean isGamepadButtonPressedThisFrame(int button) {
        if (instance == null || instance.gamepadListener == null) return false;
        return instance.gamepadListener.isButtonPressedThisFrame(button);
    }

    public static boolean isGamepadButtonPressed(int button) {
        if (instance == null || instance.gamepadListener == null) return false;
        return instance.gamepadListener.isButtonPressed(button);
    }

    public static boolean isGamepadButtonReleased(int button) {
        if (instance == null || instance.gamepadListener == null) return false;
        return instance.gamepadListener.isButtonReleased(button);
    }

    // ======================================================================
    // INPUT CONFIGURATION / REBINDING
    // ======================================================================

    public static void rebindAction(InputAction action, int newBinding) {
        if (instance == null) return;
        instance.config.bindAction(action, newBinding);
        System.out.println("Rebound " + action + " to binding " + newBinding);
    }

    public static int getBindingForAction(InputAction action) {
        if (instance == null) return action.getDefaultBinding();
        return instance.config.getBindingForAction(action);
    }

    public static void resetInputBindings() {
        if (instance == null) return;
        instance.config.resetToDefaults();
        System.out.println("Input bindings reset to defaults");
    }

    public static void saveInputConfig(String filepath) {
        if (instance == null) return;
        instance.config.save(filepath);
    }

    public static void loadInputConfig(String filepath) {
        if (instance == null) return;
        instance.config.load(filepath);
    }

    // ======================================================================
    // INTERNAL - Called by callbacks
    // ======================================================================

    /**
     * Called by GLFW scroll callback.
     * Package-private - only DefaultCallback should call this.
     */
    public static void notifyScroll(double xOffset, double yOffset) {
        if (instance != null && instance.mouseListener != null) {
            instance.mouseListener.onScroll(xOffset, yOffset);
        }
    }

    // ======================================================================
    // UTILITY
    // ======================================================================

    public static String getKeyName(int key) {
        String name = glfwGetKeyName(key, 0);
        return name != null ? name : "Unknown";
    }

    public static void destroy() {
        instance = null;
        System.out.println("InputManager destroyed");
    }
}