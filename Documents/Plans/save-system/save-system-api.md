# Save System - API Reference

This document details the public API: SaveManager, PersistentId component, and ISaveable interface.

---

## SaveManager

Static API for all save/load operations. Singleton pattern - initialized once at game startup.

### Initialization

```java
/**
 * Initialize the save system.
 * Call once at game startup, after SceneManager is created.
 *
 * @param sceneManager The game's SceneManager instance
 */
public static void initialize(SceneManager sceneManager);

/**
 * Initialize with custom save directory.
 * Useful for testing or custom installations.
 *
 * @param sceneManager The game's SceneManager instance
 * @param savesDirectory Custom directory for save files
 */
public static void initialize(SceneManager sceneManager, Path savesDirectory);
```

**Usage:**
```java
// In GameApplication.initialize() or Main.main()
SceneManager sceneManager = new SceneManager(/* ... */);
SaveManager.initialize(sceneManager);
```

---

### Save Operations

```java
/**
 * Save current game state to a slot.
 *
 * @param slotName Slot identifier (becomes filename: "slot1" -> "slot1.save")
 * @return true if save succeeded, false on error
 */
public static boolean save(String slotName);

/**
 * Save with custom display name.
 *
 * @param slotName Slot identifier
 * @param displayName Human-readable name for save/load UI
 * @return true if save succeeded
 */
public static boolean save(String slotName, String displayName);
```

**Usage:**
```java
// Quick save
SaveManager.save("quicksave");

// Named save
SaveManager.save("slot1", "Village - After Boss Fight");

// Auto-save (call at checkpoints, scene transitions, etc.)
SaveManager.save("autosave", "Auto Save");
```

---

### Load Operations

```java
/**
 * Load a save file into memory.
 *
 * IMPORTANT: This does NOT load the scene. After calling load(),
 * you must call SceneManager.loadScene() with the saved scene name.
 *
 * @param slotName Slot to load
 * @return true if load succeeded, false if file not found or corrupted
 */
public static boolean load(String slotName);

/**
 * Get the scene name from the currently loaded save.
 * Use this to know which scene to load.
 *
 * @return Scene name, or null if no save loaded
 */
public static String getSavedSceneName();

/**
 * Check if a save is currently loaded.
 *
 * @return true if SaveManager has save data in memory
 */
public static boolean hasSaveLoaded();
```

**Usage:**
```java
// Load game flow
if (SaveManager.load("slot1")) {
    String sceneName = SaveManager.getSavedSceneName();
    SceneManager.loadScene(sceneName);
    // SaveManager automatically restores entity states via SceneLifecycleListener
} else {
    // Show error: "Failed to load save"
}
```

---

### New Game

```java
/**
 * Start a new game.
 * Creates fresh SaveData with no scene states or global data.
 * Call before loading the starting scene.
 */
public static void newGame();
```

**Usage:**
```java
// New game flow
SaveManager.newGame();
SceneManager.loadScene("IntroScene");
```

---

### Global State

Global state persists across scene transitions. Use for player-level data that isn't tied to a specific scene.

```java
/**
 * Set a global persistent value.
 *
 * @param namespace Category (e.g., "player", "quests", "settings")
 * @param key Key within namespace
 * @param value Value to store (primitives, strings, lists, maps)
 */
public static void setGlobal(String namespace, String key, Object value);

/**
 * Get a global persistent value.
 *
 * @param namespace Category
 * @param key Key within namespace
 * @param defaultValue Value to return if key doesn't exist
 * @return The stored value, or defaultValue if not found
 */
public static <T> T getGlobal(String namespace, String key, T defaultValue);

/**
 * Check if a global key exists.
 *
 * @param namespace Category
 * @param key Key within namespace
 * @return true if the key exists
 */
public static boolean hasGlobal(String namespace, String key);

/**
 * Remove a global key.
 *
 * @param namespace Category
 * @param key Key to remove
 */
public static void removeGlobal(String namespace, String key);
```

**Usage:**
```java
// Player data
SaveManager.setGlobal("player", "gold", 500);
SaveManager.setGlobal("player", "level", 5);
SaveManager.setGlobal("player", "class", "warrior");

int gold = SaveManager.getGlobal("player", "gold", 0);
String playerClass = SaveManager.getGlobal("player", "class", "none");

// Quest progress
SaveManager.setGlobal("quests", "main_quest_stage", 3);
SaveManager.setGlobal("quests", "blacksmith_quest", "COMPLETED");

// Lists work too
List<String> unlockedAreas = Arrays.asList("village", "forest", "cave");
SaveManager.setGlobal("player", "unlockedAreas", unlockedAreas);

// Retrieve list
List<String> areas = SaveManager.getGlobal("player", "unlockedAreas", List.of());
```

---

### Scene Flags

Scene-specific flags that don't belong to any entity. Alternative to global state for scene-local data.

```java
/**
 * Set a flag for the current scene.
 *
 * @param key Flag name
 * @param value Flag value
 */
public static void setSceneFlag(String key, Object value);

/**
 * Get a flag for the current scene.
 *
 * @param key Flag name
 * @param defaultValue Value if flag doesn't exist
 * @return The flag value
 */
public static <T> T getSceneFlag(String key, T defaultValue);
```

**Usage:**
```java
// Mark boss as defeated (scene-specific)
SaveManager.setSceneFlag("boss_defeated", true);
SaveManager.setSceneFlag("secret_door_opened", true);

// Check flag
if (SaveManager.getSceneFlag("boss_defeated", false)) {
    // Boss stays dead
}
```

---

### Save Slot Management

```java
/**
 * List all available save slots.
 * Returns metadata only - actual save data not loaded.
 *
 * @return List of SaveSlotInfo, sorted by timestamp (newest first)
 */
public static List<SaveSlotInfo> listSaves();

/**
 * Check if a save slot exists.
 *
 * @param slotName Slot to check
 * @return true if the .save file exists
 */
public static boolean saveExists(String slotName);

/**
 * Delete a save slot.
 *
 * @param slotName Slot to delete
 * @return true if deleted, false if not found
 */
public static boolean deleteSave(String slotName);

/**
 * Get the saves directory path.
 *
 * @return Path to saves directory
 */
public static Path getSavesDirectory();
```

**Usage:**
```java
// Populate save/load menu
List<SaveSlotInfo> saves = SaveManager.listSaves();
for (SaveSlotInfo info : saves) {
    String label = String.format("%s - %s (%s)",
        info.slotName(),
        info.displayName(),
        formatPlayTime(info.playTime())
    );
    // Display in UI...
}

// Delete save
if (SaveManager.saveExists("slot1")) {
    SaveManager.deleteSave("slot1");
}
```

---

### Play Time Tracking

```java
/**
 * Update play time accumulator.
 * Call once per frame from your game loop.
 *
 * @param deltaTime Frame delta time in seconds
 */
public static void updatePlayTime(float deltaTime);

/**
 * Get current session play time (since last save/load).
 *
 * @return Play time in seconds
 */
public static float getSessionPlayTime();

/**
 * Get total play time from current save.
 *
 * @return Total play time in seconds, or 0 if no save loaded
 */
public static float getTotalPlayTime();
```

**Usage:**
```java
// In game loop
@Override
public void update(float deltaTime) {
    SaveManager.updatePlayTime(deltaTime);
    // ... rest of update
}
```

---

### Internal Registration (Called by PersistentId)

```java
/**
 * Register an entity for save tracking.
 * Called automatically by PersistentId.onStart().
 *
 * @param pid The PersistentId component
 */
static void registerEntity(PersistentId pid);

/**
 * Unregister an entity.
 * Called automatically by PersistentId.onDestroy().
 *
 * @param pid The PersistentId component
 */
static void unregisterEntity(PersistentId pid);
```

---

## PersistentId Component

A component that gives GameObjects a stable identifier for save/load matching.

### Class Definition

```java
package com.pocket.rpg.save;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.serialization.HideInInspector;

/**
 * Marks a GameObject as saveable with a stable persistent ID.
 *
 * Add this component to any entity that needs to persist state across saves:
 * - Player character
 * - Chests that can be opened
 * - NPCs with dialogue state
 * - Enemies that stay dead
 * - Collectibles that don't respawn
 *
 * The ID is stored in the scene file and preserved across save/load cycles.
 */
public class PersistentId extends Component {

    /**
     * Stable identifier for this entity.
     *
     * Can be:
     * - Set in editor (e.g., "player", "chest_01")
     * - Auto-generated on first save (UUID)
     * - Deterministic from scene context (hash of scene+name+index)
     */
    private String id;

    /**
     * Optional tag for grouping/filtering.
     *
     * Examples: "chest", "enemy", "npc", "pickup"
     *
     * Useful for:
     * - Querying all entities of a type
     * - Batch operations (destroy all "enemy" entities)
     */
    private String persistenceTag;

    /**
     * Whether this entity should be saved if spawned at runtime.
     *
     * ONLY RELEVANT IF: Decision 2 = Option B or C
     *
     * Default: false (spawned entities not saved)
     * Set to true: this spawned entity will be saved
     */
    private boolean saveWhenSpawned = false;

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
     *
     * Useful for entities that should always have the same ID
     * even if scene is reloaded.
     *
     * @param sceneName Current scene name
     * @param entityName GameObject name
     * @param index Index if multiple entities have same name
     * @return Deterministic ID (hash-based)
     */
    public static String deterministicId(String sceneName, String entityName, int index) {
        String combined = sceneName + ":" + entityName + ":" + index;
        return Integer.toHexString(combined.hashCode());
    }

    // Getters and setters...
}
```

### Usage Examples

**In Scene Editor:**
1. Select a GameObject (e.g., a chest)
2. Add Component → Save → PersistentId
3. Set ID to "chest_01" (or leave blank for auto-generation)
4. Set Tag to "chest" (optional)

**In Code (for special entities):**
```java
// Player always has ID "player"
GameObject player = new GameObject("Player");
player.addComponent(new PersistentId("player"));
player.addComponent(new Inventory());
player.addComponent(new Health());

// Chest with auto ID
GameObject chest = Prefabs.instantiate("chest");
chest.addComponent(new PersistentId());  // ID generated automatically
```

---

## ISaveable Interface

Interface for components that need to save/restore custom state.

### Interface Definition

```java
package com.pocket.rpg.save;

import java.util.Map;

/**
 * Interface for components that need custom save/load logic.
 *
 * IMPORTANT: By default, components are NOT saved.
 * Only components implementing ISaveable will have their state captured.
 *
 * The interface uses Map<String, Object> for flexibility:
 * - Simple values: int, float, boolean, String
 * - Complex values: List, Map (nested)
 * - Assets: store as path strings, reload on load
 *
 * DO NOT save:
 * - Transient runtime state (animation timers, cached values)
 * - Component references (use @ComponentRef instead)
 * - Assets directly (save paths, reload on load)
 */
public interface ISaveable {

    /**
     * Capture current state for saving.
     *
     * Called when SaveManager.save() is invoked.
     * Return a map of field names to values that should be saved.
     *
     * Guidelines:
     * - Only include state that needs persistence
     * - Skip derived/computed values
     * - Use simple types (primitives, strings, lists, maps)
     * - For assets, save the path string
     *
     * @return Map of field names to values, or null/empty if nothing to save
     */
    Map<String, Object> getSaveState();

    /**
     * Restore state from a save file.
     *
     * Called during scene load when saved state exists for this component.
     * The map contains the same structure returned by getSaveState().
     *
     * Guidelines:
     * - Handle missing keys gracefully (save might be from older version)
     * - Use SerializationUtils.fromSerializable() for type conversion
     * - Reload assets from paths
     *
     * @param state Previously saved state (may be null)
     */
    void loadSaveState(Map<String, Object> state);

    /**
     * Check if this component has meaningful state to save.
     *
     * Used to skip components that haven't changed from defaults.
     * Override to return false if nothing worth saving.
     *
     * @return true if getSaveState() should be called
     */
    default boolean hasSaveableState() {
        return true;
    }
}
```

### Guidelines for Implementing ISaveable

**What to Save:**
- Player-modified state (health, gold, inventory items)
- Progress flags (chest opened, enemy killed, dialogue stage)
- Persistent settings (equipped items, ability loadout)

**What NOT to Save:**
- Default values (save file should only contain changes)
- Computed values (world matrix, bounding boxes)
- Transient runtime state (animation frame, particle timers)
- Component references (use `@ComponentRef`, resolved at load)
- Asset objects directly (save paths instead)

**Type Conversion:**

When loading, JSON deserializes numbers as `Double` or `Long`. Use safe casts:

```java
// Safe number handling
int value = ((Number) state.get("health")).intValue();
float speed = ((Number) state.get("speed")).floatValue();

// Lists
@SuppressWarnings("unchecked")
List<String> items = (List<String>) state.get("items");

// Nested maps
@SuppressWarnings("unchecked")
Map<String, Object> nested = (Map<String, Object>) state.get("data");

// Enums (save as string)
// Save:
state.put("state", myEnum.name());
// Load:
MyEnum value = MyEnum.valueOf((String) state.get("state"));
```

---

## Complete Example: Player Entity

```java
// Player setup in scene or code
GameObject player = new GameObject("Player");

// Required for saving
player.addComponent(new PersistentId("player"));

// Components that implement ISaveable
player.addComponent(new Health());      // Saves currentHealth
player.addComponent(new Inventory());   // Saves gold, items
player.addComponent(new QuestTracker()); // Saves quest progress

// Components that DON'T need ISaveable
player.addComponent(new SpriteRenderer());  // Visual only
player.addComponent(new PlayerController()); // Input handling
player.addComponent(new AnimationComponent()); // Runtime animation state
```

---

## Save/Load Flow Diagram

```
SAVE FLOW:
==========
1. Game calls SaveManager.save("slot1")
2. SaveManager iterates registered PersistentId entities
3. For each entity:
   a. Capture transform position
   b. For each ISaveable component:
      - Call getSaveState()
      - Store in SavedEntityState.componentStates
4. Store in SavedSceneState.modifiedEntities
5. Serialize SaveData to JSON
6. Write to {saves_dir}/slot1.save


LOAD FLOW:
==========
1. Game calls SaveManager.load("slot1")
2. SaveManager reads and deserializes JSON
3. SaveManager stores SaveData in memory

4. Game calls SceneManager.loadScene(sceneName)
5. RuntimeSceneLoader loads scene file (normal flow)
6. GameObjects created with components
7. Parent-child hierarchy established
8. @ComponentRef resolved

9. Each PersistentId.onStart() calls SaveManager.registerEntity()
10. SaveManager.registerEntity() immediately:
    a. Looks up SavedEntityState by ID
    b. If found, applies position
    c. For each ISaveable component:
       - Look up saved state by class name
       - Call loadSaveState(state)

11. Scene.initialize() completes
12. Game resumes with restored state
```
