package com.pocket.rpg.scenes;

import lombok.Getter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SceneManager handles loading, unloading, and transitioning between scenes.
 * Supports lifecycle listeners for scene events.
 */
public class SceneManager {
    private final Map<String, Scene> scenes;
    private final List<SceneLifecycleListener> lifecycleListeners;
    @Getter
    private Scene currentScene;

    public SceneManager() {
        this.scenes = new HashMap<>();
        this.lifecycleListeners = new ArrayList<>();
    }

    public void registerScene(Scene scene) {
        scenes.put(scene.getName(), scene);
    }

    public void addLifecycleListener(SceneLifecycleListener listener) {
        if (!lifecycleListeners.contains(listener)) {
            lifecycleListeners.add(listener);
        }
    }

    public void removeLifecycleListener(SceneLifecycleListener listener) {
        lifecycleListeners.remove(listener);
    }

    public void loadScene(String sceneName) {
        Scene scene = scenes.get(sceneName);
        if (scene == null) {
            System.err.println("Scene not found: " + sceneName);
            return;
        }

        loadScene(scene);
    }

    public void loadScene(Scene scene) {
        if (currentScene != null) {
            currentScene.destroy();
            fireSceneUnloaded(currentScene);
        }

        currentScene = scene;
        currentScene.initialize();
        fireSceneLoaded(currentScene);

        System.out.println("Loaded scene: " + scene.getName());
    }

    public void update(float deltaTime) {
        if (currentScene != null) {
            currentScene.update(deltaTime);
        }
    }

    public void destroy() {
        if (currentScene != null) {
            currentScene.destroy();
            fireSceneUnloaded(currentScene);
        }
        scenes.clear();
        lifecycleListeners.clear();
    }

    private void fireSceneLoaded(Scene scene) {
        for (SceneLifecycleListener listener : lifecycleListeners) {
            listener.onSceneLoaded(scene);
        }
    }

    private void fireSceneUnloaded(Scene scene) {
        for (SceneLifecycleListener listener : lifecycleListeners) {
            listener.onSceneUnloaded(scene);
        }
    }
}