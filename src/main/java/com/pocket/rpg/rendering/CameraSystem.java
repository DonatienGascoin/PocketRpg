package com.pocket.rpg.rendering;

import com.pocket.rpg.config.GameConfig;
import com.pocket.rpg.core.Camera;
import lombok.Getter;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * CameraSystem bridges camera parameters with game resolution.
 * <p>
 * Responsibilities:
 * 1. Store fixed game resolution (internal rendering resolution)
 * 2. Manage viewport size (window dimensions)
 * 3. Build projection matrices from game resolution
 * 4. Coordinate conversion (viewport ↔ game ↔ world)
 * <p>
 * KEY DESIGN CHANGE: This is NO LONGER a singleton.
 * It's owned by GameEngine and injected where needed.
 */
public class CameraSystem {

    // Fixed game resolution (internal rendering resolution)
    @Getter
    private final int gameWidth;
    @Getter
    private final int gameHeight;

    // Physical window size (can change when window resizes)
    @Getter
    private int viewportWidth;
    @Getter
    private int viewportHeight;

    // Orthographic projection settings
    @Getter
    private float nearPlane = -1.0f;
    @Getter
    private float farPlane = 1.0f;

    // Cached projection matrix
    private final Matrix4f cachedProjectionMatrix = new Matrix4f();
    private boolean projectionDirty = true;

    /**
     * Creates a CameraSystem with fixed game resolution.
     * This is the internal rendering resolution that never changes.
     *
     * @throws IllegalArgumentException if resolution is invalid
     */
    public CameraSystem(GameConfig config) {
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
        setViewportSize(config.getWindowWidth(), config.getWindowHeight());

        updateProjectionMatrix();

        System.out.println("CameraSystem created with game resolution: " +
                gameWidth + "x" + gameHeight);

    }

    // ======================================================================
    // PROJECTION MATRIX
    // ======================================================================

    /**
     * Build projection matrix for 2D orthographic rendering.
     * Uses game resolution to define screen space (0,0) to (gameWidth, gameHeight).
     */
    private void updateProjectionMatrix() {
        // Standard 2D orthographic projection
        // Left=0, Right=gameWidth, Top=0, Bottom=gameHeight
        cachedProjectionMatrix.identity().ortho(
                0, gameWidth,           // left, right
                gameHeight, 0,          // bottom, top (flipped for screen space)
                nearPlane, farPlane     // near, far
        );
        projectionDirty = false;
    }

    /**
     * Get the projection matrix.
     * This is the same for all cameras since it's based on fixed game resolution.
     * Returns a copy to prevent external modification.
     *
     * @return A copy of the projection matrix
     */
    public Matrix4f getProjectionMatrix() {
        if (projectionDirty) {
            updateProjectionMatrix();
        }
        return new Matrix4f(cachedProjectionMatrix);
    }

    /**
     * Mark projection matrix as dirty (e.g., if near/far planes change).
     */
    private void markProjectionDirty() {
        this.projectionDirty = true;
    }

    /**
     * Converts screen X coordinate to game X coordinate.
     * Accounts for pillarbox/letterbox scaling.
     *
     * @param screenX X coordinate in screen/window pixels
     * @return X coordinate in game resolution pixels
     */
    public float screenToGameX(float screenX) {
        // Calculate the viewport offset and scale
        float windowAspect = (float) viewportWidth / viewportHeight;
        float gameAspect = (float) gameWidth / gameHeight;

        float scale;
        float offsetX = 0;

        if (windowAspect > gameAspect) {
            // Window is wider than game - pillarbox (black bars on sides)
            scale = (float) viewportHeight / gameHeight;
            float scaledWidth = gameWidth * scale;
            offsetX = (viewportWidth - scaledWidth) / 2f;
        } else {
            // Window is taller than game - letterbox (black bars on top/bottom)
            scale = (float) viewportWidth / gameWidth;
        }

        // Convert screen coordinate to game coordinate
        return (screenX - offsetX) / scale;
    }

    /**
     * Converts screen Y coordinate to game Y coordinate.
     * Accounts for pillarbox/letterbox scaling.
     *
     * Note: Screen Y is typically top-down (0 = top), but game Y is bottom-up (0 = bottom).
     * This method handles the conversion.
     *
     * @param screenY Y coordinate in screen/window pixels (0 = top)
     * @return Y coordinate in game resolution pixels (0 = bottom)
     */
    public float screenToGameY(float screenY) {
        // Calculate the viewport offset and scale
        float windowAspect = (float) viewportWidth / viewportHeight;
        float gameAspect = (float) gameWidth / gameHeight;

        float scale;
        float offsetY = 0;

        if (windowAspect > gameAspect) {
            // Window is wider than game - pillarbox
            scale = (float) viewportHeight / gameHeight;
        } else {
            // Window is taller than game - letterbox (black bars on top/bottom)
            scale = (float) viewportWidth / gameWidth;
            float scaledHeight = gameHeight * scale;
            offsetY = (viewportHeight - scaledHeight) / 2f;
        }

        // Convert screen coordinate to game coordinate
        // Also flip Y axis (screen Y=0 is top, game Y=0 is bottom)
        float gameY = (screenY - offsetY) / scale;
        return gameHeight - gameY;  // Flip Y axis
    }

    /**
     * Converts screen coordinates to game coordinates.
     * Convenience method that returns both X and Y.
     *
     * @param screenX X coordinate in screen pixels
     * @param screenY Y coordinate in screen pixels
     * @return Vector2f with game coordinates
     */
    public Vector2f screenToGame(float screenX, float screenY) {
        return new Vector2f(screenToGameX(screenX), screenToGameY(screenY));
    }

    /**
     * Converts game X coordinate to screen X coordinate.
     *
     * @param gameX X coordinate in game resolution pixels
     * @return X coordinate in screen/window pixels
     */
    public float gameToScreenX(float gameX) {
        float windowAspect = (float) viewportWidth / viewportHeight;
        float gameAspect = (float) gameWidth / gameHeight;

        float scale;
        float offsetX = 0;

        if (windowAspect > gameAspect) {
            scale = (float) viewportHeight / gameHeight;
            float scaledWidth = gameWidth * scale;
            offsetX = (viewportWidth - scaledWidth) / 2f;
        } else {
            scale = (float) viewportWidth / gameWidth;
        }

        return gameX * scale + offsetX;
    }

    /**
     * Converts game Y coordinate to screen Y coordinate.
     *
     * @param gameY Y coordinate in game resolution pixels (0 = bottom)
     * @return Y coordinate in screen/window pixels (0 = top)
     */
    public float gameToScreenY(float gameY) {
        float windowAspect = (float) viewportWidth / viewportHeight;
        float gameAspect = (float) gameWidth / gameHeight;

        float scale;
        float offsetY = 0;

        if (windowAspect > gameAspect) {
            scale = (float) viewportHeight / gameHeight;
        } else {
            scale = (float) viewportWidth / gameWidth;
            float scaledHeight = gameHeight * scale;
            offsetY = (viewportHeight - scaledHeight) / 2f;
        }

        // Flip Y axis and apply scale/offset
        float flippedGameY = gameHeight - gameY;
        return flippedGameY * scale + offsetY;
    }

    // ======================================================================
    // VIEWPORT MANAGEMENT
    // ======================================================================

    /**
     * Update viewport size when window resizes.
     * This does NOT affect game resolution or projection matrix.
     *
     * @param width  New viewport width in pixels
     * @param height New viewport height in pixels
     */
    public void setViewportSize(int width, int height) {
        if (width <= 0 || height <= 0) {
            System.err.println("WARNING: Invalid viewport size: " + width + "x" + height);
            return;
        }

        if (this.viewportWidth != width || this.viewportHeight != height) {
            this.viewportWidth = width;
            this.viewportHeight = height;
            System.out.println("Viewport size updated: " + width + "x" + height +
                    " (game resolution: " + gameWidth + "x" + gameHeight + ")");
        }
    }

    // ======================================================================
    // COORDINATE CONVERSION - Viewport ↔ Game Resolution
    // ======================================================================

    /**
     * Convert viewport screen coordinates to game resolution coordinates.
     * This scales mouse position from window size to internal game resolution.
     * <p>
     * Example: Window is 1920x1080, game is 640x480
     * Mouse at (960, 540) → Game coords (320, 240)
     *
     * @param viewportX X coordinate in viewport (window pixels)
     * @param viewportY Y coordinate in viewport (window pixels)
     * @return Position in game resolution coordinates
     */
    public Vector2f viewportToGame(float viewportX, float viewportY) {
        float gameX = (viewportX / viewportWidth) * gameWidth;
        float gameY = (viewportY / viewportHeight) * gameHeight;
        return new Vector2f(gameX, gameY);
    }

    /**
     * Convert game resolution coordinates to viewport screen coordinates.
     *
     * @param gameX X coordinate in game resolution
     * @param gameY Y coordinate in game resolution
     * @return Position in viewport coordinates
     */
    public Vector2f gameToViewport(float gameX, float gameY) {
        float viewportX = (gameX / gameWidth) * viewportWidth;
        float viewportY = (gameY / gameHeight) * viewportHeight;
        return new Vector2f(viewportX, viewportY);
    }

    // ======================================================================
    // COORDINATE CONVERSION - Game ↔ World (with Camera)
    // ======================================================================

    /**
     * Convert game resolution coordinates to world coordinates using camera.
     * <p>
     * Pipeline: Game coords → NDC → View space → World space
     *
     * @param camera The camera defining the view
     * @param gameX  X coordinate in game resolution (0 to gameWidth)
     * @param gameY  Y coordinate in game resolution (0 to gameHeight)
     * @param depth  Z-depth (typically 0 for 2D)
     * @return Position in world space
     */
    public Vector3f gameToWorld(Camera camera, float gameX, float gameY, float depth) {
        if (camera == null) {
            System.err.println("ERROR: Cannot convert to world - camera is null");
            return new Vector3f(gameX, gameY, depth);
        }

        // Game coords → NDC (Normalized Device Coordinates)
        float ndcX = (2.0f * gameX) / gameWidth - 1.0f;
        float ndcY = 1.0f - (2.0f * gameY) / gameHeight;

        // Get inverse matrices
        Matrix4f invProjection = new Matrix4f(cachedProjectionMatrix).invert();
        Matrix4f invView = new Matrix4f(camera.getViewMatrix()).invert();

        // NDC → Clip space → View space → World space
        Vector4f clipCoords = new Vector4f(ndcX, ndcY, depth, 1.0f);
        Vector4f viewCoords = invProjection.transform(clipCoords);
        Vector4f worldCoords = invView.transform(viewCoords);

        return new Vector3f(worldCoords.x, worldCoords.y, depth);
    }

    /**
     * Convert world coordinates to game resolution coordinates using camera.
     * <p>
     * Pipeline: World space → View space → Clip space → NDC → Game coords
     *
     * @param camera   The camera defining the view
     * @param worldPos Position in world space
     * @return Position in game resolution coordinates (with Z preserved)
     */
    public Vector3f worldToGame(Camera camera, Vector3f worldPos) {
        if (camera == null) {
            System.err.println("ERROR: Cannot convert to game - camera is null");
            return new Vector3f(worldPos.x, worldPos.y, worldPos.z);
        }

        // Get matrices
        Matrix4f projection = new Matrix4f(cachedProjectionMatrix);
        Matrix4f view = camera.getViewMatrix();

        // World → View → Clip space
        Vector4f clipPos = new Vector4f(worldPos.x, worldPos.y, worldPos.z, 1.0f);
        view.transform(clipPos);
        projection.transform(clipPos);

        // Clip → NDC (no perspective divide needed for orthographic)
        float ndcX = clipPos.x;
        float ndcY = clipPos.y;

        // NDC → Game coords
        float gameX = (ndcX + 1.0f) * 0.5f * gameWidth;
        float gameY = (1.0f - ndcY) * 0.5f * gameHeight;

        return new Vector3f(gameX, gameY, clipPos.z);
    }

    // ======================================================================
    // COORDINATE CONVERSION - Viewport ↔ World (Full Pipeline)
    // ======================================================================

    /**
     * Convert viewport coordinates directly to world coordinates.
     * Combines viewport→game and game→world conversions.
     * <p>
     * This is the most common use case for mouse input.
     *
     * @param camera    The camera defining the view
     * @param viewportX X coordinate in viewport (window pixels)
     * @param viewportY Y coordinate in viewport (window pixels)
     * @param depth     Z-depth (typically 0 for 2D)
     * @return Position in world space
     */
    public Vector3f viewportToWorld(Camera camera, float viewportX, float viewportY, float depth) {
        // Step 1: Viewport → Game resolution
        Vector2f gameCoords = viewportToGame(viewportX, viewportY);

        // Step 2: Game → World (using camera)
        return gameToWorld(camera, gameCoords.x, gameCoords.y, depth);
    }

    /**
     * Convert world coordinates directly to viewport coordinates.
     * Combines world→game and game→viewport conversions.
     *
     * @param camera   The camera defining the view
     * @param worldPos Position in world space
     * @return Position in viewport coordinates
     */
    public Vector2f worldToViewport(Camera camera, Vector3f worldPos) {
        // Step 1: World → Game resolution (using camera)
        Vector3f gameCoords = worldToGame(camera, worldPos);

        // Step 2: Game → Viewport
        return gameToViewport(gameCoords.x, gameCoords.y);
    }

    // ======================================================================
    // UTILITY - Screen Bounds in World Space
    // ======================================================================

    /**
     * Get the world-space bounds visible by the camera.
     * Useful for culling, bounds checking, etc.
     *
     * @param camera The camera
     * @return Array of 4 corners: [topLeft, topRight, bottomRight, bottomLeft]
     */
    public Vector3f[] getWorldBounds(Camera camera) {
        Vector3f topLeft = gameToWorld(camera, 0, 0, 0);
        Vector3f topRight = gameToWorld(camera, gameWidth, 0, 0);
        Vector3f bottomRight = gameToWorld(camera, gameWidth, gameHeight, 0);
        Vector3f bottomLeft = gameToWorld(camera, 0, gameHeight, 0);

        return new Vector3f[]{topLeft, topRight, bottomRight, bottomLeft};
    }

    /**
     * Get the center of the visible world bounds.
     *
     * @param camera The camera
     * @return Center position in world space
     */
    public Vector3f getWorldCenter(Camera camera) {
        float centerX = gameWidth / 2.0f;
        float centerY = gameHeight / 2.0f;
        return gameToWorld(camera, centerX, centerY, 0);
    }

    // ======================================================================
    // GETTERS
    // ======================================================================

    /**
     * Get the game resolution aspect ratio.
     */
    public float getAspectRatio() {
        return (float) gameWidth / (float) gameHeight;
    }

    // ======================================================================
    // ADVANCED SETTINGS
    // ======================================================================

    /**
     * Set orthographic near/far planes.
     * Rarely needed for 2D games, but available for layering.
     *
     * @param near Near clipping plane
     * @param far  Far clipping plane
     */
    public void setOrthographicPlanes(float near, float far) {
        this.nearPlane = near;
        this.farPlane = far;
        markProjectionDirty();
    }

    // ======================================================================
    // DEBUGGING
    // ======================================================================

    @Override
    public String toString() {
        return String.format("CameraSystem[game=%dx%d, viewport=%dx%d, aspect=%.2f]",
                gameWidth, gameHeight, viewportWidth, viewportHeight, getAspectRatio());
    }
}