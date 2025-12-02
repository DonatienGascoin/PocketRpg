package com.pocket.rpg.core;

import com.pocket.rpg.config.EngineConfiguration;
import com.pocket.rpg.input.events.InputEventBus;
import com.pocket.rpg.postProcessing.PostProcessor;
import com.pocket.rpg.rendering.CameraSystem;
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
import lombok.Builder;
import lombok.NonNull;

@Builder
public class GameEngine {
    // Configuration
    @NonNull
    private final EngineConfiguration config;

    // Platform-independent systems (owned by engine)
    private SceneManager sceneManager;
    private PerformanceMonitor performanceMonitor;

    // Systems injected from outside
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

    /**
     * Initializes the game engine and all its subsystems.
     */
    public void initialize() {
        System.out.println(LogUtils.buildBox("Initializing Game Engine"));

        initAssetLoader();

        ConsoleStatisticsReporter reporter = null; // TODO: Merge with performance monitor?
        if (config.getRendering().isEnableStatistics()) {
            reporter = new ConsoleStatisticsReporter(config.getRendering().getStatisticsInterval());
        }

        // audio.initialize();

        // Initialize scene manager
        initSceneManager();

        System.out.println("Game engine initialized successfully");
    }


    /**
     * Update game state.
     * Called every frame by the application.
     */
    public void update(float deltaTime) {
        AssetManager.getInstance().update(deltaTime);
//      audio.update(deltaTime);
        // Update scene
        sceneManager.update(deltaTime);
    }

    /**
     * Render current scene.
     * Called every frame by the application.
     */
    public void render() {
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

        AssetManager.destroy();

        System.out.println("Game engine destroyed");
    }

    private void initSceneManager() {
        sceneManager = new SceneManager(cameraSystem);

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

    private static void initAssetLoader() {
        AssetManager.initialize();

        AssetManager manager = AssetManager.getInstance();
        manager.registerLoader("texture", new TextureLoader());
        manager.registerLoader("shader", new ShaderLoader());
        manager.registerLoader("sprite", new SpriteLoader());
        manager.registerLoader("spritesheet", new SpriteSheetLoader());
    }
}
