# Prefab Prerequisite Plans Review

Review of Plans 1-5 against the original `prefabUpdateDesign.md` and the 17 review findings in `prefabUpdateDesignReview.md`.

---

## Review Findings Coverage Matrix

Verifying every review finding is addressed by exactly one plan.

| # | Finding | Severity | Addressed By | Status |
|---|---------|----------|-------------|--------|
| 1 | SceneWillChangeEvent cannot be cancelled | Critical | Plan 4 Phase 3 | COVERED -- converts record to class with cancel()/isCancelled(), EditorSceneController checks after publish |
| 2 | scene.markDirty() scattered everywhere | Critical | Plan 2 | COVERED -- DirtyTracker interface + ComponentListRenderer extraction |
| 3 | Keyboard shortcuts not guarded | Critical | Plan 4 Phase 5 | COVERED -- mode guards on Delete, Duplicate, Save, tools, play toggle |
| 4 | Hierarchy panel behavior unspecified | High | Plan 5 Phase 4 | COVERED -- show only working entity in hierarchy during prefab edit (deferred hierarchical prefab support) |
| 5 | Shallow clone of mutable sub-objects | High | Plan 1 | COVERED -- recursive deep copy for List, Map, arrays |
| 6 | Ctrl+Z during confirmation popup | High | Plan 5 Phase 1 | COVERED -- UndoManager.enabled = false while modal open |
| 7 | Double-clicking second prefab unspecified | High | Plan 5 Phase 1 | COVERED -- guard in enterEditMode() for switching |
| 8 | No escape hatch | High | Plan 5 Phase 1 | COVERED -- Escape key binding |
| 9 | EditorSelectionManager coupling | Architectural | Plan 4 Phase 4 | COVERED -- SelectionGuard wrapper |
| 10 | PrefabInspector reuse ambiguous | Architectural | Plan 2 Phase 2 | COVERED -- ComponentListRenderer extraction |
| 11 | Undo stash/restore should be scoped | Architectural | Plan 3 | COVERED -- pushScope()/popScope() |
| 12 | Use RequestPrefabEditEvent | Architectural | Plan 5 Phase 1 | COVERED -- event-driven entry points |
| 13 | Ctrl+S should save prefab, not be blocked | UX | Plan 5 Phase 6 | COVERED -- remaps Ctrl+S to prefab save in PREFAB_EDIT mode |
| 14 | No "Save & Exit" button | UX | Plan 5 Phase 2 | COVERED -- saveAndExit() + button |
| 15 | "Reset to Saved" has no confirmation | UX | Plan 5 Phase 1/2 | COVERED -- confirmation dialog |
| 16 | Transform component is a trap | UX | Plan 5 Phase 2 | COVERED -- warning for non-origin defaults |
| 17 | Component reordering in goals but not design | UX | Roadmap | COVERED -- removed from goals, deferred |

**Lower-severity findings**:

| Finding | Addressed By | Status |
|---------|-------------|--------|
| JsonPrefab.sourcePath may be null | Plan 5 Phase 7 | COVERED -- fix population in loadJsonPrefabs() and saveJsonPrefab() |
| Temporary EditorScene isolation | Plan 5 Phase 3 | COVERED -- never set as context.currentScene |
| PlayModeController.play() bypasses guard | Plan 4 Phase 5 | COVERED (after update) -- onPlayToggle() guard + EditorUIController guard |
| External file modification during editing | Not addressed | SEE BELOW |
| No instance count shown | Plan 5 Phase 2 | COVERED -- "N instances in current scene" header |
| Layout shifts with Reset button | Plan 5 Phase 2 | COVERED -- always visible, disabled when clean |

---

## Software Engineer Review

### Plan 1: Deep Copy Robustness

**Verdict**: APPROVED with minor notes.

**Strengths**:
- Code is complete and correct. Recursive approach handles nested mutables properly.
- Visibility change to `public static` is justified for Plan 5's use case.
- Codebase audit confirms only `SpritePostEffect.effects` (List) currently needs this, but defensive coverage is appropriate.

**Issues**:

1. **MINOR -- `PostEffect` type handling**: `SpritePostEffect.effects` is a `List<PostEffect>`. `PostEffect` may itself be mutable. The deep copy calls `deepCopyValue()` on each element, but if `PostEffect` is a custom class that's not a Vector/List/Map/array, it falls through to the `return value` passthrough. **Verify**: Is `PostEffect` immutable? If not, consider adding a clone mechanism for it, or document the limitation.

2. **MINOR -- Generic type erasure**: `List<Object> copy = new ArrayList<>(list.size())` loses the original `List`'s concrete type. If a component field is typed as `ArrayList<String>`, the clone will still be `ArrayList<String>` at runtime (since `new ArrayList<>()` is used). But if it's typed as `LinkedList<String>`, the clone becomes `ArrayList`. This is acceptable since component field serialization doesn't distinguish List implementations.

3. **MINOR -- Thread safety**: `deepCopyValue()` is stateless and can be called from any thread. Good.

### Plan 2: Dirty Tracking Decoupling

**Verdict**: APPROVED with one medium concern.

**Strengths**:
- `DirtyTracker` as `@FunctionalInterface` is clean and enables lambda usage.
- `ComponentListRenderer` extraction is well-scoped -- only the reusable part moves.
- Keeping entity-level markDirty calls on `EntityInspector` is the right call.

**Issues**:

1. **MEDIUM -- ComponentFieldEditor.setContext() API**: The plan proposes `setContext(DirtyTracker, EditorScene)` but `ComponentFieldEditor` currently only uses `scene` for `FieldEditorContext.setCurrentScene(scene)`. In prefab edit mode, `scene` is null. `FieldEditorContext.setCurrentScene(null)` will clear the scene reference, which is used by `FieldEditorContext.getCurrentScene()` for asset pickers and other field editors that need scene context. **Verify**: Do any field editors in `editor/ui/fields/` call `FieldEditorContext.getCurrentScene()` for functionality beyond dirty tracking? If so, they may break when editing a prefab. The plan should document which field editors use the scene reference and confirm they handle null gracefully or don't need a scene during prefab edit.

2. **MINOR -- ComponentBrowserPopup ownership**: `ComponentListRenderer` takes a `ComponentBrowserPopup` in its constructor. Both `EntityInspector` and (future) `PrefabInspector` will need their own popup instances since ImGui popup IDs must be unique per window. The plan already shows `EntityInspector` sharing its popup with `ComponentListRenderer`, and `PrefabInspector` will create its own. This is correct but should be made explicit.

3. **MINOR -- `renderPopup()` call placement**: The plan shows `componentListRenderer.renderPopup()` being called in `EntityInspector.render()`. This must be called AFTER the `begin()/end()` block for the popup to render correctly. The current code already does this (line 98 of EntityInspector), so the extraction should preserve this ordering.

### Plan 3: UndoManager Scoped Stacks

**Verdict**: APPROVED.

**Strengths**:
- Clean API. `pushScope()`/`popScope()` is self-documenting.
- Scoping `lastCommand` and `lastCommandTime` prevents cross-scope merging bugs.
- Edge cases are thoroughly documented.
- `IllegalStateException` on empty pop is correct.

**Issues**:

1. **MINOR -- Memory**: Scope stacks hold references to all commands in the parent scope. For deeply nested scopes (unlikely) or very long editing sessions, this could accumulate memory. Not a practical concern since nesting depth will be 1 (prefab edit) and the stacks are bounded by `maxHistorySize`.

2. **MINOR -- Singleton interaction**: `UndoManager` is a singleton. `pushScope()`/`popScope()` must only be called from the main thread (EDT). Since all editor UI runs on the main thread, this is safe. Consider adding a comment.

### Plan 4: Editor Mode Awareness

**Verdict**: APPROVED with one significant concern.

**Strengths**:
- `EditorMode` enum is extensible for future modes.
- `SelectionGuard` keeps `EditorSelectionManager` clean.
- `SelectionInterceptor` is flexible -- the guard doesn't need to know about PrefabEditController.
- Vetoable `SceneWillChangeEvent` pattern works with the synchronous EventBus.

**Issues**:

1. **SIGNIFICANT -- 47 call site migration is risky**: Migrating ~47 selection call sites across 13 files in one phase creates a large, hard-to-review diff. **Recommendation**: Split Phase 7 into sub-phases by file group:
   - Phase 7a: Hierarchy panel files (HierarchyPanel, HierarchySelectionHandler)
   - Phase 7b: Tool files (SelectionTool, MoveTool, RotateTool, ScaleTool)
   - Phase 7c: Panel files (AssetBrowserPanel, AnimatorEditorPanel, TilesetPalettePanel, CollisionPanel)
   - Phase 7d: Remaining (InspectorPanel, UIDesignerInputHandler, SceneViewToolbar, EditorUIController)
   Each sub-phase is independently testable via manual smoke test.

2. **MEDIUM -- onUndo/onRedo in PLAY mode**: The plan makes undo/redo use `activeDirtyTracker` which is null during PLAY mode. This means `UndoManager.undo()` is still called but nothing marks dirty. During play mode, the UndoManager stacks are the scene's stacks (no scope is pushed). Undoing scene commands during play mode is incorrect -- it modifies the hidden scene. **Recommendation**: Add a guard to suppress undo/redo entirely in PLAY mode:
   ```java
   if (modeManager != null && modeManager.isPlayMode()) return;
   ```

3. **MEDIUM -- `EditorModeChangedEvent` import in record**: The `EditorModeChangedEvent` record references `EditorMode` which is in the `editor` package. The event is in `editor/events`. Verify the import works correctly (it should since `EditorMode` is public).

4. **MINOR -- `PlayModeController.stop()` sets mode to SCENE**: If the editor is somehow in PREFAB_EDIT mode and play mode stop is triggered, it would set mode to SCENE. This shouldn't happen since entering play mode from prefab edit is guarded, but consider adding a defensive check: only set SCENE if previous mode was PLAY.

### Plan 5: Prefab Edit Mode

**Verdict**: APPROVED with several medium concerns.

**Strengths**:
- Comprehensive coverage of all remaining findings.
- Clean integration with Plans 1-4.
- UX decisions from Product Owner review are all incorporated.
- Confirmation popup lifecycle is well-defined.

**Issues**:

1. **SIGNIFICANT -- SceneWillChangeEvent retry**: The plan acknowledges that after cancelling a scene change, the user must re-trigger the action manually. This means: user clicks File > Open Scene "forest.scene", confirmation popup appears, user clicks "Save & Continue", prefab saves, mode exits... but forest.scene is NOT opened. The user must click File > Open Scene again. **Recommendation**: Add a `Runnable retryAction` field to `SceneWillChangeEvent` (or pass the scene path). After the confirmation resolves, execute the retry. This is ~10 additional lines.

2. **MEDIUM -- Editor close guard**: Finding mentions "Editor close" as a guard trigger, but the plan doesn't specify where the close guard is implemented. The window close callback is in `EditorApplication` (GLFW window close callback). This needs an explicit task: subscribe to window close event, cancel if prefab edit dirty, show confirmation.

3. **MEDIUM -- Escape key conflicts**: The plan adds Escape to exit prefab edit mode. But `EditorShortcutHandlersImpl.onEntityCancel()` already uses Escape to deselect entities and clear tile selections. During prefab edit mode, this handler runs first (shortcuts are processed before controller-level key checks). **Recommendation**: Either:
   - Guard `onEntityCancel()` in PREFAB_EDIT mode (Plan 4 should add this)
   - Or have the PrefabEditController check Escape in the ImGui render loop (where it has priority over shortcut dispatch)

4. **MEDIUM -- SavePrefabPopup.trySave() uses cloneComponent()**: The existing `SavePrefabPopup` already uses `ComponentReflectionUtils.cloneComponent()` which calls `deepCopyValue()`. After Plan 1, this automatically benefits from the improved deep copy. No action needed, but worth noting for regression awareness.

5. **MINOR -- Working entity rendering**: The plan says "create a lightweight temporary EditorScene containing just the one entity". The `EditorScene` constructor creates default layers and structures. Consider a minimal constructor or a `createMinimal()` factory to avoid unnecessary allocations for the temporary scene.

---

## QA Analyst Review

### Test Coverage Assessment

| Plan | New Tests | Regression Tests | Manual Tests | Gap? |
|------|-----------|-----------------|--------------|------|
| 1 | ComponentReflectionUtilsTest | ComponentCommandsTest | None needed | No |
| 2 | ComponentListRendererTest | EntityCommandsTest | Inspector smoke test | See below |
| 3 | UndoManagerScopeTest | Existing undo tests | None needed | No |
| 4 | EditorModeManagerTest, SelectionGuardTest | Full suite | Mode transition smoke tests | See below |
| 5 | PrefabEditControllerTest | Full suite | 13 manual verification steps | See below |

### Plan 1: Deep Copy -- QA Notes

1. **Test gap -- PostEffect deep copy**: Add a test case for `SpritePostEffect` specifically, since it's the only component with a mutable List in production. Clone a SpritePostEffect, modify the effects list, verify isolation.

2. **Test gap -- Empty collections**: Test deep copy of empty List, empty Map, zero-length array. These are valid states.

3. **Test gap -- Self-referential structures**: While unlikely in component fields, what happens if a List contains itself? The recursive copy would stack overflow. Document this as an unsupported case (it would indicate a bug in component data).

### Plan 2: Dirty Tracking -- QA Notes

1. **Test gap -- ComponentFieldEditor with null scene**: Add a test where `ComponentFieldEditor.setContext(lambdaTracker, null)` is called. Verify that field editors that don't need a scene still work. This directly tests the prefab edit mode pathway.

2. **Test gap -- Concurrent rendering**: `ComponentListRenderer` is shared by EntityInspector. Verify that switching between entity selection and no selection doesn't leave stale state in the renderer.

3. **Manual test missing**: The plan says "Manual smoke test: open editor, inspect entities" but doesn't specify testing the override asterisks (*) on prefab instances. Add: "Select a prefab instance, verify overridden fields show asterisk, modify a field, verify asterisk appears."

### Plan 3: Undo Scopes -- QA Notes

1. **Test gap -- Description methods in scope**: Add test: push scope, execute command, verify `getUndoDescription()` returns the scoped command's description (not the parent scope's).

2. **Test gap -- getUndoCount/getRedoCount in scope**: Push scope, execute 2 commands, undo 1. Verify `getUndoCount() == 1`, `getRedoCount() == 1`. Pop scope. Verify original counts restored.

3. **Test gap -- execute() after pop**: Pop scope, then execute a new command. Verify it goes to the restored parent scope's stack correctly.

### Plan 4: Mode Awareness -- QA Notes

1. **SIGNIFICANT -- Regression risk from 47 call sites**: Each migrated call site is a potential regression. **Recommendation**: Create a manual smoke test checklist that covers each selection type after migration:
   - [ ] Click entity in hierarchy -> selects correctly
   - [ ] Click camera in hierarchy -> selects correctly
   - [ ] Click tilemap layer -> selects, activates brush tools
   - [ ] Click collision layer -> selects, activates collision tools
   - [ ] Click asset in browser -> shows in inspector
   - [ ] Click animator state -> shows in inspector
   - [ ] Click animator transition -> shows in inspector
   - [ ] Ctrl+click for multi-select -> works
   - [ ] Click in viewport -> selects entity
   - [ ] Drag-select in viewport -> multi-selects
   - [ ] Move/Rotate/Scale tool click -> selects entity first

2. **Test gap -- Event ordering**: The vetoable SceneWillChangeEvent depends on subscriber ordering. If PlayModeController's handler runs before PrefabEditController's handler, play mode stops before the prefab guard can cancel. Since both are registered via `EditorEventBus.subscribe()`, the order depends on registration order. **Verify**: Registration order in EditorApplication initialization. PlayModeController is created first, so its handler registers first and runs first. This means play mode will stop, THEN the prefab guard checks. Since play mode stop sets mode to SCENE, the prefab guard's `isActive()` check may already be false. **This is a bug**: PrefabEditController should register its SceneWillChangeEvent handler with higher priority, or the handler should check `state == EDITING` directly rather than relying on EditorMode.

3. **Test gap -- Mode transition during animation**: What if the user is mid-drag (e.g., dragging an entity with MoveTool) when play mode starts? The tool should cancel the drag. Current code already handles this in PlayModeController, but verify it works with the new mode system.

### Plan 5: Prefab Edit Mode -- QA Notes

1. **SIGNIFICANT -- Confirmation popup interaction matrix**: The plan lists triggers but doesn't specify the test matrix for all combinations. Create:

   | Trigger | Dirty? | Expected Behavior |
   |---------|--------|-------------------|
   | Click entity | Yes | Popup: Save/Discard/Cancel |
   | Click entity | No | Exit prefab edit, select entity |
   | Scene change | Yes | Cancel event, show popup |
   | Scene change | No | Exit prefab edit, scene changes |
   | Play mode | Yes | Show popup |
   | Play mode | No | Exit prefab edit, play starts |
   | Escape | Yes | Show popup |
   | Escape | No | Exit immediately |
   | Close editor | Yes | Show popup |
   | Close editor | No | Exit, editor closes |
   | Double-click another prefab | Yes | Show popup, on resolve re-enter for new prefab |
   | Double-click another prefab | No | Exit, enter new prefab |

2. **Test gap -- Undo to clean state**: Enter prefab edit, make a change (dirty=true), undo (dirty should become false since undo stack is empty). Verify dirty tracking is correct. The plan mentions `dirty = !undoStack.isEmpty()` after undo but this isn't tested explicitly.

3. **Test gap -- Save, then undo**: Enter prefab edit, make change A, make change B, save (dirty=false, undo cleared). Now make change C. Undo. Change C is undone but change A and B are NOT undoable (they were cleared on save). Verify this lifecycle.

4. **Test gap -- Reset to Saved after save**: Enter, make change, save, make another change, Reset to Saved. Should revert to the just-saved state (not the original on-disk state from before the first save). Verify `savedComponents` is updated on save.

5. **Manual test -- Viewport rendering**: The plan doesn't specify how to test the viewport rendering in isolation. Add: "Enter prefab edit mode, verify only the working entity appears in viewport. Pan/zoom the camera. Exit, verify scene entities reappear."

6. **MEDIUM -- Data loss scenario**: User enters prefab edit, makes changes, clicks Save. Save fails (disk error, permissions). `dirty` is set to false by the plan's save() implementation before the persist call completes. **Recommendation**: Only clear dirty after successful persist. Move `dirty = false` after the `PrefabRegistry.saveJsonPrefab()` call, inside a try block.

---

## Cross-Plan Integration Risks

1. **Plan 2 + Plan 4 ordering**: Plan 4 has a soft dependency on Plan 2's `DirtyTracker`. If Plan 4 is implemented first, the `activeDirtyTracker` setter on `EditorShortcutHandlersImpl` will compile but won't have anything meaningful to set. The fallback code (`if (activeDirtyTracker != null)`) handles this gracefully, so parallel implementation is safe.

2. **Plan 4's SelectionGuard + Plan 5's interceptor**: The `SelectionInterceptor` is set by `PrefabEditController` (Plan 5), but the guard infrastructure is in Plan 4. If Plan 4 is tested without Plan 5, the interceptor is always the default passthrough. This means Plan 4's tests can only verify that the guard mechanism works in the abstract (using a mock interceptor), not the actual prefab edit flow.

3. **Plan 1 deep copy + Plan 5 component cloning**: Plan 5's `enterEditMode()` deep-clones the prefab's components. It calls `ComponentReflectionUtils.cloneComponent()` which uses `deepCopyValue()`. Plan 1 must be done first, otherwise the cloned working entity shares mutable fields with the prefab definition.

---

## Missing from All Plans

1. **External prefab file modification during editing** (lower-severity review finding): If the `.prefab.json` file is modified on disk while the editor has it open in prefab edit mode, "Reset to Saved" would reload stale data. **Recommendation**: Document this as a known limitation. A file watcher is out of scope for the initial implementation.

2. **Animator panel interaction during prefab edit**: If the user was editing an animator before entering prefab edit, the animator panel's state persists. When exiting prefab edit, does the animator panel restore correctly? This is likely fine since the animator panel reads from `EditorSelectionManager`, which is restored on mode exit. But it should be verified.

3. **Multi-scene considerations**: If the editor supports multiple scenes in the future, cache invalidation (Plan 5 Phase 8) only covers `context.getCurrentScene()`. Scenes in background tabs would have stale caches. Document this as a known limitation.

---

## PR #9 vs Plan 4 Undo Suppression — Expert Comparison

### PR #9 Summary

PR #9 ("Fix play mode NPE: change inspector entity type to HierarchyItem") changes inspector entity types from `EditorGameObject` to `HierarchyItem` to fix NPEs during play mode. Key pattern:

```java
// New helper in CustomComponentInspector:
protected EditorGameObject editorEntity() {
    return entity instanceof EditorGameObject ego ? ego : null;
}
```

Undo commands are only created when `editorEntity()` returns non-null (i.e., NOT during play mode). In `ReflectionFieldEditor.drawComponent()`, the entity is cast to `EditorGameObject` — if it's a `RuntimeGameObjectAdapter` (play mode), undo is skipped.

### What PR #9 Does and Doesn't Do

| Aspect | PR #9 | Plan 4 |
|--------|-------|--------|
| **Scope** | Inspector-level: prevents NPE when rendering play mode entities | Editor-wide: mode-aware shortcut system |
| **Undo in inspector fields** | Skips undo command creation during play mode (editorEntity() returns null) | Not addressed (inspector is below shortcut layer) |
| **Ctrl+Z shortcut** | NOT addressed — Ctrl+Z still calls `UndoManager.undo()` which pops scene commands | ADDRESSED — suppresses Ctrl+Z/Ctrl+Y entirely in PLAY mode |
| **scene.markDirty() on undo** | NOT addressed — `onUndo()` still calls `scene.markDirty()` unconditionally | ADDRESSED — uses `activeDirtyTracker` (null in PLAY mode) |
| **Delete/Duplicate shortcuts** | NOT addressed | ADDRESSED — suppressed outside SCENE mode |

### Verdict

**PR #9 and Plan 4 are complementary, not conflicting.** They operate at different layers:

```
User presses Ctrl+Z
  → EditorShortcutHandlersImpl.onUndo()  ← Plan 4 guards HERE
    → UndoManager.undo()
      → EditorCommand.undo()  ← operates on scene entities

User edits field in play mode inspector
  → ReflectionFieldEditor.drawComponent()  ← PR #9 guards HERE
    → editorEntity() returns null → no undo command created
```

- PR #9 prevents *new* undo commands from being created during play mode
- Plan 4 prevents *existing* scene undo commands from being executed via Ctrl+Z
- Both are necessary

### Merge Coordination Note

After PR #9 merges, Plan 2's `ComponentFieldEditor` changes must account for the `HierarchyItem` type (PR #9 changed the entity parameter from `EditorGameObject` to `HierarchyItem`). The `ComponentListRenderer` should accept `HierarchyItem` or be aware of the new type. This is a merge coordination issue, not an architectural conflict.

---

## Summary Verdict

| Plan | Engineer | QA | Overall |
|------|----------|-----|---------|
| 1. Deep Copy | APPROVED | APPROVED | Ready to implement |
| 2. Dirty Tracking | APPROVED (verify field editor null scene) | APPROVED (add null scene test) | Ready to implement |
| 3. Undo Scopes | APPROVED | APPROVED (add description/count tests) | Ready to implement |
| 4. Mode Awareness | APPROVED | APPROVED (add regression checklist) | Ready to implement (blocking issues resolved) |
| 5. Prefab Edit Mode | APPROVED | APPROVED (add test matrix) | Ready to implement (blocking issues resolved) |

**Blocking issues** (all resolved in plan updates):
- ~~Plan 4: SceneWillChangeEvent subscriber ordering risk~~ FIXED -- PrefabEditController uses `state == State.EDITING` check instead of modeManager
- ~~Plan 5: Save error handling~~ FIXED -- dirty flag only cleared after successful persist, inside try/catch
- ~~Plan 5: Escape key conflict with existing onEntityCancel() shortcut~~ FIXED -- Plan 4 guards onEntityCancel() in PREFAB_EDIT mode
- ~~Plan 5: Editor close window guard not specified~~ FIXED -- added GLFW window close guard task
- ~~Plan 4: Undo/redo in PLAY mode~~ FIXED -- suppressed entirely in PLAY mode

**Non-blocking improvements** (can be addressed during implementation):
- Plan 4: Split call site migration into sub-phases (13 files, 47 call sites)
- Plan 5: SceneWillChangeEvent retry mechanism (user must re-trigger scene change after confirmation)
- All plans: Additional test cases noted in QA review above
