package com.pocket.rpg.editor;

import com.pocket.rpg.config.GameConfig;
import com.pocket.rpg.config.RenderingConfig;
import com.pocket.rpg.core.ViewportConfig;
import com.pocket.rpg.editor.rendering.PlayModeRenderer;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.scene.RuntimeSceneLoader;
import com.pocket.rpg.editor.scene.RuntimeSceneManager;
import com.pocket.rpg.editor.serialization.EditorSceneSerializer;
import com.pocket.rpg.rendering.OverlayRenderer;
import com.pocket.rpg.scenes.RuntimeScene;
import com.pocket.rpg.serialization.SceneData;
import com.pocket.rpg.transitions.SceneTransition;
import com.pocket.rpg.transitions.TransitionManager;
import lombok.Getter;

import java.util.function.Consumer;

/**
 * Controls Play Mode lifecycle in the Scene Editor.
 * <p>
 * Manages:
 * - Play/Pause/Stop state transitions
 * - Runtime scene creation from editor scene
 * - Scene snapshot and restoration
 * - Runtime systems (SceneManager, TransitionManager, etc.)
 */
public class PlayModeController {

    /**
     * Play mode states.
     */
    public enum PlayState {
        STOPPED,    // Editor mode, editing enabled
        PLAYING,    // Game running
        PAUSED      // Game frozen, still rendering
    }

    private final EditorContext context;
    private final GameConfig gameConfig;
    private final RenderingConfig renderingConfig;

    // Play mode state
    @Getter
    private PlayState state = PlayState.STOPPED;

    // Editor scene backup
    private SceneData snapshot;
    private String snapshotFilePath;

    // Runtime systems (created on play, destroyed on stop)
    private ViewportConfig viewportConfig;
    private RuntimeSceneLoader sceneLoader;
    private RuntimeSceneManager sceneManager;
    private TransitionManager transitionManager;
    private PlayModeRenderer renderer;

    // Message callback
    private Consumer<String> messageCallback;

    public PlayModeController(EditorContext context, GameConfig gameConfig) {
        this.context = context;
        this.gameConfig = gameConfig;
        this.renderingConfig = context.getRenderingConfig();
    }

    /**
     * Sets a callback for status messages.
     */
    public void setMessageCallback(Consumer<String> callback) {
        this.messageCallback = callback;
    }

    // ========================================================================
    // STATE TRANSITIONS
    // ========================================================================

    /**
     * Starts play mode.
     */
    public void play() {
        if (state != PlayState.STOPPED) {
            showMessage("Already playing");
            return;
        }

        EditorScene editorScene = context.getCurrentScene();
        if (editorScene == null) {
            showMessage("No scene to play");
            return;
        }

        try {
            // 1. Snapshot editor scene
            snapshot = EditorSceneSerializer.toSceneData(editorScene);
            snapshotFilePath = editorScene.getFilePath();

            // 2. Create viewport config from game settings
            viewportConfig = new ViewportConfig(gameConfig);

            // 3. Create scene loader
            sceneLoader = new RuntimeSceneLoader(viewportConfig, renderingConfig);

            // 4. Create RuntimeSceneManager (supports dynamic scene loading)
            sceneManager = new RuntimeSceneManager(
                    viewportConfig,
                    renderingConfig,
                    sceneLoader,
                    "scenes/"  // Base path for scene files
            );

            // 5. Initialize renderer FIRST (we need its OverlayRenderer)
            renderer = new PlayModeRenderer(gameConfig, renderingConfig);
            renderer.init();

            // 6. Get overlay renderer from PlayModeRenderer
            OverlayRenderer overlayRenderer = renderer.getOverlayRenderer();
            if (overlayRenderer != null) {
                overlayRenderer.setScreenSize(gameConfig.getGameWidth(), gameConfig.getGameHeight());
            }

            // 7. Create transition manager with the overlay renderer
            transitionManager = new TransitionManager(
                    sceneManager,
                    overlayRenderer,
                    gameConfig.getDefaultTransitionConfig()
            );

            // 8. Initialize global SceneTransition API (or reset if already set)
            try {
                SceneTransition.initialize(transitionManager);
            } catch (IllegalStateException e) {
                // Already initialized from previous play - need to reset it
                // Try reflection to reset, or just proceed (transitions may not work)
                System.out.println("SceneTransition already initialized, attempting reset...");
                try {
                    // Try to call reset() if it exists
                    java.lang.reflect.Method resetMethod = SceneTransition.class.getDeclaredMethod("reset");
                    resetMethod.setAccessible(true);
                    resetMethod.invoke(null);
                    SceneTransition.initialize(transitionManager);
                } catch (Exception ex) {
                    // No reset method, try setting field directly
                    try {
                        java.lang.reflect.Field managerField = SceneTransition.class.getDeclaredField("manager");
                        managerField.setAccessible(true);
                        managerField.set(null, transitionManager);
                        System.out.println("SceneTransition manager updated via reflection");
                    } catch (Exception ex2) {
                        System.err.println("Could not reset SceneTransition: " + ex2.getMessage());
                        // Proceed anyway - scene transitions may not work but play mode will
                    }
                }
            }

            // 9. Load initial scene from snapshot
            RuntimeScene runtimeScene = sceneLoader.load(snapshot);
            sceneManager.loadScene(runtimeScene);

            // 10. Switch state
            state = PlayState.PLAYING;

            showMessage("Play mode started");
            System.out.println("Play mode started: " + editorScene.getName());

        } catch (Exception e) {
            System.err.println("Failed to start play mode: " + e.getMessage());
            e.printStackTrace();
            cleanup();
            showMessage("Error: " + e.getMessage());
        }
    }

    /**
     * Pauses the game.
     */
    public void pause() {
        if (state != PlayState.PLAYING) {
            return;
        }

        state = PlayState.PAUSED;
        showMessage("Paused");
    }

    /**
     * Resumes from pause.
     */
    public void resume() {
        if (state != PlayState.PAUSED) {
            return;
        }

        state = PlayState.PLAYING;
        showMessage("Resumed");
    }

    /**
     * Stops play mode and restores editor state.
     */
    public void stop() {
        if (state == PlayState.STOPPED) {
            return;
        }

        System.out.println("Stopping play mode...");

        // 1. Cleanup runtime systems (this destroys transitionManager)
        cleanup();

        // Note: We don't clear SceneTransition here because initialize(null) throws.
        // The static reference will be overwritten on next play() call.

        // 2. Restore editor scene from snapshot
        if (snapshot != null) {
            try {
                EditorScene restored = EditorSceneSerializer.fromSceneData(snapshot, snapshotFilePath);
                context.setCurrentScene(restored);
            } catch (Exception e) {
                System.err.println("Failed to restore editor scene: " + e.getMessage());
                // Create empty scene as fallback
                context.setCurrentScene(new EditorScene("Recovered"));
            }
        }

        // 3. Clear snapshot
        snapshot = null;
        snapshotFilePath = null;

        // 4. Switch state
        state = PlayState.STOPPED;

        showMessage("Play mode stopped");
        System.out.println("Play mode stopped");
    }

    // ========================================================================
    // UPDATE / RENDER
    // ========================================================================

    /**
     * Updates play mode (called every frame).
     *
     * @param deltaTime Time since last frame
     */
    public void update(float deltaTime) {
        if (state != PlayState.PLAYING) {
            return;
        }

        // Update transitions first (they may trigger scene changes)
        if (transitionManager != null) {
            transitionManager.update(deltaTime);
        }

        // Update current scene
        if (sceneManager != null) {
            sceneManager.update(deltaTime);
        }
    }

    /**
     * Renders the game scene (called every frame when not stopped).
     */
    public void render() {
        if (state == PlayState.STOPPED) {
            return;
        }

        if (renderer != null && sceneManager != null) {
            renderer.render(sceneManager.getCurrentScene(), transitionManager);
        }
    }

    /**
     * Gets the output texture for ImGui display.
     */
    public int getOutputTexture() {
        if (renderer != null) {
            return renderer.getOutputTexture();
        }
        return 0;
    }

    /**
     * Gets the game width for aspect ratio calculations.
     */
    public int getGameWidth() {
        return gameConfig.getGameWidth();
    }

    /**
     * Gets the game height for aspect ratio calculations.
     */
    public int getGameHeight() {
        return gameConfig.getGameHeight();
    }

    // ========================================================================
    // HELPERS
    // ========================================================================

    /**
     * Cleans up all runtime systems.
     */
    private void cleanup() {
        if (renderer != null) {
            renderer.destroy();
            renderer = null;
        }

        if (sceneManager != null) {
            sceneManager.destroy();
            sceneManager = null;
        }

        transitionManager = null;
        sceneLoader = null;
        viewportConfig = null;
    }

    /**
     * Shows a status message.
     */
    private void showMessage(String message) {
        if (messageCallback != null) {
            messageCallback.accept(message);
        }
    }

    /**
     * Checks if play mode is active (playing or paused).
     */
    public boolean isActive() {
        return state != PlayState.STOPPED;
    }

    /**
     * Checks if currently playing (not paused).
     */
    public boolean isPlaying() {
        return state == PlayState.PLAYING;
    }

    /**
     * Checks if paused.
     */
    public boolean isPaused() {
        return state == PlayState.PAUSED;
    }

    /**
     * Checks if stopped (editor mode).
     */
    public boolean isStopped() {
        return state == PlayState.STOPPED;
    }
}