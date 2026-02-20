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

    /**
     * Called after scene.initialize() + applyCameraData(), before teleportPlayerToSpawn().
     * All component onStart() calls have completed. Use this for deferred initialization
     * that depends on a fully-initialized scene (e.g., ISaveable state application).
     *
     * @param scene The scene that was just initialized
     */
    default void onPostSceneInitialize(Scene scene) {}
}