package com.pocket.rpg.scenes;

import com.pocket.rpg.config.RenderingConfig;
import com.pocket.rpg.core.camera.GameCamera;
import com.pocket.rpg.core.window.ViewportConfig;
import com.pocket.rpg.editor.scene.RuntimeSceneLoader;
import com.pocket.rpg.serialization.SceneData;
import lombok.Getter;
import lombok.NonNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SceneManager handles loading, unloading, and managing scenes.
 * Supports lifecycle listeners for scene events.
 * <p>
 * Can load scenes from:
 * <ul>
 *   <li>Registered Scene instances (for programmatic scenes)</li>
 *   <li>Scene files via RuntimeSceneLoader (for data-driven scenes)</li>
 * </ul>
 */
public class SceneManager {
    private final Map<String, Scene> scenes;
    private final List<SceneLifecycleListener> lifecycleListeners;

    @Getter
    private Scene currentScene;

    private final ViewportConfig viewportConfig;
    private final RenderingConfig renderingConfig;

    // File-based scene loading
    private RuntimeSceneLoader sceneLoader;
    private String scenesBasePath;

    public SceneManager(@NonNull ViewportConfig viewportConfig, @NonNull RenderingConfig renderingConfig) {
        this.scenes = new HashMap<>();
        this.lifecycleListeners = new ArrayList<>();
        this.viewportConfig = viewportConfig;
        this.renderingConfig = renderingConfig;
    }

    // ========================================================================
    // SCENE LOADER CONFIGURATION
    // ========================================================================

    /**
     * Configures file-based scene loading.
     *
     * @param loader   RuntimeSceneLoader for loading .scene files
     * @param basePath Base path for scene files (e.g., "gameData/scenes/")
     */
    public void setSceneLoader(RuntimeSceneLoader loader, String basePath) {
        this.sceneLoader = loader;
        this.scenesBasePath = basePath != null ? basePath : "";
    }

    // ========================================================================
    // SCENE REGISTRATION
    // ========================================================================

    /**
     * Registers a scene with the manager.
     * The scene can then be loaded by name.
     *
     * @param scene the scene to register
     */
    public void registerScene(Scene scene) {
        scenes.put(scene.getName(), scene);
    }

    // ========================================================================
    // LIFECYCLE LISTENERS
    // ========================================================================

    /**
     * Adds a lifecycle listener.
     *
     * @param listener the listener to add
     */
    public void addLifecycleListener(SceneLifecycleListener listener) {
        if (!lifecycleListeners.contains(listener)) {
            lifecycleListeners.add(listener);
        }
    }

    /**
     * Removes a lifecycle listener.
     *
     * @param listener the listener to remove
     */
    public void removeLifecycleListener(SceneLifecycleListener listener) {
        lifecycleListeners.remove(listener);
    }

    // ========================================================================
    // SCENE LOADING
    // ========================================================================

    /**
     * Loads a scene by name.
     * <p>
     * Lookup order:
     * <ol>
     *   <li>Registered scenes (via registerScene())</li>
     *   <li>Scene files (via sceneLoader if configured)</li>
     * </ol>
     *
     * @param sceneName name of the scene to load
     */
    public void loadScene(String sceneName) {
        // First check registered scenes
        Scene scene = scenes.get(sceneName);

        // Try file-based loading if not found and loader configured
        if (scene == null && sceneLoader != null) {
            scene = loadSceneFromFile(sceneName);
        }

        if (scene == null) {
            System.err.println("Scene not found: " + sceneName);
            return;
        }

        loadScene(scene);
    }

    /**
     * Loads a scene directly.
     * Unloads the current scene if one is active.
     *
     * @param scene the scene to load
     */
    public void loadScene(Scene scene) {
        if (currentScene != null) {
            currentScene.destroy();
            fireSceneUnloaded(currentScene);
        }

        currentScene = scene;

        // Initialize the scene (creates camera with defaults)
        currentScene.initialize(viewportConfig, renderingConfig);

        // Apply camera data if this is a RuntimeScene with stored camera config
        if (scene instanceof RuntimeScene runtimeScene) {
            applyCameraData(runtimeScene);
        }

        fireSceneLoaded(currentScene);

        System.out.println("Loaded scene: " + scene.getName());
    }

    /**
     * Attempts to load a scene from file.
     *
     * @param sceneName Name of the scene (without path/extension)
     * @return Loaded scene or null if not found
     */
    private Scene loadSceneFromFile(String sceneName) {
        String[] pathsToTry = {
                scenesBasePath + sceneName + ".scene",
                scenesBasePath + sceneName,
                sceneName + ".scene",
                sceneName
        };

        for (String path : pathsToTry) {
            try {
                RuntimeScene scene = sceneLoader.loadFromPath(path);
                if (scene != null) {
                    System.out.println("Loaded scene from file: " + path);
                    return scene;
                }
            } catch (Exception e) {
                // Try next path
            }
        }

        System.err.println("Could not find scene file for: " + sceneName);
        System.err.println("  Tried paths: " + String.join(", ", pathsToTry));
        return null;
    }

    /**
     * Applies stored camera data to the scene's camera.
     * Called after initialize() which creates the camera with defaults.
     */
    private void applyCameraData(RuntimeScene scene) {
        SceneData.CameraData cameraData = scene.getCameraData();
        if (cameraData == null || scene.getCamera() == null) {
            return;
        }

        // Apply position
        float[] pos = cameraData.getPosition();
        if (pos != null && pos.length >= 2) {
            scene.getCamera().setPosition(pos[0], pos[1]);
        }

        // Apply orthographic size (with guard for invalid values)
        float orthoSize = cameraData.getOrthographicSize();
        if (orthoSize > 0) {
            scene.getCamera().setOrthographicSize(orthoSize);
        }

        System.out.println("Applied camera data: orthoSize=" + orthoSize);
    }

    // ========================================================================
    // UPDATE
    // ========================================================================

    /**
     * Updates the current scene.
     * Called every frame.
     *
     * @param deltaTime time since last frame
     */
    public void update(float deltaTime) {
        if (currentScene != null) {
            currentScene.update(deltaTime);
        }
    }

    // ========================================================================
    // CLEANUP
    // ========================================================================

    /**
     * Destroys the scene manager and cleans up resources.
     */
    public void destroy() {
        if (currentScene != null) {
            currentScene.destroy();
            fireSceneUnloaded(currentScene);
        }
        GameCamera.setMainCamera(null);
        scenes.clear();
        lifecycleListeners.clear();
    }

    // ========================================================================
    // EVENTS
    // ========================================================================

    /**
     * Fires the scene loaded event to all listeners.
     *
     * @param scene the scene that was loaded
     */
    private void fireSceneLoaded(Scene scene) {
        for (SceneLifecycleListener listener : lifecycleListeners) {
            listener.onSceneLoaded(scene);
        }
    }

    /**
     * Fires the scene unloaded event to all listeners.
     *
     * @param scene the scene that was unloaded
     */
    private void fireSceneUnloaded(Scene scene) {
        for (SceneLifecycleListener listener : lifecycleListeners) {
            listener.onSceneUnloaded(scene);
        }
    }
}
