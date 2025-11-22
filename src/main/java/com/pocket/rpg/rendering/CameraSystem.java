package com.pocket.rpg.rendering;

import com.pocket.rpg.components.Camera;
import com.pocket.rpg.utils.DirtyReference;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Camera management system that handles viewport, projection, and view matrices.
 * FIXED: Now properly separates game resolution from window viewport.
 */
public class CameraSystem {

    private static volatile CameraSystem instance = null;
    private static final Object LOCK = new Object();

    private final List<Camera> registeredCameras = new CopyOnWriteArrayList<>();
    private Camera activeCamera = null;

    // FIX: Separate game resolution from viewport
    private int gameWidth = 640;   // Fixed internal game resolution
    private int gameHeight = 480;
    private int viewportWidth = 800;  // Physical window size
    private int viewportHeight = 600;

    // Matrix dirty references for efficient updates
    private DirtyReference<Matrix4f> projectionMatrixRef;
    private DirtyReference<Matrix4f> viewMatrixRef;

    // Default clear color
    private static final Vector4f DEFAULT_CLEAR_COLOR = new Vector4f(0.1f, 0.1f, 0.15f, 1.0f);

    // Fallback camera for when none exists
    private Camera fallbackCamera = null;

    /**
     * FIX: Private constructor now takes game resolution
     */
    private CameraSystem(int gameWidth, int gameHeight) {
        this.gameWidth = gameWidth;
        this.gameHeight = gameHeight;
        this.viewportWidth = gameWidth;
        this.viewportHeight = gameHeight;

        projectionMatrixRef = new DirtyReference<>(new Matrix4f(), m -> {});
        viewMatrixRef = new DirtyReference<>(new Matrix4f(), m -> {});

        registeredCameras.clear();
        activeCamera = null;
    }

    /**
     * FIX: Initializes with game resolution (fixed internal resolution).
     * Viewport is set separately via setViewportSize.
     */
    public static void initialize(int gameWidth, int gameHeight) {
        if (instance != null) {
            System.err.println("WARNING: CameraSystem already initialized. Call destroy() first to reinitialize.");
            return;
        }

        synchronized (LOCK) {
            if (instance == null) {
                instance = new CameraSystem(gameWidth, gameHeight);
                System.out.println("CameraSystem initialized with game resolution: " + gameWidth + "x" + gameHeight);
            }
        }
    }

    /**
     * Gets the singleton instance.
     */
    public static CameraSystem getInstance() {
        if (instance == null) {
            throw new IllegalStateException("CameraSystem not initialized. Call initialize() first.");
        }
        return instance;
    }

    /**
     * Registers a camera with the system.
     */
    public static void registerCamera(Camera camera) {
        if (camera == null) {
            System.err.println("WARNING: Attempted to register null camera");
            return;
        }

        CameraSystem sys = getInstance();

        if (!sys.registeredCameras.contains(camera)) {
            // FIX: Set game resolution on camera
            camera.setGameResolution(sys.gameWidth, sys.gameHeight);

            sys.registeredCameras.add(camera);

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
     */
    public static void unregisterCamera(Camera camera) {
        CameraSystem sys = getInstance();
        sys.registeredCameras.remove(camera);

        synchronized (LOCK) {
            if (sys.activeCamera == camera) {
                sys.activeCamera = null;

                for (Camera cam : sys.registeredCameras) {
                    if (cam.isEnabled()) {
                        sys.activeCamera = cam;
                        System.out.println("Active camera changed to: " + cam);
                        break;
                    }
                }

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
     */
    public Camera getActiveCamera() {
        synchronized (LOCK) {
            if (activeCamera != null && !activeCamera.isEnabled()) {
                activeCamera = null;

                for (Camera cam : registeredCameras) {
                    if (cam.isEnabled()) {
                        activeCamera = cam;
                        System.out.println("Active camera auto-switched to: " + cam);
                        break;
                    }
                }
            }

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
        camera.setGameResolution(gameWidth, gameHeight);
        return camera;
    }

    /**
     * FIX: Updates viewport size (physical window size).
     * This does NOT change game resolution.
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

                // Note: We do NOT update camera resolution here
                // Cameras always use fixed game resolution

                System.out.println("Viewport size updated: " + width + "x" + height +
                        " (game resolution remains: " + sys.gameWidth + "x" + sys.gameHeight + ")");
            }
        }
    }

    /**
     * Updates camera matrices.
     */
    public void updateFrame() {
        Camera camera = getActiveCamera();
        if (camera == null) return;

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
     * FIX: Gets the fixed game width.
     */
    public static int getGameWidth() {
        return getInstance().gameWidth;
    }

    /**
     * FIX: Gets the fixed game height.
     */
    public static int getGameHeight() {
        return getInstance().gameHeight;
    }

    /**
     * Gets the viewport width (physical window size).
     */
    public static int getViewportWidth() {
        return getInstance().viewportWidth;
    }

    /**
     * Gets the viewport height (physical window size).
     */
    public static int getViewportHeight() {
        return getInstance().viewportHeight;
    }

    /**
     * Clears all registered cameras and resets the system.
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