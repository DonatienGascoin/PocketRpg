package com.pocket.rpg.scenes;

import com.pocket.rpg.config.GameConfig;
import com.pocket.rpg.config.RenderingConfig;
import com.pocket.rpg.core.window.ViewportConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Mock implementation of SceneManager for testing.
 * Lightweight, deterministic, and doesn't require real scenes.
 */
public class MockSceneManager extends SceneManager {

    private final Map<String, Scene> registeredScenes = new HashMap<>();
    private final List<String> loadedScenes = new ArrayList<>();
    private final List<String> loadedSpawnIds = new ArrayList<>();
    private Scene currentScene;

    public MockSceneManager() {
        super(new ViewportConfig(GameConfig.builder().gameWidth(640).gameHeight(480).windowWidth(1280).windowHeight(960).build()), RenderingConfig.builder().defaultOrthographicSize(7.5f).build());
    }@Override
    public void registerScene(Scene scene) {
        registeredScenes.put(scene.getName(), scene);
    }

    @Override
    public void loadScene(String sceneName) {
        loadedScenes.add(sceneName);
        loadedSpawnIds.add(null);

        // Create or retrieve mock scene
        Scene scene = registeredScenes.get(sceneName);
        if (scene == null) {
            scene = new MockScene(sceneName);
            registeredScenes.put(sceneName, scene);
        }

        currentScene = scene;
    }

    @Override
    public void loadScene(String sceneName, String spawnId) {
        loadedScenes.add(sceneName);
        loadedSpawnIds.add(spawnId);

        // Create or retrieve mock scene
        Scene scene = registeredScenes.get(sceneName);
        if (scene == null) {
            scene = new MockScene(sceneName);
            registeredScenes.put(sceneName, scene);
        }

        currentScene = scene;
    }

    @Override
    public Scene getCurrentScene() {
        return currentScene;
    }

    @Override
    public void update(float deltaTime) {
        if (currentScene != null) {
            currentScene.update(deltaTime);
        }
    }

    @Override
    public void destroy() {
        // No-op for testing
    }

    /**
     * Gets the list of all scenes that were loaded (in order).
     */
    public List<String> getLoadedScenes() {
        return new ArrayList<>(loadedScenes);
    }

    /**
     * Gets the number of times loadScene was called.
     */
    public int getLoadSceneCallCount() {
        return loadedScenes.size();
    }

    /**
     * Checks if a specific scene was loaded.
     */
    public boolean wasSceneLoaded(String sceneName) {
        return loadedScenes.contains(sceneName);
    }

    /**
     * Gets the number of times a specific scene was loaded.
     */
    public int getLoadCountForScene(String sceneName) {
        return (int) loadedScenes.stream()
                .filter(name -> name.equals(sceneName))
                .count();
    }

    /**
     * Gets the spawn ID for the most recent loadScene call.
     */
    public String getLastSpawnId() {
        return loadedSpawnIds.isEmpty() ? null : loadedSpawnIds.get(loadedSpawnIds.size() - 1);
    }

    /**
     * Resets the mock state.
     */
    public void reset() {
        loadedScenes.clear();
        loadedSpawnIds.clear();
        currentScene = null;
    }

    /**
     * Simple mock scene for testing.
     */
    private static class MockScene extends Scene {
        public MockScene(String name) {
            super(name);
        }

        @Override
        public void onLoad() {
            // No-op
        }

        @Override
        public void update(float deltaTime) {
            // No-op
        }

        @Override
        public void destroy() {
            // No-op
        }
    }
}