package com.pocket.rpg.components;

import com.pocket.rpg.rendering.CameraSystem;
import lombok.Getter;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * Camera component that manages projection parameters.
 * Now automatically registers with CameraSystem when enabled/disabled.
 * Matrices and viewport are managed by CameraSystem.
 * 
 * FIXED: Transform change detection now works correctly
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
    private float orthographicSize = 10.0f;
    private float nearPlane = -1.0f;
    private float farPlane = 1.0f;

    // Perspective settings
    @Getter
    private float fieldOfView = 60.0f;
    private float perspectiveNear = 0.1f;
    private float perspectiveFar = 1000.0f;

    // Viewport (for projection calculation)
    private int viewportWidth = 800;
    private int viewportHeight = 600;

    // Matrices (cached)
    private Matrix4f projectionMatrix = new Matrix4f();
    private Matrix4f viewMatrix = new Matrix4f();

    private boolean projectionDirty = true;
    private boolean viewDirty = true;

    // FIX: Store transform VALUES instead of reference
    private Vector3f lastPosition = new Vector3f();
    private Vector3f lastRotation = new Vector3f();
    private Vector3f lastScale = new Vector3f(1, 1, 1);

    public Camera() {
        this(ProjectionType.ORTHOGRAPHIC);
    }

    public Camera(ProjectionType projectionType) {
        this.projectionType = projectionType;
    }

    public Camera(float r, float g, float b, float a) {
        this.projectionType = ProjectionType.ORTHOGRAPHIC;
        this.clearColor.set(r, g, b, a);
    }

    @Override
    public void startInternal() {
        projectionDirty = true;
        viewDirty = true;
        
        // Initialize last transform values
        if (gameObject != null) {
            Transform transform = getTransform();
            lastPosition.set(transform.getPosition());
            lastRotation.set(transform.getRotation());
            lastScale.set(transform.getScale());
        }

        // Register with CameraSystem
        CameraSystem.registerCamera(this);
    }

    /**
     * FIX: Now properly detects transform changes by comparing values
     */
    @Override
    public void update(float deltaTime) {
        if (gameObject == null) return;

        Transform transform = getTransform();
        Vector3f currentPos = transform.getPosition();
        Vector3f currentRot = transform.getRotation();
        Vector3f currentScale = transform.getScale();

        // Check if any transform values changed
        boolean posChanged = !lastPosition.equals(currentPos, 0.0001f);
        boolean rotChanged = !lastRotation.equals(currentRot, 0.0001f);
        boolean scaleChanged = !lastScale.equals(currentScale, 0.0001f);

        if (posChanged || rotChanged || scaleChanged) {
            lastPosition.set(currentPos);
            lastRotation.set(currentRot);
            lastScale.set(currentScale);
            viewDirty = true;
        }
    }

    @Override
    public void setEnabled(boolean enabled) {
        boolean wasEnabled = this.enabled;
        super.setEnabled(enabled);

        if (enabled && !wasEnabled) {
            CameraSystem.registerCamera(this);
        } else if (!enabled && wasEnabled) {
            CameraSystem.unregisterCamera(this);
        }
    }

    @Override
    public void destroy() {
        CameraSystem.unregisterCamera(this);
    }

    public void setViewportSize(int width, int height) {
        if (this.viewportWidth != width || this.viewportHeight != height) {
            this.viewportWidth = width;
            this.viewportHeight = height;
            this.projectionDirty = true;
        }
    }

    private void updateViewMatrix() {
        if (gameObject == null) return;

        Transform transform = gameObject.getTransform();
        Vector3f pos = transform.getPosition();
        Vector3f rot = transform.getRotation();

        viewMatrix.identity();

        if (projectionType == ProjectionType.ORTHOGRAPHIC) {
            viewMatrix.translate(-pos.x, -pos.y, -pos.z);
            if (rot.z != 0) {
                viewMatrix.rotateZ((float) Math.toRadians(-rot.z));
            }
        } else {
            viewMatrix.rotateX((float) Math.toRadians(-rot.x));
            viewMatrix.rotateY((float) Math.toRadians(-rot.y));
            viewMatrix.rotateZ((float) Math.toRadians(-rot.z));
            viewMatrix.translate(-pos.x, -pos.y, -pos.z);
        }

        viewDirty = false;
    }

    private void updateProjectionMatrix() {
        if (viewportWidth <= 0 || viewportHeight <= 0) return;

        float aspect = (float) viewportWidth / (float) viewportHeight;

        if (projectionType == ProjectionType.ORTHOGRAPHIC) {
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

    // Getters/setters

    public ProjectionType getProjectionType() {
        return projectionType;
    }

    public void setProjectionType(ProjectionType type) {
        if (this.projectionType != type) {
            this.projectionType = type;
            this.projectionDirty = true;
            this.viewDirty = true; // View matrix formula depends on projection type
        }
    }

    public boolean isOrthographic() {
        return projectionType == ProjectionType.ORTHOGRAPHIC;
    }

    public boolean isPerspective() {
        return projectionType == ProjectionType.PERSPECTIVE;
    }

    public Vector4f getClearColor() {
        return new Vector4f(clearColor);
    }

    public void setClearColor(float r, float g, float b, float a) {
        this.clearColor.set(r, g, b, a);
    }

    public void setClearColor(Vector4f color) {
        this.clearColor.set(color);
    }

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

    public void markViewDirty() {
        this.viewDirty = true;
    }

    public void markProjectionDirty() {
        this.projectionDirty = true;
    }

    public boolean hasTransformChanged() {
        return viewDirty;
    }

    public boolean hasParametersChanged() {
        return projectionDirty;
    }

    public Vector3f screenToWorld(float screenX, float screenY, float depth) {
        if (projectionDirty) updateProjectionMatrix();
        if (viewDirty) updateViewMatrix();

        float ndcX = (2.0f * screenX) / viewportWidth - 1.0f;
        float ndcY = 1.0f - (2.0f * screenY) / viewportHeight;

        Matrix4f invProj = new Matrix4f(projectionMatrix).invert();
        Matrix4f invView = new Matrix4f(viewMatrix).invert();

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
