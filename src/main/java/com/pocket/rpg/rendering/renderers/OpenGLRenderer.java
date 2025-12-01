package com.pocket.rpg.rendering.renderers;

import com.pocket.rpg.config.RenderingConfig;
import com.pocket.rpg.rendering.RenderPipeline;
import com.pocket.rpg.scenes.Scene;
import lombok.Getter;
import org.joml.Vector4f;

import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.glClear;
import static org.lwjgl.opengl.GL11.glClearColor;

public class OpenGLRenderer implements RenderInterface {
    private final RenderingConfig config;
    @Getter
    private Renderer renderer;
    @Getter
    private RenderPipeline renderPipeline;

    private final Vector4f clearColor;

    public OpenGLRenderer(RenderingConfig config) {
        this.config = config;
        this.clearColor = config.getClearColor();
    }

    @Override
    public void init(int width, int height) {
        System.out.println("Initializing OpenGL renderer: " + width + "x" + height);

        renderer = new BatchRenderer(config);
        renderer.init(width, height);

        renderPipeline = new RenderPipeline(renderer, config);

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

    @Override
    public void clear() {
        glClearColor(clearColor.x, clearColor.y, clearColor.z, clearColor.w);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
    }

}
