package com.pocket.rpg.scenes;

import lombok.Setter;

/**
 * Static facade for scene management.
 * Delegates all calls to a {@link SceneManagerContext} implementation.
 * <p>
 * Follows the same service locator pattern as {@link com.pocket.rpg.input.Input}.
 *
 * @see SceneManagerContext
 * @see DefaultSceneManagerContext
 */
public class SceneManager {

    @Setter
    private static SceneManagerContext context;

    public static SceneManagerContext getContext() {
        return context;
    }

    public static boolean hasContext() {
        return context != null;
    }

    public static Scene getActiveScene() {
        return context != null ? context.getActiveScene() : null;
    }

    public static Scene getCurrentScene() {
        return context != null ? context.getCurrentScene() : null;
    }

    public static String getPendingSpawnId() {
        return context != null ? context.getPendingSpawnId() : null;
    }

    public static void setSceneLoader(RuntimeSceneLoader loader, String basePath) {
        getContext().setSceneLoader(loader, basePath);
    }

    public static void registerScene(Scene scene) {
        getContext().registerScene(scene);
    }

    public static void addLifecycleListener(SceneLifecycleListener listener) {
        getContext().addLifecycleListener(listener);
    }

    public static void removeLifecycleListener(SceneLifecycleListener listener) {
        getContext().removeLifecycleListener(listener);
    }

    public static void loadScene(String sceneName) {
        getContext().loadScene(sceneName);
    }

    public static void loadScene(String sceneName, String spawnId) {
        getContext().loadScene(sceneName, spawnId);
    }

    public static void loadScene(Scene scene) {
        getContext().loadScene(scene);
    }

    public static void update(float deltaTime) {
        getContext().update(deltaTime);
    }

    public static void destroy() {
        if (context != null) {
            context.destroy();
            context = null;
        }
    }
}
