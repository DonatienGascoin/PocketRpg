package com.pocket.rpg.editor.tools;

/**
 * Interface for tools that need viewport bounds for overlay rendering.
 * Eliminates duplicate instanceof chains in SceneViewport.
 */
public interface ViewportAwareTool {
    
    /**
     * Sets the viewport bounds for overlay rendering.
     *
     * @param x      Viewport X position (screen coordinates)
     * @param y      Viewport Y position (screen coordinates)
     * @param width  Viewport width
     * @param height Viewport height
     */
    void setViewportBounds(float x, float y, float width, float height);
}
