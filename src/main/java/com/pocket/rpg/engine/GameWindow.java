package com.pocket.rpg.engine;

import com.pocket.rpg.postProcessing.PostProcessor;
import com.pocket.rpg.postProcessing.postEffects.VignetteEffect;
import com.pocket.rpg.rendering.CameraSystem;
import com.pocket.rpg.rendering.ConsoleStatisticsReporter;
import com.pocket.rpg.rendering.RenderPipeline;
import com.pocket.rpg.rendering.Renderer;
import com.pocket.rpg.scenes.*;
import com.pocket.rpg.utils.DefaultCallback;
import com.pocket.rpg.utils.WindowConfig;

import java.util.List;

/**
 * Main game window with integrated camera system and render pipeline.
 * FIXED: Proper CameraSystem initialization
 */
public class GameWindow extends Window {

    private Renderer renderer;
    private RenderPipeline renderPipeline;
    private CameraSystem cameraSystem;
    private SceneManager sceneManager;

    public GameWindow() {
        super(WindowConfig.builder()
                .initialWidth(640)
                .initialHeight(480)
                .title("Pocket RPG Engine")
                .vsync(true)
                .scalingMode(PostProcessor.ScalingMode.MAINTAIN_ASPECT_RATIO)
                .enablePillarBox(true)
                .pillarboxAspectRatio(640f / 480f)
                .postProcessingEffects(List.of(new VignetteEffect(1f, 1.5f)))
                .callback(new DefaultCallback().registerResizeCallback(CameraSystem::setViewportSize))
                .build());
    }

    @Override
    protected void initGame() {
        System.out.println("Initializing game systems...");
        

        CameraSystem.initialize(getScreenWidth(), getScreenHeight());
        cameraSystem = CameraSystem.getInstance();

        if (cameraSystem == null) {
            throw new IllegalStateException("Failed to initialize CameraSystem");
        }

        // Initialize renderer
        renderer = new Renderer();
        renderer.init(getScreenWidth(), getScreenHeight());

        // Create render pipeline
        renderPipeline = new RenderPipeline(renderer, cameraSystem);
        renderPipeline.setStatisticsReporter(new ConsoleStatisticsReporter(60));

        // Initialize scene manager
        sceneManager = new SceneManager();

        // Add scene lifecycle listener to clear cameras on scene changes
        sceneManager.addLifecycleListener(new SceneLifecycleListener() {
            @Override
            public void onSceneLoaded(Scene scene) {
                System.out.println("Scene loaded: " + scene.getName());
            }

            @Override
            public void onSceneUnloaded(Scene scene) {
                System.out.println("Scene unloaded: " + scene.getName());
                // Cameras unregister themselves in their destroy() method
            }
        });

        // Register test scenes
        sceneManager.registerScene(new SmallOptimizationTestScene());
        sceneManager.registerScene(new LargePerformanceBenchmarkScene());
        sceneManager.registerScene(new ExampleScene());

        // Load first scene
//        sceneManager.loadScene("LargePerformanceBenchmark");
        sceneManager.loadScene("ExampleScene");

        System.out.println("Game systems initialized successfully");
        System.out.println("Active cameras: " + CameraSystem.getCameraCount());
    }

    @Override
    protected void renderGame(float deltaTime) {
        try {
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

        // FIX: Properly destroy CameraSystem
        CameraSystem.destroy();

        System.out.println("Game systems destroyed");
    }
}
