package com.pocket.rpg.save;

import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Captures runtime changes to a single scene.
 * <p>
 * Design principle: Only store DELTAS from initial scene state.
 * - Scene file defines initial state (positions, components, etc.)
 * - This class stores what changed at runtime
 * - On load: apply scene file, then apply these changes
 */
@Getter
@Setter
public class SavedSceneState {

    /**
     * Scene name (matches .scene filename without extension).
     * Example: "Village", "DungeonLevel1"
     */
    private String sceneName;

    /**
     * Entities that have been modified from their initial state.
     * <p>
     * Key: persistentId of the entity
     * Value: the entity's changed state
     * <p>
     * Only includes entities that:
     * 1. Have a PersistentId component
     * 2. Have ISaveable components with state to save
     * 3. Have actually been modified (position changed, component state changed)
     */
    private Map<String, SavedEntityState> modifiedEntities = new HashMap<>();

    /**
     * PersistentIds of entities that have been destroyed.
     * <p>
     * On load: after loading scene file, destroy these entities.
     * <p>
     * Use cases:
     * - Player killed an enemy permanently
     * - Player collected a one-time pickup
     * - Player destroyed a destructible object
     */
    private Set<String> destroyedEntities = new HashSet<>();

    /**
     * Scene-specific flags and metadata.
     * <p>
     * For game logic that doesn't fit into entity state.
     * <p>
     * Examples:
     * - "boss_defeated": true
     * - "secret_door_opened": true
     * - "npc_dialogue_stage": 3
     * - "puzzle_solved": true
     * - "visited": true
     */
    private Map<String, Object> sceneFlags = new HashMap<>();

    public SavedSceneState() {
    }

    public SavedSceneState(String sceneName) {
        this.sceneName = sceneName;
    }
}
