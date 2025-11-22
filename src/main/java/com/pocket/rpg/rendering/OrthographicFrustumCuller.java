package com.pocket.rpg.rendering;

import com.pocket.rpg.components.Camera;
import com.pocket.rpg.components.SpriteRenderer;
import com.pocket.rpg.components.Transform;
import org.joml.Vector3f;

/**
 * Frustum culler for orthographic (2D) cameras.
 * Uses simple AABB tests against camera viewport bounds.
 */
public class OrthographicFrustumCuller extends FrustumCuller {

    // Camera frustum bounds in world space
    private float cameraLeft;
    private float cameraRight;
    private float cameraTop;
    private float cameraBottom;

    /**
     * Updates the orthographic frustum bounds from the camera.
     */
    @Override
    public void updateFromCamera(Camera camera) {
        this.camera = camera;

        if (camera == null || camera.getGameObject() == null) {
            // Default viewport bounds
            cameraLeft = 0;
            cameraRight = CameraSystem.getViewportWidth();
            cameraTop = 0;
            cameraBottom = CameraSystem.getViewportHeight();
            return;
        }

        // Get camera position
        Transform camTransform = camera.getGameObject().getTransform();
        Vector3f camPos = camTransform.getPosition();

        // Calculate world-space bounds for screen-space orthographic camera
        // (0,0 at top-left, positive Y down)
        cameraLeft = camPos.x;
        cameraRight = camPos.x + CameraSystem.getViewportWidth();
        cameraTop = camPos.y;
        cameraBottom = camPos.y + CameraSystem.getViewportHeight();
    }

    /**
     * Tests if a sprite is visible in the orthographic frustum.
     * Uses AABB test with rotation padding for conservative culling.
     */
    @Override
    public boolean isVisible(SpriteRenderer spriteRenderer) {
        if (spriteRenderer == null || spriteRenderer.getSprite() == null) {
            return false;
        }

        // Calculate sprite AABB with rotation padding
        float[] spriteAABB = calculateAABB(spriteRenderer, true);
        if (spriteAABB == null) return false;

        // Camera frustum AABB
        float[] cameraAABB = new float[] {
                cameraLeft, cameraTop, cameraRight, cameraBottom
        };

        // Test intersection
        return aabbIntersects(spriteAABB, cameraAABB);
    }

    /**
     * Gets the camera frustum bounds.
     * @return [left, top, right, bottom] in world space
     */
    public float[] getFrustumBounds() {
        return new float[] { cameraLeft, cameraTop, cameraRight, cameraBottom };
    }
}