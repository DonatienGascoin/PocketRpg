package com.pocket.rpg.scenes;

import com.pocket.rpg.config.RenderingConfig;
import com.pocket.rpg.core.camera.GameCamera;
import com.pocket.rpg.core.window.ViewportConfig;
import lombok.Getter;
import lombok.NonNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SceneManager handles loading, unloading, and managing scenes.
 * Supports lifecycle listeners for scene events.
 */
public class SceneManager {
    private final Map<String, Scene> scenes;
    private final List<SceneLifecycleListener> lifecycleListeners;
    @Getter
    private Scene currentScene;

    private final ViewportConfig viewportConfig;
    private final RenderingConfig renderingConfig;

    public SceneManager(@NonNull ViewportConfig viewportConfig, @NonNull RenderingConfig renderingConfig) {
        this.scenes = new HashMap<>();
        this.lifecycleListeners = new ArrayList<>();
        this.viewportConfig = viewportConfig;
        this.renderingConfig = renderingConfig;
    }

    /**
     * Registers a scene with the manager.
     * The scene can then be loaded by name.
     *
     * @param scene the scene to register
     */
    public void registerScene(Scene scene) {
        scenes.put(scene.getName(), scene);
    }

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

    /**
     * Loads a scene by name.
     * Unloads the current scene if one is active.
     *
     * @param sceneName name of the scene to load
     */
    public void loadScene(String sceneName) {
        Scene scene = scenes.get(sceneName);
        if (scene == null) {
            System.err.println("Scene not found: " + sceneName);
            return;
        }

        loadScene(scene);
    }

    /**
     * Loads a scene.
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
        currentScene.initialize(viewportConfig, renderingConfig);
        fireSceneLoaded(currentScene);

        System.out.println("Loaded scene: " + scene.getName());
    }

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