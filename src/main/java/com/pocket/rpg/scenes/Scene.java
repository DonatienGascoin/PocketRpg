package com.pocket.rpg.scenes;

import com.pocket.rpg.components.Camera;
import com.pocket.rpg.components.SpritePostEffect;
import com.pocket.rpg.components.SpriteRenderer;
import com.pocket.rpg.engine.GameObject;
import com.pocket.rpg.rendering.Renderer;

import java.util.ArrayList;
import java.util.List;

/**
 * Scene holds and manages GameObjects.
 * Scenes can be loaded and unloaded by the SceneManager.
 * Now caches the active camera for O(1) lookup performance.
 */
public abstract class Scene {
    private final String name;
    private final List<GameObject> gameObjects;
    private final List<SpriteRenderer> spriteRenderers;
    private Renderer renderer;
    private boolean initialized = false;

    // Cached active camera for O(1) lookup
    private Camera activeCamera;

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
        if (spriteRenderer != null && spriteRenderer.isEnabled() && gameObject.isEnabled()) {
            registerSpriteRenderer(spriteRenderer);
        }

        // Check if this GameObject has a Camera and cache it
        if (gameObject.isEnabled()) {
            Camera camera = gameObject.getComponent(Camera.class);
            if (camera != null && camera.isEnabled()) {
                registerCamera(camera);
            }
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

            // If this GameObject has the active camera, clear it
            Camera camera = gameObject.getComponent(Camera.class);
            if (camera == activeCamera) {
                unregisterCamera(camera);
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
     * Registers a camera as the active camera for this scene.
     * Called automatically when a GameObject with Camera is added,
     * or when a Camera component is enabled.
     *
     * If a camera is already active, the new camera takes precedence.
     *
     * @param camera The camera to register
     */
    public void registerCamera(Camera camera) {
        if (camera != null && camera.isEnabled()) {
            this.activeCamera = camera;
            System.out.println("Camera registered: " + camera);
        }
    }

    /**
     * Unregisters a camera from this scene.
     * Called when a Camera component is disabled or its GameObject is removed.
     *
     * @param camera The camera to unregister
     */
    public void unregisterCamera(Camera camera) {
        if (this.activeCamera == camera) {
            this.activeCamera = null;
            System.out.println("Camera unregistered: " + camera);

            // Try to find another active camera as fallback
            findAndSetActiveCamera();
        }
    }

    /**
     * Searches for an active camera in the scene and sets it as active.
     * This is called as a fallback when the current camera is unregistered.
     */
    private void findAndSetActiveCamera() {
        for (GameObject go : gameObjects) {
            if (go.isEnabled()) {
                Camera camera = go.getComponent(Camera.class);
                if (camera != null && camera.isEnabled()) {
                    this.activeCamera = camera;
                    System.out.println("Fallback camera found: " + camera);
                    return;
                }
            }
        }
    }

    /**
     * Gets the active camera for this scene.
     * This is a cached O(1) lookup.
     *
     * @return The active camera, or null if no camera is active
     */
    public Camera getActiveCamera() {
        // Validate that cached camera is still valid
        if (activeCamera != null && !activeCamera.isEnabled()) {
            // Camera was disabled without notifying us - clear it
            activeCamera = null;
            findAndSetActiveCamera();
        }
        return activeCamera;
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
     * Now uses Renderer.drawSpriteRenderer() which reads Transform directly.
     */
    void render() {
        if (renderer == null) return;

        // Note: begin() is called by SceneManager.render() via beginWithCamera()
        // We just render the sprite renderers here

        // Render all registered SpriteRenderers
        for (SpriteRenderer spriteRenderer : spriteRenderers) {
            if (spriteRenderer.isEnabled() &&
                    spriteRenderer.getSprite() != null &&
                    spriteRenderer.getGameObject() != null &&
                    spriteRenderer.getGameObject().isEnabled()) {
                SpritePostEffect fx = spriteRenderer.getGameObject().getComponent(SpritePostEffect.class);
                if (fx != null) {
                    fx.renderWithEffects(renderer, spriteRenderer);
                } else {
                    renderer.drawSpriteRenderer(spriteRenderer);
                }
//                renderer.drawSpriteRenderer(spriteRenderer);
            }
        }
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
        activeCamera = null;
    }

    public String getName() {
        return name;
    }

    public Renderer getRenderer() {
        return renderer;
    }
}