package com.pocket.rpg.rendering.culling;

import com.pocket.rpg.components.SpriteRenderer;
import com.pocket.rpg.core.Camera;

/**
 * Frustum culler for orthographic (2D) cameras.
 * Uses Camera.getWorldBounds() for frustum calculation.
 * <p>
 * Coordinate system (Y-up):
 * <ul>
 *   <li>bounds[0] = left (min X)</li>
 *   <li>bounds[1] = bottom (min Y)</li>
 *   <li>bounds[2] = right (max X)</li>
 *   <li>bounds[3] = top (max Y)</li>
 * </ul>
 */
public class OrthographicFrustumCuller extends FrustumCuller {

    // Camera frustum bounds in world space (Y-up)
    private float worldLeft;
    private float worldBottom;
    private float worldRight;
    private float worldTop;

    public OrthographicFrustumCuller() {
        // Default constructor
    }

    /**
     * Updates the orthographic frustum bounds from camera.
     * Uses Camera.getWorldBounds() which accounts for:
     * - Camera position (center of view)
     * - Camera zoom
     * - Orthographic size
     */
    @Override
    public void updateFromCamera(Camera camera) {
        this.camera = camera;

        if (camera == null) {
            // Fallback: no culling
            worldLeft = Float.MIN_VALUE;
            worldRight = Float.MAX_VALUE;
            worldBottom = Float.MIN_VALUE;
            worldTop = Float.MAX_VALUE;
            return;
        }

        // Get bounds from camera [left, bottom, right, top] (Y-up)
        float[] bounds = camera.getWorldBounds();
        worldLeft = bounds[0];
        worldBottom = bounds[1];
        worldRight = bounds[2];
        worldTop = bounds[3];
        System.out.printf("L: %s, B: %s, L: %s, R: %s%n", worldLeft, worldBottom, worldRight, worldTop);
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
        // Returns [minX, minY, maxX, maxY]
        float[] spriteAABB = calculateAABB(spriteRenderer, true);
        if (spriteAABB == null) {
            return false;
        }

        // Camera frustum AABB [minX, minY, maxX, maxY]
        float[] cameraAABB = new float[]{
                worldLeft, worldBottom, worldRight, worldTop
        };

        // Test intersection
        return aabbIntersects(spriteAABB, cameraAABB);
    }

    /**
     * Gets the camera frustum bounds in world space.
     * Useful for debugging and visualization.
     *
     * @return [left, bottom, right, top] in world coordinates (Y-up)
     */
    public float[] getFrustumBounds() {
        return new float[]{worldLeft, worldBottom, worldRight, worldTop};
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
        return worldTop - worldBottom;
    }

    @Override
    public String toString() {
        return String.format("OrthographicFrustumCuller[bounds=(%.1f, %.1f) to (%.1f, %.1f), size=%.1fx%.1f]",
                worldLeft, worldBottom, worldRight, worldTop,
                getVisibleWidth(), getVisibleHeight());
    }
}