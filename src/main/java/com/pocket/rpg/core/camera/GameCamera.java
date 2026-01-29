package com.pocket.rpg.core.camera;

import com.pocket.rpg.core.window.ViewportConfig;
import com.pocket.rpg.rendering.core.RenderCamera;
import lombok.Getter;
import lombok.Setter;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * Runtime camera for 2D orthographic rendering with world-unit coordinate system.
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
 * @see com.pocket.rpg.config.RenderingConfig#getDefaultOrthographicSize(int)
 */
public class GameCamera implements RenderCamera {

    // ======================================================================
    // STATIC - Main Camera Reference
    // ======================================================================

    @Getter
    @Setter
    private static GameCamera mainCamera;

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
    // BOUNDS CLAMPING
    // ======================================================================

    private boolean useBounds = false;
    private float boundsMinX, boundsMinY, boundsMaxX, boundsMaxY;

    /**
     * Enables camera bounds clamping. When enabled, the camera position
     * will be clamped so the visible area stays within the specified bounds.
     */
    public void setBounds(float minX, float minY, float maxX, float maxY) {
        this.useBounds = true;
        this.boundsMinX = minX;
        this.boundsMinY = minY;
        this.boundsMaxX = maxX;
        this.boundsMaxY = maxY;
    }

    /**
     * Returns whether bounds clamping is active.
     */
    public boolean hasBounds() {
        return useBounds;
    }

    /**
     * Disables camera bounds clamping.
     */
    public void clearBounds() {
        this.useBounds = false;
    }

    private void clampToBounds() {
        if (!useBounds) return;

        // Inset bounds by camera's visible half-dimensions so the
        // visible area never extends beyond the configured bounds
        float aspectRatio = (float) viewport.getGameWidth() / viewport.getGameHeight();
        float halfHeight = orthographicSize / zoom;
        float halfWidth = halfHeight * aspectRatio;

        float minX = boundsMinX + halfWidth;
        float maxX = boundsMaxX - halfWidth;
        float minY = boundsMinY + halfHeight;
        float maxY = boundsMaxY - halfHeight;

        // If bounds are smaller than the camera view, center the camera
        if (minX > maxX) {
            float cx = (boundsMinX + boundsMaxX) * 0.5f;
            minX = cx;
            maxX = cx;
        }
        if (minY > maxY) {
            float cy = (boundsMinY + boundsMaxY) * 0.5f;
            minY = cy;
            maxY = cy;
        }

        float x = Math.max(minX, Math.min(maxX, position.x));
        float y = Math.max(minY, Math.min(maxY, position.y));
        position.set(x, y, position.z);
    }

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
    private float orthographicSize = 15f;

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
    public GameCamera(ViewportConfig viewport) {
        if (viewport == null) {
            throw new IllegalArgumentException("ViewportConfig cannot be null");
        }
        this.viewport = viewport;

        updateProjectionMatrix();
        updateViewMatrix();
    }

    /**
     * Creates a camera with viewport configuration and initial orthographic size.
     *
     * @param viewport         Shared viewport config
     * @param orthographicSize Half-height in world units
     */
    public GameCamera(ViewportConfig viewport, float orthographicSize) {
        this(viewport);
        setOrthographicSize(orthographicSize);
    }

    // ======================================================================
    // PROJECTION MATRIX - Centered Y-Up
    // ======================================================================

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
                1000f, -1000f             // near, far
        );

        projectionDirty = false;
    }

    @Override
    public Matrix4f getProjectionMatrix() {
        if (projectionDirty) {
            updateProjectionMatrix();
        }
        return new Matrix4f(projectionMatrix);
    }

    // ======================================================================
    // VIEW MATRIX
    // ======================================================================

    private void updateViewMatrix() {
        viewMatrix.identity();

        if (rotation != 0) {
            viewMatrix.rotateZ((float) Math.toRadians(-rotation));
        }

        viewMatrix.translate(-position.x, -position.y, -position.z);

        viewDirty = false;
    }

    @Override
    public Matrix4f getViewMatrix() {
        if (viewDirty) {
            updateViewMatrix();
        }
        return new Matrix4f(viewMatrix);
    }

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

    public void setPosition(Vector3f pos) {
        this.position.set(pos);
        clampToBounds();
        markViewDirty();
    }

    public void setPosition(float x, float y) {
        this.position.set(x, y, 0);
        clampToBounds();
        markViewDirty();
    }

    public void setPosition(float x, float y, float z) {
        this.position.set(x, y, z);
        clampToBounds();
        markViewDirty();
    }

    public Vector3f getPosition() {
        return new Vector3f(position);
    }

    public void translate(float dx, float dy) {
        this.position.add(dx, dy, 0);
        clampToBounds();
        markViewDirty();
    }

    public void translate(float dx, float dy, float dz) {
        this.position.add(dx, dy, dz);
        clampToBounds();
        markViewDirty();
    }

    // ======================================================================
    // TRANSFORM - Rotation
    // ======================================================================

    public void setRotation(float degrees) {
        this.rotation = degrees;
        markViewDirty();
    }

    public void rotate(float degrees) {
        this.rotation += degrees;
        markViewDirty();
    }

    // ======================================================================
    // TRANSFORM - Zoom
    // ======================================================================

    public void setZoom(float zoom) {
        if (zoom <= 0) {
            System.err.println("WARNING: Zoom must be positive, got: " + zoom);
            return;
        }
        this.zoom = zoom;
        markProjectionDirty();
    }

    public void zoomBy(float factor) {
        if (factor <= 0) {
            System.err.println("WARNING: Zoom factor must be positive, got: " + factor);
            return;
        }
        this.zoom *= factor;
        markProjectionDirty();
    }

    // ======================================================================
    // COORDINATE CONVERSION - RenderCamera interface
    // ======================================================================

    @Override
    public Vector3f screenToWorld(float screenX, float screenY) {
        // Step 1: Window → Game (using ViewportConfig scaling)
        float gameX = viewport.windowToGameX(screenX);
        float gameY = viewport.windowToGameY(screenY);

        // Step 2: Game → NDC
        float ndcX = (2f * gameX / viewport.getGameWidth()) - 1f;
        float ndcY = 1f - (2f * gameY / viewport.getGameHeight());

        // Step 3: NDC → World
        Matrix4f invProjection = new Matrix4f(getProjectionMatrix()).invert();
        Matrix4f invView = new Matrix4f(getViewMatrix()).invert();

        Vector4f clipCoords = new Vector4f(ndcX, ndcY, 0, 1f);
        invProjection.transform(clipCoords);
        invView.transform(clipCoords);

        return new Vector3f(clipCoords.x, clipCoords.y, 0);
    }

    @Override
    public Vector2f worldToScreen(float worldX, float worldY) {
        // Step 1: World → Clip space
        Matrix4f projection = getProjectionMatrix();
        Matrix4f view = getViewMatrix();

        Vector4f pos = new Vector4f(worldX, worldY, 0, 1f);
        view.transform(pos);
        projection.transform(pos);

        // Step 2: NDC → Game coordinates
        float gameX = (pos.x + 1f) * 0.5f * viewport.getGameWidth();
        float gameY = (1f - pos.y) * 0.5f * viewport.getGameHeight();

        // Step 3: Game → Window
        float windowX = viewport.gameToWindowX(gameX);
        float windowY = viewport.gameToWindowY(gameY);

        return new Vector2f(windowX, windowY);
    }

    // ======================================================================
    // COORDINATE CONVERSION - Extended API
    // ======================================================================

    /**
     * Convert screen coordinates to world coordinates with depth.
     */
    public Vector3f screenToWorld(float screenX, float screenY, float depth) {
        Vector3f result = screenToWorld(screenX, screenY);
        result.z = depth;
        return result;
    }

    public Vector3f screenToWorld(Vector2f screenPos) {
        return screenToWorld(screenPos.x, screenPos.y);
    }

    public Vector2f worldToScreen(Vector3f worldPos) {
        return worldToScreen(worldPos.x, worldPos.y);
    }

    // ======================================================================
    // VISIBLE BOUNDS
    // ======================================================================

    @Override
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

    public float getVisibleWidth() {
        float aspectRatio = (float) viewport.getGameWidth() / viewport.getGameHeight();
        float effectiveOrthoSize = orthographicSize / zoom;
        return effectiveOrthoSize * 2 * aspectRatio;
    }

    public float getVisibleHeight() {
        float effectiveOrthoSize = orthographicSize / zoom;
        return effectiveOrthoSize * 2;
    }

    public Vector3f[] getWorldCorners() {
        float[] bounds = getWorldBounds();
        return new Vector3f[]{
                new Vector3f(bounds[0], bounds[1], 0),  // bottom-left
                new Vector3f(bounds[2], bounds[1], 0),  // bottom-right
                new Vector3f(bounds[2], bounds[3], 0),  // top-right
                new Vector3f(bounds[0], bounds[3], 0)   // top-left
        };
    }

    public boolean isPointVisible(float worldX, float worldY) {
        float[] bounds = getWorldBounds();
        return worldX >= bounds[0] && worldX <= bounds[2] &&
                worldY >= bounds[1] && worldY <= bounds[3];
    }

    // ======================================================================
    // VIEWPORT ACCESS
    // ======================================================================

    public ViewportConfig getViewport() {
        return viewport;
    }

    public int getGameWidth() {
        return viewport.getGameWidth();
    }

    public int getGameHeight() {
        return viewport.getGameHeight();
    }

    // ======================================================================
    // UTILITY
    // ======================================================================

    public void update(float deltaTime) {
        // Future: smoothing, shake effects, etc.
    }

    @Override
    public String toString() {
        return String.format("GameCamera[pos=(%.1f,%.1f,%.1f), rot=%.1f°, orthoSize=%.1f, zoom=%.2f, visible=%.1fx%.1f]",
                position.x, position.y, position.z, rotation, orthographicSize, zoom,
                getVisibleWidth(), getVisibleHeight());
    }
}