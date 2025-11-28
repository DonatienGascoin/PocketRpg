package com.pocket.rpg.core;

import com.pocket.rpg.config.WindowConfig;
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
import lombok.Setter;

public class GameEngine {
    private final WindowConfig config;

    // Platform-independent systems (owned by engine)
    private SceneManager sceneManager;

    // Platform-dependent systems (injected from outside)
    @Setter
    private RenderInterface renderer;
    @Setter
    private CameraManager cameraManager;

    public GameEngine(WindowConfig config) {
        this.config = config;
    }

    public void initialize() {
        System.out.println(LogUtils.buildBox("INITIALIZING GAME ENGINE"));

        if (renderer == null) {
            throw new IllegalStateException("Renderer must be injected before init");
        }

        if (cameraManager == null) {
            throw new IllegalStateException("CameraManager must be injected before init");
        }

        // Initialize AssetManager
        AssetManager.initialize();

        AssetManager manager = AssetManager.getInstance();
        manager.registerLoader("texture", new TextureLoader());
        manager.registerLoader("shader", new ShaderLoader());
        manager.registerLoader("sprite", new SpriteLoader());
        manager.registerLoader("spritesheet", new SpriteSheetLoader());

        // Set viewport to window size. TODO: Already done in GameApplication, where to put it?
        CameraManager.setViewportSize(config.getWindowWidth(), config.getWindowHeight());

        ConsoleStatisticsReporter reporter = null; // TODO: Merge with performance monitor?
        if (config.isEnableStatistics()) {
            reporter = new ConsoleStatisticsReporter(config.getStatisticsInterval());
        }
        config.setReporter(reporter);

        renderer.init(config.getGameWidth(), config.getGameHeight());

        // Initialize scene manager
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

        System.out.println("Game engine initialized successfully");
    }

    /**
     * Update game state.
     * Called every frame by the application.
     */
    public void update(float deltaTime) {
        // Update AssetManager
        AssetManager.getInstance().update(deltaTime);
//        if (audio != null) audio.update(deltaTime);
        // Update scene
        sceneManager.update(deltaTime);
    }

    /**
     * Render current scene.
     * Called every frame by the application.
     */
    public void render() {
        if (renderer != null && sceneManager.getCurrentScene() != null) {
            renderer.render(sceneManager.getCurrentScene());
        }
    }

    public void destroy() {
        System.out.println("Destroying game engine...");

        if (sceneManager != null) {
            sceneManager.destroy();
        }

        if (renderer != null) {
            renderer.destroy();
        }

        CameraManager.destroy();
        AssetManager.destroy();

        System.out.println("Game engine destroyed");
    }
}
