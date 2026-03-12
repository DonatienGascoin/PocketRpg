package com.pocket.rpg.testing;

import com.pocket.rpg.scenes.RuntimeSceneLoader;
import com.pocket.rpg.scenes.Scene;
import com.pocket.rpg.scenes.SceneLifecycleListener;
import com.pocket.rpg.scenes.SceneManagerContext;
import lombok.Getter;
import lombok.Setter;

/**
 * Mock {@link SceneManagerContext} for unit tests.
 * Holds an active scene and stubs all other methods.
 */
public class MockSceneManagerContext implements SceneManagerContext {

    @Getter
    @Setter
    private Scene activeScene;

    public MockSceneManagerContext() {}

    public MockSceneManagerContext(Scene scene) {
        this.activeScene = scene;
    }

    @Override
    public Scene getCurrentScene() {
        return activeScene;
    }

    @Override
    public String getPendingSpawnId() {
        return null;
    }

    @Override
    public void setSceneLoader(RuntimeSceneLoader loader, String basePath) {}

    @Override
    public void registerScene(Scene scene) {}

    @Override
    public void addLifecycleListener(SceneLifecycleListener listener) {}

    @Override
    public void removeLifecycleListener(SceneLifecycleListener listener) {}

    @Override
    public void loadScene(String sceneName) {}

    @Override
    public void loadScene(String sceneName, String spawnId) {}

    @Override
    public void loadScene(Scene scene) {}

    @Override
    public void update(float deltaTime) {}

    @Override
    public void destroy() {}
}
