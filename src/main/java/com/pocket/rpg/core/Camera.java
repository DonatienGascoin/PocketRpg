package com.pocket.rpg.core;

import com.pocket.rpg.rendering.CameraSystem;
import lombok.Getter;
import lombok.Setter;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;

/**
 * Camera for 2D orthographic rendering.
 * Defines WHAT to show (view parameters), not HOW to render it (resolution).
 * CameraSystem handles the bridge between camera parameters and game resolution.
 */
public class Camera {

    // Static reference to main camera (Unity's Camera.main equivalent)
    @Getter
    @Setter
    private static Camera mainCamera;

    // Reference to camera system for coordinate conversions
    @Getter
    @Setter
    private CameraSystem cameraSystem;

    // Camera transform (position, rotation, zoom)
    private Vector3f position = new Vector3f(0, 0, 0);
    @Getter
    private float rotation = 0f; // Z-axis rotation in degrees
    @Getter
    private float zoom = 1f; // Zoom level (1.0 = 1 pixel = 1 world unit)

    // Store last transform values for dirty checking
    private Vector3f lastPosition = new Vector3f();
    private float lastRotation = 0f;
    private float lastZoom = 1f;

    // Dirty flags
    private boolean viewDirty = true;

    // Cached view matrix (built from transform)
    private Matrix4f viewMatrix = new Matrix4f();

    public Camera() {
        // Default camera at origin, no rotation, 1:1 zoom
    }

    /**
     * Update camera (check for transform changes).
     */
    public void update(float deltaTime) {
        boolean posChanged = !lastPosition.equals(position, 0.0001f);
        boolean rotChanged = Math.abs(lastRotation - rotation) > 0.0001f;
        boolean zoomChanged = Math.abs(lastZoom - zoom) > 0.0001f;

        if (posChanged || rotChanged || zoomChanged) {
            lastPosition.set(position);
            lastRotation = rotation;
            lastZoom = zoom;
        }

        viewDirty = false;
    }

    /**
     * Update view matrix based on camera transform.
     * Called internally when view is dirty.
     */
    private void updateViewMatrix() {
        viewMatrix.identity();

        // Apply zoom (scale)
        viewMatrix.scale(zoom, zoom, 1.0f);

        // Apply rotation
        if (rotation != 0) {
            viewMatrix.rotateZ((float) Math.toRadians(-rotation));
        }

        // Apply translation (camera moves opposite to world)
        viewMatrix.translate(-position.x, -position.y, -position.z);

        viewDirty = false;
    }

    /**
     * Gets the view matrix (lazy update).
     * Returns a copy to prevent external modification.
     */
    public Matrix4f getViewMatrix() {
        if (viewDirty) {
            updateViewMatrix();
        }
        return new Matrix4f(viewMatrix);
    }

    /**
     * Marks the view matrix as dirty, forcing rebuild on next access.
     */
    public void markViewDirty() {
        this.viewDirty = true;
    }

    // ======================================================================
    // COORDINATE CONVERSION - Unity-style API
    // ======================================================================

    /**
     * Convert screen (viewport) coordinates to world coordinates.
     * Unity equivalent: Camera.ScreenToWorldPoint()
     * <p>
     * Example usage:
     * <pre>
     * Vector2f mousePos = Input.getMousePosition();
     * Vector3f worldPos = camera.screenToWorldPoint(mousePos.x, mousePos.y);
     * </pre>
     *
     * @param screenX X coordinate in screen/viewport pixels
     * @param screenY Y coordinate in screen/viewport pixels
     * @param depth   Z-depth (typically 0 for 2D)
     * @return Position in world space
     */
    public Vector3f screenToWorldPoint(float screenX, float screenY, float depth) {
        if (cameraSystem == null) {
            System.err.println("ERROR: CameraSystem not set on Camera. Call setCameraSystem() first.");
            return new Vector3f(screenX, screenY, depth);
        }
        return cameraSystem.viewportToWorld(this, screenX, screenY, depth);
    }

    /**
     * Convert screen (viewport) coordinates to world coordinates at Z=0.
     * Convenience overload for 2D games.
     *
     * @param screenX X coordinate in screen/viewport pixels
     * @param screenY Y coordinate in screen/viewport pixels
     * @return Position in world space (Z=0)
     */
    public Vector3f screenToWorldPoint(float screenX, float screenY) {
        return screenToWorldPoint(screenX, screenY, 0);
    }

    /**
     * Convert screen (viewport) coordinates to world coordinates.
     * Accepts Vector2f for convenience.
     *
     * @param screenPos Position in screen/viewport coordinates
     * @return Position in world space (Z=0)
     */
    public Vector3f screenToWorldPoint(Vector2f screenPos) {
        return screenToWorldPoint(screenPos.x, screenPos.y, 0);
    }

    /**
     * Convert world coordinates to screen (viewport) coordinates.
     * Unity equivalent: Camera.WorldToScreenPoint()
     * <p>
     * Example usage:
     * <pre>
     * Vector3f worldPos = new Vector3f(100, 200, 0);
     * Vector2f screenPos = camera.worldToScreenPoint(worldPos);
     * </pre>
     *
     * @param worldPos Position in world space
     * @return Position in screen/viewport coordinates
     */
    public Vector2f worldToScreenPoint(Vector3f worldPos) {
        if (cameraSystem == null) {
            System.err.println("ERROR: CameraSystem not set on Camera. Call setCameraSystem() first.");
            return new Vector2f(worldPos.x, worldPos.y);
        }
        return cameraSystem.worldToViewport(this, worldPos);
    }

    /**
     * Convert world coordinates to screen coordinates.
     * Convenience overload that takes separate coordinates.
     *
     * @param worldX X coordinate in world space
     * @param worldY Y coordinate in world space
     * @return Position in screen/viewport coordinates
     */
    public Vector2f worldToScreenPoint(float worldX, float worldY) {
        return worldToScreenPoint(new Vector3f(worldX, worldY, 0));
    }

    /**
     * Get the world-space bounds visible by this camera.
     * Useful for culling, bounds checking, and positioning UI elements.
     *
     * @return Array of 4 corners: [topLeft, topRight, bottomRight, bottomLeft]
     */
    public Vector3f[] getWorldBounds() {
        if (cameraSystem == null) {
            System.err.println("ERROR: CameraSystem not set on Camera. Call setCameraSystem() first.");
            return new Vector3f[0];
        }
        return cameraSystem.getWorldBounds(this);
    }

    /**
     * Get the center of the visible world bounds.
     *
     * @return Center position in world space
     */
    public Vector3f getWorldCenter() {
        if (cameraSystem == null) {
            System.err.println("ERROR: CameraSystem not set on Camera. Call setCameraSystem() first.");
            return new Vector3f(position);
        }
        return cameraSystem.getWorldCenter(this);
    }

    // ======================================================================
    // CAMERA TRANSFORM
    // ======================================================================

    /**
     * Set camera position in world space.
     */
    public void setPosition(Vector3f position) {
        this.position.set(position);
        viewDirty = true;
    }

    public void setPosition(float x, float y) {
        this.position.set(x, y, 0);
        viewDirty = true;
    }

    public void setPosition(float x, float y, float z) {
        this.position.set(x, y, z);
        viewDirty = true;
    }

    public Vector3f getPosition() {
        return new Vector3f(position);
    }

    /**
     * Move camera by offset.
     */
    public void translate(float dx, float dy) {
        this.position.add(dx, dy, 0);
        viewDirty = true;
    }

    public void translate(float dx, float dy, float dz) {
        this.position.add(dx, dy, dz);
        viewDirty = true;
    }

    /**
     * Set camera rotation (Z-axis, in degrees).
     */
    public void setRotation(float degrees) {
        this.rotation = degrees;
        viewDirty = true;
    }

    /**
     * Rotate camera by offset.
     */
    public void rotate(float degrees) {
        this.rotation += degrees;
        viewDirty = true;
    }

    /**
     * Set camera zoom level.
     * 1.0 = normal (1 pixel = 1 world unit)
     * 2.0 = 2x zoom in (objects appear larger)
     * 0.5 = 2x zoom out (objects appear smaller)
     */
    public void setZoom(float zoom) {
        if (zoom <= 0) {
            System.err.println("WARNING: Zoom must be positive, got: " + zoom);
            return;
        }
        this.zoom = zoom;
        viewDirty = true;
    }

    /**
     * Adjust zoom by factor (multiplicative).
     * zoomBy(2.0) doubles zoom, zoomBy(0.5) halves zoom.
     */
    public void zoomBy(float factor) {
        if (factor <= 0) {
            System.err.println("WARNING: Zoom factor must be positive, got: " + factor);
            return;
        }
        this.zoom *= factor;
        viewDirty = true;
    }

    // ======================================================================
    // STATE QUERIES
    // ======================================================================

    /**
     * Returns true if camera transform has changed since last update.
     */
    public boolean hasTransformChanged() {
        return viewDirty;
    }

    @Override
    public String toString() {
        return String.format("Camera[pos=(%.1f,%.1f,%.1f), rot=%.1f°, zoom=%.2f]",
                position.x, position.y, position.z, rotation, zoom);
    }
}