# Enable/Disable Feature - Implementation Plan

## Overview

Add enable/disable support for GameObjects and Components, allowing users to toggle them on and off in both the editor and at runtime. Disabled GameObjects stop updating, rendering, and receiving input. Disabled Components stop their individual behavior/rendering while the parent GameObject stays active.

### Current State

Much of the runtime infrastructure already exists but is disconnected:

- `GameObject.enabled` field exists, `update()`/`lateUpdate()` check it
- `Component.enabled` field exists with hierarchical `isEnabled()` check
- `Component.triggerEnable()`/`triggerDisable()` exist but are **never called** by GameObject
- `GameObjectData.active` field exists but is **never read or written** during editor serialization
- `RuntimeSceneLoader` already maps `GameObjectData.active` to `GameObject.setEnabled()` (lines 226, 250, 306)
- Renderers (`SpriteRenderer`, `TilemapRenderer`) already check `isEnabled()`
- UI pipeline (`UIRenderer`) already checks both canvas and component enabled state at every level
- `LayoutGroup.getLayoutChildren()` already skips disabled children
- `UIInputHandler` already skips disabled buttons
- `EditorGameObject.isEnabled()` is hardcoded to `return true`

### Design Decisions

1. **Editor rendering**: Disabled entities/components are **hidden** (not rendered) in the editor scene view, matching runtime behavior. Disabled entities are **not selectable** via scene view click — use the hierarchy panel to find and re-enable them.
2. **Hierarchy panel**: Disabled GameObjects shown with grayed-out text in both editor and play mode.
3. **Inspector**: Disabled components have a visually distinct header color (muted/grayed) with a checkbox toggle. Fields remain visible and editable so users can configure a disabled component.
4. **GameObject enabled**: A checkbox next to the name field in the entity inspector header. GameObject icon greyed out when disabled.
5. **Serialization**: `enabled` state is persisted for both GameObjects (via `GameObjectData.active`) and Components (via explicit field in JSON, like `componentKey`). Only written when `false` (default `true` for backwards compatibility).
6. **Prefab overrides**: `enabled` state is overridable per-instance for both GameObjects and Components.
7. **Hierarchical propagation**: `Component.isEnabled()` already chains through `owner.isEnabled()`, so children are effectively disabled when their parent is. `setEnabled()` fires callbacks only on components whose effective state actually changes — respecting each child's own `enabled` flag.
8. **Gizmos**: Gizmos do NOT render for disabled entities/components. The gizmo system currently does not check `isEnabled()` — this needs to be added.
9. **Keyboard shortcut**: `Ctrl+Shift+A` toggles enabled state for selected GameObjects (not components — component toggles are inspector-only).
10. **Undo everywhere**: Every enable/disable toggle (GameObject checkbox, component checkbox, context menu, keyboard shortcut) pushes an undo command.

### Impact Verification

Audit of all `isEnabled()` calls in editor code confirms the `EditorGameObject.isEnabled()` change is safe:

| Call Site | Current Behavior | After Change |
|-----------|-----------------|--------------|
| `HierarchyTreeRenderer` (line 359) | Always shows enabled icon | Shows `VisibilityOff` when disabled (desired) |
| `EntityInspector.renderRuntime()` (line 145) | Always shows "(enabled)" | Shows "(disabled)" when appropriate (desired) |
| `GizmoRenderer` (lines 89-147) | Does NOT check `isEnabled()` | Needs update — skip disabled entities/components (Phase 5) |
| `EditorScene` tilemap layer update (line 745) | Already uses enabled for layer visibility | No change |

---

## Phase 1: Core Runtime Wiring

**Goal:** Connect the existing enable/disable plumbing that's already in place but not wired up.

### Files to Modify

| File | Change |
|------|--------|
| `core/GameObject.java` | Wire `setEnabled()` to call `triggerEnable()`/`triggerDisable()` on components, propagate to children, invalidate Scene caches |
| `components/Component.java` | Make `enabled` field `public` (was `protected`) so serializer can access it directly |

### Tasks

- [ ] Change `Component.enabled` from `protected` to `public` — allows the serializer to read the field directly without the hierarchical `isEnabled()` check.
- [ ] Update `GameObject.setEnabled()` to iterate components (snapshot) and call `triggerEnable()` or `triggerDisable()` when state changes
- [ ] Implement `propagateParentEnabledChange(boolean parentNowEnabled)` to recursively notify children's components, respecting each child's own `enabled` flag
- [ ] Invalidate Scene caches when enabled state changes: call `scene.unregisterCachedComponents(this)` when disabled, `scene.registerCachedComponents(this)` when re-enabled. This removes/adds Renderable and UICanvas entries from the Scene's fast-lookup lists.

### Implementation Detail

```java
public void setEnabled(boolean enabled) {
    if (this.enabled == enabled) return;
    this.enabled = enabled;

    // Notify components on this GameObject (snapshot for iteration safety)
    for (Component component : new ArrayList<>(components)) {
        if (enabled) {
            component.triggerEnable();
        } else {
            component.triggerDisable();
        }
    }

    // Invalidate Scene caches (renderables, uiCanvases)
    if (scene != null) {
        if (enabled) {
            scene.registerCachedComponents(this);
        } else {
            scene.unregisterCachedComponents(this);
        }
    }

    // Propagate to children — only notify children whose effective state changed
    for (GameObject child : new ArrayList<>(children)) {
        child.propagateParentEnabledChange(enabled);
    }
}

/**
 * Called when an ancestor's enabled state changes.
 * Only fires callbacks on components whose effective state actually changed.
 * Respects this child's own enabled flag — if child is individually disabled,
 * its components won't receive triggerEnable() when parent is re-enabled.
 */
void propagateParentEnabledChange(boolean parentNowEnabled) {
    // If this child is individually disabled, its effective state didn't change
    if (!this.enabled) return;

    // This child is individually enabled, so parent change affects it
    for (Component component : new ArrayList<>(components)) {
        if (parentNowEnabled) {
            component.triggerEnable();
        } else {
            component.triggerDisable();
        }
    }

    // Recurse to grandchildren
    for (GameObject child : new ArrayList<>(children)) {
        child.propagateParentEnabledChange(parentNowEnabled);
    }
}
```

Key points:
- `triggerEnable()` already guards with `if (started && enabled)` — won't fire on individually-disabled components
- `triggerDisable()` already guards with `if (started && enabled)` — won't fire on already-disabled components
- `propagateParentEnabledChange()` short-circuits at children with `enabled=false` (their effective state didn't change)
- Snapshot iteration (`new ArrayList<>(...)`) prevents `ConcurrentModificationException` if callbacks modify the component/child list — consistent with existing patterns in `update()` and `start()`

### Testing

- [ ] Unit test: Disable GameObject -> components receive `onDisable()`
- [ ] Unit test: Re-enable GameObject -> components receive `onEnable()`
- [ ] Unit test: Disable parent -> child components receive `onDisable()`
- [ ] Unit test: Component with `enabled=false` does NOT receive `onEnable()` when parent is enabled
- [ ] Unit test: Child with `enabled=false` — its components do NOT receive callbacks when parent toggles
- [ ] Unit test: Grandchild propagation works (3 levels deep)
- [ ] Unit test: Component disables its own GameObject during `update()` — no crash, no double-callback
- [ ] Unit test: Exception in one component's `onDisable()` does not block sibling components (already handled by try-catch in `triggerDisable()`)

---

## Phase 2: Serialization

**Goal:** Persist enabled state for both GameObjects and Components through save/load.

### Files to Modify

| File | Change |
|------|--------|
| `serialization/GameObjectData.java` | Already has `active` field — no change needed |
| `editor/scene/EditorGameObject.java` | Add `enabled` field, wire through `toData()`/`fromData()`, update `isEnabled()` and `isRenderVisible()` |
| `serialization/custom/ComponentTypeAdapterFactory.java` | Add `enabled` field serialization (like `componentKey`) |

### Tasks

- [ ] Add `boolean enabled = true` field to `EditorGameObject` with getter/setter
- [ ] Update `EditorGameObject.isEnabled()` to return the field value instead of hardcoded `true`
- [ ] Update `EditorGameObject.toData()` to write `enabled` to `GameObjectData.setActive()`
- [ ] Update `EditorGameObject.fromData()` to read `GameObjectData.isActive()` into `enabled`
- [ ] Update `EditorGameObject.isRenderVisible()` to check `isEnabled()` (includes parent chain from Phase 4):
  ```java
  @Override
  public boolean isRenderVisible() {
      return isEnabled() && getCurrentSprite() != null;
  }
  ```
- [ ] Add `enabled` write/read in `ComponentTypeAdapterFactory` (same pattern as `componentKey`: only write when `false`, read and apply)
- [ ] Verify `RuntimeSceneLoader` already maps `GameObjectData.active` → `GameObject.setEnabled()` (confirmed: lines 226, 250, 306 — no change needed)
- [ ] Handle prefab instance override for GameObject enabled state (stored as top-level `active` on `GameObjectData`, alongside `name`/`parentId`/`order`)

### Component Serialization Detail

**IMPORTANT:** Use the `enabled` *field* directly, not `isEnabled()` — because `isEnabled()` is hierarchical and would return `false` for a component whose owner is disabled, even if the component's own `enabled` field is `true`.

In `writeComponentProperties()`, after the `componentKey` block:
```java
// Write enabled (only when false, since true is default)
// Use enabled — isEnabled() is hierarchical and would give wrong result
if (!component.enabled) {
    out.name("enabled");
    out.value(false);
}
```

In `readComponentProperties()`, after the `componentKey` block:
```java
// Read enabled (defaults to true if absent — backwards compatible)
JsonElement enabledElement = json.get("enabled");
if (enabledElement != null && enabledElement.isJsonPrimitive()) {
    boolean enabledValue = enabledElement.getAsBoolean();
    if (!enabledValue) {
        // Access field directly to avoid triggering onEnable/onDisable callbacks during deserialization
        // Component hasn't started yet, so setEnabled() callbacks won't fire anyway
        component.setEnabled(false);
    }
}
```

### Testing

- [ ] Round-trip test: Save scene with disabled GameObject, reload, verify still disabled
- [ ] Round-trip test: Save scene with disabled Component, reload, verify still disabled
- [ ] Test: Missing `enabled` field in JSON defaults to `true` (backwards compatibility)
- [ ] Test: Prefab instance with overridden enabled state
- [ ] Test: Component on disabled parent serializes correctly (component's own `enabled=true` should NOT be written as `false`)
- [ ] Test: Old scene files (without `enabled` fields) load with everything enabled

---

## Phase 3: Editor Inspector UI

**Goal:** Add toggles for enabling/disabling GameObjects and Components in the inspector.

### Files to Modify

| File | Change |
|------|--------|
| `editor/panels/inspector/EntityInspector.java` | Add enabled checkbox next to entity name |
| `editor/panels/inspector/ComponentListRenderer.java` | Add enabled checkbox in component headers, change header color when disabled |
| `editor/undo/commands/` | **NEW** `ToggleEntityEnabledCommand.java`, `ToggleComponentEnabledCommand.java` |

### Tasks

- [ ] Add enabled checkbox in `EntityInspector.render()` before the name field
- [ ] Create `ToggleEntityEnabledCommand` undo command
- [ ] Add enabled checkbox in `ComponentListRenderer` component headers (left of the component name)
- [ ] Create `ToggleComponentEnabledCommand` undo command
- [ ] Style disabled component headers with muted color (push `ImGuiCol.Header` to a darker/grayed shade). Only the header is grayed — field labels and values remain normal so disabled components are easy to configure.
- [ ] Add enabled checkbox in `EntityInspector.renderRuntime()` (play mode — changes are temporary, no undo commands, direct state mutation since play-mode state is discarded on exit)
- [ ] Add enabled checkbox per component in play-mode inspector (same: no undo, direct mutation)

### Inspector Layout — Entity Header

```
[x] [icon] [Entity Name________] [Save] [Delete]
```

The checkbox toggles `EditorGameObject.setEnabled()` via `ToggleEntityEnabledCommand`, pushes to undo stack, and marks the scene dirty. All toggle operations must go through undo commands — never modify `enabled` directly without undo.

### Inspector Layout — Component Header

```
[x] SpriteRenderer *                    [swap] [key] [x]
```

A checkbox before the component name. When unchecked:
- Header background becomes muted/darker
- Fields below remain fully visible and editable at normal brightness (so you can configure a disabled component without visual clutter)

### Disabled Component Header Style

```java
boolean compEnabled = comp.isEnabled();
if (!compEnabled) {
    ImGui.pushStyleColor(ImGuiCol.Header, 0.25f, 0.25f, 0.25f, 1.0f);
    ImGui.pushStyleColor(ImGuiCol.HeaderHovered, 0.30f, 0.30f, 0.30f, 1.0f);
    ImGui.pushStyleColor(ImGuiCol.HeaderActive, 0.28f, 0.28f, 0.28f, 1.0f);
}
// ... render header with checkbox ...
if (!compEnabled) {
    ImGui.popStyleColor(3);
}
```

---

## Phase 4: Hierarchy Panel & Shortcuts

**Goal:** Make disabled state visible in the hierarchy tree and add user-facing shortcuts.

### Files to Modify

| File | Change |
|------|--------|
| `editor/panels/hierarchy/HierarchyTreeRenderer.java` | Gray out disabled entities in editor mode tree, add context menu toggle |
| `editor/shortcut/EditorShortcuts.java` | Register `Ctrl+Shift+A` shortcut for toggle enabled |

### Tasks

- [ ] In `renderEntityTree()`: when entity is disabled, push grayed text color and use `VisibilityOff` icon
- [ ] In `renderHierarchyItemTree()` (play mode): already uses `VisibilityOff` icon for disabled items — verify this still works
- [ ] When a parent is disabled, children should also appear grayed (EditorGameObject needs to chain `isEnabled()` through parent — see note below)
- [ ] Add "Enable" / "Disable" toggle to hierarchy right-click context menu (single-select and multi-select)
- [ ] Register `Ctrl+Shift+A` keyboard shortcut to toggle enabled on all selected GameObjects (not components). Uses batch undo command for multi-selection.
- [ ] Duplicate entity preserves disabled state (verify existing duplication code copies `enabled` field)

### Hierarchy Parent Chain for Editor

`EditorGameObject.isEnabled()` currently only checks its own field. For hierarchy graying to cascade, it needs to check the parent chain:

```java
@Override
public boolean isEnabled() {
    if (!enabled) return false;
    if (parent != null) return parent.isEnabled();
    return true;
}
```

### Context Menu Addition

In `renderEntityContextMenu()`, after "Duplicate" and before "Unparent":
```java
// Single select
String enableLabel = entity.isEnabled()
    ? MaterialIcons.VisibilityOff + " Disable"
    : MaterialIcons.Visibility + " Enable";
if (ImGui.menuItem(enableLabel)) {
    UndoManager.getInstance().execute(new ToggleEntityEnabledCommand(entity, scene));
    scene.markDirty();
}

// Multi-select
if (multiSelect) {
    boolean anyEnabled = selected.stream().anyMatch(EditorGameObject::isEnabled);
    String bulkLabel = anyEnabled
        ? MaterialIcons.VisibilityOff + " Disable All"
        : MaterialIcons.Visibility + " Enable All";
    if (ImGui.menuItem(bulkLabel)) {
        // Batch undo command — one compound command wrapping individual toggles
        UndoManager.getInstance().execute(new BulkToggleEnabledCommand(scene, selected, !anyEnabled));
        scene.markDirty();
    }
}
```

### Visual Treatment

```java
boolean entityEnabled = entity.isEnabled();
if (!entityEnabled) {
    ImGui.pushStyleColor(ImGuiCol.Text, 0.5f, 0.5f, 0.5f, 0.6f);
}

String icon = entityEnabled
    ? IconUtils.getIconForEntity(entity)
    : MaterialIcons.VisibilityOff;
String label = icon + " " + entity.getName();

// ... render tree node ...

if (!entityEnabled) {
    ImGui.popStyleColor();
}
```

---

## Phase 5: Editor Rendering & Gizmo Integration

**Goal:** Ensure disabled entities don't render in the editor scene view, UI components respect disabled state, and gizmos are hidden for disabled entities/components.

### Files to Modify

| File | Change |
|------|--------|
| `editor/scene/EditorGameObject.java` | `isRenderVisible()` checks `enabled` (done in Phase 2) |
| `editor/rendering/EditorUIBridge.java` | Respect component `enabled` state instead of force-enabling |
| `editor/rendering/EditorSceneRenderer.java` | No change needed (already checks `isRenderVisible()`) |
| `editor/gizmos/GizmoRenderer.java` | Skip disabled entities in always-gizmos, skip disabled entities/components in selected-gizmos |

### Tasks

- [ ] Verify `EditorSceneRenderer` and `EditorSceneAdapter` correctly skip entities where `isRenderVisible()` returns `false` (already the case)
- [ ] Update `EditorUIBridge` to check component `enabled` state. Remove `uiComp.setEnabled(true)` force-enable. When a UI component is disabled, skip it during editor preview rendering.
- [ ] Verify that disabled children of a disabled parent also stop rendering (hierarchy chain via `isEnabled()` parent check from Phase 4)
- [ ] Update `GizmoRenderer.renderAlwaysGizmos()` to skip disabled entities entirely
- [ ] Update `GizmoRenderer.renderSelectedGizmos()` to skip disabled entities and skip disabled components within enabled entities

### Gizmo Changes

Currently `GizmoRenderer` does NOT check `isEnabled()`. It needs to be updated:

**Always-gizmos** (`renderAlwaysGizmos`):
```java
for (EditorGameObject entity : scene.getEntities()) {
    if (!entity.isEnabled()) continue;  // ADD: skip disabled entities
    // ... existing gizmo rendering ...
}
```

**Selected-gizmos** (`renderSelectedGizmos`):
```java
for (EditorGameObject entity : scene.getSelectedEntities()) {
    if (!entity.isEnabled()) continue;  // ADD: skip disabled entities

    for (Component component : entity.getComponents()) {
        if (!component.isEnabled()) continue;  // ADD: skip disabled components
        if (component instanceof GizmoDrawableSelected gizmoDrawableSelected) {
            gizmoDrawableSelected.onDrawGizmosSelected(ctx);
        }
    }
}
```

---

## Phase 6: Prefab Override Support

**Goal:** Allow per-instance enabled state override for prefab entities.

### Files to Modify

| File | Change |
|------|--------|
| `editor/scene/EditorGameObject.java` | Handle `enabled` as an overridable property for prefab instances |

### Tasks

- [ ] For prefab instances: `enabled` is a top-level property (not a component override), stored alongside `name`, `parentId`, `order` in `GameObjectData`
- [ ] `EditorGameObject.toData()` writes `enabled` → `GameObjectData.setActive()` for both scratch and prefab entities
- [ ] Verify component `enabled` overrides work through the existing `componentOverrides` system (component enabled state can differ per-instance)
- [ ] If a prefab defines a component as disabled, an instance should be able to override it to enabled (and vice versa)

---

## Phase 7: Component System Audit

**Goal:** Ensure components that register with global systems properly handle enable/disable.

### Files to Modify

| File | Change |
|------|--------|
| `components/pokemon/GridMovement.java` | Add `onEnable()`/`onDisable()` to register/unregister from collision system |
| Other components as needed | Audit and add enable/disable handlers |

### Tasks

- [ ] **GridMovement**: Add `onEnable()` → register with `CollisionSystem.entityOccupancyMap`; add `onDisable()` → unregister. Currently only registers in `onStart()` and unregisters in `onDestroy()`, so disabling leaves a phantom collision entry.
- [ ] Audit all components for global system registration patterns (search for `onStart()` with register/subscribe calls that don't have matching `onDisable()`)
- [ ] Document in `common-pitfalls.md`: "Components that register with global systems (collision, audio, events) must unregister in `onDisable()` and re-register in `onEnable()`"

---

## Phase 8: Testing & Polish

**Goal:** Comprehensive testing and edge case handling.

### Core Behavior Tests

- [ ] Disable GameObject → components receive `onDisable()`, stop updating, stop rendering
- [ ] Re-enable GameObject → components receive `onEnable()`, resume updating and rendering
- [ ] Disable parent → all descendants stop rendering and updating
- [ ] Re-enable parent → descendants resume (unless individually disabled)
- [ ] Disable individual component → only that component stops, others continue
- [ ] Disable SpriteRenderer → entity still updates but doesn't render
- [ ] Disable UICanvas → entire UI tree disappears
- [ ] Disable UIButton → button no longer receives hover/click

### Serialization Tests

- [ ] Round-trip: disabled GameObject preserved through save/load
- [ ] Round-trip: disabled Component preserved through save/load
- [ ] Backwards compatibility: old scene files (no `enabled` fields) load with everything enabled
- [ ] Component on disabled parent serializes its own `enabled` field correctly (not the hierarchical result)

### Editor Tests

- [ ] Hierarchy correctly grays out disabled entities and their children
- [ ] Inspector checkbox toggles work with undo/redo
- [ ] Interleaved undo: toggle enabled → edit field → undo both → correct state
- [ ] Duplicate disabled entity → duplicate is also disabled
- [ ] Context menu Enable/Disable works (single and multi-select)
- [ ] `Ctrl+Shift+A` shortcut toggles selected entities
- [ ] Play mode shows correct enabled/disabled visuals; changes revert on exit
- [ ] Disabled entity is NOT selectable by clicking in scene view
- [ ] Disabled entity IS selectable via hierarchy panel click
- [ ] Selected entity remains selected when disabled via inspector checkbox
- [ ] Prefab instance can be disabled without converting to scratch
- [ ] Disabled prefab instance preserves enabled override after reload

### Edge Case Tests

- [ ] Component disables its own GameObject during `update()` → no crash, iteration completes safely
- [ ] Disabled GridMovement → tile is no longer blocked in collision system
- [ ] Not-yet-started component on disabled entity → starts correctly when entity is re-enabled and updated (deferred start in `update()`)
- [ ] `onTransformChanged()` does not fire on disabled components (existing behavior, documented)

### Code Review

- [ ] Final code review of all changes

---

## File Changes Summary

### New Files

| File | Phase | Description |
|------|-------|-------------|
| `editor/undo/commands/ToggleEntityEnabledCommand.java` | 3 | Undo command for toggling entity enabled |
| `editor/undo/commands/ToggleComponentEnabledCommand.java` | 3 | Undo command for toggling component enabled |
| `editor/undo/commands/BulkToggleEnabledCommand.java` | 4 | Compound undo command wrapping multiple `ToggleEntityEnabledCommand` for multi-select and shortcut |

### Modified Files

| File | Phase | Changes |
|------|-------|---------|
| `components/Component.java` | 1 | Make `enabled` field `public` |
| `core/GameObject.java` | 1 | Wire `setEnabled()` to notify components, add `propagateParentEnabledChange()`, invalidate Scene caches |
| `editor/scene/EditorGameObject.java` | 2, 4, 5, 6 | Add `enabled` field, update `isEnabled()` with parent chain, `isRenderVisible()`, `toData()`, `fromData()` |
| `serialization/custom/ComponentTypeAdapterFactory.java` | 2 | Serialize/deserialize component `enabled` field |
| `editor/panels/inspector/EntityInspector.java` | 3 | Add enabled checkbox in header |
| `editor/panels/inspector/ComponentListRenderer.java` | 3 | Add enabled checkbox + disabled header style |
| `editor/panels/hierarchy/HierarchyTreeRenderer.java` | 4 | Gray out disabled entities, add context menu toggle |
| `editor/shortcut/EditorShortcuts.java` | 4 | Register `Ctrl+Shift+A` shortcut |
| `editor/rendering/EditorUIBridge.java` | 5 | Respect component enabled state in UI preview |
| `editor/gizmos/GizmoRenderer.java` | 5 | Skip disabled entities/components in gizmo rendering |
| `components/pokemon/GridMovement.java` | 7 | Add `onEnable()`/`onDisable()` for collision registration |
| `.claude/reference/common-pitfalls.md` | 7 | Document enable/disable registration pattern |

### Unchanged (already working)

| File | Why No Change Needed |
|------|---------------------|
| `components/Component.java` | **Moved to Modified** — add `enabled` accessor (Phase 1) |
| `components/rendering/SpriteRenderer.java` | `isRenderVisible()` already checks `isEnabled()` |
| `components/rendering/TilemapRenderer.java` | `isRenderVisible()` already checks `isEnabled()` |
| `rendering/ui/UIRenderer.java` | Already checks canvas, GameObject, and component enabled state |
| `components/ui/LayoutGroup.java` | `getLayoutChildren()` already skips disabled children |
| `ui/UIInputHandler.java` | Already skips disabled GameObjects and buttons |
| `scenes/Scene.java` | Update loop already checks `gameObject.isEnabled()` |
| `rendering/pipeline/RenderDispatcher.java` | Already checks `isRenderVisible()` |
| `editor/gizmos/GizmoRenderer.java` | **Moved to Modified** — needs `isEnabled()` checks added |
| `scenes/RuntimeSceneLoader.java` | Already maps `GameObjectData.active` → `GameObject.setEnabled()` |

---

## Backwards Compatibility

- Scenes saved without `enabled` fields load with `enabled=true` (default) — no migration needed
- `GameObjectData.active` already defaults to `true`
- Component `enabled` absent from JSON means `true` (default)

## Naming Note

`GameObjectData` uses `active` while `GameObject` and `EditorGameObject` use `enabled`. This inconsistency is pre-existing (not introduced by this plan). Renaming `GameObjectData.active` to `enabled` is optional cleanup — low priority and not required for this feature.
