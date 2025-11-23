package com.pocket.rpg.input;

/**
 * Tracks the complete state of an action across frames.
 * Provides isPressedThisFrame (rising edge), isPressed (held), and isReleased (falling edge).
 */
class ActionState {
    private boolean currentlyPressed = false;
    private boolean pressedLastFrame = false;

    public static final ActionState INACTIVE = new ActionState();

    /**
     * Update state - call once per frame.
     */
    public void update(boolean isPressed) {
        pressedLastFrame = currentlyPressed;
        currentlyPressed = isPressed;
    }

    /**
     * Returns true only on the frame the action was pressed.
     * Rising edge detection.
     */
    public boolean isPressedThisFrame() {
        return currentlyPressed && !pressedLastFrame;
    }

    /**
     * Returns true while the action is held down.
     */
    public boolean isPressed() {
        return currentlyPressed;
    }

    /**
     * Returns true only on the frame the action was released.
     * Falling edge detection.
     */
    public boolean isReleased() {
        return !currentlyPressed && pressedLastFrame;
    }
}