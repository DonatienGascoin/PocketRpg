package com.pocket.rpg.input;

import com.pocket.rpg.config.InputConfig;
import com.pocket.rpg.input.listeners.KeyListener;
import com.pocket.rpg.input.listeners.MouseListener;
import org.joml.Vector2f;

import java.util.HashMap;
import java.util.Map;
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

    // Axes
    float getAxis(InputAxis axis);
    float getAxis(String axisName);
    float getAxisRaw(InputAxis axis);
    float getAxisRaw(String axisName);
    void setAxisValue(String axisName, float value);
}