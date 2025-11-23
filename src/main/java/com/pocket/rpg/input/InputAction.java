package com.pocket.rpg.input;

import static org.lwjgl.glfw.GLFW.*;

/**
 * Enum defining all input actions in the game.
 * Each action knows its default binding and input type (keyboard/mouse/gamepad).
 * This provides type-safe input handling with no magic strings.
 */
public enum InputAction {
    // ==================== KEYBOARD ACTIONS ====================
    // Movement
    MOVE_UP(GLFW_KEY_W, InputType.KEYBOARD),
    MOVE_DOWN(GLFW_KEY_S, InputType.KEYBOARD),
    MOVE_LEFT(GLFW_KEY_A, InputType.KEYBOARD),
    MOVE_RIGHT(GLFW_KEY_D, InputType.KEYBOARD),

    // Actions
    JUMP(GLFW_KEY_SPACE, InputType.KEYBOARD),
    INTERACT(GLFW_KEY_E, InputType.KEYBOARD),
    ATTACK(GLFW_KEY_LEFT_SHIFT, InputType.KEYBOARD),
    SPRINT(GLFW_KEY_LEFT_CONTROL, InputType.KEYBOARD),

    // UI
    PAUSE(GLFW_KEY_ESCAPE, InputType.KEYBOARD),
    INVENTORY(GLFW_KEY_I, InputType.KEYBOARD),
    MAP(GLFW_KEY_M, InputType.KEYBOARD),

    // Testing
    ROTATE_LEFT(GLFW_KEY_Q, InputType.KEYBOARD),
    ROTATE_RIGHT(GLFW_KEY_E, InputType.KEYBOARD),
    SCALE_UP(GLFW_KEY_EQUAL, InputType.KEYBOARD),
    SCALE_DOWN(GLFW_KEY_MINUS, InputType.KEYBOARD),

    // ==================== MOUSE ACTIONS ====================
    MOUSE_PRIMARY(GLFW_MOUSE_BUTTON_LEFT, InputType.MOUSE),
    MOUSE_SECONDARY(GLFW_MOUSE_BUTTON_RIGHT, InputType.MOUSE),
    MOUSE_MIDDLE(GLFW_MOUSE_BUTTON_MIDDLE, InputType.MOUSE),

    // ==================== GAMEPAD ACTIONS ====================
    GAMEPAD_A(0, InputType.GAMEPAD),
    GAMEPAD_B(1, InputType.GAMEPAD),
    GAMEPAD_X(2, InputType.GAMEPAD),
    GAMEPAD_Y(3, InputType.GAMEPAD),
    GAMEPAD_LB(4, InputType.GAMEPAD),
    GAMEPAD_RB(5, InputType.GAMEPAD),
    GAMEPAD_START(9, InputType.GAMEPAD),
    GAMEPAD_SELECT(8, InputType.GAMEPAD);

    private final int defaultBinding;
    private final InputType inputType;

    InputAction(int defaultBinding, InputType inputType) {
        this.defaultBinding = defaultBinding;
        this.inputType = inputType;
    }

    public int getDefaultBinding() {
        return defaultBinding;
    }

    public InputType getInputType() {
        return inputType;
    }

    // Type checking methods - NO MAGIC STRINGS!
    public boolean isKeyboardAction() {
        return inputType == InputType.KEYBOARD;
    }

    public boolean isMouseAction() {
        return inputType == InputType.MOUSE;
    }

    public boolean isGamepadAction() {
        return inputType == InputType.GAMEPAD;
    }
}