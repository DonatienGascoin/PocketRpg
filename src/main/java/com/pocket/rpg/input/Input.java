package com.pocket.rpg.input;

import lombok.Setter;
import org.joml.Vector2f;

/**
 * Service locator for InputManager.
 * Provides Unity-style static API while maintaining testability.
 * <p>
 * Usage in gameplay code:
 * if (Input.getKeyDown(KeyCode.W)) { ... }
 * <p>
 * Usage in tests:
 * InputManager.setContext(mockInputManager);
 */
public class Input {

    @Setter
    private static InputContext context;

    /**
     * Initialize the input system with a context.
     * Called once during application startup.
     */
    public static void initialize(InputContext inputContext) {
        if (context != null) {
            System.err.println("WARNING: InputManager already initialized");
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

    // Mouse API
    public static Vector2f getMousePosition() {
        return getContext().getMousePosition();
    }

    public static Vector2f getMouseDelta() {
        return getContext().getMouseDelta();
    }

    public static float getMouseScrollDelta() {
        return getContext().getMouseScrollDelta();
    }

    public static boolean getMouseButton(KeyCode button) {
        return getContext().getMouseButton(button);
    }

    public static boolean getMouseButtonDown(KeyCode button) {
        return getContext().getMouseButtonDown(button);
    }

    public static boolean getMouseButtonUp(KeyCode button) {
        return getContext().getMouseButtonUp(button);
    }

    public static boolean isMouseDragging(KeyCode button) {
        return getContext().isMouseDragging(button);
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