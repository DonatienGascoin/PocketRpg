package com.pocket.rpg.scenes;


/**
 * Interface for scene manager implementations.
 * Allows different implementations (production, mock/test).
 *
 * @see SceneManager
 */
public interface SceneManagerContext {

    Scene getActiveScene();

    Scene getCurrentScene();

    String getPendingSpawnId();

    void setSceneLoader(RuntimeSceneLoader loader, String basePath);

    void registerScene(Scene scene);

    void addLifecycleListener(SceneLifecycleListener listener);

    void removeLifecycleListener(SceneLifecycleListener listener);

    void loadScene(String sceneName);

    void loadScene(String sceneName, String spawnId);

    void loadScene(Scene scene);

    void update(float deltaTime);

    void destroy();
}
