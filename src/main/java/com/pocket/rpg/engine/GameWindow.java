package com.pocket.rpg.engine;

import com.pocket.rpg.postProcessing.PostProcessor;
import com.pocket.rpg.postProcessing.postEffects.VignetteEffect;
import com.pocket.rpg.rendering.BatchRenderer;
import com.pocket.rpg.rendering.CameraSystem;
import com.pocket.rpg.rendering.RenderPipeline;
import com.pocket.rpg.rendering.Renderer;
import com.pocket.rpg.resources.AssetManager;
import com.pocket.rpg.resources.loaders.ShaderLoader;
import com.pocket.rpg.resources.loaders.SpriteLoader;
import com.pocket.rpg.resources.loaders.SpriteSheetLoader;
import com.pocket.rpg.resources.loaders.TextureLoader;
import com.pocket.rpg.scenes.*;
import com.pocket.rpg.utils.DefaultCallback;
import com.pocket.rpg.utils.WindowConfig;

import java.util.List;

/**
 * Main game window with integrated camera system and render pipeline.
 * FIXED: Now properly uses fixed game resolution for pixel-perfect rendering.
 */
public class GameWindow extends Window {

    private Renderer renderer;
    private RenderPipeline renderPipeline;
    private SceneManager sceneManager;

    public GameWindow() {
        super(WindowConfig.builder()
                // Window size (can be resized by user)
                .initialWidth(640)
                .initialHeight(480)
                // Fixed game resolution (never changes)
                .gameWidth(640)
                .gameHeight(480)

                .title("Pocket RPG Engine")
                .vsync(true)
                .scalingMode(PostProcessor.ScalingMode.MAINTAIN_ASPECT_RATIO)
                .enablePillarBox(true)
                // Pillarbox aspect ratio will auto-calculate from game resolution (4:3)
                .postProcessingEffects(List.of(new VignetteEffect(1f, 1.5f)))
                .callback(new DefaultCallback().registerResizeCallback(CameraSystem::setViewportSize))
                .build());
    }

    @Override
    protected void initGame() {
        System.out.println("╔═══════════════════════════════════════════════════════════════╗");
        System.out.println("║                       POCKET RPG ENGINE                       ║");
        System.out.println("╠═══════════════════════════════════════════════════════════════╣");
        System.out.println("║  Initializing game systems...                                 ║");
        System.out.println("╚═══════════════════════════════════════════════════════════════╝");
        System.out.println("Initializing game systems...");
        System.out.println("Window size: " + getScreenWidth() + "x" + getScreenHeight());
        System.out.println("Game resolution: " + config.getGameWidth() + "x" + config.getGameHeight());

        // Initialize AssetManager
        AssetManager.initialize();

        AssetManager manager = AssetManager.getInstance();
        manager.registerLoader("texture", new TextureLoader());
        manager.registerLoader("shader", new ShaderLoader());
        manager.registerLoader("sprite", new SpriteLoader());
        manager.registerLoader("spritesheet", new SpriteSheetLoader());

        CameraSystem.initialize(config.getGameWidth(), config.getGameHeight());
        CameraSystem cameraSystem = CameraSystem.getInstance();

        if (cameraSystem == null) {
            throw new IllegalStateException("Failed to initialize CameraSystem");
        }

        // Set viewport to window size
        CameraSystem.setViewportSize(getScreenWidth(), getScreenHeight());

        renderer = new BatchRenderer();
        renderer.init(config.getGameWidth(), config.getGameHeight());

        // Create render pipeline
        renderPipeline = new RenderPipeline(renderer, cameraSystem);
//        renderPipeline.setStatisticsReporter(new ConsoleStatisticsReporter(60));

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
        sceneManager.registerScene(new SmallOptimizationTestScene());
        sceneManager.registerScene(new LargePerformanceBenchmarkScene());
//        sceneManager.registerScene(new ExampleScene());
        sceneManager.registerScene(new ExampleInputSystemScene());
        sceneManager.registerScene(new Phase2TestScene());

        // Load first scene
//        sceneManager.loadScene("ExampleScene");
        sceneManager.loadScene("Phase2Test");
//        sceneManager.loadScene("LargePerformanceBenchmark");

        System.out.println("Game systems initialized successfully");
        System.out.println("Active cameras: " + CameraSystem.getCameraCount());
        System.out.println("Pixel-perfect mode: ENABLED");
        System.out.println("  - Game renders at: " + config.getGameWidth() + "x" + config.getGameHeight());
        System.out.println("  - Window displays at: " + getScreenWidth() + "x" + getScreenHeight());
    }

    @Override
    protected void update(float deltaTime) {
        try {
            // Update AssetManager
            AssetManager.getInstance().update(deltaTime);
            // Update scene
            sceneManager.update(deltaTime);

            // Render via pipeline
            if (sceneManager.getCurrentScene() != null) {
                renderPipeline.render(sceneManager.getCurrentScene());
            }
        } catch (Exception e) {
            System.err.println("ERROR in renderGame: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    protected void destroyGame() {
        System.out.println("Destroying game systems...");

        if (sceneManager != null) {
            sceneManager.destroy();
        }

        if (renderer != null) {
            renderer.destroy();
        }

        CameraSystem.destroy();

        // Destroy AssetManager
        AssetManager.destroy();

        System.out.println("Game systems destroyed");
    }
}