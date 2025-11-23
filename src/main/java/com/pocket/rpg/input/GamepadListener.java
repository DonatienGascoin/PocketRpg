package com.pocket.rpg.input;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.glfw.GLFW.*;

/**
 * Handles gamepad input state tracking and action mapping.
 * Future implementation - framework is ready for gamepad support.
 */
public class GamepadListener {
    private int joystickId;
    private InputConfig config;

    // Button states
    private Map<Integer, ActionState> buttonStates = new HashMap<>();
    private Map<InputAction, ActionState> gamepadActionStates = new HashMap<>();

    // Axis values
    private Map<Integer, Float> axisValues = new HashMap<>();

    // Deadzone for analog sticks
    private float deadzone = 0.15f;

    // Standard gamepad axis indices (Xbox/PlayStation layout)
    public static final int AXIS_LEFT_X = 0;
    public static final int AXIS_LEFT_Y = 1;
    public static final int AXIS_RIGHT_X = 2;
    public static final int AXIS_RIGHT_Y = 3;
    public static final int AXIS_LEFT_TRIGGER = 4;
    public static final int AXIS_RIGHT_TRIGGER = 5;

    public GamepadListener(int joystickId, InputConfig config) {
        this.joystickId = joystickId;
        this.config = config;
    }

    /**
     * Poll gamepad state - called once per frame.
     */
    public void poll() {
        if (!glfwJoystickPresent(joystickId)) {
            return; // Gamepad disconnected
        }

        // Update axes
        FloatBuffer axes = glfwGetJoystickAxes(joystickId);
        if (axes != null) {
            for (int i = 0; i < axes.limit(); i++) {
                float value = axes.get(i);
                // Apply deadzone
                if (Math.abs(value) < deadzone) {
                    value = 0f;
                }
                axisValues.put(i, value);
            }
        }

        // Update button states
        ByteBuffer buttons = glfwGetJoystickButtons(joystickId);
        if (buttons != null) {
            for (int i = 0; i < buttons.limit(); i++) {
                ActionState state = buttonStates.computeIfAbsent(i, k -> new ActionState());
                boolean pressed = buttons.get(i) == GLFW_PRESS;
                state.update(pressed);
            }
        }

        // Update all GAMEPAD actions based on current bindings
        for (InputAction action : InputAction.values()) {
            // TYPE-SAFE CHECK - NO MAGIC STRING!
            if (!action.isGamepadAction()) {
                continue; // Skip non-gamepad actions
            }

            int boundButton = config.getBindingForAction(action);
            ActionState buttonState = buttonStates.computeIfAbsent(boundButton, k -> new ActionState());

            ActionState actionState = gamepadActionStates.computeIfAbsent(action, a -> new ActionState());
            actionState.update(buttonState.isPressed());
        }
    }

    // ======================================================================
    // AXIS API
    // ======================================================================

    public float getAxisRaw(int axisIndex) {
        return axisValues.getOrDefault(axisIndex, 0f);
    }

    // ======================================================================
    // BUTTON API
    // ======================================================================

    public boolean isButtonPressedThisFrame(int button) {
        return buttonStates.getOrDefault(button, ActionState.INACTIVE).isPressedThisFrame();
    }

    public boolean isButtonPressed(int button) {
        return buttonStates.getOrDefault(button, ActionState.INACTIVE).isPressed();
    }

    public boolean isButtonReleased(int button) {
        return buttonStates.getOrDefault(button, ActionState.INACTIVE).isReleased();
    }

    // ======================================================================
    // ACTION API
    // ======================================================================

    public boolean isActionPressedThisFrame(InputAction action) {
        if (!action.isGamepadAction()) {
            System.err.println("WARNING: " + action + " is not a gamepad action!");
            return false;
        }
        return gamepadActionStates.getOrDefault(action, ActionState.INACTIVE).isPressedThisFrame();
    }

    public boolean isActionPressed(InputAction action) {
        if (!action.isGamepadAction()) {
            System.err.println("WARNING: " + action + " is not a gamepad action!");
            return false;
        }
        return gamepadActionStates.getOrDefault(action, ActionState.INACTIVE).isPressed();
    }

    public boolean isActionReleased(InputAction action) {
        if (!action.isGamepadAction()) {
            System.err.println("WARNING: " + action + " is not a gamepad action!");
            return false;
        }
        return gamepadActionStates.getOrDefault(action, ActionState.INACTIVE).isReleased();
    }

    // ======================================================================
    // UTILITY
    // ======================================================================

    public boolean isConnected() {
        return glfwJoystickPresent(joystickId);
    }

    public String getName() {
        return glfwGetJoystickName(joystickId);
    }

    public void setDeadzone(float deadzone) {
        this.deadzone = deadzone;
    }

    public float getDeadzone() {
        return deadzone;
    }
}