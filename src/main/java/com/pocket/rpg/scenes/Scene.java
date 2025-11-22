package com.pocket.rpg.scenes;

import com.pocket.rpg.components.Camera;
import com.pocket.rpg.components.SpriteRenderer;
import com.pocket.rpg.engine.GameObject;
import lombok.Getter;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

/**
 * Scene holds and manages GameObjects.
 * Scenes can be loaded and unloaded by the SceneManager.
 * Provides list of SpriteRenderers for rendering pipeline.
 * 
 * FIXED: Now supports safe GameObject add/remove during update/render
 */
public abstract class Scene {
    @Getter
    private final String name;
    private final List<GameObject> gameObjects;
    private final List<SpriteRenderer> spriteRenderers;
    private boolean initialized = false;

    // FIX: Deferred execution for safe modifications during update/render
    private Queue<Runnable> deferredActions = new LinkedList<>();
    private boolean isUpdating = false;
    private boolean isRendering = false;

    public Scene(String name) {
        this.name = name;
        this.gameObjects = new ArrayList<>();
        this.spriteRenderers = new ArrayList<>();
    }

    public abstract void onLoad();

    public void onUnload() {
        // Default implementation
    }

    void initialize() {
        this.initialized = true;
        onLoad();

        for (GameObject go : gameObjects) {
            go.start();
        }
    }

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

        SpriteRenderer spriteRenderer = gameObject.getComponent(SpriteRenderer.class);
        if (spriteRenderer != null && spriteRenderer.isEnabled() && gameObject.isEnabled()) {
            registerSpriteRenderer(spriteRenderer);
        }

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
            SpriteRenderer spriteRenderer = gameObject.getComponent(SpriteRenderer.class);
            if (spriteRenderer != null) {
                unregisterSpriteRenderer(spriteRenderer);
            }

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
     * Registers a SpriteRenderer for rendering.
     * Safe to call anytime.
     */
    public void registerSpriteRenderer(SpriteRenderer spriteRenderer) {
        if (!spriteRenderers.contains(spriteRenderer)) {
            if (isRendering) {
                // Defer until after rendering completes
                deferredActions.add(() -> spriteRenderers.add(spriteRenderer));
            } else {
                spriteRenderers.add(spriteRenderer);
            }
        }
    }

    /**
     * Unregisters a SpriteRenderer from rendering.
     * Safe to call anytime.
     */
    public void unregisterSpriteRenderer(SpriteRenderer spriteRenderer) {
        if (isRendering) {
            // Defer until after rendering completes
            deferredActions.add(() -> spriteRenderers.remove(spriteRenderer));
        } else {
            spriteRenderers.remove(spriteRenderer);
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
     * Called every frame to update all GameObjects.
     * FIX: Now uses safe iteration and processes deferred actions.
     */
    void update(float deltaTime) {
        isUpdating = true;

        // Create copy to avoid ConcurrentModificationException
        List<GameObject> gameObjectsToUpdate = new ArrayList<>(gameObjects);

        for (GameObject gameObject : gameObjectsToUpdate) {
            // Check if still in scene (might have been removed)
            if (!gameObjects.contains(gameObject)) {
                continue;
            }

            if (gameObject.isEnabled()) {
                gameObject.update(deltaTime);
            }
        }

        isUpdating = false;

        // Process deferred actions after update completes
        processDeferredActions();
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

    /**
     * Destroys the scene and all its GameObjects.
     */
    void destroy() {
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
}
