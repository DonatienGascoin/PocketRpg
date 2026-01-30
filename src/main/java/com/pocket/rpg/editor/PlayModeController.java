package com.pocket.rpg.editor;

import com.pocket.rpg.audio.Audio;
import com.pocket.rpg.audio.music.MusicManager;
import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.SpriteRenderer;
import com.pocket.rpg.config.GameConfig;
import com.pocket.rpg.config.InputConfig;
import com.pocket.rpg.config.RenderingConfig;
import com.pocket.rpg.core.window.ViewportConfig;
import com.pocket.rpg.editor.rendering.EditorFramebuffer;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.scene.RuntimeGameObjectAdapter;
import com.pocket.rpg.editor.scene.RuntimeSceneLoader;
import com.pocket.rpg.editor.serialization.EditorSceneSerializer;
import com.pocket.rpg.rendering.core.RenderCamera;
import com.pocket.rpg.rendering.core.Renderable;
import com.pocket.rpg.rendering.pipeline.RenderParams;
import com.pocket.rpg.rendering.pipeline.RenderPipeline;
import com.pocket.rpg.rendering.postfx.PostProcessor;
import com.pocket.rpg.rendering.targets.FramebufferTarget;
import com.pocket.rpg.core.application.GameLoop;
import com.pocket.rpg.resources.Assets;
import com.pocket.rpg.scenes.RuntimeScene;
import com.pocket.rpg.scenes.Scene;
import com.pocket.rpg.scenes.SceneManager;
import com.pocket.rpg.scenes.transitions.SceneTransition;
import com.pocket.rpg.scenes.transitions.TransitionManager;
import com.pocket.rpg.serialization.GameObjectData;
import com.pocket.rpg.serialization.SceneData;
import com.pocket.rpg.serialization.Serializer;
import com.pocket.rpg.editor.events.EditorEventBus;
import com.pocket.rpg.editor.events.PlayModePausedEvent;
import com.pocket.rpg.editor.events.PlayModeStartedEvent;
import com.pocket.rpg.editor.events.PlayModeStoppedEvent;
import com.pocket.rpg.editor.events.SceneWillChangeEvent;
import com.pocket.rpg.input.Input;
import com.pocket.rpg.ui.UIInputHandler;
import lombok.Getter;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Controls Play Mode lifecycle in the Scene Editor.
 * <p>
 * Uses {@link GameLoop} for update coordination, ensuring scene
 * is frozen during transitions.
 * <p>
 * Uses {@link RenderPipeline} because Play Mode simulates the actual
 * game runtime, which requires:
 * <ul>
 *   <li>Post-processing effects (blur, vignette, etc.)</li>
 *   <li>UI canvas rendering</li>
 *   <li>Transition overlays</li>
 *   <li>Game camera (not editor camera)</li>
 * </ul>
 *
 * @see GameLoop
 * @see RenderPipeline
 */
public class PlayModeController {

    public enum PlayState {
        STOPPED,
        PLAYING,
        PAUSED
    }

    private final EditorContext context;
    private final GameConfig gameConfig;
    private final RenderingConfig renderingConfig;
    private final InputConfig inputConfig;

    @Getter
    private PlayState state = PlayState.STOPPED;

    // Editor scene backup
    private SceneData snapshot;
    private String snapshotFilePath;

    // Runtime systems
    private ViewportConfig viewportConfig;
    private RuntimeSceneLoader sceneLoader;
    private GameLoop gameLoop;
    private TransitionManager transitionManager;
    private PlayModeInputManager inputManager;
    private UIInputHandler uiInputHandler;

    // Play mode selection (separate from editor selection)
    @Getter
    private PlayModeSelectionManager playModeSelectionManager;

    // Display area within editor window (set by GameViewPanel each frame)
    private float displayX, displayY, displayWidth, displayHeight;

    // Rendering
    private RenderPipeline pipeline;
    private EditorFramebuffer outputFramebuffer;
    @Getter
    private PostProcessor postProcessor;

    private Consumer<String> messageCallback;


    public PlayModeController(EditorContext context, GameConfig gameConfig, InputConfig inputConfig) {
        this.context = context;
        this.gameConfig = gameConfig;
        this.renderingConfig = context.getRenderingConfig();
        this.inputConfig = inputConfig;

        // Stop play mode when scene is about to change
        EditorEventBus.get().subscribe(SceneWillChangeEvent.class, event -> {
            if (isActive()) {
                stop();
            }
        });
    }

    public void setMessageCallback(Consumer<String> callback) {
        this.messageCallback = callback;
    }

    // ========================================================================
    // STATE TRANSITIONS
    // ========================================================================

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
            // 1. Snapshot editor scene (deep copy via JSON serialization)
            // This ensures runtime cannot corrupt the snapshot
            SceneData tempData = EditorSceneSerializer.toSceneData(editorScene);
            snapshot = Serializer.deepCopy(tempData, SceneData.class);
            snapshotFilePath = editorScene.getFilePath();

            // 2. Create viewport config
            viewportConfig = new ViewportConfig(gameConfig);

            // 3. Create scene loader
            sceneLoader = new RuntimeSceneLoader();

            // 4. Initialize input
            long windowHandle = context.getWindow().getWindowHandle();
            inputManager = new PlayModeInputManager(windowHandle, inputConfig);
            inputManager.init();

            // 5. Create output framebuffer
            int width = gameConfig.getGameWidth();
            int height = gameConfig.getGameHeight();
            outputFramebuffer = new EditorFramebuffer(width, height);
            outputFramebuffer.init();

            // 6. Create post-processor if effects are configured
            if (gameConfig.getPostProcessingEffects() != null &&
                    !gameConfig.getPostProcessingEffects().isEmpty()) {
                postProcessor = new PostProcessor(gameConfig);
                postProcessor.init(context.getWindow());
            }

            // 7. Create render pipeline
            pipeline = new RenderPipeline(viewportConfig, renderingConfig);
            if (postProcessor != null) {
                pipeline.setPostProcessor(postProcessor);
            }
            pipeline.init();

            // 8. Create SceneManager with loader
            SceneManager sceneManager = new SceneManager(viewportConfig, renderingConfig);
            sceneManager.setSceneLoader(sceneLoader, "gameData/scenes/");

            // 9. Create TransitionManager
            transitionManager = new TransitionManager(
                    sceneManager,
                    pipeline.getOverlayRenderer(),
                    gameConfig.getDefaultTransitionConfig(),
                    gameConfig.getTransitions(),
                    gameConfig.getDefaultTransitionName()
            );
            pipeline.setTransitionManager(transitionManager);

            // 10. Initialize SceneTransition static API
            SceneTransition.forceInitialize(transitionManager);

            // 11. Create GameLoop
            gameLoop = new GameLoop(sceneManager, transitionManager);

            // 12. Create UI input handler
            uiInputHandler = new UIInputHandler(gameConfig);

            // 12. Initialize MusicManager for scene-based music
            MusicManager.initialize(sceneManager, Assets.getContext());

            // 13. Load initial scene from a copy (keeps snapshot pristine for restore)
            SceneData runtimeCopy = Serializer.deepCopy(snapshot, SceneData.class);
            RuntimeScene runtimeScene = sceneLoader.load(runtimeCopy);
            sceneManager.loadScene(runtimeScene);

            // 14. Create play mode selection manager
            playModeSelectionManager = new PlayModeSelectionManager();

            // 15. Switch state
            state = PlayState.PLAYING;
            EditorEventBus.get().publish(new PlayModeStartedEvent());

            showMessage("Play mode started");
            System.out.println("Play mode started: " + editorScene.getName());

        } catch (Exception e) {
            System.err.println("Failed to start play mode: " + e.getMessage());
            e.printStackTrace();
            cleanup();
            showMessage("Error: " + e.getMessage());
        }
    }

    public void pause() {
        if (state != PlayState.PLAYING) return;
        state = PlayState.PAUSED;
        Audio.pauseAll();
        EditorEventBus.get().publish(new PlayModePausedEvent());
        showMessage("Paused");
    }

    public void resume() {
        if (state != PlayState.PAUSED) return;
        state = PlayState.PLAYING;
        Audio.resumeAll();
        EditorEventBus.get().publish(new PlayModeStartedEvent());
        showMessage("Resumed");
    }

    public void stop() {
        if (state == PlayState.STOPPED) return;

        System.out.println("Stopping play mode...");

        cleanup();

        // No need to restore editor scene from snapshot:
        // Play mode uses a separate RuntimeScene and the editor update loop
        // is skipped while playing (EditorApplication returns early).
        // Keeping the original EditorScene preserves dirty flag and undo history.

        snapshot = null;
        snapshotFilePath = null;
        state = PlayState.STOPPED;
        EditorEventBus.get().publish(new PlayModeStoppedEvent());

        showMessage("Play mode stopped");
        System.out.println("Play mode stopped");
    }

    // ========================================================================
    // UPDATE / RENDER
    // ========================================================================

    public void update(float deltaTime) {
        if (state != PlayState.PLAYING) return;

        // Prune destroyed objects from selection once per frame
        if (playModeSelectionManager != null) {
            playModeSelectionManager.pruneDestroyedObjects();
        }

        // Input always captured
        if (inputManager != null) {
            inputManager.update(deltaTime);
        }

        // UI input (hover, clicks) - convert editor window mouse to game coordinates
        updateUIInput();

        // GameLoop handles transition freeze
        if (gameLoop != null) {
            gameLoop.update(deltaTime);
        }

        // End frame for input
        if (inputManager != null) {
            inputManager.endFrame();
        }
    }

    /**
     * Sets the display area of the game image within the editor window.
     * Called by GameViewPanel after rendering the game texture.
     * Uses previous frame's values, which is standard for input.
     */
    public void setDisplayArea(float x, float y, float width, float height) {
        this.displayX = x;
        this.displayY = y;
        this.displayWidth = width;
        this.displayHeight = height;
    }

    private void updateUIInput() {
        if (uiInputHandler == null || gameLoop == null) return;
        if (displayWidth <= 0 || displayHeight <= 0) return;

        // Convert editor window mouse position to game coordinates
        var mousePos = Input.getMousePosition();
        float gameMouseX = (mousePos.x - displayX) / displayWidth * gameConfig.getGameWidth();
        float gameMouseY = (mousePos.y - displayY) / displayHeight * gameConfig.getGameHeight();

        Scene currentScene = gameLoop.getSceneManager().getCurrentScene();
        if (currentScene != null) {
            uiInputHandler.update(currentScene.getUICanvases(), gameMouseX, gameMouseY);
        }
    }

    public void render() {
        if (state == PlayState.STOPPED) return;
        if (pipeline == null || outputFramebuffer == null || gameLoop == null) return;

        Scene scene = gameLoop.getSceneManager().getCurrentScene();
        if (scene == null) return;

        // Get renderables and camera from scene
        List<Renderable> renderables = scene.getRenderers();
        RenderCamera camera = scene.getCamera();

        // Create target
        FramebufferTarget target = new FramebufferTarget(outputFramebuffer);

        // Build params with full pipeline
        RenderParams params = RenderParams.builder()
                .renderables(renderables)
                .camera(camera)
                .uiCanvases(scene.getUICanvases())
                .clearColor(renderingConfig.getClearColor())
                .renderScene(true)
                .renderUI(true)
                .renderPostFx(postProcessor != null)
                .renderOverlay(true)
                .build();

        // Execute pipeline
        pipeline.execute(target, params);
    }

    public int getOutputTexture() {
        return outputFramebuffer != null ? outputFramebuffer.getTextureId() : 0;
    }

    public int getGameWidth() {
        return gameConfig.getGameWidth();
    }

    public int getGameHeight() {
        return gameConfig.getGameHeight();
    }

    // ========================================================================
    // HELPERS
    // ========================================================================

    private void cleanup() {
        // Stop music playback
        if (Audio.music() != null) {
            Audio.music().stop();
        }

        if (inputManager != null) {
            inputManager.destroy();
            inputManager = null;
        }

        if (gameLoop != null) {
            gameLoop.destroy();
            gameLoop = null;
        }

        if (pipeline != null) {
            pipeline.destroy();
            pipeline = null;
        }

        if (postProcessor != null) {
            postProcessor.destroy();
            postProcessor = null;
        }

        if (outputFramebuffer != null) {
            outputFramebuffer.destroy();
            outputFramebuffer = null;
        }

        if (playModeSelectionManager != null) {
            playModeSelectionManager.clearSelection();
            playModeSelectionManager = null;
        }
        RuntimeGameObjectAdapter.clearCache();

        uiInputHandler = null;
        transitionManager = null;
        sceneLoader = null;
        viewportConfig = null;
    }

    private void showMessage(String message) {
        if (messageCallback != null) {
            messageCallback.accept(message);
        }
    }

    /**
     * Returns the currently running runtime scene, or null if not in play mode.
     */
    public Scene getRuntimeScene() {
        return gameLoop != null ? gameLoop.getSceneManager().getCurrentScene() : null;
    }

    public boolean isActive() {
        return state != PlayState.STOPPED;
    }

    public boolean isPlaying() {
        return state == PlayState.PLAYING;
    }

    public boolean isPaused() {
        return state == PlayState.PAUSED;
    }

    public boolean isStopped() {
        return state == PlayState.STOPPED;
    }

    /**
     * Gets all available scene names from the scenes directory.
     *
     * @return list of scene names (without .scene extension)
     */
    public List<String> getAvailableScenes() {
        List<String> scenes = new ArrayList<>();
        File scenesDir = new File("gameData/scenes/");

        if (scenesDir.exists() && scenesDir.isDirectory()) {
            File[] files = scenesDir.listFiles((dir, name) -> name.endsWith(".scene"));
            if (files != null) {
                for (File file : files) {
                    String name = file.getName();
                    // Remove .scene extension
                    scenes.add(name.substring(0, name.length() - 6));
                }
            }
        }

        return scenes;
    }

    /**
     * Gets the transition manager (only available during play mode).
     *
     * @return the transition manager, or null if not in play mode
     */
    public TransitionManager getTransitionManager() {
        return transitionManager;
    }
}
