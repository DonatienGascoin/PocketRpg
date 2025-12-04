package com.pocket.rpg.scenes;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.SpriteRenderer;
import com.pocket.rpg.core.Camera;
import com.pocket.rpg.core.GameObject;
import com.pocket.rpg.rendering.CameraSystem;
import com.pocket.rpg.ui.UICanvas;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Scene holds and manages GameObjects.
 */
public abstract class Scene {
    @Getter
    private final String name;

    @Getter
    protected final Camera camera;

    @Getter
    private CameraSystem cameraSystem;

    private final CopyOnWriteArrayList<GameObject> gameObjects;

    // Cached components for quick access
    private final List<SpriteRenderer> spriteRenderers;
    private final List<UICanvas> uiCanvases;  // Kept sorted by sortOrder
    private boolean canvasSortDirty = false;

    private boolean initialized = false;

    @Getter
    private boolean staticBatchDirty = false;

    public Scene(String name) {
        this.name = name;
        this.gameObjects = new CopyOnWriteArrayList<>();
        this.spriteRenderers = new ArrayList<>();
        this.uiCanvases = new ArrayList<>();
        this.camera = new Camera();
    }

    // ===========================================
    // Scene Lifecycle Management
    // ===========================================

    public void initialize(CameraSystem cameraSystem) {
        this.initialized = true;
        this.cameraSystem = cameraSystem;

        camera.setCameraSystem(cameraSystem);
        Camera.setMainCamera(camera);

        onLoad();

        for (GameObject go : gameObjects) {
            go.start();
        }
    }

    public void update(float deltaTime) {
        // Re-sort canvases if needed (deferred sorting)
        if (canvasSortDirty) {
            uiCanvases.sort(Comparator.comparingInt(UICanvas::getSortOrder));
            canvasSortDirty = false;
        }

        // Phase 1: Regular update
        for (GameObject gameObject : gameObjects) {
            if (!gameObjects.contains(gameObject)) continue;
            if (gameObject.isEnabled()) {
                gameObject.update(deltaTime);
            }
        }

        // Phase 2: Late update
        for (GameObject gameObject : gameObjects) {
            if (!gameObjects.contains(gameObject)) continue;
            if (gameObject.isEnabled()) {
                gameObject.lateUpdate(deltaTime);
            }
        }
    }

    public void destroy() {
        onUnload();

        List<GameObject> gameObjectsToDestroy = new ArrayList<>(gameObjects);
        for (GameObject gameObject : gameObjectsToDestroy) {
            gameObject.destroy();
        }

        gameObjects.clear();
        spriteRenderers.clear();
        uiCanvases.clear();
    }

    public abstract void onLoad();

    public void onUnload() {
    }

    // ===========================================
    // GameObject Management
    // ===========================================

    public void addGameObject(GameObject obj) {
        if (obj.getScene() != null) {
            throw new IllegalStateException(
                    "GameObject '" + obj.getName() + "' already belongs to a scene"
            );
        }

        gameObjects.add(obj);
        obj.setScene(this);
        registerCachedComponents(obj);

        if (initialized) {
            obj.start();
        }
    }

    public void removeGameObject(GameObject obj) {
        if (gameObjects.remove(obj)) {
            obj.destroy();
            unregisterCachedComponents(obj);
            obj.setScene(null);
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

    // ===========================================
    // Component Caching
    // ===========================================

    /**
     * Registers cached components from a GameObject and its children.
     */
    public void registerCachedComponents(GameObject gameObject) {
        // Sprite renderers
        for (SpriteRenderer spr : gameObject.getComponents(SpriteRenderer.class)) {
            if (!spriteRenderers.contains(spr)) {
                spriteRenderers.add(spr);
            }
        }

        // UI canvases (insert sorted)
        for (UICanvas canvas : gameObject.getComponents(UICanvas.class)) {
            if (!uiCanvases.contains(canvas)) {
                insertCanvasSorted(canvas);
            }
        }

        // Register children recursively
        for (GameObject child : gameObject.getChildren()) {
            registerCachedComponents(child);
        }
    }

    /**
     * Registers a single component.
     */
    public void registerCachedComponent(Component component) {
        if (component instanceof SpriteRenderer spr && !spriteRenderers.contains(spr)) {
            spriteRenderers.add(spr);
        } else if (component instanceof UICanvas canvas && !uiCanvases.contains(canvas)) {
            insertCanvasSorted(canvas);
        }
    }

    /**
     * Unregisters cached components from a GameObject and its children.
     */
    public void unregisterCachedComponents(GameObject gameObject) {
        spriteRenderers.removeAll(gameObject.getComponents(SpriteRenderer.class));
        uiCanvases.removeAll(gameObject.getComponents(UICanvas.class));

        for (GameObject child : gameObject.getChildren()) {
            unregisterCachedComponents(child);
        }
    }

    /**
     * Unregisters a single component.
     */
    public void unregisterCachedComponent(Component component) {
        if (component instanceof SpriteRenderer spr) {
            spriteRenderers.remove(spr);
        } else if (component instanceof UICanvas canvas) {
            uiCanvases.remove(canvas);
        }
    }

    /**
     * Insert canvas in sorted position by sortOrder.
     */
    private void insertCanvasSorted(UICanvas canvas) {
        int insertIndex = 0;
        for (int i = 0; i < uiCanvases.size(); i++) {
            if (uiCanvases.get(i).getSortOrder() > canvas.getSortOrder()) {
                break;
            }
            insertIndex = i + 1;
        }
        uiCanvases.add(insertIndex, canvas);
    }

    /**
     * Mark canvas list as needing re-sort.
     * Called when a canvas's sortOrder changes.
     */
    public void markCanvasSortDirty() {
        canvasSortDirty = true;
    }

    // ===========================================
    // Component Access
    // ===========================================

    public List<SpriteRenderer> getSpriteRenderers() {
        return new ArrayList<>(spriteRenderers);
    }

    /**
     * Returns canvases sorted by sortOrder (ascending).
     */
    public List<UICanvas> getUICanvases() {
        return new ArrayList<>(uiCanvases);
    }

    // ===========================================
    // Static Batch Management
    // ===========================================

    public void markStaticBatchDirty() {
        staticBatchDirty = true;
    }

    public void clearStaticBatchDirty() {
        staticBatchDirty = false;
    }
}