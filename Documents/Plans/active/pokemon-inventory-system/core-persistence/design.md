# Core Persistence Infrastructure — Plan 0

## Overview

Shared foundation that all other plans depend on. This plan creates the `PlayerData` class, adds two engine-level lifecycle hooks (`onBeforeSceneUnload` and `onPostSceneInitialize`), and documents the persistence patterns that every plan must follow.

**This plan must be implemented before any other plan.**

## Dependencies

- **None** — this is the foundational layer.

## What This Plan Provides

| Deliverable | Used by |
|-------------|---------|
| `PlayerData` class with `load()`/`save()` | scene-data-persistence, item-inventory, pokemon-ecs, dialogue-rewards |
| `onBeforeSceneUnload()` component lifecycle hook | scene-data-persistence (PlayerMovement position flush) |
| `onPostSceneInitialize()` scene lifecycle hook | scene-data-persistence (BattleReturnHandler) |
| Persistence pattern documentation | All plans that write player state |

---

## PlayerData — The Complete Class

Single source of truth for all cross-scene player state. Stored as a JSON string in `SaveManager.globalState` under the `"player"` namespace, serialized via the project's existing `Serializer` (Gson wrapper).

### Field Inventory

Every field that will ever exist on `PlayerData`, organized by which plan writes to it:

| Field | Type | Written by | Plan |
|-------|------|-----------|------|
| `lastOverworldScene` | `String` | `PlayerMovement` (onBeforeSceneUnload) | scene-data-persistence |
| `lastGridX` | `int` | `PlayerMovement` (onBeforeSceneUnload) | scene-data-persistence |
| `lastGridY` | `int` | `PlayerMovement` (onBeforeSceneUnload) | scene-data-persistence |
| `lastDirection` | `Direction` | `PlayerMovement` (onBeforeSceneUnload) | scene-data-persistence |
| `returningFromBattle` | `boolean` | `BattleManager` (before transition) | scene-data-persistence |
| `playerName` | `String` | New game setup | pokemon-ecs |
| `money` | `int` | `PlayerInventoryComponent` (write-through) | item-inventory |
| `team` | `List<PokemonInstanceData>` | `PlayerPartyComponent` (write-through) | pokemon-ecs |
| `inventory` | `InventoryData` | `PlayerInventoryComponent` (write-through) | item-inventory |
| `boxes` | `List<List<PokemonInstanceData>>` | `PokemonStorageComponent` (write-through) | pokemon-ecs |
| `boxNames` | `List<String>` | `PokemonStorageComponent` (write-through) | pokemon-ecs |

### Initial Implementation (this plan)

Plan 0 creates `PlayerData` with all type-independent fields. Fields that depend on types defined in other plans (`PokemonInstanceData`, `InventoryData`) are added by those plans when the types exist.

```java
/**
 * Single source of truth for all cross-scene player state.
 *
 * <p>Stored as a JSON string in {@code SaveManager.globalState} under the "player" namespace,
 * serialized via Gson. {@code SaveManager.save()} writes globalState to disk — so as long as
 * this object is up-to-date in globalState, the save file will be correct.</p>
 *
 * <h3>Persistence patterns</h3>
 * <ul>
 *   <li><b>Write-through</b>: Components that own game state (party, inventory, storage)
 *       call {@link #save()} immediately on every mutation. This guarantees
 *       {@code SaveManager.save()} always captures the latest state.</li>
 *   <li><b>onBeforeSceneUnload</b>: Position data is flushed once at scene transition
 *       by {@code PlayerMovement}, because position changes every few frames and only
 *       matters at scene boundaries. This is the <b>only</b> exception to write-through.</li>
 * </ul>
 *
 * <h3>Adding new fields</h3>
 * <p>When a new plan adds fields to this class:</p>
 * <ol>
 *   <li>Add the field with a sensible default (null for objects, 0 for numbers)</li>
 *   <li>Gson handles missing fields gracefully — old save files deserialize with defaults</li>
 *   <li>The component that owns the field must follow write-through or document why not</li>
 * </ol>
 */
public class PlayerData {
    // --- Position context (scene-data-persistence) ---
    // Written by PlayerMovement via onBeforeSceneUnload
    public String lastOverworldScene;
    public int lastGridX;
    public int lastGridY;
    public Direction lastDirection;
    public boolean returningFromBattle;

    // --- Player identity (pokemon-ecs) ---
    // Set during new game, read by PokemonFactory for OT name
    public String playerName;

    // --- Money (item-inventory) ---
    // Written by PlayerInventoryComponent via write-through
    public int money;

    // --- Box names (pokemon-ecs) ---
    // Written by PokemonStorageComponent via write-through
    public List<String> boxNames;

    // --- Fields added by other plans when their types exist ---
    // team: List<PokemonInstanceData>           — added by pokemon-ecs
    // inventory: InventoryData                  — added by item-inventory
    // boxes: List<List<PokemonInstanceData>>    — added by pokemon-ecs

    // --- Persistence ---
    public static PlayerData load() {
        String json = SaveManager.getGlobal("player", "data", "");
        if (json.isEmpty()) return new PlayerData();
        return Serializer.fromJson(json, PlayerData.class);
    }

    public void save() {
        SaveManager.setGlobal("player", "data", Serializer.toJson(this));
    }
}
```

### Why Gson instead of `toMap()`/`fromMap()`
- New fields are picked up automatically — no manual map key management
- Gson deserializes into typed fields directly — no `Number.intValue()` hacks or `LinkedTreeMap` casting
- The project already has `Serializer.toJson()`/`fromJson()` wrapping a configured Gson instance
- Stored as a JSON string in globalState, which round-trips cleanly (string → Gson serialize → disk → Gson deserialize → string)
- Old save files missing new fields deserialize gracefully (fields get Java defaults)

### PlayerData Save Structure (complete, after all plans)

```json
{
  "lastOverworldScene": "route_1",
  "lastGridX": 12,
  "lastGridY": 8,
  "lastDirection": "DOWN",
  "returningFromBattle": false,
  "playerName": "Red",
  "money": 3000,
  "boxNames": ["Box 1", "Box 2", "Box 3", "Box 4", "Box 5", "Box 6", "Box 7", "Box 8"],
  "team": [
    {
      "species": "pikachu",
      "nickname": "Sparky",
      "level": 25,
      "exp": 12500,
      "nature": "JOLLY",
      "ivs": { "hp": 28, "atk": 31, "def": 15, "spAtk": 20, "spDef": 22, "spd": 30 },
      "currentHp": 58,
      "statusCondition": "NONE",
      "heldItem": null,
      "moves": [
        { "moveId": "thunderbolt", "maxPp": 15, "currentPp": 12 },
        { "moveId": "quick_attack", "maxPp": 30, "currentPp": 30 }
      ],
      "originalTrainer": "Red",
      "caughtIn": "pokeball"
    }
  ],
  "inventory": {
    "pockets": {
      "MEDICINE": [
        { "itemId": "potion", "quantity": 5 }
      ],
      "POKEBALL": [
        { "itemId": "pokeball", "quantity": 10 }
      ]
    },
    "registeredItems": ["bicycle"]
  },
  "boxes": [[], [], [], [], [], [], [], []]
}
```

---

## Persistence Patterns

Two persistence patterns coexist. Every component that writes to `PlayerData` must follow one of them.

### Write-Through (default for game state)

Every mutation immediately flushes to `PlayerData.save()`. This guarantees `SaveManager.save()` always captures the latest state, regardless of whether a scene transition happened.

```java
// Pattern: every public mutation method ends with flushToPlayerData()
public boolean addItem(String itemId, int quantity) {
    boolean result = inventory.addItem(itemId, quantity);
    if (result) flushToPlayerData();
    return result;
}

private void flushToPlayerData() {
    PlayerData data = PlayerData.load();
    data.inventory = InventoryData.fromInventory(inventory);
    data.money = money;
    data.save();
}
```

**Why it's cheap:** `PlayerData.save()` writes to `SaveManager.globalState` in memory — not to disk. Disk writes only happen on explicit `SaveManager.save()` calls (save menu).

**Used by:** `PlayerInventoryComponent`, `PlayerPartyComponent`, `PokemonStorageComponent`

### onBeforeSceneUnload (exception for position)

Position data is flushed once at scene transition. Write-through would be wasteful because position changes every few frames and only matters at scene boundaries.

```java
// Pattern: override onBeforeSceneUnload in the component that owns the data
@Override
protected void onBeforeSceneUnload() {
    PlayerData data = PlayerData.load();
    data.lastOverworldScene = gameObject.getScene().getName();
    data.lastGridX = movement.getGridX();
    // ...
    data.save();
}
```

**Used by:** `PlayerMovement` (only)

### Decision Guide

| Question | Answer |
|----------|--------|
| Does the state change frequently? (every frame/tick) | Use `onBeforeSceneUnload` |
| Could the player save mid-scene and expect this state? | Use write-through |
| Is there a discrete user action that changes it? (buy item, catch pokemon) | Use write-through |
| Does it only matter when switching scenes? | Use `onBeforeSceneUnload` |

In practice, **position is the only state that uses `onBeforeSceneUnload`**. Everything else uses write-through.

---

## `onBeforeSceneUnload()` — Component Lifecycle Hook

A new lifecycle hook on `Component`, following the same pattern as `onStart()`, `onDestroy()`, etc. Called on all started, enabled components before the current scene is destroyed.

```java
// Component.java — new protected hook
protected void onBeforeSceneUnload() { }
```

**Trigger path:** `SceneManager` calls a new method on `Scene` which iterates all game objects and their components:

```java
// Scene.java
public void notifyBeforeUnload() {
    for (GameObject go : new ArrayList<>(gameObjects)) {
        notifyBeforeUnloadRecursive(go);
    }
}

private void notifyBeforeUnloadRecursive(GameObject go) {
    for (Component comp : new ArrayList<>(go.getAllComponents())) {
        if (comp.isStarted() && comp.isEnabled()) {
            try {
                comp.onBeforeSceneUnload();
            } catch (Exception e) {
                Log.error(comp.logTag(), "onBeforeSceneUnload() failed", e);
            }
        }
    }
    for (GameObject child : new ArrayList<>(go.getChildren())) {
        notifyBeforeUnloadRecursive(child);
    }
}
```

**Why a Component lifecycle method instead of SceneLifecycleListener?**
- Follows existing patterns — `onStart()`, `onDestroy()`, `onEnable()`, `onDisable()` all work this way
- No manual registration/unregistration boilerplate
- Any component can override it — not just player components
- Future uses: NPC AI could pause pathing, audio components could stop sounds, etc.

---

## `onPostSceneInitialize()` — Scene Lifecycle Hook

A new hook on `SceneLifecycleListener` that fires **after** `scene.initialize()` + `applyCameraData()` but **before** `teleportPlayerToSpawn()`. This is the window where game code can act on a fully-initialized scene before spawn-point teleportation potentially overwrites positions.

```java
// SceneLifecycleListener.java — add default method
public interface SceneLifecycleListener {
    void onSceneLoaded(Scene scene);
    void onSceneUnloaded(Scene scene);
    default void onPostSceneInitialize(Scene scene) {}  // NEW
}
```

Default empty implementation so existing listeners (`SaveManager`, `MusicManager`) require no changes.

**Why this hook exists:** SceneManager should not contain game logic. This hook provides a well-timed extension point where game code subscribes to run between initialization and spawn teleportation. SceneManager just fires hooks.

---

## SceneManager — Updated `loadSceneInternal()` Flow

```java
private void loadSceneInternal(Scene scene, String spawnId) {
    List<GameObjectData> snapshots = Collections.emptyList();
    if (currentScene != null) {
        currentScene.notifyBeforeUnload();                    // NEW — component lifecycle hook
        snapshots = snapshotPersistentEntities(currentScene); // Existing (removed by scene-data-persistence plan)
        currentScene.destroy();
        fireSceneUnloaded(currentScene);
    }

    currentScene = scene;
    currentScene.initialize(viewportConfig, renderingConfig);
    // └─ All onStart() run here. GridMovement registers with collision system.

    if (scene instanceof RuntimeScene runtimeScene) {
        applyCameraData(runtimeScene);
    }

    restorePersistentEntities(currentScene, snapshots);       // Existing (removed by scene-data-persistence plan)

    firePostSceneInitialize(currentScene);                    // NEW — game code hook

    if (spawnId != null && !spawnId.isEmpty()) {
        teleportPlayerToSpawn(currentScene, spawnId);
    }

    fireSceneLoaded(currentScene);
}
```

---

## Package Layout

```
com.pocket.rpg/
├── save/
│   └── PlayerData.java              # NEW — cross-scene player state
│
├── components/
│   └── Component.java               # MODIFIED — add onBeforeSceneUnload()
│
├── scenes/
│   ├── Scene.java                   # MODIFIED — add notifyBeforeUnload()
│   ├── SceneManager.java            # MODIFIED — call hooks in loadSceneInternal
│   └── SceneLifecycleListener.java  # MODIFIED — add onPostSceneInitialize()
```

---

## Phases

### Phase 1: PlayerData Class
- [ ] Create `PlayerData` in `com.pocket.rpg.save` (or appropriate package)
- [ ] Fields: `lastOverworldScene`, `lastGridX`, `lastGridY`, `lastDirection`, `returningFromBattle`, `playerName`, `money`, `boxNames`
- [ ] `load()` reads from `SaveManager.getGlobal("player", "data", "")`, returns fresh instance if empty
- [ ] `save()` writes via `SaveManager.setGlobal("player", "data", Serializer.toJson(this))`
- [ ] Handle empty/null globalState gracefully (return fresh `PlayerData` with defaults)
- [ ] Unit test: in-memory round-trip (`save()` → `load()` preserves all fields)
- [ ] Unit test: disk round-trip (`save()` → `SaveManager.save()` → `SaveManager.load()` → `load()`)
- [ ] Unit test: `load()` with empty globalState returns sensible defaults
- [ ] Unit test: Gson handles missing fields gracefully (simulate old save file without new fields)

### Phase 2: `onBeforeSceneUnload()` Lifecycle Hook
- [ ] Add `protected void onBeforeSceneUnload() {}` to `Component.java`
- [ ] Add `notifyBeforeUnload()` to `Scene.java` — iterates all game objects recursively, calls hook on started+enabled components, catches exceptions per component
- [ ] Add `currentScene.notifyBeforeUnload()` call in `SceneManager.loadSceneInternal()` before `currentScene.destroy()`
- [ ] Guard with `if (currentScene != null)` (first scene load has no previous scene)
- [ ] Unit test: hook fires before destroy, with components still alive
- [ ] Unit test: hook does NOT fire on first scene load (no previous scene)
- [ ] Unit test: exception in one component's hook doesn't prevent others from running

### Phase 3: `onPostSceneInitialize()` Lifecycle Hook
- [ ] Add `default void onPostSceneInitialize(Scene scene) {}` to `SceneLifecycleListener`
- [ ] Add `firePostSceneInitialize(scene)` to `SceneManager` — fires after `initialize()` + `applyCameraData()`, before `teleportPlayerToSpawn()`
- [ ] Verify existing listeners (`SaveManager`, `MusicManager`) compile without changes (default method)
- [ ] Unit test: `onPostSceneInitialize` fires after initialize, before `teleportPlayerToSpawn`
- [ ] Unit test: existing `onSceneLoaded` still fires after everything (ordering preserved)

---

## Files to Change

| File | Change | Phase |
|------|--------|-------|
| `PlayerData.java` | **NEW** — Player state data class with Gson serialization | 1 |
| `Component.java` | Add `protected void onBeforeSceneUnload() {}` | 2 |
| `Scene.java` | Add `notifyBeforeUnload()` — iterates components, calls hook | 2 |
| `SceneManager.java` | Call `notifyBeforeUnload()` before destroy; add `firePostSceneInitialize()` after init | 2, 3 |
| `SceneLifecycleListener.java` | Add `default void onPostSceneInitialize(Scene scene) {}` | 3 |

---

## Acceptance Criteria

- [ ] `PlayerData` round-trips through Gson without data loss (in-memory and full disk cycle)
- [ ] `PlayerData.load()` returns sensible defaults when globalState is empty (new game)
- [ ] Gson deserializes old save files (missing new fields) without errors — fields get Java defaults
- [ ] `onBeforeSceneUnload()` fires on all started+enabled components before scene destruction
- [ ] `onBeforeSceneUnload()` exception in one component does not prevent others from running
- [ ] `onPostSceneInitialize()` fires after scene init + camera data, before spawn teleport
- [ ] Existing `SceneLifecycleListener` implementations compile without changes (default method)
- [ ] All existing tests pass (no regressions in Component, Scene, SceneManager, SaveManager)

## Testing Plan

### New Unit Tests
- `PlayerData` in-memory round-trip: `save()` → `load()` preserves all fields
- `PlayerData` disk round-trip: `save()` → `SaveManager.save()` → `SaveManager.load()` → `load()` preserves all fields
- `PlayerData.load()` with empty globalState: returns fresh instance with null/default fields
- `PlayerData` Gson forward-compatibility: deserialize JSON missing new fields → defaults applied
- `onBeforeSceneUnload()` fires on all started+enabled components before destroy
- `onBeforeSceneUnload()` does NOT fire on first scene load (no previous scene)
- Exception in one component's `onBeforeSceneUnload()` doesn't prevent others from running
- `onPostSceneInitialize()` fires after `initialize()`, before `teleportPlayerToSpawn()`
- `onPostSceneInitialize()` does not break existing `onSceneLoaded` ordering

### Existing Tests That Must Pass
- All `Component` lifecycle tests (onStart, onDestroy, onEnable, onDisable)
- All `Scene` tests (initialization, game object management)
- All `SceneManager` tests (scene loading, lifecycle listeners, camera data)
- All `SaveManager` tests (globalState read/write, save/load cycle)

### Manual Tests
- Run editor — verify no regressions in scene loading
- Run game — verify scene transitions still work
- New game → verify `PlayerData.load()` returns clean defaults
