package com.pocket.rpg.scenes;

import com.pocket.rpg.rendering.Renderer;

import java.util.HashMap;
import java.util.Map;

/**
 * SceneManager handles loading, unloading, and transitioning between scenes.
 */
public class SceneManager {
    private final Map<String, Scene> scenes;
    private final Renderer renderer;

    private Scene currentScene;

    public SceneManager(Renderer renderer) {
        this.renderer = renderer;
        this.scenes = new HashMap<>();
    }

    /**
     * Registers a scene with the manager.
     */
    public void registerScene(Scene scene) {
        scenes.put(scene.getName(), scene);
    }

    /**
     * Loads a scene by name.
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
     * Loads a scene object.
     */
    public void loadScene(Scene scene) {
        // Unload current scene if exists
        if (currentScene != null) {
            currentScene.destroy();
        }

        // Load new scene
        currentScene = scene;
        currentScene.initialize(renderer);

        System.out.println("Loaded scene: " + scene.getName());
    }

    /**
     * Updates the current scene.
     */
    public void update(float deltaTime) {
        if (currentScene != null) {
            currentScene.update(deltaTime);
        }
    }

    /**
     * Renders the current scene.
     */
    public void render() {
        if (currentScene != null) {
            currentScene.render();
        }
    }

    /**
     * Destroys all scenes.
     */
    public void destroy() {
        if (currentScene != null) {
            currentScene.destroy();
        }
        scenes.clear();
    }
}