package com.pocket.rpg.save;

import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Root data structure for save files.
 * Follows SceneData's versioning pattern for migration support.
 */
@Getter
@Setter
public class SaveData {

    // ========================================================================
    // METADATA
    // ========================================================================

    /**
     * Save format version for migration support.
     * Increment when making breaking changes to save format.
     */
    private int version = 1;

    /**
     * Unique identifier for this save (UUID).
     * Generated once when save is first created.
     */
    private String saveId;

    /**
     * Human-readable name shown in save/load UI.
     * Examples: "Slot 1", "Auto Save", "Village - Level 5"
     */
    private String displayName;

    /**
     * Unix timestamp (milliseconds) when save was last written.
     */
    private long timestamp;

    /**
     * Total play time in seconds across all sessions.
     */
    private float playTime;

    // ========================================================================
    // GLOBAL STATE
    // ========================================================================

    /**
     * Global persistent data that survives scene transitions.
     * <p>
     * Structure: namespace -> (key -> value)
     * <p>
     * Example namespaces:
     * - "player" -> {"gold": 500, "level": 12, "class": "warrior"}
     * - "quests" -> {"main_quest": "COMPLETED", "side_quest_1": "IN_PROGRESS"}
     * - "settings" -> {"musicVolume": 0.8, "difficulty": "normal"}
     * - "achievements" -> {"first_kill": true, "speedrun": false}
     * <p>
     * Values can be: primitives, strings, lists, nested maps
     */
    private Map<String, Map<String, Object>> globalState = new HashMap<>();

    // ========================================================================
    // SCENE STATE
    // ========================================================================

    /**
     * Name of the scene the player was in when they saved.
     * Used to load the correct scene on game load.
     */
    private String currentScene;

    /**
     * Per-scene state changes, keyed by scene name.
     * <p>
     * Only scenes that have been modified are stored here.
     * A scene with no entry means "use initial state from scene file".
     */
    private Map<String, SavedSceneState> sceneStates = new HashMap<>();

    // ========================================================================
    // CONSTRUCTORS
    // ========================================================================

    public SaveData() {
        this.saveId = UUID.randomUUID().toString();
    }

    // ========================================================================
    // MIGRATION
    // ========================================================================

    /**
     * Check if this save needs migration to a newer format.
     */
    public boolean needsMigration() {
        return version < 1;
    }

    /**
     * Migrate save data to the current version.
     * Follow SceneData's pattern for version-by-version migration.
     */
    public void migrate() {
        // Future version migrations would go here:
        // if (version < 2) {
        //     migrateV1ToV2();
        //     version = 2;
        // }
        version = 1;
    }
}
