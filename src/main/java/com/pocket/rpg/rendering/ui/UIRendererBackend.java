package com.pocket.rpg.rendering.ui;

import com.pocket.rpg.components.ui.UIImage;
import com.pocket.rpg.rendering.resources.Sprite;
import com.pocket.rpg.rendering.resources.Texture;
import org.joml.Vector4f;

/**
 * Interface for UI rendering backends.
 * <p>
 * UI components call these methods to render themselves.
 * This decouples UI components from the specific rendering implementation.
 * <p>
 * Supports multiple rendering modes:
 * <ul>
 *   <li>Immediate mode: drawQuad, drawSprite - for panels, images, buttons</li>
 *   <li>Nine-slice mode: drawNineSlice - for scalable UI elements</li>
 *   <li>Tiled mode: drawTiled - for repeating patterns</li>
 *   <li>Filled mode: drawFilled - for progress bars and cooldowns</li>
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

    /**
     * Draws a textured sprite with rotation.
     *
     * @param x        X position (screen-space, origin top-left)
     * @param y        Y position (screen-space, origin top-left)
     * @param width    Width in pixels
     * @param height   Height in pixels
     * @param rotation Rotation in degrees (clockwise)
     * @param originX  Rotation origin X (0-1, where 0=left, 0.5=center, 1=right)
     * @param originY  Rotation origin Y (0-1, where 0=top, 0.5=center, 1=bottom)
     * @param sprite   Sprite to draw (may be null for solid color)
     * @param tint     RGBA tint color (multiplied with texture)
     */
    void drawSprite(float x, float y, float width, float height,
                    float rotation, float originX, float originY,
                    Sprite sprite, Vector4f tint);

    /**
     * Draws a solid color quad with rotation.
     *
     * @param x        X position (screen-space, origin top-left)
     * @param y        Y position (screen-space, origin top-left)
     * @param width    Width in pixels
     * @param height   Height in pixels
     * @param rotation Rotation in degrees (clockwise)
     * @param originX  Rotation origin X (0-1)
     * @param originY  Rotation origin Y (0-1)
     * @param color    RGBA color
     */
    void drawQuad(float x, float y, float width, float height,
                  float rotation, float originX, float originY, Vector4f color);

    // ========================================================================
    // NINE-SLICE MODE
    // ========================================================================

    /**
     * Draws a sprite using 9-slice rendering.
     * Corners remain fixed size, edges stretch along one axis, center stretches both ways.
     * Requires the sprite to have NineSliceData configured.
     *
     * @param x          X position (screen-space, origin top-left)
     * @param y          Y position (screen-space, origin top-left)
     * @param width      Width in pixels
     * @param height     Height in pixels
     * @param rotation   Rotation in degrees (clockwise)
     * @param originX    Rotation origin X (0-1)
     * @param originY    Rotation origin Y (0-1)
     * @param sprite     Sprite with NineSliceData
     * @param tint       RGBA tint color
     * @param fillCenter Whether to render the center region
     */
    void drawNineSlice(float x, float y, float width, float height,
                       float rotation, float originX, float originY,
                       Sprite sprite, Vector4f tint, boolean fillCenter);

    // ========================================================================
    // TILED MODE
    // ========================================================================

    /**
     * Draws a sprite by tiling it to fill the specified area.
     *
     * @param x             X position (screen-space, origin top-left)
     * @param y             Y position (screen-space, origin top-left)
     * @param width         Width in pixels
     * @param height        Height in pixels
     * @param rotation      Rotation in degrees (clockwise)
     * @param originX       Rotation origin X (0-1)
     * @param originY       Rotation origin Y (0-1)
     * @param sprite        Sprite to tile
     * @param tint          RGBA tint color
     * @param pixelsPerUnit Pixels per unit for tile size calculation
     */
    void drawTiled(float x, float y, float width, float height,
                   float rotation, float originX, float originY,
                   Sprite sprite, Vector4f tint, float pixelsPerUnit);

    // ========================================================================
    // FILLED MODE (progress bars, cooldowns)
    // ========================================================================

    /**
     * Draws a partially filled sprite for progress bars and cooldowns.
     *
     * @param x           X position (screen-space, origin top-left)
     * @param y           Y position (screen-space, origin top-left)
     * @param width       Width in pixels
     * @param height      Height in pixels
     * @param rotation    Rotation in degrees (clockwise)
     * @param originX     Rotation origin X (0-1)
     * @param originY     Rotation origin Y (0-1)
     * @param sprite      Sprite to draw
     * @param tint        RGBA tint color
     * @param fillMethod  How to fill (horizontal, vertical, radial)
     * @param fillOrigin  Where the fill starts from
     * @param fillAmount  Fill amount (0 = empty, 1 = full)
     * @param clockwise   For radial fills, whether to fill clockwise
     */
    void drawFilled(float x, float y, float width, float height,
                    float rotation, float originX, float originY,
                    Sprite sprite, Vector4f tint,
                    UIImage.FillMethod fillMethod, UIImage.FillOrigin fillOrigin,
                    float fillAmount, boolean clockwise);

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
     * Adds a sprite to the current batch with rotation.
     * Must be called between beginBatch and endBatch.
     * Vertices are rotated around the specified pivot point.
     *
     * @param x        X position (screen-space)
     * @param y        Y position (screen-space)
     * @param width    Width in pixels
     * @param height   Height in pixels
     * @param u0       Texture U coordinate (left)
     * @param v0       Texture V coordinate (top)
     * @param u1       Texture U coordinate (right)
     * @param v1       Texture V coordinate (bottom)
     * @param rotation Rotation in degrees (clockwise)
     * @param pivotX   Pivot X position (screen-space, absolute)
     * @param pivotY   Pivot Y position (screen-space, absolute)
     * @param tint     RGBA tint color
     */
    void batchSprite(float x, float y, float width, float height,
                     float u0, float v0, float u1, float v1,
                     float rotation, float pivotX, float pivotY, Vector4f tint);

    /**
     * Ends the current batch and flushes all sprites to the GPU.
     */
    void endBatch();

    /**
     * @return Maximum number of sprites per batch
     */
    int getMaxBatchSize();
}
