# Prefab Update Design

## Problem Statement

Prefab creation is currently a one-shot operation. Once exported via `SavePrefabPopup`, the `.prefab.json` file is written and the prefab is registered. Users can override field values on instances, but there is no way to **structurally modify** a prefab (add/remove components, change the component list). This makes prefabs brittle: any design iteration requires deleting the prefab file, re-exporting from a scratch entity, and manually fixing all instances.

## Current System Summary

**Prefab definition** (`JsonPrefab`): Stores `id`, `displayName`, `category`, and a `List<Component>` with default field values.

**Prefab instance** (`EditorGameObject` with `prefabId != null`): Stores only a `prefabId` + `componentOverrides` map. The component list is reconstructed at runtime by cloning the prefab's components and applying the overrides.

**Key constraints in current code:**
- `EditorGameObject.addComponent()` throws `IllegalStateException` for prefab instances
- `EditorGameObject.removeComponent()` operates on the `components` list, which is empty for prefab instances
- `componentOverrides` is keyed by fully-qualified component type name (e.g. `com.pocket.rpg.components.SpriteRenderer`). There is no concept of "added components" or "removed components" at the instance level
- `SavePrefabPopup.trySave()` rejects duplicate IDs (`PrefabRegistry.hasPrefab(id)`)

## Goals

1. Allow updating an existing JSON prefab's component list (add/remove components, reorder)
2. Allow updating default field values on existing components
3. Handle existing instances gracefully when the prefab changes
4. Keep the override system working correctly after updates
5. Don't break runtime instantiation (`Prefab.instantiate()`)

## Non-Goals (for this feature)

- Prefab variants / inheritance (parent-child prefab chains)
- Per-instance component additions/removals (instance-level structural overrides)
- Nested prefabs (prefab containing references to other prefabs)

---

## Design

### 1. Prefab Edit Mode

The editor gains a third visualization mode alongside Scene and Play: **Prefab Edit Mode**. No new panels or windows are created. The existing `SceneViewport`, `InspectorPanel`, and `SceneViewToolbar` remain the same panels — they just render different content depending on whether prefab edit mode is active, exactly like they already do for play mode.

#### How existing panels change behavior

- **`SceneViewport`**: Already switches rendering between scene content and play mode overlay. Prefab edit adds a third branch: when active, the viewport renders only the working entity instead of the full scene. Same grid, same camera, same panel.
- **`InspectorPanel`**: Already routes to different rendering logic based on what's selected (entity, tilemap, camera, asset, animator). When prefab edit is active, it routes to a `PrefabInspector` rendering delegate instead. Same panel, different content.
- **`SceneViewToolbar`**: Disables tool buttons and play controls, shows a mode indicator. Same toolbar.

#### Mode controller

Following the `PlayModeController` pattern, a new `PrefabEditController` manages the mode lifecycle. It is not a UI component — it holds the editing state and the working entity, and other panels query it to decide what to render.

```
EditorApplication
├── PlayModeController  (Scene <-> Play)
└── PrefabEditController (Scene <-> Prefab Edit)
```

States: `INACTIVE` / `EDITING`

The two modes are mutually exclusive:
- Entering prefab edit stops play mode (if active)
- Entering play mode exits prefab edit (if active)
- `SceneWillChangeEvent` exits prefab edit (same as play mode)

#### What the controller holds

```java
public class PrefabEditController {
    private PrefabEditState state = PrefabEditState.INACTIVE;

    // The prefab being edited
    private JsonPrefab targetPrefab;

    // Working copy: a scratch EditorGameObject built from the prefab's components
    // This is what the inspector and viewport operate on
    private EditorGameObject workingEntity;

    // Snapshot of the original component list (for cancel / diff)
    private List<Component> originalComponents;

    // Snapshot of the original component list (for reset to saved)
    // Rebuilt from the on-disk prefab each time we enter edit mode
    private List<Component> savedComponents;

    // Dirty tracking: true when any change has been made (component add/remove,
    // field value change, metadata change). Set to true whenever UndoManager
    // receives a command during prefab edit. Reset to false on save or reset.
    private boolean dirty = false;

    // Stashed scene undo stacks (restored on exit)
    private UndoManager.UndoSnapshot stashedSceneSnapshot;
}
```

#### How to enter prefab edit mode

Two entry points:

1. **Asset Browser**: Double-click a `.prefab.json` file, or right-click > "Edit Prefab"
2. **Inspector**: When a prefab instance is selected, an "Edit Prefab" button in the prefab info section (only for JSON prefabs, disabled for code-defined prefabs with tooltip "Code-defined prefabs cannot be edited in the editor")

#### Entering the mode

```
1. Stash the current UndoManager stacks (scene undo/redo)
2. Clear UndoManager (fresh slate for prefab editing)
3. Deep-clone the prefab's List<Component> into originalComponents (for cancel/diff)
4. Build a scratch EditorGameObject from the prefab's components:
   - Not a prefab instance (isScratchEntity() == true)
   - Components are clones of the prefab's component list
   - Position at origin
5. Set state = EDITING
6. Publish PrefabEditStartedEvent
```

#### Exiting the mode

Two ways out: **Save** or **Revert**.

On exit (either path):
```
1. Clear UndoManager (discard prefab undo history)
2. Restore the stashed scene undo/redo stacks
3. Set state = INACTIVE
4. Publish PrefabEditStoppedEvent
5. Discard workingEntity and originalComponents
```

On **Save** (before the above):
```
1. Read the workingEntity's current component list
2. Deep-clone each component into the JsonPrefab
3. Persist to disk via PrefabRegistry.saveJsonPrefab()
4. Invalidate all instance caches in the current scene (section 6)
```

On **Revert**: nothing extra -- the working copy is simply discarded.

### 2. Scene Viewport in Prefab Edit Mode

The `SceneViewport` already supports two rendering contexts (scene content vs play mode overlay). Prefab edit mode adds a third behavior.

#### What changes in the viewport

When `PrefabEditController.isActive()`:

- **Rendered content**: Only the `workingEntity` is shown (single entity centered at origin). The scene's tilemaps, other entities, collision layers are hidden. The viewport renders the prefab's sprite (if it has a `SpriteRenderer`) on the grid.
- **Grid, pan, scroll**: Work exactly as in scene mode (no changes needed)
- **Visual indicator**: A colored border (similar to play mode's orange border, but a different color -- e.g. blue/teal) and a label "PREFAB: {displayName}" in the viewport corner
- **Tools**: Only the Select tool is active (no tile brushes, no collision tools). Tool switching is disabled/hidden in the toolbar.
- **Drop target**: Disabled (cannot drag assets into the prefab viewport)
- **Gizmos**: Transform gizmo for the working entity is shown (to preview position/scale changes on the default Transform values)

#### Implementation approach

`SceneViewport.renderContent()` already checks `isPlayModeActive()` to render the play mode overlay. Add a similar check:

```java
if (prefabEditController.isActive()) {
    renderPrefabEditContent();  // Renders only the working entity
    renderPrefabEditOverlay();  // Blue border + label
    return;
}
```

The `ViewportRenderer` already renders `EditorScene` contents to a framebuffer. For prefab edit mode, it renders a minimal temporary scene containing only the `workingEntity`. This could be:
- A lightweight `EditorScene` with just the one entity (simplest reuse), or
- Direct rendering of the single entity's sprite bypassing the scene (less code reuse)

The temporary `EditorScene` approach is simpler because all existing rendering code (sprite sorting, z-index, camera projection) works unchanged.

### 3. Inspector Panel in Prefab Edit Mode

The `InspectorPanel` gains a new branch in its rendering logic, similar to how it already switches between entity, tilemap, camera, asset, and animator inspectors.

#### Detection

```java
// In InspectorPanel.renderEditorInspector():
if (prefabEditController != null && prefabEditController.isActive()) {
    renderPrefabInspector();
    return;
}
// ... existing selection-based logic
```

#### PrefabInspector (new class)

A new inspector class: `PrefabInspector`, following the same pattern as `EntityInspector`. The inspector window title itself changes to `"Inspector - Prefab"` (or the ImGui window title stays "Inspector" but a prominent header occupies the top of the panel). The header uses a distinct background color (e.g. teal/blue bar) so the user can never confuse it with the normal entity inspector.

It renders:

```
+------------------------------------------------------+
| ┌──────────────────────────────────────────────────┐ |
| │  PREFAB MODE                                     │ |
| │  Editing: "Treasure Chest" (chest)               │ |
| └──────────────────────────────────────────────────┘ |
+------------------------------------------------------+
|  Display Name: [Treasure Chest    ]                  |
|  Category:     [Interactables     ]                  |
+------------------------------------------------------+
|  Components                                          |
|  ┌──────────────────────────────────────────────┐    |
|  │ v Transform                                  │    |
|  │     localPosition: [0, 0, 0]                 │    |
|  │     localRotation: [0, 0, 0]                 │    |
|  │     localScale:    [1, 1, 1]                 │    |
|  ├──────────────────────────────────────────────┤    |
|  │ v SpriteRenderer                      [x Del]│    |
|  │     sprite: sprites/chest.png                │    |
|  │     zIndex: 5                                │    |
|  └──────────────────────────────────────────────┘    |
|                                                      |
|  [+ Add Component]                                   |
+------------------------------------------------------+
|                                                      |
|  +-------------------------------------------------+ |
|  |          SAVE PREFAB          (green when dirty) | |
|  +-------------------------------------------------+ |
|  |  Reset to Saved  |       (only when dirty)       | |
|  +-------------------------------------------------+ |
|  |  Revert & Exit   |                               | |
|  +-------------------------------------------------+ |
|                                                      |
+------------------------------------------------------+
```

The header block is rendered with a colored background spanning the full width:

```java
// Prefab mode header
ImDrawList drawList = ImGui.getWindowDrawList();
float startX = ImGui.getCursorScreenPosX();
float startY = ImGui.getCursorScreenPosY();
float width = ImGui.getContentRegionAvailX();
float headerHeight = ImGui.getTextLineHeightWithSpacing() * 2 + 8;

int headerColor = ImGui.colorConvertFloat4ToU32(0.15f, 0.4f, 0.5f, 1f);
drawList.addRectFilled(startX, startY, startX + width, startY + headerHeight, headerColor, 4f);

ImGui.setCursorPosY(ImGui.getCursorPosY() + 4);
ImGui.text("  PREFAB MODE");
ImGui.text("  Editing: \"" + targetPrefab.getDisplayName() + "\" (" + targetPrefab.getId() + ")");
ImGui.setCursorPosY(ImGui.getCursorPosY() + 4);
ImGui.separator();
```

#### What the PrefabInspector reuses

Almost everything from `EntityInspector`:
- **Component list rendering**: Same collapsing headers, same `ComponentFieldEditor` / `ReflectionFieldEditor` for field editing
- **Add Component button**: Same `ComponentBrowserPopup`
- **Remove Component button**: Same per-component delete button (with `@RequiredComponent` dependency check)

What it does **not** show:
- Entity name field (prefab has `displayName` instead)
- "Save as Prefab" button (already editing a prefab)
- Delete entity button
- Prefab info section (no override display -- this IS the prefab)

#### The Save Button

The Save button must be **prominent and unmistakable**. This is intentional: prefab edits affect all instances, so the save action must be deliberate.

Design:
- Full-width button at the bottom of the inspector
- Large height (e.g. `ImGui.button("SAVE PREFAB", -1, 40)`)
- **Color changes based on dirty state:**
  - **No changes (clean):** Muted/gray -- visually inactive, still clickable but nothing to save
  - **Unsaved changes (dirty):** Green/teal -- draws attention, signals "you have changes to persist"
- **No keyboard shortcut** -- save is manual only. Ctrl+S continues to save the scene, not the prefab.

Below the save button:
- **Reset to Saved button**: Restores the working entity to the currently saved prefab state (the on-disk version). This discards all edits made since entering prefab edit mode (or since the last save within the session). Undo history is cleared on reset since the working entity jumps back to a known state. Only visible when dirty.
- **Revert & Exit button**: Exits prefab edit mode, discarding unsaved changes. Always visible. If dirty, triggers the unsaved changes popup first (see section 5).

```java
// Bottom of PrefabInspector.render():
ImGui.separator();
ImGui.spacing();

boolean dirty = prefabEditController.isDirty();

// Big save button -- color depends on dirty state
if (dirty) {
    ImGui.pushStyleColor(ImGuiCol.Button, 0.2f, 0.6f, 0.4f, 1f);
    ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.3f, 0.7f, 0.5f, 1f);
    ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0.15f, 0.5f, 0.35f, 1f);
} else {
    ImGui.pushStyleColor(ImGuiCol.Button, 0.3f, 0.3f, 0.3f, 1f);
    ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.35f, 0.35f, 0.35f, 1f);
    ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0.25f, 0.25f, 0.25f, 1f);
}
if (ImGui.button(MaterialIcons.Save + "  SAVE PREFAB", -1, 40) && dirty) {
    prefabEditController.save();
}
ImGui.popStyleColor(3);

ImGui.spacing();

// Reset to saved state (only when dirty)
if (dirty) {
    if (ImGui.button(MaterialIcons.Undo + " Reset to Saved", -1, 0)) {
        prefabEditController.resetToSaved();
    }
    ImGui.spacing();
}

// Revert & Exit
if (ImGui.button(MaterialIcons.Close + " Revert & Exit", -1, 0)) {
    prefabEditController.requestExit();  // Shows confirmation if dirty
}
```

#### Undo commands during prefab edit

All component edits on the `workingEntity` go through the standard `UndoManager`. Since we replaced the stacks on mode entry, Ctrl+Z/Ctrl+Y only undo/redo prefab changes. The commands used are the same as for scratch entities:

- `AddComponentCommand` -- adding a component
- `RemoveComponentCommand` -- removing a component
- `SetPropertyCommand` / field-level commands -- changing field values
- `RenameEntityCommand` is not needed (use prefab-specific metadata commands for displayName/category)

New commands needed:
- `SetPrefabMetadataCommand` -- for changing displayName or category (captures old/new values)

When the mode exits (save or cancel), the undo stacks are cleared and the scene stacks are restored. The prefab edit history is gone. This is the intended behavior: once you leave prefab edit mode, those undos are not meaningful anymore.

### 4. What the Toolbar Shows

The `SceneViewToolbar` needs to react to prefab edit mode:

- **Tool buttons** (select, brush, eraser, etc.): Disabled/hidden. Only the select tool remains active.
- **Play/Pause/Stop buttons**: Disabled (cannot enter play mode while editing a prefab)
- **Mode indicator**: Show "Editing: {prefab displayName}" in the toolbar, possibly with the prefab's preview sprite as a small icon
- **Grid toggle, gizmo toggle**: Still functional (useful for positioning)

### 5. Interaction Guards (Unsaved Changes Protection)

During prefab edit mode, many editor actions could disrupt the editing session. Any action that would leave prefab edit mode or change what the inspector shows must be guarded when there are unsaved changes.

#### The confirmation popup

When an action would exit prefab mode while `dirty == true`, a modal popup appears:

```
+------------------------------------------------------+
|  Unsaved Prefab Changes                              |
+------------------------------------------------------+
|  You have unsaved changes to prefab                  |
|  "Treasure Chest" (chest).                           |
|                                                      |
|  [Save & Continue]  [Discard]  [Cancel]              |
+------------------------------------------------------+
```

The three buttons:
- **Save & Continue**: Save the prefab to disk, then proceed with the interrupted action (exit mode, select entity, etc.)
- **Discard**: Throw away changes and proceed with the interrupted action
- **Cancel**: Abort the interrupted action, stay in prefab edit mode with changes intact

The popup stores a `Runnable pendingAction` -- the action that triggered it. On Save or Discard, `pendingAction.run()` is called after the appropriate cleanup. On Cancel, `pendingAction` is discarded.

#### What triggers the guard

Every path that would change the inspector content or exit prefab mode must go through `PrefabEditController.requestExit(Runnable afterExit)` (or a guard check). The table below lists all such paths:

| Trigger | Source | Guard behavior |
|---|---|---|
| **Entity selection** | `HierarchySelectionHandler.selectEntity()` | Show popup. On Save/Discard: exit prefab mode, then select the entity. On Cancel: selection does not change. |
| **Camera selection** | `HierarchySelectionHandler.selectCamera()` | Same as entity selection. |
| **Tilemap layer selection** | `HierarchySelectionHandler.selectTilemapLayers()` | Same as entity selection. |
| **Collision layer selection** | `HierarchySelectionHandler.selectCollisionMap()` | Same as entity selection. |
| **Animator state/transition selection** | `AnimatorEditorPanel` callbacks → `selectionManager.selectAnimatorState/Transition()` | Show popup. On Save/Discard: exit prefab mode, then select animator item. On Cancel: animator selection does not change. |
| **Asset selection** | `AssetBrowserPanel` → `selectionManager.selectAsset()` | Same pattern. |
| **Revert & Exit button** | `PrefabInspector` | Show popup (unless clean, in which case exit immediately). |
| **Ctrl+S (scene save)** | `EditorShortcuts.FILE_SAVE` | **Blocked entirely** during prefab edit mode (see below). |
| **Scene change** | `SceneWillChangeEvent` | Show popup. On Save/Discard: exit prefab mode, proceed with scene change. On Cancel: abort scene change. |
| **Editor close** | Window close event | Show popup. On Save/Discard: exit prefab mode, proceed with close. On Cancel: abort close. |
| **Play mode start** | Play button | Show popup. On Save/Discard: exit prefab mode, then start play mode. On Cancel: stay in prefab mode. |

#### Implementation: guarding selection changes

The cleanest approach is to intercept at the `EditorSelectionManager` level. When prefab edit mode is active and dirty, selection changes are deferred:

```java
// In EditorSelectionManager:
public void selectEntity(EditorGameObject entity) {
    if (prefabEditController != null && prefabEditController.isActiveAndDirty()) {
        prefabEditController.requestExit(() -> selectEntityInternal(entity));
        return;
    }
    selectEntityInternal(entity);
}
```

This pattern applies to all `select*()` methods on `EditorSelectionManager`. When prefab mode is active but clean (no unsaved changes), the selection proceeds directly -- it exits prefab mode and selects the new item without a popup.

For the animator panel, the same guard applies through `selectAnimatorState()` and `selectAnimatorTransition()` on the selection manager.

#### Ctrl+S is blocked in prefab mode

Ctrl+S saves the scene, but during prefab edit mode the scene is invisible and conceptually "frozen". Allowing scene save would be confusing (the user can't see what they're saving). The shortcut handler should no-op and optionally show a toast: "Scene save disabled during prefab editing. Use Save Prefab to save changes."

```java
// In EditorShortcutHandlersImpl.onSaveScene():
public void onSaveScene() {
    if (prefabEditController.isActive()) {
        showMessage("Scene save disabled during prefab editing");
        return;
    }
    menuBar.triggerSaveScene();
}
```

#### When clean (no unsaved changes)

If `dirty == false`, all the guarded actions proceed immediately. Prefab edit mode exits silently and the requested action (select entity, open animator, etc.) completes. No popup is shown.

### 6. Impact on Existing Instances

This is the core complexity. When a prefab changes, existing instances in scenes need to respond correctly. The design follows a principle: **instances are resilient to prefab changes without requiring manual migration**.

#### 6a. Component Added to Prefab

**What happens:** The new component appears on all instances automatically.

**Why it works:** `EditorGameObject.getMergedComponents()` iterates `prefab.getComponents()` and clones each one. A newly added component in the prefab will be cloned and appear in the merged list with its default values. No instance-level changes needed.

**Action required:** Call `invalidateComponentCache()` on all loaded instances so `getMergedComponents()` re-runs.

#### 6b. Component Removed from Prefab

**What happens:** The component disappears from all instances. Overrides for that component type become **orphaned**.

**Detailed behavior:**

1. `getMergedComponents()` no longer iterates over the removed component type, so it won't appear in the merged result
2. The instance's `componentOverrides` map may still contain an entry keyed by the removed component's type name (e.g. `"com.pocket.rpg.components.OldComponent" -> {"field": value}`)
3. These orphaned overrides are **harmless**: they sit in the map but are never read by `getMergedComponents()` because there's no matching prefab component to clone and apply them to
4. At next scene save, the orphaned overrides are persisted to the scene file (they're still in the map). This is acceptable -- they're small and inert

**Should we clean up orphaned overrides?** Two options:

- **Option A (Lazy cleanup):** Leave them. They cause no harm. If the component is re-added to the prefab later, the old overrides will even be re-applied automatically. This is the simpler approach.
- **Option B (Eager cleanup):** On prefab save, scan all loaded scenes and strip override entries for removed component types. This keeps scene files clean but requires iterating all entities across all scenes.

**Recommendation:** Option A (lazy cleanup) for the initial implementation. Optionally add a "Clean orphaned overrides" utility later. If desired, a warning badge could appear in the inspector showing "X orphaned overrides" with a button to clear them.

#### 6c. Default Field Value Changed on Existing Component

**What happens:** Instances that have **not** overridden that field will see the new default. Instances that **have** overridden the field keep their override.

**Why it works:** This is already how the system works. `getFieldValue()` checks overrides first, then falls back to `prefab.getFieldDefault()`. Changing the default only affects non-overridden fields.

**Subtlety:** If an instance previously overrode a field to match the old default (e.g., override `zIndex=5` when default was `5`), `isFieldOverridden()` compares current override value to current default. After the default changes to `10`, the instance's `zIndex=5` override now genuinely differs and will be shown as overridden. This is correct behavior.

#### 6d. Component Type Renamed / Replaced

If a component is removed and a different one added, this is effectively 6b + 6a combined. The old overrides become orphaned, the new component appears with defaults. No special handling needed.

#### 6e. Summary Table

| Prefab Change | Instance Effect | Override Impact |
|---|---|---|
| Component added | Appears with defaults | None (no overrides exist yet) |
| Component removed | Disappears | Overrides become orphaned (inert) |
| Default value changed | Non-overridden fields update | Overridden fields unchanged |
| Component reordered | Display order changes | No impact on overrides |

### 7. Cache Invalidation

When a prefab is updated, all `EditorGameObject` instances of that prefab need their cached merged components invalidated.

```java
// In PrefabEditController.save():
EditorScene scene = context.getCurrentScene();
if (scene != null) {
    for (EditorGameObject entity : scene.getAllEntities()) {
        if (targetPrefab.getId().equals(entity.getPrefabId())) {
            entity.invalidateComponentCache();
        }
    }
}
```

Scenes that are not currently loaded don't need invalidation -- they'll rebuild caches when loaded.

### 8. Runtime Impact

`Prefab.instantiate()` and `RuntimeSceneLoader` need **no changes**. They already:
1. Read components from the prefab definition
2. Clone each one
3. Apply overrides from `GameObjectData.componentOverrides`
4. Skip override entries that don't match any component (the orphaned overrides from 6b are naturally ignored because the loop iterates prefab components, not override keys)

The only consideration: if a scene was saved with an old prefab version and loaded with a new one, the orphaned overrides in the scene JSON are simply never matched. This is safe.

### 9. Implementation Components

#### New classes

| Class | Location | Purpose |
|---|---|---|
| `PrefabEditController` | `editor/` | Mode lifecycle: enter/exit, stash/restore undo, hold working entity |
| `PrefabInspector` | `editor/panels/inspector/` | Inspector rendering for prefab edit mode (component list, metadata, save button) |
| `SetPrefabMetadataCommand` | `editor/undo/commands/` | Undo command for displayName/category changes |

#### Modified classes

| Class | Change |
|---|---|
| `InspectorPanel` | Add `PrefabEditController` reference. When active, delegate to `PrefabInspector` instead of selection-based routing |
| `EditorSelectionManager` | Guard all `select*()` methods: when prefab edit mode is active, defer through `PrefabEditController.requestExit()` |
| `SceneViewport` | When prefab edit active, render only the working entity + colored border overlay |
| `SceneViewToolbar` | Disable tool buttons and play controls during prefab edit. Show mode indicator |
| `UndoManager` | Add `stash()` / `restore()` methods to save and restore undo/redo stacks |
| `PrefabRegistry` | Add `saveJsonPrefab(JsonPrefab)` that persists and re-registers without throwing |
| `EntityInspector` | Add "Edit Prefab" button in `renderPrefabInfo()` |
| `JsonPrefabLoader` | Add "Edit Prefab" to right-click context menu in Asset Browser |
| `EditorGameObject` | Add `getOrphanedOverrides()` method for optional UI display |
| `EditorApplication` | Wire `PrefabEditController` into the application lifecycle, pass to panels |
| `EditorShortcutHandlersImpl` | Block Ctrl+S during prefab edit mode, show toast instead |

### 10. Undo System Detail

Scene undo history must not be accessible during prefab edit mode. The scene is invisible while editing a prefab, so undoing scene operations would be confusing and potentially destructive. The undo stacks are stashed on entry and restored on exit, meaning Ctrl+Z/Y only ever operates on prefab edits.

#### Lifecycle

```
Enter prefab edit:
  1. stashedSceneSnapshot = UndoManager.stash()
     (moves undo+redo stacks out, clears UndoManager)
  2. dirty = false

During prefab edit:
  - Ctrl+Z / Ctrl+Y operate on the (initially empty, then growing) undo/redo stacks
  - All standard commands work: AddComponent, RemoveComponent, SetProperty, etc.
  - Any command pushed/executed sets dirty = true
  - Undo back to empty stack sets dirty = false
    (i.e. dirty = !undoStack.isEmpty() after each undo/redo)

Save:
  1. Write workingEntity's components into the JsonPrefab + persist to disk
  2. Update savedComponents snapshot (deep clone current state)
  3. UndoManager.clear() (edits before save are no longer undoable)
  4. dirty = false

Reset to saved:
  1. Rebuild workingEntity from savedComponents (deep clone)
  2. UndoManager.clear()
  3. dirty = false

Exit prefab edit (save or cancel):
  1. UndoManager.clear() (discard any remaining prefab undo history)
  2. UndoManager.restore(stashedSceneSnapshot)
     (scene undo/redo are back, as if nothing happened)
```

#### Dirty tracking

`dirty` is set to `true` whenever any change is made to the working entity:
- Component added or removed
- Any component field value changed (via `SetPropertyCommand`, drag edits, etc.)
- Prefab metadata changed (displayName, category)

It is set back to `false` when:
- The undo stack is empty (all changes have been undone)
- Save is performed (current state becomes the new baseline)
- Reset to saved is performed (state reverts to on-disk version)

The simplest implementation: set `dirty = true` on every `UndoManager.execute()` or `push()` call, and recompute `dirty = !undoStack.isEmpty()` after every `undo()`. This works because the undo stack starts empty on mode entry (and after save/reset), so an empty stack always means "matches saved state".

#### New methods on `UndoManager`

```java
public record UndoSnapshot(Deque<EditorCommand> undoStack, Deque<EditorCommand> redoStack) {}

public UndoSnapshot stash() {
    UndoSnapshot snapshot = new UndoSnapshot(
        new ArrayDeque<>(undoStack),
        new ArrayDeque<>(redoStack)
    );
    clear();
    return snapshot;
}

public void restore(UndoSnapshot snapshot) {
    clear();
    undoStack.addAll(snapshot.undoStack());
    redoStack.addAll(snapshot.redoStack());
}
```

### 11. Edge Cases

**Duplicate component types:** The current system identifies components by their fully-qualified class name. If a prefab has two components of the same type, overrides are ambiguous. The Prefab Editor should warn if duplicate types are added.

**Java prefabs (code-defined):** `PlayerPrefab` and other Java prefabs cannot be edited through this system. The "Edit Prefab" button should be disabled/hidden for non-JSON prefabs, with a tooltip: "Code-defined prefabs cannot be edited in the editor."

**Prefab in use by runtime code:** Some code may call `PrefabRegistry.getPrefab("chest").getComponents()` directly. After an update, the returned components list reflects the new definition immediately (since we mutate the `JsonPrefab` object in-place after save). This is correct behavior.

**Scene not saved after prefab update:** If the user updates a prefab but doesn't save the scene, the scene file still references the old prefab version. On next load, the scene will use the new prefab definition (loaded from the updated `.prefab.json`) with the old overrides. This is fine -- the system is designed for this exact scenario.

**Prefab file deleted externally:** Already handled -- `EditorGameObject.isPrefabValid()` returns false and a broken-link sprite is shown.

**Ctrl+S during prefab edit:** Blocked entirely (see section 5). The scene is invisible and not editable during prefab mode, so saving it would be confusing. A toast message informs the user.

**Closing the editor during prefab edit:** Triggers the unsaved changes popup if dirty (see section 5). On Save: prefab is saved, then editor closes. On Discard: prefab changes are lost, editor closes. On Cancel: close is aborted, stay in prefab edit mode.

### 12. Alternative Considered: Editing Prefab In-Scene

Instead of a dedicated mode, we could allow adding/removing components on prefab instances directly in the regular inspector, storing structural changes as instance-level overrides (e.g. `"__addedComponents": [...]`, `"__removedComponents": [...]`).

**Rejected because:**
- Dramatically increases override complexity
- Blurs the line between "prefab definition" and "instance customization"
- Makes it harder to understand what an instance actually contains
- Scene files become heavier
- Conflicts with the goal of keeping instances lightweight

If per-instance structural overrides are needed in the future, they should be designed as a separate "Prefab Variants" feature.
