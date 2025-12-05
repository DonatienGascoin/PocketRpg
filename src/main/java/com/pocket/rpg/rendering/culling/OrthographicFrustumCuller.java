package com.pocket.rpg.rendering.culling;

import com.pocket.rpg.components.SpriteRenderer;
import com.pocket.rpg.core.Camera;

/**
 * Frustum culler for orthographic (2D) cameras.
 * Uses Camera.getWorldBounds() for frustum calculation.
 * <p>
 * Since Camera now has centered position, the bounds are calculated
 * as position ± half visible area.
 */
public class OrthographicFrustumCuller extends FrustumCuller {

    // Camera frustum bounds in world space
    private float worldLeft;
    private float worldTop;
    private float worldRight;
    private float worldBottom;

    public OrthographicFrustumCuller() {
        // Default constructor
    }

    /**
     * Updates the orthographic frustum bounds from camera.
     * Uses Camera.getWorldBounds() which accounts for:
     * - Camera position (center of view)
     * - Camera zoom
     * - Game resolution
     */
    @Override
    public void updateFromCamera(Camera camera) {
        this.camera = camera;

        if (camera == null) {
            // Fallback: no culling
            worldLeft = Float.MIN_VALUE;
            worldRight = Float.MAX_VALUE;
            worldTop = Float.MIN_VALUE;
            worldBottom = Float.MAX_VALUE;
            return;
        }

        // Get bounds from camera (already accounts for centered position + zoom)
        float[] bounds = camera.getWorldBounds();
        worldLeft = bounds[0];
        worldTop = bounds[1];
        worldRight = bounds[2];
        worldBottom = bounds[3];
    }

    /**
     * Tests if a sprite is visible in the orthographic frustum.
     *
     * @param spriteRenderer The sprite to test
     * @return true if sprite intersects the camera frustum
     */
    @Override
    public boolean isVisible(SpriteRenderer spriteRenderer) {
        if (spriteRenderer == null || spriteRenderer.getSprite() == null) {
            return false;
        }

        // Calculate sprite AABB with rotation padding (conservative)
        float[] spriteAABB = calculateAABB(spriteRenderer, true);
        if (spriteAABB == null) {
            return false;
        }

        // Camera frustum AABB
        float[] cameraAABB = new float[]{
                worldLeft, worldTop, worldRight, worldBottom
        };

        // Test intersection
        return aabbIntersects(spriteAABB, cameraAABB);
    }

    /**
     * Gets the camera frustum bounds in world space.
     * Useful for debugging and visualization.
     *
     * @return [left, top, right, bottom] in world coordinates
     */
    public float[] getFrustumBounds() {
        return new float[]{worldLeft, worldTop, worldRight, worldBottom};
    }

    /**
     * Gets the visible world width.
     */
    public float getVisibleWidth() {
        return worldRight - worldLeft;
    }

    /**
     * Gets the visible world height.
     */
    public float getVisibleHeight() {
        return worldBottom - worldTop;
    }

    @Override
    public String toString() {
        return String.format("OrthographicFrustumCuller[bounds=(%.1f, %.1f) to (%.1f, %.1f), size=%.1fx%.1f]",
                worldLeft, worldTop, worldRight, worldBottom,
                getVisibleWidth(), getVisibleHeight());
    }
}