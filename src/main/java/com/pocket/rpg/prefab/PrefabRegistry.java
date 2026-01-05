package com.pocket.rpg.prefab;

import com.pocket.rpg.prefab.prefabs.ChestPrefab;
import com.pocket.rpg.prefab.prefabs.PlayerPrefab;
import com.pocket.rpg.rendering.Sprite;
import com.pocket.rpg.resources.Assets;
import lombok.Getter;
import lombok.Setter;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registry for all available prefabs in the game.
 * <p>
 * Singleton that manages prefab registration and lookup.
 * Prefabs must be registered before they can be used in the editor
 * or instantiated at runtime.
 * <p>
 * Usage:
 * <pre>
 * // During initialization
 * PrefabRegistry.initialize();
 * PrefabRegistry.getInstance().register(new PlayerPrefab());
 *
 * // Later, to instantiate
 * Prefab prefab = PrefabRegistry.getInstance().getPrefab("player");
 * GameObject player = prefab.instantiate(position, overrides);
 * </pre>
 */
public class PrefabRegistry {

    private static final String PREFABS_DIRECTORY = "prefabs/";
    private static PrefabRegistry instance;

    private final Map<String, Prefab> prefabs = new LinkedHashMap<>();

    @Setter
    @Getter
    private Sprite defaultPreviewSprite;
    private boolean initialized = false;

    private PrefabRegistry() {
        // Private constructor for singleton
    }

    /**
     * Gets the singleton instance, creating it if necessary.
     */
    public static PrefabRegistry getInstance() {
        if (instance == null) {
            instance = new PrefabRegistry();
        }
        return instance;
    }

    /**
     * Initializes the registry.
     * <p>
     * Loads the default preview sprite and registers built-in prefabs.
     * Should be called once during application startup.
     * <p>
     * <b>IMPORTANT</b>: Assets must be initialized already
     */
    public static void initialize() {
        PrefabRegistry registry = getInstance();
        if (registry.initialized) {
            return;
        }

        // Try to load default preview sprite
        try {
            registry.defaultPreviewSprite = Assets.load("editor/prefabDefault.png"); // TODO: Can't work: always appending gameData/assets/
        } catch (Exception e) {
            System.out.println("Default prefab preview sprite not found, using null");
            registry.defaultPreviewSprite = null;
        }

        // Register Java prefabs
        registry.registerBuiltInPrefabs();

        // Scan and load JSON prefabs
        registry.loadJsonPrefabs();

        registry.initialized = true;
        System.out.println("PrefabRegistry initialized with " + registry.prefabs.size() + " prefabs");
    }

    private void registerBuiltInPrefabs() {
        register(new ChestPrefab());
        register(new PlayerPrefab());
    }

    /**
     * Scans the prefabs directory and loads all JSON prefabs.
     */
    private void loadJsonPrefabs() {
        try {
            List<String> prefabPaths = Assets.getContext().scanByType(JsonPrefab.class);

            for (String path : prefabPaths) {
                try {
                    JsonPrefab prefab = Assets.load(path, JsonPrefab.class);

                    if (prefab != null && !prefabs.containsKey(prefab.getId())) {
                        prefabs.put(prefab.getId(), prefab);
                        System.out.println("Loaded JSON prefab: " + prefab.getId() + " from " + path);
                    }
                } catch (Exception e) {
                    System.err.println("Failed to load prefab " + path + ": " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to scan for JSON prefabs: " + e.getMessage());
        }
    }

    /**
     * Saves a JSON prefab to disk and registers it.
     *
     * @param prefab   The prefab to save
     * @param filename The filename (without path), e.g., "my_prefab.prefab.json"
     */
    public void saveJsonPrefab(JsonPrefab prefab, String filename) {
        String path = PREFABS_DIRECTORY + filename;

        try {
            Assets.persist(prefab, path);

            // Register or update
            prefabs.put(prefab.getId(), prefab);

            System.out.println("Saved JSON prefab: " + prefab.getId() + " to " + path);
        } catch (Exception e) {
            throw new RuntimeException("Failed to save prefab: " + e.getMessage(), e);
        }
    }

    /**
     * Checks if a prefab is a JSON prefab (vs Java prefab).
     */
    public boolean isJsonPrefab(String id) {
        Prefab prefab = prefabs.get(id);
        return prefab instanceof JsonPrefab;
    }

    /**
     * Gets a JSON prefab by ID (cast helper).
     */
    public JsonPrefab getJsonPrefab(String id) {
        Prefab prefab = prefabs.get(id);
        return prefab instanceof JsonPrefab ? (JsonPrefab) prefab : null;
    }

    /**
     * Registers a prefab.
     *
     * @param prefab The prefab to register
     * @throws IllegalArgumentException if a prefab with the same ID already exists
     */
    public void register(Prefab prefab) {
        if (prefab == null) {
            throw new IllegalArgumentException("Prefab cannot be null");
        }

        String id = prefab.getId();
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Prefab ID cannot be null or blank");
        }

        if (prefabs.containsKey(id)) {
            throw new IllegalArgumentException("Prefab already registered: " + id);
        }

        prefabs.put(id, prefab);
        System.out.println("Registered prefab: " + id + " (" + prefab.getDisplayName() + ")");
    }

    /**
     * Unregisters a prefab by ID.
     *
     * @param id The prefab ID to unregister
     * @return true if the prefab was removed
     */
    public boolean unregister(String id) {
        return prefabs.remove(id) != null;
    }

    /**
     * Gets a prefab by ID.
     *
     * @param id The prefab ID
     * @return The prefab, or null if not found
     */
    public Prefab getPrefab(String id) {
        return prefabs.get(id);
    }

    /**
     * Checks if a prefab is registered.
     *
     * @param id The prefab ID
     * @return true if registered
     */
    public boolean hasPrefab(String id) {
        return prefabs.containsKey(id);
    }

    /**
     * Gets all registered prefab IDs.
     */
    public Collection<String> getRegisteredIds() {
        return prefabs.keySet();
    }

    /**
     * Gets all registered prefabs.
     */
    public Collection<Prefab> getAllPrefabs() {
        return prefabs.values();
    }

    /**
     * Gets the number of registered prefabs.
     */
    public int getPrefabCount() {
        return prefabs.size();
    }

    /**
     * Gets the preview sprite for a prefab with fallback chain:
     * 1. Prefab's custom preview sprite
     * 2. Default placeholder sprite
     *
     * @param prefabId The prefab ID
     * @return Preview sprite (may be null if no default available)
     */
    public Sprite getPreviewSprite(String prefabId) {
        Prefab prefab = prefabs.get(prefabId);
        if (prefab == null) {
            return defaultPreviewSprite;
        }

        Sprite preview = prefab.getPreviewSprite();
        return preview != null ? preview : defaultPreviewSprite;
    }

    /**
     * Clears all registered prefabs.
     */
    public void clear() {
        prefabs.clear();
    }

    /**
     * Destroys the registry instance.
     */
    public static void destroy() {
        if (instance != null) {
            instance.prefabs.clear();
            instance.defaultPreviewSprite = null;
            instance.initialized = false;
            instance = null;
            System.out.println("PrefabRegistry destroyed");
        }
    }
}
