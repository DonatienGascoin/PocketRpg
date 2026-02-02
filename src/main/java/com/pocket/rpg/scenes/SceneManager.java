package com.pocket.rpg.scenes;

import com.pocket.rpg.components.GridMovement;
import com.pocket.rpg.components.interaction.CameraBoundsZone;
import com.pocket.rpg.components.interaction.SpawnPoint;
import com.pocket.rpg.components.core.PersistentEntity;
import com.pocket.rpg.config.RenderingConfig;
import com.pocket.rpg.core.GameObject;
import com.pocket.rpg.core.camera.GameCamera;
import com.pocket.rpg.core.window.ViewportConfig;
import com.pocket.rpg.editor.scene.RuntimeSceneLoader;
import com.pocket.rpg.save.SaveManager;
import com.pocket.rpg.serialization.GameObjectData;
import com.pocket.rpg.serialization.SceneData;
import com.pocket.rpg.ui.UIManager;
import lombok.Getter;
import lombok.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * SceneManager handles loading, unloading, and managing scenes.
 * Supports lifecycle listeners for scene events.
 * <p>
 * Can load scenes from:
 * <ul>
 *   <li>Registered Scene instances (for programmatic scenes)</li>
 *   <li>Scene files via RuntimeSceneLoader (for data-driven scenes)</li>
 * </ul>
 */
public class SceneManager {
    private final Map<String, Scene> scenes;
    private final List<SceneLifecycleListener> lifecycleListeners;

    @Getter
    private Scene currentScene;

    private final ViewportConfig viewportConfig;
    private final RenderingConfig renderingConfig;

    // File-based scene loading
    private RuntimeSceneLoader sceneLoader;
    private String scenesBasePath;

    // Spawn point to teleport to after loading a scene
    private String pendingSpawnId;

    public SceneManager(@NonNull ViewportConfig viewportConfig, @NonNull RenderingConfig renderingConfig) {
        this.scenes = new HashMap<>();
        this.lifecycleListeners = new ArrayList<>();
        this.viewportConfig = viewportConfig;
        this.renderingConfig = renderingConfig;
    }

    // ========================================================================
    // SCENE LOADER CONFIGURATION
    // ========================================================================

    /**
     * Configures file-based scene loading.
     *
     * @param loader   RuntimeSceneLoader for loading .scene files
     * @param basePath Base path for scene files (e.g., "gameData/scenes/")
     */
    public void setSceneLoader(RuntimeSceneLoader loader, String basePath) {
        this.sceneLoader = loader;
        this.scenesBasePath = basePath != null ? basePath : "";
    }

    // ========================================================================
    // SCENE REGISTRATION
    // ========================================================================

    /**
     * Registers a scene with the manager.
     * The scene can then be loaded by name.
     *
     * @param scene the scene to register
     */
    public void registerScene(Scene scene) {
        scenes.put(scene.getName(), scene);
    }

    // ========================================================================
    // LIFECYCLE LISTENERS
    // ========================================================================

    /**
     * Adds a lifecycle listener.
     *
     * @param listener the listener to add
     */
    public void addLifecycleListener(SceneLifecycleListener listener) {
        if (!lifecycleListeners.contains(listener)) {
            lifecycleListeners.add(listener);
        }
    }

    /**
     * Removes a lifecycle listener.
     *
     * @param listener the listener to remove
     */
    public void removeLifecycleListener(SceneLifecycleListener listener) {
        lifecycleListeners.remove(listener);
    }

    // ========================================================================
    // SCENE LOADING
    // ========================================================================

    /**
     * Loads a scene by name.
     * <p>
     * Lookup order:
     * <ol>
     *   <li>Registered scenes (via registerScene())</li>
     *   <li>Scene files (via sceneLoader if configured)</li>
     * </ol>
     *
     * @param sceneName name of the scene to load
     */
    public void loadScene(String sceneName) {
        // Clear UIManager before file-based loading, which registers
        // keys during addGameObject(). Without this, stale keys from the
        // current scene would trigger overwrite warnings.
        UIManager.clear();

        // First check registered scenes
        Scene scene = scenes.get(sceneName);

        // Try file-based loading if not found and loader configured
        if (scene == null && sceneLoader != null) {
            scene = loadSceneFromFile(sceneName);
        }

        if (scene == null) {
            System.err.println("Scene not found: " + sceneName);
            return;
        }

        loadScene(scene);
    }

    /**
     * Loads a scene by name, targeting a spawn point.
     *
     * @param sceneName name of the scene to load
     * @param spawnId   spawn point ID to teleport the player to after loading
     */
    public void loadScene(String sceneName, String spawnId) {
        this.pendingSpawnId = spawnId;
        loadScene(sceneName);
        // Clear pendingSpawnId in case loadScene(sceneName) returned early
        // (e.g., scene not found). loadScene(Scene) clears it on success.
        this.pendingSpawnId = null;
    }

    /**
     * Loads a scene directly.
     * Unloads the current scene if one is active.
     * Handles persistent entity snapshot/restore and spawn point teleportation.
     *
     * @param scene the scene to load
     */
    public void loadScene(Scene scene) {
        loadSceneInternal(scene, pendingSpawnId);
    }

    /**
     * Internal scene loading with explicit spawnId parameter.
     * Eliminates temporal coupling from the pendingSpawnId field.
     */
    private void loadSceneInternal(Scene scene, String spawnId) {
        // 1. Snapshot persistent entities from old scene
        List<GameObjectData> snapshots = Collections.emptyList();
        if (currentScene != null) {
            snapshots = snapshotPersistentEntities(currentScene);
            currentScene.destroy();
            fireSceneUnloaded(currentScene);
        }

        currentScene = scene;

        // Initialize the scene (creates camera with defaults)
        currentScene.initialize(viewportConfig, renderingConfig);

        // Apply camera data if this is a RuntimeScene with stored camera config
        if (scene instanceof RuntimeScene runtimeScene) {
            applyCameraData(runtimeScene);
        }

        // 2. Restore persistent entities
        restorePersistentEntities(currentScene, snapshots);

        // 3. Teleport player to spawn point
        if (spawnId != null && !spawnId.isEmpty()) {
            teleportPlayerToSpawn(currentScene, spawnId);
        }

        fireSceneLoaded(currentScene);

        System.out.println("Loaded scene: " + scene.getName());
    }

    /**
     * Attempts to load a scene from file.
     *
     * @param sceneName Name of the scene (without path/extension)
     * @return Loaded scene or null if not found
     */
    private Scene loadSceneFromFile(String sceneName) {
        String[] pathsToTry = {
                scenesBasePath + sceneName + ".scene",
                scenesBasePath + sceneName,
                sceneName + ".scene",
                sceneName
        };

        for (String path : pathsToTry) {
            try {
                RuntimeScene scene = sceneLoader.loadFromPath(path);
                if (scene != null) {
                    System.out.println("Loaded scene from file: " + path);
                    return scene;
                }
            } catch (Exception e) {
                // Try next path
            }
        }

        System.err.println("Could not find scene file for: " + sceneName);
        System.err.println("  Tried paths: " + String.join(", ", pathsToTry));
        return null;
    }

    /**
     * Applies stored camera data to the scene's camera.
     * Called after initialize() which creates the camera with defaults.
     */
    private void applyCameraData(RuntimeScene scene) {
        SceneData.CameraData cameraData = scene.getCameraData();
        if (cameraData == null || scene.getCamera() == null) {
            return;
        }

        // Apply position
        float[] pos = cameraData.getPosition();
        if (pos != null && pos.length >= 2) {
            scene.getCamera().setPosition(pos[0], pos[1]);
        }

        // Apply orthographic size (with guard for invalid values)
        float orthoSize = cameraData.getOrthographicSize();
        if (orthoSize > 0) {
            scene.getCamera().setOrthographicSize(orthoSize);
        }

        // Apply camera bounds via CameraBoundsZone lookup
        // Priority: saved state > scene default (initialBoundsId)
        String boundsId = SaveManager.getGlobal("camera", "activeBoundsId", "");
        if (boundsId.isEmpty()) {
            boundsId = cameraData.getInitialBoundsId();
        }
        if (boundsId != null && !boundsId.isEmpty()) {
            applyCameraBoundsZone(scene, boundsId);
        }

        System.out.println("Applied camera data: orthoSize=" + orthoSize);
    }

    /**
     * Searches the scene for a CameraBoundsZone with the given boundsId and applies it.
     */
    private void applyCameraBoundsZone(Scene scene, String boundsId) {
        for (GameObject obj : scene.getGameObjects()) {
            CameraBoundsZone zone = obj.getComponent(CameraBoundsZone.class);
            if (zone != null && boundsId.equals(zone.getBoundsId())) {
                zone.applyBounds(scene.getCamera());
                System.out.println("Applied camera bounds zone: " + boundsId);
                return;
            }
        }
        System.err.println("CameraBoundsZone not found: " + boundsId);
    }

    // ========================================================================
    // PERSISTENT ENTITY MANAGEMENT
    // ========================================================================

    /**
     * Snapshots all persistent entities in the scene.
     *
     * @param scene the scene to snapshot from
     * @return list of snapshots (empty if no persistent entities)
     */
    private List<GameObjectData> snapshotPersistentEntities(Scene scene) {
        List<GameObjectData> snapshots = new ArrayList<>();
        for (GameObject obj : scene.getGameObjects()) {
            collectPersistentSnapshots(obj, snapshots);
        }
        if (!snapshots.isEmpty()) {
            System.out.println("[SceneManager] Snapshotted " + snapshots.size() + " persistent entities");
        }
        return snapshots;
    }

    /**
     * Recursively collects persistent entity snapshots from an entity and its children.
     */
    private void collectPersistentSnapshots(GameObject obj, List<GameObjectData> snapshots) {
        PersistentEntity pe = obj.getComponent(PersistentEntity.class);
        if (pe != null) {
            GameObjectData snapshot = PersistentEntitySnapshot.snapshot(obj);
            if (snapshot != null) {
                snapshots.add(snapshot);
            }
        }
        for (GameObject child : obj.getChildren()) {
            collectPersistentSnapshots(child, snapshots);
        }
    }

    /**
     * Restores persistent entities in the new scene from snapshots.
     * <p>
     * For each snapshot, finds a matching entity by PersistentEntity.entityTag.
     * If found, applies the snapshot state. If not found, creates a new entity
     * from the snapshot and adds it to the scene.
     *
     * @param scene     the new scene
     * @param snapshots persistent entity snapshots from the old scene
     */
    private void restorePersistentEntities(Scene scene, List<GameObjectData> snapshots) {
        if (snapshots.isEmpty()) {
            return;
        }

        // Warn on duplicate entityTag values
        Set<String> seenTags = new HashSet<>();
        for (GameObjectData snapshot : snapshots) {
            String tag = snapshot.getTag();
            if (tag != null && !tag.isEmpty() && !seenTags.add(tag)) {
                System.err.println("[SceneManager] WARNING: Duplicate entityTag '" + tag
                        + "' found in persistent entity snapshots. Only the first match will be restored correctly.");
            }
        }

        for (GameObjectData snapshot : snapshots) {
            String entityTag = snapshot.getTag();
            if (entityTag == null || entityTag.isEmpty()) {
                continue;
            }

            GameObject existing = findPersistentEntity(scene, entityTag);
            if (existing != null) {
                // Apply snapshot state to existing entity
                PersistentEntitySnapshot.applySnapshot(snapshot, existing);
                System.out.println("[SceneManager] Restored persistent entity '" + entityTag
                        + "' to existing entity: " + existing.getName());
            } else {
                // Create new entity from snapshot
                GameObject created = PersistentEntitySnapshot.createFromSnapshot(snapshot);
                if (created != null) {
                    scene.addGameObject(created);
                    System.out.println("[SceneManager] Created persistent entity '" + entityTag
                            + "' from snapshot: " + created.getName());
                }
            }
        }
    }

    /**
     * Finds a persistent entity by entityTag in the scene.
     */
    private GameObject findPersistentEntity(Scene scene, String entityTag) {
        for (GameObject obj : scene.getGameObjects()) {
            GameObject found = findPersistentEntityRecursive(obj, entityTag);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /**
     * Recursively searches for a persistent entity by entityTag.
     */
    private GameObject findPersistentEntityRecursive(GameObject obj, String entityTag) {
        PersistentEntity pe = obj.getComponent(PersistentEntity.class);
        if (pe != null && entityTag.equals(pe.getEntityTag())) {
            return obj;
        }
        for (GameObject child : obj.getChildren()) {
            GameObject found = findPersistentEntityRecursive(child, entityTag);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /**
     * Teleports the player persistent entity to a spawn point.
     * Applies facing direction and camera bounds from the spawn point.
     *
     * @param scene   the scene containing the spawn point
     * @param spawnId the spawn point ID
     */
    private void teleportPlayerToSpawn(Scene scene, String spawnId) {
        // Find the player entity (persistent entity with tag "Player")
        GameObject player = findPersistentEntity(scene, "Player");
        if (player == null) {
            System.err.println("[SceneManager] No persistent entity with tag 'Player' found for spawn teleport");
            return;
        }

        // Find the spawn point (recursively)
        SpawnPoint spawn = findSpawnPointRecursive(scene, spawnId);

        if (spawn == null) {
            System.err.println("[SceneManager] Spawn point not found: " + spawnId);
            return;
        }

        // Teleport
        scene.teleportToSpawn(player, spawnId);

        // Apply facing direction
        GridMovement gridMovement = player.getComponent(GridMovement.class);
        if (gridMovement != null && spawn.getFacingDirection() != null) {
            gridMovement.setFacingDirection(spawn.getFacingDirection());
        }

        // Apply camera bounds from spawn point
        if (scene.getCamera() != null) {
            spawn.applyCameraBounds(scene.getCamera());
        }

        System.out.println("[SceneManager] Teleported player to spawn: " + spawnId);
    }

    /**
     * Recursively searches a scene for a SpawnPoint with the given ID.
     */
    private SpawnPoint findSpawnPointRecursive(Scene scene, String spawnId) {
        for (GameObject obj : scene.getGameObjects()) {
            SpawnPoint found = findSpawnPointInHierarchy(obj, spawnId);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private SpawnPoint findSpawnPointInHierarchy(GameObject obj, String spawnId) {
        SpawnPoint sp = obj.getComponent(SpawnPoint.class);
        if (sp != null && spawnId.equals(sp.getSpawnId())) {
            return sp;
        }
        for (GameObject child : obj.getChildren()) {
            SpawnPoint found = findSpawnPointInHierarchy(child, spawnId);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    // ========================================================================
    // UPDATE
    // ========================================================================

    /**
     * Updates the current scene.
     * Called every frame.
     *
     * @param deltaTime time since last frame
     */
    public void update(float deltaTime) {
        if (currentScene != null) {
            currentScene.update(deltaTime);
        }
    }

    // ========================================================================
    // CLEANUP
    // ========================================================================

    /**
     * Destroys the scene manager and cleans up resources.
     */
    public void destroy() {
        if (currentScene != null) {
            currentScene.destroy();
            fireSceneUnloaded(currentScene);
        }
        // Clear UIManager so the next session starts clean.
        // Note: UiKeyRefResolver.pendingKeys is NOT cleared here — it uses
        // identity hash codes, so entries for destroyed components are orphaned
        // and harmless. Clearing would wipe editor component keys too.
        UIManager.clear();

        GameCamera.setMainCamera(null);
        scenes.clear();
        lifecycleListeners.clear();
    }

    // ========================================================================
    // EVENTS
    // ========================================================================

    /**
     * Fires the scene loaded event to all listeners.
     *
     * @param scene the scene that was loaded
     */
    private void fireSceneLoaded(Scene scene) {
        for (SceneLifecycleListener listener : lifecycleListeners) {
            listener.onSceneLoaded(scene);
        }
    }

    /**
     * Fires the scene unloaded event to all listeners.
     *
     * @param scene the scene that was unloaded
     */
    private void fireSceneUnloaded(Scene scene) {
        for (SceneLifecycleListener listener : lifecycleListeners) {
            listener.onSceneUnloaded(scene);
        }
    }
}
