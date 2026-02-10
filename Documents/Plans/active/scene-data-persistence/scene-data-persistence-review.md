# Review: Scene Data Persistence Plan

## Review Round 1

Three-perspective review of the original plan, followed by a consensus.

### Senior Software Engineer — Round 1

**Issues raised:**
1. **Ownership gap** — "Save before transition" had no defined owner. No `beforeSceneUnload` hook existed.
2. **Coordinate-based loadScene** leaked grid concerns into SceneManager.
3. **Companion entities** unaddressed.
4. **Gson deserialization quirks** would break `fromMap()` after disk round-trip.
5. **~57 tests** to delete/rewrite not mentioned.

### Product Owner — Round 1

**Issues raised:**
1. **Premature removal** — removing a working system before the replacement is exercised.
2. **Scene authoring cost** — every scene needs a player, must be prefab to avoid maintenance burden.
3. **No battle system exists yet** — plan is preparation for unbuilt features.

### QA — Round 1

**Issues raised:**
1. **No disk round-trip test** — in-memory only misses Gson type bugs.
2. **Edge cases undefined** — multi-hop scenes, scenes without player, stale return data.
3. **Crash safety undocumented** — globalState loss on crash not acknowledged.
4. **Editor regression** — deleting PersistentEntity crashes old scene files.

### Round 1 Consensus

1. Build alongside PersistentEntity, remove later
2. Add `beforeSceneUnload` lifecycle hook
3. Drop coordinate-based `loadScene` overload — player self-teleports in `onStart()`
4. Player must be a prefab instance in all scenes
5. Test through full save/load disk cycle
6. Document crash safety as intentional

---

## Review Round 2

Review of the revised plan, which incorporated all Round 1 feedback plus three additional findings from technical investigation:
- `onStart()` runs before `teleportPlayerToSpawn()` — constrains battle return design
- Deleting `PersistentEntity` throws `JsonParseException` on old scene files — needs manual cleanup
- Prefab propagation works (scenes store prefabId + overrides, new components appear on load)

---

### Senior Software Engineer — Round 2

**Status: Approved with minor notes**

The revised plan addresses all Round 1 architecture concerns:

**Resolved:**
- `beforeSceneUnload` hook cleanly replaces the implicit snapshot timing. The `PlayerStateTracker` pattern is sound — automatic flush, no manual save needed in trigger code.
- Coordinate-based `loadScene` overload dropped. Self-teleport in `onStart()` with null spawnId is the right approach. The execution order constraint is clearly documented.
- Gson resilience called out explicitly with specific mitigation (`Number.intValue()`, defensive checks).
- Phased approach (build alongside → migrate → remove) is low-risk.

**Minor notes:**

1. **PlayerStateTracker lifecycle hook binding.** The plan shows `PlayerStateTracker` implementing `onBeforeSceneUnload()`, but this is a `SceneLifecycleListener` method, not a `Component` lifecycle method. The component would need to register itself as a `SceneLifecycleListener` with `SceneManager` in `onStart()` and unregister in `onDestroy()`. This should be noted in Phase 3 to avoid implementation confusion.

2. **Self-teleport and CollisionSystem registration.** When `PlayerStateTracker.onStart()` calls `gridMovement.setGridPosition()`, `GridMovement.onStart()` may or may not have run yet (depends on component ordering on the entity). If `GridMovement.onStart()` hasn't run, the collision system registration hasn't happened, and `setGridPosition()` will fail to update the occupancy map. The plan should note that `PlayerStateTracker` must ensure GridMovement is initialized before self-teleporting — possibly by using `lateStart()` or a deferred callback, or by documenting the required component order.

3. **Phase ordering is good.** Phases 1-4 are the deliverable. Phases 5-6 are deferred. Phase 7 is review. This is a clean separation. Phases 5 and 6 could be combined into a single follow-up plan when the time comes.

---

### Product Owner — Round 2

**Status: Approved**

All Round 1 concerns addressed:

**Resolved:**
- **Build alongside, not replace** — The revised plan explicitly keeps `PersistentEntity` functional during Phases 1-4. Removal is deferred to a separate effort. No risk of breaking existing functionality while proving the new system.
- **Prefab requirement documented** — Dedicated section explains why and confirms the prefab system supports it (prefabId + overrides).
- **Crash safety documented** — Explicit section with clear rationale ("matches Pokemon game behavior").
- **Companion entities acknowledged** — Documented as deferred with a note that the architecture doesn't prevent it.
- **Manual migration over automation** — With only 3 scenes and 3 prefabs, manually editing scene files to remove PersistentEntity is simpler and safer than building migration map infrastructure. No need for ComponentRegistry changes.

**Observation:** The plan's deliverable scope (Phases 1-4) is well-sized. It introduces `PlayerData`, the lifecycle hook, and `PlayerStateTracker` — all of which are useful infrastructure even before a battle system exists. The battle transition wiring in Phase 4 can be lightweight (a test scene) until the actual battle system is built.

No further concerns.

---

### QA — Round 2

**Status: Approved with two additions**

**Resolved:**
- **Disk round-trip test** — Phase 1 now explicitly includes integration tests through the full `save()` → `load()` → Gson path.
- **Edge cases** — Testing strategy now covers multi-hop (overworld → cutscene → battle → overworld), scenes without player, `newGame()` clearing state, empty globalState defaults.
- **Editor regression** — With only 3 scenes, manual cleanup of PersistentEntity references from scene files is straightforward and eliminates the deserialization crash risk entirely. No migration map needed.
- **Execution order** — Clearly documented, with rationale for null spawnId on battle return.

**Additions requested:**

1. **Test: `beforeSceneUnload` does NOT fire on first scene load.** When the game starts and the first scene loads, `currentScene` is null. Verify `fireBeforeSceneUnload()` is not called (no NPE, no unexpected side effects). The plan's code shows `if (currentScene != null)` which handles this, but an explicit test case is warranted.

2. **Test: `PlayerStateTracker` in a scene without GridMovement.** If somehow `PlayerStateTracker` exists on an entity without `GridMovement` (misconfigured prefab, cutscene scene), `onBeforeSceneUnload()` would NPE on `gm.getGridX()`. The component should handle this gracefully — null check on `getComponent(GridMovement.class)` and skip flushing if absent. Add to Phase 3 tasks.

---

### Round 2 Consensus

**The plan is approved.** Three additions to incorporate:

| # | Addition | Owner | Phase |
|---|----------|-------|-------|
| 1 | Note that `PlayerStateTracker` must register as `SceneLifecycleListener` in `onStart()` / unregister in `onDestroy()` | SE | 3 |
| 2 | Ensure `GridMovement` is initialized before self-teleport (component ordering or deferred call) | SE | 3 |
| 3 | `PlayerStateTracker` null-checks `GridMovement` in `onBeforeSceneUnload()` to handle misconfigured entities | QA | 3 |
| 4 | Manual scene file cleanup (3 scenes, 3 prefabs) replaces migration map approach for PersistentEntity removal | PO | 5-6 |

These are implementation details, not architectural changes. The plan's structure, phasing, and approach are sound.

---

## Review Round 3

The plan was revised again to address five design flaws identified during self-review:

1. `PlayerStateTracker` as `SceneLifecycleListener` required manual registration boilerplate — cross-cutting pattern not used elsewhere
2. Camera bounds not handled on battle return (null spawnId skips `SpawnPoint.applyCameraBounds()`)
3. GridMovement initialization ordering unsolved — self-teleport in `onStart()` could fire before `GridMovement.onStart()`
4. Manual `toMap()`/`fromMap()` serialization would age poorly as `PlayerData` grows
5. `returningFromBattle` flag consumed by components was fragile — could get stuck

**Changes made:**
- `onBeforeSceneUnload()` is now a **Component lifecycle method** (like `onStart()`), called via `Scene.notifyBeforeUnload()` → no registration needed
- Return teleportation moved from `PlayerStateTracker.onStart()` to **`SceneManager.applyReturnPosition()`** — runs after `scene.initialize()` (GridMovement initialized) and before `teleportPlayerToSpawn()` (correct timing)
- Camera bounds verified: `applyCameraData()` already reads `camera.activeBoundsId` from globalState — no new camera code needed
- `PlayerData` serialized via **`Serializer.toJson()`/`fromJson()`** (project's existing Gson wrapper) — no manual map conversion
- `returningFromBattle` flag consumed and cleared by SceneManager in one place

---

### Senior Software Engineer — Round 3

**Status: Approved**

All Round 2 notes resolved:

- **Lifecycle hook binding** — No longer an issue. `onBeforeSceneUnload()` is a standard `Component` method following the same pattern as `onStart()`, `onDestroy()`. Scene iterates game objects, calls the hook. Zero boilerplate for component authors.
- **GridMovement ordering** — Fully solved. `applyReturnPosition()` runs in SceneManager after `scene.initialize()` completes, so all `onStart()` calls are done and GridMovement is registered with the collision system. No component ordering concerns.
- **Separation of concerns** — `PlayerStateTracker` now has a single responsibility (flush position on unload). All read-side logic (return teleport, flag clearing) lives in SceneManager where the execution order is deterministic. Clean split.

The `loadSceneInternal()` flow is now well-ordered:
```
notifyBeforeUnload → destroy → initialize (all onStart) → applyCameraData → applyReturnPosition → teleportPlayerToSpawn
```

Each step has a clear purpose, no step depends on component ordering, and the existing camera bounds mechanism is reused without new code. No further concerns.

---

### Product Owner — Round 3

**Status: Approved**

- **Serialization maintenance eliminated** — `Serializer.toJson()`/`fromJson()` means adding fields to `PlayerData` requires zero serialization code changes. Significant long-term win as inventory, Pokemon team, and quests are added.
- **PlayerStateTracker is trivial** — 10 lines of code, single override, null-safe. Easy for new contributors to understand. Compared to the Round 2 version (listener registration, self-teleport logic, conditional behavior), this is a major simplicity improvement.
- **SceneManager orchestration is explicit** — The `loadSceneInternal()` flow reads top-to-bottom with no hidden callbacks or conditional teleports. Anyone reading the code can understand the full scene transition sequence.

No further concerns.

---

### QA — Round 3

**Status: Approved**

All Round 2 additions are addressed in the revised plan:

- **GridMovement null-check** — `PlayerStateTracker.onBeforeSceneUnload()` null-checks `GridMovement`, and `SceneManager.applyReturnPosition()` also null-checks both the player entity and `GridMovement`. Two layers of safety.
- **First scene load** — `notifyBeforeUnload()` is inside `if (currentScene != null)` — no call on first load. Test case retained in testing strategy.
- **Exception isolation** — `Scene.notifyBeforeUnload()` catches exceptions per-component, matching the existing pattern in `Component.start()` and `Component.destroy()`.

**Additional observations:**

- The `applyReturnPosition()` → `teleportPlayerToSpawn()` ordering means a spawnId always wins over the return flag. This is safe: if someone accidentally passes a spawnId on battle return, the spawn position is used (predictable, not silent corruption). The return flag stays set but is harmlessly overwritten by the spawn teleport, then cleared next time `applyReturnPosition` runs without a spawnId. No stuck state.
- The flag-preservation edge case (scene without player) is explicitly tested and documented. The multi-hop chain (overworld → cutscene → battle → overworld) works correctly because the flag persists until a player-containing scene consumes it.

No further concerns.

---

### Round 3 Consensus

**The plan is approved unanimously with no remaining issues.**

All five design flaws from self-review are resolved. The plan is ready for implementation.

| Aspect | Assessment |
|--------|-----------|
| Architecture | Sound — lifecycle hook follows existing patterns, SceneManager orchestration is deterministic |
| Serialization | Solved — Gson via `Serializer`, no manual map conversion |
| Timing | Solved — `applyReturnPosition()` placed at correct point in `loadSceneInternal()` |
| Camera | Verified — existing `applyCameraData()` handles bounds from globalState |
| Maintainability | Good — `PlayerStateTracker` is 10 lines, `PlayerData` fields auto-serialize |
| Risk | Low — builds alongside PersistentEntity, removal is deferred |
| Test coverage | Comprehensive — unit, integration, manual, and edge case tests defined |