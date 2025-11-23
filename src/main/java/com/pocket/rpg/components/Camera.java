package com.pocket.rpg.components;

import com.pocket.rpg.rendering.CameraSystem;
import lombok.Getter;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * Camera component that manages projection parameters.
 * FIXED: Now uses fixed game resolution instead of viewport size
 * for pixel-perfect rendering.
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

    // FIX: Use game resolution instead of viewport
    private int gameWidth = 640;
    private int gameHeight = 480;

    // Matrices (cached)
    private Matrix4f projectionMatrix = new Matrix4f();
    private Matrix4f viewMatrix = new Matrix4f();

    private boolean projectionDirty = true;
    private boolean viewDirty = true;

    // Store transform VALUES instead of reference
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
    public void onStart() {
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
    public void onDestroy() {
        CameraSystem.unregisterCamera(this);
    }

    /**
     * FIX: Sets the game resolution (fixed internal resolution).
     * This should be called once during initialization with the game's
     * fixed resolution, NOT the window size.
     */
    public void setGameResolution(int width, int height) {
        if (this.gameWidth != width || this.gameHeight != height) {
            this.gameWidth = width;
            this.gameHeight = height;
            this.projectionDirty = true;
        }
    }

    /**
     * Gets the game width (fixed internal resolution).
     */
    public int getGameWidth() {
        return gameWidth;
    }

    /**
     * Gets the game height (fixed internal resolution).
     */
    public int getGameHeight() {
        return gameHeight;
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

    /**
     * FIX: Uses fixed game resolution instead of viewport size.
     */
    private void updateProjectionMatrix() {
        if (gameWidth <= 0 || gameHeight <= 0) {
            System.err.println("WARNING: Invalid game resolution: " + gameWidth + "x" + gameHeight);
            return;
        }

        float aspect = (float) gameWidth / (float) gameHeight;

        if (projectionType == ProjectionType.ORTHOGRAPHIC) {
            // FIX: Use game resolution for pixel-perfect rendering
            projectionMatrix.identity().ortho(0, gameWidth, gameHeight, 0, nearPlane, farPlane);
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
            this.viewDirty = true;
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

    /**
     * FIX: Screen to world conversion now uses game resolution.
     */
    public Vector3f screenToWorld(float screenX, float screenY, float depth) {
        if (projectionDirty) updateProjectionMatrix();
        if (viewDirty) updateViewMatrix();

        float ndcX = (2.0f * screenX) / gameWidth - 1.0f;
        float ndcY = 1.0f - (2.0f * screenY) / gameHeight;

        Matrix4f invProj = new Matrix4f(projectionMatrix).invert();
        Matrix4f invView = new Matrix4f(viewMatrix).invert();

        Vector4f clipCoords = new Vector4f(ndcX, ndcY, depth, 1.0f);
        Vector4f viewCoords = invProj.transform(clipCoords);
        Vector4f worldCoords = invView.transform(viewCoords);

        return new Vector3f(worldCoords.x, worldCoords.y, worldCoords.z);
    }

    @Override
    public String toString() {
        return String.format("Camera[type=%s, gameRes=%dx%d, clearColor=(%.2f,%.2f,%.2f,%.2f)]",
                projectionType, gameWidth, gameHeight,
                clearColor.x, clearColor.y, clearColor.z, clearColor.w);
    }
}