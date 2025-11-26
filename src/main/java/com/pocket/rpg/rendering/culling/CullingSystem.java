package com.pocket.rpg.rendering.culling;

import com.pocket.rpg.components.Camera;
import com.pocket.rpg.components.SpriteRenderer;
import com.pocket.rpg.rendering.CameraSystem;

/**
 * Manages frustum culling for the rendering system.
 * Automatically switches between orthographic and perspective cullers.
 * Detects viewport changes and updates cullers accordingly.
 */
public class CullingSystem {

    private FrustumCuller orthographicCuller;
    private FrustumCuller perspectiveCuller;
    private FrustumCuller activeCuller;

    private int lastViewportWidth = -1;
    private int lastViewportHeight = -1;
    private Camera.ProjectionType lastProjectionType = null;

    private CullingStatistics statistics;

    public CullingSystem() {
        this.orthographicCuller = new OrthographicFrustumCuller();
        this.perspectiveCuller = new PerspectiveFrustumCuller();
        this.activeCuller = orthographicCuller; // Default to orthographic
        this.statistics = new CullingStatistics();
    }

    /**
     * Updates the culling system for the current frame.
     * Detects camera/viewport changes and updates the active culler.
     */
    public void updateFrame(Camera camera) {

        if (camera == null) {
            // No camera - use default orthographic culler
            activeCuller = orthographicCuller;
            return;
        }

        // Detect viewport changes
        int currentWidth = CameraSystem.getViewportWidth();
        int currentHeight = CameraSystem.getViewportHeight();
        boolean viewportChanged = (currentWidth != lastViewportWidth ||
                currentHeight != lastViewportHeight);

        // Detect projection type changes
        Camera.ProjectionType currentType = camera.getProjectionType();
        boolean projectionChanged = (currentType != lastProjectionType);

        // Switch culler if projection type changed
        if (projectionChanged) {
            activeCuller = (currentType == Camera.ProjectionType.ORTHOGRAPHIC)
                    ? orthographicCuller
                    : perspectiveCuller;
            lastProjectionType = currentType;
        }

        // Update culler if camera or viewport changed
        if (viewportChanged || projectionChanged || camera != activeCuller.camera) {
            activeCuller.updateFromCamera(camera);
            lastViewportWidth = currentWidth;
            lastViewportHeight = currentHeight;
        }

        // Reset statistics for new frame
        statistics.startFrame();
    }

    /**
     * Tests if a sprite should be rendered (is visible).
     * Updates culling statistics.
     *
     * @param spriteRenderer The sprite to test
     * @return true if sprite should be rendered
     */
    public boolean shouldRender(SpriteRenderer spriteRenderer) {
        statistics.incrementTotal();

        boolean visible = activeCuller.isVisible(spriteRenderer);

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
     * Gets the currently active culler.
     */
    public FrustumCuller getActiveCuller() {
        return activeCuller;
    }

    /**
     * Resets the culling system.
     */
    public void reset() {
        lastViewportWidth = -1;
        lastViewportHeight = -1;
        lastProjectionType = null;
        statistics.reset();
    }
}