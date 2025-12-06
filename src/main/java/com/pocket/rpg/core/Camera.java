package com.pocket.rpg.core;

import lombok.Getter;
import lombok.Setter;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * Unified Camera for 2D orthographic rendering with world-unit coordinate system.
 * <p>
 * Handles:
 * - Transform (position, rotation, zoom)
 * - Projection matrix (orthographic, based on orthographicSize)
 * - View matrix (position is CENTER of view)
 * - Coordinate conversion (window ↔ game ↔ world)
 *
 * <h2>Coordinate System</h2>
 * The camera uses a centered Y-up coordinate system:
 * <ul>
 *   <li>Origin (0, 0) is at the center of the camera view</li>
 *   <li>Positive X points right</li>
 *   <li>Positive Y points up</li>
 *   <li>Positive Z points toward the camera (depth sorting)</li>
 * </ul>
 *
 * <h2>Orthographic Size</h2>
 * The {@link #orthographicSize} defines the half-height of the visible area in world units.
 * <pre>
 * Visible Height = orthographicSize × 2
 * Visible Width  = Visible Height × aspectRatio
 * </pre>
 *
 * <h2>Unity-Style Behavior</h2>
 * <ul>
 *   <li>{@code camera.setPosition(player.position)} centers the player on screen</li>
 *   <li>{@code screenToWorldPoint()} converts mouse position to world coordinates</li>
 *   <li>{@code worldToScreenPoint()} converts world position to screen coordinates</li>
 * </ul>
 *
 * @see com.pocket.rpg.config.RenderingConfig#getDefaultOrthographicSize(int)
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

    /**
     * Additional zoom multiplier on top of orthographicSize.
     * <ul>
     *   <li>1.0 = no additional zoom</li>
     *   <li>2.0 = 2× zoom in (sprites appear twice as large)</li>
     *   <li>0.5 = 2× zoom out (sprites appear half as large)</li>
     * </ul>
     */
    @Getter
    private float zoom = 1f;

    // ======================================================================
    // ORTHOGRAPHIC PROJECTION
    // ======================================================================

    /**
     * Half-height of the visible area in world units.
     * <p>
     * The full visible height is {@code orthographicSize × 2}.
     * Width is calculated from aspect ratio.
     * <p>
     * Smaller values = zoomed in (fewer world units visible).
     * Larger values = zoomed out (more world units visible).
     */
    @Getter
    private float orthographicSize = 15f;  // Default: 30 units visible vertically

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

    /**
     * Creates a camera with viewport configuration and initial orthographic size.
     *
     * @param viewport         Shared viewport config
     * @param orthographicSize Half-height in world units
     */
    public Camera(ViewportConfig viewport, float orthographicSize) {
        this(viewport);
        setOrthographicSize(orthographicSize);
    }

    // ======================================================================
    // PROJECTION MATRIX - Centered Y-Up
    // ======================================================================

    /**
     * Updates the orthographic projection matrix.
     * <p>
     * Uses centered projection with Y-up coordinate system:
     * <pre>
     * left   = -halfWidth
     * right  = +halfWidth
     * bottom = -orthographicSize (accounting for zoom)
     * top    = +orthographicSize (accounting for zoom)
     * </pre>
     */
    private void updateProjectionMatrix() {
        float aspectRatio = (float) viewport.getGameWidth() / viewport.getGameHeight();

        // Effective size after zoom (zoom > 1 = see less = smaller ortho)
        float effectiveOrthoSize = orthographicSize / zoom;

        float halfHeight = effectiveOrthoSize;
        float halfWidth = halfHeight * aspectRatio;

        // Centered projection, Y-up
        projectionMatrix.identity().ortho(
                -halfWidth, halfWidth,    // left, right
                -halfHeight, halfHeight,  // bottom, top (Y-up!)
                1000f, -1000f             // near, far (generous range for Z sorting)
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
    // VIEW MATRIX
    // ======================================================================

    /**
     * Updates the view matrix.
     * <p>
     * The view matrix translates and rotates the world opposite to camera movement.
     * Camera position represents the center of the visible area.
     */
    private void updateViewMatrix() {
        viewMatrix.identity();

        // Apply rotation around Z axis (screen center)
        if (rotation != 0) {
            viewMatrix.rotateZ((float) Math.toRadians(-rotation));
        }

        // Translate world opposite to camera position
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
     * Marks matrices as needing recalculation.
     */
    private void markViewDirty() {
        viewDirty = true;
    }

    private void markProjectionDirty() {
        projectionDirty = true;
    }

    // ======================================================================
    // ORTHOGRAPHIC SIZE
    // ======================================================================

    /**
     * Sets the orthographic size (half-height in world units).
     * <p>
     * This controls how much of the world is visible:
     * <ul>
     *   <li>Smaller values = zoomed in (fewer units visible)</li>
     *   <li>Larger values = zoomed out (more units visible)</li>
     * </ul>
     *
     * @param orthographicSize Half-height in world units (must be positive)
     */
    public void setOrthographicSize(float orthographicSize) {
        if (orthographicSize <= 0) {
            System.err.println("WARNING: orthographicSize must be positive, got: " + orthographicSize);
            return;
        }
        this.orthographicSize = orthographicSize;
        markProjectionDirty();
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
     * Sets camera position in world space (2D convenience).
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
     * Moves camera by offset in world units.
     */
    public void translate(float dx, float dy) {
        this.position.add(dx, dy, 0);
        markViewDirty();
    }

    /**
     * Moves camera by offset in world units.
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
     * <p>
     * This is a multiplier on top of orthographicSize:
     * <ul>
     *   <li>1.0 = normal (orthographicSize units visible)</li>
     *   <li>2.0 = 2× zoom in (half as many units visible)</li>
     *   <li>0.5 = 2× zoom out (twice as many units visible)</li>
     * </ul>
     *
     * @param zoom Zoom level (must be positive)
     */
    public void setZoom(float zoom) {
        if (zoom <= 0) {
            System.err.println("WARNING: Zoom must be positive, got: " + zoom);
            return;
        }
        this.zoom = zoom;
        markProjectionDirty();
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
        markProjectionDirty();
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

        // Step 2: Game → NDC
        // Game coordinates: (0,0) top-left to (gameWidth, gameHeight) bottom-right
        // NDC: (-1,-1) bottom-left to (1,1) top-right
        float ndcX = (2f * gameX / viewport.getGameWidth()) - 1f;
        float ndcY = 1f - (2f * gameY / viewport.getGameHeight());  // Flip Y for screen coords

        // Step 3: NDC → World (using inverse matrices)
        Matrix4f invProjection = new Matrix4f(getProjectionMatrix()).invert();
        Matrix4f invView = new Matrix4f(getViewMatrix()).invert();

        Vector4f clipCoords = new Vector4f(ndcX, ndcY, 0, 1f);
        invProjection.transform(clipCoords);
        invView.transform(clipCoords);

        return new Vector3f(clipCoords.x, clipCoords.y, depth);
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
        // Step 1: World → Clip space
        Matrix4f projection = getProjectionMatrix();
        Matrix4f view = getViewMatrix();

        Vector4f pos = new Vector4f(worldPos.x, worldPos.y, worldPos.z, 1f);
        view.transform(pos);
        projection.transform(pos);

        // Step 2: NDC → Game coordinates
        // NDC: (-1,-1) to (1,1)
        // Game: (0,0) top-left to (gameWidth, gameHeight) bottom-right
        float gameX = (pos.x + 1f) * 0.5f * viewport.getGameWidth();
        float gameY = (1f - pos.y) * 0.5f * viewport.getGameHeight();  // Flip Y for screen coords

        // Step 3: Game → Window
        float windowX = viewport.gameToWindowX(gameX);
        float windowY = viewport.gameToWindowY(gameY);

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
    // VISIBLE BOUNDS
    // ======================================================================

    /**
     * Gets the visible world bounds (accounting for zoom and orthographicSize).
     * Camera is at the CENTER of these bounds.
     *
     * @return [left, bottom, right, top] in world coordinates (Y-up)
     */
    public float[] getWorldBounds() {
        float aspectRatio = (float) viewport.getGameWidth() / viewport.getGameHeight();
        float effectiveOrthoSize = orthographicSize / zoom;

        float halfH = effectiveOrthoSize;
        float halfW = halfH * aspectRatio;

        return new float[]{
                position.x - halfW,  // left
                position.y - halfH,  // bottom
                position.x + halfW,  // right
                position.y + halfH   // top
        };
    }

    /**
     * Gets the visible world width in world units (accounting for zoom).
     */
    public float getVisibleWidth() {
        float aspectRatio = (float) viewport.getGameWidth() / viewport.getGameHeight();
        float effectiveOrthoSize = orthographicSize / zoom;
        return effectiveOrthoSize * 2 * aspectRatio;
    }

    /**
     * Gets the visible world height in world units (accounting for zoom).
     */
    public float getVisibleHeight() {
        float effectiveOrthoSize = orthographicSize / zoom;
        return effectiveOrthoSize * 2;
    }

    /**
     * Gets the world-space corners visible by this camera.
     *
     * @return Array of 4 corners: [bottomLeft, bottomRight, topRight, topLeft]
     */
    public Vector3f[] getWorldCorners() {
        float[] bounds = getWorldBounds();
        return new Vector3f[]{
                new Vector3f(bounds[0], bounds[1], 0),  // bottom-left
                new Vector3f(bounds[2], bounds[1], 0),  // bottom-right
                new Vector3f(bounds[2], bounds[3], 0),  // top-right
                new Vector3f(bounds[0], bounds[3], 0)   // top-left
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
     * Gets the game width from viewport config (in pixels).
     */
    public int getGameWidth() {
        return viewport.getGameWidth();
    }

    /**
     * Gets the game height from viewport config (in pixels).
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
        return String.format("Camera[pos=(%.1f,%.1f,%.1f), rot=%.1f°, orthoSize=%.1f, zoom=%.2f, visible=%.1fx%.1f]",
                position.x, position.y, position.z, rotation, orthographicSize, zoom,
                getVisibleWidth(), getVisibleHeight());
    }
}