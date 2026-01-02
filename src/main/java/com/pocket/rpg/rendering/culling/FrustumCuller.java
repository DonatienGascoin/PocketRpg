package com.pocket.rpg.rendering.culling;

import com.pocket.rpg.components.SpriteRenderer;
import com.pocket.rpg.components.Transform;
import com.pocket.rpg.core.GameCamera;
import com.pocket.rpg.rendering.Sprite;
import org.joml.Vector3f;

/**
 * Abstract base class for frustum culling implementations.
 * Provides shared AABB (Axis-Aligned Bounding Box) intersection logic.
 * <p>
 * Uses world units for all calculations.
 */
public abstract class FrustumCuller {

    protected GameCamera camera;

    /**
     * Updates the culler from the camera's current state.
     * Called once per frame before culling tests.
     *
     * @param camera The camera to use for culling
     */
    public abstract void updateFromCamera(GameCamera camera);

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
     * <p>
     * Uses {@link Sprite#getWorldWidth()} and {@link Sprite#getWorldHeight()}
     * for world-unit dimensions.
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

        // Calculate sprite bounds in WORLD UNITS
        float spriteWidth = sprite.getWorldWidth() * scale.x;
        float spriteHeight = sprite.getWorldHeight() * scale.y;

        // Account for origin (rotation/scale pivot point)
        float originOffsetX = spriteWidth * spriteRenderer.getOriginX();
        float originOffsetY = spriteHeight * spriteRenderer.getOriginY();

        // Calculate base AABB (Y-up coordinate system)
        float minX = pos.x - originOffsetX;
        float maxX = pos.x + (spriteWidth - originOffsetX);
        float minY = pos.y - originOffsetY;
        float maxY = pos.y + (spriteHeight - originOffsetY);

        // Add rotation padding if requested (conservative approach)
        if (addRotationPadding) {
            Vector3f rot = transform.getRotation();
            if (rot.z != 0) {
                // Calculate diagonal distance (worst-case rotation)
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

        // AABB intersection test: rectangles overlap if they don't NOT overlap
        return !(aabb1[2] < aabb2[0] ||  // aabb1.maxX < aabb2.minX
                aabb1[0] > aabb2[2] ||  // aabb1.minX > aabb2.maxX
                aabb1[3] < aabb2[1] ||  // aabb1.maxY < aabb2.minY
                aabb1[1] > aabb2[3]);   // aabb1.minY > aabb2.maxY
    }
}