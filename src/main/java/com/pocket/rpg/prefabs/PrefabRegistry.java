package com.pocket.rpg.prefabs;

import com.pocket.rpg.core.GameObject;
import com.pocket.rpg.resources.AssetManager;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

/**
 * Registry for prefab factories.
 * <p>
 * Prefabs are templates for creating GameObjects with predefined components.
 * Each prefab is identified by a unique ID and associated with a factory function.
 * <p>
 * Usage:
 * <pre>
 * PrefabRegistry registry = new PrefabRegistry(assetManager);
 *
 * // Register prefabs
 * registry.register("Player", (assets, pos) -> {
 *     GameObject go = new GameObject("Player");
 *     go.getTransform().setPosition(pos);
 *     go.addComponent(new SpriteRenderer(assets.loadSprite("player.png")));
 *     go.addComponent(new PlayerController());
 *     return go;
 * });
 *
 * // Instantiate
 * GameObject player = registry.instantiate("Player", new Vector3f(5, 5, 0));
 * </pre>
 * <p>
 * For JSON-based prefabs (future enhancement), see PrefabLoader.
 */
public class PrefabRegistry {

    /**
     * Functional interface for prefab factories.
     * Takes AssetManager and position, returns configured GameObject.
     */
    @FunctionalInterface
    public interface PrefabFactory extends BiFunction<AssetManager, Vector3f, GameObject> {
    }

    private final AssetManager assetManager;
    private final Map<String, PrefabFactory> factories = new HashMap<>();
    private final Map<String, PrefabMetadata> metadata = new HashMap<>();

    /**
     * Creates a PrefabRegistry with the given AssetManager.
     */
    public PrefabRegistry(AssetManager assetManager) {
        this.assetManager = assetManager;
    }

    // ========================================================================
    // REGISTRATION
    // ========================================================================

    /**
     * Registers a prefab factory.
     *
     * @param prefabId Unique identifier for this prefab
     * @param factory  Factory function that creates the GameObject
     */
    public void register(String prefabId, PrefabFactory factory) {
        register(prefabId, factory, null);
    }

    /**
     * Registers a prefab factory with metadata.
     *
     * @param prefabId Unique identifier for this prefab
     * @param factory  Factory function
     * @param metadata Optional metadata for editor display
     */
    public void register(String prefabId, PrefabFactory factory, PrefabMetadata metadata) {
        if (factories.containsKey(prefabId)) {
            throw new IllegalArgumentException("Prefab already registered: " + prefabId);
        }
        factories.put(prefabId, factory);
        if (metadata != null) {
            this.metadata.put(prefabId, metadata);
        }
    }

    /**
     * Unregisters a prefab.
     */
    public void unregister(String prefabId) {
        factories.remove(prefabId);
        metadata.remove(prefabId);
    }

    // ========================================================================
    // INSTANTIATION
    // ========================================================================

    /**
     * Instantiates a prefab at the given position.
     *
     * @param prefabId Prefab identifier
     * @param position World position for the new GameObject
     * @return New GameObject instance
     * @throws IllegalArgumentException if prefab not found
     */
    public GameObject instantiate(String prefabId, Vector3f position) {
        PrefabFactory factory = factories.get(prefabId);
        if (factory == null) {
            throw new IllegalArgumentException("Unknown prefab: " + prefabId);
        }
        return factory.apply(assetManager, position);
    }

    /**
     * Instantiates a prefab with a custom name.
     *
     * @param prefabId Prefab identifier
     * @param name     Name for the new GameObject
     * @param position World position
     * @return New GameObject instance
     */
    public GameObject instantiate(String prefabId, String name, Vector3f position) {
        GameObject go = instantiate(prefabId, position);
        go.setName(name);
        return go;
    }

    /**
     * Instantiates a prefab at origin.
     */
    public GameObject instantiate(String prefabId) {
        return instantiate(prefabId, new Vector3f(0, 0, 0));
    }

    // ========================================================================
    // QUERIES
    // ========================================================================

    /**
     * Checks if a prefab is registered.
     */
    public boolean hasPrefab(String prefabId) {
        return factories.containsKey(prefabId);
    }

    /**
     * Returns all registered prefab IDs.
     */
    public Set<String> getPrefabIds() {
        return factories.keySet();
    }

    /**
     * Returns metadata for a prefab (for editor display).
     */
    public PrefabMetadata getMetadata(String prefabId) {
        return metadata.get(prefabId);
    }

    /**
     * Returns the number of registered prefabs.
     */
    public int size() {
        return factories.size();
    }

    // ========================================================================
    // METADATA
    // ========================================================================

    /**
     * Metadata for editor display and categorization.
     */
    public static class PrefabMetadata {
        private final String displayName;
        private final String category;
        private final String iconPath;
        private final String description;

        public PrefabMetadata(String displayName, String category) {
            this(displayName, category, null, null);
        }

        public PrefabMetadata(String displayName, String category, String iconPath, String description) {
            this.displayName = displayName;
            this.category = category;
            this.iconPath = iconPath;
            this.description = description;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getCategory() {
            return category;
        }

        public String getIconPath() {
            return iconPath;
        }

        public String getDescription() {
            return description;
        }
    }
}
