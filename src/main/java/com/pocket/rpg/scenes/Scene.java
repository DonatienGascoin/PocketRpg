package com.pocket.rpg.scenes;

import com.pocket.rpg.collision.CollisionMap;
import com.pocket.rpg.collision.CollisionSystem;
import com.pocket.rpg.collision.EntityOccupancyMap;
import com.pocket.rpg.collision.trigger.*;
import com.pocket.rpg.collision.trigger.handlers.StairsHandler;
import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.interaction.SpawnPoint;
import com.pocket.rpg.components.pokemon.GridMovement;
import com.pocket.rpg.components.rendering.SpriteRenderer;
import com.pocket.rpg.scenes.transitions.SceneTransition;
import com.pocket.rpg.components.ui.UICanvas;
import com.pocket.rpg.config.RenderingConfig;
import com.pocket.rpg.ui.ComponentKeyRegistry;
import com.pocket.rpg.core.camera.GameCamera;
import com.pocket.rpg.core.GameObject;
import com.pocket.rpg.core.window.ViewportConfig;
import com.pocket.rpg.rendering.core.Renderable;
import com.pocket.rpg.serialization.ComponentReferenceResolver;
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

    // Trigger system
    @Getter
    private final TriggerDataMap triggerDataMap;
    @Getter
    private final TriggerSystem triggerSystem;

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

        // Initialize trigger system
        this.triggerDataMap = new TriggerDataMap();
        this.triggerSystem = new TriggerSystem(triggerDataMap, collisionMap);
        registerDefaultTriggerHandlers();

        // Camera created in initialize() when ViewportConfig is available
    }

    /**
     * Registers default trigger handlers.
     * <p>
     * Override this method to customize handler registration.
     * Call super.registerDefaultTriggerHandlers() to keep defaults.
     */
    protected void registerDefaultTriggerHandlers() {
        // Stairs handler - only remaining collision-based trigger
        triggerSystem.registerHandler(StairsData.class, new StairsHandler());

        // Note: WARP and DOOR triggers are now entity-based components:
        // - WarpZone component with TriggerZone for teleportation
        // - Door component for interactive doors
    }

    /**
     * Finds a spawn point by ID in this scene.
     * Searches for SpawnPoint components on GameObjects.
     *
     * @param spawnId The spawn point ID to find
     * @return TileCoord of the spawn point, or null if not found
     */
    public TileCoord findSpawnPoint(String spawnId) {
        if (spawnId == null || spawnId.isBlank()) {
            return null;
        }

        // Search GameObjects for SpawnPoint components (recursively)
        for (GameObject obj : gameObjects) {
            TileCoord found = findSpawnPointRecursive(obj, spawnId);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /**
     * Recursively searches for a spawn point by ID.
     */
    private TileCoord findSpawnPointRecursive(GameObject obj, String spawnId) {
        SpawnPoint spawn = obj.getComponent(SpawnPoint.class);
        if (spawn != null && spawnId.equals(spawn.getSpawnId())) {
            var pos = obj.getTransform().getPosition();
            int x = (int) Math.floor(pos.x);
            int y = (int) Math.floor(pos.y);
            int z = 0; // Default elevation - SpawnPoint could add elevation field if needed
            return new TileCoord(x, y, z);
        }
        for (GameObject child : obj.getChildren()) {
            TileCoord found = findSpawnPointRecursive(child, spawnId);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /**
     * Teleports an entity to a spawn point in this scene.
     *
     * @param entity  The entity to teleport
     * @param spawnId The spawn point ID
     */
    public void teleportToSpawn(GameObject entity, String spawnId) {
        TileCoord spawnCoord = findSpawnPoint(spawnId);
        if (spawnCoord == null) {
            System.err.println("[Scene] Spawn point not found: " + spawnId);
            return;
        }

        // Get GridMovement component if available
        GridMovement movement = entity.getComponent(GridMovement.class);
        if (movement != null) {
            movement.setGridPosition(spawnCoord.x(), spawnCoord.y());
            movement.setZLevel(spawnCoord.elevation());

            System.out.println("[Scene] Teleported " + entity.getName() +
                    " to spawn '" + spawnId + "' at (" + spawnCoord.x() + ", " + spawnCoord.y() + ")");
        } else {
            // Fallback: directly set transform position
            float tileSize = 1.0f; // Default tile size
            entity.getTransform().setPosition(
                    spawnCoord.x() * tileSize + tileSize * 0.5f,
                    spawnCoord.y() * tileSize + tileSize * 0.5f,
                    entity.getTransform().getPosition().z
            );
            System.out.println("[Scene] Teleported " + entity.getName() +
                    " to spawn '" + spawnId + "' (no GridMovement)");
        }
    }

    /**
     * Loads another scene and teleports to a spawn point.
     * <p>
     * Uses SceneTransition if initialized, otherwise logs a warning.
     * The spawn point is stored for the target scene to use after loading.
     *
     * @param sceneName   Target scene name
     * @param targetSpawn Spawn point ID in target scene
     */
    protected void loadSceneWithSpawn(String sceneName, String targetSpawn) {
        if (!SceneTransition.isInitialized()) {
            System.err.println("[Scene] SceneTransition not initialized - cannot load scene: " + sceneName);
            return;
        }

        System.out.println("[Scene] Loading scene '" + sceneName + "' with spawn point '" + targetSpawn + "'");
        SceneTransition.loadScene(sceneName, targetSpawn);
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
        if (this.initialized) {
            return;
        }
        this.initialized = true;
        this.viewportConfig = viewportConfig;
        // Create camera with viewport config
        this.camera = new GameCamera(viewportConfig, renderingConfig.getDefaultOrthographicSize(viewportConfig.getGameHeight()));
        GameCamera.setMainCamera(camera);

        onLoad();

        // Resolve all @ComponentReference annotations (hierarchy + key in single pass)
        for (GameObject go : gameObjects) {
            ComponentReferenceResolver.resolveAll(go);
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

    /**
     * Notifies all started, enabled components that the scene is about to be unloaded.
     * Called by SceneManager before destroy(). Exception in one component does not
     * prevent others from being notified.
     */
    public void notifyBeforeUnload() {
        for (GameObject go : new ArrayList<>(gameObjects)) {
            notifyBeforeUnloadRecursive(go);
        }
    }

    private void notifyBeforeUnloadRecursive(GameObject go) {
        for (Component comp : new ArrayList<>(go.getAllComponents())) {
            comp.triggerBeforeSceneUnload();
        }
        for (GameObject child : new ArrayList<>(go.getChildren())) {
            notifyBeforeUnloadRecursive(child);
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
        triggerDataMap.clear();
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
            ComponentReferenceResolver.resolveAll(obj);
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

    /**
     * Finds the first GameObject that has a component of the given type.
     * Searches recursively through all children.
     *
     * @param componentType the component class to search for
     * @return the owning GameObject, or null if not found
     */
    public <T extends Component> GameObject findGameObjectByComponent(Class<T> componentType) {
        for (GameObject go : gameObjects) {
            GameObject found = findGameObjectByComponentRecursive(go, componentType);
            if (found != null) return found;
        }
        return null;
    }

    private <T extends Component> GameObject findGameObjectByComponentRecursive(GameObject go, Class<T> componentType) {
        if (go.getComponent(componentType) != null) {
            return go;
        }
        for (GameObject child : go.getChildren()) {
            GameObject found = findGameObjectByComponentRecursive(child, componentType);
            if (found != null) return found;
        }
        return null;
    }

    public List<GameObject> getGameObjects() {
        return new ArrayList<>(gameObjects);
    }

    /**
     * Finds all components across all GameObjects (including children) that implement the given interface or extend the given class.
     * Useful for querying by interface (e.g. IPausable) or by concrete component type.
     *
     * @param type The interface or class to match against
     * @return List of matching components (empty list if none found, never null)
     */
    @SuppressWarnings("unchecked")
    public <T> List<T> getComponentsImplementing(Class<T> type) {
        List<T> result = new ArrayList<>();
        for (GameObject go : gameObjects) {
            collectComponentsImplementing(go, type, result);
        }
        return result;
    }

    private <T> void collectComponentsImplementing(GameObject go, Class<T> type, List<T> result) {
        for (Component component : go.getAllComponents()) {
            if (type.isInstance(component)) {
                result.add(type.cast(component));
            }
        }
        for (GameObject child : go.getChildren()) {
            collectComponentsImplementing(child, type, result);
        }
    }

    // ===========================================
    // Component Caching
    // ===========================================

    /**
     * Registers cached components from a GameObject and its children.
     * Skips disabled GameObjects — their components should not be in the cache.
     */
    public void registerCachedComponents(GameObject gameObject) {
        if (!gameObject.isEnabled()) return;

        // Register all Renderable components
        for (Component component : gameObject.getAllComponents()) {
            if (component instanceof Renderable renderable) {
                if (!renderables.contains(renderable)) {
                    renderables.add(renderable);
                    renderableSortDirty = true;
                }
            }
            // Register component with ComponentKeyRegistry if componentKey is set
            String key = component.getComponentKey();
            if (key != null && !key.isBlank()) {
                ComponentKeyRegistry.register(key, component);
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
        // Register component with ComponentKeyRegistry if componentKey is set
        String key = component.getComponentKey();
        if (key != null && !key.isBlank()) {
            ComponentKeyRegistry.register(key, component);
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
            // Unregister component from ComponentKeyRegistry if componentKey is set
            String key = component.getComponentKey();
            if (key != null && !key.isBlank()) {
                ComponentKeyRegistry.unregister(key);
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
        // Unregister component from ComponentKeyRegistry if componentKey is set
        String key = component.getComponentKey();
        if (key != null && !key.isBlank()) {
            ComponentKeyRegistry.unregister(key);
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