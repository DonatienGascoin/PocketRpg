package com.pocket.rpg.engine;

import lombok.Getter;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * Camera for 2D orthographic rendering.
 * Defines WHAT to show (view parameters), not HOW to render it (resolution).
 * CameraSystem handles the bridge between camera parameters and game resolution.
 */
public class Camera {

    // Clear color (RGBA, 0-1 range)
    private Vector4f clearColor = new Vector4f(0.1f, 0.1f, 0.1f, 1.0f);

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
            viewDirty = true;
        }
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
    // RENDERING SETTINGS
    // ======================================================================

    public Vector4f getClearColor() {
        return new Vector4f(clearColor);
    }

    public void setClearColor(float r, float g, float b, float a) {
        this.clearColor.set(r, g, b, a);
    }

    public void setClearColor(Vector4f color) {
        this.clearColor.set(color);
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