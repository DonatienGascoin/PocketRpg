package com.pocket.rpg.rendering.culling;

import com.pocket.rpg.components.SpriteRenderer;
import com.pocket.rpg.components.TilemapRenderer;
import com.pocket.rpg.rendering.RenderCamera;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages frustum culling for the rendering system.
 * Uses RenderCamera.getWorldBounds() for culling calculations.
 *
 * <h2>Supported Culling</h2>
 * <ul>
 *   <li>{@link SpriteRenderer} - Per-sprite AABB culling</li>
 *   <li>{@link TilemapRenderer} - Per-chunk AABB culling</li>
 * </ul>
 */
public class CullingSystem {

    @Getter
    private final OrthographicFrustumCuller culler;
    @Getter
    private final CullingStatistics statistics;

    private final List<long[]> visibleChunksResult = new ArrayList<>();

    public CullingSystem() {
        this.culler = new OrthographicFrustumCuller();
        this.statistics = new CullingStatistics();
    }

    /**
     * Updates the culling system for the current frame.
     *
     * @param camera The camera to use for culling
     */
    public void updateFrame(RenderCamera camera) {
        if (camera == null) {
            System.err.println("WARNING: CullingSystem.updateFrame called with null camera");
            return;
        }

        culler.updateFromCamera(camera);
        statistics.startFrame();
    }

    // ========================================================================
    // SPRITE CULLING
    // ========================================================================

    /**
     * Tests if a sprite should be rendered.
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

            float[] chunkBounds = tilemapRenderer.getChunkWorldBounds(cx, cy);

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
     * Gets visible chunks using world bounds directly.
     * Useful when camera is not available but bounds are known.
     *
     * @param tilemapRenderer The tilemap to cull
     * @param worldBounds     [left, bottom, right, top] in world coordinates
     * @return List of visible chunk coordinates
     */
    public List<long[]> getVisibleChunks(TilemapRenderer tilemapRenderer, float[] worldBounds) {
        visibleChunksResult.clear();

        if (tilemapRenderer == null || worldBounds == null) {
            return visibleChunksResult;
        }

        for (Long chunkKey : tilemapRenderer.chunkKeys()) {
            int cx = TilemapRenderer.chunkKeyToX(chunkKey);
            int cy = TilemapRenderer.chunkKeyToY(chunkKey);

            float[] chunkBounds = tilemapRenderer.getChunkWorldBounds(cx, cy);

            if (aabbIntersects(chunkBounds, worldBounds)) {
                visibleChunksResult.add(new long[]{cx, cy});
            }
        }

        return visibleChunksResult;
    }

    /**
     * Tests if a specific chunk is visible.
     */
    public boolean isChunkVisible(TilemapRenderer tilemapRenderer, int cx, int cy) {
        if (tilemapRenderer == null) {
            return false;
        }

        float[] chunkBounds = tilemapRenderer.getChunkWorldBounds(cx, cy);
        float[] frustumBounds = culler.getFrustumBounds();

        return aabbIntersects(chunkBounds, frustumBounds);
    }

    private boolean aabbIntersects(float[] aabb1, float[] aabb2) {
        if (aabb1 == null || aabb2 == null) return false;

        return !(aabb1[2] < aabb2[0] ||
                aabb1[0] > aabb2[2] ||
                aabb1[3] < aabb2[1] ||
                aabb1[1] > aabb2[3]);
    }

    public void reset() {
        statistics.reset();
    }
}