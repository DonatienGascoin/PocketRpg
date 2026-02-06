# Plan 1: Quick Wins + Undo Tracker + UITransformInspector Refactor

## Overview

**Problem:** UITransformInspector is 1,230 lines with heavy boilerplate: manual undo tracking, manual ImGui layout math, repeated style push/pop blocks, verbose parent-chain lookups. A latent Critical bug in `pushOverrideStyle`/`popOverrideStyle` can corrupt ImGui's style stack. Undo tracking is duplicated across 4 separate static maps that are never cleared on selection change, causing cross-entity key collisions.

**Approach:** Three focused phases — quick wins for all inspectors (including the critical bug fix), a standalone undo tracker that eliminates boilerplate in 10+ inspectors, and a targeted refactor of UITransformInspector. No new abstraction layers, no forced migration of the 17 inspectors that work fine.

**Target:** UITransformInspector from 1,230 → 950–1,000 lines. Fix cross-entity undo bug. Fix `pushOverrideStyle` bug.

---

## Critical Reference Files

Read before implementing:

| File | Why |
|------|-----|
| `.claude/reference/common-pitfalls.md` | ImGui push/pop rules — the override style fix addresses a violation |
| `editor/ui/fields/FieldEditorContext.java` | Override context, `pushOverrideStyle`/`popOverrideStyle` (lines 160-173) |
| `editor/ui/fields/FieldEditorUtils.java` | `inspectorRow()` (lines 101-151), `LABEL_WIDTH`, `RESET_BUTTON_WIDTH` |
| `editor/ui/fields/PrimitiveEditors.java` | Current undo pattern (lines 87-127), `undoStartValues` (line 27) |
| `editor/ui/layout/EditorFields.java` | `undoStartValues` (line 37), `handleUndo()` (lines 178-190) |
| `editor/ui/inspectors/UITransformInspector.java` | The refactor target |
| `editor/panels/inspector/EntityInspector.java` | Selection-change detection site |
| `editor/ui/inspectors/CustomComponentInspector.java` | `unbind()` — clear() call site |

---

## Phase 1: Quick Wins + Critical Bug Fix

**Goal:** Ship `findComponentInParent<T>()`, `accentButton()`, and fix the `pushOverrideStyle`/`popOverrideStyle` style stack corruption bug. No new classes.

### Tasks

- [ ] Add `findComponentInParent<T>()` to `HierarchyItem`
- [ ] Add `accentButton()` to `FieldEditorUtils`
- [ ] Fix `pushOverrideStyle`/`popOverrideStyle` in `FieldEditorContext`
- [ ] Update all `popOverrideStyle()` call sites (drop `fieldName` param)
- [ ] Clean up unnecessary `instanceof` casts after `getComponent()` calls (optional grep-and-fix)

### Files to Modify

| File | Change |
|------|--------|
| `editor/panels/hierarchy/HierarchyItem.java` | Add `findComponentInParent<T>()` |
| `editor/ui/fields/FieldEditorUtils.java` | Add `accentButton()` |
| `editor/ui/fields/FieldEditorContext.java` | Fix `pushOverrideStyle`/`popOverrideStyle` |
| `editor/ui/fields/PrimitiveEditors.java` | Update `popOverrideStyle()` calls (drop param) |
| `editor/ui/fields/VectorEditors.java` | Update `popOverrideStyle()` calls (drop param) |
| Other editors with `popOverrideStyle` | Update calls (drop param) — grep to find all |

### Implementation Detail

#### 1a. `findComponentInParent<T>()` on HierarchyItem

```java
// In HierarchyItem.java — add as default method

default <T extends Component> T findComponentInParent(Class<T> type) {
    HierarchyItem parent = getHierarchyParent();
    int depth = 0;
    while (parent != null && depth < 100) {
        T comp = parent.getComponent(type);
        if (comp != null) return comp;
        parent = parent.getHierarchyParent();
        depth++;
    }
    return null;
}
```

Depth guard prevents infinite loop if a reparenting bug creates a hierarchy cycle.

#### 1b. `accentButton()` on FieldEditorUtils

```java
// In FieldEditorUtils.java

private static final float[] ACCENT_COLOR = {0.2f, 0.6f, 0.9f, 1.0f};
private static final float[] ACCENT_HOVER = {0.3f, 0.7f, 1.0f, 1.0f};
private static final float[] ACCENT_ACTIVE = {0.15f, 0.5f, 0.8f, 1.0f};

public static boolean accentButton(boolean active, String label) {
    if (active) {
        ImGui.pushStyleColor(ImGuiCol.Button, ACCENT_COLOR[0], ACCENT_COLOR[1], ACCENT_COLOR[2], ACCENT_COLOR[3]);
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, ACCENT_HOVER[0], ACCENT_HOVER[1], ACCENT_HOVER[2], ACCENT_HOVER[3]);
        ImGui.pushStyleColor(ImGuiCol.ButtonActive, ACCENT_ACTIVE[0], ACCENT_ACTIVE[1], ACCENT_ACTIVE[2], ACCENT_ACTIVE[3]);
    }
    boolean clicked = ImGui.smallButton(label);
    if (active) ImGui.popStyleColor(3);
    return clicked;
}
```

> **Note:** Get the actual accent colors from UITransformInspector's existing push calls. The values above are placeholders — match whatever the inspector currently uses.

#### 1c. Fix `pushOverrideStyle` / `popOverrideStyle`

**Current code (FieldEditorContext.java:160-173) — BUGGY:**

```java
public static void pushOverrideStyle(String fieldName) {
    if (isFieldOverridden(fieldName)) {
        ImGui.pushStyleColor(ImGuiCol.Text, ...);
    }
}

public static void popOverrideStyle(String fieldName) {
    if (isFieldOverridden(fieldName)) {  // re-queries — may disagree with push!
        ImGui.popStyleColor();
    }
}
```

**Fixed code:**

```java
private static boolean overrideStylePushed = false;

public static void pushOverrideStyle(String fieldName) {
    overrideStylePushed = isFieldOverridden(fieldName);
    if (overrideStylePushed) {
        ImGui.pushStyleColor(ImGuiCol.Text, OVERRIDE_COLOR[0], OVERRIDE_COLOR[1],
            OVERRIDE_COLOR[2], OVERRIDE_COLOR[3]);
    }
}

public static void popOverrideStyle() {
    if (overrideStylePushed) {
        ImGui.popStyleColor();
        overrideStylePushed = false;
    }
}
```

**Call site update:** All callers currently pass a `fieldName` to `popOverrideStyle()`. Drop the parameter. Grep for `popOverrideStyle(` to find all call sites.

### Verification

- [ ] Unit test `findComponentInParent()`: 3-level hierarchy returns correct component
- [ ] Unit test `findComponentInParent()`: null parent returns null
- [ ] Unit test `findComponentInParent()`: no match returns null
- [ ] Unit test `findComponentInParent()`: depth > 100 returns null (doesn't hang)
- [ ] Compile-verify all inspectors after `popOverrideStyle()` signature change
- [ ] Manual test: edit prefab instance fields → override styling appears
- [ ] Manual test: click reset button on overridden field → styling clears, no visual glitch
- [ ] Manual test: existing editor workflows pass (field editing, undo, prefab override)

---

## Phase 2: Centralized Undo Tracking — `FieldUndoTracker`

**Goal:** Extract the 10-line undo pattern into a one-line call. Fix the cross-entity key collision bug in `EditorFields`. Add selection-change detection for clearing stale undo state.

### Tasks

- [ ] Create `FieldUndoTracker.java`
- [ ] Add `clearUndoState()` to `PrimitiveEditors`
- [ ] Add `clearUndoState()` to `VectorEditors`
- [ ] Add `clearUndoState()` to `EditorFields`
- [ ] Add `FieldUndoTracker.clear()` to `CustomComponentInspector.unbind()`
- [ ] Add selection-change detection to `EntityInspector` (or `InspectorPanel`)
- [ ] Add `FieldUndoTracker.clear()` to play mode transitions in `PlayModeController`

### Files to Create

| File | Description |
|------|-------------|
| `editor/ui/fields/FieldUndoTracker.java` | Centralized undo tracking with string-key API |

### Files to Modify

| File | Change |
|------|--------|
| `editor/ui/fields/PrimitiveEditors.java` | Add `public static void clearUndoState()` |
| `editor/ui/fields/VectorEditors.java` | Add `public static void clearUndoState()` |
| `editor/ui/layout/EditorFields.java` | Add `public static void clearUndoState()` |
| `editor/ui/inspectors/CustomComponentInspector.java` | Add `FieldUndoTracker.clear()` to `unbind()` |
| `editor/panels/inspector/EntityInspector.java` | Add selection-change detection + `FieldUndoTracker.clear()` |
| `editor/EditorUIController.java` or `PlayModeController.java` | Add `FieldUndoTracker.clear()` on play mode transitions |

### Implementation Detail

#### 2a. `FieldUndoTracker`

```java
// editor/ui/fields/FieldUndoTracker.java

package com.pocket.rpg.editor.ui.fields;

import com.pocket.rpg.editor.panels.hierarchy.EditorGameObject;
import com.pocket.rpg.editor.undo.SetComponentFieldCommand;
import com.pocket.rpg.editor.undo.SetterUndoCommand;
import com.pocket.rpg.editor.undo.UndoManager;
import com.pocket.rpg.engine.ecs.Component;
import imgui.ImGui;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

public final class FieldUndoTracker {

    private static final Map<String, Object> startValues = new HashMap<>();

    private FieldUndoTracker() {}

    /**
     * Track undo for the last ImGui widget. Call IMMEDIATELY after the widget.
     *
     * ORDERING CONTRACT:
     * Must be called BEFORE any ImGui call that changes "last item"
     * (text, button, checkbox, dragFloat, etc.)
     * Safe calls between widget and track(): setTooltip(), popStyleColor(), popID()
     *
     * @param key         Unique key — use undoKey(component, fieldId) to avoid cross-entity collisions
     * @param current     Current value of the field (read from component, not from buffer)
     * @param setter      Consumer to apply value on undo/redo
     * @param description Undo menu description
     * @return true if an undo command was pushed (edit completed)
     */
    public static <T> boolean track(String key, T current,
                                     Consumer<T> setter, String description) {
        if (ImGui.isItemActivated()) {
            startValues.put(key, current);
        }

        if (ImGui.isItemDeactivatedAfterEdit() && startValues.containsKey(key)) {
            @SuppressWarnings("unchecked")
            T startValue = (T) startValues.remove(key);
            if (!Objects.equals(startValue, current)) {
                UndoManager.getInstance().push(
                    new SetterUndoCommand<>(setter, startValue, current, description)
                );
                return true;
            }
        }
        return false;
    }

    /**
     * Track undo for a reflection field. Creates SetComponentFieldCommand
     * for proper prefab override sync.
     *
     * @param key       Unique key — use undoKey(component, fieldName)
     * @param current   Current field value
     * @param component The component being edited
     * @param fieldName The reflection field name
     * @param entity    The entity — pass explicitly, NOT from FieldEditorContext
     */
    public static boolean trackReflection(String key, Object current,
                                           Component component, String fieldName,
                                           EditorGameObject entity) {
        if (ImGui.isItemActivated()) {
            startValues.put(key, current);
        }

        if (ImGui.isItemDeactivatedAfterEdit() && startValues.containsKey(key)) {
            Object startValue = startValues.remove(key);
            if (!Objects.equals(startValue, current)) {
                UndoManager.getInstance().push(
                    new SetComponentFieldCommand(component, fieldName, startValue, current, entity)
                );
                return true;
            }
        }
        return false;
    }

    /**
     * Build a standard undo key for a component field.
     * Uses identityHashCode for cross-entity collision avoidance.
     */
    public static String undoKey(Component component, String fieldId) {
        return System.identityHashCode(component) + "@" + fieldId;
    }

    /**
     * Clear all tracking state. Must be called on:
     * - Inspector unbind
     * - Entity selection change
     * - Play mode enter/exit
     */
    public static void clear() {
        startValues.clear();
        PrimitiveEditors.clearUndoState();
        VectorEditors.clearUndoState();
        // EditorFields is in a different package — call directly or make clearUndoState public
        com.pocket.rpg.editor.ui.layout.EditorFields.clearUndoState();
    }

    /** Debug: number of pending undo entries. Log warning if > 50. */
    public static int pendingCount() {
        return startValues.size();
    }
}
```

> **Note:** `EditorFields` is in `editor/ui/layout/`, not `editor/ui/fields/`. The `clear()` method needs a cross-package call. Make `EditorFields.clearUndoState()` public.

#### 2b. `clearUndoState()` methods

Add to each class that has a static `undoStartValues`:

```java
// In PrimitiveEditors.java, VectorEditors.java, EditorFields.java:

public static void clearUndoState() {
    undoStartValues.clear();
}
```

#### 2c. Selection-change detection

```java
// In EntityInspector.java — add field and check at top of draw():

private int lastSelectedEntityId = -1;

// At top of draw() or render() method:
int currentId = (selectedEntity != null) ? selectedEntity.getId() : -1;
if (currentId != lastSelectedEntityId) {
    FieldUndoTracker.clear();
    lastSelectedEntityId = currentId;
}
```

> **Important:** Check what `EntityInspector` uses to track the selected entity. It may use
> `selectionManager.getSelectedEntity()` or a similar API. Match the existing pattern.

#### 2d. Play mode clear

```java
// In PlayModeController (or wherever play mode transitions happen):
// Add to enterPlayMode():
FieldUndoTracker.clear();

// Add to exitPlayMode():
FieldUndoTracker.clear();
```

### Verification

- [ ] Unit test `track()`: activation → change → deactivation pushes `SetterUndoCommand`
- [ ] Unit test `track()`: activation → no change → deactivation pushes nothing
- [ ] Unit test `track()`: two different keys interleaved don't collide
- [ ] Unit test `trackReflection()`: creates `SetComponentFieldCommand` with correct entity
- [ ] Unit test `undoKey()`: same component + different fields → different keys
- [ ] Unit test `undoKey()`: different components + same field → different keys
- [ ] Unit test `clear()`: removes all entries from `startValues`
- [ ] Unit test `clear()`: calls `clearUndoState()` on legacy classes
- [ ] Manual test: edit a field → undo → value restored
- [ ] Manual test: start drag on entity A → select entity B → release drag → no stale undo command
- [ ] Manual test: start drag on entity A's offset.x (EditorFields) → select entity B → release → no wrong undo
- [ ] Manual test: enter play mode mid-drag → exit play mode → no stale undo state

---

## Phase 3: UITransformInspector Targeted Refactor

**Goal:** Refactor UITransformInspector from 1,230 → 950–1,000 lines using Phase 1 + 2 tools, plus private layout helpers. Delete `EditorLayout`/`EditorFields` which have zero other consumers.

### Tasks

- [ ] Replace `handlePercentUndo()` + `percentUndoStart` HashMap with `FieldUndoTracker.track()`
- [ ] Replace accent button push/pop blocks with `FieldEditorUtils.accentButton()`
- [ ] Replace manual parent-chain lookups with `findComponentInParent<T>()`
- [ ] Remove unnecessary `instanceof` casts after `getComponent()` calls
- [ ] Add private `calculateFlexWidth()` helper method
- [ ] Refactor `drawAxisField()` (size section) using helpers
- [ ] Refactor offset/rotation/scale sections where applicable
- [ ] Remove `EditorLayout` / `EditorFields` imports
- [ ] Delete `EditorLayout.java`, `EditorFields.java` (and `LayoutContext.java` if it exists)
- [ ] Verify no other file imports the deleted classes

### Files to Modify

| File | Change |
|------|--------|
| `editor/ui/inspectors/UITransformInspector.java` | Refactor using Phase 1+2 tools + private helpers |

### Files to Delete

| File | Reason |
|------|--------|
| `editor/ui/layout/EditorLayout.java` | Zero consumers after UITransformInspector refactor |
| `editor/ui/layout/EditorFields.java` | Zero consumers after UITransformInspector refactor |
| `editor/ui/layout/LayoutContext.java` | Internal to EditorLayout (delete if exists) |

### Implementation Detail

#### 3a. Private flex width calculator

```java
// Private helper inside UITransformInspector

private float calculateFlexWidth(float fixedContentWidth, int flexSlots) {
    float available = ImGui.getContentRegionAvailX();
    if (FieldEditorContext.isActive()) {
        available -= FieldEditorUtils.RESET_BUTTON_WIDTH;
    }
    float remaining = available - FieldEditorUtils.LABEL_WIDTH - fixedContentWidth;
    return flexSlots > 0 ? Math.max(0, remaining / flexSlots) : 0;
}
```

#### 3b. Replace `handlePercentUndo()` with `FieldUndoTracker`

**Before (~20 lines + HashMap):**

```java
private final Map<String, Float> percentUndoStart = new HashMap<>();

private void handlePercentUndo(String id, boolean isWidth) {
    if (ImGui.isItemActivated()) {
        percentUndoStart.put(id, isWidth ? component.getWidthPercent() : component.getHeightPercent());
    }
    if (ImGui.isItemDeactivatedAfterEdit() && percentUndoStart.containsKey(id)) {
        float start = percentUndoStart.remove(id);
        float current = isWidth ? component.getWidthPercent() : component.getHeightPercent();
        if (start != current) {
            UndoManager.getInstance().push(
                new SetterUndoCommand<>(isWidth ? component::setWidthPercent : component::setHeightPercent,
                    start, current, "Change " + (isWidth ? "Width" : "Height") + " %"));
        }
    }
}
```

**After (1 call, inline):**

```java
// Immediately after ImGui.dragFloat() for percent mode:
FieldUndoTracker.track(
    FieldUndoTracker.undoKey(component, "percent_" + id),
    isWidth ? component.getWidthPercent() : component.getHeightPercent(),
    isWidth ? component::setWidthPercent : component::setHeightPercent,
    "Change " + (isWidth ? "Width" : "Height") + " %"
);
```

Delete `percentUndoStart` field and `handlePercentUndo()` method.

#### 3c. Replace accent button blocks with `accentButton()`

**Before (~8 lines per button):**

```java
if (wasPercent) {
    ImGui.pushStyleColor(ImGuiCol.Button, ...);
    ImGui.pushStyleColor(ImGuiCol.ButtonHovered, ...);
    ImGui.pushStyleColor(ImGuiCol.ButtonActive, ...);
}
if (ImGui.smallButton(modeLabel + "##" + id)) { ... }
if (wasPercent) ImGui.popStyleColor(3);
```

**After (1 line):**

```java
if (FieldEditorUtils.accentButton(isPercent, (isPercent ? "%%" : "px") + "##" + id)) { ... }
```

#### 3d. Replace parent-chain lookups with `findComponentInParent<T>()`

**Before:**

```java
HierarchyItem parent = entity.getHierarchyParent();
UITransform parentTransform = null;
if (parent != null) {
    parentTransform = parent.getComponent(UITransform.class);
}
```

**After:**

```java
UITransform parentTransform = entity.findComponentInParent(UITransform.class);
```

#### 3e. Delete EditorLayout/EditorFields

Before deleting, verify no other file imports them:

```bash
# Run before deletion:
grep -r "EditorLayout\|EditorFields" src/main/java --include="*.java" -l
```

Should return only `UITransformInspector.java` (and the files themselves). If any other file imports them, migrate that file first.

### Verification

- [ ] Anchor preset grid: all 9 positions + custom value + undo/redo
- [ ] Pivot preset grid: all 9 positions + custom value + undo/redo
- [ ] Offset drag X/Y with undo/redo
- [ ] Size drag (fixed/pixel mode) with cascading children + undo
- [ ] Size drag (percent mode) with undo
- [ ] px / % mode toggle with undo
- [ ] Lock aspect ratio (both modes)
- [ ] Match parent toggles (master + per-property)
- [ ] Layout group disabled state (fields disabled when in layout group)
- [ ] Prefab instance: override styling appears on changed fields
- [ ] Prefab instance: reset buttons work on overridden fields
- [ ] Play mode: inspector renders without NPE
- [ ] Side-by-side visual comparison: widths, spacing, alignment match pre-refactor
- [ ] Keyboard entry into drag field → undo works correctly
- [ ] Start drag → switch entity selection mid-drag → no stale undo command
- [ ] No `EditorLayout` or `EditorFields` imports remain in codebase
- [ ] Codebase compiles with zero errors after deletion

---

## File Changes Summary

### New Files

| File | Phase | Description |
|------|-------|-------------|
| `editor/ui/fields/FieldUndoTracker.java` | 2 | Centralized undo tracking |

### Modified Files

| File | Phase | Changes |
|------|-------|---------|
| `editor/panels/hierarchy/HierarchyItem.java` | 1 | Add `findComponentInParent<T>()` |
| `editor/ui/fields/FieldEditorUtils.java` | 1 | Add `accentButton()` |
| `editor/ui/fields/FieldEditorContext.java` | 1 | Fix `pushOverrideStyle`/`popOverrideStyle` |
| `editor/ui/fields/PrimitiveEditors.java` | 1, 2 | Update `popOverrideStyle()` calls; add `clearUndoState()` |
| `editor/ui/fields/VectorEditors.java` | 1, 2 | Update `popOverrideStyle()` calls; add `clearUndoState()` |
| `editor/ui/layout/EditorFields.java` | 2 | Add `clearUndoState()` |
| `editor/ui/inspectors/CustomComponentInspector.java` | 2 | Add `FieldUndoTracker.clear()` to `unbind()` |
| `editor/panels/inspector/EntityInspector.java` | 2 | Selection-change detection + `clear()` |
| `PlayModeController.java` (or equivalent) | 2 | `FieldUndoTracker.clear()` on play mode transitions |
| `editor/ui/inspectors/UITransformInspector.java` | 3 | Refactor using Phase 1+2 tools |

### Deleted Files

| File | Phase | Reason |
|------|-------|--------|
| `editor/ui/layout/EditorLayout.java` | 3 | Zero consumers after refactor |
| `editor/ui/layout/EditorFields.java` | 3 | Zero consumers after refactor |
| `editor/ui/layout/LayoutContext.java` | 3 | Internal to EditorLayout (if exists) |

---

## Phase 4: Code Review

- [ ] Review in `Documents/Reviews/inspector-system-refactor-review.md`
- [ ] Verify all phase verification checklists are complete
- [ ] Check if `Documents/Encyclopedia/` needs updates (ask user)
- [ ] Check if `.claude/reference/common-pitfalls.md` needs updates for the override style fix
- [ ] Check if `.claude/reference/field-editors.md` needs updates for `FieldUndoTracker`
- [ ] Update PROGRESS.md with implementation status
