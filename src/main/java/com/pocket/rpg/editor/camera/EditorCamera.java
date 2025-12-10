package com.pocket.rpg.editor.camera;

import com.pocket.rpg.editor.core.EditorConfig;
import lombok.Getter;
import lombok.Setter;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;

/**
 * Free-movement camera for the Scene Editor.
 * Supports panning (WASD/middle-mouse drag) and zooming (scroll wheel).
 * <p>
 * Unlike the game Camera, this camera:
 * - Has no constraints or bounds
 * - Supports smooth free panning
 * - Has configurable zoom limits
 * - Doesn't require a ViewportConfig (uses viewport dimensions directly)
 */
public class EditorCamera {

    // Camera position (center of view in world units)
    private final Vector3f position = new Vector3f(0, 0, 0);

    // Zoom level (1.0 = normal, 2.0 = 2x zoom in, 0.5 = 2x zoom out)
    @Getter
    private float zoom = 1.0f;

    // Viewport dimensions (pixels)
    @Getter
    private int viewportWidth;
    @Getter
    private int viewportHeight;

    // Configuration
    private final float pixelsPerUnit;
    private final float minZoom;
    private final float maxZoom;
    private final float panSpeed;
    private final float zoomSpeed;

    // Cached matrices
    private final Matrix4f projectionMatrix = new Matrix4f();
    private final Matrix4f viewMatrix = new Matrix4f();
    private boolean projectionDirty = true;
    private boolean viewDirty = true;

    // Pan state (for middle-mouse drag)
    @Setter
    private boolean isPanning = false;
    private final Vector2f panStartMouse = new Vector2f();
    private final Vector3f panStartPosition = new Vector3f();

    /**
     * Creates an editor camera with configuration.
     */
    public EditorCamera(EditorConfig config) {
        this.pixelsPerUnit = config.getPixelsPerUnit();
        this.minZoom = config.getMinZoom();
        this.maxZoom = config.getMaxZoom();
        this.panSpeed = config.getCameraPanSpeed();
        this.zoomSpeed = config.getZoomSpeed();
        this.zoom = config.getDefaultZoom();
    }

    /**
     * Creates an editor camera with explicit settings.
     */
    public EditorCamera(float pixelsPerUnit, float minZoom, float maxZoom, float panSpeed, float zoomSpeed) {
        this.pixelsPerUnit = pixelsPerUnit;
        this.minZoom = minZoom;
        this.maxZoom = maxZoom;
        this.panSpeed = panSpeed;
        this.zoomSpeed = zoomSpeed;
    }

    // ========================================================================
    // VIEWPORT
    // ========================================================================

    /**
     * Sets the viewport dimensions.
     * Call when window/viewport resizes.
     */
    public void setViewportSize(int width, int height) {
        if (width <= 0 || height <= 0) return;

        if (this.viewportWidth != width || this.viewportHeight != height) {
            this.viewportWidth = width;
            this.viewportHeight = height;
            projectionDirty = true;
        }
    }

    // ========================================================================
    // POSITION
    // ========================================================================

    /**
     * Gets camera position (center of view).
     */
    public Vector3f getPosition() {
        return new Vector3f(position);
    }

    /**
     * Sets camera position directly.
     */
    public void setPosition(float x, float y) {
        position.set(x, y, 0);
        viewDirty = true;
    }

    /**
     * Sets camera position directly.
     */
    public void setPosition(Vector3f pos) {
        position.set(pos);
        viewDirty = true;
    }

    /**
     * Moves camera by offset in world units.
     */
    public void translate(float dx, float dy) {
        position.add(dx, dy, 0);
        viewDirty = true;
    }

    /**
     * Centers camera on a world position.
     */
    public void centerOn(float worldX, float worldY) {
        setPosition(worldX, worldY);
    }

    // ========================================================================
    // ZOOM
    // ========================================================================

    /**
     * Sets zoom level (clamped to min/max).
     */
    public void setZoom(float zoom) {
        this.zoom = Math.max(minZoom, Math.min(maxZoom, zoom));
        projectionDirty = true;
    }

    /**
     * Adjusts zoom by delta (from scroll wheel).
     * Positive delta = zoom in, negative = zoom out.
     */
    public void adjustZoom(float delta) {
        // Exponential zoom for smooth feel
        float factor = 1.0f + (delta * zoomSpeed);
        setZoom(zoom * factor);
    }

    /**
     * Zooms toward a screen point (keeps that point stationary).
     *
     * @param screenX Screen X coordinate
     * @param screenY Screen Y coordinate
     * @param delta   Zoom delta (positive = in, negative = out)
     */
    public void zoomToward(float screenX, float screenY, float delta) {
        // Get world position before zoom
        Vector3f worldBefore = screenToWorld(screenX, screenY);

        // Apply zoom
        float oldZoom = zoom;
        adjustZoom(delta);

        // Get world position after zoom
        Vector3f worldAfter = screenToWorld(screenX, screenY);

        // Adjust camera position to keep the point stationary
        position.add(worldBefore.x - worldAfter.x, worldBefore.y - worldAfter.y, 0);
        viewDirty = true;
    }

    /**
     * Resets zoom to 1.0.
     */
    public void resetZoom() {
        setZoom(1.0f);
    }

    // ========================================================================
    // PANNING (keyboard)
    // ========================================================================

    /**
     * Updates camera based on keyboard input.
     * Call each frame with movement direction and delta time.
     *
     * @param moveX     Horizontal movement (-1 = left, 1 = right)
     * @param moveY     Vertical movement (-1 = down, 1 = up)
     * @param deltaTime Time since last frame
     */
    public void updateKeyboardPan(float moveX, float moveY, float deltaTime) {
        if (moveX == 0 && moveY == 0) return;

        // Speed adjusted by zoom (pan slower when zoomed in)
        float adjustedSpeed = panSpeed / zoom;

        translate(moveX * adjustedSpeed * deltaTime, moveY * adjustedSpeed * deltaTime);
    }

    // ========================================================================
    // PANNING (mouse drag)
    // ========================================================================

    /**
     * Starts mouse panning from screen position.
     */
    public void startPan(float screenX, float screenY) {
        isPanning = true;
        panStartMouse.set(screenX, screenY);
        panStartPosition.set(position);
    }

    /**
     * Updates pan during mouse drag.
     */
    public void updatePan(float screenX, float screenY) {
        if (!isPanning) return;

        // Calculate mouse delta in screen pixels
        float dx = screenX - panStartMouse.x;
        float dy = screenY - panStartMouse.y;

        // Convert to world units (invert for natural drag feel)
        float worldDx = -dx / (pixelsPerUnit * zoom);
        float worldDy = dy / (pixelsPerUnit * zoom);  // Y-up coordinate system

        // Set position relative to pan start
        position.set(panStartPosition.x + worldDx, panStartPosition.y + worldDy, 0);
        viewDirty = true;
    }

    /**
     * Ends mouse panning.
     */
    public void endPan() {
        isPanning = false;
    }

    // ========================================================================
    // COORDINATE CONVERSION
    // ========================================================================

    /**
     * Converts screen coordinates to world coordinates.
     *
     * @param screenX Screen X (0 = left edge of viewport)
     * @param screenY Screen Y (0 = top edge of viewport)
     * @return World position
     */
    public Vector3f screenToWorld(float screenX, float screenY) {
        // Screen center in pixels
        float centerX = viewportWidth / 2f;
        float centerY = viewportHeight / 2f;

        // Offset from center in pixels
        float offsetX = screenX - centerX;
        float offsetY = centerY - screenY;  // Flip Y (screen Y=0 at top, world Y=0 at center going up)

        // Convert to world units
        float worldX = position.x + offsetX / (pixelsPerUnit * zoom);
        float worldY = position.y + offsetY / (pixelsPerUnit * zoom);

        return new Vector3f(worldX, worldY, 0);
    }

    /**
     * Converts world coordinates to screen coordinates.
     *
     * @param worldX World X
     * @param worldY World Y
     * @return Screen position (x, y in viewport pixels)
     */
    public Vector2f worldToScreen(float worldX, float worldY) {
        // Offset from camera in world units
        float offsetX = worldX - position.x;
        float offsetY = worldY - position.y;

        // Convert to pixels
        float pixelX = offsetX * pixelsPerUnit * zoom;
        float pixelY = offsetY * pixelsPerUnit * zoom;

        // Add center offset and flip Y
        float screenX = viewportWidth / 2f + pixelX;
        float screenY = viewportHeight / 2f - pixelY;

        return new Vector2f(screenX, screenY);
    }

    /**
     * Gets the tile coordinate at a screen position.
     *
     * @param screenX  Screen X coordinate
     * @param screenY  Screen Y coordinate
     * @param tileSize Size of tiles in world units
     * @return Tile coordinates (x, y)
     */
    public int[] screenToTile(float screenX, float screenY, float tileSize) {
        Vector3f world = screenToWorld(screenX, screenY);
        int tileX = (int) Math.floor(world.x / tileSize);
        int tileY = (int) Math.floor(world.y / tileSize);
        return new int[]{tileX, tileY};
    }

    // ========================================================================
    // VISIBLE BOUNDS
    // ========================================================================

    /**
     * Gets the visible world bounds.
     *
     * @return [left, bottom, right, top] in world coordinates
     */
    public float[] getWorldBounds() {
        float halfWidth = (viewportWidth / 2f) / (pixelsPerUnit * zoom);
        float halfHeight = (viewportHeight / 2f) / (pixelsPerUnit * zoom);

        return new float[]{
                position.x - halfWidth,   // left
                position.y - halfHeight,  // bottom
                position.x + halfWidth,   // right
                position.y + halfHeight   // top
        };
    }

    /**
     * Gets visible width in world units.
     */
    public float getVisibleWidth() {
        return viewportWidth / (pixelsPerUnit * zoom);
    }

    /**
     * Gets visible height in world units.
     */
    public float getVisibleHeight() {
        return viewportHeight / (pixelsPerUnit * zoom);
    }

    /**
     * Checks if a world point is visible.
     */
    public boolean isPointVisible(float worldX, float worldY) {
        float[] bounds = getWorldBounds();
        return worldX >= bounds[0] && worldX <= bounds[2] &&
                worldY >= bounds[1] && worldY <= bounds[3];
    }

    /**
     * Checks if a world rectangle intersects the visible area.
     */
    public boolean isRectVisible(float x, float y, float width, float height) {
        float[] bounds = getWorldBounds();
        return !(x + width < bounds[0] || x > bounds[2] ||
                y + height < bounds[1] || y > bounds[3]);
    }

    // ========================================================================
    // MATRICES
    // ========================================================================

    /**
     * Gets the projection matrix.
     */
    public Matrix4f getProjectionMatrix() {
        if (projectionDirty) {
            updateProjectionMatrix();
        }
        return new Matrix4f(projectionMatrix);
    }

    /**
     * Gets the view matrix.
     */
    public Matrix4f getViewMatrix() {
        if (viewDirty) {
            updateViewMatrix();
        }
        return new Matrix4f(viewMatrix);
    }

    private void updateProjectionMatrix() {
        if (viewportWidth <= 0 || viewportHeight <= 0) {
            projectionMatrix.identity();
            return;
        }

        // Calculate visible world size
        float halfWidth = (viewportWidth / 2f) / (pixelsPerUnit * zoom);
        float halfHeight = (viewportHeight / 2f) / (pixelsPerUnit * zoom);

        // Centered orthographic projection (Y-up)
        projectionMatrix.identity().ortho(
                -halfWidth, halfWidth,
                -halfHeight, halfHeight,
                -1000f, 1000f
        );

        projectionDirty = false;
    }

    private void updateViewMatrix() {
        viewMatrix.identity();
        viewMatrix.translate(-position.x, -position.y, -position.z);
        viewDirty = false;
    }

    // ========================================================================
    // UTILITY
    // ========================================================================

    /**
     * Resets camera to origin with default zoom.
     */
    public void reset() {
        position.set(0, 0, 0);
        zoom = 1.0f;
        projectionDirty = true;
        viewDirty = true;
    }

    @Override
    public String toString() {
        return String.format("EditorCamera[pos=(%.1f,%.1f), zoom=%.2f, visible=%.1fx%.1f]",
                position.x, position.y, zoom, getVisibleWidth(), getVisibleHeight());
    }
}
