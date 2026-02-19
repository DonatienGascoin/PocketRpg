# Plan: Save-Based Scene Data Persistence

## Overview

### Problem
The `PersistentEntity` snapshot system carries entire entities (all components, all children) across scene transitions via reflection-based cloning. This creates problems:
- **Battle scenes**: The over-world player entity shape (GridMovement, AnimationComponent, InputHandler) is unwanted — only the data matters (inventory, Pokemon team)
- **Round-trip transitions** (over-world → battle → over-world): The snapshot is replaced at each `snapshotPersistentEntities` call, so the player snapshot is lost when returning from battle
- **Implicit data flow**: Component state travels through opaque reflection cloning — hard to reason about what's actually persisted

### Approach
Build on the infrastructure from **core-persistence (Plan 0)** — `PlayerData`, `onBeforeSceneUnload`, `onPostSceneInitialize` — to implement the game-specific persistence behavior: position flushing, battle return teleportation, scene migration, and removal of the old `PersistentEntity` system.

1. `PlayerMovement` flushes position to `PlayerData` via `onBeforeSceneUnload()` (from Plan 0)
2. `BattleReturnHandler` teleports the player back after battle via `onPostSceneInitialize()` (from Plan 0)
3. Every scene that needs a player defines one as a **prefab instance** in its scene file
4. `PersistentEntity` is removed — no two parallel systems

### Dependencies

- **core-persistence (Plan 0)** — `PlayerData`, `onBeforeSceneUnload`, `onPostSceneInitialize`

### Core Principle
**`SaveManager.globalState` is the single source of truth for cross-scene data.** Scenes are self-contained; components initialize from global state. SceneManager handles scene lifecycle and hooks only — game mechanics live in game code via listeners.

---

## Prerequisites from Plan 0

This plan depends on infrastructure from **core-persistence (Plan 0)**:
- **`PlayerData`** — data class with `load()`/`save()`, position fields, persistence patterns
- **`onBeforeSceneUnload()`** — Component lifecycle hook, fires before scene destruction
- **`onPostSceneInitialize()`** — SceneLifecycleListener hook, fires after init but before spawn teleport

See `pokemon-inventory-system/core-persistence/design.md` for the complete definitions.

---

## Battle Return Handler

A game-level `SceneLifecycleListener` implementation that handles return-from-battle teleportation. Registered at game startup (e.g. in `DemoScene` or a future `GameManager`).

```java
public class BattleReturnHandler implements SceneLifecycleListener {

    @Override
    public void onPostSceneInitialize(Scene scene) {
        PlayerData data = PlayerData.load();
        if (data == null || !data.returningFromBattle) return;

        GameObject player = findPlayerEntity(scene);
        if (player == null) return;  // No player in this scene (cutscene) — flag stays for next scene

        GridMovement gm = player.getComponent(GridMovement.class);
        if (gm != null) {
            gm.setGridPosition(data.lastGridX, data.lastGridY);
            gm.setFacingDirection(data.lastDirection);
        }

        // Camera bounds already restored by applyCameraData() (reads globalState)

        data.returningFromBattle = false;
        data.save();
    }

    @Override
    public void onSceneLoaded(Scene scene) { }

    @Override
    public void onSceneUnloaded(Scene scene) { }
}
```

**Why this solves the ordering problems:**
- Runs after `scene.initialize()` → all `onStart()` complete → GridMovement registered with collision → `setGridPosition()` works
- Runs before `teleportPlayerToSpawn()` → if a spawnId IS provided, it overwrites (correct for normal transitions). If spawnId is null, return position stands.
- Camera bounds already restored by `applyCameraData()` reading `camera.activeBoundsId` from globalState (set by the last SpawnPoint before the battle)
- Flag consumed and cleared immediately — no lingering state in components
- SceneManager has zero knowledge of battle returns

**Edge case: overworld → cutscene → battle → overworld.** If a scene loads without a player entity (cutscene), the handler finds no player, doesn't clear the flag. The flag persists until a scene with a player entity loads. This is correct behavior.

---

## Position Flush in `PlayerMovement`

`PlayerMovement` already owns the `GridMovement` reference via `@ComponentReference`. Adding position flushing here is natural — no new component needed.

```java
// Added to PlayerMovement.java
@Override
protected void onBeforeSceneUnload() {
    if (movement == null) return;

    PlayerData data = PlayerData.load();
    Scene scene = gameObject.getScene();
    if (scene != null) {
        data.lastOverworldScene = scene.getName();
    }
    data.lastGridX = movement.getGridX();
    data.lastGridY = movement.getGridY();
    data.lastDirection = movement.getFacingDirection();
    data.save();
}
```

**What it doesn't do:**
- No self-teleporting — `BattleReturnHandler` handles that via `onPostSceneInitialize()`
- No game state — inventory, team, etc. use write-through and manage themselves
- No battle return logic — just writes position, never reads it

---

## `loadSceneInternal()` — Updated Flow

See **core-persistence (Plan 0)** for the full updated `loadSceneInternal()` method. The key additions from Plan 0 that this plan depends on:

1. `notifyBeforeUnload()` — fires before destroy, triggers `PlayerMovement` position flush
2. `firePostSceneInitialize()` — fires after init, triggers `BattleReturnHandler` return teleport
3. `teleportPlayerToSpawn()` — existing, runs after `firePostSceneInitialize`, overwrites if spawnId present

After **Phase 5** of this plan, the `snapshotPersistentEntities` / `restorePersistentEntities` calls are removed from this flow.

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
│     └─ PlayerMovement saves position to PlayerData          │
│                                                             │
│  1. town_square.destroy()                                   │
│                                                             │
│  2. house_interior.initialize()                             │
│     └─ Player entity loaded from scene file (prefab)        │
│     └─ All Components.onStart() run                         │
│                                                             │
│  3. applyCameraData() — restores camera bounds              │
│                                                             │
│  4. firePostSceneInitialize()                               │
│     └─ BattleReturnHandler: returningFromBattle == false    │
│        → SKIP                                               │
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
│     └─ PlayerMovement saves to PlayerData:                │
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
│  3. firePostSceneInitialize()                             │
│     └─ BattleReturnHandler: returningFromBattle == false  │
│        → SKIP                                             │
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
│     └─ No PlayerMovement in battle → nothing flushed      │
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
│  4. firePostSceneInitialize()                             │
│     └─ BattleReturnHandler:                               │
│        └─ returningFromBattle == true                     │
│        └─ Finds player entity                             │
│        └─ gridMovement.setGridPosition(12, 8) ← WORKS    │
│           (GridMovement.onStart() already ran, collision   │
│            system registered)                              │
│        └─ gridMovement.setFacingDirection(DOWN)           │
│        └─ Clears flag, saves PlayerData                   │
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

### Phase 1: Position Flush in `PlayerMovement`
- [ ] Add `onBeforeSceneUnload()` override to `PlayerMovement`
- [ ] Null-check `GridMovement` reference — graceful skip if absent
- [ ] Writes `lastOverworldScene`, `lastGridX`, `lastGridY`, `lastDirection` to PlayerData
- [ ] Unit test: position flushed on scene unload
- [ ] Unit test: missing GridMovement (no crash)

### Phase 2: Battle Return Handler
- [ ] Create `BattleReturnHandler` implementing `SceneLifecycleListener`
- [ ] `onPostSceneInitialize()`: reads `PlayerData.returningFromBattle`, finds player entity, teleports via `GridMovement.setGridPosition()` + `setFacingDirection()`
- [ ] Clears `returningFromBattle` flag and saves
- [ ] If no player entity in scene (cutscene), does not clear the flag — persists for next scene
- [ ] Register handler in game startup (DemoScene or future GameManager)
- [ ] Camera bounds: verify `applyCameraData()` already restores from `globalState` — no new camera code needed
- [ ] Unit test: return position applied when flag is true
- [ ] Unit test: spawnId teleport overwrites return position when both present
- [ ] Unit test: flag preserved across scenes without player entities

### Phase 3: Wire Up Battle Transitions
- [ ] Battle entry: encounter trigger calls `startTransition("battle_scene")` — no spawnId, no manual PlayerData save (`onBeforeSceneUnload` handles it)
- [ ] Battle exit: BattleManager sets `returningFromBattle = true`, writes updated game state, calls `startTransition(playerData.lastOverworldScene)` — no spawnId
- [ ] Overworld-to-overworld: unchanged, spawnId passed, `teleportPlayerToSpawn()` handles position
- [ ] Verify TransitionManager flow works for all three cases
- [ ] Manual testing: walk between scenes, enter/exit battle, verify position and state

### Phase 4: Migrate Existing Scenes
- [ ] Ensure all over-world scenes have player as prefab instance
- [ ] Remove `PersistentEntity` components from scene files and prefabs
- [ ] Verify overworld-to-overworld transitions work without `PersistentEntity` snapshots
- [ ] Regression test: every existing demo scene transition

### Phase 5: Remove PersistentEntity System
- [ ] Remove `snapshotPersistentEntities()` and `restorePersistentEntities()` calls from `SceneManager.loadSceneInternal()`
- [ ] Remove `PersistentEntity.java` component
- [ ] Remove `PersistentEntitySnapshot.java` utility
- [ ] Delete or rewrite `PersistentEntitySnapshotTest` (~47 tests) and `SceneManagerPersistenceTest` (~10 tests)
- [ ] Clean up `RuntimeSceneLoader` `sourcePrefabId` auto-set logic
- [ ] Update player-finding mechanism (currently uses `PersistentEntity` tag — needs replacement, e.g. a `PlayerTag` component or scene query by component type)
- [ ] Clean up imports across the codebase

### Phase 6: Documentation & Code Review
- [ ] Review all changes
- [ ] Verify no orphaned references
- [ ] Verify SaveManager.globalState integration is correct
- [ ] Check scene files are self-contained and testable in editor
- [ ] Update `.claude/reference/architecture.md` — new lifecycle hooks, PlayerData, persistence strategy
- [ ] Update `.claude/reference/common-pitfalls.md` — write-through vs onBeforeSceneUnload rules
- [ ] Ask user about Encyclopedia guide updates

---

## Files to Change

Infrastructure files (`PlayerData`, `Component`, `Scene`, `SceneLifecycleListener`, `SceneManager`) are handled by **core-persistence (Plan 0)**.

| File | Change | Phase |
|------|--------|-------|
| `PlayerMovement.java` | Add `onBeforeSceneUnload()` — flush position to PlayerData | 1 |
| `BattleReturnHandler.java` | **NEW** — Handles return-from-battle teleportation via `onPostSceneInitialize()` | 2 |
| `DemoScene.java` (or GameManager) | Register `BattleReturnHandler` as SceneLifecycleListener | 2 |
| Scene files (`.scene`) | Remove PersistentEntity from entities | 4 |
| Player prefab | Remove PersistentEntity component | 4 |
| `PersistentEntity.java` | **DELETE** | 5 |
| `PersistentEntitySnapshot.java` | **DELETE** | 5 |
| `RuntimeSceneLoader.java` | Remove sourcePrefabId auto-set for PersistentEntity | 5 |
| `PersistentEntitySnapshotTest.java` | **DELETE** or rewrite | 5 |
| `SceneManagerPersistenceTest.java` | Rewrite for new flow | 5 |
| `.claude/reference/architecture.md` | Update persistence strategy | 6 |
| `.claude/reference/common-pitfalls.md` | Add write-through vs onBeforeSceneUnload guidance | 6 |
| `TransitionManager.java` | No changes expected | — |
| `SaveManager.java` | No changes expected | — |

---

## Acceptance Criteria

Prerequisites from Plan 0 (tested there): `PlayerData` round-trip, lifecycle hooks fire correctly.

- [ ] `PlayerMovement` flushes position to `PlayerData` on scene unload (no new component needed)
- [ ] `PlayerMovement` handles missing `GridMovement` gracefully (no crash)
- [ ] `BattleReturnHandler` teleports player to saved position when `returningFromBattle` is true
- [ ] `BattleReturnHandler` does NOT clear flag when scene has no player entity (cutscene passthrough)
- [ ] `teleportPlayerToSpawn()` overwrites return position when spawnId is provided (normal transitions win)
- [ ] SceneManager contains zero game logic — all battle-return behavior lives in `BattleReturnHandler`
- [ ] `PersistentEntity` system fully removed — no snapshot/restore calls, no component, no utility class
- [ ] All existing demo scene transitions work after migration
- [ ] `newGame()` clears PlayerData to clean state

---

## Testing Strategy

Infrastructure tests (`PlayerData` round-trips, lifecycle hook ordering) are covered by **core-persistence (Plan 0)**.

### New Unit Tests
- `PlayerMovement.onBeforeSceneUnload()` flushes position/direction to PlayerData
- `PlayerMovement.onBeforeSceneUnload()` handles missing `GridMovement` gracefully (no crash)
- `BattleReturnHandler` teleports player when `returningFromBattle` is true
- `BattleReturnHandler` skipped when `returningFromBattle` is false
- `BattleReturnHandler` + `teleportPlayerToSpawn()` — spawn wins (correct for normal transitions)
- `BattleReturnHandler` preserves flag when no player entity in scene

### Existing Tests That Must Pass
- All `SceneManager` tests (scene loading, lifecycle listeners, camera data)
- All `SaveManager` tests (globalState read/write, save/load cycle)
- `PersistentEntitySnapshotTest` (~47 tests) — must pass until Phase 5 removes them
- `SceneManagerPersistenceTest` (~10 tests) — must pass until Phase 5 rewrites them

### Integration Tests
- Overworld → overworld with spawnId: player at spawn, not at return position
- Battle return with null spawnId: player at return position, not at default
- Multi-hop: overworld → cutscene → battle → overworld: return position survives intermediate scenes

### Manual Tests
- Walk between two over-world scenes, verify position/direction at spawn points
- Enter battle from over-world, exit battle, verify return to correct position
- Verify NPCs reset to initial positions after battle return
- Verify camera bounds correct after battle return
- Save mid-scene, reload, verify PlayerData state is current (write-through contract)
- New game: verify all PlayerData fields are clean defaults

### Edge Case Tests
- Scene without player entity (cutscene): no crash, `returningFromBattle` flag preserved
- `newGame()` clears PlayerData: verify clean state
- `PlayerData.load()` with empty globalState: returns sensible defaults
