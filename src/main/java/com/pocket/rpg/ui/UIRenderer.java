package com.pocket.rpg.ui;

import com.pocket.rpg.components.ui.UICanvas;
import com.pocket.rpg.config.GameConfig;

import java.util.List;

/**
 * Interface for UI rendering systems.
 * Created by PlatformFactory, used by GameEngine.
 */
public interface UIRenderer {

    /**
     * Initialize the UI renderer with game configuration.
     * Uses gameWidth/gameHeight for pillarbox/letterbox UI scaling.
     */
    void init(GameConfig config);

    /**
     * Update viewport size (window size, not game resolution).
     * Used for calculating pillarbox/letterbox offset.
     */
    void setViewportSize(int width, int height);

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