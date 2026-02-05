package com.pocket.rpg.rendering.core;

import com.pocket.rpg.components.rendering.SpriteRenderer;
import com.pocket.rpg.components.rendering.TilemapRenderer;

/**
 * Interface for components that can be rendered by the rendering pipeline.
 * Implementations include {@link SpriteRenderer} and
 * {@link TilemapRenderer}.
 *
 * <h2>Rendering Contract</h2>
 * <ul>
 *   <li>{@link #getZIndex()} - Determines render order (lower = rendered first/behind)</li>
 *   <li>{@link #isRenderVisible()} - Quick visibility check before culling</li>
 * </ul>
 *
 * <h2>Z-Index Sorting</h2>
 * The rendering pipeline sorts all Renderables by zIndex before rendering.
 * For equal zIndex values, order is undefined (stable sort not guaranteed).
 *
 * @see SpriteRenderer
 * @see TilemapRenderer
 */
public interface Renderable {

    /**
     * Gets the z-index for render ordering.
     * Lower values render first (behind higher values).
     *
     * @return Z-index value
     */
    int getZIndex();

    /**
     * Checks if this renderable should be considered for rendering.
     * This is a quick pre-culling check that verifies:
     * <ul>
     *   <li>Component is enabled</li>
     *   <li>GameObject exists and is enabled</li>
     *   <li>Has valid render data (sprite, tiles, etc.)</li>
     * </ul>
     *
     * <p>Note: This does NOT perform frustum culling - that happens
     * separately in the {@link com.pocket.rpg.rendering.culling.CullingSystem}.</p>
     *
     * @return true if this renderable should be processed for rendering
     */
    boolean isRenderVisible();
}
