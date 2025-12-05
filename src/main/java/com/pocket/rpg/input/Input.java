package com.pocket.rpg.input;

import lombok.Setter;
import org.joml.Vector2f;

/**
 * Service locator for InputManager.
 * Provides Unity-style static API while maintaining testability.
 *
 * <h3>Mouse Button Methods:</h3>
 * <ul>
 *   <li><b>Normal methods</b> (getMouseButton, getMouseButtonDown, getMouseButtonUp):
 *       Automatically return false if mouse is consumed by UI. Use these in game code.</li>
 *   <li><b>Raw methods</b> (getMouseButtonRaw, getMouseButtonDownRaw, getMouseButtonUpRaw):
 *       Return actual state regardless of consumption. For UI system internal use.</li>
 * </ul>
 *
 * <h3>Usage in gameplay code:</h3>
 * <pre>{@code
 * // No need to check isMouseConsumed() - it's automatic!
 * if (Input.getMouseButtonDown(KeyCode.MOUSE_BUTTON_LEFT)) {
 *     // This only triggers if UI didn't consume the click
 *     handleGameClick();
 * }
 * }</pre>
 *
 * <h3>Usage in tests:</h3>
 * <pre>{@code
 * InputManager.setContext(mockInputManager);
 * }</pre>
 */
public class Input {

    @Setter
    private static InputContext context;

    public static boolean hasContext() {
        return context != null;
    }

    /**
     * Initialize the input system with a context.
     * Called once during application startup.
     * <p>
     * Allow to swap context for testing or different modes.
     */
    public static void initialize(InputContext inputContext) {
        if (inputContext == null) {
            throw new IllegalArgumentException("Input context cannot be null");
        }
        context = inputContext;
        System.out.println("InputManager initialized");
    }

    /**
     * Get the current context (for advanced usage).
     */
    public static InputContext getContext() {
        if (context == null) {
            throw new IllegalStateException(
                    "InputManager not initialized. Call InputManager.initialize() first.");
        }
        return context;
    }

    /**
     * Destroy the input manager and clear context.
     */
    public static void destroy() {
        if (context != null) {
            context.destroy();
            context = null;
            System.out.println("InputManager destroyed");
        }
    }

    // ========================================
    // STATIC API (Unity-style convenience)
    // ========================================
    // These delegate to the context instance

    public static void update(float deltaTime) {
        getContext().update(deltaTime);
    }

    public static void endFrame() {
        getContext().endFrame();
    }

    // Keyboard API
    public static boolean getKey(KeyCode key) {
        return getContext().getKey(key);
    }

    public static boolean getKeyDown(KeyCode key) {
        return getContext().getKeyDown(key);
    }

    public static boolean getKeyUp(KeyCode key) {
        return getContext().getKeyUp(key);
    }

    public static boolean anyKey() {
        return getContext().anyKey();
    }

    public static boolean anyKeyDown() {
        return getContext().anyKeyDown();
    }

    // Action API
    public static boolean isActionHeld(InputAction action) {
        return getContext().isActionHeld(action);
    }

    public static boolean isActionPressed(InputAction action) {
        return getContext().isActionPressed(action);
    }

    public static boolean isActionReleased(InputAction action) {
        return getContext().isActionReleased(action);
    }

    // Mouse Position API
    public static Vector2f getMousePosition() {
        return getContext().getMousePosition();
    }

    public static Vector2f getMouseDelta() {
        return getContext().getMouseDelta();
    }

    public static float getMouseScrollDelta() {
        return getContext().getMouseScrollDelta();
    }

    // ========================================
    // Mouse Button API (Consumption-Aware)
    // ========================================
    // These automatically return false if UI consumed the mouse input.
    // Use these in game code - no need to check isMouseConsumed() separately!

    /**
     * Returns true if mouse button is held, UNLESS mouse is consumed by UI.
     * Use this in game code - UI consumption is handled automatically.
     *
     * @param button the mouse button to check
     * @return true if held and not consumed by UI
     */
    public static boolean getMouseButton(KeyCode button) {
        return getContext().getMouseButton(button);
    }

    /**
     * Returns true if mouse button was just pressed, UNLESS mouse is consumed by UI.
     * Use this in game code - UI consumption is handled automatically.
     *
     * @param button the mouse button to check
     * @return true if just pressed and not consumed by UI
     */
    public static boolean getMouseButtonDown(KeyCode button) {
        return getContext().getMouseButtonDown(button);
    }

    /**
     * Returns true if mouse button was just released, UNLESS mouse is consumed by UI.
     * Use this in game code - UI consumption is handled automatically.
     *
     * @param button the mouse button to check
     * @return true if just released and not consumed by UI
     */
    public static boolean getMouseButtonUp(KeyCode button) {
        return getContext().getMouseButtonUp(button);
    }

    // ========================================
    // Mouse Button API (Raw - For UI System)
    // ========================================
    // These return the actual state regardless of consumption.
    // Only use these if you need the raw input state (e.g., in UI system).

    /**
     * Returns true if mouse button is held, regardless of UI consumption.
     * For UI system internal use only - game code should use getMouseButton().
     *
     * @param button the mouse button to check
     * @return true if held (ignores consumption)
     */
    public static boolean getMouseButtonRaw(KeyCode button) {
        return getContext().getMouseButtonRaw(button);
    }

    /**
     * Returns true if mouse button was just pressed, regardless of UI consumption.
     * For UI system internal use only - game code should use getMouseButtonDown().
     *
     * @param button the mouse button to check
     * @return true if just pressed (ignores consumption)
     */
    public static boolean getMouseButtonDownRaw(KeyCode button) {
        return getContext().getMouseButtonDownRaw(button);
    }

    /**
     * Returns true if mouse button was just released, regardless of UI consumption.
     * For UI system internal use only - game code should use getMouseButtonUp().
     *
     * @param button the mouse button to check
     * @return true if just released (ignores consumption)
     */
    public static boolean getMouseButtonUpRaw(KeyCode button) {
        return getContext().getMouseButtonUpRaw(button);
    }

    public static boolean isMouseDragging(KeyCode button) {
        return getContext().isMouseDragging(button);
    }

    // ========================================
    // Mouse Consumption (UI Input Blocking)
    // ========================================

    /**
     * Returns true if mouse input was consumed by UI this frame.
     *
     * <p>Note: You typically don't need to check this manually anymore!
     * The normal mouse button methods (getMouseButton, getMouseButtonDown,
     * getMouseButtonUp) automatically return false when consumed.
     *
     * <p>This method is mainly useful for:
     * <ul>
     *   <li>Checking if UI is under the cursor (for cursor changes)</li>
     *   <li>Debugging</li>
     *   <li>Special cases where you need to know consumption separately</li>
     * </ul>
     *
     * @return true if UI consumed mouse input this frame
     */
    public static boolean isMouseConsumed() {
        return getContext().isMouseConsumed();
    }

    /**
     * Sets the mouse consumed flag. Called by UIInputHandler.
     * Game code should not call this directly.
     *
     * @param consumed true if mouse input was consumed
     */
    public static void setMouseConsumed(boolean consumed) {
        getContext().setMouseConsumed(consumed);
    }

    // Axis API
    public static float getAxis(InputAxis axis) {
        return getContext().getAxis(axis);
    }

    public static float getAxis(String axisName) {
        return getContext().getAxis(axisName);
    }

    public static float getAxisRaw(InputAxis axis) {
        return getContext().getAxisRaw(axis);
    }

    public static float getAxisRaw(String axisName) {
        return getContext().getAxisRaw(axisName);
    }

    public static void setAxisValue(String axisName, float value) {
        getContext().setAxisValue(axisName, value);
    }

    // Utility
    public static void clear() {
        getContext().clear();
    }
}