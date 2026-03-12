package com.pocket.rpg.scenes;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Mock implementation of SceneManagerContext for testing.
 * Lightweight, deterministic, and doesn't require real scenes.
 * Tracks loadScene calls for assertions.
 */
public class MockSceneManager implements SceneManagerContext {

    private final Map<String, Scene> registeredScenes = new HashMap<>();
    private final List<String> loadedScenes = new ArrayList<>();
    private final List<String> loadedSpawnIds = new ArrayList<>();
    private Scene currentScene;

    @Override
    public Scene getActiveScene() {
        return currentScene;
    }

    @Override
    public Scene getCurrentScene() {
        return currentScene;
    }

    @Override
    public String getPendingSpawnId() {
        return loadedSpawnIds.isEmpty() ? null : loadedSpawnIds.get(loadedSpawnIds.size() - 1);
    }

    @Override
    public void setSceneLoader(RuntimeSceneLoader loader, String basePath) {}

    @Override
    public void registerScene(Scene scene) {
        registeredScenes.put(scene.getName(), scene);
    }

    @Override
    public void addLifecycleListener(SceneLifecycleListener listener) {}

    @Override
    public void removeLifecycleListener(SceneLifecycleListener listener) {}

    @Override
    public void loadScene(String sceneName) {
        loadedScenes.add(sceneName);
        loadedSpawnIds.add(null);

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

        Scene scene = registeredScenes.get(sceneName);
        if (scene == null) {
            scene = new MockScene(sceneName);
            registeredScenes.put(sceneName, scene);
        }

        currentScene = scene;
    }

    @Override
    public void loadScene(Scene scene) {
        currentScene = scene;
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

    // ========================================================================
    // TEST HELPERS
    // ========================================================================

    public List<String> getLoadedScenes() {
        return new ArrayList<>(loadedScenes);
    }

    public int getLoadSceneCallCount() {
        return loadedScenes.size();
    }

    public boolean wasSceneLoaded(String sceneName) {
        return loadedScenes.contains(sceneName);
    }

    public int getLoadCountForScene(String sceneName) {
        return (int) loadedScenes.stream()
                .filter(name -> name.equals(sceneName))
                .count();
    }

    public String getLastSpawnId() {
        return loadedSpawnIds.isEmpty() ? null : loadedSpawnIds.get(loadedSpawnIds.size() - 1);
    }

    public void reset() {
        loadedScenes.clear();
        loadedSpawnIds.clear();
        currentScene = null;
    }

    private static class MockScene extends Scene {
        public MockScene(String name) {
            super(name);
        }

        @Override
        public void onLoad() {}

        @Override
        public void update(float deltaTime) {}

        @Override
        public void destroy() {}
    }
}
