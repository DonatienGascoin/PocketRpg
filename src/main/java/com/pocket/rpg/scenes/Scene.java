package com.pocket.rpg.scenes;

import com.pocket.rpg.components.SpriteRenderer;
import com.pocket.rpg.engine.GameObject;
import com.pocket.rpg.rendering.Renderer;

import java.util.ArrayList;
import java.util.List;

/**
 * Scene holds and manages GameObjects.
 * Scenes can be loaded and unloaded by the SceneManager.
 */
public abstract class Scene {
    private final String name;
    private final List<GameObject> gameObjects;
    private final List<SpriteRenderer> spriteRenderers;
    private Renderer renderer;
    private boolean initialized = false;

    public Scene(String name) {
        this.name = name;
        this.gameObjects = new ArrayList<>();
        this.spriteRenderers = new ArrayList<>();
    }

    /**
     * Called when the scene is loaded.
     * Override this to create your initial GameObjects.
     */
    public abstract void onLoad();

    /**
     * Called when the scene is unloaded.
     * Override this to perform cleanup.
     */
    public void onUnload() {
        // Default implementation
    }

    /**
     * Initializes the scene with the renderer.
     */
    void initialize(Renderer renderer) {
        this.renderer = renderer;
        this.initialized = true;
        onLoad();

        // Start all existing GameObjects
        for (GameObject go : gameObjects) {
            go.start();
        }
    }

    /**
     * Adds a GameObject to this scene.
     */
    public void addGameObject(GameObject gameObject) {
        gameObject.setScene(this);
        gameObjects.add(gameObject);

        // Register any SpriteRenderer components
        SpriteRenderer spriteRenderer = gameObject.getComponent(SpriteRenderer.class);
        if (spriteRenderer != null) {
            registerSpriteRenderer(spriteRenderer);
        }

        // If scene is already initialized, start the GameObject immediately
        if (initialized) {
            gameObject.start();
        }
    }

    /**
     * Removes a GameObject from this scene.
     */
    public void removeGameObject(GameObject gameObject) {
        if (gameObjects.remove(gameObject)) {
            // Unregister any SpriteRenderer components
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
     * Gets all GameObjects in the scene.
     */
    public List<GameObject> getGameObjects() {
        return new ArrayList<>(gameObjects);
    }

    /**
     * Registers a SpriteRenderer to be drawn by the Renderer.
     * Called automatically when a GameObject with SpriteRenderer is added.
     */
    public void registerSpriteRenderer(SpriteRenderer spriteRenderer) {
        if (!spriteRenderers.contains(spriteRenderer)) {
            spriteRenderers.add(spriteRenderer);
        }
    }

    /**
     * Unregisters a SpriteRenderer from the Renderer.
     */
    public void unregisterSpriteRenderer(SpriteRenderer spriteRenderer) {
        spriteRenderers.remove(spriteRenderer);
    }

    /**
     * Updates all GameObjects in the scene.
     */
    void update(float deltaTime) {
        // Update all GameObjects
        for (GameObject gameObject : gameObjects) {
            if (gameObject.isEnabled()) {
                gameObject.update(deltaTime);
            }
        }
    }

    /**
     * Renders all sprites in the scene.
     */
    void render() {
        if (renderer == null) return;

        renderer.begin();

        // Render all registered SpriteRenderers
        for (SpriteRenderer spriteRenderer : spriteRenderers) {
            if (spriteRenderer.isEnabled() && spriteRenderer.getSprite() != null) {
                renderer.drawSprite(spriteRenderer.getSprite());
            }
        }

        renderer.end();
    }

    /**
     * Destroys all GameObjects in the scene.
     */
    void destroy() {
        onUnload();

        for (GameObject gameObject : gameObjects) {
            gameObject.destroy();
        }
        gameObjects.clear();
        spriteRenderers.clear();
    }

    public String getName() {
        return name;
    }

    public Renderer getRenderer() {
        return renderer;
    }
}