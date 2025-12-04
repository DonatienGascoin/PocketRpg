package com.pocket.rpg.ui;

import com.pocket.rpg.rendering.Sprite;
import org.joml.Vector4f;

/**
 * Backend interface for UI rendering operations.
 * Implementations provide platform-specific rendering (OpenGL, Vulkan, etc.)
 */
public interface UIRendererBackend {

    /**
     * Draw a solid color quad at the specified position.
     *
     * @param x Screen X position (pixels from left)
     * @param y Screen Y position (pixels from top)
     * @param width Width in pixels
     * @param height Height in pixels
     * @param color RGBA color
     */
    void drawQuad(float x, float y, float width, float height, Vector4f color);

    /**
     * Draw a textured quad at the specified position.
     *
     * @param x Screen X position (pixels from left)
     * @param y Screen Y position (pixels from top)
     * @param width Width in pixels
     * @param height Height in pixels
     * @param sprite Sprite containing texture and UV coordinates
     * @param tint RGBA tint color (multiplied with texture)
     */
    void drawSprite(float x, float y, float width, float height, Sprite sprite, Vector4f tint);
}