package com.pocket.rpg.core;

import com.pocket.rpg.config.GameConfig;
import lombok.Getter;

/**
 * Shared viewport configuration that handles resolution scaling.
 * <p>
 * Contains:
 * - Fixed game resolution (internal rendering resolution)
 * - Dynamic window size (changes on resize)
 * - Pillarbox/letterbox scaling calculations
 * <p>
 * This is shared across all Cameras and UI systems.
 * Created once at startup, updated on window resize.
 */
public class ViewportConfig {

    // Fixed game resolution (never changes after startup)
    @Getter
    private final int gameWidth;
    @Getter
    private final int gameHeight;

    // Dynamic window size (updated on resize)
    @Getter
    private int windowWidth;
    @Getter
    private int windowHeight;

    // Cached scaling values for pillarbox/letterbox
    @Getter
    private float scale;
    @Getter
    private float offsetX;
    @Getter
    private float offsetY;

    /**
     * Creates viewport config from game configuration.
     *
     * @param config Game configuration containing resolution settings
     * @throws IllegalArgumentException if resolution is invalid
     */
    public ViewportConfig(GameConfig config) {
        if (config.getGameWidth() <= 0 || config.getGameHeight() <= 0) {
            throw new IllegalArgumentException("Game resolution must be positive: " +
                    config.getGameWidth() + "x" + config.getGameHeight());
        }
        if (config.getWindowWidth() <= 0 || config.getWindowHeight() <= 0) {
            throw new IllegalArgumentException("Window size must be positive: " +
                    config.getWindowWidth() + "x" + config.getWindowHeight());
        }

        this.gameWidth = config.getGameWidth();
        this.gameHeight = config.getGameHeight();
        this.windowWidth = config.getWindowWidth();
        this.windowHeight = config.getWindowHeight();

        recalculateScaling();

        System.out.println("ViewportConfig created: game=" + gameWidth + "x" + gameHeight +
                ", window=" + windowWidth + "x" + windowHeight);
    }

    /**
     * Creates viewport config with explicit values.
     *
     * @param gameWidth    Fixed game width
     * @param gameHeight   Fixed game height
     * @param windowWidth  Initial window width
     * @param windowHeight Initial window height
     */
    public ViewportConfig(int gameWidth, int gameHeight, int windowWidth, int windowHeight) {
        if (gameWidth <= 0 || gameHeight <= 0) {
            throw new IllegalArgumentException("Game resolution must be positive");
        }
        if (windowWidth <= 0 || windowHeight <= 0) {
            throw new IllegalArgumentException("Window size must be positive");
        }

        this.gameWidth = gameWidth;
        this.gameHeight = gameHeight;
        this.windowWidth = windowWidth;
        this.windowHeight = windowHeight;

        recalculateScaling();
    }

    /**
     * Updates window size. Call this on window resize events.
     *
     * @param width  New window width
     * @param height New window height
     */
    public void setWindowSize(int width, int height) {
        if (width <= 0 || height <= 0) {
            System.err.println("WARNING: Invalid window size: " + width + "x" + height);
            return;
        }

        if (this.windowWidth != width || this.windowHeight != height) {
            this.windowWidth = width;
            this.windowHeight = height;
            recalculateScaling();
            System.out.println("ViewportConfig: window resized to " + width + "x" + height +
                    " (scale=" + String.format("%.2f", scale) + ")");
        }
    }

    /**
     * Recalculates pillarbox/letterbox scaling based on current window and game sizes.
     */
    private void recalculateScaling() {
        float windowAspect = (float) windowWidth / windowHeight;
        float gameAspect = (float) gameWidth / gameHeight;

        if (windowAspect > gameAspect) {
            // Window is wider than game - pillarbox (black bars on sides)
            scale = (float) windowHeight / gameHeight;
            float scaledWidth = gameWidth * scale;
            offsetX = (windowWidth - scaledWidth) / 2f;
            offsetY = 0;
        } else {
            // Window is taller than game - letterbox (black bars on top/bottom)
            scale = (float) windowWidth / gameWidth;
            float scaledHeight = gameHeight * scale;
            offsetX = 0;
            offsetY = (windowHeight - scaledHeight) / 2f;
        }
    }

    // ======================================================================
    // COORDINATE CONVERSION - Window ↔ Game
    // ======================================================================

    /**
     * Converts window X coordinate to game X coordinate.
     * Accounts for pillarbox offset and scaling.
     *
     * @param windowX X coordinate in window pixels
     * @return X coordinate in game resolution
     */
    public float windowToGameX(float windowX) {
        return (windowX - offsetX) / scale;
    }

    /**
     * Converts window Y coordinate to game Y coordinate.
     * Accounts for letterbox offset and scaling.
     *
     * @param windowY Y coordinate in window pixels (0 = top)
     * @return Y coordinate in game resolution (0 = top)
     */
    public float windowToGameY(float windowY) {
        return (windowY - offsetY) / scale;
    }

    /**
     * Converts game X coordinate to window X coordinate.
     *
     * @param gameX X coordinate in game resolution
     * @return X coordinate in window pixels
     */
    public float gameToWindowX(float gameX) {
        return gameX * scale + offsetX;
    }

    /**
     * Converts game Y coordinate to window Y coordinate.
     *
     * @param gameY Y coordinate in game resolution
     * @return Y coordinate in window pixels
     */
    public float gameToWindowY(float gameY) {
        return gameY * scale + offsetY;
    }

    /**
     * Checks if a window coordinate is within the game viewport.
     * Returns false if the coordinate is in the pillarbox/letterbox area.
     *
     * @param windowX X coordinate in window pixels
     * @param windowY Y coordinate in window pixels
     * @return true if coordinate is within game viewport
     */
    public boolean isInGameViewport(float windowX, float windowY) {
        float gameX = windowToGameX(windowX);
        float gameY = windowToGameY(windowY);
        return gameX >= 0 && gameX < gameWidth && gameY >= 0 && gameY < gameHeight;
    }

    // ======================================================================
    // UTILITY
    // ======================================================================

    /**
     * Gets the game aspect ratio.
     */
    public float getGameAspectRatio() {
        return (float) gameWidth / gameHeight;
    }

    /**
     * Gets half game width (useful for centering calculations).
     */
    public float getHalfWidth() {
        return gameWidth / 2f;
    }

    /**
     * Gets half game height (useful for centering calculations).
     */
    public float getHalfHeight() {
        return gameHeight / 2f;
    }

    @Override
    public String toString() {
        return String.format("ViewportConfig[game=%dx%d, window=%dx%d, scale=%.2f, offset=(%.1f,%.1f)]",
                gameWidth, gameHeight, windowWidth, windowHeight, scale, offsetX, offsetY);
    }
}