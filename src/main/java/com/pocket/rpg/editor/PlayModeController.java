package com.pocket.rpg.editor;

import com.pocket.rpg.config.GameConfig;
import com.pocket.rpg.config.InputConfig;
import com.pocket.rpg.config.RenderingConfig;
import com.pocket.rpg.core.camera.GameCamera;
import com.pocket.rpg.core.window.ViewportConfig;
import com.pocket.rpg.editor.rendering.EditorFramebuffer;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.scene.RuntimeSceneLoader;
import com.pocket.rpg.editor.scene.RuntimeSceneManager;
import com.pocket.rpg.editor.serialization.EditorSceneSerializer;
import com.pocket.rpg.rendering.postfx.PostProcessor;
import com.pocket.rpg.rendering.targets.FramebufferTarget;
import com.pocket.rpg.rendering.core.RenderCamera;
import com.pocket.rpg.rendering.pipeline.RenderParams;
import com.pocket.rpg.rendering.pipeline.RenderPipeline;
import com.pocket.rpg.rendering.core.Renderable;
import com.pocket.rpg.resources.Assets;
import com.pocket.rpg.scenes.RuntimeScene;
import com.pocket.rpg.scenes.Scene;
import com.pocket.rpg.serialization.SceneData;
import com.pocket.rpg.scenes.transitions.SceneTransition;
import com.pocket.rpg.scenes.transitions.TransitionManager;
import lombok.Getter;
import org.joml.Vector4f;

import java.util.List;
import java.util.function.Consumer;

/**
 * Controls Play Mode lifecycle in the Scene Editor.
 * <p>
 * <b>RENDERING ARCHITECTURE NOTE:</b>
 * This class uses {@link RenderPipeline} (UnifiedRenderer) because Play Mode
 * simulates the actual game runtime, which requires:
 * <ul>
 *   <li>Post-processing effects (blur, vignette, etc.)</li>
 *   <li>UI canvas rendering</li>
 *   <li>Transition overlays</li>
 *   <li>Game camera (not editor camera)</li>
 * </ul>
 * <p>
 * This is different from {@code EditorSceneRenderer} which handles editor-specific
 * features like layer dimming and selection highlighting.
 * <p>
 * Replaces: {@code PlayModeRenderer} (to be deleted in Phase 6)
 *
 * @see RenderPipeline
 * @see com.pocket.rpg.editor.rendering.EditorSceneRenderer
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
    private RuntimeSceneManager sceneManager;
    private TransitionManager transitionManager;
    private PlayModeInputManager inputManager;

    // Rendering
    private RenderPipeline pipeline;
    private EditorFramebuffer outputFramebuffer;
    private PostProcessor postProcessor;

    private Consumer<String> messageCallback;

    private static final Vector4f CLEAR_COLOR = new Vector4f(0.1f, 0.1f, 0.15f, 1.0f);

    public PlayModeController(EditorContext context, GameConfig gameConfig, InputConfig inputConfig) {
        this.context = context;
        this.gameConfig = gameConfig;
        this.renderingConfig = context.getRenderingConfig();
        this.inputConfig = inputConfig;
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
            // 1. Snapshot editor scene
            snapshot = EditorSceneSerializer.toSceneData(editorScene);
            snapshotFilePath = editorScene.getFilePath();

            // 2. Create viewport config
            viewportConfig = new ViewportConfig(gameConfig);

            // 3. Create scene loader and manager
            sceneLoader = new RuntimeSceneLoader(viewportConfig, renderingConfig);
            sceneManager = new RuntimeSceneManager(
                    viewportConfig,
                    renderingConfig,
                    sceneLoader,
                    "gameData/scenes/" // TODO: Extract to GameConfig
            );

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

            // 8. Create transition manager and set on pipeline
            transitionManager = new TransitionManager(
                    sceneManager,
                    pipeline.getOverlayRenderer(),
                    gameConfig.getDefaultTransitionConfig()
            );
            pipeline.setTransitionManager(transitionManager);

            // 9. Initialize SceneTransition API
            SceneTransition.forceInitialize(transitionManager);

            // 10. Load initial scene
            RuntimeScene runtimeScene = sceneLoader.load(snapshot);
            sceneManager.loadScene(runtimeScene);

            // 11. Switch state
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

    public void pause() {
        if (state != PlayState.PLAYING) return;
        state = PlayState.PAUSED;
        showMessage("Paused");
    }

    public void resume() {
        if (state != PlayState.PAUSED) return;
        state = PlayState.PLAYING;
        showMessage("Resumed");
    }

    public void stop() {
        if (state == PlayState.STOPPED) return;

        System.out.println("Stopping play mode...");

        cleanup();

        // Restore editor scene
        if (snapshot != null) {
            try {
                EditorScene restored = EditorSceneSerializer.fromSceneData(snapshot, snapshotFilePath);
                context.setCurrentScene(restored);
            } catch (Exception e) {
                System.err.println("Failed to restore editor scene: " + e.getMessage());
                context.setCurrentScene(new EditorScene("Recovered"));
            }
        }

        snapshot = null;
        snapshotFilePath = null;
        state = PlayState.STOPPED;

        showMessage("Play mode stopped");
        System.out.println("Play mode stopped");
    }

    // ========================================================================
    // UPDATE / RENDER
    // ========================================================================

    public void update(float deltaTime) {
        if (state != PlayState.PLAYING) return;

        if (inputManager != null) {
            inputManager.update(deltaTime);
        }

        if (transitionManager != null) {
            transitionManager.update(deltaTime);
        }

        if (sceneManager != null) {
            sceneManager.update(deltaTime);
        }

        if (inputManager != null) {
            inputManager.endFrame();
        }
    }

    public void render() {
        if (state == PlayState.STOPPED) return;
        if (pipeline == null || outputFramebuffer == null) return;

        Scene scene = sceneManager != null ? sceneManager.getCurrentScene() : null;
        if (scene == null) return;

        // Get renderables and camera from scene
        // Note: Scene.getRenderers() returns List<Renderable>
        List<Renderable> renderables = scene.getRenderers();

        // Scene.getCamera() returns GameCamera which should implement RenderCamera
        RenderCamera camera = scene.getCamera();

        // Create target
        FramebufferTarget target = new FramebufferTarget(outputFramebuffer);

        // Build params with full pipeline (transitionManager is on pipeline)
        RenderParams params = RenderParams.builder()
                .renderables(renderables)
                .camera(camera)
                .uiCanvases(scene.getUICanvases())
                .clearColor(CLEAR_COLOR)
                .renderScene(true)
                .renderUI(true)
                .renderPostFx(postProcessor != null)
                .renderOverlay(true)
                .build();

        // Execute pipeline
        pipeline.execute(target, params);

//        System.out.println("[PlayMode] orthoSize=" + ((GameCamera)camera).getOrthographicSize() +
//                ", zoom=" + ((GameCamera)camera).getZoom());
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
        if (inputManager != null) {
            inputManager.destroy();
            inputManager = null;
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

        if (sceneManager != null) {
            sceneManager.destroy();
            sceneManager = null;
        }

        transitionManager = null;
        sceneLoader = null;
        viewportConfig = null;
    }

    private void showMessage(String message) {
        if (messageCallback != null) {
            messageCallback.accept(message);
        }
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
}