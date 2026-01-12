package com.pocket.rpg.rendering.culling;

import com.pocket.rpg.components.SpriteRenderer;
import com.pocket.rpg.rendering.core.RenderCamera;

/**
 * Frustum culler for orthographic (2D) cameras.
 * Uses RenderCamera.getWorldBounds() for frustum calculation.
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

    private float worldLeft;
    private float worldBottom;
    private float worldRight;
    private float worldTop;

    public OrthographicFrustumCuller() {
    }

    @Override
    public void updateFromCamera(RenderCamera camera) {
        this.camera = camera;

        if (camera == null) {
            worldLeft = Float.MIN_VALUE;
            worldRight = Float.MAX_VALUE;
            worldBottom = Float.MIN_VALUE;
            worldTop = Float.MAX_VALUE;
            return;
        }

        float[] bounds = camera.getWorldBounds();
        worldLeft = bounds[0];
        worldBottom = bounds[1];
        worldRight = bounds[2];
        worldTop = bounds[3];
    }

    @Override
    public boolean isVisible(SpriteRenderer spriteRenderer) {
        if (spriteRenderer == null || spriteRenderer.getSprite() == null) {
            return false;
        }

        float[] spriteAABB = calculateAABB(spriteRenderer, true);
        if (spriteAABB == null) {
            return false;
        }

        float[] cameraAABB = new float[]{
                worldLeft, worldBottom, worldRight, worldTop
        };

        return aabbIntersects(spriteAABB, cameraAABB);
    }

    /**
     * Gets the camera frustum bounds in world space.
     *
     * @return [left, bottom, right, top] in world coordinates (Y-up)
     */
    public float[] getFrustumBounds() {
        return new float[]{worldLeft, worldBottom, worldRight, worldTop};
    }

    public float getVisibleWidth() {
        return worldRight - worldLeft;
    }

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