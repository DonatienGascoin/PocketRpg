# Plan: Save-Based Scene Data Persistence

## Overview

### Problem
The `PersistentEntity` snapshot system carries entire entities (all components, all children) across scene transitions via reflection-based cloning. This creates problems:
- **Battle scenes**: The over-world player entity shape (GridMovement, AnimationComponent, InputHandler) is unwanted — only the data matters (inventory, Pokemon team)
- **Round-trip transitions** (over-world → battle → over-world): The snapshot is replaced at each `snapshotPersistentEntities` call, so the player snapshot is lost when returning from battle
- **Implicit data flow**: Component state travels through opaque reflection cloning — hard to reason about what's actually persisted

### Approach
Introduce a `PlayerData` data class stored in `SaveManager.globalState` as the single source of truth for cross-scene player state. Build it **alongside** the existing `PersistentEntity` system, prove it works, then remove the old system in a later phase.

1. Encapsulate player game state in `PlayerData`, stored in `SaveManager.globalState`
2. Every scene that needs a player defines one as a **prefab instance** in its scene file
3. A new `onBeforeSceneUnload()` component lifecycle method lets components flush state automatically
4. `SceneManager` orchestrates return teleportation — components just read/write data, SceneManager handles timing
5. `PersistentEntity` remains functional during migration — removal is a separate final phase

### Core Principle
**`SaveManager.globalState` is the single source of truth for cross-scene data.** Scenes are self-contained; components initialize from global state. Scene-level orchestration stays in SceneManager.

---

## PlayerData — The Data Class

A plain data class holding all player state that matters across scenes. Stored as a **JSON string** in `SaveManager.globalState` under the `"player"` namespace, serialized via the project's existing `Serializer` (Gson wrapper).

```java
public class PlayerData {
    // Position context (for returning to over-world after battle)
    public String lastOverworldScene;    // "route_1"
    public int lastGridX;               // 12
    public int lastGridY;               // 8
    public Direction lastDirection;      // Direction.DOWN
    public boolean returningFromBattle;  // true when exiting battle

    // Game state
    // public List<PokemonData> team;    // Future
    // public InventoryData inventory;   // Future
    // public int gold;                  // Future
    // public QuestLog quests;           // Future

    // Persistence via Serializer (Gson)
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

**Why a data class?**
- Explicit — you can read the class to know exactly what persists
- Testable — no component lifecycle needed to inspect/modify state
- Scene-independent — battle scene reads the same `PlayerData` as over-world
- Debuggable — can log/inspect the entire player state in one place

**Why Gson instead of `toMap()`/`fromMap()`?**
- New fields are picked up automatically — no manual map key management
- Gson deserializes into typed fields directly — no `Number.intValue()` hacks or `LinkedTreeMap` casting
- The project already has `Serializer.toJson()`/`fromJson()` wrapping a configured Gson instance
- Stored as a JSON string in globalState, which round-trips cleanly through the save file (string → Gson serialize → disk → Gson deserialize → string)

---

## `onBeforeSceneUnload()` — Component Lifecycle Method

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

## SceneManager Orchestration

The key design decision: **SceneManager handles return teleportation**, not components. This avoids execution order conflicts (onStart vs teleportPlayerToSpawn) and keeps timing correct.

### New `loadSceneInternal()` Flow

```java
private void loadSceneInternal(Scene scene, String spawnId) {
    List<GameObjectData> snapshots = Collections.emptyList();
    if (currentScene != null) {
        currentScene.notifyBeforeUnload();                    // NEW — lifecycle hook
        snapshots = snapshotPersistentEntities(currentScene); // Existing (kept during migration)
        currentScene.destroy();
        fireSceneUnloaded(currentScene);
    }

    currentScene = scene;
    currentScene.initialize(viewportConfig, renderingConfig);
    // └─ All onStart() run here. GridMovement registers with collision system.

    if (scene instanceof RuntimeScene runtimeScene) {
        applyCameraData(runtimeScene);
        // └─ Restores camera bounds from globalState ("camera.activeBoundsId")
    }

    restorePersistentEntities(currentScene, snapshots);       // Existing (kept during migration)

    applyReturnPosition(currentScene);                        // NEW — battle return teleport

    if (spawnId != null && !spawnId.isEmpty()) {
        teleportPlayerToSpawn(currentScene, spawnId);         // Existing — overwrites if spawnId present
    }

    fireSceneLoaded(currentScene);
}
```

### New `applyReturnPosition()` Method

```java
private void applyReturnPosition(Scene scene) {
    PlayerData data = PlayerData.load();
    if (data == null || !data.returningFromBattle) return;

    GameObject player = findPlayerEntity(scene);  // Uses existing player-finding mechanism
    if (player == null) return;  // No player in this scene (cutscene) — flag stays for next scene

    GridMovement gm = player.getComponent(GridMovement.class);
    if (gm != null) {
        gm.setGridPosition(data.lastGridX, data.lastGridY);
        gm.setFacingDirection(data.lastDirection);
    }

    // Camera bounds already restored by applyCameraData() above (reads globalState)

    data.returningFromBattle = false;
    data.save();
}
```

**Why this solves the ordering problems:**
- Runs after `scene.initialize()` → all `onStart()` complete → GridMovement registered with collision → `setGridPosition()` works
- Runs before `teleportPlayerToSpawn()` → if a spawnId IS provided, it overwrites (correct for normal transitions). If spawnId is null, return position stands.
- Camera bounds already restored by `applyCameraData()` reading `camera.activeBoundsId` from globalState (set by the last SpawnPoint before the battle)
- Flag consumed and cleared by SceneManager immediately — no lingering state in components

**Edge case: overworld → cutscene → battle → overworld.** If a scene loads without a player entity (cutscene), `applyReturnPosition` finds no player, doesn't clear the flag. The flag persists until a scene with a player entity loads. This is correct behavior.

---

## PlayerStateTracker Component

A simple component on the player entity. Its only job: flush position data to `PlayerData` on scene unload.

```java
@ComponentMeta(category = "Core")
public class PlayerStateTracker extends Component {

    @Override
    protected void onBeforeSceneUnload() {
        GridMovement gm = getComponent(GridMovement.class);
        if (gm == null) return;  // Graceful skip if no GridMovement

        PlayerData data = PlayerData.load();
        Scene scene = gameObject.getScene();
        if (scene != null) {
            data.lastOverworldScene = scene.getName();
        }
        data.lastGridX = gm.getGridX();
        data.lastGridY = gm.getGridY();
        data.lastDirection = gm.getFacingDirection();
        data.save();
    }
}
```

**What it doesn't do:**
- No self-teleporting — SceneManager handles that in `applyReturnPosition()`
- No SceneLifecycleListener registration — `onBeforeSceneUnload()` is a standard Component hook
- No battle return logic — just writes, never reads

---

## Transition Examples

### Example 1: Over-world Scene A → Over-world Scene B (walking through a door)

```
Scene A: "town_square" (Player at grid 5,10 facing UP)
Trigger: Player steps on warp tile → startTransition("house_interior", "door_entrance")

┌─ FADING_OUT ──────────────────────────────────────────────┐
│ Screen fading to black                                     │
│ Input.clear() prevents movement                            │
└───────────────────────────────────────────────────────────┘
                            │
                     AT MIDPOINT
                            │
┌─ SceneManager.loadScene("house_interior", "door_entrance") ┐
│                                                             │
│  0. town_square.notifyBeforeUnload()                        │
│     └─ PlayerStateTracker saves position to PlayerData      │
│                                                             │
│  1. town_square.destroy()                                   │
│                                                             │
│  2. house_interior.initialize()                             │
│     └─ Player entity loaded from scene file (prefab)        │
│     └─ All Components.onStart() run                         │
│                                                             │
│  3. applyCameraData() — restores camera bounds              │
│                                                             │
│  4. applyReturnPosition()                                   │
│     └─ returningFromBattle == false → SKIP                  │
│                                                             │
│  5. teleportPlayerToSpawn("door_entrance")                  │
│     └─ Player placed at spawn position                      │
│     └─ facingDirection set from SpawnPoint                   │
│     └─ Camera bounds applied                                │
│                                                             │
└─────────────────────────────────────────────────────────────┘
                            │
┌─ FADING_IN ───────────────────────────────────────────────┐
│ Scene fully ready, screen reveals house_interior           │
└───────────────────────────────────────────────────────────┘
```

---

### Example 2: Over-world → Battle (entering a random encounter)

```
Scene: "route_1" (Player at grid 12,8 facing DOWN)
Trigger: Random encounter fires → startTransition("battle_scene")

┌─ FADING_OUT (battle transition effect) ──────────────────┐
│ Screen fading / battle transition animation                │
└──────────────────────────────────────────────────────────┘
                            │
                     AT MIDPOINT
                            │
┌─ SceneManager.loadScene("battle_scene") ─────────────────┐
│                                                           │
│  0. route_1.notifyBeforeUnload()                          │
│     └─ PlayerStateTracker saves to PlayerData:            │
│        lastOverworldScene = "route_1"                     │
│        lastGridX = 12, lastGridY = 8                      │
│        lastDirection = DOWN                               │
│     (Automatic — encounter trigger only calls             │
│      startTransition, nothing else)                       │
│                                                           │
│  1. route_1.destroy()                                     │
│                                                           │
│  2. battle_scene.initialize()                             │
│     └─ NO player over-world entity                        │
│     └─ BattleManager.onStart():                           │
│        └─ Reads PlayerData (Pokemon team, inventory)      │
│        └─ Sets up battle state                            │
│                                                           │
│  3. applyReturnPosition()                                 │
│     └─ returningFromBattle == false → SKIP                │
│                                                           │
│  4. No teleportPlayerToSpawn (no spawnId)                 │
│                                                           │
└──────────────────────────────────────────────────────────┘
                            │
┌─ FADING_IN ──────────────────────────────────────────────┐
│ Battle scene revealed, battle begins                      │
└──────────────────────────────────────────────────────────┘
```

---

### Example 3: Battle → Over-world (returning after battle)

```
Battle ends: Player won. Pokemon HP reduced, item consumed, XP gained.

┌─ BEFORE TRANSITION ──────────────────────────────────────┐
│ BattleManager writes updated state to PlayerData:         │
│                                                           │
│   playerData.team[0].currentHp = 45;  // was 80          │
│   playerData.inventory.remove("Potion");                  │
│   playerData.team[0].xp += 120;                           │
│   playerData.returningFromBattle = true;                  │
│   playerData.save();                                      │
│                                                           │
│ Read return destination:                                  │
│   scene = playerData.lastOverworldScene;  // "route_1"    │
└──────────────────────────────────────────────────────────┘
                            │
    startTransition("route_1")   ← NO spawnId
                            │
                     AT MIDPOINT
                            │
┌─ SceneManager.loadScene("route_1") ──────────────────────┐
│                                                           │
│  0. battle_scene.notifyBeforeUnload()                     │
│     └─ No PlayerStateTracker in battle → nothing flushed  │
│                                                           │
│  1. battle_scene.destroy()                                │
│                                                           │
│  2. route_1.initialize()                                  │
│     └─ Player entity from scene file (prefab, fresh)      │
│     └─ All Components.onStart() run                       │
│     └─ GridMovement registered with collision system      │
│     └─ NPCs at initial positions (from scene file)        │
│                                                           │
│  3. applyCameraData()                                     │
│     └─ Reads "camera.activeBoundsId" from globalState     │
│     └─ Applies camera bounds (saved before battle)        │
│                                                           │
│  4. applyReturnPosition()                                 │
│     └─ returningFromBattle == true                        │
│     └─ Finds player entity                                │
│     └─ gridMovement.setGridPosition(12, 8) ← WORKS       │
│        (GridMovement.onStart() already ran, collision      │
│         system registered)                                 │
│     └─ gridMovement.setFacingDirection(DOWN)              │
│     └─ Clears flag, saves PlayerData                      │
│                                                           │
│  5. teleportPlayerToSpawn → SKIPPED (no spawnId)          │
│                                                           │
│  Player at (12,8) facing DOWN, with post-battle state.    │
│  Camera bounds correct. GridMovement initialized.         │
└──────────────────────────────────────────────────────────┘
                            │
┌─ FADING_IN ──────────────────────────────────────────────┐
│ Over-world revealed, player exactly where they were       │
└──────────────────────────────────────────────────────────┘
```

---

## NPC Behavior After Battle

**Rule: NPCs revert to scene-file positions after battle.**

When `route_1` reloads after a battle, all entities load fresh from the scene file. NPCs return to their initial positions. This matches Pokemon game behavior and avoids the complexity of persisting every NPC's mid-movement state for a short battle.

**Exception:** Story-critical NPCs that must remember position use `PersistentId` + `ISaveable` (the save system). Their state is stored in `SavedSceneState.modifiedEntities` and restored when the scene loads.

---

## Player Entity as Prefab Instance

**Requirement: The player entity in every scene must be a prefab instance, not a copy-paste.**

The project's prefab system stores `prefabId` + component overrides in scene files. On load, `RuntimeSceneLoader` instantiates the live prefab template and applies overrides. This means:
- Adding a new component to the player prefab automatically propagates to all scenes on load
- Scene files stay small (only overrides, not full component data)
- Component field values default to the prefab template unless overridden

This is essential because every over-world scene needs a player entity. Without prefab instances, updating the player would require editing every scene file.

---

## Crash Safety

**Documented limitation:** `SaveManager.globalState` lives in memory between saves. It is only written to disk on explicit `SaveManager.save()` calls.

If the game crashes during a battle:
- `PlayerData` in memory is **lost**
- The save file on disk contains the last explicit save state
- On restart and load: player is at whatever state the last save captured

**This is intentional and matches Pokemon game behavior** — the player restarts from their last save point. It is not a bug.

---

## Companion Entities

**Deferred, but acknowledged.** The current `PersistentEntity` system supports companions via `entityTag` (e.g. "Companion1"). This plan focuses on the player; companion persistence can follow the same `CompanionData` pattern later. The architecture (globalState + prefab instances + `onBeforeSceneUnload`) does not prevent this.

---

## Phases

### Phase 1: PlayerData Class
- [ ] Create `PlayerData` data class with fields: `lastOverworldScene`, `lastGridX`, `lastGridY`, `lastDirection`, `returningFromBattle`
- [ ] Include placeholder fields for future game state (commented team/inventory/gold)
- [ ] `load()` uses `Serializer.fromJson()`, `save()` uses `Serializer.toJson()` — no manual `toMap()`/`fromMap()`
- [ ] Handle empty/null globalState gracefully (return fresh `PlayerData` with defaults)
- [ ] Unit tests: in-memory round-trip (`save()` → `load()` preserves all fields)
- [ ] Integration tests: full disk round-trip (`save()` → `SaveManager.save()` → `SaveManager.load()` → `load()`)

### Phase 2: `onBeforeSceneUnload()` Lifecycle Hook
- [ ] Add `protected void onBeforeSceneUnload() {}` to `Component.java`
- [ ] Add `notifyBeforeUnload()` to `Scene.java` — iterates all game objects recursively, calls hook on started+enabled components, catches exceptions per component
- [ ] Add `currentScene.notifyBeforeUnload()` call in `SceneManager.loadSceneInternal()` before `currentScene.destroy()`
- [ ] Guard with `if (currentScene != null)` (first scene load has no previous scene)
- [ ] Unit test: hook fires before destroy, with components still alive
- [ ] Unit test: hook does NOT fire on first scene load (no previous scene)
- [ ] Unit test: exception in one component's hook doesn't prevent others from running

### Phase 3: `applyReturnPosition()` in SceneManager
- [ ] Add `applyReturnPosition(Scene scene)` method to SceneManager
- [ ] Call it after `scene.initialize()` and `applyCameraData()`, before `teleportPlayerToSpawn()`
- [ ] Reads `PlayerData.returningFromBattle` — if false, returns immediately
- [ ] Finds player entity, gets `GridMovement`, calls `setGridPosition()` and `setFacingDirection()`
- [ ] Clears `returningFromBattle` flag and saves
- [ ] If no player entity in scene (cutscene), does not clear the flag — persists for next scene
- [ ] Camera bounds: verify `applyCameraData()` already restores from `globalState` — no new camera code needed
- [ ] Unit test: return position applied when flag is true
- [ ] Unit test: spawnId teleport overwrites return position when both present
- [ ] Unit test: flag preserved across scenes without player entities

### Phase 4: PlayerStateTracker Component
- [ ] Create `PlayerStateTracker` component — overrides `onBeforeSceneUnload()` only
- [ ] Null-check `GridMovement` — graceful skip if absent
- [ ] Writes `lastOverworldScene`, `lastGridX`, `lastGridY`, `lastDirection` to PlayerData
- [ ] Add to the player prefab
- [ ] Unit tests: state flushing on scene unload, missing GridMovement (no crash)

### Phase 5: Wire Up Battle Transitions
- [ ] Battle entry: encounter trigger calls `startTransition("battle_scene")` — no spawnId, no manual PlayerData save (`onBeforeSceneUnload` handles it)
- [ ] Battle exit: BattleManager sets `returningFromBattle = true`, writes updated game state, calls `startTransition(playerData.lastOverworldScene)` — no spawnId
- [ ] Overworld-to-overworld: unchanged, spawnId passed, `teleportPlayerToSpawn()` handles position
- [ ] Verify TransitionManager flow works for all three cases
- [ ] Manual testing: walk between scenes, enter/exit battle, verify position and state

### Phase 6: Migrate Existing Scenes (deferred — separate effort)
- [ ] Ensure all 3 over-world scenes have player as prefab instance
- [ ] Manually remove `PersistentEntity` components from all 3 scene files and 3 prefabs
- [ ] Verify overworld-to-overworld transitions work without `PersistentEntity` snapshots
- [ ] Regression test: every existing demo scene transition

### Phase 7: Remove PersistentEntity System (deferred — separate effort)
- [ ] Remove `snapshotPersistentEntities()` and `restorePersistentEntities()` calls from `SceneManager.loadSceneInternal()`
- [ ] Remove `PersistentEntity.java` component
- [ ] Remove `PersistentEntitySnapshot.java` utility
- [ ] Delete or rewrite `PersistentEntitySnapshotTest` (~47 tests) and `SceneManagerPersistenceTest` (~10 tests)
- [ ] Clean up `RuntimeSceneLoader` `sourcePrefabId` auto-set logic
- [ ] Update player-finding mechanism (currently uses `PersistentEntity` tag — needs replacement)
- [ ] Clean up imports across the codebase

### Phase 8: Code Review
- [ ] Review all changes
- [ ] Verify no orphaned references
- [ ] Verify SaveManager.globalState integration is correct
- [ ] Check scene files are self-contained and testable in editor

---

## Files to Change

| File | Change | Phase |
|------|--------|-------|
| `PlayerData.java` | **NEW** — Player state data class with Gson serialization | 1 |
| `Component.java` | Add `protected void onBeforeSceneUnload() {}` | 2 |
| `Scene.java` | Add `notifyBeforeUnload()` — iterates components, calls hook | 2 |
| `SceneManager.java` | Call `notifyBeforeUnload()` before destroy; add `applyReturnPosition()` | 2, 3 |
| `PlayerStateTracker.java` | **NEW** — Flushes player position on scene unload | 4 |
| Player prefab | Add `PlayerStateTracker` component | 4 |
| Scene files (`.scene`) | Remove PersistentEntity from entities | 6 |
| `PersistentEntity.java` | **DELETE** | 7 |
| `PersistentEntitySnapshot.java` | **DELETE** | 7 |
| `RuntimeSceneLoader.java` | Remove sourcePrefabId auto-set for PersistentEntity | 7 |
| `PersistentEntitySnapshotTest.java` | **DELETE** or rewrite | 7 |
| `SceneManagerPersistenceTest.java` | Rewrite for new flow | 7 |
| `TransitionManager.java` | No changes expected | — |
| `SaveManager.java` | No changes expected | — |

---

## Testing Strategy

### Unit Tests
- `PlayerData` in-memory round-trip: `save()` → `load()` preserves all fields
- `PlayerData` disk round-trip: `save()` → `SaveManager.save()` → `SaveManager.load()` → `load()` preserves all fields
- `onBeforeSceneUnload()` fires on all started+enabled components before destroy
- `onBeforeSceneUnload()` does NOT fire on first scene load (no previous scene)
- Exception in one component's `onBeforeSceneUnload()` doesn't prevent others from running
- `applyReturnPosition()` teleports player when `returningFromBattle` is true
- `applyReturnPosition()` skipped when `returningFromBattle` is false
- `applyReturnPosition()` followed by `teleportPlayerToSpawn()` — spawn wins (correct for normal transitions)
- `applyReturnPosition()` preserves flag when no player entity in scene
- `PlayerStateTracker` flushes position/direction on scene unload
- `PlayerStateTracker` handles missing `GridMovement` gracefully (no crash)

### Integration Tests
- Overworld → overworld with spawnId: player at spawn, not at return position
- Battle return with null spawnId: player at return position, not at default
- Multi-hop: overworld → cutscene → battle → overworld: return position survives intermediate scenes

### Manual Tests
- Walk between two over-world scenes, verify position/direction at spawn points
- Enter battle from over-world, exit battle, verify return to correct position
- Verify NPCs reset to initial positions after battle return
- Verify camera bounds correct after battle return

### Edge Case Tests
- Scene without player entity (cutscene): no crash, `returningFromBattle` flag preserved
- `newGame()` clears PlayerData: verify clean state
- `PlayerData.load()` with empty globalState: returns sensible defaults
