package com.pocket.rpg.save;

import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

/**
 * Captures runtime state changes for a single entity.
 * <p>
 * Design: Only store what's different from scene file defaults.
 * Null values mean "use default from scene file".
 */
@Getter
@Setter
public class SavedEntityState {

    /**
     * Persistent ID matching the entity.
     * Must match PersistentId.id on the GameObject.
     */
    private String persistentId;

    /**
     * World position [x, y, z].
     * <p>
     * Null means: use position from scene file.
     * Set means: entity was moved at runtime.
     */
    private float[] position;

    /**
     * Whether the entity is active/enabled.
     * <p>
     * Null means: use active state from scene file.
     * Set to false: entity was disabled at runtime.
     */
    private Boolean active;

    /**
     * Component state changes.
     * <p>
     * Structure: componentClassName -> ISaveable.getSaveState() result
     * <p>
     * Key is fully qualified class name:
     * "com.pocket.rpg.components.Inventory"
     * <p>
     * Value is whatever the component's getSaveState() returns.
     * <p>
     * Only components implementing ISaveable appear here.
     * Components not in this map keep their scene file defaults.
     */
    private Map<String, Map<String, Object>> componentStates = new HashMap<>();

    public SavedEntityState() {
    }

    public SavedEntityState(String persistentId) {
        this.persistentId = persistentId;
    }
}
