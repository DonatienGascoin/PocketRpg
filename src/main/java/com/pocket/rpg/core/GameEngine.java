package com.pocket.rpg.core;

import com.pocket.rpg.config.EngineConfiguration;
import com.pocket.rpg.input.events.InputEventBus;
import com.pocket.rpg.postProcessing.PostProcessor;
import com.pocket.rpg.rendering.CameraSystem;
import com.pocket.rpg.rendering.OverlayRenderer;
import com.pocket.rpg.rendering.renderers.RenderInterface;
import com.pocket.rpg.resources.AssetManager;
import com.pocket.rpg.resources.loaders.ShaderLoader;
import com.pocket.rpg.resources.loaders.SpriteLoader;
import com.pocket.rpg.resources.loaders.SpriteSheetLoader;
import com.pocket.rpg.resources.loaders.TextureLoader;
import com.pocket.rpg.scenes.*;
import com.pocket.rpg.transitions.SceneTransition;
import com.pocket.rpg.transitions.TransitionManager;
import com.pocket.rpg.ui.UIInputHandler;
import com.pocket.rpg.ui.UIRenderer;
import com.pocket.rpg.utils.LogUtils;
import com.pocket.rpg.utils.PerformanceMonitor;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;

@Builder
public class GameEngine {
    @NonNull
    private final EngineConfiguration config;

    private SceneManager sceneManager;
    private TransitionManager transitionManager;

    private PerformanceMonitor performanceMonitor;

    @NonNull
    private final CameraSystem cameraSystem;
    @NonNull
    private final AbstractWindow window;
    @NonNull
    private final RenderInterface renderer;
    @NonNull
    private final InputEventBus inputEventBus;
    @NonNull
    private final PostProcessor postProcessor;

    // UI System - injected via builder
    @NonNull
    @Getter
    private final UIRenderer uiRenderer;

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

    public void initialize() {
        System.out.println(LogUtils.buildBox("Initializing Game Engine"));

        initAssetLoader();
        initSceneManager();
        initTransitionSystem();
        initUISystem();

        System.out.println("Game engine initialized successfully");
    }

    private void initUISystem() {
        System.out.println("Initializing UI system...");

        uiRenderer.init(config.getGame());
        uiRenderer.setViewportSize(window.getScreenWidth(), window.getScreenHeight());

        // Listen for resize events
        inputEventBus.addResizeListener(uiRenderer::setViewportSize);

        System.out.println("UI system initialized");
    }

    private void initTransitionSystem() {
        System.out.println("Initializing transition system...");

        OverlayRenderer overlayRenderer = renderer.getOverlayRenderer();
        overlayRenderer.init();
        overlayRenderer.setScreenSize(window.getScreenWidth(), window.getScreenHeight());
        inputEventBus.addResizeListener(overlayRenderer::setScreenSize);
        transitionManager = new TransitionManager(
                sceneManager,
                overlayRenderer,
                config.getGame().getDefaultTransitionConfig()
        );

        SceneTransition.initialize(transitionManager);

        System.out.println("Transition system initialized");
    }

    public void update(float deltaTime) {
        AssetManager.getInstance().update(deltaTime);
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

    public void render() {
        if (sceneManager.getCurrentScene() != null) {
            renderer.render(sceneManager.getCurrentScene());
        }
        transitionManager.render();
    }

    /**
     * Renders UI after post-processing.
     * Called from GameApplication after postProcessor.endCaptureAndApplyEffects().
     */
    public void renderUI() {
        if (sceneManager.getCurrentScene() != null) {
            uiRenderer.render(sceneManager.getCurrentScene().getUICanvases());
        }
    }

    public void destroy() {
        System.out.println("Destroying game engine...");

        uiRenderer.destroy();

        if (sceneManager != null) {
            sceneManager.destroy();
        }

        AssetManager.destroy();

        System.out.println("Game engine destroyed");
    }

    private void initSceneManager() {
        sceneManager = new SceneManager(cameraSystem);

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
//        sceneManager.loadScene("ExampleScene");
        sceneManager.loadScene("Demo");
    }

    private static void initAssetLoader() {
        AssetManager.initialize();

        AssetManager manager = AssetManager.getInstance();
        manager.registerLoader("texture", new TextureLoader());
        manager.registerLoader("shader", new ShaderLoader());
        manager.registerLoader("sprite", new SpriteLoader());
        manager.registerLoader("spritesheet", new SpriteSheetLoader());
    }
}