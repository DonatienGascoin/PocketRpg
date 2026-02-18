# Shortcut/Undo Dispatch Refactor Design

**Date:** 2026-02-18
**Status:** Proposal
**Triggered by:** `Documents/Reviews/dialogueInspectorReview.md` (Cross-Cutting Issue #1)

---

## Problem Statement

The InspectorPanel registers `inspector.undo` / `inspector.redo` shortcuts at `PANEL_FOCUSED` scope (priority 1), which shadow the global `editor.edit.undo` (GLOBAL scope, priority 3). The shortcut system's `findBestMatch()` picks the highest-priority match and consumes the key -- no fallthrough.

When the InspectorPanel is focused and the user is inspecting a **component** (not an asset), the `inspector.undo` handler calls `AssetInspectorRegistry::undo`, but `currentInspector` is null, so the call does nothing. The global scene undo never fires because the key was already consumed.

### Concrete broken behavior

1. **User selects an entity in the hierarchy.** The InspectorPanel shows the entity's components via `EntityInspector`. The user edits a field (e.g. position, sprite, or any component property). The edit pushes a command onto `UndoManager`. The user presses Ctrl+Z with the Inspector focused. **Nothing happens.** The `inspector.undo` shortcut fires, calls `AssetInspectorRegistry.undo()`, finds `currentInspector == null`, returns silently. The global `editor.edit.undo` never runs.

2. **User edits a DialogueInteractable's direction checkboxes.** The checkboxes call `markSceneDirty()` but push no undo command. Even if dispatch were fixed, Ctrl+Z has nothing to revert for those specific changes. This is the "custom widget undo gap."

3. **User selects an asset in the Asset Browser, edits it, then clicks back to an entity.** Now the InspectorPanel shows entity components. Ctrl+Z should undo the last scene action, but the `inspector.undo` handler still fires and does nothing (the asset inspector was deselected, so `currentInspector` was cleared by `notifyDeselect()`).

### Scope of impact

This affects **every component inspector** -- not just dialogue. Any time the InspectorPanel has focus and shows a component, Ctrl+Z is dead. The user must click another panel (Scene View, Hierarchy) for undo to work. The DialogueInteractableInspector is the worst-affected because it has the most custom widgets that lack undo support.

---

## Current Architecture

### Shortcut dispatch

```
ShortcutRegistry.processShortcuts(context)
  for each binding (sorted by modifier count desc):
    if binding.isPressed():
      match = findBestMatch(candidates, context)  // picks highest-priority scope
      if match != null:
        match.execute()
        return true   // key consumed, no fallthrough
```

Key rule: **one binding, one action, no fallthrough.** When multiple actions share a binding, only the highest-priority one fires. This works correctly for panels with dedicated undo stacks (AnimationEditor, AnimatorEditor, DialogueEditor) because those panels **always** have their own undo context when focused.

### Undo systems

| System | Scope | Stack | Used by |
|--------|-------|-------|---------|
| `UndoManager` (singleton) | Scene-level | Global undo/redo stacks with scope isolation | All scene edits, component field changes, entity operations |
| `AssetInspectorRenderer.undo()` | Per-asset | Each renderer maintains its own stack | Asset inspectors (Sprite, Animation, DialogueEvents, DialogueVariables) |

### The InspectorPanel's dual role

The InspectorPanel shows different content based on selection:

| Selection state | Inspector shown | Undo target |
|----------------|----------------|-------------|
| Entity selected | `EntityInspector` | `UndoManager` (scene) |
| Multiple entities | `MultiSelectionInspector` | `UndoManager` (scene) |
| Asset selected (Asset Browser) | `AssetInspector` -> `AssetInspectorRegistry` | Asset renderer's stack |
| Camera selected | `CameraInspector` | `UndoManager` (scene) |
| Tilemap layer | `TilemapLayersInspector` | `UndoManager` (scene) |
| Collision layer | `CollisionMapInspector` | `UndoManager` (scene) |
| Animator state | `AnimatorStateInspector` | `UndoManager` (scene) |
| Animator transition | `AnimatorTransitionInspector` | `UndoManager` (scene) |
| Trigger selected | `TriggerInspector` | `UndoManager` (scene) |
| Prefab edit mode | `PrefabInspector` | `UndoManager` (scene, scoped) |
| Nothing | "Select an item" text | `UndoManager` (scene) |

Only **one** of these states routes undo to the asset renderer. All others should route to `UndoManager`.

### Other panels with panel-scoped undo

These panels correctly use `PANEL_FOCUSED` undo because they **always** have their own undo context:

- `AnimationEditorPanel` -- `editor.animation.undo` -> `this::undo` (animation-specific undo stack)
- `AnimatorEditorPanel` -- `editor.animator.undo` -> `this::undo` (animator-specific undo stack)
- `DialogueEditorPanel` -- `editor.dialogue.undo` -> `this::undo` (dialogue-specific undo stack)

These are fundamentally different from the InspectorPanel. They are single-purpose panels with dedicated undo contexts. The InspectorPanel is multi-purpose -- its undo target depends on what it is currently showing.

---

## Design Options

### Option A: Context-aware undo routing in InspectorPanel

**Change the handler, not the dispatch system.**

Replace the unconditional `AssetInspectorRegistry::undo` handler with a routing method that checks what the InspectorPanel is currently showing:

```java
// InspectorPanel.java - provideShortcuts()
.handler(this::handleUndo)

// New method
private void handleUndo() {
    if (isShowingAssetInspector()) {
        AssetInspectorRegistry.undo();
    } else {
        // Fall through to scene undo
        UndoManager.getInstance().undo();
    }
}
```

Where `isShowingAssetInspector()` checks the current selection state (same conditions used in `renderCurrentInspector()`).

**Pros:**
- Minimal change: only `InspectorPanel.provideShortcuts()` is modified
- No changes to `ShortcutRegistry`, `ShortcutAction`, or any other panel
- Maintains the "one binding, one action" invariant -- no new dispatch concepts
- Easy to understand: the handler itself knows its context
- The InspectorPanel already has all the state needed to make this decision (`selectionManager`, `wasShowingAssetInspector`)

**Cons:**
- The handler must duplicate some of the routing logic from `renderEditorInspector()`/`renderCurrentInspector()`
- If a new inspector mode is added, both the rendering path and the undo routing must be updated
- Does not address the custom widget undo gap (but that's a separate concern)

### Option B: Fallthrough in the shortcut dispatch

**Add a "soft consume" concept to `ShortcutAction`.**

Allow a shortcut handler to return a boolean indicating whether it actually handled the key. If it returns false, `findBestMatch()` continues to the next priority level:

```java
// ShortcutAction.java
public interface ShortcutHandler {
    boolean handle(); // true = consumed, false = pass to next
}

// ShortcutRegistry.processShortcuts()
for (ShortcutAction candidate : sortedByPriority) {
    if (candidate.isApplicable(context)) {
        if (candidate.execute()) {  // returns boolean now
            return true;  // consumed
        }
        // else: continue to next candidate
    }
}
```

The `inspector.undo` handler would return `false` when `currentInspector == null`, letting the global `editor.edit.undo` fire.

**Pros:**
- Generic solution: any panel-scoped shortcut can opt into fallthrough
- Clean separation: the handler only needs to know if *it* handled the key, not what should happen next
- Could solve future similar conflicts in other panels

**Cons:**
- Changes the fundamental dispatch contract: "one binding, one action" becomes "one binding, first action that claims it"
- **Breaking change risk:** Every existing `Runnable` handler becomes `() -> { ...; return true; }`. All panel shortcuts, all global shortcuts, everything must be migrated or wrapped
- The `ShortcutAction` class currently uses `Runnable` for handlers. Changing to a `BooleanSupplier` or custom functional interface touches every registration site
- Introduces a subtle order dependency: if two `PANEL_FOCUSED` actions share a binding, which one gets first shot? Currently impossible (same scope + same panel = conflict), but the concept opens the door
- Adds complexity to the dispatch loop that most shortcuts will never use -- only the InspectorPanel needs this
- Harder to reason about: "Ctrl+Z might do A, or might do B, depending on whether A's handler returns true" is less predictable than "Ctrl+Z always does A when inspector is focused"

### Option C: Remove InspectorPanel's undo shortcuts entirely

**Make the global undo smart enough to handle both contexts.**

Remove `inspector.undo` / `inspector.redo` from InspectorPanel. Modify the global `editor.edit.undo` handler (in `EditorShortcutHandlersImpl.onUndo()`) to check if an asset is being inspected:

```java
// EditorShortcutHandlersImpl.java
@Override
public void onUndo() {
    if (modeManager != null && modeManager.isPlayMode()) return;

    // Check if asset inspector is active
    if (isAssetInspectorActive()) {
        AssetInspectorRegistry.undo();
        return;
    }

    // Scene undo
    if (UndoManager.getInstance().undo()) {
        if (activeDirtyTracker != null) activeDirtyTracker.markDirty();
        showMessage("Undo");
    }
}
```

**Pros:**
- Eliminates the shadowing problem entirely: only one Ctrl+Z action exists
- No changes to the shortcut dispatch system
- Simpler mental model: Ctrl+Z always goes through the same handler

**Cons:**
- **Violates the panel ownership model:** `EditorShortcutHandlersImpl` shouldn't know about `AssetInspectorRegistry` -- that's the InspectorPanel's concern
- Creates a dependency from the global handler to asset inspection state. Where does `isAssetInspectorActive()` come from? The `EditorShortcutHandlersImpl` doesn't currently have a reference to the `InspectorPanel` or `EditorSelectionManager`
- When the AnimationEditor, AnimatorEditor, or DialogueEditor is focused, their `PANEL_FOCUSED` undo still fires first (correctly). But if the InspectorPanel has no panel-scoped undo, the global undo fires even when the user just switched focus from the Animator to the Inspector while the Animator has pending undo. This is a different bug -- but it reveals that the global handler approach conflates concerns
- Other panels with `PANEL_FOCUSED` undo (Animation, Animator, Dialogue) would continue to work because they still have panel-scoped shortcuts. But the inconsistency is confusing: "why does the Animator panel have its own undo shortcut but the Inspector doesn't?"

### Option D: Conditional applicability on ShortcutAction

**Make `inspector.undo` not applicable when inspecting components.**

Add a custom `isApplicable` override (or a predicate on the builder) that makes the action inapplicable when the InspectorPanel is showing component inspection. When inapplicable, `findBestMatch()` skips it and the global undo fires naturally.

```java
// InspectorPanel.java - provideShortcuts()
panelShortcut()
    .id("inspector.undo")
    .displayName("Inspector Undo")
    .defaultBinding(undoBinding)
    .allowInInput(true)
    .applicableWhen(() -> isShowingAssetInspector())
    .handler(AssetInspectorRegistry::undo)
    .build()
```

This would require adding an `applicableWhen` predicate to `ShortcutAction`:

```java
// ShortcutAction.java
private final BooleanSupplier additionalGuard;

public boolean isApplicable(ShortcutContext context) {
    if (additionalGuard != null && !additionalGuard.getAsBoolean()) {
        return false;
    }
    // ... existing scope checks
}
```

**Pros:**
- Uses the existing dispatch model: `findBestMatch()` naturally skips inapplicable actions and falls through to the next
- No changes to the dispatch loop itself -- just a new field on `ShortcutAction`
- Clean separation: the panel defines when its shortcut is relevant
- Reusable: other panels could use `applicableWhen()` for context-dependent shortcuts
- The global `editor.edit.undo` fires naturally when `inspector.undo` is inapplicable

**Cons:**
- Adds a new concept (`applicableWhen`) to `ShortcutAction`. While small, it's a new API surface
- The predicate captures a reference to the InspectorPanel's state -- if the panel is garbage collected or not yet initialized, the predicate could be stale. This is manageable since panel lifecycle is well-defined
- The predicate runs every frame during shortcut processing (inside `findBestMatch`), but it's just a boolean check, so negligible cost
- Slightly more complex than Option A, for the same effective behavior

---

## Recommended Approach: Option A (Context-aware undo routing)

### Rationale

Option A is the simplest correct solution. It changes the fewest files, introduces no new concepts, and solves the problem completely.

**Why not B (fallthrough)?** Too invasive. Changing `Runnable` to a return-value-based handler touches every shortcut registration in the codebase. The added complexity benefits only the InspectorPanel -- every other panel's undo shortcut has a dedicated context and never needs fallthrough.

**Why not C (remove panel undo)?** Violates separation of concerns. The global handler shouldn't know about asset inspection. It also changes the semantic model for one panel while leaving the others unchanged, creating an inconsistency.

**Why not D (conditional applicability)?** It's a close second. The `applicableWhen` predicate is a clean, reusable concept. But for a single use case, it's over-engineering. If a second panel needs the same pattern in the future, D becomes the better choice and the refactor from A to D is straightforward: extract the routing condition into an `applicableWhen` predicate, revert the handler to `AssetInspectorRegistry::undo`, and add the predicate to the builder. For now, A is simpler.

**Why A?** The InspectorPanel already has all the state it needs. Adding a 5-line routing method to an existing class is the minimum viable change. It follows the same pattern used elsewhere in the codebase (e.g., `EditorShortcutHandlersImpl.onSaveScene()` checks `modeManager.isPrefabEditMode()` before deciding what to save).

---

## Detailed Design

### Changes to `InspectorPanel.java`

Replace the shortcut handlers with context-aware routing methods. The `provideShortcuts` method (lines 267-298) changes the handler references from `AssetInspectorRegistry::undo` / `AssetInspectorRegistry::redo` to `this::handleUndo` / `this::handleRedo`.

Three new private methods are added:

```java
/**
 * Routes undo to the correct system based on current inspection context.
 * When inspecting an asset, delegates to the asset's undo stack.
 * Otherwise, delegates to the scene-level UndoManager.
 */
private void handleUndo() {
    if (isShowingAssetInspector()) {
        AssetInspectorRegistry.undo();
    } else {
        UndoManager.getInstance().undo();
    }
}

/**
 * Routes redo to the correct system based on current inspection context.
 */
private void handleRedo() {
    if (isShowingAssetInspector()) {
        AssetInspectorRegistry.redo();
    } else {
        UndoManager.getInstance().redo();
    }
}

/**
 * Returns true if the InspectorPanel is currently showing an asset inspector
 * (i.e., the undo target is the asset's own undo stack, not the scene UndoManager).
 */
private boolean isShowingAssetInspector() {
    if (selectionManager == null) return false;
    if (prefabEditController != null && prefabEditController.isActive()) return false;
    if (isPlayMode()) return false;
    return selectionManager.isAssetSelected();
}
```

A new import is added:

```java
import com.pocket.rpg.editor.undo.UndoManager;
```

### Files changed

| File | Change |
|------|--------|
| `src/.../editor/panels/InspectorPanel.java` | Replace `AssetInspectorRegistry::undo` / `::redo` handler references with `this::handleUndo` / `this::handleRedo`. Add `handleUndo()`, `handleRedo()`, `isShowingAssetInspector()` methods. Add `UndoManager` import. |

**No other files are changed.** The shortcut system, `AssetInspectorRegistry`, `UndoManager`, `EditorShortcutHandlersImpl`, and all other panels remain untouched.

### Edge cases

1. **Asset inspector popup is active** (`hasPendingPopup()` returns true): The panel is still effectively showing the asset inspector, so `isShowingAssetInspector()` correctly returns true (via `selectionManager.isAssetSelected()` -- the selection hasn't changed yet during the popup).

2. **Prefab edit mode:** Returns false from `isShowingAssetInspector()`, routing to `UndoManager`. This is correct -- prefab edits go through the scene undo system (with scope isolation via `UndoManager.pushScope()`).

3. **Play mode:** Returns false from `isShowingAssetInspector()`. `UndoManager.undo()` is safe to call -- it returns false if the stack is empty.

4. **Animator state/transition selected in Inspector:** `selectionManager.isAssetSelected()` returns false, so undo routes to `UndoManager`. Correct.

5. **Nothing selected:** `selectionManager.isAssetSelected()` returns false, undo routes to `UndoManager`. Correct -- this undoes the last scene action.

---

## Future Work: Custom Widget Undo

This design solves the dispatch conflict but does not address the "custom widget undo gap" -- widgets like direction checkboxes, condition combos, and variable text inputs in `DialogueInteractableInspector` that mutate component fields without pushing undo commands.

Addressing this is a separate concern with two sub-problems:

### Sub-problem 1: Making custom widgets undo-aware

Custom inspectors that use direct ImGui widgets should push `SetComponentFieldCommand` entries. The pattern:

```java
// Before: no undo
if (ImGui.checkbox("Directional##dir", active)) {
    component.setDirectionalInteraction(!active);
    markSceneDirty();
}

// After: with undo
if (ImGui.checkbox("Directional##dir", active)) {
    boolean oldValue = component.isDirectionalInteraction();
    boolean newValue = !active;
    component.setDirectionalInteraction(newValue);
    if (editorEntity() != null) {
        UndoManager.getInstance().push(
            new SetComponentFieldCommand(component, "directionalInteraction",
                oldValue, newValue, editorEntity())
        );
    }
    markSceneDirty();
}
```

This is incremental work -- each custom inspector can be updated independently.

### Sub-problem 2: List operations

List mutations (add/remove conditional dialogues, add/remove conditions) need a different command type -- something like `ListModifyCommand` that captures the list state before and after. This is more complex than field-level undo and should be designed separately.

### Recommendation

Track the custom widget undo gap as a separate improvement. The dispatch fix unblocks Ctrl+Z for all *existing* undo commands when the Inspector is focused, which is the critical fix.

---

## Impact Analysis

### Panels affected

| Panel | Impact |
|-------|--------|
| **InspectorPanel** | Direct change: handler routing added |
| **AnimationEditorPanel** | None -- has its own dedicated undo, unaffected |
| **AnimatorEditorPanel** | None -- has its own dedicated undo, unaffected |
| **DialogueEditorPanel** | None -- has its own dedicated undo, unaffected |
| All other panels | None |

### Inspectors affected

| Inspector | Impact |
|-----------|--------|
| **EntityInspector** | Fixed -- Ctrl+Z now works when focused |
| **MultiSelectionInspector** | Fixed -- Ctrl+Z now works when focused |
| **CameraInspector** | Fixed -- Ctrl+Z now works when focused |
| **TilemapLayersInspector** | Fixed -- Ctrl+Z now works when focused |
| **CollisionMapInspector** | Fixed -- Ctrl+Z now works when focused |
| **AnimatorStateInspector** | Fixed -- Ctrl+Z now works when focused |
| **AnimatorTransitionInspector** | Fixed -- Ctrl+Z now works when focused |
| **TriggerInspector** | Fixed -- Ctrl+Z now works when focused |
| **PrefabInspector** | Fixed -- Ctrl+Z now works when focused |
| **AssetInspector** | Unchanged -- still routes to asset renderer |
| **DialogueInteractableInspector** | Partially fixed -- Ctrl+Z works for FieldEditors-based changes; custom widgets still lack undo (separate concern) |
| All other custom component inspectors | Fixed -- Ctrl+Z works for FieldEditors-based changes |

### Backward compatibility

- **Shortcut config files:** No change. The action IDs remain the same.
- **Shortcut bindings:** No change. Same keybindings, same scope, same priority.
- **User behavior:** Ctrl+Z in the Inspector now does the correct thing instead of doing nothing. No workflow changes needed.

---

## Testing Strategy

### Manual testing

1. **Entity inspection undo:** Select an entity, change a field (e.g. position via Inspector drag), press Ctrl+Z with Inspector focused. Verify the change is undone.
2. **Asset inspection undo:** Select an asset in the Asset Browser (e.g. `.dialogue-events.json`), make an edit, press Ctrl+Z with Inspector focused. Verify the asset edit is undone.
3. **Switch context:** Edit an entity field, then select an asset, then click back to the entity. Press Ctrl+Z. Verify scene undo fires.
4. **Prefab edit mode:** Enter prefab edit, make a change, press Ctrl+Z in Inspector. Verify scoped undo fires.
5. **Play mode:** Verify Ctrl+Z in Inspector does nothing harmful.
6. **Other panels:** Verify Ctrl+Z in Animation/Animator/Dialogue editors still uses their own undo stacks (regression check).

---

## Open Questions

1. **Should `handleUndo()` show a status bar message?** The global `onUndo()` in `EditorShortcutHandlersImpl` calls `showMessage("Undo")`. The new `handleUndo()` doesn't have access to the status bar callback. Recommendation: skip for now. The global handler already shows messages, and the asset inspectors have their own visual undo feedback.

2. **Should `handleUndo()` check play mode?** The global handler blocks undo in play mode. Since `UndoManager.undo()` is safe to call with an empty stack, this is low risk. But adding a guard is cheap and defensive.

3. **Should `handleUndo()` mark the dirty tracker?** The global handler calls `activeDirtyTracker.markDirty()` after a successful undo. The InspectorPanel doesn't have a reference to the dirty tracker. The undo command's `undo()` method may trigger dirty marking itself. This should be verified during implementation.

4. **Option D as a future pattern?** If other panels need conditional shortcut applicability, consider adding the `applicableWhen` predicate to `ShortcutAction` at that time. The refactor from A to D is straightforward.
