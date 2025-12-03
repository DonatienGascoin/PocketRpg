package com.pocket.rpg.transitions;

import com.pocket.rpg.config.TransitionConfig;
import com.pocket.rpg.rendering.OverlayRenderer;
import com.pocket.rpg.scenes.SceneManager;
import lombok.Getter;

/**
 * Manages scene transitions.
 * Orchestrates the transition lifecycle: fade out, scene switch, fade in.
 * <p>
 * Transition flow:
 * 1. IDLE - no transition active
 * 2. FADING_OUT - rendering current scene with increasing overlay
 * 3. SWITCHING - actual scene change occurs
 * 4. FADING_IN - rendering new scene with decreasing overlay
 * 5. Back to IDLE
 */
public class TransitionManager {

    /**
     * States of the transition system.
     */
    private enum State {
        IDLE,        // No transition active
        FADING_OUT,  // Fading out from old scene
        SWITCHING,   // Performing the scene switch
        FADING_IN    // Fading in to new scene
    }

    @Getter
    private State state = State.IDLE;
    private ISceneTransition currentTransition;
    private String targetSceneName;
    private final SceneManager sceneManager;
    private final OverlayRenderer overlayRenderer;
    private final TransitionConfig defaultConfig;

    /**
     * Creates a transition manager.
     *
     * @param sceneManager    the scene manager to control
     * @param overlayRenderer the overlay renderer for drawing transition effects
     * @param defaultConfig   the default transition configuration
     */
    public TransitionManager(SceneManager sceneManager,
                             OverlayRenderer overlayRenderer,
                             TransitionConfig defaultConfig) {
        if (sceneManager == null) {
            throw new IllegalArgumentException("SceneManager cannot be null");
        }
        if (overlayRenderer == null) {
            throw new IllegalArgumentException("OverlayRenderer cannot be null");
        }
        if (defaultConfig == null) {
            throw new IllegalArgumentException("Default TransitionConfig cannot be null");
        }

        this.sceneManager = sceneManager;
        this.overlayRenderer = overlayRenderer;
        this.defaultConfig = new TransitionConfig(defaultConfig); // Defensive copy

        // Validate default config
        this.defaultConfig.validate();
    }

    /**
     * Starts a transition to the specified scene using the default configuration.
     *
     * @param sceneName name of the scene to transition to
     * @throws IllegalStateException if a transition is already in progress
     */
    public void startTransition(String sceneName) {
        startTransition(sceneName, defaultConfig);
    }

    /**
     * Starts a transition to the specified scene with a custom configuration.
     *
     * @param sceneName name of the scene to transition to
     * @param config    the transition configuration to use
     * @throws IllegalStateException if a transition is already in progress
     */
    public void startTransition(String sceneName, TransitionConfig config) {
        if (state != State.IDLE) {
            throw new IllegalStateException(
                    "Cannot start transition while another is in progress. " +
                            "Current state: " + state
            );
        }

        if (sceneName == null || sceneName.isEmpty()) {
            throw new IllegalArgumentException("Scene name cannot be null or empty");
        }

        if (config == null) {
            throw new IllegalArgumentException("TransitionConfig cannot be null");
        }

        this.targetSceneName = sceneName;
        this.currentTransition = createTransition(config);
        this.currentTransition.reset();
        this.state = State.FADING_OUT;

        System.out.println("Starting transition to scene: " + sceneName);
    }

    /**
     * Updates the transition state.
     * Must be called every frame.
     * Does nothing if no transition is active (early return for performance).
     *
     * @param deltaTime time since last frame in seconds
     */
    public void update(float deltaTime) {
        // Early return if not transitioning (zero overhead when idle)
        if (state == State.IDLE || currentTransition == null) {
            return;
        }

        // Update the transition effect
        currentTransition.update(deltaTime);

        // Handle state transitions
        switch (state) {
            case FADING_OUT:
                // Check if we've reached the midpoint (scene switch point)
                if (currentTransition.isAtMidpoint()) {
                    state = State.SWITCHING;
                    performSceneSwitch();
                    state = State.FADING_IN;
                }
                break;

            case FADING_IN:
                // Check if transition is complete
                if (currentTransition.isComplete()) {
                    completeTransition();
                }
                break;

            case SWITCHING:
                // This state should be very brief (just the scene switch)
                state = State.FADING_IN;
                break;

            case IDLE:
                // Should not reach here
                break;
        }
    }

    /**
     * Renders the transition effect.
     * Must be called every frame after scene rendering.
     * Does nothing if no transition is active (early return for performance).
     */
    public void render() {
        // Early return if not transitioning (zero overhead when idle)
        if (state == State.IDLE || currentTransition == null) {
            return;
        }

        // Delegate rendering to the transition
        currentTransition.render(overlayRenderer);
    }

    /**
     * Checks if a transition is currently active.
     *
     * @return true if transitioning, false otherwise
     */
    public boolean isTransitioning() {
        return state != State.IDLE;
    }

    /**
     * Gets the current transition progress.
     *
     * @return progress from 0.0 to 1.0, or 0.0 if no transition active
     */
    public float getProgress() {
        if (currentTransition == null) {
            return 0.0f;
        }
        return currentTransition.getProgress();
    }

    /**
     * Checks if currently in the fade out phase.
     *
     * @return true if fading out, false otherwise
     */
    public boolean isFadingOut() {
        return state == State.FADING_OUT;
    }

    /**
     * Checks if currently in the fade in phase.
     *
     * @return true if fading in, false otherwise
     */
    public boolean isFadingIn() {
        return state == State.FADING_IN;
    }

    /**
     * Gets the name of the target scene (the scene being loaded).
     *
     * @return target scene name, or null if not transitioning
     */
    public String getTargetScene() {
        return targetSceneName;
    }

    /**
     * Gets the default transition configuration.
     * Returns a copy to prevent modification.
     *
     * @return a copy of the default configuration
     */
    public TransitionConfig getDefaultConfig() {
        return new TransitionConfig(defaultConfig);
    }

    /**
     * Gets the scene manager.
     * Package-private for SceneTransition static API.
     *
     * @return the scene manager
     */
    SceneManager getSceneManager() {
        return sceneManager;
    }

    /**
     * Performs the actual scene switch.
     * Called at the midpoint of the transition.
     */
    private void performSceneSwitch() {
        System.out.println("Switching to scene: " + targetSceneName);
        sceneManager.loadScene(targetSceneName);
    }

    /**
     * Completes the transition and returns to idle state.
     */
    private void completeTransition() {
        System.out.println("Transition complete");
        state = State.IDLE;
        currentTransition = null;
        targetSceneName = null;
    }

    /**
     * Creates a transition based on configuration.
     *
     * @param config transition configuration
     * @return the created transition
     */
    private ISceneTransition createTransition(TransitionConfig config) {
        config.validate();

        return switch (config.getType()) {
            case FADE -> new FadeTransition(config);
            case FADE_WITH_TEXT -> new FadeWithTextTransition(config);

            // Wipe transitions
            case WIPE_LEFT -> new WipeTransition(config, WipeTransition.WipeDirection.LEFT);
            case WIPE_RIGHT -> new WipeTransition(config, WipeTransition.WipeDirection.RIGHT);
            case WIPE_UP -> new WipeTransition(config, WipeTransition.WipeDirection.UP);
            case WIPE_DOWN -> new WipeTransition(config, WipeTransition.WipeDirection.DOWN);
            case WIPE_CIRCLE_IN -> new WipeTransition(config, WipeTransition.WipeDirection.CIRCLE_IN);
            case WIPE_CIRCLE_OUT -> new WipeTransition(config, WipeTransition.WipeDirection.CIRCLE_OUT);
        };
    }

    /**
     * Cancels any active transition immediately.
     * Warning: This may leave the scene manager in an inconsistent state.
     * Use only for emergency situations.
     */
    public void cancelTransition() {
        if (state != State.IDLE) {
            System.out.println("WARNING: Cancelling transition in progress");
            state = State.IDLE;
            currentTransition = null;
            targetSceneName = null;
        }
    }
}