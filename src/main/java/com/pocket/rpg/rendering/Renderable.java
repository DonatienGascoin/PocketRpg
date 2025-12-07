package com.pocket.rpg.rendering;

import com.pocket.rpg.components.TilemapRenderer;
import com.pocket.rpg.core.GameObject;

/**
 * Interface for components that can be rendered by the rendering pipeline.
 * Implementations include {@link com.pocket.rpg.components.SpriteRenderer} and
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
 * <h2>Implementation Notes</h2>
 * Implementing classes must also extend {@link com.pocket.rpg.components.Component}
 * to provide access to {@link GameObject} and {@link com.pocket.rpg.components.Transform}.
 * This is not enforced at compile-time to avoid diamond inheritance issues,
 * but the rendering pipeline assumes this contract.
 *
 * @see com.pocket.rpg.components.SpriteRenderer
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

    /**
     * Gets the GameObject this renderable is attached to.
     * Required for accessing Transform data during rendering.
     *
     * <p>Implementations inheriting from Component already have this method.</p>
     *
     * @return The parent GameObject, or null if not attached
     */
    GameObject getGameObject();
}