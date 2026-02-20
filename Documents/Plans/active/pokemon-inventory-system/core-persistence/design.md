# Core Persistence Infrastructure — Plan 0

## Overview

Shared foundation that all other plans depend on. This plan creates the `PlayerData` class, adds two engine-level lifecycle hooks (`onBeforeSceneUnload` and `onPostSceneInitialize`), fixes `ISaveable` state application timing to eliminate component ordering issues, adds `GridMovement` mid-scene persistence via `ISaveable`, and documents the persistence patterns that every plan must follow.

**This plan must be implemented before any other plan.**

## Dependencies

- **None** — this is the foundational layer.

## What This Plan Provides

| Deliverable | Used by |
|-------------|---------|
| `PlayerData` class with `load()`/`save()` | scene-data-persistence, item-inventory, pokemon-ecs, dialogue-rewards |
| `onBeforeSceneUnload()` component lifecycle hook | scene-data-persistence (PlayerMovement position flush) |
| `onPostSceneInitialize()` scene lifecycle hook | scene-data-persistence (BattleReturnHandler), SaveManager (deferred state application) |
| Deferred `ISaveable` state application | SaveManager (fixes component ordering for all ISaveable components) |
| `GridMovement` implements `ISaveable` | Mid-scene save/load (position + direction) |
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

Three persistence patterns coexist. Components that write to `PlayerData` use write-through or `onBeforeSceneUnload`. Components with per-entity state that must survive mid-scene saves use `ISaveable`.

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

### ISaveable (per-entity, per-scene state)

For component state that changes frequently but must survive mid-scene saves. Unlike `PlayerData` (cross-scene global state), `ISaveable` stores state per-entity within a scene's `SavedSceneState`. Requires `PersistentId` on the entity.

- **On save:** `SaveManager.captureEntityState()` calls `getSaveState()` during `SaveManager.save()`
- **On load:** `SaveManager.applyAllSavedStates()` calls `loadSaveState()` in `onPostSceneInitialize` — after all `onStart()` calls complete (see [Deferred ISaveable State Application](#deferred-isaveable-state-application))

```java
// Pattern: implement ISaveable on the component that owns the runtime state
public class GridMovement extends Component implements ISaveable {

    @Override
    public Map<String, Object> getSaveState() {
        return Map.of(
            "gridX", gridX,
            "gridY", gridY,
            "facingDirection", facingDirection.name()
        );
    }

    @Override
    public void loadSaveState(Map<String, Object> state) {
        if (state == null) return;
        if (state.containsKey("gridX") && state.containsKey("gridY")) {
            int savedX = ((Number) state.get("gridX")).intValue();
            int savedY = ((Number) state.get("gridY")).intValue();
            setGridPosition(savedX, savedY);
        }
        if (state.containsKey("facingDirection")) {
            facingDirection = Direction.valueOf((String) state.get("facingDirection"));
        }
    }
}
```

**Why `setGridPosition()` is safe here:** `loadSaveState()` is called from `SaveManager.onPostSceneInitialize()`, which fires after `scene.initialize()` completes. All `onStart()` calls are done — `GridMovement` has already registered with the collision system. See [Deferred ISaveable State Application](#deferred-isaveable-state-application) for details.

**Used by:** `GridMovement` (position + direction), `TrainerComponent` (defeated state, added by pokemon-ecs plan)

### Decision Guide

| Question | Answer |
|----------|--------|
| Does the state change frequently? (every frame/tick) | Use `onBeforeSceneUnload` |
| Could the player save mid-scene and expect this state? | Use write-through |
| Is there a discrete user action that changes it? (buy item, catch pokemon) | Use write-through |
| Does it only matter when switching scenes? | Use `onBeforeSceneUnload` |
| Is it per-entity state that changes frequently and must survive mid-scene saves? | Use `ISaveable` |

In practice, **position is the only state that uses `onBeforeSceneUnload`** for cross-scene persistence. `GridMovement` additionally uses `ISaveable` for mid-scene save/load. Everything else uses write-through.

### How ISaveable and PlayerData Coexist

`GridMovement` position is persisted through two parallel paths that serve different purposes and never conflict:

| Concern | Mechanism | Writes to | When written | When consumed |
|---------|-----------|-----------|-------------|---------------|
| Mid-scene save/load | `ISaveable` on `GridMovement` | `SavedSceneState` (per-scene) | `SaveManager.save()` | `SaveManager.onPostSceneInitialize()` |
| Cross-scene teleport (battle return) | `PlayerData` via `onBeforeSceneUnload` | `SaveData.globalState` (cross-scene) | Scene transition | `BattleReturnHandler.onPostSceneInitialize()` |

**Why no conflicts:**
- They write to **different locations** in the save file (`SavedSceneState` vs `globalState`)
- They are consumed at **different times**: ISaveable state is applied first (SaveManager registered before BattleReturnHandler), then BattleReturnHandler may override position if `returningFromBattle == true`
- When both fire, BattleReturnHandler wins — correct, because battle-return position is more recent than the last save
- `returningFromBattle` is set by BattleManager right before the transition and cleared immediately on return — it would never be `true` in a save file under normal flow

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

## Deferred ISaveable State Application

### Problem: Component Ordering on Load

Currently, `PersistentId.onStart()` calls `SaveManager.registerEntity()` which immediately calls `applySavedStateToEntity()`. This calls `loadSaveState()` on `ISaveable` components — but depending on component list order in the prefab, `GridMovement.onStart()` may not have run yet.

If `GridMovement.onStart()` hasn't run:
- The collision system registration hasn't happened
- `loadSaveState()` calling `setGridPosition()` would call `moveEntity()` on an unregistered entity
- Collision state corruption

Components on the same GameObject start in **insertion order** (order they appear in the serialized component list). There is no ordering guarantee between `PersistentId` and `GridMovement`, and no `lateStart()` mechanism.

### Solution: Split Registration from State Application

`PersistentId.onStart()` registers the entity with SaveManager but does **not** apply saved state. SaveManager applies all saved states in its `onPostSceneInitialize()` handler — after `scene.initialize()` completes and all `onStart()` calls are done.

```java
// SaveManager.java — changes

private boolean savedStatesApplied = false;

static void registerEntity(PersistentId pid) {
    if (instance == null) return;

    String id = pid.getId();
    if (id == null || id.isEmpty()) {
        id = PersistentId.generateId();
        pid.setId(id);
    }

    instance.registeredEntities.put(id, pid);

    // If saved states have already been applied (entity added after scene init),
    // apply immediately — safe because all onStart() calls completed before
    // this entity was added to the scene
    if (instance.savedStatesApplied) {
        instance.applySavedStateToEntity(pid);
    }
}

private void applyAllSavedStates() {
    if (currentSave == null || currentSceneName == null) return;

    // Snapshot: applySavedStateToEntity can destroy entities,
    // which triggers PersistentId.onDestroy() → unregisterEntity(),
    // modifying registeredEntities during iteration
    for (PersistentId pid : new ArrayList<>(registeredEntities.values())) {
        applySavedStateToEntity(pid);
    }
}
```

The `savedStatesApplied` flag handles both timing cases:

| When entity registers | What happens |
|---|---|
| During `scene.initialize()` (normal) | Registers only. State applied later in `onPostSceneInitialize` when all components are started |
| After `scene.initialize()` (dynamic spawn) | `savedStatesApplied == true` → state applied immediately. Safe because the entity's `onStart()` completed before `addGameObject()` returns |

### SaveManager Listener — Updated Implementation

SaveManager's lifecycle listener is updated to use `onPostSceneInitialize` for deferred state application and `onSceneUnloaded` for cleanup:

```java
// In SaveManager.initialize()
sceneManager.addLifecycleListener(new SceneLifecycleListener() {
    @Override
    public void onSceneLoaded(Scene scene) {
        // No-op: currentSceneName set in onPostSceneInitialize,
        // registeredEntities cleared in onSceneUnloaded
    }

    @Override
    public void onSceneUnloaded(Scene scene) {
        instance.registeredEntities.clear();
        instance.savedStatesApplied = false;
    }

    @Override
    public void onPostSceneInitialize(Scene scene) {
        instance.currentSceneName = scene.getName();
        instance.applyAllSavedStates();
        instance.savedStatesApplied = true;
    }
});
```

### Bug Fix: `registeredEntities.clear()` Timing

The existing code clears `registeredEntities` in `onSceneLoaded()`, which fires **after** `scene.initialize()` — wiping entities that `PersistentId.onStart()` just registered. This means `captureCurrentSceneState()` would find no entities on save. The bug hasn't surfaced because no `ISaveable` components exist yet.

**Fix:** Move `registeredEntities.clear()` to `onSceneUnloaded()`. Old scene entities are already unregistered by `PersistentId.onDestroy()`, so the clear is belt-and-suspenders cleanup. New scene registrations survive because they happen after the unload.

### Listener Ordering

SaveManager's `onPostSceneInitialize` must fire **before** `BattleReturnHandler`'s, so that ISaveable state is applied first and battle-return teleportation can override it. This is guaranteed by registration order: `SaveManager.initialize()` is called during application bootstrap, before any game-level listeners (like `BattleReturnHandler`) are registered. `SceneManager.lifecycleListeners` is an `ArrayList` that fires in insertion order.

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
        // └─ SaveManager.onSceneUnloaded: clears registeredEntities, resets savedStatesApplied
    }

    currentScene = scene;
    currentScene.initialize(viewportConfig, renderingConfig);
    // └─ All onStart() run here:
    //    - GridMovement registers with collision system
    //    - PersistentId registers with SaveManager (state NOT applied yet)

    if (scene instanceof RuntimeScene runtimeScene) {
        applyCameraData(runtimeScene);
    }

    restorePersistentEntities(currentScene, snapshots);       // Existing (removed by scene-data-persistence plan)

    firePostSceneInitialize(currentScene);                    // NEW — game code hook
    // └─ SaveManager.onPostSceneInitialize: sets currentSceneName,
    //    applies all ISaveable saved states (all onStart() complete, collision ready)
    // └─ BattleReturnHandler.onPostSceneInitialize: teleports player
    //    if returningFromBattle (overrides ISaveable position)

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
│   ├── PlayerData.java              # NEW — cross-scene player state
│   ├── SaveManager.java             # MODIFIED — deferred state application, listener timing fix
│   └── PersistentId.java            # MODIFIED — register only, no immediate state application
│
├── components/
│   ├── Component.java               # MODIFIED — add onBeforeSceneUnload()
│   └── pokemon/
│       └── GridMovement.java        # MODIFIED — implement ISaveable
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

### Phase 4: Deferred State Application & GridMovement ISaveable

#### SaveManager — Deferred State Application
- [ ] Add `savedStatesApplied` flag to `SaveManager`
- [ ] Add `applyAllSavedStates()` method — iterates snapshot of `registeredEntities`, calls `applySavedStateToEntity()` per entity
- [ ] Update `registerEntity()` — remove immediate `applySavedStateToEntity()` call, apply immediately only if `savedStatesApplied == true` (late registration)
- [ ] Update SaveManager's `SceneLifecycleListener`:
  - `onPostSceneInitialize()`: set `currentSceneName`, call `applyAllSavedStates()`, set `savedStatesApplied = true`
  - `onSceneUnloaded()`: clear `registeredEntities`, set `savedStatesApplied = false`
  - `onSceneLoaded()`: no-op (work moved to the other two hooks)
- [ ] Unit test: ISaveable state applied after all `onStart()` calls complete (no component ordering dependency)
- [ ] Unit test: `loadSaveState()` can safely call `setGridPosition()` (collision system registered)
- [ ] Unit test: late-registered entity (added after scene init) gets state applied immediately
- [ ] Unit test: `captureCurrentSceneState()` finds registered entities after scene load (registeredEntities not cleared prematurely)

#### GridMovement — ISaveable Implementation
- [ ] `GridMovement` implements `ISaveable`
- [ ] `getSaveState()` returns `gridX`, `gridY`, `facingDirection`
- [ ] `loadSaveState()` calls `setGridPosition()` + sets `facingDirection` (safe because deferred application guarantees `onStart()` completed)
- [ ] `loadSaveState()` handles null/missing keys gracefully
- [ ] Unit test: mid-scene save round-trip — `getSaveState()` → `loadSaveState()` preserves position and direction
- [ ] Unit test: full disk round-trip — save mid-scene → `SaveManager.save()` → `SaveManager.load()` → scene reload → position restored
- [ ] Unit test: ISaveable position + BattleReturnHandler coexistence — BattleReturnHandler overrides ISaveable when `returningFromBattle == true`
- [ ] Unit test: ISaveable position stands when `returningFromBattle == false` (normal save/load)

---

## Files to Change

| File | Change | Phase |
|------|--------|-------|
| `PlayerData.java` | **NEW** — Player state data class with Gson serialization | 1 |
| `Component.java` | Add `protected void onBeforeSceneUnload() {}` | 2 |
| `Scene.java` | Add `notifyBeforeUnload()` — iterates components, calls hook | 2 |
| `SceneManager.java` | Call `notifyBeforeUnload()` before destroy; add `firePostSceneInitialize()` after init | 2, 3 |
| `SceneLifecycleListener.java` | Add `default void onPostSceneInitialize(Scene scene) {}` | 3 |
| `SaveManager.java` | Deferred state application in `onPostSceneInitialize()`, move `registeredEntities.clear()` to `onSceneUnloaded()`, add `savedStatesApplied` flag, update `registerEntity()` | 4 |
| `PersistentId.java` | No code change — `registerEntity()` behavior changes in SaveManager | 4 |
| `GridMovement.java` | Implement `ISaveable` — `getSaveState()` / `loadSaveState()` for gridX, gridY, facingDirection | 4 |

---

## Acceptance Criteria

- [ ] `PlayerData` round-trips through Gson without data loss (in-memory and full disk cycle)
- [ ] `PlayerData.load()` returns sensible defaults when globalState is empty (new game)
- [ ] Gson deserializes old save files (missing new fields) without errors — fields get Java defaults
- [ ] `onBeforeSceneUnload()` fires on all started+enabled components before scene destruction
- [ ] `onBeforeSceneUnload()` exception in one component does not prevent others from running
- [ ] `onPostSceneInitialize()` fires after scene init + camera data, before spawn teleport
- [ ] Existing `SceneLifecycleListener` implementations compile without changes (default method)
- [ ] `ISaveable` state applied after all `onStart()` calls — no component ordering dependency between `PersistentId` and `GridMovement`
- [ ] `GridMovement` position/direction round-trips through mid-scene save/load
- [ ] `BattleReturnHandler` correctly overrides ISaveable position when `returningFromBattle == true`
- [ ] ISaveable position stands when `returningFromBattle == false` (normal save/load)
- [ ] `registeredEntities` survives `onSceneLoaded` — `captureCurrentSceneState()` finds entities
- [ ] Late-registered entities (added after scene init) get saved state applied immediately
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
- Deferred state application: ISaveable `loadSaveState()` runs after all `onStart()` calls complete
- Deferred state application: `loadSaveState()` can call `setGridPosition()` with collision system ready
- Late registration: entity added after scene init gets saved state applied immediately via `savedStatesApplied` flag
- `registeredEntities` not cleared prematurely: `captureCurrentSceneState()` finds entities after scene load
- `GridMovement` ISaveable round-trip: `getSaveState()` → `loadSaveState()` preserves gridX, gridY, facingDirection
- `GridMovement` full disk round-trip: save mid-scene → reload → position and direction restored
- ISaveable + BattleReturnHandler coexistence: BattleReturnHandler overrides ISaveable position when `returningFromBattle == true`
- ISaveable + BattleReturnHandler coexistence: ISaveable position stands when `returningFromBattle == false`

### Existing Tests That Must Pass
- All `Component` lifecycle tests (onStart, onDestroy, onEnable, onDisable)
- All `Scene` tests (initialization, game object management)
- All `SceneManager` tests (scene loading, lifecycle listeners, camera data)
- All `SaveManager` tests (globalState read/write, save/load cycle)

### Manual Tests
- Run editor — verify no regressions in scene loading
- Run game — verify scene transitions still work
- New game → verify `PlayerData.load()` returns clean defaults
- Walk to a new position mid-scene, save, reload — verify player at saved position with correct direction
- Walk to a new position, transition to another scene, come back — verify `onBeforeSceneUnload` position used (not stale ISaveable)
