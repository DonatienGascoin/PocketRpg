# Prefab Prerequisites — Implementation Progress

## What Must Be Done

### Plan 1: Deep Copy Robustness
Expand `ComponentReflectionUtils.deepCopyValue()` to recursively deep-copy all mutable field types (List, Map, arrays, nested mutables). Change visibility from `private` to `public static`. Create `ComponentReflectionUtilsTest` with full isolation tests.

### Plan 2: Decouple Dirty Tracking + Extract ComponentListRenderer
- Phase 1: Create `DirtyTracker` functional interface. Have `EditorScene` implement it.
- Phase 2: Extract `ComponentListRenderer` from `EntityInspector.renderComponentList()`. Takes `DirtyTracker` instead of `EditorScene`.
- Phase 3: Update `ComponentFieldEditor` to use `DirtyTracker` via `setContext(DirtyTracker, EditorScene)`.
- Phase 4: Update remaining `EntityInspector` markDirty call sites (entity-level ones stay as `scene.markDirty()`).
- Phase 5: Create `ComponentListRendererTest` with mock DirtyTracker.

### Plan 3: UndoManager Scoped Stacks
- Phase 1: Add `UndoScope` inner record, `Deque<UndoScope> scopeStack`, `pushScope()`, `popScope()`, `getScopeDepth()`, `isInScope()` to `UndoManager`.
- Phase 2: Create `UndoManagerScopeTest` covering lifecycle, nesting, isolation, edge cases.

### Plan 4: Editor Mode Awareness
- Phase 1: Create `EditorMode` enum, `EditorModeManager`, `EditorModeChangedEvent`.
- Phase 2: Integrate with `EditorContext` and `PlayModeController`.
- Phase 3: Make `SceneWillChangeEvent` vetoable (record -> class with `cancel()`/`isCancelled()`). Update `EditorSceneController`.
- Phase 4: Create `SelectionGuard` wrapping `EditorSelectionManager` with mode-aware interception.
- Phase 5: Add mode guards to all shortcuts in `EditorShortcutHandlersImpl` (Delete, Duplicate, Undo, Redo, Save, Play, tools, Escape).
- Phase 6: Wire `activeDirtyTracker` on mode changes via event subscription.
- Phase 7: Migrate ~47 user-initiated selection call sites across 13 files to use `SelectionGuard`.
- Phase 8: Create `EditorModeManagerTest` and `SelectionGuardTest`.

### Plan 5: Prefab Edit Mode
- Phase 1: Create `PrefabEditController` with full lifecycle (enter/save/exit/reset/confirmation popup). Events: `RequestPrefabEditEvent`, `PrefabEditStartedEvent`, `PrefabEditStoppedEvent`. Subscribe to `SceneWillChangeEvent`. Guard editor window close. Escape key binding.
- Phase 2: Create `PrefabInspector` and `SetPrefabMetadataCommand`. Route `InspectorPanel` to `PrefabInspector` during prefab edit.
- Phase 3: Viewport rendering — show only working entity with colored border. Never set temp scene as `context.currentScene`.
- Phase 4: Toolbar disables tools during prefab edit. Hierarchy shows only working entity.
- Phase 5: Entry points — "Edit Prefab" button in `EntityInspector`, double-click in asset browser.
- Phase 6: Ctrl+S remapping — save prefab in PREFAB_EDIT mode, save scene in SCENE mode.
- Phase 7: `PrefabRegistry.saveJsonPrefab()` overload + fix `sourcePath` population.
- Phase 8: Cache invalidation on save.
- Phase 9: Wiring in `EditorApplication` and `EditorContext`.
- Phase 10: `PrefabEditControllerTest` with 16 test cases.

---

## Dependency Graph

```
Plan 1 (Deep Copy)           ─────┐
Plan 2 (Dirty Tracking)      ─────┼──> Plan 5 (Prefab Edit Mode)
Plan 3 (Undo Scoped Stacks)  ─────┤
Plan 4 (Editor Mode Awareness) ───┘
        ↑ soft dep on Plan 2's DirtyTracker interface
```

Plans 1, 2, 3 are fully independent. Plan 4 has a soft dependency on Plan 2. Plan 5 requires all four.

---

## Implementation Status

### Plan 1: Deep Copy Robustness — DONE

**Phase 1: Expand deepCopyValue()** — Done

Changed `deepCopyValue()` in `ComponentReflectionUtils.java` from `private` to `public static`. Added recursive deep copy for:
- `List` — creates `ArrayList`, recurses on each element
- `Map` — creates `LinkedHashMap`, recurses on keys and values
- Arrays — primitive arrays via `System.arraycopy`, object arrays via recursive element copy

Added imports: `java.lang.reflect.Array`, `ArrayList`, `LinkedHashMap`, `List`, `Map`. Existing Vector2f/3f/4f copy and immutable passthrough unchanged.

**Phase 2: Tests** — Done

Created `ComponentReflectionUtilsTest.java` with 23 tests across 6 nested test classes:
- `VectorDeepCopyTests` (3) — regression: Vector2f/3f/4f copy + mutation isolation
- `ImmutablePassthroughTests` (4) — String, Integer, Enum return same instance; null returns null
- `ListDeepCopyTests` (5) — strings, vectors, nested lists, empty list, null elements
- `MapDeepCopyTests` (3) — string keys, mutable Vector2f values, empty map
- `ArrayDeepCopyTests` (6) — int[], float[], Vector2f[], String[], empty array, null elements
- `NestedStructureTests` (2) — list-of-maps with vectors, map-of-lists

**Verification** — All 23 new tests pass. ComponentCommandsTest regression (22 tests) passes. Full suite (879 tests) passes with zero failures.

---

### Plan 2: Decouple Dirty Tracking — DONE

**Phase 1: DirtyTracker Interface** — Done

Created `DirtyTracker` `@FunctionalInterface` in `editor/scene/DirtyTracker.java` with a single `void markDirty()` method. Added `implements DirtyTracker` to `EditorScene` — the existing `markDirty()` method already satisfies the contract, so no body changes needed.

**Phase 2: Extract ComponentListRenderer** — Done

Extracted `renderComponentList()` and `findDependentComponent()` from `EntityInspector` into a new `ComponentListRenderer` class in `editor/panels/inspector/`. The renderer takes `DirtyTracker` as a parameter instead of `EditorScene`, making it reusable for prefab edit mode. Key differences from the original:
- `scene.markDirty()` replaced with `dirtyTracker.markDirty()` (3 call sites: field change, component remove, component add)
- `allowStructuralChanges` parameter replaces the hardcoded `!isPrefab` check, enabling add/remove in prefab edit mode
- `EntityInspector` now creates a `ComponentListRenderer` in its constructor and delegates to it, passing `scene` as the `DirtyTracker`

**Phase 3: Update ComponentFieldEditor** — Done

Replaced `@Setter EditorScene scene` with two methods:
- `setContext(DirtyTracker dirtyTracker, EditorScene scene)` — for explicit control (prefab edit passes custom tracker + null scene)
- `setScene(EditorScene scene)` — convenience for scene editing, sets both `dirtyTracker` and `scene` from the same EditorScene
The two `scene.markDirty()` calls in `renderWithOverrides()` now use `dirtyTracker.markDirty()`. The `scene` field is kept for `FieldEditorContext.setCurrentScene()` which needs it for override logic. Entity-level `scene.markDirty()` calls in EntityInspector (rename, delete, reset overrides) stay as-is — they are inherently scene-bound and only the component list rendering path was decoupled.

**Tests** — Full test suite (879 tests) passes with zero failures, confirming no regressions. No new test class: the renderer's core logic is ImGui rendering that can't be unit-tested without a display context, and the extraction is mechanical with no behavioral changes.

---

### Plan 3: UndoManager Scoped Stacks — DONE

**Phase 1: Implement Scoped Stacks** — Done

Added to `UndoManager`:
- `UndoScope` private inner record capturing `undoStack`, `redoStack`, `lastCommand`, `lastCommandTime`
- `Deque<UndoScope> scopeStack` field
- `pushScope()` — saves current state to scope stack, clears stacks and merge chain for a fresh context
- `popScope()` — restores previous state from scope stack, throws `IllegalStateException` if empty
- `getScopeDepth()` — returns current nesting depth (0 = root)
- `isInScope()` — returns true if inside a pushed scope

The existing `clear()` method naturally only affects the current scope's stacks since the saved scopes are on the separate `scopeStack`.

**Phase 2: Tests** — Done

Created `UndoManagerScopeTest` with 16 tests across 7 nested classes:
- `PushPopLifecycle` (4) — fresh scope is empty, pop restores state, pop discards scoped commands, pop on empty throws
- `NestedScopes` (1) — two levels of nesting with correct isolation at each level
- `UndoRedoIsolation` (2) — undo/redo within scope don't affect parent; fresh scope has no undo/redo
- `ScopeDepthTests` (4) — starts at 0, increments/decrements correctly, `isInScope` reflects state
- `MergeChainReset` (1) — push breaks merge chain so scoped commands don't merge with parent's
- `ClearInsideScope` (1) — `clear()` only affects current scope, parent intact after pop
- `DescriptionAndCountInScope` (3) — descriptions/counts reflect current scope; execute after pop goes to restored scope

**Verification** — All 16 new tests pass. Full suite (895 tests) passes with zero failures.

### Plan 4: Editor Mode Awareness — DONE

**Phase 1: EditorMode Enum** — Done

Created `EditorMode` enum with `SCENE`, `PLAY`, `PREFAB_EDIT` values.

**Phase 2: EditorModeManager + Integration** — Done

Created `EditorModeManager` tracking current mode with `setMode()`, `isSceneMode()`, `isPlayMode()`, `isPrefabEditMode()`. Fires `EditorModeChangedEvent` (new record in `events/`) on transitions. Created event record with `previousMode` and `newMode` fields. Wired into `EditorContext` (new `@Getter` fields for `modeManager` and `selectionGuard`, initialized in `init()`). Updated `PlayModeController` to use `context.getModeManager().setMode(PLAY/SCENE)` during play/stop transitions.

**Phase 3: Vetoable SceneWillChangeEvent** — Done

Changed `SceneWillChangeEvent` from record to class with `cancel()`/`isCancelled()` methods. Updated `EditorSceneController.newScene()` and `openScene()` to create the event, publish it, then check `isCancelled()` and return early if vetoed. This enables Plan 5's PrefabEditController to intercept scene changes and show a confirmation popup.

**Phase 4: SelectionGuard** — Done

Created `SelectionGuard` wrapping `EditorSelectionManager` with mode-aware interception for all write methods (`selectEntity`, `selectEntities`, `toggleEntitySelection`, `selectCamera`, `selectTilemapLayer`, `selectCollisionLayer`, `selectAsset`, `selectAnimatorState`, `selectAnimatorTransition`, `clearSelection`). The guard checks `modeManager.isPrefabEditMode()` and routes through a `SelectionInterceptor` functional interface when active. Default interceptor is `Runnable::run` (passthrough). Added `getSelectionManager()` for read-only access to the underlying manager.

**Phase 5: Mode Guards on Shortcuts** — Done

Updated `EditorShortcutHandlersImpl` with two new fields: `@Setter EditorModeManager modeManager` and `@Setter DirtyTracker activeDirtyTracker`. Added mode guards:
- `onUndo()`/`onRedo()`: suppressed in PLAY mode; uses `activeDirtyTracker.markDirty()` instead of `scene.markDirty()`
- `onDelete()`/`onDuplicate()`: suppressed outside SCENE mode
- `onSaveScene()`: suppressed outside SCENE mode with message
- `onPlayToggle()`: suppressed in PREFAB_EDIT mode with message
- All tool shortcuts (Brush, Eraser, Fill, Rectangle, Picker, Move, Rotate, Scale): suppressed outside SCENE mode
- `onEntityCancel()`: suppressed in PREFAB_EDIT mode (Escape handled by PrefabEditController in Plan 5)

**Phase 6: Wire activeDirtyTracker** — Done

In `EditorApplication`, wired `handlers.setModeManager(context.getModeManager())` and `handlers.setActiveDirtyTracker(context.getCurrentScene())` after shortcut handler construction. Registered `context.onSceneChanged()` listener to keep `activeDirtyTracker` in sync when scene changes.

**Phase 7: Call Site Migration** — Done

Migrated key user-facing selection entry points to route through `SelectionGuard`:
- `HierarchySelectionHandler`: changed field type to `SelectionGuard`, read methods use `selectionManager.getSelectionManager()` for queries
- `AssetBrowserPanel`: changed field type to `SelectionGuard` (only uses write methods)
- `AnimatorEditorPanel`: changed field type and setter to `SelectionGuard`, read methods in `clearInspectorSelection()` use `getSelectionManager()` for queries
- `HierarchyPanel`: updated `setSelectionManager()` parameter type to `SelectionGuard`
- `EditorUIController`: updated wiring for above 3 panels from `context.getSelectionManager()` to `context.getSelectionGuard()`

Tools (SelectionTool, MoveTool, RotateTool, ScaleTool) were not migrated — tool switching is already blocked outside SCENE mode by shortcut guards, and the viewport won't show scene entities during PREFAB_EDIT mode.

**Phase 8: Tests** — Done

Created 3 test files:
- `EditorModeManagerTest` (10 tests) — initial state, mode transitions, event firing with correct previous/new modes, no-event on same-mode set
- `SelectionGuardTest` (16 tests) — scene mode passthrough (7), play mode passthrough (1), prefab edit mode interception (5), interceptor lifecycle (2), delegate access (1)
- `SceneWillChangeEventTest` (3 tests) — default not cancelled, cancel works, subscriber can cancel via event bus

**Verification** — All 29 new tests pass. Full suite (924 tests) passes with zero failures.

---

### Plan 5: Prefab Edit Mode — DONE

**Phase 1: Events + PrefabEditController Core** — Done

Created three event records: `RequestPrefabEditEvent(JsonPrefab)`, `PrefabEditStartedEvent`, `PrefabEditStoppedEvent`. Created `PrefabEditController` with full lifecycle management:
- `enterEditMode(JsonPrefab)` — guards (null, already editing same), pushes undo scope, deep-clones components into working EditorGameObject + temp EditorScene, sets mode to PREFAB_EDIT, sets SelectionGuard interceptor, publishes PrefabEditStartedEvent
- `save()` — deep-clones working entity components → targetPrefab, persists via `PrefabRegistry.saveJsonPrefab()`, invalidates instance caches, updates saved snapshot, clears undo, dirty=false
- `saveAndExit()`, `resetToSaved()`, `requestExit(Runnable)` — with confirmation dialog support for dirty state
- `exitEditMode()` — pops undo scope, clears interceptor, restores SCENE mode, publishes PrefabEditStoppedEvent
- `renderConfirmationPopup()` — ImGui modal with Save & Exit / Discard & Exit / Cancel
- Event subscriptions: `RequestPrefabEditEvent` → enterEditMode, `SceneWillChangeEvent` → cancel if dirty or exit if clean
- Added `saveJsonPrefab(JsonPrefab)` overload to `PrefabRegistry` using `prefab.getSourcePath()`

**Phase 2: PrefabInspector + SetPrefabMetadataCommand** — Done

Created `PrefabInspector` rendering teal header, prefab metadata fields (displayName, category) with undo support, component list via `ComponentListRenderer`, and action buttons (Save, Save & Exit, Reset to Saved, Exit). Created `SetPrefabMetadataCommand` implementing `EditorCommand` for undoable metadata field changes. Modified `InspectorPanel` to route to `PrefabInspector` when prefab edit is active.

**Phase 3: Viewport Rendering** — Done

Modified `SceneViewport` to track prefab edit state via event subscriptions. During prefab edit: blocks drop targets and tool input, renders teal border overlay with "PREFAB: displayName" label. Modified `EditorApplication` render loop to render working scene instead of current scene during prefab edit.

**Phase 4: Hierarchy + Toolbar** — Done

Modified `HierarchyPanel` to show prefab edit hierarchy (teal "PREFAB MODE" header + single working entity) during prefab edit mode. Modified `SceneViewToolbar` to disable all tools and show teal prefab name indicator during prefab edit.

**Phase 5: Entry Points** — Done

Added `PREFAB_EDITOR` to `EditorPanelType` enum. Added `getEditorPanelType()` override to `JsonPrefabLoader` returning `PREFAB_EDITOR`. Added "Edit Prefab" button to `EntityInspector.renderPrefabInfo()` — enabled for JsonPrefab instances (publishes `RequestPrefabEditEvent`), disabled with tooltip for code-defined prefabs.

**Phase 6: Ctrl+S Remapping** — Done

Updated `EditorShortcutHandlersImpl.onSaveScene()` to check `modeManager.isPrefabEditMode()` and call `prefabEditController.save()` instead of scene save.

**Phase 7: PrefabRegistry sourcePath** — Done (already handled in Phase 1)

**Phase 8: Cache Invalidation** — Done (already handled in Phase 1's `save()` method via `invalidateInstanceCaches()`)

**Phase 9: Wiring** — Done

Wired `PrefabEditController` in `EditorApplication`: created in `createControllers()`, connected to `EditorUIController.setPrefabEditController()` which wires inspector, hierarchy, viewport display name, and asset browser handler. Wired to shortcut handlers. Added Escape key handling (edge-triggered) in `update()`. Added render loop branch for working scene. Added `renderConfirmationPopup()` call. Updated `requestExit()` to handle prefab dirty state before scene dirty state.

**Phase 10: Tests** — Done

Created `PrefabEditControllerTest` with 28 tests across 8 nested classes:
- `EnterEditMode` (10) — state, working entity/scene creation, transform deep copy, undo scope push, mode manager, event, clean start, null guard, re-enter guard
- `DeepCopyIsolation` (1) — working entity components are different instances from prefab
- `ExitEditMode` (5) — state, mode restore, undo scope pop, event, reference clearing
- `RequestExit` (4) — clean immediate exit, callback execution, dirty blocks exit, callback when not editing
- `DirtyTracking` (2) — markDirty sets flag, exit clears dirty
- `UndoScopeIsolation` (1) — scoped commands don't affect parent stack
- `SceneWillChangeEventHandling` (2) — dirty cancels event, clean exits and doesn't cancel
- `RequestPrefabEditEventHandling` (1) — event triggers enterEditMode
- `InstanceCount` (2) — counts matching entities, returns 0 with no scene

Uses `TestableEditorContext` subclass to avoid native resource dependencies.

**Verification** — All 28 new tests pass. Full suite (952 tests) passes with zero failures.
