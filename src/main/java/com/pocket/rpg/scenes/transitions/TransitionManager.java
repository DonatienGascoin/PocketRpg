package com.pocket.rpg.scenes.transitions;

import com.pocket.rpg.config.TransitionConfig;
import com.pocket.rpg.config.TransitionEntry;
import com.pocket.rpg.input.Input;
import com.pocket.rpg.rendering.core.OverlayRenderer;
import com.pocket.rpg.rendering.resources.Sprite;
import com.pocket.rpg.scenes.SceneManager;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

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
    /**
     * -- GETTER --
     *  Gets the scene manager.
     *  Package-private for SceneTransition static API.
     *
     * @return the scene manager
     */
    @Getter
    private final SceneManager sceneManager;
    private final OverlayRenderer overlayRenderer;
    private final TransitionConfig defaultConfig;
    private final List<TransitionEntry> transitionEntries;
    private final String defaultTransitionName;

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
        this(sceneManager, overlayRenderer, defaultConfig, new ArrayList<>(), "");
    }

    /**
     * Creates a transition manager with named transition entries.
     *
     * @param sceneManager          the scene manager to control
     * @param overlayRenderer       the overlay renderer for drawing transition effects
     * @param defaultConfig         the default transition configuration
     * @param transitionEntries     the available named luma transitions
     * @param defaultTransitionName the default transition name to use when config has no name set
     */
    public TransitionManager(SceneManager sceneManager,
                             OverlayRenderer overlayRenderer,
                             TransitionConfig defaultConfig,
                             List<TransitionEntry> transitionEntries,
                             String defaultTransitionName) {
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
        this.transitionEntries = transitionEntries != null ? transitionEntries : new ArrayList<>();
        this.defaultTransitionName = defaultTransitionName != null ? defaultTransitionName : "";

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
            System.out.println(
                    "Cannot start transition while another is in progress. " +
                            "Current state: " + state
            );
            return;
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

        // Clear input to prevent movement during the one-frame gap
        // (transition starts mid-scene-update, remaining components still run this frame)
        if (Input.hasContext()) {
            Input.clear();
        }

        System.out.println("Starting transition to scene: " + sceneName);
    }

    /**
     * Callback to execute at the midpoint of a fade effect.
     */
    private Runnable midpointCallback;

    /**
     * Plays a fade effect without changing scenes.
     * Useful for within-scene transitions like warps, doors, or cutscenes.
     * <p>
     * The callback is executed at the midpoint (when screen is fully faded).
     *
     * @param onMidpoint action to perform at midpoint (e.g., teleport player)
     * @param config     the transition configuration to use
     * @throws IllegalStateException if a transition is already in progress
     */
    public void playFadeEffect(Runnable onMidpoint, TransitionConfig config) {
        if (state != State.IDLE) {
            System.out.println(
                    "Cannot start fade effect while another transition is in progress. " +
                            "Current state: " + state
            );
            return;
        }

        if (onMidpoint == null) {
            throw new IllegalArgumentException("Midpoint callback cannot be null");
        }

        if (config == null) {
            throw new IllegalArgumentException("TransitionConfig cannot be null");
        }

        this.targetSceneName = null; // No scene change
        this.midpointCallback = onMidpoint;
        this.currentTransition = createTransition(config);
        this.currentTransition.reset();
        this.state = State.FADING_OUT;

        // Clear input to prevent movement during the one-frame gap
        if (Input.hasContext()) {
            Input.clear();
        }

        System.out.println("Starting fade effect");
    }

    /**
     * Plays a fade effect with the default configuration.
     *
     * @param onMidpoint action to perform at midpoint
     * @throws IllegalStateException if a transition is already in progress
     */
    public void playFadeEffect(Runnable onMidpoint) {
        playFadeEffect(onMidpoint, defaultConfig);
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
                // Check if we've reached the midpoint (scene switch / callback point)
                if (currentTransition.isAtMidpoint()) {
                    state = State.SWITCHING;
                    performMidpointAction();
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
     * Performs the midpoint action: either scene switch or callback execution.
     * Called at the midpoint of the transition.
     */
    private void performMidpointAction() {
        try {
            if (targetSceneName != null) {
                // Scene transition: load the target scene
                System.out.println("Switching to scene: " + targetSceneName);
                sceneManager.loadScene(targetSceneName);
            } else if (midpointCallback != null) {
                // Fade effect: execute the callback
                System.out.println("Executing fade effect callback");
                midpointCallback.run();
            }
        } catch (Exception e) {
            System.err.println("Midpoint action failed: " + e.getMessage());
            completeTransition();  // Reset state on failure
        }
    }

    /**
     * Completes the transition and returns to idle state.
     */
    private void completeTransition() {
        System.out.println("Transition complete");
        state = State.IDLE;
        currentTransition = null;
        targetSceneName = null;
        midpointCallback = null;
    }

    /**
     * Creates a transition based on configuration.
     * If the config specifies a transition name, resolves it to a luma texture.
     * Otherwise, falls back to a plain FadeTransition.
     *
     * @param config transition configuration
     * @return the created transition
     */
    private ISceneTransition createTransition(TransitionConfig config) {
        config.validate();

        // If config has no transition name, fall back to the global default
        String name = config.getTransitionName();
        if (name == null || name.isEmpty()) {
            name = defaultTransitionName;
        }
        TransitionEntry entry = resolveTransition(name);
        if (entry != null && entry.getLumaSprite() != null) {
            int textureId = entry.getLumaSprite().getTexture().getTextureId();
            return new LumaTransition(config, textureId);
        }

        // Fallback: plain fade
        return new FadeTransition(config);
    }

    /**
     * Resolves a transition name to a TransitionEntry.
     *
     * @param name the transition name (empty = null, "Random" = random from list, otherwise lookup by name)
     * @return the resolved TransitionEntry, or null for plain fade
     */
    private TransitionEntry resolveTransition(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }

        if ("Random".equalsIgnoreCase(name)) {
            if (transitionEntries.isEmpty()) {
                return null;
            }
            int index = ThreadLocalRandom.current().nextInt(transitionEntries.size());
            return transitionEntries.get(index);
        }

        for (TransitionEntry entry : transitionEntries) {
            if (name.equals(entry.getName())) {
                return entry;
            }
        }

        System.err.println("Transition not found: " + name + ", falling back to fade");
        return null;
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
