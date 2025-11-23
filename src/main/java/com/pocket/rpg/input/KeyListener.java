package com.pocket.rpg.input;

import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.glfw.GLFW.*;

/**
 * Handles keyboard input state tracking and action mapping.
 */
public class KeyListener {
    private long windowHandle;
    private InputConfig config;

    // Raw key states (GLFW keys -> state)
    private Map<Integer, ActionState> keyStates = new HashMap<>();

    // Action states (InputAction -> state)
    private Map<InputAction, ActionState> actionStates = new HashMap<>();

    public KeyListener(long windowHandle, InputConfig config) {
        this.windowHandle = windowHandle;
        this.config = config;
    }

    /**
     * Poll keyboard state - called once per frame BEFORE game update.
     */
    public void poll() {
        // Update all tracked raw keys
        for (Map.Entry<Integer, ActionState> entry : keyStates.entrySet()) {
            int key = entry.getKey();
            ActionState state = entry.getValue();
            boolean isPressed = glfwGetKey(windowHandle, key) == GLFW_PRESS;
            state.update(isPressed);
        }

        // Update all KEYBOARD actions based on current bindings
        for (InputAction action : InputAction.values()) {
            // TYPE-SAFE CHECK - NO MAGIC STRING!
            if (!action.isKeyboardAction()) {
                continue; // Skip non-keyboard actions
            }

            int boundKey = config.getBindingForAction(action);
            ActionState keyState = keyStates.computeIfAbsent(boundKey, k -> new ActionState());

            ActionState actionState = actionStates.computeIfAbsent(action, a -> new ActionState());
            actionState.update(keyState.isPressed());
        }
    }

    // ======================================================================
    // RAW KEY API (for special cases)
    // ======================================================================

    public boolean isKeyPressedThisFrame(int key) {
        return keyStates.getOrDefault(key, ActionState.INACTIVE).isPressedThisFrame();
    }

    public boolean isKeyPressed(int key) {
        return keyStates.getOrDefault(key, ActionState.INACTIVE).isPressed();
    }

    public boolean isKeyReleased(int key) {
        return keyStates.getOrDefault(key, ActionState.INACTIVE).isReleased();
    }

    // ======================================================================
    // ACTION API (RECOMMENDED)
    // ======================================================================

    public boolean isActionPressedThisFrame(InputAction action) {
        if (!action.isKeyboardAction()) {
            System.err.println("WARNING: " + action + " is not a keyboard action!");
            return false;
        }
        return actionStates.getOrDefault(action, ActionState.INACTIVE).isPressedThisFrame();
    }

    public boolean isActionPressed(InputAction action) {
        if (!action.isKeyboardAction()) {
            System.err.println("WARNING: " + action + " is not a keyboard action!");
            return false;
        }
        return actionStates.getOrDefault(action, ActionState.INACTIVE).isPressed();
    }

    public boolean isActionReleased(InputAction action) {
        if (!action.isKeyboardAction()) {
            System.err.println("WARNING: " + action + " is not a keyboard action!");
            return false;
        }
        return actionStates.getOrDefault(action, ActionState.INACTIVE).isReleased();
    }

    /**
     * Get axis value from two actions (-1 to 1).
     */
    public float getAxis(InputAction negative, InputAction positive) {
        float value = 0f;
        if (isActionPressed(negative)) value -= 1f;
        if (isActionPressed(positive)) value += 1f;
        return value;
    }

    /**
     * Track a key for raw queries (optional optimization).
     */
    public void trackKey(int key) {
        keyStates.putIfAbsent(key, new ActionState());
    }
}