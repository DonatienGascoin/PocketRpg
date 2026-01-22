package com.pocket.rpg.save;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.HideInInspector;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * Marks a GameObject as saveable with a stable persistent ID.
 * <p>
 * Add this component to any entity that needs to persist state across saves:
 * - Player character
 * - Chests that can be opened
 * - NPCs with dialogue state
 * - Enemies that stay dead
 * - Collectibles that don't respawn
 * <p>
 * The ID is stored in the scene file and preserved across save/load cycles.
 */
public class PersistentId extends Component {

    /**
     * Stable identifier for this entity.
     * <p>
     * Can be:
     * - Set in editor (e.g., "player", "chest_01")
     * - Auto-generated on first save (UUID)
     * - Deterministic from scene context (hash of scene+name+index)
     */
    @Getter
    @Setter
    private String id;

    /**
     * Optional tag for grouping/filtering.
     * <p>
     * Examples: "chest", "enemy", "npc", "pickup"
     * <p>
     * Useful for:
     * - Querying all entities of a type
     * - Batch operations (destroy all "enemy" entities)
     */
    @Getter
    @Setter
    private String persistenceTag;

    // Runtime state (not serialized)
    @HideInInspector
    private transient boolean registered = false;

    // ========================================================================
    // CONSTRUCTORS
    // ========================================================================

    /**
     * Default constructor for serialization.
     * ID will be generated on first save if not set.
     */
    public PersistentId() {
    }

    /**
     * Constructor with explicit ID.
     *
     * @param id The persistent ID
     */
    public PersistentId(String id) {
        this.id = id;
    }

    // ========================================================================
    // LIFECYCLE
    // ========================================================================

    @Override
    protected void onStart() {
        if (!registered) {
            // Generate ID if not set
            if (id == null || id.isEmpty()) {
                id = generateId();
            }
            SaveManager.registerEntity(this);
            registered = true;
        }
    }

    @Override
    protected void onDestroy() {
        if (registered) {
            SaveManager.unregisterEntity(this);
            registered = false;
        }
    }

    // ========================================================================
    // STATIC HELPERS
    // ========================================================================

    /**
     * Generate a random unique ID.
     * Uses first 8 characters of UUID for brevity.
     */
    public static String generateId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * Generate a deterministic ID from context.
     * <p>
     * Useful for entities that should always have the same ID
     * even if scene is reloaded.
     *
     * @param sceneName  Current scene name
     * @param entityName GameObject name
     * @param index      Index if multiple entities have same name
     * @return Deterministic ID (hash-based)
     */
    public static String deterministicId(String sceneName, String entityName, int index) {
        String combined = sceneName + ":" + entityName + ":" + index;
        return Integer.toHexString(combined.hashCode());
    }
}
