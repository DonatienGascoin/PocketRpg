package com.pocket.rpg.rendering.culling;

import com.pocket.rpg.components.SpriteRenderer;
import com.pocket.rpg.core.Camera;

/**
 * Manages frustum culling for the rendering system.
 * Since we only support 2D orthographic rendering, this is simplified
 * to only use orthographic culling.
 */
public class CullingSystem {

    private OrthographicFrustumCuller culler;
    private CullingStatistics statistics;

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
    public void updateFrame(Camera camera) {
        if (camera == null) {
            System.err.println("WARNING: CullingSystem.updateFrame called with null camera");
            return;
        }

        // Update culler with camera state
        culler.updateFromCamera(camera);

        // Reset statistics for new frame
        statistics.startFrame();
    }

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

    /**
     * Gets the culling statistics for the current frame.
     */
    public CullingStatistics getStatistics() {
        return statistics;
    }

    /**
     * Gets the active culler.
     */
    public OrthographicFrustumCuller getCuller() {
        return culler;
    }

    /**
     * Resets the culling system.
     */
    public void reset() {
        statistics.reset();
    }
}