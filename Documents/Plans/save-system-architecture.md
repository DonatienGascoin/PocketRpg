# Save System Architecture

## Overview

A save/load system for PocketRpg that persists game state across sessions. This is fundamentally different from scene serialization:

| Aspect | Scene Data | Save Game Data |
|--------|------------|----------------|
| **Purpose** | Define initial state (authoring) | Capture runtime changes (player progress) |
| **Location** | `gameData/scenes/` (bundled with game) | User data directory (per-player) |
| **When written** | Editor-time | Runtime |
| **Contents** | All entities/components | Only changed/relevant state |

---

## Architectural Decisions

### Decision 1: Scene Transition Handling

How should the save system interact with scene changes?

#### Option A: Recreate from Save (RECOMMENDED)

All objects are destroyed on scene change, then restored from save data when the new scene loads.

**How it works:**
1. Player triggers scene transition
2. SaveManager captures current scene state to memory
3. Old scene destroyed completely (existing behavior)
4. New scene loads from `.scene` file (existing behavior)
5. SaveManager applies saved state to entities with `PersistentId` component

**Pros:**
- Clean architecture - scenes are self-contained
- Easier to debug (every scene load is predictable)
- Save file is always the source of truth
- Simpler implementation - no changes to SceneManager
- Matches existing scene architecture

**Cons:**
- Need robust save/load for seamless transitions
- Slight overhead on scene transitions

**Best for:** Most RPGs, adventure games, level-based games

---

#### Option B: DontDestroyOnLoad

Some objects (like the player) survive scene changes without being destroyed and recreated.

**How it works:**
1. Mark certain GameObjects as "persistent"
2. On scene transition, persistent objects are removed from old scene but not destroyed
3. New scene loads
4. Persistent objects are added to new scene

**Pros:**
- Player keeps all runtime state instantly (no save/load)
- No delay for persistent objects
- Good for continuous gameplay (player walks between connected scenes)
- Animation states, particle effects, etc. preserved

**Cons:**
- More complex scene management
- Risk of duplicate objects if not careful
- Requires changes to `SceneManager.loadScene()`
- Harder to reason about object lifecycle
- Parent-child relationships become complex

**Best for:** Open-world games with seamless transitions, games where player physically walks between areas

**Implementation complexity:** HIGH - requires modifying SceneManager, handling edge cases with hierarchy

---

### Decision 2: Dynamically Spawned Entities

How should the save system handle entities created at runtime (not defined in scene files)?

#### Option A: Don't Save Spawned Entities (RECOMMENDED)

Only entities defined in scene files are saved. Runtime-spawned entities are lost on save/load.

**Examples of what would NOT be saved:**
- Enemies spawned by a spawner component
- Particle effects
- Temporary projectiles
- Items dropped by enemies (unless placed in scene file)

**Pros:**
- Simpler save format
- Smaller save files
- Enemies/particles naturally respawn (often desired)
- Easier to implement and debug

**Cons:**
- Can't persist dropped loot on the ground
- Can't persist summoned companions
- Some game designs won't work

**Best for:** Linear games, games where enemies respawn, roguelikes

---

#### Option B: Save Spawned Entities

Runtime-created entities with `PersistentId` are fully saved and restored.

**What gets saved:**
- The prefab ID used to spawn the entity
- Position and other transform data
- All ISaveable component states
- A newly generated persistent ID

**Pros:**
- More complete world simulation
- Can persist dropped loot, player-created objects
- Supports sandbox-style games
- Summoned companions persist

**Cons:**
- More complex save format
- Larger save files
- Must track which prefab spawned each entity
- Edge cases with spawner components (don't double-spawn)

**Best for:** Sandbox games, games with dropped loot, persistent companions

---

#### Option C: Configurable Per-Entity

Each spawned entity can opt-in to persistence via a flag on `PersistentId`.

```java
PersistentId pid = new PersistentId();
pid.setSaveWhenSpawned(true);  // This spawned entity will be saved
gameObject.addComponent(pid);
```

**Pros:**
- Most flexible
- Spawned items saved, temporary effects ignored
- Game designer has full control

**Cons:**
- Most complex implementation
- Game code must explicitly mark saveables
- Easy to forget to set the flag

**Best for:** Games that need fine-grained control

---

### Decision 3: Auto-Save

**Selected: Manual Only**

Save only when explicitly called by game code. This keeps the system simple and gives full control to the game logic.

To add auto-save later, simply call `SaveManager.save("autosave")` at appropriate moments:
- Scene transitions
- Checkpoints
- Time intervals
- After major events

---

## Save File Location

Save files should be stored in the user's data directory, not in `gameData/` (which is read-only in shipped games).

```
Windows:
  %APPDATA%/PocketRpg/saves/
  Example: C:\Users\John\AppData\Roaming\PocketRpg\saves\

Unix/Mac:
  ~/.pocketrpg/saves/
  Example: /home/john/.pocketrpg/saves/

Files:
  slot1.save
  slot2.save
  slot3.save
  autosave.save
  quicksave.save
```

---

## Integration with Existing Systems

The save system leverages existing PocketRpg infrastructure:

| Existing System | Location | How We Use It |
|-----------------|----------|---------------|
| **Serializer** | `serialization/Serializer.java` | `toJson()` / `fromJson()` for save files |
| **SceneData pattern** | `serialization/SceneData.java` | Version field + migration pattern |
| **SerializationUtils** | `serialization/SerializationUtils.java` | Type conversion (vectors, enums, assets) |
| **SceneLifecycleListener** | `scenes/SceneManager.java` | Hook for scene load/unload events |
| **Component** | `components/Component.java` | Base class for PersistentId |
| **ComponentRegistry** | `serialization/ComponentRegistry.java` | Component instantiation by name |

---

## Game Flow Examples

### New Game

```java
// Player clicks "New Game" in menu
SaveManager.newGame();  // Creates empty SaveData
SceneManager.loadScene("IntroScene");
```

### Save Game

```java
// Player opens pause menu, clicks "Save"
boolean success = SaveManager.save("slot1", "Village - Level 5");
if (success) {
    StatusBar.showMessage("Game saved!");
}
```

### Load Game

```java
// Player selects save slot in menu
if (SaveManager.load("slot1")) {
    String sceneName = SaveManager.getSavedSceneName();
    SceneManager.loadScene(sceneName);
    // SaveManager automatically restores entity states
}
```

### Scene Transition with Save

```java
// Player enters door to next area
SaveManager.save("autosave");  // Optional auto-save
SceneManager.loadScene("DungeonLevel2");
// Previous scene state preserved in SaveManager.currentSave
```

### Accessing Global State

```java
// Set global value (persists across scenes)
SaveManager.setGlobal("player", "gold", 500);
SaveManager.setGlobal("quests", "main_quest_stage", 3);

// Get global value
int gold = SaveManager.getGlobal("player", "gold", 0);
int stage = SaveManager.getGlobal("quests", "main_quest_stage", 1);
```

---

## Files to Create

| File | Purpose |
|------|---------|
| `save/SaveData.java` | Root save structure with version + migration |
| `save/SavedSceneState.java` | Per-scene delta state |
| `save/SavedEntityState.java` | Single entity's saved state |
| `save/SpawnedEntityData.java` | Runtime-created entities (if Option B/C) |
| `save/SaveManager.java` | Static API, scene lifecycle hooks |
| `save/PersistentId.java` | Component for saveable entities |
| `save/ISaveable.java` | Interface for saveable components |
| `save/SaveSlotInfo.java` | Record for UI display |

## Files to Modify

| File | Change |
|------|--------|
| `core/GameApplication.java` | Call `SaveManager.initialize()` at startup |

---

## Implementation Phases

### Phase 1: Core Infrastructure
- Create `com.pocket.rpg.save` package
- Implement `SaveData`, `SavedSceneState`, `SavedEntityState`
- Implement `ISaveable` interface
- Implement `PersistentId` component
- Basic `SaveManager` with file I/O

### Phase 2: Scene Integration
- Hook `SaveManager` into `SceneLifecycleListener`
- Implement entity registration/unregistration
- Implement `captureCurrentSceneState()`
- Implement `applySavedStateToEntity()`

### Phase 3: Global State & Utilities
- Implement global state get/set
- Implement `listSaves()`, `deleteSave()`, `saveExists()`
- Implement `SaveSlotInfo` for UI
- Play time tracking

### Phase 4: Testing
- Create test scene with saveable entities
- Unit tests for save/load cycle
- Integration tests

### Phase 5: Code Review
- Review all new/modified files
- Write review to `Documents/Reviews/save-system-review.md`

---

## Verification

1. **New Game Flow:**
   - Call `SaveManager.newGame()`
   - Load a scene, modify entity positions
   - Save to slot1
   - Verify `.save` file exists in AppData

2. **Load Game Flow:**
   - Load slot1
   - Verify correct scene loads
   - Verify entity positions match saved state
   - Verify `ISaveable` component states restored

3. **Global State:**
   - Set global value with `setGlobal()`
   - Change scene
   - Verify `getGlobal()` returns saved value

4. **Save Slot Management:**
   - Create multiple saves
   - Verify `listSaves()` returns correct metadata
   - Delete save, verify removed

---

## Related Documents

- `save-system-data-structures.md` - All data classes and JSON format
- `save-system-api.md` - SaveManager API and ISaveable interface
- `save-system-examples.md` - Example component implementations
- `save-system-scenarios.md` - How different entity types are saved under each option

---

## Next Steps

After reviewing all documents:

1. **Choose your options** for:
   - Scene transition handling (Option A or B)
   - Spawned entity handling (Option A, B, or C)

2. **Approve implementation** when ready
