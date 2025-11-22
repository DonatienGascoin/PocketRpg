package com.pocket.rpg.rendering;

import com.pocket.rpg.components.Camera;
import com.pocket.rpg.components.SpriteRenderer;
import com.pocket.rpg.components.Transform;
import org.joml.Vector3f;

/**
 * Frustum culler for orthographic (2D) cameras.
 * FIXED: Now uses game resolution instead of viewport size.
 */
public class OrthographicFrustumCuller extends FrustumCuller {

    // Camera frustum bounds in world space
    private float cameraLeft;
    private float cameraRight;
    private float cameraTop;
    private float cameraBottom;

    /**
     * FIX: Updates the orthographic frustum bounds using game resolution.
     */
    @Override
    public void updateFromCamera(Camera camera) {
        this.camera = camera;

        if (camera == null || camera.getGameObject() == null) {
            // Default to game resolution bounds
            cameraLeft = 0;
            cameraRight = CameraSystem.getGameWidth();
            cameraTop = 0;
            cameraBottom = CameraSystem.getGameHeight();
            return;
        }

        // Get camera position
        Transform camTransform = camera.getGameObject().getTransform();
        Vector3f camPos = camTransform.getPosition();

        // FIX: Calculate world-space bounds using GAME resolution
        // (0,0 at top-left, positive Y down)
        cameraLeft = camPos.x;
        cameraRight = camPos.x + CameraSystem.getGameWidth();
        cameraTop = camPos.y;
        cameraBottom = camPos.y + CameraSystem.getGameHeight();
    }

    /**
     * Tests if a sprite is visible in the orthographic frustum.
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