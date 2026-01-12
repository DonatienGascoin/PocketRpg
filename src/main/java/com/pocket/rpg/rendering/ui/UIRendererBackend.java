package com.pocket.rpg.rendering.ui;

import com.pocket.rpg.rendering.resources.Sprite;
import com.pocket.rpg.rendering.resources.Texture;
import org.joml.Vector4f;

/**
 * Interface for UI rendering backends.
 * <p>
 * UI components call these methods to render themselves.
 * This decouples UI components from the specific rendering implementation.
 * <p>
 * Supports two rendering modes:
 * <ul>
 *   <li>Immediate mode: drawQuad, drawSprite - for panels, images, buttons</li>
 *   <li>Batched mode: beginBatch, batchSprite, endBatch - for text rendering</li>
 * </ul>
 */
public interface UIRendererBackend {

    // ========================================================================
    // IMMEDIATE MODE
    // ========================================================================

    /**
     * Draws a solid color quad.
     *
     * @param x      X position (screen-space, origin top-left)
     * @param y      Y position (screen-space, origin top-left)
     * @param width  Width in pixels
     * @param height Height in pixels
     * @param color  RGBA color
     */
    void drawQuad(float x, float y, float width, float height, Vector4f color);

    /**
     * Draws a textured sprite.
     *
     * @param x      X position (screen-space, origin top-left)
     * @param y      Y position (screen-space, origin top-left)
     * @param width  Width in pixels
     * @param height Height in pixels
     * @param sprite Sprite to draw (may be null for solid color)
     * @param tint   RGBA tint color (multiplied with texture)
     */
    void drawSprite(float x, float y, float width, float height, Sprite sprite, Vector4f tint);

    // ========================================================================
    // BATCHED MODE (for text rendering)
    // ========================================================================

    /**
     * Begins a batch for the given texture.
     * All subsequent batchSprite calls will use this texture until endBatch.
     * <p>
     * For text rendering, pass null to indicate font atlas mode
     * (uses red channel as alpha).
     *
     * @param texture Texture for this batch, or null for text mode
     */
    void beginBatch(Texture texture);

    /**
     * Adds a sprite to the current batch.
     * Must be called between beginBatch and endBatch.
     *
     * @param x      X position (screen-space)
     * @param y      Y position (screen-space)
     * @param width  Width in pixels
     * @param height Height in pixels
     * @param u0     Texture U coordinate (left)
     * @param v0     Texture V coordinate (top)
     * @param u1     Texture U coordinate (right)
     * @param v1     Texture V coordinate (bottom)
     * @param tint   RGBA tint color
     */
    void batchSprite(float x, float y, float width, float height,
                     float u0, float v0, float u1, float v1, Vector4f tint);

    /**
     * Ends the current batch and flushes all sprites to the GPU.
     */
    void endBatch();

    /**
     * @return Maximum number of sprites per batch
     */
    int getMaxBatchSize();
}
