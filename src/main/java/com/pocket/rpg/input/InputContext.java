package com.pocket.rpg.input;

import org.joml.Vector2f;

/**
 * Interface for input context implementations.
 * Allows different implementations (real, mock, replay, etc.)
 *
 * Mouse Button Methods:
 * - Normal methods (getMouseButton, getMouseButtonDown, getMouseButtonUp) automatically
 *   return false if mouse is consumed by UI. Use these in game code.
 * - Raw methods (getMouseButtonRaw, getMouseButtonDownRaw, getMouseButtonUpRaw) return
 *   the actual state regardless of consumption. Used internally by UI system.
 */
public interface InputContext {

    // Lifecycle
    void update(float deltaTime);

    void endFrame();

    void clear();

    void destroy();

    // Keyboard
    boolean getKey(KeyCode key);

    boolean getKeyDown(KeyCode key);

    boolean getKeyUp(KeyCode key);

    boolean anyKey();

    boolean anyKeyDown();

    // Actions
    boolean isActionHeld(InputAction action);

    boolean isActionPressed(InputAction action);

    boolean isActionReleased(InputAction action);

    // Mouse position
    Vector2f getMousePosition();

    Vector2f getMouseDelta();

    float getMouseScrollDelta();

    // Mouse buttons (consumption-aware - returns false if UI consumed input)
    boolean getMouseButton(KeyCode button);

    boolean getMouseButtonDown(KeyCode button);

    boolean getMouseButtonUp(KeyCode button);

    // Mouse buttons (raw - ignores consumption, for UI system use)
    boolean getMouseButtonRaw(KeyCode button);

    boolean getMouseButtonDownRaw(KeyCode button);

    boolean getMouseButtonUpRaw(KeyCode button);

    boolean isMouseDragging(KeyCode button);

    // Mouse consumption (for UI input blocking)
    boolean isMouseConsumed();

    void setMouseConsumed(boolean consumed);

    // Axes
    float getAxis(InputAxis axis);

    float getAxis(String axisName);

    float getAxisRaw(InputAxis axis);

    float getAxisRaw(String axisName);

    void setAxisValue(String axisName, float value);
}