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
        // 1. Initialize camera system first
        cameraSystem = new CameraSystem(getScreenWidth(), getScreenHeight());

        // 3. Initialize renderer
        renderer = new Renderer();
        renderer.init(getScreenWidth(), getScreenHeight());

        // 4. Create render pipeline
        renderPipeline = new RenderPipeline(renderer, cameraSystem);
        renderPipeline.setStatisticsReporter(new ConsoleStatisticsReporter(60));

        // 5. Initialize scene manager
        sceneManager = new SceneManager();

        // 6. Add scene lifecycle listener to clear cameras on scene changes
        sceneManager.addLifecycleListener(new SceneLifecycleListener() {
            @Override
            public void onSceneLoaded(com.pocket.rpg.scenes.Scene scene) {
                System.out.println("Scene loaded: " + scene.getName());
            }

            @Override
            public void onSceneUnloaded(com.pocket.rpg.scenes.Scene scene) {
                System.out.println("Scene unloaded: " + scene.getName());
                // Cameras unregister themselves in their destroy() method
            }
        });

        // 7. Register test scenes
        sceneManager.registerScene(new SmallOptimizationTestScene());
        sceneManager.registerScene(new LargePerformanceBenchmarkScene());
        sceneManager.registerScene(new ExampleScene());
        // sceneManager.registerScene(new LargePerformanceBenchmarkScene());

        // 8. Load first scene
//        sceneManager.loadScene("SmallOptimizationTest");
        sceneManager.loadScene("LargePerformanceBenchmark");
//        sceneManager.loadScene("ExampleScene");
    }

    @Override
    protected void renderGame(float deltaTime) {
        // Update scene
        sceneManager.update(deltaTime);

        // Render via pipeline
        if (sceneManager.getCurrentScene() != null) {
            renderPipeline.render(sceneManager.getCurrentScene());
        }
    }

    @Override
    protected void destroyGame() {
        if (sceneManager != null) {
            sceneManager.destroy();
        }

        if (renderer != null) {
            renderer.destroy();
        }

        CameraSystem.clear();

        super.destroy();
    }
}