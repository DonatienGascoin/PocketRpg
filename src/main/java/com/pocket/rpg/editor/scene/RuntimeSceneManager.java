package com.pocket.rpg.editor.scene;

import com.pocket.rpg.config.RenderingConfig;
import com.pocket.rpg.core.window.ViewportConfig;
import com.pocket.rpg.scenes.RuntimeScene;
import com.pocket.rpg.scenes.Scene;
import com.pocket.rpg.scenes.SceneManager;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Extended SceneManager that supports dynamic scene loading during play mode.
 * <p>
 * When a scene transition requests a scene by name, this manager will:
 * 1. Check if the scene is already registered
 * 2. If not, attempt to load it via the RuntimeSceneLoader
 * 3. Register and switch to the loaded scene
 */
public class RuntimeSceneManager extends SceneManager {

    private final RuntimeSceneLoader sceneLoader;
    private final String scenesBasePath;

    // Cache of loaded scenes by name
    private final Map<String, Scene> loadedScenes = new HashMap<>();

    // Optional scene factory for custom loading logic
    private Function<String, RuntimeScene> sceneFactory;

    /**
     * Creates a RuntimeSceneManager.
     *
     * @param viewportConfig  Viewport configuration
     * @param renderingConfig Rendering configuration
     * @param sceneLoader     Loader for converting scene files to RuntimeScene
     * @param scenesBasePath  Base path for scene files (e.g., "scenes/")
     */
    public RuntimeSceneManager(ViewportConfig viewportConfig,
                               RenderingConfig renderingConfig,
                               RuntimeSceneLoader sceneLoader,
                               String scenesBasePath) {
        super(viewportConfig, renderingConfig);
        this.sceneLoader = sceneLoader;
        this.scenesBasePath = scenesBasePath != null ? scenesBasePath : "scenes/";
    }

    /**
     * Sets a custom scene factory for loading scenes.
     * If set, this takes precedence over the default file-based loading.
     *
     * @param factory Function that takes a scene name and returns a RuntimeScene
     */
    public void setSceneFactory(Function<String, RuntimeScene> factory) {
        this.sceneFactory = factory;
    }

    /**
     * Loads a scene by name.
     * <p>
     * If the scene is already loaded/registered, switches to it.
     * Otherwise, attempts to load from file and register it.
     *
     * @param sceneName Name of the scene to load
     */
    @Override
    public void loadScene(String sceneName) {
        // Check if already in cache
        if (loadedScenes.containsKey(sceneName)) {
            Scene cached = loadedScenes.get(sceneName);
            super.loadScene(cached);
            System.out.println("Switched to cached scene: " + sceneName);
            return;
        }

        // Try to load the scene
        RuntimeScene scene = loadSceneByName(sceneName);
        if (scene != null) {
            loadedScenes.put(sceneName, scene);
            super.loadScene(scene);
            System.out.println("Loaded and switched to scene: " + sceneName);
        } else {
            System.err.println("Failed to load scene: " + sceneName);
        }
    }

    /**
     * Loads a scene by name using factory or file system.
     */
    private RuntimeScene loadSceneByName(String sceneName) {
        // Try custom factory first
        if (sceneFactory != null) {
            try {
                return sceneFactory.apply(sceneName);
            } catch (Exception e) {
                System.err.println("Scene factory failed for '" + sceneName + "': " + e.getMessage());
                // Fall through to file-based loading
            }
        }

        // Try file-based loading
        return loadSceneFromFile(sceneName);
    }

    /**
     * Attempts to load a scene from a .scene file.
     */
    private RuntimeScene loadSceneFromFile(String sceneName) {
        // Try multiple path patterns
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
     * Preloads a scene without switching to it.
     * Useful for reducing transition load times.
     *
     * @param sceneName Name of the scene to preload
     * @return true if preload succeeded
     */
    public boolean preloadScene(String sceneName) {
        if (loadedScenes.containsKey(sceneName)) {
            return true; // Already loaded
        }

        RuntimeScene scene = loadSceneByName(sceneName);
        if (scene != null) {
            loadedScenes.put(sceneName, scene);
            System.out.println("Preloaded scene: " + sceneName);
            return true;
        }
        return false;
    }

    /**
     * Unloads a scene from cache (freeing memory).
     * Does not unload the current scene.
     *
     * @param sceneName Name of the scene to unload
     */
    public void unloadScene(String sceneName) {
        Scene current = getCurrentScene();
        Scene toUnload = loadedScenes.get(sceneName);

        if (toUnload != null && toUnload != current) {
            toUnload.destroy();
            loadedScenes.remove(sceneName);
            System.out.println("Unloaded scene: " + sceneName);
        }
    }

    /**
     * Clears all cached scenes except the current one.
     */
    public void clearSceneCache() {
        Scene current = getCurrentScene();

        for (Map.Entry<String, Scene> entry : loadedScenes.entrySet()) {
            if (entry.getValue() != current) {
                entry.getValue().destroy();
            }
        }

        loadedScenes.clear();

        // Re-add current if it exists
        if (current != null) {
            loadedScenes.put(current.getName(), current);
        }

        System.out.println("Cleared scene cache");
    }

    @Override
    public void destroy() {
        // Destroy all loaded scenes
        for (Scene scene : loadedScenes.values()) {
            scene.destroy();
        }
        loadedScenes.clear();

        super.destroy();
    }
}