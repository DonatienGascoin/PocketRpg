package com.pocket.rpg.ui;

import com.pocket.rpg.rendering.Sprite;
import com.pocket.rpg.rendering.Texture;
import org.joml.Vector4f;

/**
 * Backend interface for UI rendering operations.
 * Implementations provide platform-specific rendering (OpenGL, Vulkan, etc.)
 *
 * Coordinate system:
 * - Origin (0,0) at TOP-LEFT
 * - Positive X = right
 * - Positive Y = down
 *
 * Supports two rendering modes:
 * 1. Immediate mode: drawQuad(), drawSprite() - one draw call each
 * 2. Batched mode: beginBatch(), batchSprite(), endBatch() - many sprites in one draw call
 */
public interface UIRendererBackend {

    // ========================================
    // IMMEDIATE MODE (single draw calls)
    // ========================================

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

    // ========================================
    // BATCHED MODE (for text rendering, particles, etc.)
    // ========================================

    /**
     * Begins a sprite batch.
     * All batchSprite() calls until endBatch() will be rendered in a single draw call.
     *
     * @param texture The texture atlas to use for all sprites in this batch
     */
    void beginBatch(Texture texture);

    /**
     * Adds a sprite to the current batch.
     * Must be called between beginBatch() and endBatch().
     *
     * @param x Screen X position
     * @param y Screen Y position
     * @param width Width in pixels
     * @param height Height in pixels
     * @param u0 Left UV coordinate (0-1)
     * @param v0 Top UV coordinate (0-1)
     * @param u1 Right UV coordinate (0-1)
     * @param v1 Bottom UV coordinate (0-1)
     * @param tint RGBA tint color
     */
    void batchSprite(float x, float y, float width, float height,
                     float u0, float v0, float u1, float v1, Vector4f tint);

    /**
     * Ends the current batch and renders all batched sprites.
     */
    void endBatch();

    /**
     * Returns the maximum number of sprites that can be batched in a single draw call.
     * Implementation-dependent.
     */
    default int getMaxBatchSize() {
        return 1000;  // Default, implementations can override
    }
}