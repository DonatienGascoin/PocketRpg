package com.pocket.rpg.input;

import org.joml.Vector2f;

import java.util.List;

public interface InputInterface {

    /**
     * Call at the end of each frame to update input states.
     */
    void endFrame();

    /**
     * Check if the specified input action was pressed in the current frame.
     */
    boolean wasPressedThisFrame(List<Integer> bindings);

    /**
     * Check if the specified input action is currently being pressed.
     */
    boolean isPressed(List<Integer> bindings);

    /**
     * Check if the specified input action was released in the current frame.
     */
    boolean wasReleasedThisFrame(List<Integer> bindings);

    /**
     * Get the current mouse position.
     */
    Vector2f getMousePosition();

    /**
     * Check if a specific mouse button was pressed this frame.
     */
    boolean wasMouseButtonPressedThisFrame(int button);

    /**
     * Check if a specific mouse button is currently pressed.
     */
    boolean isMouseButtonPressed(int button);

    /**
     * Check if a specific mouse button was released this frame.
     */
    boolean wasMouseButtonReleasedThisFrame(int button);

    /**
     * Get the current scroll offset.
     */
    Vector2f getScroll();

}
