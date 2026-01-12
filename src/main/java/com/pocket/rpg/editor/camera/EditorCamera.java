package com.pocket.rpg.editor.camera;

import com.pocket.rpg.editor.core.EditorConfig;
import com.pocket.rpg.rendering.core.RenderCamera;
import lombok.Getter;
import lombok.Setter;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;

/**
 * Free-movement camera for the Scene Editor.
 * Supports panning (WASD/middle-mouse drag) and zooming (scroll wheel).
 * <p>
 * Unlike GameCamera, this camera:
 * - Has no constraints or bounds
 * - Supports smooth free panning
 * - Has configurable zoom limits
 * - Uses pixelsPerUnit instead of orthographicSize
 */
public class EditorCamera implements RenderCamera {

    // Camera position (center of view in world units)
    private final Vector3f position = new Vector3f(0, 0, 0);

    @Getter
    private float zoom = 1.0f;

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

    // Pan state
    @Setter
    private boolean isPanning = false;
    private final Vector2f panStartMouse = new Vector2f();
    private final Vector3f panStartPosition = new Vector3f();

    public EditorCamera(EditorConfig config) {
        this.pixelsPerUnit = config.getPixelsPerUnit();
        this.minZoom = config.getMinZoom();
        this.maxZoom = config.getMaxZoom();
        this.panSpeed = config.getCameraPanSpeed();
        this.zoomSpeed = config.getZoomSpeed();
        this.zoom = config.getDefaultZoom();
    }

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

    public Vector3f getPosition() {
        return new Vector3f(position);
    }

    public void setPosition(float x, float y) {
        position.set(x, y, 0);
        viewDirty = true;
    }

    public void setPosition(Vector3f pos) {
        position.set(pos);
        viewDirty = true;
    }

    public void translate(float dx, float dy) {
        position.add(dx, dy, 0);
        viewDirty = true;
    }

    public void centerOn(float worldX, float worldY) {
        setPosition(worldX, worldY);
    }

    // ========================================================================
    // ZOOM
    // ========================================================================

    public void setZoom(float zoom) {
        this.zoom = Math.max(minZoom, Math.min(maxZoom, zoom));
        projectionDirty = true;
    }

    public void adjustZoom(float delta) {
        float factor = 1.0f + (delta * zoomSpeed);
        setZoom(zoom * factor);
    }

    public void zoomToward(float screenX, float screenY, float delta) {
        Vector3f worldBefore = screenToWorld(screenX, screenY);
        adjustZoom(delta);
        Vector3f worldAfter = screenToWorld(screenX, screenY);

        position.add(worldBefore.x - worldAfter.x, worldBefore.y - worldAfter.y, 0);
        viewDirty = true;
    }

    public void resetZoom() {
        setZoom(1.0f);
    }

    // ========================================================================
    // PANNING (keyboard)
    // ========================================================================

    public void updateKeyboardPan(float moveX, float moveY, float deltaTime) {
        if (moveX == 0 && moveY == 0) return;

        float adjustedSpeed = panSpeed / zoom;
        translate(moveX * adjustedSpeed * deltaTime, moveY * adjustedSpeed * deltaTime);
    }

    // ========================================================================
    // PANNING (mouse drag)
    // ========================================================================

    public void startPan(float screenX, float screenY) {
        isPanning = true;
        panStartMouse.set(screenX, screenY);
        panStartPosition.set(position);
    }

    public void updatePan(float screenX, float screenY) {
        if (!isPanning) return;

        float dx = screenX - panStartMouse.x;
        float dy = screenY - panStartMouse.y;

        float worldDx = -dx / (pixelsPerUnit * zoom);
        float worldDy = dy / (pixelsPerUnit * zoom);

        position.set(panStartPosition.x + worldDx, panStartPosition.y + worldDy, 0);
        viewDirty = true;
    }

    public void endPan() {
        isPanning = false;
    }

    // ========================================================================
    // COORDINATE CONVERSION - RenderCamera interface
    // ========================================================================

    @Override
    public Vector3f screenToWorld(float screenX, float screenY) {
        float centerX = viewportWidth / 2f;
        float centerY = viewportHeight / 2f;

        float offsetX = screenX - centerX;
        float offsetY = centerY - screenY;

        float worldX = position.x + offsetX / (pixelsPerUnit * zoom);
        float worldY = position.y + offsetY / (pixelsPerUnit * zoom);

        return new Vector3f(worldX, worldY, 0);
    }

    @Override
    public Vector2f worldToScreen(float worldX, float worldY) {
        float offsetX = worldX - position.x;
        float offsetY = worldY - position.y;

        float pixelX = offsetX * pixelsPerUnit * zoom;
        float pixelY = offsetY * pixelsPerUnit * zoom;

        float screenX = viewportWidth / 2f + pixelX;
        float screenY = viewportHeight / 2f - pixelY;

        return new Vector2f(screenX, screenY);
    }

    public int[] screenToTile(float screenX, float screenY, float tileSize) {
        Vector3f world = screenToWorld(screenX, screenY);
        int tileX = (int) Math.floor(world.x / tileSize);
        int tileY = (int) Math.floor(world.y / tileSize);
        return new int[]{tileX, tileY};
    }

    // ========================================================================
    // VISIBLE BOUNDS - RenderCamera interface
    // ========================================================================

    @Override
    public float[] getWorldBounds() {
        float halfWidth = (viewportWidth / 2f) / (pixelsPerUnit * zoom);
        float halfHeight = (viewportHeight / 2f) / (pixelsPerUnit * zoom);

        return new float[]{
                position.x - halfWidth,
                position.y - halfHeight,
                position.x + halfWidth,
                position.y + halfHeight
        };
    }

    public float getVisibleWidth() {
        return viewportWidth / (pixelsPerUnit * zoom);
    }

    public float getVisibleHeight() {
        return viewportHeight / (pixelsPerUnit * zoom);
    }

    public boolean isPointVisible(float worldX, float worldY) {
        float[] bounds = getWorldBounds();
        return worldX >= bounds[0] && worldX <= bounds[2] &&
                worldY >= bounds[1] && worldY <= bounds[3];
    }

    public boolean isRectVisible(float x, float y, float width, float height) {
        float[] bounds = getWorldBounds();
        return !(x + width < bounds[0] || x > bounds[2] ||
                y + height < bounds[1] || y > bounds[3]);
    }

    // ========================================================================
    // MATRICES - RenderCamera interface
    // ========================================================================

    @Override
    public Matrix4f getProjectionMatrix() {
        if (projectionDirty) {
            updateProjectionMatrix();
        }
        return new Matrix4f(projectionMatrix);
    }

    @Override
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

        float halfWidth = (viewportWidth / 2f) / (pixelsPerUnit * zoom);
        float halfHeight = (viewportHeight / 2f) / (pixelsPerUnit * zoom);

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