package com.pocket.rpg.input;

import org.joml.Vector2f;
import org.lwjgl.BufferUtils;

import java.nio.DoubleBuffer;
import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.glfw.GLFW.*;

/**
 * Handles mouse input state tracking and action mapping.
 */
public class MouseListener {
    private long windowHandle;
    private InputConfig config;

    // Mouse position
    private Vector2f screenPosition = new Vector2f();
    private Vector2f previousScreenPosition = new Vector2f();
    private Vector2f mouseDelta = new Vector2f();

    // Mouse button states (raw)
    private Map<Integer, ActionState> buttonStates = new HashMap<>();

    // Mouse action states
    private Map<InputAction, ActionState> mouseActionStates = new HashMap<>();

    // Scroll
    private Vector2f scrollDelta = new Vector2f();

    public MouseListener(long windowHandle, InputConfig config) {
        this.windowHandle = windowHandle;
        this.config = config;
    }

    /**
     * Poll mouse state - called once per frame.
     */
    public void poll() {
        // Update position
        previousScreenPosition.set(screenPosition);

        DoubleBuffer xBuffer = BufferUtils.createDoubleBuffer(1);
        DoubleBuffer yBuffer = BufferUtils.createDoubleBuffer(1);
        glfwGetCursorPos(windowHandle, xBuffer, yBuffer);

        screenPosition.set((float)xBuffer.get(0), (float)yBuffer.get(0));
        mouseDelta.set(screenPosition).sub(previousScreenPosition);

        // Update all tracked button states
        for (Map.Entry<Integer, ActionState> entry : buttonStates.entrySet()) {
            int button = entry.getKey();
            ActionState state = entry.getValue();
            boolean isPressed = glfwGetMouseButton(windowHandle, button) == GLFW_PRESS;
            state.update(isPressed);
        }

        // Update all MOUSE actions based on current bindings
        for (InputAction action : InputAction.values()) {
            // TYPE-SAFE CHECK - NO MAGIC STRING!
            if (!action.isMouseAction()) {
                continue; // Skip non-mouse actions
            }

            int boundButton = config.getBindingForAction(action);
            ActionState buttonState = buttonStates.computeIfAbsent(boundButton, k -> new ActionState());

            ActionState actionState = mouseActionStates.computeIfAbsent(action, a -> new ActionState());
            actionState.update(buttonState.isPressed());
        }

        // Reset scroll delta (will be updated by callback if scrolling occurs)
        scrollDelta.set(0, 0);
    }

    // ======================================================================
    // POSITION API
    // ======================================================================

    public Vector2f getScreenPosition() {
        return new Vector2f(screenPosition);
    }

    public Vector2f getMouseDelta() {
        return new Vector2f(mouseDelta);
    }

    public Vector2f getScrollDelta() {
        return new Vector2f(scrollDelta);
    }

    // ======================================================================
    // RAW BUTTON API
    // ======================================================================

    public boolean isButtonPressedThisFrame(int button) {
        return buttonStates.getOrDefault(button, ActionState.INACTIVE).isPressedThisFrame();
    }

    public boolean isButtonPressed(int button) {
        return buttonStates.getOrDefault(button, ActionState.INACTIVE).isPressed();
    }

    public boolean isButtonReleased(int button) {
        return buttonStates.getOrDefault(button, ActionState.INACTIVE).isReleased();
    }

    // ======================================================================
    // ACTION API (RECOMMENDED)
    // ======================================================================

    public boolean isActionPressedThisFrame(InputAction action) {
        if (!action.isMouseAction()) {
            System.err.println("WARNING: " + action + " is not a mouse action!");
            return false;
        }
        return mouseActionStates.getOrDefault(action, ActionState.INACTIVE).isPressedThisFrame();
    }

    public boolean isActionPressed(InputAction action) {
        if (!action.isMouseAction()) {
            System.err.println("WARNING: " + action + " is not a mouse action!");
            return false;
        }
        return mouseActionStates.getOrDefault(action, ActionState.INACTIVE).isPressed();
    }

    public boolean isActionReleased(InputAction action) {
        if (!action.isMouseAction()) {
            System.err.println("WARNING: " + action + " is not a mouse action!");
            return false;
        }
        return mouseActionStates.getOrDefault(action, ActionState.INACTIVE).isReleased();
    }

    /**
     * Called by GLFW scroll callback.
     * Package-private - only InputManager should call this.
     */
    void onScroll(double xOffset, double yOffset) {
        scrollDelta.set((float)xOffset, (float)yOffset);
    }

    /**
     * Track a button for raw queries (optional optimization).
     */
    public void trackButton(int button) {
        buttonStates.putIfAbsent(button, new ActionState());
    }
}