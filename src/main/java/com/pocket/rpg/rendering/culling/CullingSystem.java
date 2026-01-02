package com.pocket.rpg.rendering.culling;

import com.pocket.rpg.components.SpriteRenderer;
import com.pocket.rpg.components.TilemapRenderer;
import com.pocket.rpg.core.GameCamera;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages frustum culling for the rendering system.
 * Uses Camera's getWorldBounds() for culling calculations.
 *
 * <h2>Supported Culling</h2>
 * <ul>
 *   <li>{@link SpriteRenderer} - Per-sprite AABB culling</li>
 *   <li>{@link TilemapRenderer} - Per-chunk AABB culling</li>
 * </ul>
 */
public class CullingSystem {

    /**
     * -- GETTER --
     *  Gets the active culler.
     */
    @Getter
    private final OrthographicFrustumCuller culler;
    /**
     * -- GETTER --
     *  Gets the culling statistics for the current frame.
     */
    @Getter
    private final CullingStatistics statistics;

    // Reusable list for visible chunks (avoid allocation per frame)
    private final List<long[]> visibleChunksResult = new ArrayList<>();

    public CullingSystem() {
        this.culler = new OrthographicFrustumCuller();
        this.statistics = new CullingStatistics();
    }

    /**
     * Updates the culling system for the current frame.
     * Updates the culler with the camera's current state.
     *
     * @param camera The camera to use for culling
     */
    public void updateFrame(GameCamera camera) {
        if (camera == null) {
            System.err.println("WARNING: CullingSystem.updateFrame called with null camera");
            return;
        }

        // Update culler with camera state
        culler.updateFromCamera(camera);

        // Reset statistics for new frame
        statistics.startFrame();
    }

    // ========================================================================
    // SPRITE CULLING
    // ========================================================================

    /**
     * Tests if a sprite should be rendered (is visible in camera frustum).
     * Updates culling statistics.
     *
     * @param spriteRenderer The sprite to test
     * @return true if sprite should be rendered
     */
    public boolean shouldRender(SpriteRenderer spriteRenderer) {
        statistics.incrementTotal();

        boolean visible = culler.isVisible(spriteRenderer);

        if (visible) {
            statistics.incrementRendered();
        } else {
            statistics.incrementCulled();
        }

        return visible;
    }

    // ========================================================================
    // TILEMAP CULLING
    // ========================================================================

    /**
     * Gets all visible chunks for a tilemap.
     * Performs AABB intersection test for each chunk against camera frustum.
     *
     * @param tilemapRenderer The tilemap to cull
     * @return List of visible chunk coordinates as [cx, cy] arrays
     */
    public List<long[]> getVisibleChunks(TilemapRenderer tilemapRenderer) {
        visibleChunksResult.clear();

        if (tilemapRenderer == null) {
            return visibleChunksResult;
        }

        float[] frustumBounds = culler.getFrustumBounds();

        for (Long chunkKey : tilemapRenderer.chunkKeys()) {
            int cx = TilemapRenderer.chunkKeyToX(chunkKey);
            int cy = TilemapRenderer.chunkKeyToY(chunkKey);

            // Get chunk world bounds
            float[] chunkBounds = tilemapRenderer.getChunkWorldBounds(cx, cy);

            // AABB intersection test
            if (aabbIntersects(chunkBounds, frustumBounds)) {
                visibleChunksResult.add(new long[]{cx, cy});
                statistics.incrementRendered();
            } else {
                statistics.incrementCulled();
            }
            statistics.incrementTotal();
        }

        return visibleChunksResult;
    }

    /**
     * Tests if a specific chunk is visible.
     *
     * @param tilemapRenderer The tilemap containing the chunk
     * @param cx      Chunk X coordinate
     * @param cy      Chunk Y coordinate
     * @return true if chunk intersects camera frustum
     */
    public boolean isChunkVisible(TilemapRenderer tilemapRenderer, int cx, int cy) {
        if (tilemapRenderer == null) {
            return false;
        }

        float[] chunkBounds = tilemapRenderer.getChunkWorldBounds(cx, cy);
        float[] frustumBounds = culler.getFrustumBounds();

        return aabbIntersects(chunkBounds, frustumBounds);
    }

    /**
     * AABB intersection test.
     *
     * @param aabb1 First AABB [minX, minY, maxX, maxY]
     * @param aabb2 Second AABB [minX, minY, maxX, maxY]
     * @return true if AABBs intersect
     */
    private boolean aabbIntersects(float[] aabb1, float[] aabb2) {
        if (aabb1 == null || aabb2 == null) return false;

        return !(aabb1[2] < aabb2[0] ||  // aabb1.maxX < aabb2.minX
                aabb1[0] > aabb2[2] ||   // aabb1.minX > aabb2.maxX
                aabb1[3] < aabb2[1] ||   // aabb1.maxY < aabb2.minY
                aabb1[1] > aabb2[3]);    // aabb1.minY > aabb2.maxY
    }

    // ========================================================================
    // STATISTICS & ACCESS
    // ========================================================================

    /**
     * Resets the culling system.
     */
    public void reset() {
        statistics.reset();
    }
}