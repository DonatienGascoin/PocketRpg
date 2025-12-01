package com.pocket.rpg.input;

import com.pocket.rpg.input.events.KeyEvent;
import com.pocket.rpg.input.events.MouseButtonEvent;

/**
 * Interface that any input backend (GLFW, LWJGL, SDL, etc.) must implement.
 */
public interface InputBackend {
    /**
     * Map a backend-specific key code to our abstract KeyCode enum.
     *
     * @param backendKeyCode The key code from the backend (e.g., GLFW key code)
     * @return The corresponding KeyCode, or KeyCode.UNKNOWN if not mapped
     */
    KeyCode getKeyCode(int backendKeyCode);

    /**
     * Map our abstract KeyCode to a backend-specific key code.
     *
     * @param keyCode The abstract KeyCode
     * @return The backend-specific key code, or -1 if not mapped
     */
    int mapToBackend(KeyCode keyCode);

    /**
     * Get a human-readable name for a key code.
     *
     * @param keyCode The KeyCode to get the name for
     * @return The name of the key (e.g., "Space", "Left Shift")
     */
    String getKeyName(KeyCode keyCode);

    /**
     * Get the action (PRESS, RELEASE) for a backend-specific key code.
     *
     * @param backendKeyCode The key code from the backend
     * @return The corresponding KeyEvent.Action
     */
    KeyEvent.Action getKeyAction(int backendKeyCode);

    /**
     * Get the action (PRESS, RELEASE) for a backend-specific mouse button code.
     *
     * @param backendKeyCode The mouse button code from the backend
     * @return The corresponding MouseButtonEvent.Action
     */
    MouseButtonEvent.Action getMouseButtonAction(int backendKeyCode);
}