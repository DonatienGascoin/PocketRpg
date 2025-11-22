package com.pocket.rpg.utils;

import com.pocket.rpg.rendering.CameraSystem;
import org.joml.Vector2f;

/**
 * Utility class for converting between screen coordinates and game coordinates
 * in a pixel-perfect fixed resolution system.
 *
 * This is essential for proper mouse/input handling when the window size
 * differs from the game resolution.
 */
public class CoordinateConverter {

    /**
     * Converts screen coordinates (window pixel coordinates) to game coordinates
     * (fixed internal resolution coordinates).
     *
     * This accounts for the scaling and pillarboxing applied by PostProcessor.
     *
     * @param screenX Mouse X position in window pixels
     * @param screenY Mouse Y position in window pixels
     * @param windowWidth Current window width
     * @param windowHeight Current window height
     * @return Game coordinates as Vector2f
     */
    public static Vector2f screenToGame(float screenX, float screenY,
                                        int windowWidth, int windowHeight) {
        int gameWidth = CameraSystem.getGameWidth();
        int gameHeight = CameraSystem.getGameHeight();

        // Calculate the actual rendered viewport (accounting for aspect ratio preservation)
        float gameAspect = (float) gameWidth / gameHeight;
        float windowAspect = (float) windowWidth / windowHeight;

        int viewportX, viewportY, viewportWidth, viewportHeight;

        if (windowAspect > gameAspect) {
            // Window is wider - pillarboxes on sides
            viewportHeight = windowHeight;
            viewportWidth = (int) (viewportHeight * gameAspect);
            viewportX = (windowWidth - viewportWidth) / 2;
            viewportY = 0;
        } else {
            // Window is taller - letterboxes on top/bottom
            viewportWidth = windowWidth;
            viewportHeight = (int) (viewportWidth / gameAspect);
            viewportX = 0;
            viewportY = (windowHeight - viewportHeight) / 2;
        }

        // Convert screen coordinates to viewport coordinates
        float viewportRelativeX = screenX - viewportX;
        float viewportRelativeY = screenY - viewportY;

        // Check if click is outside the viewport (in pillarbox/letterbox area)
        if (viewportRelativeX < 0 || viewportRelativeX >= viewportWidth ||
                viewportRelativeY < 0 || viewportRelativeY >= viewportHeight) {
            return new Vector2f(-1, -1); // Invalid coordinates
        }

        // Scale to game coordinates
        float gameX = (viewportRelativeX / viewportWidth) * gameWidth;
        float gameY = (viewportRelativeY / viewportHeight) * gameHeight;

        return new Vector2f(gameX, gameY);
    }

    /**
     * Converts game coordinates to screen coordinates.
     *
     * @param gameX X coordinate in game space
     * @param gameY Y coordinate in game space
     * @param windowWidth Current window width
     * @param windowHeight Current window height
     * @return Screen coordinates as Vector2f
     */
    public static Vector2f gameToScreen(float gameX, float gameY,
                                        int windowWidth, int windowHeight) {
        int gameWidth = CameraSystem.getGameWidth();
        int gameHeight = CameraSystem.getGameHeight();

        // Calculate viewport
        float gameAspect = (float) gameWidth / gameHeight;
        float windowAspect = (float) windowWidth / windowHeight;

        int viewportX, viewportY, viewportWidth, viewportHeight;

        if (windowAspect > gameAspect) {
            viewportHeight = windowHeight;
            viewportWidth = (int) (viewportHeight * gameAspect);
            viewportX = (windowWidth - viewportWidth) / 2;
            viewportY = 0;
        } else {
            viewportWidth = windowWidth;
            viewportHeight = (int) (viewportWidth / gameAspect);
            viewportX = 0;
            viewportY = (windowHeight - viewportHeight) / 2;
        }

        // Scale from game to viewport
        float viewportX_f = (gameX / gameWidth) * viewportWidth;
        float viewportY_f = (gameY / gameHeight) * viewportHeight;

        // Convert to screen coordinates
        float screenX = viewportX + viewportX_f;
        float screenY = viewportY + viewportY_f;

        return new Vector2f(screenX, screenY);
    }

    /**
     * Checks if screen coordinates are within the game viewport
     * (not in pillarbox/letterbox area).
     *
     * @param screenX Mouse X position in window pixels
     * @param screenY Mouse Y position in window pixels
     * @param windowWidth Current window width
     * @param windowHeight Current window height
     * @return true if coordinates are within the game viewport
     */
    public static boolean isInGameViewport(float screenX, float screenY,
                                           int windowWidth, int windowHeight) {
        Vector2f gameCoords = screenToGame(screenX, screenY, windowWidth, windowHeight);
        return gameCoords.x >= 0 && gameCoords.y >= 0;
    }

    /**
     * Gets the scale factor between game resolution and viewport.
     * Useful for converting sizes (like brush sizes) between coordinate systems.
     *
     * @param windowWidth Current window width
     * @param windowHeight Current window height
     * @return Scale factor (viewport pixels per game pixel)
     */
    public static float getScaleFactor(int windowWidth, int windowHeight) {
        int gameWidth = CameraSystem.getGameWidth();
        int gameHeight = CameraSystem.getGameHeight();

        float gameAspect = (float) gameWidth / gameHeight;
        float windowAspect = (float) windowWidth / windowHeight;

        if (windowAspect > gameAspect) {
            // Letterboxed - scale based on height
            return (float) windowHeight / gameHeight;
        } else {
            // Pillarboxed - scale based on width
            return (float) windowWidth / gameWidth;
        }
    }
}