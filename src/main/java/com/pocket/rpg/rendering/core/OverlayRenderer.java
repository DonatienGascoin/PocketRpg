package com.pocket.rpg.rendering.core;

import org.joml.Vector4f;

/**
 * Interface for rendering fullscreen overlays.
 * Platform-agnostic abstraction for screen-space effects.
 * <p>
 * Used for:
 * - Transition effects (fades, wipes)
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
     * Draws a wipe effect from left to right.
     *
     * @param color    color of the wipe overlay
     * @param progress wipe progress (0.0 = nothing, 1.0 = full screen)
     */
    void drawWipeLeft(Vector4f color, float progress);

    /**
     * Draws a wipe effect from right to left.
     *
     * @param color    color of the wipe overlay
     * @param progress wipe progress (0.0 = nothing, 1.0 = full screen)
     */
    void drawWipeRight(Vector4f color, float progress);

    /**
     * Draws a wipe effect from top to bottom.
     *
     * @param color    color of the wipe overlay
     * @param progress wipe progress (0.0 = nothing, 1.0 = full screen)
     */
    void drawWipeUp(Vector4f color, float progress);

    /**
     * Draws a wipe effect from bottom to top.
     *
     * @param color    color of the wipe overlay
     * @param progress wipe progress (0.0 = nothing, 1.0 = full screen)
     */
    void drawWipeDown(Vector4f color, float progress);

    /**
     * Draws a circular wipe effect.
     *
     * @param color     color of the wipe overlay
     * @param progress  wipe progress (0.0 = nothing, 1.0 = full screen)
     * @param expanding true for expanding circle, false for contracting
     */
    void drawCircleWipe(Vector4f color, float progress, boolean expanding);

    /**
     * Updates the screen size for accurate circle rendering.
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