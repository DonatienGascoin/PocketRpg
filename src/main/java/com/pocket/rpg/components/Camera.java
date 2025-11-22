package com.pocket.rpg.components;

import com.pocket.rpg.scenes.Scene;
import lombok.Getter;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * Camera component that manages the view and projection matrices.
 * Also controls the clear color for rendering.
 * <p>
 * Now automatically registers/unregisters with Scene when enabled/disabled.
 */
public class Camera extends Component {

    public enum ProjectionType {
        ORTHOGRAPHIC,
        PERSPECTIVE
    }

    private ProjectionType projectionType = ProjectionType.ORTHOGRAPHIC;

    // Clear color (RGBA, 0-1 range)
    private Vector4f clearColor = new Vector4f(0.1f, 0.1f, 0.1f, 1.0f);

    // Orthographic settings
    @Getter
    private float orthographicSize = 10.0f; // Half-height in world units
    private float nearPlane = -1.0f;
    private float farPlane = 1.0f;

    // Perspective settings
    @Getter
    private float fieldOfView = 60.0f; // In degrees
    private float perspectiveNear = 0.1f;
    private float perspectiveFar = 1000.0f;

    // Viewport
    private int viewportWidth = 800;
    private int viewportHeight = 600;

    // Matrices
    private Matrix4f projectionMatrix = new Matrix4f();
    private Matrix4f viewMatrix = new Matrix4f();

    private boolean projectionDirty = true;
    private boolean viewDirty = true;

    private Transform lastTransform;

    /**
     * Creates an orthographic camera with default settings.
     */
    public Camera() {
        this(ProjectionType.ORTHOGRAPHIC);
    }

    /**
     * Creates a camera with the specified projection type.
     */
    public Camera(ProjectionType projectionType) {
        this.projectionType = projectionType;
    }

    /**
     * Creates an orthographic camera with custom clear color.
     */
    public Camera(float r, float g, float b, float a) {
        this.projectionType = ProjectionType.ORTHOGRAPHIC;
        this.clearColor.set(r, g, b, a);
    }

    @Override
    public void startInternal() {
        projectionDirty = true;
        viewDirty = true;
        lastTransform = getTransform();

        // Register with scene when started
        if (gameObject != null && gameObject.getScene() != null) {
            gameObject.getScene().registerCamera(this);
        }
    }

    @Override
    public void update(float deltaTime) {
        // Mark view as dirty if transform changed
        if (!lastTransform.equals(getTransform())) {
            lastTransform = getTransform();
            viewDirty = true;
        }
    }

    @Override
    public void setEnabled(boolean enabled) {
        boolean wasEnabled = this.enabled;
        super.setEnabled(enabled);

        // Notify scene when camera is enabled/disabled
        if (gameObject != null && gameObject.getScene() != null) {
            Scene scene = gameObject.getScene();

            if (enabled && !wasEnabled) {
                // Camera was enabled - register with scene
                scene.registerCamera(this);
            } else if (!enabled && wasEnabled) {
                // Camera was disabled - unregister from scene
                scene.unregisterCamera(this);
            }
        }
    }

    @Override
    public void destroy() {
        // Unregister from scene when destroyed
        if (gameObject != null && gameObject.getScene() != null) {
            gameObject.getScene().unregisterCamera(this);
        }
    }

    /**
     * Sets the viewport size. Should be called when window is resized.
     */
    public void setViewportSize(int width, int height) {
        if (this.viewportWidth != width || this.viewportHeight != height) {
            this.viewportWidth = width;
            this.viewportHeight = height;
            this.projectionDirty = true;
        }
    }

    /**
     * Updates the view matrix based on the camera's transform.
     */
    private void updateViewMatrix() {
        if (gameObject == null) return;

        Transform transform = gameObject.getTransform();
        Vector3f pos = transform.getPosition();
        Vector3f rot = transform.getRotation();

        viewMatrix.identity();

        if (projectionType == ProjectionType.ORTHOGRAPHIC) {
            // For 2D orthographic, just translate (camera moves opposite to world)
            viewMatrix.translate(-pos.x, -pos.y, -pos.z);

            // Apply rotation if needed (around Z for 2D)
            if (rot.z != 0) {
                viewMatrix.rotateZ((float) Math.toRadians(-rot.z));
            }
        } else {
            // Perspective camera - full 3D transform
            viewMatrix.rotateX((float) Math.toRadians(-rot.x));
            viewMatrix.rotateY((float) Math.toRadians(-rot.y));
            viewMatrix.rotateZ((float) Math.toRadians(-rot.z));
            viewMatrix.translate(-pos.x, -pos.y, -pos.z);
        }

        viewDirty = false;
    }

    /**
     * Updates the projection matrix based on camera settings.
     */
    private void updateProjectionMatrix() {
        if (viewportWidth <= 0 || viewportHeight <= 0) return;

        float aspect = (float) viewportWidth / (float) viewportHeight;

        if (projectionType == ProjectionType.ORTHOGRAPHIC) {
            // For screen-space coordinates (0,0 at top-left)
            projectionMatrix.identity().ortho(0, viewportWidth, viewportHeight, 0, nearPlane, farPlane);
        } else {
            projectionMatrix.identity().perspective(
                    (float) Math.toRadians(fieldOfView),
                    aspect,
                    perspectiveNear,
                    perspectiveFar
            );
        }

        projectionDirty = false;
    }

    // Projection Type

    public ProjectionType getProjectionType() {
        return projectionType;
    }

    public void setProjectionType(ProjectionType type) {
        if (this.projectionType != type) {
            this.projectionType = type;
            this.projectionDirty = true;
        }
    }

    public boolean isOrthographic() {
        return projectionType == ProjectionType.ORTHOGRAPHIC;
    }

    public boolean isPerspective() {
        return projectionType == ProjectionType.PERSPECTIVE;
    }

    // Clear Color

    public Vector4f getClearColor() {
        return new Vector4f(clearColor);
    }

    public void setClearColor(float r, float g, float b, float a) {
        this.clearColor.set(r, g, b, a);
    }

    public void setClearColor(Vector4f color) {
        this.clearColor.set(color);
    }

    // Orthographic Settings

    public void setOrthographicSize(float size) {
        if (this.orthographicSize != size) {
            this.orthographicSize = size;
            this.projectionDirty = true;
        }
    }

    public void setOrthographicPlanes(float near, float far) {
        this.nearPlane = near;
        this.farPlane = far;
        this.projectionDirty = true;
    }

    // Perspective Settings

    public void setFieldOfView(float fov) {
        if (this.fieldOfView != fov) {
            this.fieldOfView = fov;
            if (projectionType == ProjectionType.PERSPECTIVE) {
                this.projectionDirty = true;
            }
        }
    }

    public void setPerspectivePlanes(float near, float far) {
        this.perspectiveNear = near;
        this.perspectiveFar = far;
        if (projectionType == ProjectionType.PERSPECTIVE) {
            this.projectionDirty = true;
        }
    }

    // Matrices

    public Matrix4f getProjectionMatrix() {
        if (projectionDirty) {
            updateProjectionMatrix();
        }
        return new Matrix4f(projectionMatrix);
    }

    public Matrix4f getViewMatrix() {
        if (viewDirty) {
            updateViewMatrix();
        }
        return new Matrix4f(viewMatrix);
    }

    /**
     * Marks the view matrix as dirty, forcing an update next frame.
     */
    public void markViewDirty() {
        this.viewDirty = true;
    }

    /**
     * Converts screen coordinates to world coordinates.
     */
    public Vector3f screenToWorld(float screenX, float screenY, float depth) {
        if (projectionDirty) updateProjectionMatrix();
        if (viewDirty) updateViewMatrix();

        // Normalize screen coordinates to NDC (-1 to 1)
        float ndcX = (2.0f * screenX) / viewportWidth - 1.0f;
        float ndcY = 1.0f - (2.0f * screenY) / viewportHeight;

        // Create inverse matrices
        Matrix4f invProj = new Matrix4f(projectionMatrix).invert();
        Matrix4f invView = new Matrix4f(viewMatrix).invert();

        // Transform from NDC to world
        Vector4f clipCoords = new Vector4f(ndcX, ndcY, depth, 1.0f);
        Vector4f viewCoords = invProj.transform(clipCoords);
        Vector4f worldCoords = invView.transform(viewCoords);

        return new Vector3f(worldCoords.x, worldCoords.y, worldCoords.z);
    }

    @Override
    public String toString() {
        return String.format("Camera[type=%s, clearColor=(%.2f,%.2f,%.2f,%.2f)]",
                projectionType, clearColor.x, clearColor.y, clearColor.z, clearColor.w);
    }
}