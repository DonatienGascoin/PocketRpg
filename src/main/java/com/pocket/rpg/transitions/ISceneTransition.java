package com.pocket.rpg.transitions;

import com.pocket.rpg.rendering.OverlayRenderer;

/**
 * Interface for scene transition effects.
 * Implementations define how scenes transition from one to another.
 */
public interface ISceneTransition {

    /**
     * Updates the transition state.
     *
     * @param deltaTime time since last frame in seconds
     */
    void update(float deltaTime);

    /**
     * Renders the transition effect.
     * Called after the scene has been rendered to draw the overlay effect.
     *
     * @param overlayRenderer the overlay renderer to use for drawing
     */
    void render(OverlayRenderer overlayRenderer);

    /**
     * Checks if the transition has completed.
     *
     * @return true if transition is complete, false otherwise
     */
    boolean isComplete();

    /**
     * Resets the transition to its initial state.
     * Allows transition objects to be reused.
     */
    void reset();

    /**
     * Gets the current progress of the transition.
     *
     * @return progress from 0.0 (start) to 1.0 (complete)
     */
    float getProgress();

    /**
     * Checks if the transition is at the midpoint.
     * This is when the actual scene switch should occur.
     *
     * @return true if at midpoint, false otherwise
     */
    boolean isAtMidpoint();
}