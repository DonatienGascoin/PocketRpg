package com.pocket.rpg.rendering;

import com.pocket.rpg.components.Camera;
import com.pocket.rpg.components.Transform;
import com.pocket.rpg.utils.DirtyReference;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Static camera management system that handles viewport, projection, and view matrices.
 * Cameras register/unregister themselves automatically when enabled/disabled.
 * Uses DirtyReference for efficient matrix updates.
 */
public class CameraSystem {

    private static CameraSystem instance = null;

    // Static camera registry
    private final List<Camera> registeredCameras = new ArrayList<>();
    private Camera activeCamera = null;

    // Viewport dimensions
    private int viewportWidth = 800;
    private int viewportHeight = 600;

    // Matrix dirty references for efficient updates
    private DirtyReference<Matrix4f> projectionMatrixRef;
    private DirtyReference<Matrix4f> viewMatrixRef;

    // Default clear color
    private static final Vector4f DEFAULT_CLEAR_COLOR = new Vector4f(0.1f, 0.1f, 0.15f, 1.0f);

    /**
     * Initializes the camera system with viewport size.
     * Must be called before using any cameras.
     */
    public CameraSystem(int width, int height) {
        if (instance != null) {
            return;
        }

        viewportWidth = width;
        viewportHeight = height;

        // Initialize dirty references with no-op consumers (consumers set per-camera later)
        projectionMatrixRef = new DirtyReference<>(new Matrix4f(), m -> {
        });
        viewMatrixRef = new DirtyReference<>(new Matrix4f(), m -> {
        });

        registeredCameras.clear();
        activeCamera = null;

        instance = this;
    }

    /**
     * Registers a camera with the system.
     * The first camera registered becomes active automatically.
     */
    public static void registerCamera(Camera camera) {

        if (camera != null && !instance.registeredCameras.contains(camera)) {
            instance.registeredCameras.add(camera);

            // First camera becomes active
            if (instance.activeCamera == null) {
                instance.activeCamera = camera;
            }
        }
    }

    /**
     * Unregisters a camera from the system.
     * If the active camera is unregistered, finds another camera as fallback.
     */
    public static void unregisterCamera(Camera camera) {
        instance.registeredCameras.remove(camera);

        if (instance.activeCamera == camera) {
            instance.activeCamera = null;

            // Find fallback camera
            for (Camera cam : instance.registeredCameras) {
                if (cam.isEnabled()) {
                    instance.activeCamera = cam;
                    break;
                }
            }
        }
    }

    /**
     * Sets the active camera.
     */
    public static void setActiveCamera(Camera camera) {
        if (instance.registeredCameras.contains(camera)) {
            instance.activeCamera = camera;
        }
    }

    /**
     * Gets the currently active camera.
     */
    public Camera getActiveCamera() {
        // Validate active camera is still enabled
        if (activeCamera != null && !activeCamera.isEnabled()) {
            activeCamera = null;

            // Find replacement
            for (Camera cam : registeredCameras) {
                if (cam.isEnabled()) {
                    activeCamera = cam;
                    break;
                }
            }
        }

        return activeCamera;
    }

    /**
     * Updates viewport size. Should be called when window is resized.
     */
    public static void setViewportSize(int width, int height) {
        if (instance.viewportWidth != width || instance.viewportHeight != height) {
            instance.viewportWidth = width;
            instance.viewportHeight = height;

            // Mark all camera projections as dirty
            for (Camera camera : instance.registeredCameras) {
                camera.setViewportSize(width, height);
            }
        }
    }

    /**
     * Updates camera matrices. Should be called each frame.
     */
    public void updateFrame() {
        Camera camera = getActiveCamera();
        if (camera == null) return;

        // Check if camera transform or parameters changed
        if (hasTransformChanged(camera) || hasParametersChanged(camera)) {
            updateProjectionMatrix(camera);
            updateViewMatrix(camera);
        }
    }

    /**
     * Gets the current projection matrix.
     */
    public Matrix4f getProjectionMatrix() {
        Camera camera = getActiveCamera();
        if (camera != null) {
            return camera.getProjectionMatrix();
        }

        // Fallback to identity
        return new Matrix4f().identity();
    }

    /**
     * Gets the current view matrix.
     */
    public Matrix4f getViewMatrix() {
        Camera camera = getActiveCamera();
        if (camera != null) {
            return camera.getViewMatrix();
        }

        // Fallback to identity
        return new Matrix4f().identity();
    }

    /**
     * Gets the current clear color.
     */
    public Vector4f getClearColor() {
        Camera camera = getActiveCamera();
        if (camera != null) {
            return camera.getClearColor();
        }

        return new Vector4f(DEFAULT_CLEAR_COLOR);
    }

    /**
     * Gets the viewport width.
     */
    public static int getViewportWidth() {
        return instance.viewportWidth;
    }

    /**
     * Gets the viewport height.
     */
    public static int getViewportHeight() {
        return instance.viewportHeight;
    }

    /**
     * Checks if camera's transform has changed.
     */
    private static boolean hasTransformChanged(Camera camera) {
        if (camera.getGameObject() == null) return false;

        Transform transform = camera.getGameObject().getTransform();
        // Camera component tracks this internally via update()
        return false; // Handled by Camera.markViewDirty()
    }

    /**
     * Checks if camera's parameters (FOV, ortho size, etc.) have changed.
     */
    private static boolean hasParametersChanged(Camera camera) {
        // Camera component tracks this internally
        return false; // Handled by Camera's dirty flags
    }

    /**
     * Updates the projection matrix for the camera.
     */
    private static void updateProjectionMatrix(Camera camera) {
        // Camera handles this internally
        camera.getProjectionMatrix();
    }

    /**
     * Updates the view matrix for the camera.
     */
    private static void updateViewMatrix(Camera camera) {
        // Camera handles this internally
        camera.getViewMatrix();
    }

    /**
     * Clears all registered cameras.
     */
    public static void clear() {
        instance.registeredCameras.clear();
        instance.activeCamera = null;
    }

    /**
     * Gets the number of registered cameras.
     */
    public static int getCameraCount() {
        return instance.registeredCameras.size();
    }
}