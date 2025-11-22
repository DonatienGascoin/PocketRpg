package com.pocket.rpg.scenes;

/**
 * Listener interface for scene lifecycle events.
 * Allows external systems to react to scene loading and unloading.
 */
public interface SceneLifecycleListener {

    /**
     * Called when a scene is loaded.
     *
     * @param scene The scene that was loaded
     */
    void onSceneLoaded(Scene scene);

    /**
     * Called when a scene is unloaded.
     *
     * @param scene The scene that was unloaded
     */
    void onSceneUnloaded(Scene scene);
}