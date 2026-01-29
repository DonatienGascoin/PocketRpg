package com.pocket.rpg.core.application;

import com.pocket.rpg.scenes.SceneManager;
import com.pocket.rpg.scenes.transitions.TransitionManager;
import lombok.Getter;

/**
 * Shared game loop logic for engine consumers.
 * <p>
 * Handles the core update order:
 * <ol>
 *   <li>Transition updates (always when active)</li>
 *   <li>Scene updates (only when not transitioning)</li>
 * </ol>
 * <p>
 * This class is intentionally minimal. Game-specific states
 * (pause, cutscene, dialogue, etc.) should be handled by game code
 * that wraps this loop.
 * <p>
 * Usage:
 * <pre>
 * // In your game/editor update:
 * Input.update();
 * if (!myGamePaused) {
 *     gameLoop.update(Time.deltaTime());
 * }
 * Input.endFrame();
 * </pre>
 *
 * @see SceneManager
 * @see TransitionManager
 */
public class GameLoop {

    @Getter
    private final SceneManager sceneManager;

    @Getter
    private final TransitionManager transitionManager;

    /**
     * Creates a GameLoop with required dependencies.
     *
     * @param sceneManager      Required - manages current scene
     * @param transitionManager Optional - if null, no transition handling
     */
    public GameLoop(SceneManager sceneManager, TransitionManager transitionManager) {
        if (sceneManager == null) {
            throw new IllegalArgumentException("SceneManager is required");
        }
        this.sceneManager = sceneManager;
        this.transitionManager = transitionManager;
    }

    /**
     * Main update method. Call once per frame.
     * <p>
     * Scene is frozen during fade-out and scene switching to prevent
     * game state changes mid-transition. During fade-in, scene updates
     * resume so that camera and other systems reflect the new state
     * before the overlay clears (input is already cleared by TransitionManager).
     *
     * @param deltaTime Time since last frame in seconds
     */
    public void update(float deltaTime) {
        if (transitionManager != null && transitionManager.isTransitioning()) {
            transitionManager.update(deltaTime);

            // During fade-in, allow scene updates so camera/components settle
            if (transitionManager.isFadingIn()) {
                sceneManager.update(deltaTime);
            }
            return;
        }

        // Normal update
        sceneManager.update(deltaTime);
    }

    /**
     * Checks if scene is currently frozen (during transition).
     * <p>
     * Game code can use this to also freeze game-specific systems,
     * or to skip input processing during transitions.
     *
     * @return true if a transition is in progress
     */
    public boolean isSceneFrozen() {
        return transitionManager != null && transitionManager.isTransitioning();
    }

    /**
     * Cleans up resources.
     */
    public void destroy() {
        sceneManager.destroy();
    }
}
