package com.pocket.rpg.scenes;

import com.pocket.rpg.components.Camera;
import com.pocket.rpg.components.SpriteRenderer;
import com.pocket.rpg.engine.GameObject;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * Scene holds and manages GameObjects.
 * Scenes can be loaded and unloaded by the SceneManager.
 * Provides list of SpriteRenderers for rendering pipeline.
 */
public abstract class Scene {
    @Getter
    private final String name;
    private final List<GameObject> gameObjects;
    private final List<SpriteRenderer> spriteRenderers;
    private boolean initialized = false;

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

    public void addGameObject(GameObject gameObject) {
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

    public void removeGameObject(GameObject gameObject) {
        if (gameObjects.remove(gameObject)) {
            SpriteRenderer spriteRenderer = gameObject.getComponent(SpriteRenderer.class);
            if (spriteRenderer != null) {
                unregisterSpriteRenderer(spriteRenderer);
            }

            gameObject.destroy();
            gameObject.setScene(null);
        }
    }

    public GameObject findGameObject(String name) {
        for (GameObject go : gameObjects) {
            if (go.getName().equals(name)) {
                return go;
            }
        }
        return null;
    }

    public List<GameObject> getGameObjects() {
        return new ArrayList<>(gameObjects);
    }

    public void registerSpriteRenderer(SpriteRenderer spriteRenderer) {
        if (!spriteRenderers.contains(spriteRenderer)) {
            spriteRenderers.add(spriteRenderer);
        }
    }

    public void unregisterSpriteRenderer(SpriteRenderer spriteRenderer) {
        spriteRenderers.remove(spriteRenderer);
    }

    /**
     * Gets all registered sprite renderers.
     * Used by RenderPipeline for rendering.
     */
    public List<SpriteRenderer> getSpriteRenderers() {
        return new ArrayList<>(spriteRenderers);
    }

    void update(float deltaTime) {
        for (GameObject gameObject : gameObjects) {
            if (gameObject.isEnabled()) {
                gameObject.update(deltaTime);
            }
        }
    }

    void destroy() {
        onUnload();

        for (GameObject gameObject : gameObjects) {
            gameObject.destroy();
        }
        gameObjects.clear();
        spriteRenderers.clear();
    }

}