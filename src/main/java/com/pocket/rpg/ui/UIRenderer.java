package com.pocket.rpg.ui;

import java.util.List;

/**
 * Interface for UI rendering systems.
 * Created by PlatformFactory, used by GameEngine.
 */
public interface UIRenderer {

    /**
     * Initialize the UI renderer.
     */
    void init();

    /**
     * Update screen size for projection matrix.
     */
    void setScreenSize(int width, int height);

    /**
     * Render all UI canvases.
     * Canvases should be pre-sorted by sortOrder.
     *
     * @param canvases List of canvases sorted by sortOrder (ascending)
     */
    void render(List<UICanvas> canvases);

    /**
     * Clean up resources.
     */
    void destroy();
}