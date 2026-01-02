package com.pocket.rpg.scenes;

import com.pocket.rpg.collision.CollisionMap;
import com.pocket.rpg.collision.CollisionSystem;
import com.pocket.rpg.collision.EntityOccupancyMap;
import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.SpriteRenderer;
import com.pocket.rpg.components.ui.UICanvas;
import com.pocket.rpg.config.RenderingConfig;
import com.pocket.rpg.core.GameCamera;
import com.pocket.rpg.core.GameObject;
import com.pocket.rpg.core.ViewportConfig;
import com.pocket.rpg.rendering.Renderable;
import com.pocket.rpg.serialization.ComponentRefResolver;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Scene holds and manages GameObjects.
 * Each Scene owns a Camera that defines the view into the world.
 * <p>
 * UPDATED: Includes full collision system with behaviors and entity occupancy.
 * UPDATED: Resolves @ComponentRef annotations after onLoad(), before start().
 */
public abstract class Scene {
    @Getter
    private final String name;

    @Getter
    protected GameCamera camera;

    @Getter
    private ViewportConfig viewportConfig;

    private final CopyOnWriteArrayList<GameObject> gameObjects;

    // Cached renderables for quick access (sorted by zIndex)
    private final List<Renderable> renderables;
    private boolean renderableSortDirty = false;

    // UI canvases (kept sorted by sortOrder)
    private final List<UICanvas> uiCanvases;
    private boolean canvasSortDirty = false;

    // Collision system with entity tracking
    @Getter
    private final CollisionMap collisionMap;
    @Getter
    private final EntityOccupancyMap entityOccupancyMap;
    @Getter
    private final CollisionSystem collisionSystem;

    private boolean initialized = false;

    public Scene(String name) {
        this.name = name;
        this.gameObjects = new CopyOnWriteArrayList<>();
        this.renderables = new ArrayList<>();
        this.uiCanvases = new ArrayList<>();

        // Initialize collision system with entity tracking
        this.collisionMap = new CollisionMap();
        this.entityOccupancyMap = new EntityOccupancyMap();
        this.collisionSystem = new CollisionSystem(collisionMap, entityOccupancyMap);

        // Camera created in initialize() when ViewportConfig is available
    }

    // ===========================================
    // Scene Lifecycle Management
    // ===========================================

    /**
     * Initializes the scene with viewport configuration.
     * Creates the camera and calls onLoad().
     *
     * @param viewportConfig  Shared viewport configuration
     * @param renderingConfig Rendering configuration
     */
    public void initialize(ViewportConfig viewportConfig, RenderingConfig renderingConfig) {
        this.initialized = true;
        this.viewportConfig = viewportConfig;
        // Create camera with viewport config
        this.camera = new GameCamera(viewportConfig, renderingConfig.getDefaultOrthographicSize(viewportConfig.getGameHeight()));
        GameCamera.setMainCamera(camera);

        onLoad();

        // Resolve @ComponentRef annotations after hierarchy is established
        for (GameObject go : gameObjects) {
            ComponentRefResolver.resolveReferences(go);
        }

        for (GameObject go : gameObjects) {
            go.start();
        }
    }

    public void update(float deltaTime) {
        // Update camera
        if (camera != null) {
            camera.update(deltaTime);
        }

        // Re-sort canvases if needed (deferred sorting)
        if (canvasSortDirty) {
            uiCanvases.sort(Comparator.comparingInt(UICanvas::getSortOrder));
            canvasSortDirty = false;
        }

        // Re-sort renderables if needed (deferred sorting)
        if (renderableSortDirty) {
            renderables.sort(Comparator.comparingInt(Renderable::getZIndex));
            renderableSortDirty = false;
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
        renderables.clear();
        uiCanvases.clear();
        collisionMap.clear();
        entityOccupancyMap.clear();
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
        // Register all Renderable components
        for (Component component : gameObject.getAllComponents()) {
            if (component instanceof Renderable renderable) {
                if (!renderables.contains(renderable)) {
                    renderables.add(renderable);
                    renderableSortDirty = true;
                }
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
        if (component instanceof Renderable renderable && !renderables.contains(renderable)) {
            renderables.add(renderable);
            renderableSortDirty = true;
        }
        if (component instanceof UICanvas canvas && !uiCanvases.contains(canvas)) {
            insertCanvasSorted(canvas);
        }
    }

    /**
     * Unregisters cached components from a GameObject and its children.
     */
    public void unregisterCachedComponents(GameObject gameObject) {
        for (Component component : gameObject.getAllComponents()) {
            if (component instanceof Renderable) {
                renderables.remove(component);
            }
        }
        uiCanvases.removeAll(gameObject.getComponents(UICanvas.class));

        for (GameObject child : gameObject.getChildren()) {
            unregisterCachedComponents(child);
        }
    }

    /**
     * Unregisters a single component.
     */
    public void unregisterCachedComponent(Component component) {
        if (component instanceof Renderable) {
            renderables.remove(component);
        }
        if (component instanceof UICanvas canvas) {
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

    /**
     * Mark renderable list as needing re-sort.
     * Called when a renderable's zIndex changes.
     */
    public void markRenderableSortDirty() {
        renderableSortDirty = true;
    }

    // ===========================================
    // Renderable Access
    // ===========================================

    /**
     * Returns all renderables sorted by zIndex (ascending).
     * Lower zIndex renders first (behind higher values).
     *
     * @return Sorted list of renderables
     */
    public List<Renderable> getRenderers() {
        // Ensure sorted before returning
        if (renderableSortDirty) {
            renderables.sort(Comparator.comparingInt(Renderable::getZIndex));
            renderableSortDirty = false;
        }
        return new ArrayList<>(renderables);
    }

    /**
     * Returns sprite renderers only.
     *
     * @deprecated Use {@link #getRenderers()} for unified rendering pipeline.
     */
    @Deprecated
    public List<SpriteRenderer> getSpriteRenderers() {
        List<SpriteRenderer> result = new ArrayList<>();
        for (Renderable renderable : renderables) {
            if (renderable instanceof SpriteRenderer sr) {
                result.add(sr);
            }
        }
        return result;
    }

    /**
     * Returns canvases sorted by sortOrder (ascending).
     */
    public List<UICanvas> getUICanvases() {
        return new ArrayList<>(uiCanvases);
    }
}