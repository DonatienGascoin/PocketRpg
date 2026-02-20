package com.pocket.rpg.save;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.core.Transform;
import com.pocket.rpg.core.GameObject;
import com.pocket.rpg.scenes.Scene;
import com.pocket.rpg.scenes.SceneLifecycleListener;
import com.pocket.rpg.scenes.SceneManager;
import com.pocket.rpg.serialization.Serializer;
import lombok.Getter;
import org.joml.Vector3f;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Static API for save/load operations.
 * <p>
 * Design principles:
 * - Global state persists across scene transitions
 * - Per-scene state captures deltas from initial scene files
 * - Only entities with PersistentId are tracked
 * - Only components implementing ISaveable are saved
 */
public final class SaveManager {

    private static SaveManager instance;

    // ========================================================================
    // CONFIGURATION
    // ========================================================================

    /**
     * Base directory for save files.
     * Default: {APPDATA}/PocketRpg/saves/ on Windows
     *          ~/.pocketrpg/saves/ on Unix
     */
    @Getter
    private final Path savesDirectory;

    /**
     * Current loaded save data (null if no save loaded).
     */
    @Getter
    private SaveData currentSave;

    // ========================================================================
    // RUNTIME STATE
    // ========================================================================

    /**
     * All registered entities with PersistentId.
     * Key: persistentId
     */
    private final Map<String, PersistentId> registeredEntities = new HashMap<>();

    /**
     * Current scene name (set by onPostSceneInitialize).
     */
    private String currentSceneName;

    /**
     * Whether saved states have been applied for the current scene.
     * Used to determine if late-registered entities should get state applied immediately.
     */
    private boolean savedStatesApplied = false;

    /**
     * Play time accumulator (seconds) for current session.
     */
    private float sessionPlayTime = 0;

    // ========================================================================
    // INITIALIZATION
    // ========================================================================

    private SaveManager(Path savesDirectory) {
        this.savesDirectory = savesDirectory;
        ensureDirectoryExists();
    }

    /**
     * Initializes the save system.
     * Should be called once at game startup.
     *
     * @param sceneManager SceneManager to hook into
     */
    public static void initialize(SceneManager sceneManager) {
        initialize(sceneManager, getDefaultSavesDirectory());
    }

    /**
     * Initializes with custom save directory.
     */
    public static void initialize(SceneManager sceneManager, Path savesDirectory) {
        instance = new SaveManager(savesDirectory);

        // Hook into scene lifecycle
        sceneManager.addLifecycleListener(new SceneLifecycleListener() {
            @Override
            public void onSceneLoaded(Scene scene) {
                // No-op: currentSceneName set in onPostSceneInitialize,
                // registeredEntities cleared in onSceneUnloaded
            }

            @Override
            public void onSceneUnloaded(Scene scene) {
                instance.registeredEntities.clear();
                instance.savedStatesApplied = false;
                System.out.println("[SaveManager] Scene unloaded: " + scene.getName());
            }

            @Override
            public void onPostSceneInitialize(Scene scene) {
                instance.currentSceneName = scene.getName();
                instance.applyAllSavedStates();
                instance.savedStatesApplied = true;
                System.out.println("[SaveManager] Scene initialized: " + scene.getName());
            }
        });

        System.out.println("[SaveManager] Initialized: " + savesDirectory);
    }

    /**
     * Gets default saves directory based on OS.
     */
    private static Path getDefaultSavesDirectory() {
        String os = System.getProperty("os.name").toLowerCase();

        if (os.contains("win")) {
            // Windows: %APPDATA%/PocketRpg/saves/
            String appData = System.getenv("APPDATA");
            if (appData != null) {
                return Paths.get(appData, "PocketRpg", "saves");
            }
        }

        // Unix/Mac/Fallback: ~/.pocketrpg/saves/
        return Paths.get(System.getProperty("user.home"), ".pocketrpg", "saves");
    }

    private void ensureDirectoryExists() {
        try {
            Files.createDirectories(savesDirectory);
        } catch (IOException e) {
            System.err.println("[SaveManager] Failed to create saves directory: " + e.getMessage());
        }
    }

    /**
     * Check if SaveManager has been initialized.
     */
    public static boolean isInitialized() {
        return instance != null;
    }

    // ========================================================================
    // SAVE OPERATIONS
    // ========================================================================

    /**
     * Saves current game state to the specified slot.
     *
     * @param slotName Save slot name (e.g., "slot1", "autosave")
     * @return true if save succeeded
     */
    public static boolean save(String slotName) {
        return save(slotName, "Save " + slotName);
    }

    /**
     * Saves with custom display name.
     */
    public static boolean save(String slotName, String displayName) {
        if (instance == null) {
            System.err.println("[SaveManager] Not initialized");
            return false;
        }
        return instance.doSave(slotName, displayName);
    }

    private boolean doSave(String slotName, String displayName) {
        try {
            // Create or update save data
            if (currentSave == null) {
                currentSave = new SaveData();
            }

            currentSave.setDisplayName(displayName);
            currentSave.setTimestamp(System.currentTimeMillis());
            currentSave.setPlayTime(currentSave.getPlayTime() + sessionPlayTime);
            currentSave.setCurrentScene(currentSceneName);

            // Capture current scene state
            if (currentSceneName != null) {
                SavedSceneState sceneState = captureCurrentSceneState();
                currentSave.getSceneStates().put(currentSceneName, sceneState);
            }

            // Write to file
            Path savePath = savesDirectory.resolve(slotName + ".save");
            String json = Serializer.toPrettyJson(currentSave);
            Files.writeString(savePath, json);

            sessionPlayTime = 0;  // Reset session timer

            System.out.println("[SaveManager] Saved to: " + savePath);
            return true;

        } catch (Exception e) {
            System.err.println("[SaveManager] Failed to save: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Captures state of all saveable entities in current scene.
     */
    private SavedSceneState captureCurrentSceneState() {
        SavedSceneState state = new SavedSceneState(currentSceneName);

        for (PersistentId pid : registeredEntities.values()) {
            GameObject go = pid.getGameObject();
            if (go == null) continue;

            SavedEntityState entityState = captureEntityState(pid, go);
            if (entityState != null) {
                state.getModifiedEntities().put(pid.getId(), entityState);
            }
        }

        return state;
    }

    /**
     * Captures state of a single entity.
     */
    private SavedEntityState captureEntityState(PersistentId pid, GameObject go) {
        SavedEntityState state = new SavedEntityState(pid.getId());

        // Capture position
        Transform transform = go.getTransform();
        Vector3f pos = transform.getPosition();
        state.setPosition(new float[]{pos.x, pos.y, pos.z});

        // Capture active state if disabled
        if (!go.isEnabled()) {
            state.setActive(false);
        }

        // Capture ISaveable component states
        for (Component comp : go.getAllComponents()) {
            if (comp instanceof ISaveable saveable) {
                if (saveable.hasSaveableState()) {
                    Map<String, Object> compState = saveable.getSaveState();
                    if (compState != null && !compState.isEmpty()) {
                        state.getComponentStates().put(
                                comp.getClass().getName(),
                                compState
                        );
                    }
                }
            }
        }

        // Only return if we have meaningful state to save
        // (always save position for now, can optimize later)
        return state;
    }

    // ========================================================================
    // LOAD OPERATIONS
    // ========================================================================

    /**
     * Loads a save file and prepares for scene loading.
     * Does NOT immediately load the scene - call SceneManager.loadScene() after.
     *
     * @param slotName Save slot to load
     * @return true if load succeeded
     */
    public static boolean load(String slotName) {
        if (instance == null) {
            System.err.println("[SaveManager] Not initialized");
            return false;
        }
        return instance.doLoad(slotName);
    }

    private boolean doLoad(String slotName) {
        try {
            Path savePath = savesDirectory.resolve(slotName + ".save");

            if (!Files.exists(savePath)) {
                System.err.println("[SaveManager] Save file not found: " + savePath);
                return false;
            }

            String json = Files.readString(savePath);
            currentSave = Serializer.fromJson(json, SaveData.class);

            if (currentSave.needsMigration()) {
                currentSave.migrate();
                System.out.println("[SaveManager] Migrated save from older version");
            }

            sessionPlayTime = 0;

            System.out.println("[SaveManager] Loaded: " + currentSave.getDisplayName());
            System.out.println("[SaveManager] Scene: " + currentSave.getCurrentScene());

            return true;

        } catch (Exception e) {
            System.err.println("[SaveManager] Failed to load: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Gets the scene name from current loaded save.
     */
    public static String getSavedSceneName() {
        if (instance == null || instance.currentSave == null) {
            return null;
        }
        return instance.currentSave.getCurrentScene();
    }

    // ========================================================================
    // SCENE LIFECYCLE
    // ========================================================================

    /**
     * Applies saved state to all registered entities.
     * Called from onPostSceneInitialize, after all onStart() calls complete.
     */
    private void applyAllSavedStates() {
        if (currentSave == null || currentSceneName == null) return;

        // Snapshot: applySavedStateToEntity can destroy entities,
        // which triggers PersistentId.onDestroy() → unregisterEntity(),
        // modifying registeredEntities during iteration
        for (PersistentId pid : new ArrayList<>(registeredEntities.values())) {
            applySavedStateToEntity(pid);
        }
    }

    /**
     * Called by PersistentId.onStart() to register entities.
     * State application is deferred to onPostSceneInitialize, unless
     * the entity is registered after scene init (dynamic spawn).
     */
    static void registerEntity(PersistentId pid) {
        if (instance == null) return;

        String id = pid.getId();
        if (id == null || id.isEmpty()) {
            // Generate ID if not set
            id = PersistentId.generateId();
            pid.setId(id);
        }

        instance.registeredEntities.put(id, pid);

        // If saved states have already been applied (entity added after scene init),
        // apply immediately — safe because all onStart() calls completed before
        // this entity was added to the scene
        if (instance.savedStatesApplied) {
            instance.applySavedStateToEntity(pid);
        }
    }

    /**
     * Called by PersistentId.onDestroy() to unregister.
     */
    static void unregisterEntity(PersistentId pid) {
        if (instance == null) return;
        instance.registeredEntities.remove(pid.getId());
    }

    /**
     * Applies saved state to a newly registered entity.
     */
    private void applySavedStateToEntity(PersistentId pid) {
        if (currentSave == null || currentSceneName == null) return;

        SavedSceneState sceneState = currentSave.getSceneStates().get(currentSceneName);
        if (sceneState == null) return;

        // Check if entity should be destroyed
        if (sceneState.getDestroyedEntities().contains(pid.getId())) {
            // Mark for destruction - will be destroyed after onStart completes
            GameObject go = pid.getGameObject();
            if (go != null && go.getScene() != null) {
                go.getScene().removeGameObject(go);
                System.out.println("[SaveManager] Destroyed saved-as-destroyed entity: " + pid.getId());
            }
            return;
        }

        SavedEntityState entityState = sceneState.getModifiedEntities().get(pid.getId());
        if (entityState == null) return;

        GameObject go = pid.getGameObject();
        if (go == null) return;

        // Apply position
        if (entityState.getPosition() != null) {
            float[] pos = entityState.getPosition();
            go.getTransform().setPosition(pos[0], pos[1], pos.length > 2 ? pos[2] : 0);
        }

        // Apply active state
        if (entityState.getActive() != null) {
            go.setEnabled(entityState.getActive());
        }

        // Apply component states
        for (Map.Entry<String, Map<String, Object>> entry : entityState.getComponentStates().entrySet()) {
            String componentType = entry.getKey();
            Map<String, Object> compState = entry.getValue();

            // Find matching component
            for (Component comp : go.getAllComponents()) {
                if (comp.getClass().getName().equals(componentType) && comp instanceof ISaveable saveable) {
                    saveable.loadSaveState(compState);
                    break;
                }
            }
        }

        System.out.println("[SaveManager] Restored state for: " + pid.getId());
    }

    // ========================================================================
    // DESTROYED ENTITIES
    // ========================================================================

    /**
     * Marks an entity as destroyed in the current scene's save state.
     * Call this when permanently destroying a saveable entity.
     *
     * @param persistentId The ID of the destroyed entity
     */
    public static void markEntityDestroyed(String persistentId) {
        if (instance == null || instance.currentSave == null || instance.currentSceneName == null) {
            return;
        }

        SavedSceneState sceneState = instance.currentSave.getSceneStates()
                .computeIfAbsent(instance.currentSceneName, SavedSceneState::new);
        sceneState.getDestroyedEntities().add(persistentId);
        sceneState.getModifiedEntities().remove(persistentId);
    }

    // ========================================================================
    // GLOBAL STATE
    // ========================================================================

    /**
     * Sets a global persistent value.
     * Global state survives scene transitions.
     *
     * @param namespace Category (e.g., "player", "settings")
     * @param key       Key within namespace
     * @param value     Value to store
     */
    public static void setGlobal(String namespace, String key, Object value) {
        if (instance == null) {
            System.err.println("[SaveManager] Not initialized - cannot set global state");
            return;
        }
        if (instance.currentSave == null) {
            instance.currentSave = new SaveData();
        }

        instance.currentSave.getGlobalState()
                .computeIfAbsent(namespace, k -> new HashMap<>())
                .put(key, value);
    }

    /**
     * Gets a global persistent value.
     */
    @SuppressWarnings("unchecked")
    public static <T> T getGlobal(String namespace, String key, T defaultValue) {
        if (instance == null || instance.currentSave == null) {
            return defaultValue;
        }

        Map<String, Object> ns = instance.currentSave.getGlobalState().get(namespace);
        if (ns == null) return defaultValue;

        Object value = ns.get(key);
        if (value == null) return defaultValue;

        // Handle number type conversion
        if (defaultValue instanceof Integer && value instanceof Number) {
            return (T) Integer.valueOf(((Number) value).intValue());
        }
        if (defaultValue instanceof Float && value instanceof Number) {
            return (T) Float.valueOf(((Number) value).floatValue());
        }
        if (defaultValue instanceof Double && value instanceof Number) {
            return (T) Double.valueOf(((Number) value).doubleValue());
        }
        if (defaultValue instanceof Long && value instanceof Number) {
            return (T) Long.valueOf(((Number) value).longValue());
        }

        return (T) value;
    }

    /**
     * Check if a global key exists.
     */
    public static boolean hasGlobal(String namespace, String key) {
        if (instance == null || instance.currentSave == null) {
            return false;
        }
        Map<String, Object> ns = instance.currentSave.getGlobalState().get(namespace);
        return ns != null && ns.containsKey(key);
    }

    /**
     * Remove a global key.
     */
    public static void removeGlobal(String namespace, String key) {
        if (instance == null || instance.currentSave == null) {
            return;
        }
        Map<String, Object> ns = instance.currentSave.getGlobalState().get(namespace);
        if (ns != null) {
            ns.remove(key);
        }
    }

    // ========================================================================
    // SCENE FLAGS
    // ========================================================================

    /**
     * Sets a flag for the current scene.
     */
    public static void setSceneFlag(String key, Object value) {
        if (instance == null || instance.currentSceneName == null) {
            return;
        }
        if (instance.currentSave == null) {
            instance.currentSave = new SaveData();
        }

        SavedSceneState sceneState = instance.currentSave.getSceneStates()
                .computeIfAbsent(instance.currentSceneName, SavedSceneState::new);
        sceneState.getSceneFlags().put(key, value);
    }

    /**
     * Gets a flag for the current scene.
     */
    @SuppressWarnings("unchecked")
    public static <T> T getSceneFlag(String key, T defaultValue) {
        if (instance == null || instance.currentSave == null || instance.currentSceneName == null) {
            return defaultValue;
        }

        SavedSceneState sceneState = instance.currentSave.getSceneStates().get(instance.currentSceneName);
        if (sceneState == null) return defaultValue;

        Object value = sceneState.getSceneFlags().get(key);
        if (value == null) return defaultValue;

        return (T) value;
    }

    // ========================================================================
    // UTILITY
    // ========================================================================

    /**
     * Lists available save slots.
     */
    public static List<SaveSlotInfo> listSaves() {
        if (instance == null) return List.of();

        List<SaveSlotInfo> saves = new ArrayList<>();

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(
                instance.savesDirectory, "*.save")) {

            for (Path path : stream) {
                try {
                    String json = Files.readString(path);
                    SaveData data = Serializer.fromJson(json, SaveData.class);

                    String slotName = path.getFileName().toString()
                            .replace(".save", "");

                    saves.add(new SaveSlotInfo(
                            slotName,
                            data.getDisplayName(),
                            data.getTimestamp(),
                            data.getPlayTime(),
                            data.getCurrentScene()
                    ));
                } catch (Exception e) {
                    System.err.println("[SaveManager] Failed to read save: " + path);
                }
            }

        } catch (IOException e) {
            System.err.println("[SaveManager] Failed to list saves: " + e.getMessage());
        }

        saves.sort((a, b) -> Long.compare(b.timestamp(), a.timestamp()));
        return saves;
    }

    /**
     * Deletes a save slot.
     */
    public static boolean deleteSave(String slotName) {
        if (instance == null) return false;

        try {
            Path savePath = instance.savesDirectory.resolve(slotName + ".save");
            return Files.deleteIfExists(savePath);
        } catch (IOException e) {
            System.err.println("[SaveManager] Failed to delete save: " + e.getMessage());
            return false;
        }
    }

    /**
     * Checks if a save slot exists.
     */
    public static boolean saveExists(String slotName) {
        if (instance == null) return false;
        return Files.exists(instance.savesDirectory.resolve(slotName + ".save"));
    }

    /**
     * Starts a new game (clears current save).
     */
    public static void newGame() {
        if (instance != null) {
            instance.currentSave = new SaveData();
            instance.sessionPlayTime = 0;
            System.out.println("[SaveManager] New game started");
        }
    }

    /**
     * Updates play time (call once per frame).
     */
    public static void updatePlayTime(float deltaTime) {
        if (instance != null) {
            instance.sessionPlayTime += deltaTime;
        }
    }

    /**
     * Gets current session play time.
     */
    public static float getSessionPlayTime() {
        return instance != null ? instance.sessionPlayTime : 0;
    }

    /**
     * Gets total play time from current save.
     */
    public static float getTotalPlayTime() {
        if (instance == null || instance.currentSave == null) {
            return 0;
        }
        return instance.currentSave.getPlayTime() + instance.sessionPlayTime;
    }

    /**
     * Checks if a save is currently loaded.
     */
    public static boolean hasSaveLoaded() {
        return instance != null && instance.currentSave != null;
    }

    /**
     * Gets the saves directory path.
     */
    public static Path getSavesDirectory() {
        return instance != null ? instance.savesDirectory : null;
    }
}
