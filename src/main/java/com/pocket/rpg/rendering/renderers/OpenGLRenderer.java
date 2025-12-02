package com.pocket.rpg.rendering.renderers;

import com.pocket.rpg.config.RenderingConfig;
import com.pocket.rpg.rendering.CameraSystem;
import com.pocket.rpg.rendering.RenderPipeline;
import com.pocket.rpg.scenes.Scene;
import lombok.Getter;

public class OpenGLRenderer implements RenderInterface {
    private final CameraSystem cameraSystem;
    private final RenderingConfig config;
    @Getter
    private Renderer renderer;
    @Getter
    private RenderPipeline renderPipeline;

    public OpenGLRenderer(CameraSystem cameraSystem, RenderingConfig config) {
        this.cameraSystem = cameraSystem;
        this.config = config;
    }

    @Override
    public void init(int width, int height) {
        System.out.println("Initializing OpenGL renderer: " + width + "x" + height);

        renderer = new BatchRenderer(config);
        renderer.init(width, height);

        renderPipeline = new RenderPipeline(renderer, cameraSystem, config);

        System.out.println("OpenGL renderer initialized");
    }

    @Override
    public void render(Scene scene) {
        if (scene != null) {
            renderPipeline.render(scene);
        }
    }

    @Override
    public void destroy() {
        System.out.println("Destroying OpenGL renderer...");

        if (renderer != null) {
            renderer.destroy();
        }

        System.out.println("OpenGL renderer destroyed");
    }
}
