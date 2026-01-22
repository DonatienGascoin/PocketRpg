package com.pocket.rpg.rendering.culling;

import com.pocket.rpg.components.SpriteRenderer;
import com.pocket.rpg.components.Transform;
import com.pocket.rpg.rendering.core.RenderCamera;
import com.pocket.rpg.rendering.resources.Sprite;
import org.joml.Vector3f;

/**
 * Abstract base class for frustum culling implementations.
 * Provides shared AABB (Axis-Aligned Bounding Box) intersection logic.
 * <p>
 * Uses world units for all calculations.
 */
public abstract class FrustumCuller {

    protected RenderCamera camera;

    /**
     * Updates the culler from the camera's current state.
     * Called once per frame before culling tests.
     *
     * @param camera The camera to use for culling
     */
    public abstract void updateFromCamera(RenderCamera camera);

    /**
     * Tests if a sprite is visible in the camera frustum.
     *
     * @param spriteRenderer The sprite to test
     * @return true if visible, false if culled
     */
    public abstract boolean isVisible(SpriteRenderer spriteRenderer);

    /**
     * Calculates an AABB (Axis-Aligned Bounding Box) for a sprite in world units.
     * Accounts for sprite origin and scale, with optional rotation padding.
     *
     * @param spriteRenderer     The sprite to calculate bounds for
     * @param addRotationPadding Whether to add padding for rotation
     * @return AABB as [minX, minY, maxX, maxY] in world units
     */
    protected float[] calculateAABB(SpriteRenderer spriteRenderer, boolean addRotationPadding) {
        if (spriteRenderer == null || spriteRenderer.getSprite() == null) {
            return null;
        }

        Sprite sprite = spriteRenderer.getSprite();
        Transform transform = spriteRenderer.getGameObject().getTransform();
        Vector3f pos = transform.getPosition();
        Vector3f scale = transform.getScale();

        float spriteWidth = sprite.getWorldWidth() * scale.x;
        float spriteHeight = sprite.getWorldHeight() * scale.y;

        float originOffsetX = spriteWidth * spriteRenderer.getEffectiveOriginX();
        float originOffsetY = spriteHeight * spriteRenderer.getEffectiveOriginY();

        float minX = pos.x - originOffsetX;
        float maxX = pos.x + (spriteWidth - originOffsetX);
        float minY = pos.y - originOffsetY;
        float maxY = pos.y + (spriteHeight - originOffsetY);

        if (addRotationPadding) {
            Vector3f rot = transform.getRotation();
            if (rot.z != 0) {
                float diagonal = (float) Math.sqrt(spriteWidth * spriteWidth + spriteHeight * spriteHeight);
                float padding = (diagonal - Math.max(spriteWidth, spriteHeight)) / 2;

                minX -= padding;
                maxX += padding;
                minY -= padding;
                maxY += padding;
            }
        }

        return new float[]{minX, minY, maxX, maxY};
    }

    /**
     * Tests AABB intersection (overlap test).
     *
     * @param aabb1 First AABB [minX, minY, maxX, maxY]
     * @param aabb2 Second AABB [minX, minY, maxX, maxY]
     * @return true if AABBs intersect
     */
    protected boolean aabbIntersects(float[] aabb1, float[] aabb2) {
        if (aabb1 == null || aabb2 == null) return false;

        return !(aabb1[2] < aabb2[0] ||
                aabb1[0] > aabb2[2] ||
                aabb1[3] < aabb2[1] ||
                aabb1[1] > aabb2[3]);
    }
}