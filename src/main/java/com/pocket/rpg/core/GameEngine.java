package com.pocket.rpg.core;

import com.pocket.rpg.config.GameConfig;
import com.pocket.rpg.config.InputConfig;
import com.pocket.rpg.config.RenderingConfig;
import com.pocket.rpg.input.InputManager;
import com.pocket.rpg.input.callbacks.DefaultInputCallback;
import com.pocket.rpg.input.listeners.KeyListener;
import com.pocket.rpg.input.listeners.MouseListener;
import com.pocket.rpg.postProcessing.PostProcessor;
import com.pocket.rpg.rendering.CameraManager;
import com.pocket.rpg.rendering.renderers.RenderInterface;
import com.pocket.rpg.rendering.stats.ConsoleStatisticsReporter;
import com.pocket.rpg.resources.AssetManager;
import com.pocket.rpg.resources.loaders.ShaderLoader;
import com.pocket.rpg.resources.loaders.SpriteLoader;
import com.pocket.rpg.resources.loaders.SpriteSheetLoader;
import com.pocket.rpg.resources.loaders.TextureLoader;
import com.pocket.rpg.scenes.ExampleScene;
import com.pocket.rpg.scenes.Scene;
import com.pocket.rpg.scenes.SceneLifecycleListener;
import com.pocket.rpg.scenes.SceneManager;
import com.pocket.rpg.utils.LogUtils;
import com.pocket.rpg.utils.PerformanceMonitor;
import com.pocket.rpg.utils.Time;
import lombok.Builder;
import lombok.NonNull;

@Builder
public class GameEngine {
    // Configuration
    @NonNull
    private final InputConfig inputConfig;
    @NonNull
    private final GameConfig gameConfig;
    @NonNull
    private final RenderingConfig renderingConfig;

    // Platform-independent systems (owned by engine)
    private SceneManager sceneManager;
    private CameraManager cameraManager;
    private PerformanceMonitor performanceMonitor;

    // Platform-dependent systems (injected from outside)
    @NonNull
    private final AbstractWindow window;
    @NonNull
    private final RenderInterface renderer;
    @NonNull
    private final DefaultInputCallback inputCallbacks;
    @NonNull
    private final PostProcessor postProcessor;

    /**
     * Initializes the game engine and all its subsystems.
     */
    public void initialize() {
        System.out.println(LogUtils.buildBox("Initializing Game Engine"));

        Time.init();

        initAssetLoader();

        ConsoleStatisticsReporter reporter = null; // TODO: Merge with performance monitor?
        if (renderingConfig.isEnableStatistics()) {
            reporter = new ConsoleStatisticsReporter(renderingConfig.getStatisticsInterval());
        }


        initInputSystem();
        initCameraSystem();

        // audio.initialize();
        renderer.init(gameConfig.getGameWidth(), gameConfig.getGameHeight());
        postProcessor.init(window);

        initMonitoring();

        // Initialize scene manager
        initSceneManager();

        System.out.println("Game engine initialized successfully");
    }


    /**
     * Main application loop.
     * Runs until the window is closed.
     */
    public void loop() {
        System.out.println("Starting main loop...");

        while (!window.shouldClose()) {
            // Handle minimized window
            if (handleMinimizedWindow()) {
                continue;
            }

            // Begin post-processing capture
            postProcessor.beginCapture();

            // Clear screen
            renderer.clear();

            // Update and render game
            try {
                update(Time.deltaTime());
                render();
            } catch (Exception e) {
                System.err.println("ERROR in game loop: " + e.getMessage());
                e.printStackTrace();
            }

            // Apply post-processing
            postProcessor.endCaptureAndApplyEffects();

            // Swap buffers and poll events
            window.swapBuffers();
            window.pollEvents();

            InputManager.endFrame();

            // Update time and performance, last things in loop
            Time.update();
            performanceMonitor.update();
        }

        System.out.println("Exited main loop");
    }

    /**
     * Update game state.
     * Called every frame by the application.
     */
    private void update(float deltaTime) {
        AssetManager.getInstance().update(deltaTime);
//      audio.update(deltaTime);
        // Update scene
        sceneManager.update(deltaTime);
    }

    /**
     * Render current scene.
     * Called every frame by the application.
     */
    private void render() {
        if (sceneManager.getCurrentScene() != null) {
            renderer.render(sceneManager.getCurrentScene());
        }
    }

    /**
     * Clean up all resources used by the game engine.
     */
    public void destroy() {
        System.out.println("Destroying game engine...");

        if (sceneManager != null) {
            sceneManager.destroy();
        }

        renderer.destroy();
        postProcessor.destroy();
        window.destroy();

        InputManager.destroy();

        CameraManager.destroy();
        AssetManager.destroy();

        System.out.println("Game engine destroyed");
    }

    private boolean handleMinimizedWindow() {
        if (!window.isVisible()) {
            window.pollEvents();
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return true;
            }
            Time.update();
            return true;
        }
        return false;
    }

    private void initCameraSystem() {
        // 3. Initialize camera system
        System.out.println("Initializing camera system...");
        cameraManager = CameraManager.initialize(gameConfig.getGameWidth(), gameConfig.getGameHeight());
        CameraManager.setViewportSize(window.getScreenWidth(), window.getScreenHeight());
        inputCallbacks.addWindowResizeCallback(cameraManager);
    }

    private void initSceneManager() {
        sceneManager = new SceneManager();

        // Add scene lifecycle listener
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

        // Register test scenes
        sceneManager.registerScene(new ExampleScene());

        // Load first scene
        sceneManager.loadScene("ExampleScene");
    }

    private void initInputSystem() {
        KeyListener keyListener = new KeyListener();
        inputCallbacks.addKeyCallback(keyListener);
        MouseListener mouseListener = new MouseListener();
        inputCallbacks.addMouseButtonCallback(mouseListener);
        inputCallbacks.addMousePosCallback(mouseListener);
        inputCallbacks.addMouseScrollCallback(mouseListener);

        InputManager.initialize(inputConfig, keyListener, mouseListener);
    }

    private void initMonitoring() {
        performanceMonitor = new PerformanceMonitor();
        performanceMonitor.setEnabled(renderingConfig.isEnableStatistics());
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
