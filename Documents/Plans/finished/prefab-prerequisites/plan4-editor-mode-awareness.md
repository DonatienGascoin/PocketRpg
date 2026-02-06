# Plan 4: Editor Mode Awareness

## Overview

**Problem**: Shortcuts fire unconditionally (Delete deletes scene entities, Ctrl+Z marks the hidden scene dirty). `EditorSelectionManager` has zero controller dependencies and should stay that way. Panels don't know about modes.

**Approach**:
1. Introduce `EditorMode` enum: `SCENE`, `PLAY`, `PREFAB_EDIT`
2. Create `EditorModeManager` to track current mode and fire events
3. Create `SelectionGuard` wrapper around `EditorSelectionManager` for mode-aware selection
4. Add mode guards to shortcuts
5. Make `SceneWillChangeEvent` vetoable

**Addresses**: Review findings #2 (shortcuts not mode-aware), #3 (vetoable SceneWillChangeEvent), #7 (SelectionManager coupling), #8 (panels not mode-aware), #9 (SelectionGuard).

**Soft dependency on Plan 2**: Uses `DirtyTracker` interface in shortcut handlers for `onUndo()`/`onRedo()`.

---

## Phase 1: EditorMode + EditorModeManager

### Files

| File | Change |
|------|--------|
| `src/main/java/com/pocket/rpg/editor/EditorMode.java` | **NEW** -- enum |
| `src/main/java/com/pocket/rpg/editor/EditorModeManager.java` | **NEW** -- mode state + transitions |
| `src/main/java/com/pocket/rpg/editor/events/EditorModeChangedEvent.java` | **NEW** -- event |

### Tasks

- [ ] Create `EditorMode` enum:

```java
package com.pocket.rpg.editor;

/**
 * The current editing mode of the editor.
 */
public enum EditorMode {
    /** Normal scene editing */
    SCENE,
    /** Game is running in play mode */
    PLAY,
    /** Editing a prefab definition */
    PREFAB_EDIT
}
```

- [ ] Create `EditorModeChangedEvent`:

```java
package com.pocket.rpg.editor.events;

/**
 * Fired when the editor mode changes.
 */
public record EditorModeChangedEvent(
    EditorMode previousMode,
    EditorMode newMode
) implements EditorEvent {}
```

- [ ] Create `EditorModeManager`:

```java
package com.pocket.rpg.editor;

import com.pocket.rpg.editor.events.EditorEventBus;
import com.pocket.rpg.editor.events.EditorModeChangedEvent;

/**
 * Tracks the current editor mode and manages transitions.
 * Mode changes are published via EditorEventBus.
 */
public class EditorModeManager {

    private EditorMode currentMode = EditorMode.SCENE;

    /**
     * Gets the current editor mode.
     */
    public EditorMode getCurrentMode() {
        return currentMode;
    }

    /**
     * Sets the editor mode and fires an EditorModeChangedEvent.
     * No-op if the mode is already the requested mode.
     */
    public void setMode(EditorMode mode) {
        if (this.currentMode == mode) return;
        EditorMode previous = this.currentMode;
        this.currentMode = mode;
        EditorEventBus.get().publish(new EditorModeChangedEvent(previous, mode));
    }

    /**
     * Returns true if the editor is in scene editing mode.
     */
    public boolean isSceneMode() {
        return currentMode == EditorMode.SCENE;
    }

    /**
     * Returns true if the editor is in play mode.
     */
    public boolean isPlayMode() {
        return currentMode == EditorMode.PLAY;
    }

    /**
     * Returns true if the editor is in prefab edit mode.
     */
    public boolean isPrefabEditMode() {
        return currentMode == EditorMode.PREFAB_EDIT;
    }
}
```

---

## Phase 2: Integrate with EditorContext and PlayModeController

### Files

| File | Change |
|------|--------|
| `src/main/java/com/pocket/rpg/editor/EditorContext.java` | Add `EditorModeManager` field |
| `src/main/java/com/pocket/rpg/editor/PlayModeController.java` | Use `EditorModeManager.setMode()` |

### Tasks

- [ ] Add `EditorModeManager` to `EditorContext`:

```java
@Getter
private EditorModeManager modeManager;
```

Initialize in `init()`:
```java
this.modeManager = new EditorModeManager();
```

- [ ] Update `PlayModeController` to set mode via `EditorModeManager`:

In `play()` (after line 166 `state = PlayState.PLAYING`):
```java
context.getModeManager().setMode(EditorMode.PLAY);
```

In `stop()` (after line 205 `state = PlayState.STOPPED`):
```java
context.getModeManager().setMode(EditorMode.SCENE);
```

In `pause()` / `resume()`: no mode change needed -- PLAY covers both playing and paused states.

---

## Phase 3: Vetoable SceneWillChangeEvent

### Files

| File | Change |
|------|--------|
| `src/main/java/com/pocket/rpg/editor/events/SceneWillChangeEvent.java` | Change from record to class with `cancel()`/`isCancelled()` |
| `src/main/java/com/pocket/rpg/editor/EditorSceneController.java` | Check `isCancelled()` after publish |

### Tasks

- [ ] Convert `SceneWillChangeEvent` from record to class:

```java
package com.pocket.rpg.editor.events;

/**
 * Event published BEFORE a scene change occurs (new scene, open scene).
 * Subscribers can cancel the event to prevent the scene change (e.g., to show
 * a confirmation dialog when there are unsaved prefab edits).
 */
public class SceneWillChangeEvent implements EditorEvent {

    private boolean cancelled = false;

    /**
     * Cancels the scene change. The caller should check isCancelled()
     * after publishing and abort if true.
     */
    public void cancel() {
        this.cancelled = true;
    }

    /**
     * Returns true if any subscriber cancelled this event.
     */
    public boolean isCancelled() {
        return cancelled;
    }
}
```

- [ ] Update `EditorSceneController.newScene()` (around line 44):

```java
SceneWillChangeEvent event = new SceneWillChangeEvent();
EditorEventBus.get().publish(event);
if (event.isCancelled()) {
    return;
}
```

- [ ] Update `EditorSceneController.openScene()` (around line 72):

```java
SceneWillChangeEvent event = new SceneWillChangeEvent();
EditorEventBus.get().publish(event);
if (event.isCancelled()) {
    return;
}
```

- [ ] Update `PlayModeController` subscriber (line 96-100): The existing lambda `event -> { if (isActive()) stop(); }` works fine -- `SceneWillChangeEvent` is still the same type. PlayModeController never cancels, it just reacts.

**IMPORTANT -- Event subscriber ordering**: `EditorEventBus` dispatches to subscribers in registration order. If `PlayModeController` registers its `SceneWillChangeEvent` handler before `PrefabEditController` (Plan 5), then play mode stops first (setting mode to SCENE), and `PrefabEditController`'s handler sees `state == INACTIVE` / `mode == SCENE` instead of `PREFAB_EDIT`. This would make the veto mechanism fail.

**Solution**: `PrefabEditController`'s handler must check its own `state` field (`state == State.EDITING`) rather than the `EditorModeManager`, since mode may have been changed by a prior subscriber. Additionally, ensure `PrefabEditController` subscribes to `SceneWillChangeEvent` early in the dispatch chain. Options:
  - Add priority-based dispatch to `EditorEventBus` (overkill)
  - **Recommended**: Have `PrefabEditController` check `state == State.EDITING` directly. This is independent of registration order. The handler becomes:
    ```java
    EditorEventBus.get().subscribe(SceneWillChangeEvent.class, event -> {
        if (state == State.EDITING && dirty) {
            event.cancel();
            requestExit(null);
        } else if (state == State.EDITING) {
            exitEditMode();
        }
    });
    ```

---

## Phase 4: SelectionGuard

### Files

| File | Change |
|------|--------|
| `src/main/java/com/pocket/rpg/editor/SelectionGuard.java` | **NEW** -- wraps EditorSelectionManager with mode checks |

### Tasks

- [ ] Create `SelectionGuard`:

```java
package com.pocket.rpg.editor;

import com.pocket.rpg.animation.animator.AnimatorController;
import com.pocket.rpg.animation.animator.AnimatorState;
import com.pocket.rpg.animation.animator.AnimatorTransition;
import com.pocket.rpg.editor.scene.EditorGameObject;

import java.util.Set;

/**
 * Wraps EditorSelectionManager to intercept selection changes based on editor mode.
 * When in PREFAB_EDIT mode with unsaved changes, selection changes are deferred
 * through a confirmation flow. SelectionManager itself stays controller-free.
 */
public class SelectionGuard {

    private final EditorSelectionManager selectionManager;
    private final EditorModeManager modeManager;

    /**
     * Callback for when a selection change is attempted during a mode that
     * requires confirmation. The guard calls this with a Runnable that
     * performs the actual selection change.
     */
    @FunctionalInterface
    public interface SelectionInterceptor {
        /**
         * Called when a selection change needs mode-based approval.
         * @param action The selection action to perform if approved
         */
        void intercept(Runnable action);
    }

    private SelectionInterceptor interceptor = Runnable::run; // default: no interception

    public SelectionGuard(EditorSelectionManager selectionManager,
                          EditorModeManager modeManager) {
        this.selectionManager = selectionManager;
        this.modeManager = modeManager;
    }

    /**
     * Sets the interceptor for mode-gated selection changes.
     * The PrefabEditController sets this on mode entry and clears it on exit.
     */
    public void setInterceptor(SelectionInterceptor interceptor) {
        this.interceptor = interceptor != null ? interceptor : Runnable::run;
    }

    // ========================================================================
    // GUARDED SELECTION METHODS
    // ========================================================================

    public void selectEntity(EditorGameObject entity) {
        if (needsGuard()) {
            interceptor.intercept(() -> selectionManager.selectEntity(entity));
        } else {
            selectionManager.selectEntity(entity);
        }
    }

    public void selectEntities(Set<EditorGameObject> entities) {
        if (needsGuard()) {
            interceptor.intercept(() -> selectionManager.selectEntities(entities));
        } else {
            selectionManager.selectEntities(entities);
        }
    }

    public void toggleEntitySelection(EditorGameObject entity) {
        if (needsGuard()) {
            interceptor.intercept(() -> selectionManager.toggleEntitySelection(entity));
        } else {
            selectionManager.toggleEntitySelection(entity);
        }
    }

    public void selectCamera() {
        if (needsGuard()) {
            interceptor.intercept(() -> selectionManager.selectCamera());
        } else {
            selectionManager.selectCamera();
        }
    }

    public void selectTilemapLayer(int layerIndex) {
        if (needsGuard()) {
            interceptor.intercept(() -> selectionManager.selectTilemapLayer(layerIndex));
        } else {
            selectionManager.selectTilemapLayer(layerIndex);
        }
    }

    public void selectCollisionLayer() {
        if (needsGuard()) {
            interceptor.intercept(() -> selectionManager.selectCollisionLayer());
        } else {
            selectionManager.selectCollisionLayer();
        }
    }

    public void selectAsset(String path, Class<?> type) {
        if (needsGuard()) {
            interceptor.intercept(() -> selectionManager.selectAsset(path, type));
        } else {
            selectionManager.selectAsset(path, type);
        }
    }

    public void selectAnimatorState(AnimatorState state,
                                     AnimatorController controller,
                                     Runnable onModified) {
        if (needsGuard()) {
            interceptor.intercept(() -> selectionManager.selectAnimatorState(state, controller, onModified));
        } else {
            selectionManager.selectAnimatorState(state, controller, onModified);
        }
    }

    public void selectAnimatorTransition(AnimatorTransition transition,
                                          AnimatorController controller,
                                          Runnable onModified) {
        if (needsGuard()) {
            interceptor.intercept(() -> selectionManager.selectAnimatorTransition(transition, controller, onModified));
        } else {
            selectionManager.selectAnimatorTransition(transition, controller, onModified);
        }
    }

    public void clearSelection() {
        if (needsGuard()) {
            interceptor.intercept(selectionManager::clearSelection);
        } else {
            selectionManager.clearSelection();
        }
    }

    // ========================================================================
    // QUERY PASS-THROUGH (no guard needed for reads)
    // ========================================================================

    // Read-only methods delegate directly to selectionManager without guarding.
    // Callers that need read access should use selectionManager directly
    // (via context.getSelectionManager()) or we can add pass-through methods here.

    // ========================================================================
    // PRIVATE
    // ========================================================================

    private boolean needsGuard() {
        return modeManager.isPrefabEditMode();
    }
}
```

- [ ] Add `SelectionGuard` to `EditorContext`:

```java
@Getter
private SelectionGuard selectionGuard;
```

Initialize in `init()` after modeManager and selectionManager:
```java
this.selectionGuard = new SelectionGuard(selectionManager, modeManager);
```

---

## Phase 5: Mode-Aware Shortcuts

### Files

| File | Change |
|------|--------|
| `src/main/java/com/pocket/rpg/editor/shortcut/EditorShortcutHandlersImpl.java` | Add mode guards |

### Tasks

- [ ] Add `EditorModeManager` reference (set via constructor or setter):

```java
@Setter
private EditorModeManager modeManager;
```

- [ ] Add DirtyTracker reference for undo/redo (soft dependency on Plan 2):

```java
@Setter
private DirtyTracker activeDirtyTracker;
```

Callers set this to `scene` during scene mode, or to the prefab dirty tracker during prefab edit. If Plan 2 isn't done yet, keep the existing `scene.markDirty()` with a null check.

- [ ] Guard `onDelete()` -- suppress outside SCENE mode:

```java
@Override
public void onDelete() {
    if (modeManager != null && !modeManager.isSceneMode()) {
        return;
    }
    onEntityDelete();
}
```

- [ ] Guard `onDuplicate()` -- suppress outside SCENE mode:

```java
@Override
public void onDuplicate() {
    if (modeManager != null && !modeManager.isSceneMode()) {
        return;
    }
    // ... existing logic
}
```

- [ ] Guard `onUndo()` / `onRedo()` -- suppress in PLAY mode (no meaningful undo target), use DirtyTracker otherwise:

```java
@Override
public void onUndo() {
    // Suppress in play mode -- undoing scene commands while playing is dangerous
    if (modeManager != null && modeManager.isPlayMode()) {
        return;
    }
    if (UndoManager.getInstance().undo()) {
        if (activeDirtyTracker != null) {
            activeDirtyTracker.markDirty();
        }
        showMessage("Undo");
    }
}

@Override
public void onRedo() {
    if (modeManager != null && modeManager.isPlayMode()) {
        return;
    }
    if (UndoManager.getInstance().redo()) {
        if (activeDirtyTracker != null) {
            activeDirtyTracker.markDirty();
        }
        showMessage("Redo");
    }
}
```

- [ ] Guard `onSaveScene()` -- suppress outside SCENE mode:

```java
@Override
public void onSaveScene() {
    if (modeManager != null && !modeManager.isSceneMode()) {
        showMessage("Scene save disabled in current mode");
        return;
    }
    menuBar.triggerSaveScene();
}
```

Note: Plan 5 will override this to save the prefab when in PREFAB_EDIT mode.

- [ ] Guard `onPlayToggle()` -- suppress in PREFAB_EDIT mode (play mode entry from prefab edit must go through confirmation, not start directly):

```java
@Override
public void onPlayToggle() {
    if (modeManager != null && modeManager.isPrefabEditMode()) {
        showMessage("Exit prefab edit mode before entering play mode");
        return;
    }
    // ... existing logic
}
```

Note: `PlayModeController.play()` is also called from `EditorUIController` (play button). That call site also needs a guard check — add `if (context.getModeManager().isPrefabEditMode()) return;` before calling `playModeController.play()` in `EditorUIController`.

- [ ] Guard tool shortcuts -- suppress outside SCENE mode. Add to `onToolBrush()`, `onToolEraser()`, `onToolFill()`, `onToolRectangle()`, `onToolPicker()`, `onToolMove()`, `onToolRotate()`, `onToolScale()`:

```java
if (modeManager != null && !modeManager.isSceneMode()) {
    return;
}
```

`onToolSelection()` remains available in all modes (selection tool is always valid).

- [ ] Guard `onEntityCancel()` (Escape key) -- suppress in PREFAB_EDIT mode (Plan 5 handles Escape for prefab exit):

```java
@Override
public void onEntityCancel() {
    if (modeManager != null && modeManager.isPrefabEditMode()) {
        return;  // Escape handled by PrefabEditController
    }
    // ... existing deselect/clear logic
}
```

---

## Phase 6: Wire activeDirtyTracker on Mode Changes

### Tasks

- [ ] In the `EditorApplication` or wherever `EditorShortcutHandlersImpl` is configured, subscribe to `EditorModeChangedEvent` to update the active dirty tracker:

```java
EditorEventBus.get().subscribe(EditorModeChangedEvent.class, event -> {
    switch (event.newMode()) {
        case SCENE -> shortcutHandlers.setActiveDirtyTracker(context.getCurrentScene());
        case PLAY -> shortcutHandlers.setActiveDirtyTracker(null); // undo disabled in play
        case PREFAB_EDIT -> {} // PrefabEditController sets this
    }
});
```

Also subscribe to `SceneChangedEvent` to keep the dirty tracker in sync when scenes change:
```java
EditorEventBus.get().subscribe(SceneChangedEvent.class, event -> {
    if (context.getModeManager().isSceneMode()) {
        shortcutHandlers.setActiveDirtyTracker(event.scene());
    }
});
```

---

## Phase 7: Call Sites Migration

### Files

| File | Change |
|------|--------|
| `editor/panels/hierarchy/HierarchyPanel.java` | Route selection calls through `SelectionGuard` |
| `editor/panels/hierarchy/HierarchySelectionHandler.java` | Route selection calls through `SelectionGuard` |
| `editor/panels/AssetBrowserPanel.java` | Route selection calls through `SelectionGuard` |
| `editor/panels/AnimatorEditorPanel.java` | Route selection calls through `SelectionGuard` |
| `editor/panels/TilesetPalettePanel.java` | Route selection calls through `SelectionGuard` |
| `editor/panels/CollisionPanel.java` | Route selection calls through `SelectionGuard` |
| `editor/panels/InspectorPanel.java` | Route selection calls through `SelectionGuard` |
| `editor/panels/uidesigner/UIDesignerInputHandler.java` | Route selection calls through `SelectionGuard` |
| `editor/tools/SelectionTool.java` | Route selection calls through `SelectionGuard` |
| `editor/tools/MoveTool.java` | Route selection calls through `SelectionGuard` |
| `editor/tools/RotateTool.java` | Route selection calls through `SelectionGuard` |
| `editor/tools/ScaleTool.java` | Route selection calls through `SelectionGuard` |
| `editor/ui/SceneViewToolbar.java` | Route selection calls through `SelectionGuard` |
| `editor/EditorUIController.java` | Add prefab-edit guard before `playModeController.play()` |

### Tasks

- [ ] Identify call sites that should use `SelectionGuard` instead of `EditorSelectionManager` directly.

  **User-initiated selections (MUST route through SelectionGuard)**:
  - `HierarchyPanel.java` — entity click, camera click, tilemap layer click, collision layer click (~7 calls)
  - `HierarchySelectionHandler.java` — `selectEntity`, `selectCamera`, `selectTilemapLayers`, `selectCollisionMap`, `clearSelection` (~8 calls)
  - `AssetBrowserPanel.java` — asset click, double-click (~2 calls)
  - `SelectionTool.java` — viewport click/drag selection (~3 calls)
  - `MoveTool.java`, `RotateTool.java`, `ScaleTool.java` — click-to-select on drag start (~3 calls each)
  - `AnimatorEditorPanel.java` — state/transition click (~4 calls)
  - `TilesetPalettePanel.java` — layer selection in tileset UI (~4 calls)
  - `CollisionPanel.java` — collision layer selection (~3 calls)
  - `UIDesignerInputHandler.java` — UI element click (~4 calls)
  - `InspectorPanel.java` — asset selection routing (~4 calls)
  - `SceneViewToolbar.java` — tool-based selection changes (~2 calls)

  **Total**: ~47 user-initiated call sites across 13 files

  **System/internal selections (keep using EditorSelectionManager directly)**:
  - `EditorSelectionManager.java` — internal helpers
  - `BulkDeleteCommand.java` — clears selection after bulk delete (undo command, not user-initiated)
  - `PlayModeController.java` — clears selection on play mode stop (system cleanup)
  - `EntityCreationService.java` — selects newly created entity after creation (this one is borderline; could route through guard, but entity creation is already blocked in prefab edit by Plan 4's shortcut guards)
  - `MultiSelectionInspector.java` — internal selection manipulation

  **Total**: ~5 system call sites across 5 files (no migration needed)

- [ ] Update the ~47 user-initiated call sites to use `context.getSelectionGuard()` instead of `context.getSelectionManager()` for the guarded methods

  **Strategy**: Since `SelectionGuard` mirrors the `select*()` method signatures of `EditorSelectionManager`, the migration is mechanical: replace `selectionManager.selectEntity(x)` with `selectionGuard.selectEntity(x)`. Read-only queries (`hasEntitySelection()`, `getSelectedEntities()`, etc.) continue to use `selectionManager` directly.

---

## Phase 8: Tests

### Files

| File | Change |
|------|--------|
| `src/test/java/com/pocket/rpg/editor/EditorModeManagerTest.java` | **NEW** |
| `src/test/java/com/pocket/rpg/editor/SelectionGuardTest.java` | **NEW** |

### Test Cases -- EditorModeManagerTest

- [ ] Initial mode is SCENE
- [ ] `setMode(PLAY)` changes mode and fires event
- [ ] `setMode(PLAY)` when already PLAY is a no-op (no event)
- [ ] Event contains correct previousMode and newMode
- [ ] `isSceneMode()`, `isPlayMode()`, `isPrefabEditMode()` return correct values

### Test Cases -- SelectionGuardTest

- [ ] In SCENE mode, selection changes pass through directly
- [ ] In PREFAB_EDIT mode, selection changes are intercepted
- [ ] Interceptor receives a Runnable that performs the actual selection
- [ ] When interceptor is null/default, selections pass through
- [ ] Read-only queries are never guarded

### Test Cases -- Shortcut Mode Guards

- [ ] `onDelete()` is suppressed in PLAY mode
- [ ] `onDelete()` is suppressed in PREFAB_EDIT mode
- [ ] `onDelete()` works in SCENE mode
- [ ] `onDuplicate()` follows same pattern
- [ ] `onSaveScene()` is suppressed outside SCENE mode
- [ ] `onUndo()`/`onRedo()` use activeDirtyTracker

### Test Cases -- Vetoable Event

- [ ] `SceneWillChangeEvent.cancel()` sets `isCancelled()` to true
- [ ] `EditorSceneController.newScene()` aborts when event is cancelled
- [ ] `EditorSceneController.openScene()` aborts when event is cancelled
- [ ] PlayModeController still stops on uncancelled SceneWillChangeEvent

### Test Commands

```bash
mvn test -Dtest=EditorModeManagerTest
mvn test -Dtest=SelectionGuardTest
```

Full regression:
```bash
mvn test
```

Manual smoke tests:
- Enter play mode, verify Delete/Duplicate/tool shortcuts are suppressed
- Verify Ctrl+Z/Ctrl+Y still work in play mode (for runtime inspector changes) -- actually, undo should be suppressed in play mode too since there's no meaningful undo target
- Verify scene change during play mode still stops play mode
- Verify all tool shortcuts work normally in scene mode

---

## Size

Medium-large. 4 new files, 5 modified files, 2 test files.

---

## Code Review

- [ ] Verify `EditorMode` enum covers all current and planned modes
- [ ] Verify `EditorModeManager` fires events correctly on transitions
- [ ] Verify `SelectionGuard` doesn't break existing selection flow in SCENE mode
- [ ] Verify `SceneWillChangeEvent` veto mechanism works with existing subscribers
- [ ] Verify shortcut guards don't introduce regressions in SCENE mode
- [ ] Verify `PlayModeController` correctly sets mode via `EditorModeManager`
- [ ] Run full test suite: `mvn test`
