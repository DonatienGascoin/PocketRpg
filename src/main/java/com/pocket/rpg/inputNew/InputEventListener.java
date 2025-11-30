package com.pocket.rpg.inputNew;

import com.pocket.rpg.inputNew.events.*;

/**
 * Interface for components that want to receive input events.
 * Implement only the methods you need - all have default empty implementations.
 *
 * <h3>Usage:</h3>
 * <pre>{@code
 * public class Player implements InputEventListener {
 *     @Override
 *     public void onKeyEvent(KeyEvent event) {
 *         if (event.isPress() && event.isKey(KeyCode.SPACE)) {
 *             jump();
 *         }
 *     }
 * }
 * }</pre>
 */
public interface InputEventListener {

    /**
     * Called when a key event occurs (press or release).
     * @param event the key event
     */
    default void onKeyEvent(KeyEvent event) {}

    /**
     * Called when a mouse button event occurs.
     * @param event the mouse button event
     */
    default void onMouseButtonEvent(MouseButtonEvent event) {}

    /**
     * Called when the mouse moves.
     * @param event the mouse move event
     */
    default void onMouseMoveEvent(MouseMoveEvent event) {}

    /**
     * Called when the mouse wheel is scrolled.
     * @param event the scroll event
     */
    default void onMouseScrollEvent(MouseScrollEvent event) {}

    /**
     * Called when a virtual axis value changes significantly.
     * @param event the axis event
     */
    default void onAxisEvent(AxisEvent event) {}

    /**
     * Return a priority for this listener.
     * Higher priority listeners are called first.
     * Default is 0 (normal priority).
     *
     * Examples:
     * - UI: 100 (high priority)
     * - Player: 0 (normal)
     * - Debug: -100 (low priority)
     */
    default int getPriority() {
        return 0;
    }

    /**
     * Optional: Return a name for this listener (useful for debugging).
     */
    default String getListenerName() {
        return getClass().getSimpleName();
    }
}