package com.pocket.rpg.core;

import com.pocket.rpg.config.ConfigLoader;
import com.pocket.rpg.config.WindowConfig;
import com.pocket.rpg.postProcessing.PostProcessor;
import com.pocket.rpg.postProcessing.postEffects.VignetteEffect;
import com.pocket.rpg.rendering.CameraManager;
import com.pocket.rpg.serialization.Serializer;
import com.pocket.rpg.utils.LogUtils;

import java.util.List;


public class GameWindow extends Window {

    private GameEngine gameEngine;

    public GameWindow() {
        Serializer.init();
        var gameConfig = ConfigLoader.loadGameConfig();
        var inputConfig = ConfigLoader.loadInputConfig();
    }

    @Override
    protected WindowConfig loadConfig() {
        return WindowConfig.builder()
                // Window size (can be resized by user)
                .windowWidth(640)
                .windowHeight(480)
                // Fixed game resolution (never changes)
                .gameWidth(640)
                .gameHeight(480)

                .title("Pocket RPG Engine")
                .vsync(true)
                .scalingMode(PostProcessor.ScalingMode.MAINTAIN_ASPECT_RATIO)
                .enablePillarBox(true)
                // Pillarbox aspect ratio will auto-calculate from game resolution (4:3)
                .postProcessingEffects(List.of(new VignetteEffect(1f, 1.5f)))
//                .callback(new DefaultCallback().registerResizeCallback(CameraManager::setViewportSize))
                .build();
    }

    @Override
    protected void initGame() {
        System.out.println(LogUtils.buildBox(config.getTitle(), "Initializing window systems..."));
        System.out.println("Initializing game systems...");
        System.out.println("Window size: " + getScreenWidth() + "x" + getScreenHeight());
        System.out.println("Game resolution: " + config.getGameWidth() + "x" + config.getGameHeight());

// Already done     in GameEngine
// Set viewport to window size
//        CameraManager.setViewportSize(getScreenWidth(), getScreenHeight());

        // Create and initialize game engine
        gameEngine = new GameEngine(config);
        gameEngine.initialize();

        System.out.println("Window systems initialized successfully");
        System.out.println("Pixel-perfect mode: ENABLED");
        System.out.println("  - Game renders at: " + config.getGameWidth() + "x" + config.getGameHeight());
        System.out.println("  - Window displays at: " + getScreenWidth() + "x" + getScreenHeight());
    }

    @Override
    protected void update(float deltaTime) {
        try {
            // Delegate to engine
            gameEngine.update(deltaTime);
            gameEngine.render();
        } catch (Exception e) {
            System.err.println("ERROR in update: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    protected void destroyGame() {
        System.out.println("Destroying game systems...");

        if (gameEngine != null) {
            gameEngine.destroy();
        }

        System.out.println("Game systems destroyed");
    }
}