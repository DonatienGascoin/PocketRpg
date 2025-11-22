package com.pocket.rpg.rendering;

import com.pocket.rpg.components.Camera;
import com.pocket.rpg.components.Transform;
import com.pocket.rpg.utils.DirtyReference;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Camera management system that handles viewport, projection, and view matrices.
 * Cameras register/unregister themselves automatically when enabled/disabled.
 * 
 * FIXED: Proper singleton pattern with thread safety
 */
public class CameraSystem {

    private static volatile CameraSystem instance = null;
    private static final Object LOCK = new Object();

    // FIX: Use thread-safe collection
    private final List<Camera> registeredCameras = new CopyOnWriteArrayList<>();
    private Camera activeCamera = null;

    // Viewport dimensions
    private int viewportWidth = 800;
    private int viewportHeight = 600;

    // Matrix dirty references for efficient updates
    private DirtyReference<Matrix4f> projectionMatrixRef;
    private DirtyReference<Matrix4f> viewMatrixRef;

    // Default clear color
    private static final Vector4f DEFAULT_CLEAR_COLOR = new Vector4f(0.1f, 0.1f, 0.15f, 1.0f);

    // Fallback camera for when none exists
    private Camera fallbackCamera = null;

    /**
     * FIX: Private constructor for proper singleton pattern
     */
    private CameraSystem(int width, int height) {
        viewportWidth = width;
        viewportHeight = height;

        // Initialize dirty references with no-op consumers
        projectionMatrixRef = new DirtyReference<>(new Matrix4f(), m -> {});
        viewMatrixRef = new DirtyReference<>(new Matrix4f(), m -> {});

        registeredCameras.clear();
        activeCamera = null;
    }

    /**
     * Initializes the camera system with viewport size.
     * Must be called before using any cameras.
     * Thread-safe initialization.
     */
    public static void initialize(int width, int height) {
        if (instance != null) {
            System.err.println("WARNING: CameraSystem already initialized. Call clear() first to reinitialize.");
            return;
        }

        synchronized (LOCK) {
            if (instance == null) {
                instance = new CameraSystem(width, height);
            }
        }
    }

    /**
     * Gets the singleton instance.
     * Throws exception if not initialized.
     */
    public static CameraSystem getInstance() {
        if (instance == null) {
            throw new IllegalStateException("CameraSystem not initialized. Call initialize() first.");
        }
        return instance;
    }

    /**
     * Registers a camera with the system.
     * The first camera registered becomes active automatically.
     * Thread-safe.
     */
    public static void registerCamera(Camera camera) {
        if (camera == null) {
            System.err.println("WARNING: Attempted to register null camera");
            return;
        }

        CameraSystem sys = getInstance();
        
        if (!sys.registeredCameras.contains(camera)) {
            sys.registeredCameras.add(camera);

            // First camera becomes active
            synchronized (LOCK) {
                if (sys.activeCamera == null) {
                    sys.activeCamera = camera;
                    System.out.println("Camera registered and set as active: " + camera);
                }
            }
        }
    }

    /**
     * Unregisters a camera from the system.
     * If the active camera is unregistered, finds another camera as fallback.
     * Thread-safe.
     */
    public static void unregisterCamera(Camera camera) {
        CameraSystem sys = getInstance();
        sys.registeredCameras.remove(camera);

        synchronized (LOCK) {
            if (sys.activeCamera == camera) {
                sys.activeCamera = null;

                // Find fallback camera
                for (Camera cam : sys.registeredCameras) {
                    if (cam.isEnabled()) {
                        sys.activeCamera = cam;
                        System.out.println("Active camera changed to: " + cam);
                        break;
                    }
                }

                // Warn if no cameras left
                if (sys.activeCamera == null && !sys.registeredCameras.isEmpty()) {
                    System.err.println("WARNING: No enabled cameras available");
                } else if (sys.activeCamera == null && sys.registeredCameras.isEmpty()) {
                    System.err.println("WARNING: No cameras registered");
                }
            }
        }
    }

    /**
     * Sets the active camera.
     * Thread-safe.
     */
    public static void setActiveCamera(Camera camera) {
        CameraSystem sys = getInstance();
        
        if (sys.registeredCameras.contains(camera)) {
            synchronized (LOCK) {
                sys.activeCamera = camera;
                System.out.println("Active camera set to: " + camera);
            }
        } else {
            System.err.println("WARNING: Cannot set active camera - camera not registered");
        }
    }

    /**
     * Gets the currently active camera.
     * Creates fallback camera if none exists.
     * Thread-safe.
     */
    public Camera getActiveCamera() {
        synchronized (LOCK) {
            // Validate active camera is still enabled
            if (activeCamera != null && !activeCamera.isEnabled()) {
                activeCamera = null;

                // Find replacement
                for (Camera cam : registeredCameras) {
                    if (cam.isEnabled()) {
                        activeCamera = cam;
                        System.out.println("Active camera auto-switched to: " + cam);
                        break;
                    }
                }
            }

            // FIX: Create fallback camera if none exists
            if (activeCamera == null) {
                if (fallbackCamera == null) {
                    System.err.println("WARNING: No active camera, creating fallback");
                    fallbackCamera = createFallbackCamera();
                }
                return fallbackCamera;
            }

            return activeCamera;
        }
    }

    /**
     * Creates a fallback camera when no cameras exist.
     */
    private Camera createFallbackCamera() {
        Camera camera = new Camera(Camera.ProjectionType.ORTHOGRAPHIC);
        camera.setClearColor(0.1f, 0.1f, 0.15f, 1.0f);
        camera.setViewportSize(viewportWidth, viewportHeight);
        return camera;
    }

    /**
     * Updates viewport size. Should be called when window is resized.
     * Thread-safe.
     */
    public static void setViewportSize(int width, int height) {
        if (width <= 0 || height <= 0) {
            System.err.println("WARNING: Invalid viewport size: " + width + "x" + height);
            return;
        }

        CameraSystem sys = getInstance();
        
        synchronized (LOCK) {
            if (sys.viewportWidth != width || sys.viewportHeight != height) {
                sys.viewportWidth = width;
                sys.viewportHeight = height;

                // Update all camera viewports
                for (Camera camera : sys.registeredCameras) {
                    camera.setViewportSize(width, height);
                }

                // Update fallback camera if it exists
                if (sys.fallbackCamera != null) {
                    sys.fallbackCamera.setViewportSize(width, height);
                }

                System.out.println("Viewport size updated: " + width + "x" + height);
            }
        }
    }

    /**
     * Updates camera matrices. Should be called each frame.
     */
    public void updateFrame() {
        Camera camera = getActiveCamera();
        if (camera == null) return;

        // Matrices are updated lazily when requested
        // Just ensure camera update is called
        if (camera.hasTransformChanged() || camera.hasParametersChanged()) {
            camera.markViewDirty();
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
        return getInstance().viewportWidth;
    }

    /**
     * Gets the viewport height.
     */
    public static int getViewportHeight() {
        return getInstance().viewportHeight;
    }

    /**
     * Clears all registered cameras and resets the system.
     * Thread-safe.
     */
    public static void clear() {
        if (instance == null) {
            return;
        }

        CameraSystem sys = getInstance();
        
        synchronized (LOCK) {
            sys.registeredCameras.clear();
            sys.activeCamera = null;
            sys.fallbackCamera = null;
            System.out.println("CameraSystem cleared");
        }
    }

    /**
     * Completely destroys the singleton instance.
     * Call this before reinitializing with different parameters.
     */
    public static void destroy() {
        synchronized (LOCK) {
            if (instance != null) {
                instance.registeredCameras.clear();
                instance.activeCamera = null;
                instance.fallbackCamera = null;
                instance = null;
                System.out.println("CameraSystem destroyed");
            }
        }
    }

    /**
     * Gets the number of registered cameras.
     */
    public static int getCameraCount() {
        return getInstance().registeredCameras.size();
    }

    /**
     * Checks if the system is initialized.
     */
    public static boolean isInitialized() {
        return instance != null;
    }
}
