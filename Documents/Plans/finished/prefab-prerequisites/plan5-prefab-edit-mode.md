# Plan 5: Prefab Edit Mode

## Overview

**Problem**: Prefab creation is a one-shot operation. Once exported, there is no way to structurally modify a prefab (add/remove components, change defaults). Any iteration requires deleting and re-exporting.

**Approach**: A dedicated Prefab Edit Mode -- the third editor mode alongside Scene and Play. The editor reuses existing panels (viewport, inspector, toolbar) with different content, following the pattern established by Play Mode. With Plans 1-4 complete, the implementation leverages:
- **Deep copy** (Plan 1) -> working entity safely isolated from prefab definition
- **ComponentListRenderer + DirtyTracker** (Plan 2) -> PrefabInspector delegates to ComponentListRenderer with a lambda `() -> controller.setDirty(true)` as DirtyTracker
- **Scoped undo** (Plan 3) -> enter = `pushScope()`, exit = `popScope()`
- **Mode awareness** (Plan 4) -> shortcuts, selections, panels already gated. Vetoable SceneWillChangeEvent enables "Cancel" on scene change popup

**Addresses**: All remaining review findings + the full prefabUpdateDesign.md.

**Requires**: Plans 1, 2, 3, 4.

---

## Phase 1: PrefabEditController

### Files

| File | Change |
|------|--------|
| `src/main/java/com/pocket/rpg/editor/PrefabEditController.java` | **NEW** -- mode lifecycle controller |
| `src/main/java/com/pocket/rpg/editor/events/PrefabEditStartedEvent.java` | **NEW** -- event |
| `src/main/java/com/pocket/rpg/editor/events/PrefabEditStoppedEvent.java` | **NEW** -- event |
| `src/main/java/com/pocket/rpg/editor/events/RequestPrefabEditEvent.java` | **NEW** -- event for entry points |

### Tasks

- [ ] Create `RequestPrefabEditEvent`:

```java
public record RequestPrefabEditEvent(JsonPrefab prefab) implements EditorEvent {}
```

Entry points (asset browser, inspector) publish this event instead of holding a direct reference to `PrefabEditController` (per Architect recommendation #12).

- [ ] Create `PrefabEditStartedEvent` and `PrefabEditStoppedEvent` (simple marker events)

- [ ] Create `PrefabEditController`:

```java
public class PrefabEditController {
    public enum State { INACTIVE, EDITING }

    private State state = State.INACTIVE;
    private final EditorContext context;

    // The prefab being edited
    private JsonPrefab targetPrefab;

    // Working copy: a scratch EditorGameObject built from the prefab's components
    private EditorGameObject workingEntity;

    // Snapshot of the on-disk state (for "Reset to Saved")
    private List<Component> savedComponents;

    // Display name / category snapshots for metadata undo
    private String savedDisplayName;
    private String savedCategory;

    // Dirty tracking
    private boolean dirty = false;

    // Confirmation popup state
    private boolean showConfirmationPopup = false;
    private Runnable pendingAction = null;
    private String confirmationMessage = "";
}
```

- [ ] Implement `enterEditMode(JsonPrefab prefab)`:
  1. If already editing a different prefab, call `requestExit()` with a pending action to re-enter (finding #7)
  2. Stop play mode if active
  3. `UndoManager.getInstance().pushScope()` (Plan 3)
  4. Deep-clone prefab's components into `savedComponents` (using Plan 1's deep copy)
  5. Build scratch `EditorGameObject` from cloned components
  6. Set `context.getModeManager().setMode(EditorMode.PREFAB_EDIT)` (Plan 4)
  7. Set `SelectionGuard` interceptor to route through confirmation flow
  8. Set `activeDirtyTracker` on shortcut handlers to `this::markDirty`
  9. Set `dirty = false`, `state = EDITING`
  10. Publish `PrefabEditStartedEvent`

- [ ] Implement `save()`:
  1. Read workingEntity's current component list
  2. Deep-clone into the `JsonPrefab`'s component list
  3. Update displayName/category if changed
  4. Persist via `PrefabRegistry.saveJsonPrefab()` -- **inside try/catch**
  5. **Only on success**: Invalidate instance caches in current scene
  6. **Only on success**: Update `savedComponents` snapshot (deep clone current state)
  7. **Only on success**: `UndoManager.getInstance().clear()` (edits before save not undoable)
  8. **Only on success**: `dirty = false`
  9. **On failure**: Show error message, leave dirty=true, leave undo history intact

  ```java
  public void save() {
      // 1-3: Prepare data
      List<Component> clonedComponents = /* deep clone working entity components */;
      targetPrefab.setComponents(clonedComponents);
      targetPrefab.setDisplayName(currentDisplayName);
      targetPrefab.setCategory(currentCategory);

      try {
          // 4: Persist
          PrefabRegistry.getInstance().saveJsonPrefab(targetPrefab, deriveFilename());

          // 5-8: Only on success
          invalidateInstanceCaches();
          savedComponents = deepCloneComponents(workingEntity.getComponents());
          savedDisplayName = currentDisplayName;
          savedCategory = currentCategory;
          UndoManager.getInstance().clear();
          dirty = false;

          EditorEventBus.get().publish(new StatusMessageEvent("Prefab saved: " + targetPrefab.getDisplayName()));
      } catch (Exception e) {
          // 9: On failure, preserve state
          EditorEventBus.get().publish(new StatusMessageEvent("Error saving prefab: " + e.getMessage()));
          System.err.println("Failed to save prefab: " + e.getMessage());
          e.printStackTrace();
      }
  }
  ```

- [ ] Implement `saveAndExit()`:
  1. `save()`
  2. `exitEditMode()`

- [ ] Implement `resetToSaved()`:
  1. Show confirmation dialog ("This will discard all changes since last save. Continue?")
  2. On confirm: rebuild workingEntity from `savedComponents`, clear undo, `dirty = false`

- [ ] Implement `requestExit(Runnable afterExit)`:
  If dirty, show confirmation popup with pending action. If clean, exit immediately.

- [ ] Implement `exitEditMode()`:
  1. `UndoManager.getInstance().popScope()` (Plan 3)
  2. Clear SelectionGuard interceptor
  3. Set `context.getModeManager().setMode(EditorMode.SCENE)` (Plan 4)
  4. Restore activeDirtyTracker on shortcut handlers to current scene
  5. Discard workingEntity, targetPrefab refs
  6. `state = INACTIVE`
  7. Publish `PrefabEditStoppedEvent`

- [ ] Implement confirmation popup rendering (`renderConfirmationPopup()`):
  - "Unsaved Prefab Changes" modal
  - Three buttons: "Save & Continue", "Discard", "Cancel"
  - Stores `pendingAction` Runnable
  - Block shortcuts while popup is open (finding #6): set `UndoManager.enabled = false` while modal is open

- [ ] Subscribe to `SceneWillChangeEvent` -- cancel if dirty, show confirmation:

```java
EditorEventBus.get().subscribe(SceneWillChangeEvent.class, event -> {
    // Use state field directly, NOT modeManager.isPrefabEditMode(),
    // because a prior subscriber (PlayModeController) may have already
    // changed the mode to SCENE. Our own state field is authoritative.
    if (state == State.EDITING && dirty) {
        event.cancel();
        requestExit(null);
        // User must re-trigger the scene change after confirming.
        // See "Limitation" note below.
    } else if (state == State.EDITING) {
        exitEditMode();
    }
});
```

**Important**: The `EditorEventBus.publish()` is synchronous and returns void. The vetoable pattern works because:
1. `EditorSceneController` creates a `SceneWillChangeEvent`, publishes it
2. All subscribers (including PrefabEditController) run synchronously
3. If any subscriber calls `event.cancel()`, the event is marked cancelled
4. After `publish()` returns, `EditorSceneController` checks `event.isCancelled()` and aborts
5. The confirmation popup then appears, and on "Save & Continue" or "Discard", the user must re-trigger the scene change manually (e.g., re-click File > New Scene)

**Limitation**: There is no automatic retry mechanism. After the user confirms in the popup, the original action (e.g., opening a specific scene file) is lost. To support automatic retry, the `SceneWillChangeEvent` would need to carry a `Runnable retryAction` field. This is a UX trade-off: for the initial implementation, requiring the user to re-trigger the action is acceptable. A follow-up could enhance the event with retry support.

- [ ] Guard editor window close: The GLFW window close callback is in `EditorApplication`. When the user clicks the window close button (or Alt+F4), the application checks `context.isRunning()`. Add a guard:

  ```java
  // In EditorApplication's close handling (or via a WindowCloseEvent):
  if (prefabEditController.isActive() && prefabEditController.isDirty()) {
      // Prevent immediate close
      glfwSetWindowShouldClose(windowHandle, false);
      // Show confirmation, on resolve: context.requestExit()
      prefabEditController.requestExit(() -> context.requestExit());
      return;
  }
  ```

  If `EditorApplication` uses `glfwWindowShouldClose()` in its main loop, intercept before that check. The exact wiring depends on the main loop structure, but the pattern is: cancel the close, show confirmation, re-trigger close on user confirmation.

- [ ] Subscribe to `RequestPrefabEditEvent`:

```java
EditorEventBus.get().subscribe(RequestPrefabEditEvent.class, event -> {
    enterEditMode(event.prefab());
});
```

- [ ] Add Escape key binding to exit prefab edit (finding #8):

  **Conflict**: `EditorShortcutHandlersImpl.onEntityCancel()` already handles Escape to deselect entities and clear tile selections. During prefab edit, this handler would fire first (shortcut dispatch runs before controller-level key checks).

  **Solution**: Guard `onEntityCancel()` in Plan 4's shortcut guards:
  ```java
  @Override
  public void onEntityCancel() {
      if (modeManager != null && modeManager.isPrefabEditMode()) {
          // Escape exits prefab edit mode -- handled by PrefabEditController
          return;
      }
      // ... existing deselect/clear logic
  }
  ```

  Then in `PrefabEditController`, check for Escape during the ImGui render loop (or subscribe to a shortcut event):
  ```java
  // In PrefabEditController.update() or render():
  if (state == State.EDITING && ImGui.isKeyPressed(ImGuiKey.Escape)) {
      requestExit(null);
  }
  ```

  This ensures Escape is handled by the correct system based on mode. Update Plan 4 to add the `onEntityCancel()` guard.

---

## Phase 2: PrefabInspector

### Files

| File | Change |
|------|--------|
| `src/main/java/com/pocket/rpg/editor/panels/inspector/PrefabInspector.java` | **NEW** |
| `src/main/java/com/pocket/rpg/editor/undo/commands/SetPrefabMetadataCommand.java` | **NEW** |
| `src/main/java/com/pocket/rpg/editor/panels/inspector/InspectorPanel.java` | Route to PrefabInspector when in prefab edit mode |

### Tasks

- [ ] Create `SetPrefabMetadataCommand` -- undo command for displayName/category changes:

```java
public class SetPrefabMetadataCommand implements EditorCommand {
    private final JsonPrefab prefab;
    private final String field; // "displayName" or "category"
    private final String oldValue;
    private final String newValue;
    // ... standard execute/undo/getDescription
}
```

- [ ] Create `PrefabInspector` rendering class:

Layout:
```
+------------------------------------------------------+
| PREFAB MODE  (teal header bar)                       |
| Editing: "Display Name" (prefab-id)                  |
| N instances in current scene                         |
+------------------------------------------------------+
| Display Name: [________]                             |
| Category:     [________]                             |
+------------------------------------------------------+
| Components  (via ComponentListRenderer)              |
| v Transform                                          |
|     [fields...]                                      |
|   ⚠ Non-origin defaults affect all instances         |
| v SpriteRenderer                           [x Del]   |
|     [fields...]                                      |
| [+ Add Component]                                    |
+------------------------------------------------------+
| [💾 SAVE PREFAB]        (green when dirty)           |
| [Save & Exit]                                        |
| [Reset to Saved]        (always visible, disabled    |
|                          when clean)                  |
| [Exit]                  ("Revert & Exit" when dirty) |
+------------------------------------------------------+
```

- [ ] Delegate component rendering to `ComponentListRenderer` (Plan 2):
  - `allowStructuralChanges = true` (can add/remove components on the prefab)
  - `isPrefabInstance = false` (working entity is a scratch entity)
  - `dirtyTracker = () -> controller.setDirty(true)`

- [ ] Metadata fields: displayName and category as InputText with undo support via `SetPrefabMetadataCommand`

- [ ] Save button: full-width, color based on dirty state (green when dirty, gray when clean)

- [ ] "Save & Exit" button: calls `controller.saveAndExit()`

- [ ] "Reset to Saved" button: always visible, disabled when clean (no layout shift -- finding from Product Owner). Shows confirmation dialog when clicked.

- [ ] Exit button: shows "Exit" when clean, "Revert & Exit" when dirty. Calls `controller.requestExit(null)`.

- [ ] Instance count in header: count entities in current scene matching `targetPrefab.getId()`

- [ ] Transform warning: if Transform component has non-origin position/scale/rotation defaults, show warning text: "Non-origin default transform values will affect all instances without position overrides"

- [ ] Update `InspectorPanel` to route to `PrefabInspector`. The current `render()` method (line 68) branches on `isPlayMode()`. Add a third branch:

```java
@Override
public void render() {
    if (!isOpen()) return;
    if (ImGui.begin("Inspector")) {
        if (isPlayMode()) {
            renderPlayModeInspector();
        } else if (prefabEditController != null && prefabEditController.isActive()) {
            prefabInspector.render(prefabEditController);
        } else {
            renderEditorInspector();
        }
    }
    ImGui.end();
}
```

The `PrefabInspector` also needs `EntityInspector`'s `renderDeleteConfirmationPopup()` equivalent -- but since component deletion in prefab edit goes through `ComponentListRenderer` (which uses `RemoveComponentCommand` via UndoManager), and the confirmation is handled at the ComponentListRenderer level (dependency check tooltip), no additional popup is needed.

---

## Phase 3: Viewport Rendering

### Files

| File | Change |
|------|--------|
| `src/main/java/com/pocket/rpg/editor/EditorApplication.java` | Render loop branch for prefab edit scene |
| `src/main/java/com/pocket/rpg/editor/ui/SceneViewport.java` | Overlay, block drop targets and tool input |

### Tasks

- [ ] In `EditorApplication` render loop (line ~427), add prefab edit branch **before** the existing scene render:

```java
if (prefabEditController != null && prefabEditController.isActive()) {
    sceneRenderer.render(prefabEditController.getWorkingScene(), context.getCamera());
} else if (sceneRenderer != null && scene != null && !playModeController.isActive()) {
    sceneRenderer.render(scene, context.getCamera());
}
```

The `PrefabEditController` creates a temporary `EditorScene` (the "working scene") containing just the working entity. This scene is rendered via the same `EditorSceneRenderer.render(EditorScene, EditorCamera)` API. **Critical**: Never set this temporary scene as `context.currentScene`.

- [ ] In `SceneViewport.renderContent()`:
  - Subscribe to `PrefabEditStartedEvent`/`PrefabEditStoppedEvent` to track a `prefabEditActive` boolean
  - When `prefabEditActive`:
    - Skip `SceneViewportDropTarget.handleDropTarget()` (no drag-drop into prefab viewport)
    - Skip `inputHandler.handleInput()` (no tool input — selection of the single entity is handled by hierarchy click)
    - Render `renderPrefabEditOverlay()` — teal border + "PREFAB: {displayName}" label (similar to play mode overlay pattern at line 174)
  - Grid, pan, scroll work as normal (handled by camera, not input handler)

---

## Phase 4: Toolbar and Panel Behavior

### Files

| File | Change |
|------|--------|
| `src/main/java/com/pocket/rpg/editor/panels/SceneViewToolbar.java` | Disable tools during prefab edit |
| `src/main/java/com/pocket/rpg/editor/panels/hierarchy/HierarchyPanel.java` | Show only working entity during prefab edit |

### Tasks

- [ ] `SceneViewToolbar`: Disable tool buttons and play controls when `modeManager.isPrefabEditMode()`. Show mode indicator "Editing: {prefab displayName}". Only Selection tool remains active.

- [ ] `HierarchyPanel`: Show the single working entity in the hierarchy. The panel renders only the prefab's entity (no scene entities visible). Uses the same `HierarchyTreeRenderer` but with a filtered list containing only the working entity. Clicking it selects it. Future: when hierarchical prefabs are supported, this will show the full prefab tree. Note: if PR #9 is merged before this plan, account for the `HierarchyItem` type in `ComponentFieldEditor`/`ComponentListRenderer`.

---

## Phase 5: Entry Points

### Files

| File | Change |
|------|--------|
| `src/main/java/com/pocket/rpg/editor/panels/inspector/EntityInspector.java` | Add "Edit Prefab" button |
| Asset browser panel / `JsonPrefabLoader` | Add "Edit Prefab" context menu / double-click |

### Tasks

- [ ] In `EntityInspector.renderPrefabInfo()`: Add "Edit Prefab" button for JSON prefabs. Disabled for code-defined prefabs with tooltip "Code-defined prefabs cannot be edited in the editor". Publishes `RequestPrefabEditEvent`.

- [ ] In Asset Browser: Double-click on `.prefab.json` opens prefab edit mode. Implementation uses the existing `EditorPanelType` dispatch pattern:
  1. Add `PREFAB_EDITOR` to `EditorPanelType` enum
  2. Override `getEditorPanelType()` in `JsonPrefabLoader` to return `EditorPanelType.PREFAB_EDITOR`
  3. Register handler in `EditorUIController` (or `EditorApplication`):
  ```java
  assetBrowserPanel.registerPanelHandler(EditorPanelType.PREFAB_EDITOR, path -> {
      JsonPrefab prefab = Assets.load(path, JsonPrefab.class);
      if (prefab != null) EditorEventBus.get().publish(new RequestPrefabEditEvent(prefab));
  });
  ```

---

## Phase 6: Ctrl+S Remapping

### Files

| File | Change |
|------|--------|
| `src/main/java/com/pocket/rpg/editor/shortcut/EditorShortcutHandlersImpl.java` | Route Ctrl+S to prefab save in PREFAB_EDIT mode |

### Tasks

- [ ] Update `onSaveScene()` (building on Plan 4's mode guard):

```java
@Override
public void onSaveScene() {
    if (modeManager != null && modeManager.isPrefabEditMode()) {
        // Ctrl+S saves prefab during prefab edit (Product Owner recommendation #13)
        if (prefabEditController != null) {
            prefabEditController.save();
        }
        return;
    }
    if (modeManager != null && !modeManager.isSceneMode()) {
        showMessage("Scene save disabled in current mode");
        return;
    }
    menuBar.triggerSaveScene();
}
```

---

## Phase 7: PrefabRegistry.saveJsonPrefab()

### Files

| File | Change |
|------|--------|
| `src/main/java/com/pocket/rpg/prefab/PrefabRegistry.java` | Add `saveJsonPrefab()` method |
| `src/main/java/com/pocket/rpg/prefab/JsonPrefab.java` | Ensure `sourcePath` is available |

### Tasks

- [ ] Add `saveJsonPrefab(JsonPrefab)` to `PrefabRegistry`: The method already exists (used by `SavePrefabPopup`) but takes `(JsonPrefab, String filename)`. For prefab edit mode, we need to save to the original file path. Two options:
  - Add overload `saveJsonPrefab(JsonPrefab)` that uses `prefab.getSourcePath()` to derive the filename
  - Or have `PrefabEditController` call the existing method with the known filename

  The existing `saveJsonPrefab()` already handles re-registration via `prefabs.put(prefab.getId(), prefab)`, so no duplicate-ID issue.

- [ ] **sourcePath is already populated**: Investigation shows `JsonPrefabLoader` already sets `sourcePath` on both load (line 33: `prefab.setSourcePath(path)`) and save (line 58: `prefab.setSourcePath(path)`). No fix needed — only the `saveJsonPrefab(JsonPrefab)` overload above is required.

---

## Phase 8: Cache Invalidation

### Tasks

- [ ] On save, iterate all entities in current scene matching `targetPrefab.getId()` and call `invalidateComponentCache()`
- [ ] Scenes not currently loaded don't need invalidation

---

## Phase 9: Wiring

### Files

| File | Change |
|------|--------|
| `src/main/java/com/pocket/rpg/editor/EditorApplication.java` | Wire PrefabEditController, render loop, exit guard |
| `src/main/java/com/pocket/rpg/editor/EditorUIController.java` | Expose panels for wiring, register prefab handler |
| `src/main/java/com/pocket/rpg/editor/EditorPanelType.java` | Add `PREFAB_EDITOR` value |
| `src/main/java/com/pocket/rpg/prefab/JsonPrefabLoader.java` | Override `getEditorPanelType()` → `PREFAB_EDITOR` |

### Tasks

- [ ] In `EditorApplication.createControllers()` (or equivalent init):
  ```java
  prefabEditController = new PrefabEditController(context);
  ```

- [ ] Wire to panels:
  ```java
  uiController.getInspectorPanel().setPrefabEditController(prefabEditController);
  uiController.getSceneViewToolbar().setPrefabEditController(prefabEditController);
  handlers.setPrefabEditController(prefabEditController);
  ```

- [ ] Register asset browser handler for prefab double-click:
  ```java
  uiController.getAssetBrowserPanel().registerPanelHandler(
      EditorPanelType.PREFAB_EDITOR, path -> {
          JsonPrefab prefab = Assets.load(path, JsonPrefab.class);
          if (prefab != null) EditorEventBus.get().publish(new RequestPrefabEditEvent(prefab));
      }
  );
  ```

- [ ] Wire confirmation popup rendering into the main ImGui render loop (alongside `renderExitConfirmation()`):
  ```java
  prefabEditController.renderConfirmationPopup();
  ```

- [ ] Add prefab dirty guard in `requestExit()` (before scene dirty check, around line 467):
  ```java
  if (prefabEditController != null && prefabEditController.isActive() && prefabEditController.isDirty()) {
      prefabEditController.requestExit(() -> requestExit());
      return;
  }
  ```

---

## Phase 10: Tests

### Files

| File | Change |
|------|--------|
| `src/test/java/com/pocket/rpg/editor/PrefabEditControllerTest.java` | **NEW** |

### Test Cases

- [ ] Enter edit mode: state changes to EDITING, undo scope pushed, working entity created
- [ ] Save: components written to prefab, dirty cleared, instances invalidated
- [ ] Save & Exit: save + exit in sequence
- [ ] Reset to saved: working entity rebuilt from saved snapshot, undo cleared, dirty cleared
- [ ] Exit (clean): immediate exit, undo scope popped, mode restored to SCENE
- [ ] Exit (dirty): confirmation popup shown
- [ ] Confirmation: "Save & Continue" saves then runs pending action
- [ ] Confirmation: "Discard" exits without saving then runs pending action
- [ ] Confirmation: "Cancel" aborts pending action, stays in edit mode
- [ ] SceneWillChangeEvent while dirty: event cancelled, confirmation shown
- [ ] SceneWillChangeEvent while clean: exit immediately, event not cancelled
- [ ] Double-click second prefab while editing first: guard triggered (finding #7)
- [ ] Escape key exits prefab edit (finding #8)
- [ ] Ctrl+S saves prefab during edit mode (Product Owner #13)
- [ ] Working entity is fully isolated from prefab definition (Plan 1)
- [ ] Undo/redo only affects prefab edits, not scene (Plan 3)

### Test Commands

```bash
mvn test -Dtest=PrefabEditControllerTest
```

Full regression:
```bash
mvn test
```

---

## Manual Verification

After all plans are implemented:

1. `mvn test` -- all existing + new tests pass
2. Open editor, inspect entities, edit components, undo/redo -- verify no regressions
3. Enter prefab edit mode from asset browser (double-click .prefab.json)
4. Enter prefab edit mode from inspector ("Edit Prefab" button)
5. Add/remove components in prefab edit mode, verify undo/redo works
6. Save prefab, verify instances in scene update
7. Test all guard triggers:
   - Click entity in hierarchy while editing dirty prefab -> confirmation
   - Click asset in browser while editing dirty prefab -> confirmation
   - Scene change while editing dirty prefab -> confirmation with cancel
   - Editor close while editing dirty prefab -> confirmation
   - Play mode while editing dirty prefab -> confirmation
8. Ctrl+S saves prefab in prefab mode, saves scene in scene mode
9. Verify Delete, Duplicate, tool keys are suppressed during prefab edit
10. Verify Escape exits prefab edit mode
11. Verify "Reset to Saved" shows confirmation and works correctly
12. Verify instance count in inspector header is correct
13. Verify Transform warning appears for non-origin defaults

---

## Files Summary

| File | Status | Phase |
|------|--------|-------|
| `editor/events/RequestPrefabEditEvent.java` | NEW | 1 |
| `editor/events/PrefabEditStartedEvent.java` | NEW | 1 |
| `editor/events/PrefabEditStoppedEvent.java` | NEW | 1 |
| `editor/PrefabEditController.java` | NEW | 1 |
| `editor/panels/inspector/PrefabInspector.java` | NEW | 2 |
| `editor/undo/commands/SetPrefabMetadataCommand.java` | NEW | 2 |
| `test/.../editor/PrefabEditControllerTest.java` | NEW | 10 |
| `editor/panels/inspector/InspectorPanel.java` | MODIFY | 2 |
| `editor/EditorApplication.java` | MODIFY | 3, 9 |
| `editor/ui/SceneViewport.java` | MODIFY | 3 |
| `editor/panels/HierarchyPanel.java` | MODIFY | 4 |
| `editor/ui/SceneViewToolbar.java` | MODIFY | 4 |
| `editor/panels/inspector/EntityInspector.java` | MODIFY | 5 |
| `editor/EditorPanelType.java` | MODIFY | 5, 9 |
| `prefab/JsonPrefabLoader.java` | MODIFY | 5, 9 |
| `editor/shortcut/EditorShortcutHandlersImpl.java` | MODIFY | 6 |
| `prefab/PrefabRegistry.java` | MODIFY | 7 |
| `editor/EditorUIController.java` | MODIFY | 9 |

## Size

Large. 7 new files, 11 modified files.

---

## Code Review

- [ ] Verify PrefabEditController lifecycle is correct (enter/save/exit/reset)
- [ ] Verify undo scoping integrates correctly with pushScope/popScope
- [ ] Verify deep copy isolation (Plan 1) prevents mutations between working entity and prefab
- [ ] Verify ComponentListRenderer (Plan 2) renders correctly in PrefabInspector
- [ ] Verify all mode guards (Plan 4) work correctly during prefab edit
- [ ] Verify confirmation popup blocks shortcuts (finding #6)
- [ ] Verify temporary EditorScene is never set as context.currentScene
- [ ] Verify cache invalidation works for all instances of edited prefab
- [ ] Run full test suite: `mvn test`
