package com.pocket.rpg.rendering;

import org.joml.Vector3f;

/**
 * Interface for entities that can be rendered by EntityRenderer.
 * <p>
 * Implemented by both EditorEntity (editor) and GameObject (runtime)
 * to enable unified rendering with animation support.
 */
public interface RenderableEntity {

    /**
     * Gets the current sprite for rendering.
     * Called each frame to support animation.
     *
     * @return Current sprite, or null if not renderable
     */
    Sprite getCurrentSprite();

    /**
     * Gets entity position in world space.
     *
     * @return Position reference (not a copy)
     */
    Vector3f getPositionRef();

    /**
     * Gets the X origin (pivot point) as 0-1 ratio.
     * 0 = left edge, 0.5 = center, 1 = right edge
     */
    default float getOriginX() {
        return 0f;
    }

    /**
     * Gets the Y origin (pivot point) as 0-1 ratio.
     * 0 = bottom edge, 0.5 = center, 1 = top edge
     */
    default float getOriginY() {
        return 0f;
    }

    /**
     * Gets X scale multiplier.
     */
    default float getScaleX() {
        return 1f;
    }

    /**
     * Gets Y scale multiplier.
     */
    default float getScaleY() {
        return 1f;
    }

    /**
     * Gets Z-index for render ordering.
     * Lower values render behind higher values.
     */
    default float getZIndex() {
        return 0f;
    }

    /**
     * Checks if entity should be rendered.
     */
    default boolean isVisible() {
        return true;
    }
}