package com.pocket.rpg.rendering.core;

import org.joml.Vector4f;

/**
 * Interface for rendering fullscreen overlays.
 * Platform-agnostic abstraction for screen-space effects.
 * <p>
 * Used for:
 * - Transition effects (fades, luma wipes)
 * - Screen overlays
 * - Fullscreen effects
 */
public interface OverlayRenderer {

    /**
     * Initializes the overlay renderer.
     * Must be called before rendering.
     */
    void init();

    /**
     * Draws a fullscreen quad with the specified color.
     * Alpha channel controls transparency.
     *
     * @param color RGBA color (use w component for alpha: 0=transparent, 1=opaque)
     */
    void drawFullscreenQuad(Vector4f color);

    /**
     * Draws a luma wipe effect using a grayscale texture as a wipe pattern.
     * Pixels where the luma value is less than the cutoff are drawn with the given color.
     *
     * @param color     RGBA color of the overlay
     * @param cutoff    cutoff threshold (0.0 = nothing drawn, 1.0 = fully drawn)
     * @param textureId OpenGL texture ID of the grayscale luma texture
     */
    void drawLumaWipe(Vector4f color, float cutoff, int textureId);

    /**
     * Updates the screen size for accurate rendering calculations.
     * Should be called when the window is resized.
     *
     * @param width  screen width in pixels
     * @param height screen height in pixels
     */
    void setScreenSize(int width, int height);

    /**
     * Cleans up resources.
     * Must be called when the overlay renderer is no longer needed.
     */
    void destroy();

    /**
     * Checks if the renderer is initialized.
     *
     * @return true if initialized, false otherwise
     */
    boolean isInitialized();
}
