package com.pocket.rpg.rendering.core;

import org.joml.Vector4f;

/**
 * Abstraction for render destinations (screen or framebuffer).
 * Provides uniform interface for binding, clearing, and querying render targets.
 */
public interface RenderTarget {

    /**
     * Binds this target for rendering.
     * Also sets the viewport to match target dimensions.
     */
    void bind();

    /**
     * Unbinds this target.
     * For screen target, this is a no-op.
     */
    void unbind();

    /**
     * Clears the target with the specified color.
     * Clears both color and depth buffers.
     *
     * @param color RGBA clear color
     */
    void clear(Vector4f color);

    /**
     * @return Width of the render target in pixels
     */
    int getWidth();

    /**
     * @return Height of the render target in pixels
     */
    int getHeight();

    /**
     * @return Texture ID for reading back rendered content, or 0 for screen
     */
    int getTextureId();

    /**
     * @return true if this target renders to a texture (not screen)
     */
    default boolean isOffscreen() {
        return getTextureId() != 0;
    }
}
