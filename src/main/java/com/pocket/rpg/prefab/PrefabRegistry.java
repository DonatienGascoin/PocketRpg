package com.pocket.rpg.prefab;

import com.pocket.rpg.prefab.prefabs.ChestPrefab;
import com.pocket.rpg.prefab.prefabs.PlayerPrefab;
import com.pocket.rpg.rendering.Sprite;
import com.pocket.rpg.resources.Assets;
import lombok.Getter;
import lombok.Setter;

import java.util.Collection;
import java.util.LinkedHashMap;
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

    private static PrefabRegistry instance;

    private final Map<String, Prefab> prefabs = new LinkedHashMap<>();
    /**
     * -- GETTER --
     * Gets the default preview sprite used when prefabs don't provide one.
     * -- SETTER --
     * Sets a custom default preview sprite.
     */
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
     * Loads the default preview sprite and can register built-in prefabs.
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
            registry.defaultPreviewSprite = Assets.load("editor/prefabDefault.png");
        } catch (Exception e) {
            System.out.println("Default prefab preview sprite not found, using null");
            registry.defaultPreviewSprite = null;
        }

        registry.registerPreMadePrefabs();

        registry.initialized = true;
        System.out.println("PrefabRegistry initialized");
    }

    private void registerPreMadePrefabs() {
        register(new ChestPrefab());
        register(new PlayerPrefab());
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
