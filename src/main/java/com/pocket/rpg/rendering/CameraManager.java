package com.pocket.rpg.rendering;

import com.pocket.rpg.core.Camera;
import com.pocket.rpg.input.callbacks.InputCallbacks;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * CameraManager bridges camera parameters with game resolution.
 * Responsibilities:
 * 1. Store fixed game resolution
 * 2. Manage viewport size (window dimensions)
 * 3. Build projection matrices from camera parameters + game resolution
 * 4. Coordinate conversion (viewport ↔ game ↔ world)
 */
public class CameraManager implements InputCallbacks.WindowResizeCallback {
    private static CameraManager instance;

    // Fixed game resolution (internal rendering resolution)
    private final int gameWidth;
    private final int gameHeight;

    // Physical window size (can change when window resizes)
    private int viewportWidth;
    private int viewportHeight;

    // Orthographic projection settings
    private float nearPlane = -1.0f;
    private float farPlane = 1.0f;

    // Cached projection matrix
    private Matrix4f cachedProjectionMatrix = new Matrix4f();
    private boolean projectionDirty = true;

    private CameraManager(int gameWidth, int gameHeight) {
        this.gameWidth = gameWidth;
        this.gameHeight = gameHeight;
        this.viewportWidth = gameWidth;
        this.viewportHeight = gameHeight;
        updateProjectionMatrix();
    }

    // ======================================================================
    // INITIALIZATION
    // ======================================================================

    /**
     * Initialize CameraManager with fixed game resolution.
     * This is the internal rendering resolution that never changes.
     */
    public static CameraManager initialize(int gameWidth, int gameHeight) {
        if (instance != null) {
            System.err.println("WARNING: CameraManager already initialized");
            return instance;
        }

        if (gameWidth <= 0 || gameHeight <= 0) {
            throw new IllegalArgumentException("Game resolution must be positive: " +
                    gameWidth + "x" + gameHeight);
        }

        instance = new CameraManager(gameWidth, gameHeight);
        System.out.println("CameraManager initialized with game resolution: " +
                gameWidth + "x" + gameHeight);

        return instance;
    }

    public static CameraManager get() {
        if (instance == null) {
            throw new IllegalStateException("CameraManager not initialized. Call initialize() first.");
        }
        return instance;
    }

    public static boolean isInitialized() {
        return instance != null;
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
     */
    public static Matrix4f getProjectionMatrix() {
        CameraManager manager = get();
        if (manager.projectionDirty) {
            manager.updateProjectionMatrix();
        }
        return new Matrix4f(manager.cachedProjectionMatrix);
    }

    /**
     * Mark projection matrix as dirty (e.g., if near/far planes change).
     */
    private void markProjectionDirty() {
        this.projectionDirty = true;
    }

    // ======================================================================
    // VIEWPORT MANAGEMENT
    // ======================================================================

    /**
     * Update viewport size when window resizes.
     * This does NOT affect game resolution or projection matrix.
     */
    public static void setViewportSize(int width, int height) {
        if (width <= 0 || height <= 0) {
            System.err.println("WARNING: Invalid viewport size: " + width + "x" + height);
            return;
        }

        CameraManager manager = get();
        if (manager.viewportWidth != width || manager.viewportHeight != height) {
            manager.viewportWidth = width;
            manager.viewportHeight = height;
            System.out.println("Viewport size updated: " + width + "x" + height +
                    " (game resolution: " + manager.gameWidth + "x" + manager.gameHeight + ")");
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
     */
    public static Vector2f viewportToGame(float viewportX, float viewportY) {
        CameraManager manager = get();
        float gameX = (viewportX / manager.viewportWidth) * manager.gameWidth;
        float gameY = (viewportY / manager.viewportHeight) * manager.gameHeight;
        return new Vector2f(gameX, gameY);
    }

    /**
     * Convert game resolution coordinates to viewport screen coordinates.
     */
    public static Vector2f gameToViewport(float gameX, float gameY) {
        CameraManager manager = get();
        float viewportX = (gameX / manager.gameWidth) * manager.viewportWidth;
        float viewportY = (gameY / manager.gameHeight) * manager.viewportHeight;
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
    public static Vector3f gameToWorld(Camera camera, float gameX, float gameY, float depth) {
        if (camera == null) {
            System.err.println("ERROR: Cannot convert to world - camera is null");
            return new Vector3f(gameX, gameY, depth);
        }

        CameraManager manager = get();

        // Game coords → NDC (Normalized Device Coordinates)
        float ndcX = (2.0f * gameX) / manager.gameWidth - 1.0f;
        float ndcY = 1.0f - (2.0f * gameY) / manager.gameHeight;

        // Get inverse matrices
        Matrix4f invProjection = new Matrix4f(manager.cachedProjectionMatrix).invert();
        Matrix4f invView = new Matrix4f(camera.getViewMatrix()).invert();

        // NDC → Clip space → View space → World space
        Vector4f clipCoords = new Vector4f(ndcX, ndcY, depth, 1.0f);
        Vector4f viewCoords = invProjection.transform(clipCoords);
        Vector4f worldCoords = invView.transform(viewCoords);

        return new Vector3f(worldCoords.x, worldCoords.y, worldCoords.z);
    }

    /**
     * Convert world coordinates to game resolution coordinates using camera.
     * <p>
     * Pipeline: World space → View space → Clip space → NDC → Game coords
     */
    public static Vector3f worldToGame(Camera camera, Vector3f worldPos) {
        if (camera == null) {
            System.err.println("ERROR: Cannot convert to game - camera is null");
            return new Vector3f(worldPos.x, worldPos.y, worldPos.z);
        }

        CameraManager manager = get();

        // Get matrices
        Matrix4f projection = new Matrix4f(manager.cachedProjectionMatrix);
        Matrix4f view = camera.getViewMatrix();

        // World → View → Clip space
        Vector4f clipPos = new Vector4f(worldPos.x, worldPos.y, worldPos.z, 1.0f);
        view.transform(clipPos);
        projection.transform(clipPos);

        // Clip → NDC (no perspective divide needed for orthographic)
        float ndcX = clipPos.x;
        float ndcY = clipPos.y;

        // NDC → Game coords
        float gameX = (ndcX + 1.0f) * 0.5f * manager.gameWidth;
        float gameY = (1.0f - ndcY) * 0.5f * manager.gameHeight;

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
    public static Vector3f viewportToWorld(Camera camera, float viewportX, float viewportY, float depth) {
        // Step 1: Viewport → Game resolution
        Vector2f gameCoords = viewportToGame(viewportX, viewportY);

        // Step 2: Game → World (using camera)
        return gameToWorld(camera, gameCoords.x, gameCoords.y, depth);
    }

    /**
     * Convert world coordinates directly to viewport coordinates.
     * Combines world→game and game→viewport conversions.
     */
    public static Vector2f worldToViewport(Camera camera, Vector3f worldPos) {
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
    public static Vector3f[] getWorldBounds(Camera camera) {
        CameraManager manager = get();

        Vector3f topLeft = gameToWorld(camera, 0, 0, 0);
        Vector3f topRight = gameToWorld(camera, manager.gameWidth, 0, 0);
        Vector3f bottomRight = gameToWorld(camera, manager.gameWidth, manager.gameHeight, 0);
        Vector3f bottomLeft = gameToWorld(camera, 0, manager.gameHeight, 0);

        return new Vector3f[]{topLeft, topRight, bottomRight, bottomLeft};
    }

    /**
     * Get the center of the visible world bounds.
     */
    public static Vector3f getWorldCenter(Camera camera) {
        CameraManager manager = get();
        float centerX = manager.gameWidth / 2.0f;
        float centerY = manager.gameHeight / 2.0f;
        return gameToWorld(camera, centerX, centerY, 0);
    }

    // ======================================================================
    // GETTERS
    // ======================================================================

    public static int getGameWidth() {
        return get().gameWidth;
    }

    public static int getGameHeight() {
        return get().gameHeight;
    }

    public static int getViewportWidth() {
        return get().viewportWidth;
    }

    public static int getViewportHeight() {
        return get().viewportHeight;
    }

    public static float getAspectRatio() {
        CameraManager manager = get();
        return (float) manager.gameWidth / (float) manager.gameHeight;
    }

    // ======================================================================
    // ADVANCED SETTINGS
    // ======================================================================

    /**
     * Set orthographic near/far planes.
     * Rarely needed for 2D games, but available for layering.
     */
    public static void setOrthographicPlanes(float near, float far) {
        CameraManager manager = get();
        manager.nearPlane = near;
        manager.farPlane = far;
        manager.markProjectionDirty();
    }

    public static float getNearPlane() {
        return get().nearPlane;
    }

    public static float getFarPlane() {
        return get().farPlane;
    }

    // ======================================================================
    // LIFECYCLE
    // ======================================================================

    public static void destroy() {
        if (instance != null) {
            instance = null;
            System.out.println("CameraManager destroyed");
        }
    }

    @Override
    public String toString() {
        return String.format("CameraManager[game=%dx%d, viewport=%dx%d, aspect=%.2f]",
                gameWidth, gameHeight, viewportWidth, viewportHeight, getAspectRatio());
    }

    @Override
    public void onWindowResize(int width, int height) {
        setViewportSize(width, height);
    }
}