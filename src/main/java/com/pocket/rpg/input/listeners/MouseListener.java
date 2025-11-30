package com.pocket.rpg.input.listeners;

import com.pocket.rpg.input.KeyCode;
import com.pocket.rpg.input.callbacks.InputCallbacks;
import com.pocket.rpg.input.events.MouseButtonEvent;
import lombok.Getter;
import org.joml.Vector2f;

import java.util.EnumSet;
import java.util.Set;

/**
 * Platform-agnostic mouse listener.
 * Tracks mouse state using KeyCode enum for buttons instead of backend-specific codes.
 *
 * <h3>Features:</h3>
 * <ul>
 *   <li>Mouse button states (held, pressed, released)</li>
 *   <li>Mouse position tracking</li>
 *   <li>Mouse delta (movement since last frame)</li>
 *   <li>Mouse scroll tracking</li>
 *   <li>Drag detection</li>
 * </ul>
 *
 * <h3>Usage:</h3>
 * <pre>{@code
 * MouseListener mouseListener = new MouseListener();
 *
 * // In backend callbacks
 * mouseListener.onMouseMoved(mouseX, mouseY);
 * mouseListener.onMouseButtonPressed(KeyCode.MOUSE_BUTTON_LEFT);
 * mouseListener.onMouseScroll(0, scrollY);
 *
 * // Query state
 * if (mouseListener.isButtonHeld(KeyCode.MOUSE_BUTTON_LEFT)) {
 *     // Handle click
 * }
 *
 * // Call at end of frame
 * mouseListener.endFrame();
 * }</pre>
 */
public class MouseListener implements InputCallbacks.MouseMoveCallback, InputCallbacks.MouseButtonCallback, InputCallbacks.MouseScrollCallback {
    // Mouse button states - using KeyCode for buttons
    private final Set<KeyCode> buttonsHeld = EnumSet.noneOf(KeyCode.class);
    private final Set<KeyCode> buttonsPressed = EnumSet.noneOf(KeyCode.class);
    private final Set<KeyCode> buttonsReleased = EnumSet.noneOf(KeyCode.class);

    // Mouse position
    @Getter
    private double xPos, yPos;
    @Getter
    private double lastX, lastY;

    // Mouse scroll
    @Getter
    private double scrollX, scrollY;

    /**
     * Called when a mouse button event occurs.
     * @param button the mouse button involved in the event.
     *               See {@link KeyCode} for common mouse button codes.
     * @param action the action performed on the mouse button.
     *               See {@link MouseButtonEvent.Action} for possible actions.
     */
    @Override
    public void onMouseButton(KeyCode button, MouseButtonEvent.Action action) {
        switch (action) {
            case PRESS -> onMouseButtonPressed(button);
            case RELEASE -> onMouseButtonReleased(button);
        }
    }

    /**
     * Called when the mouse is moved.
     * @param x the new cursor x-coordinate, relative to the left edge of the content area
     * @param y the new cursor y-coordinate, relative to the top edge of the content area
     */
    @Override
    public void onMouseMove(double x, double y) {
        this.lastX = this.xPos;
        this.lastY = this.yPos;

        // Update to new position
        this.xPos = x;
        this.yPos = y;
    }

    /**
     * Called when the mouse wheel is scrolled.
     *
     * @param xOffset horizontal scroll offset
     * @param yOffset vertical scroll offset (positive = scroll up)
     */
    @Override
    public void onMouseScroll(double xOffset, double yOffset) {
        this.scrollX = xOffset;
        this.scrollY = yOffset;
    }

    /**
     * Called when a mouse button is pressed.
     *
     * @param button the button that was pressed (e.g., KeyCode.MOUSE_BUTTON_LEFT)
     */
    public void onMouseButtonPressed(KeyCode button) {
        if (!isMouseButton(button)) {
            return;
        }

        // Only mark as "pressed" if it wasn't already held
        if (!buttonsHeld.contains(button)) {
            buttonsPressed.add(button);
        }

        buttonsHeld.add(button);
    }

    /**
     * Called when a mouse button is released.
     *
     * @param button the button that was released
     */
    public void onMouseButtonReleased(KeyCode button) {
        if (!isMouseButton(button)) {
            return;
        }

        buttonsReleased.add(button);
        buttonsHeld.remove(button);
        buttonsPressed.remove(button); // In case pressed and released same frame
    }

    /**
     * Clears frame-specific state.
     * Must be called at the end of each frame.
     */
    public void endFrame() {
        // Clear frame-specific button states
        buttonsPressed.clear();
        buttonsReleased.clear();

        // Clear scroll (scroll is only valid for one frame)
        scrollX = 0;
        scrollY = 0;
    }

    // ========================================
    // Button State Queries
    // ========================================

    /**
     * Checks if a mouse button is currently being held down.
     *
     * @param button the button to check
     * @return true if the button is held down
     */
    public boolean isButtonHeld(KeyCode button) {
        if (!isMouseButton(button)) {
            return false;
        }
        return buttonsHeld.contains(button);
    }

    /**
     * Checks if a mouse button was pressed this frame (edge detection).
     *
     * @param button the button to check
     * @return true if the button was pressed this frame
     */
    public boolean wasButtonPressed(KeyCode button) {
        if (!isMouseButton(button)) {
            return false;
        }
        return buttonsPressed.contains(button);
    }

    /**
     * Checks if a mouse button was released this frame (edge detection).
     *
     * @param button the button to check
     * @return true if the button was released this frame
     */
    public boolean wasButtonReleased(KeyCode button) {
        if (!isMouseButton(button)) {
            return false;
        }
        return buttonsReleased.contains(button);
    }

    /**
     * Convenience method to check if left mouse button is held.
     */
    public boolean isLeftButtonHeld() {
        return isButtonHeld(KeyCode.MOUSE_BUTTON_LEFT);
    }

    /**
     * Convenience method to check if right mouse button is held.
     */
    public boolean isRightButtonHeld() {
        return isButtonHeld(KeyCode.MOUSE_BUTTON_RIGHT);
    }

    /**
     * Convenience method to check if middle mouse button is held.
     */
    public boolean isMiddleButtonHeld() {
        return isButtonHeld(KeyCode.MOUSE_BUTTON_MIDDLE);
    }

    /**
     * Convenience method to check if left mouse button was pressed this frame.
     */
    public boolean wasLeftButtonPressed() {
        return wasButtonPressed(KeyCode.MOUSE_BUTTON_LEFT);
    }

    /**
     * Convenience method to check if right mouse button was pressed this frame.
     */
    public boolean wasRightButtonPressed() {
        return wasButtonPressed(KeyCode.MOUSE_BUTTON_RIGHT);
    }

    /**
     * Checks if any mouse button is currently held.
     */
    public boolean isAnyButtonHeld() {
        return !buttonsHeld.isEmpty();
    }

    // ========================================
    // Position Queries
    // ========================================

    /**
     * Gets the current mouse position as a vector.
     *
     * @return mouse position (defensive copy)
     */
    public Vector2f getMousePosition() {
        return new Vector2f((float) xPos, (float) yPos);
    }

    /**
     * Gets the mouse movement since last frame.
     *
     * @return delta movement (defensive copy)
     */
    public Vector2f getMouseDelta() {
        return new Vector2f((float) (xPos - lastX), (float) (yPos - lastY));
    }

    /**
     * Gets the mouse position in screen coordinates.
     *
     * @return array [x, y]
     */
    public double[] getPosition() {
        return new double[]{xPos, yPos};
    }

    /**
     * Gets the mouse delta in screen coordinates.
     *
     * @return array [deltaX, deltaY]
     */
    public double[] getDelta() {
        return new double[]{xPos - lastX, yPos - lastY};
    }

    /**
     * Checks if the mouse has moved since last frame.
     *
     * @return true if mouse position changed
     */
    public boolean hasMoved() {
        return xPos != lastX || yPos != lastY;
    }

    /**
     * Gets the distance the mouse moved since last frame.
     *
     * @return distance in pixels
     */
    public double getMovementDistance() {
        double dx = xPos - lastX;
        double dy = yPos - lastY;
        return Math.sqrt(dx * dx + dy * dy);
    }

    // ========================================
    // Scroll Queries
    // ========================================

    /**
     * Gets the mouse scroll delta as a vector.
     * Note: Scroll delta is only valid for one frame.
     *
     * @return scroll delta (defensive copy)
     */
    public Vector2f getScrollDelta() {
        return new Vector2f((float) scrollX, (float) scrollY);
    }

    /**
     * Checks if the mouse was scrolled this frame.
     *
     * @return true if scroll delta is non-zero
     */
    public boolean wasScrolled() {
        return scrollX != 0 || scrollY != 0;
    }

    /**
     * Checks if the mouse was scrolled up this frame.
     *
     * @return true if scrolled up
     */
    public boolean wasScrolledUp() {
        return scrollY > 0;
    }

    /**
     * Checks if the mouse was scrolled down this frame.
     *
     * @return true if scrolled down
     */
    public boolean wasScrolledDown() {
        return scrollY < 0;
    }

    // ========================================
    // Drag Detection
    // ========================================

    /**
     * Checks if the mouse is being dragged with a specific button.
     * Dragging means the button is held and the mouse is moving.
     *
     * @param button the button to check
     * @return true if dragging with this button
     */
    public boolean isDragging(KeyCode button) {
        return isButtonHeld(button) && hasMoved();
    }

    /**
     * Checks if the mouse is being dragged with the left button.
     */
    public boolean isDraggingLeft() {
        return isDragging(KeyCode.MOUSE_BUTTON_LEFT);
    }

    /**
     * Checks if the mouse is being dragged with the right button.
     */
    public boolean isDraggingRight() {
        return isDragging(KeyCode.MOUSE_BUTTON_RIGHT);
    }

    /**
     * Checks if the mouse is being dragged with the middle button.
     */
    public boolean isDraggingMiddle() {
        return isDragging(KeyCode.MOUSE_BUTTON_MIDDLE);
    }

    // ========================================
    // Utility Methods
    // ========================================

    /**
     * Sets the mouse position manually.
     * Useful for cursor warping or initialization.
     *
     * @param x new x position
     * @param y new y position
     */
    public void setPosition(double x, double y) {
        this.lastX = this.xPos;
        this.lastY = this.yPos;
        this.xPos = x;
        this.yPos = y;
    }

    /**
     * Resets the last position to current position.
     * Useful to prevent large deltas after cursor warping.
     */
    public void resetDelta() {
        this.lastX = this.xPos;
        this.lastY = this.yPos;
    }

    /**
     * Clears all mouse states.
     * Useful for scene transitions or when focus is lost.
     */
    public void clear() {
        buttonsHeld.clear();
        buttonsPressed.clear();
        buttonsReleased.clear();
        scrollX = 0;
        scrollY = 0;
    }

    /**
     * Gets all currently held mouse buttons.
     *
     * @return set of held buttons (defensive copy)
     */
    public Set<KeyCode> getHeldButtons() {
        return EnumSet.copyOf(buttonsHeld);
    }

    /**
     * Checks if a KeyCode represents a mouse button.
     *
     * @param key the key to check
     * @return true if it's a mouse button
     */
    private boolean isMouseButton(KeyCode key) {
        if (key == null) {
            return false;
        }

        return switch (key) {
            case MOUSE_BUTTON_LEFT, MOUSE_BUTTON_RIGHT, MOUSE_BUTTON_MIDDLE,
                 MOUSE_BUTTON_4, MOUSE_BUTTON_5, MOUSE_BUTTON_6,
                 MOUSE_BUTTON_7, MOUSE_BUTTON_8 -> true;
            default -> false;
        };
    }
}