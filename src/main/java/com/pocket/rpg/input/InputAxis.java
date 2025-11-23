package com.pocket.rpg.input;

/**
 * Enum defining input axes.
 * Axes combine two actions (negative/positive) to produce a value from -1 to 1.
 */
public enum InputAxis {
    HORIZONTAL(InputAction.MOVE_LEFT, InputAction.MOVE_RIGHT),
    VERTICAL(InputAction.MOVE_DOWN, InputAction.MOVE_UP),
    ROTATION(InputAction.ROTATE_LEFT, InputAction.ROTATE_RIGHT),
    SCALE(InputAction.SCALE_DOWN, InputAction.SCALE_UP),

    // Gamepad axes (can be added later)
    GAMEPAD_LEFT_STICK_HORIZONTAL(null, null),
    GAMEPAD_LEFT_STICK_VERTICAL(null, null),
    GAMEPAD_RIGHT_STICK_HORIZONTAL(null, null),
    GAMEPAD_RIGHT_STICK_VERTICAL(null, null);

    private final InputAction negativeAction;
    private final InputAction positiveAction;

    InputAxis(InputAction negativeAction, InputAction positiveAction) {
        this.negativeAction = negativeAction;
        this.positiveAction = positiveAction;
    }

    public InputAction getNegativeAction() {
        return negativeAction;
    }

    public InputAction getPositiveAction() {
        return positiveAction;
    }

    public boolean isActionBased() {
        return negativeAction != null && positiveAction != null;
    }

    public boolean isGamepadAxis() {
        return name().startsWith("GAMEPAD_");
    }
}