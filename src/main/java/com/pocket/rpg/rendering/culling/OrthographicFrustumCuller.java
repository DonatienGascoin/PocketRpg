package com.pocket.rpg.rendering.culling;

import com.pocket.rpg.components.SpriteRenderer;
import com.pocket.rpg.core.Camera;
import com.pocket.rpg.rendering.CameraManager;
import org.joml.Vector3f;

/**
 * Frustum culler for orthographic (2D) cameras.
 * Uses camera position and zoom to calculate world-space frustum bounds.
 */
public class OrthographicFrustumCuller extends FrustumCuller {

    // Camera frustum bounds in world space
    private float worldLeft;
    private float worldRight;
    private float worldTop;
    private float worldBottom;

    /**
     * Updates the orthographic frustum bounds from camera.
     * <p>
     * The frustum bounds are calculated as:
     * 1. Start with game resolution (e.g., 640x480 pixels)
     * 2. Apply camera zoom (zoom > 1 = see less, zoom < 1 = see more)
     * 3. Offset by camera position
     */
    @Override
    public void updateFromCamera(Camera camera) {
        this.camera = camera;

        if (camera == null) {
            // Fallback: use game resolution as world bounds
            worldLeft = 0;
            worldRight = CameraManager.getGameWidth();
            worldTop = 0;
            worldBottom = CameraManager.getGameHeight();
            return;
        }

        // Get camera parameters
        Vector3f camPos = camera.getPosition();
        float zoom = camera.getZoom();

        // Get game resolution
        int gameWidth = CameraManager.getGameWidth();
        int gameHeight = CameraManager.getGameHeight();

        // Calculate visible world size based on zoom
        // Zoom = 1.0 means 1 pixel = 1 world unit
        // Zoom = 2.0 means we see half the world (zoomed in)
        // Zoom = 0.5 means we see twice the world (zoomed out)
        float visibleWorldWidth = gameWidth / zoom;
        float visibleWorldHeight = gameHeight / zoom;

        // Calculate world-space frustum bounds
        // Camera position is at top-left of view (matching screen space origin)
        worldLeft = camPos.x;
        worldRight = camPos.x + visibleWorldWidth;
        worldTop = camPos.y;
        worldBottom = camPos.y + visibleWorldHeight;

        // Note: Camera rotation is not considered for culling
        // This is conservative - rotated cameras may see slightly outside these bounds
        // For tight culling with rotation, we'd need to transform the frustum corners
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