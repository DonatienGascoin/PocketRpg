package com.pocket.rpg.rendering.renderers;

import com.pocket.rpg.rendering.OverlayRenderer;
import com.pocket.rpg.scenes.Scene;

/**
 * Rendering interface.
 * Allows different rendering backends (OpenGL, Vulkan, headless, etc.)
 * <p>
 * Provides access to specialized renderers:
 * - Game content renderer (sprites, world-space)
 * - Overlay renderer (transitions, screen-space effects)
 */
public interface RenderInterface {

    /**
     * Initialize the renderer.
     */
    void init(int width, int height);

    /**
     * Render a scene (game content).
     */
    void render(Scene scene);

    /**
     * Get the overlay renderer for screen-space effects.
     * Used for transitions, fades, and other fullscreen overlays.
     *
     * @return the overlay renderer instance
     */
    OverlayRenderer getOverlayRenderer();

    /**
     * Clean up renderer resources.
     */
    void destroy();
}