package com.pocket.rpg.scenes;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.SpriteRenderer;
import com.pocket.rpg.engine.Camera;
import com.pocket.rpg.engine.GameObject;
import lombok.Getter;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

/**
 * Scene holds and manages GameObjects.
 * Scenes can be loaded and unloaded by the SceneManager.
 */
public abstract class Scene {
    @Getter
    private final String name;

    @Getter
    protected final Camera camera;

    private final List<GameObject> gameObjects;

    // GameObjects cached components for quick access
    private final List<SpriteRenderer> spriteRenderers;


    private boolean initialized = false;

    // Deferred execution for safe modifications during update/render
    private final Queue<Runnable> deferredActions = new LinkedList<>();
    private boolean isUpdating = false;
    private boolean isRendering = false;

    // Edge case: Allow the Renderer to rebuild the static SpriteRenderers if one is modified
    @Getter
    private boolean staticBatchDirty = false;

    public Scene(String name) {
        this.name = name;
        this.gameObjects = new ArrayList<>();
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
        isUpdating = true;

        // Create copy to avoid ConcurrentModificationException
        List<GameObject> gameObjectsToUpdate = new ArrayList<>(gameObjects);

        // Phase 1: Regular update
        for (GameObject gameObject : gameObjectsToUpdate) {
            // Check if still in scene (might have been removed)
            if (!gameObjects.contains(gameObject)) {
                continue;
            }

            if (gameObject.isEnabled()) {
                gameObject.update(deltaTime);
            }
        }

        // Phase 2: Late update (after all regular updates complete)
        for (GameObject gameObject : gameObjectsToUpdate) {
            // Check if still in scene (might have been removed during update)
            if (!gameObjects.contains(gameObject)) {
                continue;
            }

            if (gameObject.isEnabled()) {
                gameObject.lateUpdate(deltaTime);
            }
        }

        isUpdating = false;

        // Process deferred actions after both update phases complete
        processDeferredActions();
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
        deferredActions.clear();
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
     * Safe to call during update/render - will be executed after frame completes.
     */
    public void addGameObject(GameObject gameObject) {
        if (isUpdating || isRendering) {
            // Defer execution until after frame
            deferredActions.add(() -> addGameObjectImmediate(gameObject));
        } else {
            addGameObjectImmediate(gameObject);
        }
    }

    /**
     * Internal method to immediately add a GameObject.
     */
    private void addGameObjectImmediate(GameObject gameObject) {
        gameObject.setScene(this);
        gameObjects.add(gameObject);

        registerCachedComponent(gameObject);

        if (initialized) {
            gameObject.start();
        }
    }

    /**
     * Removes a GameObject from the scene.
     * Safe to call during update/render - will be executed after frame completes.
     */
    public void removeGameObject(GameObject gameObject) {
        if (isUpdating || isRendering) {
            // Defer execution until after frame
            deferredActions.add(() -> removeGameObjectImmediate(gameObject));
        } else {
            removeGameObjectImmediate(gameObject);
        }
    }

    /**
     * Internal method to immediately remove a GameObject.
     */
    private void removeGameObjectImmediate(GameObject gameObject) {
        if (gameObjects.remove(gameObject)) {
            unregisterCachedComponent(gameObject);

            gameObject.destroy();
            gameObject.setScene(null);
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


    /**
     * Marks the scene as currently rendering.
     * Called by RenderPipeline before rendering.
     */
    public void beginRendering() {
        isRendering = true;
    }

    /**
     * Marks the scene as finished rendering.
     * Called by RenderPipeline after rendering.
     */
    public void endRendering() {
        isRendering = false;
        processDeferredActions();
    }

    /**
     * Processes all deferred actions accumulated during update/render.
     * TODO: Remove deffered actions
     */
    private void processDeferredActions() {
        while (!deferredActions.isEmpty()) {
            Runnable action = deferredActions.poll();
            try {
                action.run();
            } catch (Exception e) {
                System.err.println("Error executing deferred action: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
}