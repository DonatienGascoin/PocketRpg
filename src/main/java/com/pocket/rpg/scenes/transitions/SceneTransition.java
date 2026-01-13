package com.pocket.rpg.scenes.transitions;

import com.pocket.rpg.config.TransitionConfig;

/**
 * Unity-style static API for scene transitions.
 * <p>
 * Provides a convenient, global access point for scene loading with transitions.
 * Uses service locator pattern for simplicity in game code.
 * Usage:
 * <pre>
 * // Simple load with default transition (from GameConfig)
 * SceneTransition.loadScene("Level2");
 *
 * // Custom transition
 * TransitionConfig config = TransitionConfig.builder()
 *     .fadeOutDuration(1.0f)
 *     .fadeColor(new Vector4f(1, 0, 0, 1))
 *     .build();
 * SceneTransition.loadScene("BossLevel", config);
 *
 * // Instant load (no transition)
 * SceneTransition.loadSceneInstant("Menu");
 *
 * // Query state
 * if (SceneTransition.isTransitioning()) {
 *     Input.disable();
 * }
 * </pre>
 */
public class SceneTransition {

    private static TransitionManager instance;

    public static boolean hasContext() {
        return instance != null;
    }

    /**
     * Initializes the scene transition system.
     * Must be called once at application startup.
     *
     * @param manager the transition manager instance
     * @throws IllegalArgumentException if manager is null
     * @throws IllegalStateException    if already initialized
     */
    public static void initialize(TransitionManager manager) {
        if (manager == null) {
            throw new IllegalArgumentException("TransitionManager cannot be null");
        }
        if (instance != null) {
            throw new IllegalStateException("SceneTransition already initialized");
        }
        instance = manager;
        System.out.println("SceneTransition static API initialized");
    }

    /**
     * Initializes the scene transition system.
     * Used by the editor in order to re-initialize at each startup.
     *
     * @param manager the transition manager instance
     * @throws IllegalArgumentException if manager is null
     * @throws IllegalStateException    if already initialized
     */
    public static void forceInitialize(TransitionManager manager) {
        if (instance != null) {
            instance.cancelTransition();
        }
        instance = manager;
        System.out.println("SceneTransition static API initialized");
    }

    /**
     * Loads a scene with the default transition.
     * Default transition is configured in GameConfig and passed to TransitionManager.
     *
     * @param sceneName name of the scene to load
     * @throws IllegalStateException if not initialized
     */
    public static void loadScene(String sceneName) {
        checkInitialized();
        instance.startTransition(sceneName);
    }

    /**
     * Loads a scene with a custom transition.
     *
     * @param sceneName name of the scene to load
     * @param config    transition configuration
     * @throws IllegalStateException    if not initialized
     * @throws IllegalArgumentException if config is null
     */
    public static void loadScene(String sceneName, TransitionConfig config) {
        checkInitialized();
        if (config == null) {
            throw new IllegalArgumentException("TransitionConfig cannot be null");
        }
        instance.startTransition(sceneName, config);
    }

    /**
     * Loads a scene instantly without any transition.
     * Useful for quick scene changes where visual transition isn't needed.
     *
     * @param sceneName name of the scene to load
     * @throws IllegalStateException if not initialized
     */
    public static void loadSceneInstant(String sceneName) {
        checkInitialized();
        instance.getSceneManager().loadScene(sceneName);
    }

    /**
     * Checks if a transition is currently in progress.
     *
     * @return true if transitioning, false otherwise
     */
    public static boolean isTransitioning() {
        return instance != null && instance.isTransitioning();
    }

    /**
     * Gets the current transition progress.
     *
     * @return progress from 0.0 (start) to 1.0 (complete), or 0.0 if not transitioning
     */
    public static float getProgress() {
        return instance != null ? instance.getProgress() : 0.0f;
    }

    /**
     * Checks if currently in the fade out phase.
     *
     * @return true if fading out, false otherwise
     */
    public static boolean isFadingOut() {
        return instance != null && instance.isFadingOut();
    }

    /**
     * Checks if currently in the fade in phase.
     *
     * @return true if fading in, false otherwise
     */
    public static boolean isFadingIn() {
        return instance != null && instance.isFadingIn();
    }

    /**
     * Gets the name of the scene being transitioned to.
     *
     * @return target scene name, or null if not transitioning
     */
    public static String getTargetScene() {
        return instance != null ? instance.getTargetScene() : null;
    }

    /**
     * Gets the default transition configuration.
     * This is the configuration from GameConfig passed to TransitionManager.
     *
     * @return a copy of the default configuration
     * @throws IllegalStateException if not initialized
     */
    public static TransitionConfig getDefaultConfig() {
        checkInitialized();
        return instance.getDefaultConfig();
    }

    /**
     * Cancels the current transition immediately.
     * WARNING: This may leave the scene manager in an inconsistent state.
     * Only use in emergency situations.
     *
     * @throws IllegalStateException if not initialized
     */
    public static void cancelTransition() {
        checkInitialized();
        instance.cancelTransition();
    }

    /**
     * Gets the underlying TransitionManager instance.
     * Useful for advanced usage or testing.
     *
     * @return the transition manager instance
     * @throws IllegalStateException if not initialized
     */
    public static TransitionManager getManager() {
        checkInitialized();
        return instance;
    }

    /**
     * Checks if the system has been initialized.
     *
     * @throws IllegalStateException if not initialized
     */
    private static void checkInitialized() {
        if (instance == null) {
            throw new IllegalStateException(
                    "SceneTransition not initialized. " +
                            "Call SceneTransition.initialize(transitionManager) at startup."
            );
        }
    }

    /**
     * Checks if the system is initialized.
     *
     * @return true if initialized, false otherwise
     */
    public static boolean isInitialized() {
        return instance != null;
    }

    // Private constructor to prevent instantiation
    private SceneTransition() {
        throw new AssertionError("SceneTransition is a static API class and cannot be instantiated");
    }
}