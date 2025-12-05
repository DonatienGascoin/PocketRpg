package com.pocket.rpg.input;

import org.joml.Vector2f;

/**
 * Interface for input context implementations.
 * Allows different implementations (real, mock, replay, etc.)
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

    // Mouse
    Vector2f getMousePosition();

    Vector2f getMouseDelta();

    float getMouseScrollDelta();

    boolean getMouseButton(KeyCode button);

    boolean getMouseButtonDown(KeyCode button);

    boolean getMouseButtonUp(KeyCode button);

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