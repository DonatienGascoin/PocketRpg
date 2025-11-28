package com.pocket.rpg.rendering.renderers;

import com.pocket.rpg.scenes.Scene;

/**
 * Rrendering interface.
 * Allows different rendering backends (OpenGL, Vulkan, headless, etc.)
 */
public interface RenderInterface {

    /**
     * Initialize the renderer.
     */
    void init(int width, int height);

    /**
     * Render a scene.
     */
    void render(Scene scene);

    /**
     * Clean up renderer resources.
     */
    void destroy();
}