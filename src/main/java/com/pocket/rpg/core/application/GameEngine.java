package com.pocket.rpg.core.application;

import com.pocket.rpg.config.EngineConfiguration;
import com.pocket.rpg.core.window.AbstractWindow;
import com.pocket.rpg.core.window.ViewportConfig;
import com.pocket.rpg.input.events.InputEventBus;
import com.pocket.rpg.rendering.pipeline.RenderParams;
import com.pocket.rpg.rendering.pipeline.RenderPipeline;
import com.pocket.rpg.rendering.targets.ScreenTarget;
import com.pocket.rpg.scenes.*;
import com.pocket.rpg.scenes.transitions.SceneTransition;
import com.pocket.rpg.scenes.transitions.TransitionManager;
import com.pocket.rpg.ui.UIInputHandler;
import com.pocket.rpg.utils.LogUtils;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;

/**
 * Core game engine managing scenes, rendering, and game systems.
 * <p>
 * <b>UNIFIED RENDERING NOTE:</b>
 * This class now uses {@link RenderPipeline} directly for all rendering.
 * The pipeline handles: Scene → Post-FX → UI → Overlay in a single call.
 * <p>
 * Replaces the previous manual orchestration where GameApplication called
 * separate methods for scene, post-fx, and UI rendering.
 *
 * @see RenderPipeline
 */
@Builder
public class GameEngine {
    @NonNull
    private final EngineConfiguration config;

    @NonNull
    private final ViewportConfig viewportConfig;

    @NonNull
    private final AbstractWindow window;

    @NonNull
    private final InputEventBus inputEventBus;

    // ========================================================================
    // UNIFIED RENDERING (Phase 5b)
    // ========================================================================

    /**
     * Unified render pipeline - handles all rendering stages.
     */
    @NonNull
    private final RenderPipeline pipeline;

    /**
     * Screen target for rendering to default framebuffer.
     */
    @NonNull
    private final ScreenTarget screenTarget;

    // ========================================================================
    // GAME SYSTEMS
    // ========================================================================

    private SceneManager sceneManager;
    private TransitionManager transitionManager;

    // UI Input Handler - processes mouse input for UI (optional, can be set later)
    @Getter
    @Builder.Default
    private UIInputHandler uiInputHandler = null;

    /**
     * Sets the UI input handler. Call after constructing GameEngine if not provided in builder.
     */
    public void setUIInputHandler(UIInputHandler handler) {
        this.uiInputHandler = handler;
    }

    // ========================================================================
    // INITIALIZATION
    // ========================================================================

    public void initialize() {
        System.out.println(LogUtils.buildBox("Initializing Game Engine"));

        initSceneManager();
        initTransitionSystem();
        initUISystem();

        System.out.println("Game engine initialized successfully");
    }

    private void initSceneManager() {
        sceneManager = new SceneManager(viewportConfig, config.getRendering());

        sceneManager.addLifecycleListener(new SceneLifecycleListener() {
            @Override
            public void onSceneLoaded(Scene scene) {
                System.out.println("Scene loaded: " + scene.getName());
            }

            @Override
            public void onSceneUnloaded(Scene scene) {
                System.out.println("Scene unloaded: " + scene.getName());
            }
        });

        sceneManager.registerScene(new ExampleScene());
        sceneManager.registerScene(new DemoScene());
        sceneManager.loadScene("Demo");
    }

    private void initTransitionSystem() {
        System.out.println("Initializing transition system...");

        // TransitionManager uses pipeline's overlay renderer
        transitionManager = new TransitionManager(
                sceneManager,
                pipeline.getOverlayRenderer(),
                config.getGame().getDefaultTransitionConfig()
        );

        // Set on pipeline so execute() can render transitions
        pipeline.setTransitionManager(transitionManager);

        // Register resize listener for overlay
        inputEventBus.addResizeListener((w, h) -> {
            if (pipeline.getOverlayRenderer() != null) {
                pipeline.getOverlayRenderer().setScreenSize(w, h);
            }
        });

        SceneTransition.initialize(transitionManager);

        System.out.println("Transition system initialized");
    }

    private void initUISystem() {
        System.out.println("Initializing UI system...");

        // UI renderer is owned by pipeline - no separate init needed
        // Pipeline's UIRenderer is initialized during pipeline.init()

        System.out.println("UI system initialized");
    }

    // ========================================================================
    // UPDATE
    // ========================================================================

    public void update(float deltaTime) {
        transitionManager.update(deltaTime);
        sceneManager.update(deltaTime);
    }

    /**
     * Updates UI input handling. Call BEFORE game input processing.
     * Sets Input.mouseConsumed if UI elements are under cursor.
     *
     * @param mouseX Mouse X in game coordinates (not screen coordinates)
     * @param mouseY Mouse Y in game coordinates (not screen coordinates)
     */
    public void updateUIInput(float mouseX, float mouseY) {
        if (uiInputHandler == null) return;

        Scene currentScene = sceneManager.getCurrentScene();
        if (currentScene != null) {
            uiInputHandler.update(currentScene.getUICanvases(), mouseX, mouseY);
        }
    }

    /**
     * Resets UI input state. Call when changing scenes.
     */
    public void resetUIInput() {
        if (uiInputHandler != null) {
            uiInputHandler.reset();
        }
    }

    // ========================================================================
    // RENDERING (Unified)
    // ========================================================================

    /**
     * Renders a complete frame using the unified pipeline.
     * <p>
     * Pipeline stages:
     * <ol>
     *   <li>Clear screen</li>
     *   <li>Scene rendering (with optional post-fx capture)</li>
     *   <li>Post-processing effects</li>
     *   <li>UI canvases</li>
     *   <li>Overlay (transitions)</li>
     * </ol>
     */
    public void renderFrame() {
        Scene scene = sceneManager.getCurrentScene();
        if (scene == null) return;

        // Build render params for full pipeline
        RenderParams params = RenderParams.builder()
                .renderables(scene.getRenderers())
                .camera(scene.getCamera())
                .uiCanvases(scene.getUICanvases())
                .clearColor(config.getRendering().getClearColor())
                .renderScene(true)
                .renderUI(true)
                .renderPostFx(pipeline.hasPostProcessingEffects())
                .renderOverlay(true)
                .build();

        // Execute full pipeline to screen
        pipeline.execute(screenTarget, params);
    }

    // ========================================================================
    // LIFECYCLE
    // ========================================================================

    public void destroy() {
        System.out.println("Destroying game engine...");

        if (sceneManager != null) {
            sceneManager.destroy();
        }

        // Pipeline destroyed by GameApplication (it owns it)

        System.out.println("Game engine destroyed");
    }

    // ========================================================================
    // ACCESSORS
    // ========================================================================

    public SceneManager getSceneManager() {
        return sceneManager;
    }

    public TransitionManager getTransitionManager() {
        return transitionManager;
    }

    public RenderPipeline getPipeline() {
        return pipeline;
    }
}
