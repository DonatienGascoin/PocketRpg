package com.pocket.rpg.input.listeners;

import com.pocket.rpg.input.KeyCode;
import com.pocket.rpg.input.events.KeyEvent;

import java.util.EnumSet;
import java.util.Set;

/**
 * Platform-agnostic key listener.
 * Tracks key states using KeyCode enum instead of backend-specific key codes.
 *
 * <h3>Key States:</h3>
 * <ul>
 *   <li><b>Held:</b> Key is currently being held down</li>
 *   <li><b>Pressed:</b> Key was just pressed this frame (edge detection)</li>
 *   <li><b>Released:</b> Key was just released this frame (edge detection)</li>
 * </ul>
 *
 * <h3>Usage:</h3>
 * <pre>{@code
 * KeyListener keyListener = new KeyListener();
 *
 * // In GLFW callback or backend event
 * keyListener.onKeyPressed(KeyCode.W);
 *
 * // Query state
 * if (keyListener.isKeyHeld(KeyCode.W)) {
 *     // Move forward
 * }
 *
 * // Call at end of frame
 * keyListener.endFrame();
 * }</pre>
 * <p>
 * Single threaded usage assumed.
 */
public class KeyListener {

    // Use EnumSet for efficient storage and lookup
    private final Set<KeyCode> keysHeld = EnumSet.noneOf(KeyCode.class);
    private final Set<KeyCode> keysPressed = EnumSet.noneOf(KeyCode.class);
    private final Set<KeyCode> keysReleased = EnumSet.noneOf(KeyCode.class);

    /**
     * Invoked by the input backend when a key event occurs.
     *
     * @param key    the key code of the key event
     * @param action the action (PRESS or RELEASE)
     */

    public void onKey(KeyCode key, KeyEvent.Action action) {
        switch (action) {
            case PRESS -> onKeyPressed(key);
            case RELEASE -> onKeyReleased(key);
        }
    }

    /**
     * Called when a key is pressed.
     * Should be called from backend input callbacks.
     *
     * @param key the key that was pressed
     */
    public void onKeyPressed(KeyCode key) {
        if (key == null || key == KeyCode.UNKNOWN) {
            return;
        }

        // Only mark as "pressed" if it wasn't already held
        if (!keysHeld.contains(key)) {
            keysPressed.add(key);
        }

        keysHeld.add(key);
    }

    /**
     * Called when a key is released.
     * Should be called from backend input callbacks.
     *
     * @param key the key that was released
     */
    public void onKeyReleased(KeyCode key) {
        if (key == null || key == KeyCode.UNKNOWN) {
            return;
        }

        keysReleased.add(key);
        keysHeld.remove(key);
        keysPressed.remove(key); // In case it was pressed and released same frame
    }

    /**
     * Clears frame-specific state.
     * Must be called at the end of each frame.
     */
    public void endFrame() {
        keysPressed.clear();
        keysReleased.clear();
    }

    /**
     * Checks if a key is currently being held down.
     *
     * @param key the key to check
     * @return true if the key is held down
     */
    public boolean isKeyHeld(KeyCode key) {
        if (key == null || key == KeyCode.UNKNOWN) {
            return false;
        }
        return keysHeld.contains(key);
    }

    /**
     * Checks if a key was pressed this frame (edge detection).
     * Returns true only on the frame the key was first pressed.
     *
     * @param key the key to check
     * @return true if the key was pressed this frame
     */
    public boolean wasKeyPressed(KeyCode key) {
        if (key == null || key == KeyCode.UNKNOWN) {
            return false;
        }
        return keysPressed.contains(key);
    }

    /**
     * Checks if a key was released this frame (edge detection).
     * Returns true only on the frame the key was released.
     *
     * @param key the key to check
     * @return true if the key was released this frame
     */
    public boolean wasKeyReleased(KeyCode key) {
        if (key == null || key == KeyCode.UNKNOWN) {
            return false;
        }
        return keysReleased.contains(key);
    }

    /**
     * Checks if any key is currently held down.
     *
     * @return true if at least one key is held
     */
    public boolean isAnyKeyHeld() {
        return !keysHeld.isEmpty();
    }

    /**
     * Checks if any key was pressed this frame.
     *
     * @return true if at least one key was pressed this frame
     */
    public boolean wasAnyKeyPressed() {
        return !keysPressed.isEmpty();
    }

    /**
     * Checks if a modifier key is currently held.
     *
     * @return true if shift (left or right) is held
     */
    public boolean isShiftHeld() {
        return keysHeld.contains(KeyCode.LEFT_SHIFT) ||
                keysHeld.contains(KeyCode.RIGHT_SHIFT);
    }

    /**
     * Checks if a modifier key is currently held.
     *
     * @return true if control (left or right) is held
     */
    public boolean isControlHeld() {
        return keysHeld.contains(KeyCode.LEFT_CONTROL) ||
                keysHeld.contains(KeyCode.RIGHT_CONTROL);
    }

    /**
     * Checks if a modifier key is currently held.
     *
     * @return true if alt (left or right) is held
     */
    public boolean isAltHeld() {
        return keysHeld.contains(KeyCode.LEFT_ALT) ||
                keysHeld.contains(KeyCode.RIGHT_ALT);
    }

    /**
     * Clears all key states.
     * Useful for scene transitions or when focus is lost.
     */
    public void clear() {
        keysHeld.clear();
        keysPressed.clear();
        keysReleased.clear();
    }
}