package com.pocket.rpg.platform.glfw;

import com.pocket.rpg.input.GamepadAxis;
import com.pocket.rpg.input.GamepadButton;
import com.pocket.rpg.input.events.InputEventBus;
import org.lwjgl.glfw.GLFWJoystickCallback;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

import static org.lwjgl.glfw.GLFW.*;

/**
 * Manages GLFW gamepad polling and state tracking.
 * Handles gamepad connection/disconnection, button states, and axis values.
 * <p>
 * Single threaded usage assumed.
 */
public class GLFWGamepadManager {

    private final InputEventBus eventBus;

    // Configuration
    private static final int GAMEPAD_ID = GLFW_JOYSTICK_1; // Track first gamepad
    private static final float AXIS_CHANGE_THRESHOLD = 0.001f;

    // Connection state
    private boolean wasGamepadConnected = false;

    // State tracking for edge detection
    private final boolean[] previousButtonStates = new boolean[15];
    private final float[] previousAxisValues = new float[6];

    // Joystick callback reference (to prevent GC)
    private GLFWJoystickCallback joystickCallback;

    /**
     * Creates a new gamepad manager.
     *
     * @param eventBus The event bus to dispatch gamepad events through
     */
    public GLFWGamepadManager(InputEventBus eventBus) {
        this.eventBus = eventBus;
    }

    /**
     * Initializes the gamepad manager.
     * Sets up callbacks and checks for connected gamepads.
     */
    public void initialize() {
        // Set joystick connection callback
        joystickCallback = new GLFWJoystickCallback() {
            @Override
            public void invoke(int jid, int event) {
                handleJoystickCallback(jid, event);
            }
        };
        glfwSetJoystickCallback(joystickCallback);

        // Check for gamepad on startup
        checkGamepadConnection();
    }

    /**
     * Polls the current gamepad state.
     * Should be called every frame, typically in the window's pollEvents() method.
     */
    public void pollGamepadState() {
        // Check if gamepad is still connected
        if (!glfwJoystickPresent(GAMEPAD_ID) || !glfwJoystickIsGamepad(GAMEPAD_ID)) {
            if (wasGamepadConnected) {
                wasGamepadConnected = false;
                System.out.println("Gamepad disconnected during gameplay");
            }
            return;
        }

        // Get gamepad state
        ByteBuffer buttons = glfwGetJoystickButtons(GAMEPAD_ID);
        FloatBuffer axes = glfwGetJoystickAxes(GAMEPAD_ID);

        if (buttons == null || axes == null) {
            return;
        }

        // Process buttons (edge detection)
        processGamepadButtons(buttons);

        // Process axes (continuous values)
        processGamepadAxes(axes);
    }

    /**
     * Cleans up resources.
     * Frees the joystick callback.
     */
    public void destroy() {
        if (joystickCallback != null) {
            joystickCallback.free();
            joystickCallback = null;
        }
    }

    // ========================================
    // Private Implementation
    // ========================================

    private void handleJoystickCallback(int jid, int event) {
        if (jid == GAMEPAD_ID) {
            if (event == GLFW_CONNECTED) {
                if (glfwJoystickIsGamepad(jid)) {
                    System.out.println("Gamepad connected: " + glfwGetGamepadName(jid));
                    wasGamepadConnected = true;
                } else {
                    System.out.println("Joystick connected but not recognized as gamepad: " +
                            glfwGetJoystickName(jid));
                }
            } else if (event == GLFW_DISCONNECTED) {
                System.out.println("Gamepad disconnected");
                wasGamepadConnected = false;
            }
        }
    }

    private void checkGamepadConnection() {
        if (glfwJoystickPresent(GAMEPAD_ID) && glfwJoystickIsGamepad(GAMEPAD_ID)) {
            wasGamepadConnected = true;
            System.out.println("Gamepad detected on startup: " + glfwGetGamepadName(GAMEPAD_ID));
        }
    }

    private void processGamepadButtons(ByteBuffer buttons) {
        // Map GLFW gamepad buttons to our GamepadButton enum
        // Uses standard gamepad mapping (Xbox/PlayStation layout)
        GamepadButton[] buttonMapping = {
                GamepadButton.A,                    // 0 - GLFW_GAMEPAD_BUTTON_A
                GamepadButton.B,                    // 1 - GLFW_GAMEPAD_BUTTON_B
                GamepadButton.X,                    // 2 - GLFW_GAMEPAD_BUTTON_X
                GamepadButton.Y,                    // 3 - GLFW_GAMEPAD_BUTTON_Y
                GamepadButton.LEFT_SHOULDER,        // 4 - GLFW_GAMEPAD_BUTTON_LEFT_BUMPER
                GamepadButton.RIGHT_SHOULDER,       // 5 - GLFW_GAMEPAD_BUTTON_RIGHT_BUMPER
                GamepadButton.BACK,                 // 6 - GLFW_GAMEPAD_BUTTON_BACK
                GamepadButton.START,                // 7 - GLFW_GAMEPAD_BUTTON_START
                GamepadButton.GUIDE,                // 8 - GLFW_GAMEPAD_BUTTON_GUIDE
                GamepadButton.LEFT_STICK_BUTTON,    // 9 - GLFW_GAMEPAD_BUTTON_LEFT_THUMB
                GamepadButton.RIGHT_STICK_BUTTON,   // 10 - GLFW_GAMEPAD_BUTTON_RIGHT_THUMB
                GamepadButton.DPAD_UP,              // 11 - GLFW_GAMEPAD_BUTTON_DPAD_UP
                GamepadButton.DPAD_RIGHT,           // 12 - GLFW_GAMEPAD_BUTTON_DPAD_RIGHT
                GamepadButton.DPAD_DOWN,            // 13 - GLFW_GAMEPAD_BUTTON_DPAD_DOWN
                GamepadButton.DPAD_LEFT             // 14 - GLFW_GAMEPAD_BUTTON_DPAD_LEFT
        };

        int buttonCount = Math.min(buttons.capacity(), buttonMapping.length);

        for (int i = 0; i < buttonCount; i++) {
            boolean isPressed = buttons.get(i) == GLFW_PRESS;
            boolean wasPressed = previousButtonStates[i];

            // Edge detection: dispatch events only on state changes
            if (isPressed && !wasPressed) {
                eventBus.dispatchGamepadButtonEvent(buttonMapping[i], true);
            } else if (!isPressed && wasPressed) {
                eventBus.dispatchGamepadButtonEvent(buttonMapping[i], false);
            }

            previousButtonStates[i] = isPressed;
        }
    }

    private void processGamepadAxes(FloatBuffer axes) {
        // Map GLFW gamepad axes to our GamepadAxis enum
        // GLFW standard gamepad mapping:
        // 0 = Left X, 1 = Left Y, 2 = Right X, 3 = Right Y, 4 = Left Trigger, 5 = Right Trigger

        if (axes.capacity() >= 6) {
            // Left stick X
            float leftX = axes.get(0);
            dispatchAxisIfChanged(GamepadAxis.LEFT_STICK_X, leftX, 0);

            // Left stick Y (invert because GLFW has -1 = up, +1 = down)
            float leftY = -axes.get(1);
            dispatchAxisIfChanged(GamepadAxis.LEFT_STICK_Y, leftY, 1);

            // Right stick X
            float rightX = axes.get(2);
            dispatchAxisIfChanged(GamepadAxis.RIGHT_STICK_X, rightX, 2);

            // Right stick Y (invert)
            float rightY = -axes.get(3);
            dispatchAxisIfChanged(GamepadAxis.RIGHT_STICK_Y, rightY, 3);

            // Left trigger (convert from -1..1 to 0..1)
            float leftTrigger = (axes.get(4) + 1.0f) / 2.0f;
            dispatchAxisIfChanged(GamepadAxis.LEFT_TRIGGER, leftTrigger, 4);

            // Right trigger (convert from -1..1 to 0..1)
            float rightTrigger = (axes.get(5) + 1.0f) / 2.0f;
            dispatchAxisIfChanged(GamepadAxis.RIGHT_TRIGGER, rightTrigger, 5);
        }
    }

    private void dispatchAxisIfChanged(GamepadAxis axis, float value, int index) {
        // Only dispatch if value changed significantly (avoid spam)
        if (Math.abs(value - previousAxisValues[index]) > AXIS_CHANGE_THRESHOLD) {
            eventBus.dispatchGamepadAxisEvent(axis, value);
            previousAxisValues[index] = value;
        }
    }

    // ========================================
    // Query Methods (optional, for debugging)
    // ========================================

    /**
     * Checks if a gamepad is currently connected.
     *
     * @return true if a gamepad is connected and recognized
     */
    public boolean isGamepadConnected() {
        return wasGamepadConnected;
    }

    /**
     * Gets the name of the connected gamepad.
     *
     * @return gamepad name, or null if not connected
     */
    public String getGamepadName() {
        if (glfwJoystickPresent(GAMEPAD_ID) && glfwJoystickIsGamepad(GAMEPAD_ID)) {
            return glfwGetGamepadName(GAMEPAD_ID);
        }
        return null;
    }
}