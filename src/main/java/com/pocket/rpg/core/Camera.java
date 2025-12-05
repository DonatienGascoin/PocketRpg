package com.pocket.rpg.core;

import lombok.Getter;
import lombok.Setter;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * Unified Camera for 2D orthographic rendering.
 * <p>
 * Handles:
 * - Transform (position, rotation, zoom)
 * - Projection matrix (orthographic, based on game resolution)
 * - View matrix (centered - position is CENTER of view, not corner)
 * - Coordinate conversion (window ↔ game ↔ world)
 * <p>
 * Unity-style behavior:
 * - camera.setPosition(player.position) centers the player on screen
 * - screenToWorldPoint() converts mouse position to world coordinates
 * - worldToScreenPoint() converts world position to screen coordinates
 * <p>
 * Uses shared ViewportConfig for resolution and window scaling.
 */
public class Camera {

    // ======================================================================
    // STATIC - Main Camera Reference (Unity's Camera.main equivalent)
    // ======================================================================

    @Getter
    @Setter
    private static Camera mainCamera;

    // ======================================================================
    // SHARED CONFIGURATION
    // ======================================================================

    private final ViewportConfig viewport;

    // ======================================================================
    // TRANSFORM (per-camera)
    // ======================================================================

    private final Vector3f position = new Vector3f(0, 0, 0);

    @Getter
    private float rotation = 0f;  // Z-axis rotation in degrees

    @Getter
    private float zoom = 1f;  // 1.0 = 1 game pixel = 1 world unit

    // ======================================================================
    // CACHED MATRICES
    // ======================================================================

    private final Matrix4f projectionMatrix = new Matrix4f();
    private final Matrix4f viewMatrix = new Matrix4f();

    private boolean projectionDirty = true;
    private boolean viewDirty = true;

    // ======================================================================
    // CONSTRUCTOR
    // ======================================================================

    /**
     * Creates a camera with shared viewport configuration.
     *
     * @param viewport Shared viewport config (game resolution, window size, scaling)
     */
    public Camera(ViewportConfig viewport) {
        if (viewport == null) {
            throw new IllegalArgumentException("ViewportConfig cannot be null");
        }
        this.viewport = viewport;

        // Initialize matrices
        updateProjectionMatrix();
        updateViewMatrix();
    }

    // ======================================================================
    // PROJECTION MATRIX
    // ======================================================================

    /**
     * Updates the orthographic projection matrix.
     * Uses game resolution (0,0) to (gameWidth, gameHeight).
     */
    private void updateProjectionMatrix() {
        projectionMatrix.identity().ortho(
                0, viewport.getGameWidth(),      // left, right
                viewport.getGameHeight(), 0,     // bottom, top (Y=0 at top)
                -1f, 1f                          // near, far
        );
        projectionDirty = false;
    }

    /**
     * Gets the projection matrix.
     * Returns a copy to prevent external modification.
     */
    public Matrix4f getProjectionMatrix() {
        if (projectionDirty) {
            updateProjectionMatrix();
        }
        return new Matrix4f(projectionMatrix);
    }

    // ======================================================================
    // VIEW MATRIX (CENTERED!)
    // ======================================================================

    /**
     * Updates the view matrix with CENTER offset.
     * <p>
     * This is the key fix: camera position represents the CENTER of the view,
     * not the top-left corner. When you call setPosition(player.pos), the
     * player will appear at the center of the screen.
     */
    private void updateViewMatrix() {
        viewMatrix.identity();

        // 1. Apply zoom
        viewMatrix.scale(zoom, zoom, 1.0f);

        // 2. CENTER OFFSET - translate by half visible area
        //    This makes camera position = center of view
        float visibleWidth = viewport.getGameWidth() / zoom;
        float visibleHeight = viewport.getGameHeight() / zoom;
        viewMatrix.translate(visibleWidth / 2f, visibleHeight / 2f, 0);

        // 3. Apply rotation (around the center)
        if (rotation != 0) {
            viewMatrix.rotateZ((float) Math.toRadians(-rotation));
        }

        // 4. Apply camera position (moves world opposite to camera)
        viewMatrix.translate(-position.x, -position.y, -position.z);

        viewDirty = false;
    }

    /**
     * Gets the view matrix.
     * Returns a copy to prevent external modification.
     */
    public Matrix4f getViewMatrix() {
        if (viewDirty) {
            updateViewMatrix();
        }
        return new Matrix4f(viewMatrix);
    }

    /**
     * Marks the view matrix as needing recalculation.
     */
    private void markViewDirty() {
        viewDirty = true;
    }

    // ======================================================================
    // TRANSFORM - Position
    // ======================================================================

    /**
     * Sets camera position in world space.
     * The camera will be centered on this position.
     */
    public void setPosition(Vector3f pos) {
        this.position.set(pos);
        markViewDirty();
    }

    /**
     * Sets camera position in world space.
     */
    public void setPosition(float x, float y) {
        this.position.set(x, y, 0);
        markViewDirty();
    }

    /**
     * Sets camera position in world space.
     */
    public void setPosition(float x, float y, float z) {
        this.position.set(x, y, z);
        markViewDirty();
    }

    /**
     * Gets camera position (returns a copy).
     */
    public Vector3f getPosition() {
        return new Vector3f(position);
    }

    /**
     * Moves camera by offset.
     */
    public void translate(float dx, float dy) {
        this.position.add(dx, dy, 0);
        markViewDirty();
    }

    /**
     * Moves camera by offset.
     */
    public void translate(float dx, float dy, float dz) {
        this.position.add(dx, dy, dz);
        markViewDirty();
    }

    // ======================================================================
    // TRANSFORM - Rotation
    // ======================================================================

    /**
     * Sets camera rotation (Z-axis, in degrees).
     */
    public void setRotation(float degrees) {
        this.rotation = degrees;
        markViewDirty();
    }

    /**
     * Rotates camera by offset (in degrees).
     */
    public void rotate(float degrees) {
        this.rotation += degrees;
        markViewDirty();
    }

    // ======================================================================
    // TRANSFORM - Zoom
    // ======================================================================

    /**
     * Sets camera zoom level.
     *
     * @param zoom Zoom level (1.0 = normal, 2.0 = 2x zoom in, 0.5 = 2x zoom out)
     */
    public void setZoom(float zoom) {
        if (zoom <= 0) {
            System.err.println("WARNING: Zoom must be positive, got: " + zoom);
            return;
        }
        this.zoom = zoom;
        markViewDirty();
    }

    /**
     * Adjusts zoom by a multiplier.
     *
     * @param factor Multiplier (2.0 = double zoom, 0.5 = halve zoom)
     */
    public void zoomBy(float factor) {
        if (factor <= 0) {
            System.err.println("WARNING: Zoom factor must be positive, got: " + factor);
            return;
        }
        this.zoom *= factor;
        markViewDirty();
    }

    // ======================================================================
    // COORDINATE CONVERSION - Unity-style API
    // ======================================================================

    /**
     * Convert screen (window) coordinates to world coordinates.
     * Unity equivalent: Camera.ScreenToWorldPoint()
     * <p>
     * Handles pillarbox/letterbox scaling automatically.
     *
     * @param screenX X coordinate in window pixels
     * @param screenY Y coordinate in window pixels
     * @return Position in world space
     */
    public Vector3f screenToWorldPoint(float screenX, float screenY) {
        return screenToWorldPoint(screenX, screenY, 0);
    }

    /**
     * Convert screen (window) coordinates to world coordinates.
     *
     * @param screenX X coordinate in window pixels
     * @param screenY Y coordinate in window pixels
     * @param depth   Z-depth (typically 0 for 2D)
     * @return Position in world space
     */
    public Vector3f screenToWorldPoint(float screenX, float screenY, float depth) {
        // Step 1: Window → Game (using ViewportConfig scaling)
        float gameX = viewport.windowToGameX(screenX);
        float gameY = viewport.windowToGameY(screenY);

        // Step 2: Game → World (using inverse matrices)
        return gameToWorld(gameX, gameY, depth);
    }

    /**
     * Convert screen coordinates to world coordinates.
     *
     * @param screenPos Position in window pixels
     * @return Position in world space
     */
    public Vector3f screenToWorldPoint(Vector2f screenPos) {
        return screenToWorldPoint(screenPos.x, screenPos.y, 0);
    }

    /**
     * Convert world coordinates to screen (window) coordinates.
     * Unity equivalent: Camera.WorldToScreenPoint()
     *
     * @param worldPos Position in world space
     * @return Position in window pixels
     */
    public Vector2f worldToScreenPoint(Vector3f worldPos) {
        // Step 1: World → Game
        Vector2f gamePos = worldToGame(worldPos);

        // Step 2: Game → Window
        float windowX = viewport.gameToWindowX(gamePos.x);
        float windowY = viewport.gameToWindowY(gamePos.y);

        return new Vector2f(windowX, windowY);
    }

    /**
     * Convert world coordinates to screen coordinates.
     *
     * @param worldX X in world space
     * @param worldY Y in world space
     * @return Position in window pixels
     */
    public Vector2f worldToScreenPoint(float worldX, float worldY) {
        return worldToScreenPoint(new Vector3f(worldX, worldY, 0));
    }

    // ======================================================================
    // COORDINATE CONVERSION - Game ↔ World
    // ======================================================================

    /**
     * Convert game resolution coordinates to world coordinates.
     */
    public Vector3f gameToWorld(float gameX, float gameY, float depth) {
        // Convert game coords to NDC
        float ndcX = (2f * gameX / viewport.getGameWidth()) - 1f;
        float ndcY = 1f - (2f * gameY / viewport.getGameHeight());

        // Transform through inverse matrices
        Matrix4f invProjection = new Matrix4f(getProjectionMatrix()).invert();
        Matrix4f invView = new Matrix4f(getViewMatrix()).invert();

        Vector4f clipCoords = new Vector4f(ndcX, ndcY, depth, 1f);
        invProjection.transform(clipCoords);
        invView.transform(clipCoords);

        return new Vector3f(clipCoords.x, clipCoords.y, depth);
    }

    /**
     * Convert world coordinates to game resolution coordinates.
     */
    public Vector2f worldToGame(Vector3f worldPos) {
        // Transform through matrices
        Matrix4f projection = getProjectionMatrix();
        Matrix4f view = getViewMatrix();

        Vector4f pos = new Vector4f(worldPos.x, worldPos.y, worldPos.z, 1f);
        view.transform(pos);
        projection.transform(pos);

        // NDC → Game coords
        float gameX = (pos.x + 1f) * 0.5f * viewport.getGameWidth();
        float gameY = (1f - pos.y) * 0.5f * viewport.getGameHeight();

        return new Vector2f(gameX, gameY);
    }

    // ======================================================================
    // VISIBLE BOUNDS
    // ======================================================================

    /**
     * Gets the visible world bounds (accounting for zoom).
     * Camera is at the CENTER of these bounds.
     *
     * @return [left, top, right, bottom] in world coordinates
     */
    public float[] getWorldBounds() {
        float visibleWidth = viewport.getGameWidth() / zoom;
        float visibleHeight = viewport.getGameHeight() / zoom;
        float halfW = visibleWidth / 2f;
        float halfH = visibleHeight / 2f;

        return new float[]{
                position.x - halfW,  // left
                position.y - halfH,  // top
                position.x + halfW,  // right
                position.y + halfH   // bottom
        };
    }

    /**
     * Gets the visible world width (accounting for zoom).
     */
    public float getVisibleWidth() {
        return viewport.getGameWidth() / zoom;
    }

    /**
     * Gets the visible world height (accounting for zoom).
     */
    public float getVisibleHeight() {
        return viewport.getGameHeight() / zoom;
    }

    /**
     * Gets the world-space corners visible by this camera.
     *
     * @return Array of 4 corners: [topLeft, topRight, bottomRight, bottomLeft]
     */
    public Vector3f[] getWorldCorners() {
        float[] bounds = getWorldBounds();
        return new Vector3f[]{
                new Vector3f(bounds[0], bounds[1], 0),  // top-left
                new Vector3f(bounds[2], bounds[1], 0),  // top-right
                new Vector3f(bounds[2], bounds[3], 0),  // bottom-right
                new Vector3f(bounds[0], bounds[3], 0)   // bottom-left
        };
    }

    /**
     * Checks if a world point is visible on screen.
     *
     * @param worldX X in world space
     * @param worldY Y in world space
     * @return true if visible
     */
    public boolean isPointVisible(float worldX, float worldY) {
        float[] bounds = getWorldBounds();
        return worldX >= bounds[0] && worldX <= bounds[2] &&
                worldY >= bounds[1] && worldY <= bounds[3];
    }

    // ======================================================================
    // VIEWPORT ACCESS
    // ======================================================================

    /**
     * Gets the shared viewport config.
     */
    public ViewportConfig getViewport() {
        return viewport;
    }

    /**
     * Gets the game width from viewport config.
     */
    public int getGameWidth() {
        return viewport.getGameWidth();
    }

    /**
     * Gets the game height from viewport config.
     */
    public int getGameHeight() {
        return viewport.getGameHeight();
    }

    // ======================================================================
    // UTILITY
    // ======================================================================

    /**
     * Called each frame to update camera state.
     * Currently just clears dirty flags after updates.
     */
    public void update(float deltaTime) {
        // Future: could add smoothing, shake effects, etc.
    }

    @Override
    public String toString() {
        return String.format("Camera[pos=(%.1f,%.1f,%.1f), rot=%.1f°, zoom=%.2f, visible=%.0fx%.0f]",
                position.x, position.y, position.z, rotation, zoom,
                getVisibleWidth(), getVisibleHeight());
    }
}