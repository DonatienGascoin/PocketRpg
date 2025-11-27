package com.pocket.rpg.scenes;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.SpriteRenderer;
import com.pocket.rpg.engine.Camera;
import com.pocket.rpg.engine.GameObject;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Scene holds and manages GameObjects.
 * Scenes can be loaded and unloaded by the SceneManager.
 */
public abstract class Scene {
    @Getter
    private final String name;

    @Getter
    protected final Camera camera;

    private final CopyOnWriteArrayList<GameObject> gameObjects;


    // GameObjects cached components for quick access
    private final List<SpriteRenderer> spriteRenderers;


    private boolean initialized = false;


    // Edge case: Allow the Renderer to rebuild the static SpriteRenderers if one is modified TODO: Needed ?
    @Getter
    private boolean staticBatchDirty = false;

    public Scene(String name) {
        this.name = name;
        this.gameObjects = new CopyOnWriteArrayList<>();
        this.spriteRenderers = new ArrayList<>();
        this.camera = new Camera();
    }

    // ===========================================
    // Scene Lifecycle Management
    // ===========================================

    /**
     * Initializes the scene and all its GameObjects. Called by SceneManager when loading the scene.
     */
    public void initialize() {
        this.initialized = true;
        onLoad();

        for (GameObject go : gameObjects) {
            go.start();
        }
    }

    /**
     * Called every frame to update all GameObjects.
     */
    public void update(float deltaTime) {

        // Phase 1: Regular update
        for (GameObject gameObject : gameObjects) {
            // Check if still in scene (might have been removed)
            if (!gameObjects.contains(gameObject)) {
                continue;
            }

            if (gameObject.isEnabled()) {
                gameObject.update(deltaTime);
            }
        }

        // Phase 2: Late update (after all regular updates complete)
        for (GameObject gameObject : gameObjects) {
            // Check if still in scene (might have been removed during update)
            if (!gameObjects.contains(gameObject)) {
                continue;
            }

            if (gameObject.isEnabled()) {
                gameObject.lateUpdate(deltaTime);
            }
        }

    }


    /**
     * Destroys the scene and all its GameObjects.
     */
    public void destroy() {
        onUnload();

        // Create copy to avoid modification during iteration
        List<GameObject> gameObjectsToDestroy = new ArrayList<>(gameObjects);
        for (GameObject gameObject : gameObjectsToDestroy) {
            gameObject.destroy();
        }

        gameObjects.clear();
        spriteRenderers.clear();
    }

    /**
     * Called when the scene is loaded. Implement scene-specific initialization here.
     */
    public abstract void onLoad();

    /**
     * Called when the scene is unloaded. Implement scene-specific cleanup here.
     */
    public void onUnload() {
        // Default implementation
    }


    // ===========================================
    // GameObject Management
    // ===========================================

    /**
     * Adds a GameObject to the scene.
     *
     * @param obj The GameObject to add
     */
    public void addGameObject(GameObject obj) {
        if (obj.getScene() != null) {
            throw new IllegalStateException(
                    "GameObject '" + obj.getName() + "' already belongs to a scene"
            );
        }

        gameObjects.add(obj); 
        obj.setScene(this);
        registerCachedComponent(obj);

        if (initialized) {
            obj.start();
        }
    }

    /**
     * Removes a GameObject from the scene.
     */
    public void removeGameObject(GameObject obj) {
        if (gameObjects.remove(obj)) {
            obj.destroy();
            unregisterCachedComponent(obj);
            obj.setScene(null);
        }
    }

    /**
     * Finds a GameObject by name.
     */
    public GameObject findGameObject(String name) {
        for (GameObject go : gameObjects) {
            if (go.getName().equals(name)) {
                return go;
            }
        }
        return null;
    }

    /**
     * Gets all GameObjects (returns defensive copy).
     */
    public List<GameObject> getGameObjects() {
        return new ArrayList<>(gameObjects);
    }


    /**
     * Registers cached components from a GameObject.
     * Called when adding a GameObject to the scene.
     */
    public void registerCachedComponent(GameObject gameObject) {
        var spriteRenderers = gameObject.getComponents(SpriteRenderer.class);
        this.spriteRenderers.addAll(spriteRenderers);
    }

    /**
     * Registers a single cached component.
     */
    public void registerCachedComponent(Component component) {
        if (component instanceof SpriteRenderer spr && !this.spriteRenderers.contains(spr)) {
            this.spriteRenderers.add(spr);
        }
    }

    /**
     * Unregisters cached components from a GameObject.
     * Called when removing a GameObject from the scene.
     */
    public void unregisterCachedComponent(GameObject gameObject) {
        var spriteRenderers = gameObject.getComponents(SpriteRenderer.class);
        this.spriteRenderers.removeAll(spriteRenderers);
    }

    /**
     * Unregisters a single cached component.
     */
    public void unregisterCachedComponent(Component component) {
        if (component instanceof SpriteRenderer spr) {
            this.spriteRenderers.remove(spr);
        }
    }

    /**
     * Gets all registered sprite renderers.
     * Used by RenderPipeline for rendering.
     * Returns defensive copy to prevent modification during iteration.
     */
    public List<SpriteRenderer> getSpriteRenderers() {
        return new ArrayList<>(spriteRenderers);
    }

    /**
     * Marks the static sprite batch as dirty, forcing a rebuild.
     * Call this when you modify a static sprite's transform.
     * Only works if using BatchRenderer.
     * TODO: Needed ?
     */
    public void markStaticBatchDirty() {
        staticBatchDirty = true;
    }

    //    TODO: Needed ?
    public void clearStaticBatchDirty() {
        staticBatchDirty = false;
    }
}