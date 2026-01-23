# Save System - Data Structures

This document details all the data classes used by the save system.

---

## Package Structure

```
com.pocket.rpg.save/
├── SaveData.java           # Root save file structure
├── SavedSceneState.java    # Per-scene changes (delta from scene file)
├── SavedEntityState.java   # Single entity's runtime state
├── SpawnedEntityData.java  # Runtime-created entities (optional)
├── SaveSlotInfo.java       # Metadata for UI display
├── SaveManager.java        # Static API (see save-system-api.md)
├── PersistentId.java       # Component (see save-system-api.md)
└── ISaveable.java          # Interface (see save-system-api.md)
```

---

## SaveData

The root data structure for save files. This is what gets serialized to `.save` files.

```java
package com.pocket.rpg.save;

import java.util.*;

/**
 * Root data structure for save files.
 * Follows SceneData's versioning pattern for migration support.
 */
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
     *
     * Structure: namespace -> (key -> value)
     *
     * Example namespaces:
     * - "player" -> {"gold": 500, "level": 12, "class": "warrior"}
     * - "quests" -> {"main_quest": "COMPLETED", "side_quest_1": "IN_PROGRESS"}
     * - "settings" -> {"musicVolume": 0.8, "difficulty": "normal"}
     * - "achievements" -> {"first_kill": true, "speedrun": false}
     *
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
     *
     * Only scenes that have been modified are stored here.
     * A scene with no entry means "use initial state from scene file".
     */
    private Map<String, SavedSceneState> sceneStates = new HashMap<>();

    // ========================================================================
    // MIGRATION
    // ========================================================================

    /**
     * Check if this save needs migration to a newer format.
     */
    public boolean needsMigration() {
        return version < 1;  // Update as versions increase
    }

    /**
     * Migrate save data to the current version.
     * Follow SceneData's pattern for version-by-version migration.
     */
    public void migrate() {
        // Example migration:
        // if (version < 2) {
        //     migrateV1ToV2();
        //     version = 2;
        // }
        version = 1;
    }

    // Getters and setters...
}
```

---

## SavedSceneState

Captures runtime changes to a specific scene. This is a **delta** - it only stores what changed from the initial scene file.

```java
package com.pocket.rpg.save;

import java.util.*;

/**
 * Captures runtime changes to a single scene.
 *
 * Design principle: Only store DELTAS from initial scene state.
 * - Scene file defines initial state (positions, components, etc.)
 * - This class stores what changed at runtime
 * - On load: apply scene file, then apply these changes
 */
public class SavedSceneState {

    /**
     * Scene name (matches .scene filename without extension).
     * Example: "Village", "DungeonLevel1"
     */
    private String sceneName;

    /**
     * Entities that have been modified from their initial state.
     *
     * Key: persistentId of the entity
     * Value: the entity's changed state
     *
     * Only includes entities that:
     * 1. Have a PersistentId component
     * 2. Have ISaveable components with state to save
     * 3. Have actually been modified (position changed, component state changed)
     */
    private Map<String, SavedEntityState> modifiedEntities = new HashMap<>();

    /**
     * PersistentIds of entities that have been destroyed.
     *
     * On load: after loading scene file, destroy these entities.
     *
     * Use cases:
     * - Player killed an enemy permanently
     * - Player collected a one-time pickup
     * - Player destroyed a destructible object
     */
    private Set<String> destroyedEntities = new HashSet<>();

    /**
     * Entities that were dynamically spawned at runtime.
     *
     * ONLY USED IF: Decision 2 = Option B or C (save spawned entities)
     *
     * These need full data since they don't exist in the scene file.
     */
    private List<SpawnedEntityData> spawnedEntities = new ArrayList<>();

    /**
     * Scene-specific flags and metadata.
     *
     * For game logic that doesn't fit into entity state.
     *
     * Examples:
     * - "boss_defeated": true
     * - "secret_door_opened": true
     * - "npc_dialogue_stage": 3
     * - "puzzle_solved": true
     * - "visited": true
     */
    private Map<String, Object> sceneFlags = new HashMap<>();

    // Getters and setters...
}
```

---

## SavedEntityState

Captures the runtime state of a single saveable entity.

```java
package com.pocket.rpg.save;

import java.util.*;

/**
 * Captures runtime state changes for a single entity.
 *
 * Design: Only store what's different from scene file defaults.
 * Null values mean "use default from scene file".
 */
public class SavedEntityState {

    /**
     * Persistent ID matching the entity.
     * Must match PersistentId.id on the GameObject.
     */
    private String persistentId;

    /**
     * World position [x, y, z].
     *
     * Null means: use position from scene file.
     * Set means: entity was moved at runtime.
     */
    private float[] position;

    /**
     * World rotation [x, y, z] in degrees.
     *
     * Null means: use rotation from scene file.
     * Usually null for 2D games.
     */
    private float[] rotation;

    /**
     * Whether the entity is active/enabled.
     *
     * Null means: use active state from scene file.
     * Set to false: entity was disabled at runtime.
     */
    private Boolean active;

    /**
     * Component state changes.
     *
     * Structure: componentClassName -> ISaveable.getSaveState() result
     *
     * Key is fully qualified class name:
     *   "com.pocket.rpg.components.Inventory"
     *
     * Value is whatever the component's getSaveState() returns.
     *
     * Only components implementing ISaveable appear here.
     * Components not in this map keep their scene file defaults.
     */
    private Map<String, Map<String, Object>> componentStates = new HashMap<>();

    // Getters and setters...
}
```

---

## SpawnedEntityData

Full data for entities created at runtime. Only needed if saving spawned entities (Decision 2, Option B or C).

```java
package com.pocket.rpg.save;

import java.util.*;

/**
 * Full data for entities that were created at runtime.
 *
 * Unlike SavedEntityState (which stores deltas), this stores
 * everything needed to recreate the entity from scratch.
 *
 * ONLY USED IF: Decision 2 = Option B or C
 */
public class SpawnedEntityData {

    /**
     * Assigned persistent ID.
     * Generated when entity was spawned.
     */
    private String persistentId;

    /**
     * Entity display name.
     */
    private String name;

    /**
     * World position [x, y, z].
     */
    private float[] position;

    /**
     * World rotation [x, y, z] in degrees.
     */
    private float[] rotation;

    /**
     * Scale [x, y, z].
     */
    private float[] scale;

    /**
     * Prefab ID if instantiated from a prefab.
     *
     * Example: "prefabs/enemy_goblin"
     *
     * Null if entity was created from scratch (rare).
     */
    private String prefabId;

    /**
     * Component overrides applied to the prefab.
     *
     * Structure: componentClassName -> field overrides
     *
     * Same format as GameObjectData.componentOverrides.
     */
    private Map<String, Map<String, Object>> componentOverrides;

    /**
     * ISaveable component states.
     *
     * Structure: componentClassName -> getSaveState() result
     */
    private Map<String, Map<String, Object>> saveableStates;

    /**
     * Tag for optional filtering.
     */
    private String persistenceTag;

    // Getters and setters...
}
```

---

## SaveSlotInfo

Lightweight record for displaying save slots in UI. Contains only metadata, not actual save data.

```java
package com.pocket.rpg.save;

/**
 * Metadata about a save slot for UI display.
 *
 * This is a lightweight record - actual save data is NOT loaded
 * until the player selects a slot.
 *
 * Used by SaveManager.listSaves() to populate save/load menus.
 */
public record SaveSlotInfo(
    /**
     * Slot name (filename without .save extension).
     * Examples: "slot1", "autosave", "quicksave"
     */
    String slotName,

    /**
     * Display name set by player or auto-generated.
     * Examples: "Village - Level 5", "Auto Save", "Slot 1"
     */
    String displayName,

    /**
     * Unix timestamp when save was created.
     * Use to display "Saved: Jan 21, 2026 3:45 PM"
     */
    long timestamp,

    /**
     * Total play time in seconds.
     * Use to display "Play time: 12h 34m"
     */
    float playTime,

    /**
     * Scene name where player saved.
     * Use to display location or thumbnail.
     */
    String sceneName
) {}
```

---

## JSON Format Example

Here's what a complete save file looks like:

```json
{
  "version": 1,
  "saveId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "displayName": "Village - Level 5",
  "timestamp": 1705881600000,
  "playTime": 3600.5,

  "globalState": {
    "player": {
      "name": "Hero",
      "class": "warrior",
      "level": 5,
      "experience": 2500,
      "gold": 1500,
      "totalDeaths": 2
    },
    "quests": {
      "main_quest_stage": 3,
      "side_quest_blacksmith": "COMPLETED",
      "side_quest_herbs": "IN_PROGRESS"
    },
    "achievements": {
      "first_enemy_killed": true,
      "first_boss_killed": false,
      "speedrun_village": false
    },
    "settings": {
      "musicVolume": 0.8,
      "sfxVolume": 1.0,
      "showDamageNumbers": true
    }
  },

  "currentScene": "Village",

  "sceneStates": {
    "Village": {
      "sceneName": "Village",
      "modifiedEntities": {
        "player": {
          "persistentId": "player",
          "position": [24.5, 18.0, 0.0],
          "componentStates": {
            "com.pocket.rpg.components.Inventory": {
              "items": ["sword_steel", "potion_health", "key_dungeon"],
              "equippedWeapon": "sword_steel",
              "equippedArmor": "leather_armor"
            },
            "com.pocket.rpg.components.Health": {
              "currentHealth": 85,
              "maxHealth": 100
            }
          }
        },
        "chest_01": {
          "persistentId": "chest_01",
          "componentStates": {
            "com.pocket.rpg.components.Chest": {
              "opened": true,
              "looted": true
            }
          }
        },
        "chest_02": {
          "persistentId": "chest_02",
          "componentStates": {
            "com.pocket.rpg.components.Chest": {
              "opened": true,
              "looted": true
            }
          }
        },
        "npc_blacksmith": {
          "persistentId": "npc_blacksmith",
          "componentStates": {
            "com.pocket.rpg.components.DialogueState": {
              "dialogueStage": 2,
              "talkedToday": true
            }
          }
        }
      },
      "destroyedEntities": [
        "pickup_coin_01",
        "pickup_coin_02",
        "destructible_barrel_01"
      ],
      "sceneFlags": {
        "visited": true,
        "tutorial_completed": true,
        "shop_discount_unlocked": true
      }
    },
    "Forest": {
      "sceneName": "Forest",
      "modifiedEntities": {
        "player": {
          "persistentId": "player",
          "position": [5.0, 12.0, 0.0]
        }
      },
      "destroyedEntities": [
        "enemy_goblin_01",
        "enemy_goblin_02",
        "enemy_goblin_03"
      ],
      "sceneFlags": {
        "visited": true,
        "cleared": true
      }
    }
  }
}
```

---

## Design Notes

### Why Delta-Based Saves?

Instead of saving complete scene state, we only save changes from the scene file:

**Pros:**
- Small save files (only modified entities)
- Scene updates don't break existing saves (new enemies, tweaked positions work automatically)
- Clear separation: scene file = design, save file = player progress
- Easier debugging (can see exactly what changed)

**Cons:**
- Must be careful about entity identification
- Can't save entities without PersistentId

### Why Nullable Fields?

Fields like `position`, `rotation`, `active` in SavedEntityState are nullable:

- `null` = "use value from scene file"
- Set = "player modified this at runtime"

This keeps saves small and allows scene file updates to propagate.

### Version Migration Pattern

Follow the same pattern as `SceneData`:

```java
public void migrate() {
    if (version < 2) {
        // Move old "playerGold" to globalState["player"]["gold"]
        // ...
        version = 2;
    }
    if (version < 3) {
        // Rename "quests" namespace to "questProgress"
        // ...
        version = 3;
    }
}
```
