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

The editor gains a third visualization mode alongside Scene and Play: **Prefab Edit Mode**. This reuses the existing `SceneViewport` (grid, pan, scroll, camera) and `InspectorPanel` (with a new inspectable type) rather than introducing a separate window.

#### Mode architecture

Following the `PlayModeController` pattern, a new `PrefabEditController` manages the mode lifecycle:

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

Two ways out: **Save** or **Cancel**.

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
4. Invalidate all instance caches in the current scene (section 5)
```

On **Cancel**: nothing extra -- the working copy is simply discarded.

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

A new inspector class: `PrefabInspector`, following the same pattern as `EntityInspector`. It renders:

```
+------------------------------------------------------+
|  EDITING PREFAB                                      |
|  [chest] "Treasure Chest"                            |
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
|  |          SAVE PREFAB          |    Cancel        | |
|  +-------------------------------------------------+ |
|                                                      |
+------------------------------------------------------+
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
- **Reset button**: Restores the working entity to the currently saved prefab state (the on-disk version). This discards all edits made since entering prefab edit mode (or since the last save within the session). Undo history is cleared on reset since the working entity jumps back to a known state.
- **Cancel button**: Exits prefab edit mode without saving. Equivalent to reset + exit.

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

// Reset to saved state
if (dirty) {
    if (ImGui.button(MaterialIcons.Undo + " Reset to Saved", -1, 0)) {
        prefabEditController.resetToSaved();
    }
}

// Cancel (exit without saving)
if (ImGui.button("Cancel", -1, 0)) {
    prefabEditController.cancel();
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

### 5. Impact on Existing Instances

This is the core complexity. When a prefab changes, existing instances in scenes need to respond correctly. The design follows a principle: **instances are resilient to prefab changes without requiring manual migration**.

#### 5a. Component Added to Prefab

**What happens:** The new component appears on all instances automatically.

**Why it works:** `EditorGameObject.getMergedComponents()` iterates `prefab.getComponents()` and clones each one. A newly added component in the prefab will be cloned and appear in the merged list with its default values. No instance-level changes needed.

**Action required:** Call `invalidateComponentCache()` on all loaded instances so `getMergedComponents()` re-runs.

#### 5b. Component Removed from Prefab

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

#### 5c. Default Field Value Changed on Existing Component

**What happens:** Instances that have **not** overridden that field will see the new default. Instances that **have** overridden the field keep their override.

**Why it works:** This is already how the system works. `getFieldValue()` checks overrides first, then falls back to `prefab.getFieldDefault()`. Changing the default only affects non-overridden fields.

**Subtlety:** If an instance previously overrode a field to match the old default (e.g., override `zIndex=5` when default was `5`), `isFieldOverridden()` compares current override value to current default. After the default changes to `10`, the instance's `zIndex=5` override now genuinely differs and will be shown as overridden. This is correct behavior.

#### 5d. Component Type Renamed / Replaced

If a component is removed and a different one added, this is effectively 5b + 5a combined. The old overrides become orphaned, the new component appears with defaults. No special handling needed.

#### 5e. Summary Table

| Prefab Change | Instance Effect | Override Impact |
|---|---|---|
| Component added | Appears with defaults | None (no overrides exist yet) |
| Component removed | Disappears | Overrides become orphaned (inert) |
| Default value changed | Non-overridden fields update | Overridden fields unchanged |
| Component reordered | Display order changes | No impact on overrides |

### 6. Cache Invalidation

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

### 7. "Apply Overrides to Prefab" Workflow

A secondary workflow for updating field defaults without entering prefab edit mode. Available on prefab instances in the regular scene inspector.

#### Use case

A user has placed a prefab instance, tweaked field values (creating overrides), and wants to "push" those values back into the prefab definition so all instances get them.

#### Entry point

Inspector button on a prefab instance: **"Apply Overrides to Prefab"** (only for JSON prefabs).

#### Behavior

1. For each overridden field on the instance, update the corresponding default value in the prefab definition
2. Remove the override from the instance (it now matches the new default)
3. Save the prefab to disk
4. Invalidate caches on all instances

This does **not** add or remove components -- it only updates default values. For structural changes, enter prefab edit mode.

### 8. "Update Prefab from Entity" (Structural Re-export)

For cases where the user wants to completely redefine a prefab from a scratch entity.

#### Entry point

Hierarchy context menu on a **scratch entity**: **"Save as Prefab"** (existing) -- but now, if the entered ID matches an existing JSON prefab, instead of blocking with "already exists", show a confirmation dialog:

```
+------------------------------------------------------+
|  Update Existing Prefab?                             |
+------------------------------------------------------+
|  A prefab with ID "chest" already exists.            |
|                                                      |
|  This will replace the prefab definition with the    |
|  components from this entity.                        |
|                                                      |
|  Changes:                                            |
|  + ChestInteraction (new component)                  |
|  ~ SpriteRenderer (2 fields differ)                  |
|  - OldComponent (will be removed)                    |
|                                                      |
|  Overrides on removed components will become          |
|  orphaned but won't cause errors.                    |
|                                                      |
|  [Update Prefab]  [Save as New]  [Cancel]            |
+------------------------------------------------------+
```

**"Update Prefab"**: Replaces the prefab definition entirely with the entity's current components. Same propagation logic as section 5.

**"Save as New"**: Opens a new ID field so the user can create a separate prefab (current behavior).

### 9. Runtime Impact

`Prefab.instantiate()` and `RuntimeSceneLoader` need **no changes**. They already:
1. Read components from the prefab definition
2. Clone each one
3. Apply overrides from `GameObjectData.componentOverrides`
4. Skip override entries that don't match any component (the orphaned overrides from 5b are naturally ignored because the loop iterates prefab components, not override keys)

The only consideration: if a scene was saved with an old prefab version and loaded with a new one, the orphaned overrides in the scene JSON are simply never matched. This is safe.

### 10. Implementation Components

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
| `SceneViewport` | When prefab edit active, render only the working entity + colored border overlay |
| `SceneViewToolbar` | Disable tool buttons and play controls during prefab edit. Show mode indicator |
| `UndoManager` | Add `stash()` / `restore()` methods to save and restore undo/redo stacks |
| `SavePrefabPopup` | Allow overwriting existing JSON prefabs with confirmation dialog |
| `PrefabRegistry` | Add `updateJsonPrefab(JsonPrefab)` that handles re-registration without throwing |
| `EntityInspector` | Add "Edit Prefab" button in `renderPrefabInfo()`, "Apply Overrides to Prefab" button |
| `JsonPrefabLoader` | Add "Edit Prefab" to right-click context menu in Asset Browser |
| `EditorGameObject` | Add `getOrphanedOverrides()` method for optional UI display |
| `EditorApplication` | Wire `PrefabEditController` into the application lifecycle, pass to panels |

### 11. Undo System Detail

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

### 12. Edge Cases

**Duplicate component types:** The current system identifies components by their fully-qualified class name. If a prefab has two components of the same type, overrides are ambiguous. The Prefab Editor should warn if duplicate types are added.

**Java prefabs (code-defined):** `PlayerPrefab` and other Java prefabs cannot be edited through this system. The "Edit Prefab" button should be disabled/hidden for non-JSON prefabs, with a tooltip: "Code-defined prefabs cannot be edited in the editor."

**Prefab in use by runtime code:** Some code may call `PrefabRegistry.getPrefab("chest").getComponents()` directly. After an update, the returned components list reflects the new definition immediately (since we mutate the `JsonPrefab` object in-place after save). This is correct behavior.

**Scene not saved after prefab update:** If the user updates a prefab but doesn't save the scene, the scene file still references the old prefab version. On next load, the scene will use the new prefab definition (loaded from the updated `.prefab.json`) with the old overrides. This is fine -- the system is designed for this exact scenario.

**Prefab file deleted externally:** Already handled -- `EditorGameObject.isPrefabValid()` returns false and a broken-link sprite is shown.

**Ctrl+S during prefab edit:** Ctrl+S should save the **scene**, not the prefab. The prefab is only saved via the explicit Save Prefab button. This avoids accidental prefab saves when the user reflexively hits Ctrl+S. The scene save should work normally even during prefab edit mode (the working entity is not part of the scene).

**Closing the editor during prefab edit:** Treat as cancel. The prefab is not saved. If the scene has unsaved changes, the normal "unsaved changes" dialog appears for the scene only.

### 13. Alternative Considered: Editing Prefab In-Scene

Instead of a dedicated mode, we could allow adding/removing components on prefab instances directly in the regular inspector, storing structural changes as instance-level overrides (e.g. `"__addedComponents": [...]`, `"__removedComponents": [...]`).

**Rejected because:**
- Dramatically increases override complexity
- Blurs the line between "prefab definition" and "instance customization"
- Makes it harder to understand what an instance actually contains
- Scene files become heavier
- Conflicts with the goal of keeping instances lightweight

If per-instance structural overrides are needed in the future, they should be designed as a separate "Prefab Variants" feature.
