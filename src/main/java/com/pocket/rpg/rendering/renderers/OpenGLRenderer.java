package com.pocket.rpg.rendering.renderers;

import com.pocket.rpg.config.RenderingConfig;
import com.pocket.rpg.rendering.CameraSystem;
import com.pocket.rpg.rendering.OpenGLOverlayRenderer;
import com.pocket.rpg.rendering.OverlayRenderer;
import com.pocket.rpg.rendering.RenderPipeline;
import com.pocket.rpg.scenes.Scene;
import lombok.Getter;

/**
 * OpenGL implementation of RenderInterface.
 * Owns and manages OpenGL-specific renderers:
 * - Game content renderer (BatchRenderer via RenderPipeline)
 * - Overlay renderer (OpenGLOverlayRenderer)
 */
public class OpenGLRenderer implements RenderInterface {
    private final CameraSystem cameraSystem;
    private final RenderingConfig config;

    @Getter
    private Renderer renderer;
    @Getter
    private RenderPipeline renderPipeline;

    // Overlay renderer owned by OpenGLRenderer
    private OpenGLOverlayRenderer overlayRenderer;

    public OpenGLRenderer(CameraSystem cameraSystem, RenderingConfig config) {
        this.cameraSystem = cameraSystem;
        this.config = config;
    }

    @Override
    public void init(int width, int height) {
        System.out.println("Initializing OpenGL renderer: " + width + "x" + height);

        // Initialize game content renderer
        renderer = new BatchRenderer(config);
        renderer.init(width, height);

        renderPipeline = new RenderPipeline(renderer, cameraSystem, config);

        // Initialize overlay renderer
        overlayRenderer = new OpenGLOverlayRenderer();
        overlayRenderer.init();

        System.out.println("OpenGL renderer initialized");
    }

    @Override
    public void render(Scene scene) {
        if (scene != null) {
            renderPipeline.render(scene);
        }
    }

    @Override
    public OverlayRenderer getOverlayRenderer() {
        return overlayRenderer;
    }

    @Override
    public void destroy() {
        System.out.println("Destroying OpenGL renderer...");

        if (overlayRenderer != null) {
            overlayRenderer.destroy();
        }

        if (renderer != null) {
            renderer.destroy();
        }

        System.out.println("OpenGL renderer destroyed");
    }
}