package com.pocket.rpg.input.callbacks;


import com.pocket.rpg.input.KeyCode;
import com.pocket.rpg.input.events.KeyEvent;
import com.pocket.rpg.input.events.MouseButtonEvent;

/**
 * Collection of input callback interfaces for handling various input events.
 * Each interface defines a single method to be implemented for responding to specific input events.
 */
public final class InputCallbacks {

    // Prevent instantiation
    private InputCallbacks() {
        throw new AssertionError("Cannot instantiate InputCallbacks");
    }

    /**
     * Callback for keyboard input events.
     * Called when a key is pressed, repeated, or released.
     */
    @FunctionalInterface
    public interface KeyCallback {
        /**
         * Invoked when a key event occurs.
         *
         * @param key    the key code of the key event.
         *               See {@link KeyCode} for common key codes.
         * @param action the action performed on the key.
         *               See {@link KeyEvent.Action} for possible actions.
         */
        void onKey(KeyCode key, KeyEvent.Action action);
    }

    /**
     * Callback for mouse button events.
     * Called when a mouse button is pressed or released.
     */
    @FunctionalInterface
    public interface MouseButtonCallback {
        /**
         * Invoked when a mouse button event occurs.
         *
         * @param button the mouse button involved in the event.
         *               See {@link KeyCode} for common mouse button codes.
         * @param action the action performed on the mouse button.
         *               See {@link MouseButtonEvent.Action} for possible actions.
         */
        void onMouseButton(KeyCode button, MouseButtonEvent.Action action);
    }

    /**
     * Callback for mouse cursor movement.
     * Called when the mouse cursor moves within the window.
     */
    @FunctionalInterface
    public interface MouseMoveCallback {
        /**
         * Invoked when the mouse cursor moves.
         *
         * <p>The callback receives the cursor position in screen coordinates,
         * relative to the top-left corner of the window content area.
         * On platforms that provide it, the full sub-pixel cursor position is passed.</p>
         *
         * @param x the new cursor x-coordinate, relative to the left edge of the content area
         * @param y the new cursor y-coordinate, relative to the top edge of the content area
         */
        void onMouseMove(double x, double y);
    }

    /**
     * Callback for mouse scroll events.
     * Called when a scrolling device is used (mouse wheel, touchpad scroll area, etc.).
     */
    @FunctionalInterface
    public interface MouseScrollCallback {
        /**
         * Invoked when the mouse wheel is scrolled.
         *
         * @param xOffset the scroll offset along the x-axis (horizontal scroll)
         * @param yOffset the scroll offset along the y-axis (vertical scroll).
         *                Positive values indicate scrolling up/away from the user.
         */
        void onMouseScroll(double xOffset, double yOffset);
    }

    /**
     * Callback for window resize events.
     * Called when the window is resized by the user or programmatically.
     */
    @FunctionalInterface
    public interface WindowResizeCallback {
        /**
         * Invoked when the window is resized.
         *
         * @param width  the new width, in screen coordinates (pixels), of the window
         * @param height the new height, in screen coordinates (pixels), of the window
         */
        void onWindowResize(int width, int height);
    }
}