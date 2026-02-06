# Inspector System Refactor — Design Document (v3)

> **v3 changes:** Restructured based on v2 expert panel review (QA, architecture, product, ImGui).
> Major changes from v2:
> - **Dropped `InspectorField` sealed interface** — over-engineered for one outlier; `sealed` blocks future
>   extension; `instanceof` in `drawFloat` violated its own principle; lambda `identityHashCode` broke undo.
> - **Dropped Phase 5 full migration** — 17 working inspectors gain nothing from forced API change.
> - **`FieldUndoTracker` is now standalone** — string-key primary API, no `InspectorField` dependency.
> - **`InspectorRow` is now a private helper inside UITransformInspector** — it has exactly one consumer.
> - **Added critical bug fix**: `pushOverrideStyle`/`popOverrideStyle` re-query state independently,
>   causing ImGui style stack corruption if override state changes between push and pop.
> - **Honest line target**: 950–1,000 lines for UITransformInspector (not 700–800).
> - **3 focused phases** instead of 6.

---

## Scope & Motivation

### Who actually hurts?

An audit of all 18 custom inspectors (`@InspectorFor`) shows the pain is **heavily concentrated**:

| Inspector | Lines | Mixes APIs? | Manual layout? | Manual undo? | Needs refactor? |
|-----------|-------|-------------|----------------|--------------|-----------------|
| UITransformInspector | 1,230 | YES (heavy) | 38 calls | 4 blocks | **YES** |
| UITextInspector | 536 | Some | 6 calls | 2 blocks | Moderate |
| UIButtonInspector | 389 | Some | 9 calls | 6 blocks | Moderate |
| UIImageInspector | 281 | Minimal | 3 calls | 1 block | Low |
| Other 14 inspectors | 22–250 | No | 0–2 calls | 0–1 blocks | **No** |

**UITransformInspector is the outlier.** The other 17 inspectors work fine with the current APIs.

**Key finding from v2 review:** `EditorLayout` and `EditorFields` are used exclusively by
UITransformInspector. Zero other inspectors use them. The "two competing layout APIs" problem
is entirely contained within one file.

### What this refactor targets

1. **Phase 0** — Quick wins that help all inspectors + fix a critical existing bug
2. **Phase 1** — Centralized undo tracking that eliminates boilerplate in 10+ inspectors
3. **Phase 2** — UITransformInspector targeted refactor using Phase 0+1 tools

### Design principles

1. **Fix real bugs first** — the `pushOverrideStyle`/`popOverrideStyle` asymmetry is a latent Critical bug.
2. **Targeted, not systemic** — build tools for UITransformInspector, not an abstraction layer for 17 inspectors that don't need one.
3. **No forced migration** — existing inspector APIs stay. No inspector is rewritten unless it benefits.
4. **Immediate rendering only** — no deferred lambdas. ImGui's `isItemActivated()` / `isItemDeactivatedAfterEdit()` must work at their call site.

---

## Phase 0: Quick Wins + Critical Bug Fix

**Goal:** Ship high-value, low-risk improvements. No new classes, minimal API changes.

### 0a: Audit `getComponent<T>()` call sites

> **v3 note:** `IGameObject.getComponent()` already returns `<T extends Component> T` (line 54).
> The v2 design claimed this needed a signature change — it does not. The typed generic signature
> already exists. However, some inspector call sites may still use the cast pattern from before the
> signature was genericized. This is a grep-and-fix cleanup, not a design decision.

**Action:** Search for `instanceof` casts after `getComponent()` calls. Remove unnecessary casts
where the generic return type is sufficient. Do NOT create a phase milestone for this — it's
a 15-minute cleanup task.

### 0b: `findComponentInParent<T>()` helper

5 inspectors check parent chains for `LayoutGroup`, `UITransform`, etc.:

```java
// Current: manual parent walk (5–8 lines)
HierarchyItem parent = entity.getHierarchyParent();
if (parent != null) {
    UITransform parentTransform = parent.getComponent(UITransform.class);
    if (parentTransform != null) { ... }
}
```

Add helper to `HierarchyItem`:

```java
default <T extends Component> T findComponentInParent(Class<T> type) {
    HierarchyItem parent = getHierarchyParent();
    int depth = 0;
    while (parent != null && depth < 100) {  // depth guard prevents infinite loop on hierarchy cycles
        T comp = parent.getComponent(type);
        if (comp != null) return comp;
        parent = parent.getHierarchyParent();
        depth++;
    }
    return null;
}
```

> **v3 fix (QA #14):** Added depth guard. Hierarchy cycles shouldn't exist, but a bug in reparenting
> would hang the editor without this guard. Cost: zero. Prevention: priceless.

**Impact:** Reduces parent-walk boilerplate from 5–8 lines to 1 line. Used in UITransformInspector,
LayoutGroupInspectorBase, UIImageInspector.

### 0c: `accentButton()` helper

UITransformInspector pushes 3 style colors for every accent-colored button (px/% toggle, match-parent).
Extract:

```java
// In FieldEditorUtils

public static boolean accentButton(boolean active, String label) {
    if (active) {
        ImGui.pushStyleColor(ImGuiCol.Button, ACCENT_COLOR);
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, ACCENT_HOVER);
        ImGui.pushStyleColor(ImGuiCol.ButtonActive, ACCENT_ACTIVE);
    }
    boolean clicked = ImGui.smallButton(label);
    if (active) ImGui.popStyleColor(3);
    return clicked;
}
```

**Impact:** Eliminates ~40 lines from UITransformInspector. Reusable for any future toggle-style buttons.

### 0d: Fix `pushOverrideStyle` / `popOverrideStyle` asymmetry (CRITICAL)

> **v3 addition.** Identified by QA (#2) and ImGui Expert (#3). This is a latent bug in the current code,
> not a new-design issue.

**The bug:** `FieldEditorContext.pushOverrideStyle()` and `popOverrideStyle()` each independently
call `isFieldOverridden(fieldName)`:

```java
// FieldEditorContext.java:160-173 — CURRENT CODE (BUGGY)
public static void pushOverrideStyle(String fieldName) {
    if (isFieldOverridden(fieldName)) {                    // queries state at T1
        ImGui.pushStyleColor(ImGuiCol.Text, ...);
    }
}

public static void popOverrideStyle(String fieldName) {
    if (isFieldOverridden(fieldName)) {                    // queries state at T2
        ImGui.popStyleColor();
    }
}
```

If override state changes between push and pop (e.g., user clicks the reset button, which calls
`resetFieldToDefault()` clearing the override), push fires but pop doesn't — **ImGui style stack
permanently corrupted**. This violates the project's own `common-pitfalls.md` (lines 9–38).

Today this doesn't trigger because `markFieldOverridden()` is called after `popOverrideStyle()` in
`PrimitiveEditors.drawFloat()` (line 106→110). But the ordering is accidental, not enforced. Any
reordering of the draw methods would silently trigger the bug.

**Fix:** Store the push result, use it for pop:

```java
// FieldEditorContext.java — FIXED

private static boolean overrideStylePushed = false;

public static void pushOverrideStyle(String fieldName) {
    overrideStylePushed = isFieldOverridden(fieldName);
    if (overrideStylePushed) {
        ImGui.pushStyleColor(ImGuiCol.Text, OVERRIDE_COLOR[0], OVERRIDE_COLOR[1],
            OVERRIDE_COLOR[2], OVERRIDE_COLOR[3]);
    }
}

public static void popOverrideStyle() {    // no fieldName param needed
    if (overrideStylePushed) {
        ImGui.popStyleColor();
        overrideStylePushed = false;
    }
}
```

> **Why `popOverrideStyle()` drops its parameter:** The pop should always match the push.
> Re-querying state is the bug. The boolean stored at push time is the only correct source of truth.
>
> **All existing callers** pass the same fieldName to push and pop, so this is a safe signature change.
> A quick grep confirms no caller uses different field names between push/pop.

**Impact:** Fixes a Critical latent bug. Zero behavioral change when things work correctly.
Prevents silent ImGui stack corruption when things go wrong.

### Files to Change

| File | Change |
|------|--------|
| `editor/panels/hierarchy/HierarchyItem.java` | Add `findComponentInParent<T>()` with depth guard |
| `editor/ui/fields/FieldEditorUtils.java` | Add `accentButton()` |
| `editor/ui/fields/FieldEditorContext.java` | Fix `pushOverrideStyle`/`popOverrideStyle` |
| Various inspectors (optional) | Remove unnecessary `instanceof` casts after `getComponent()` |

### Phase 0 Testing

- Unit test `findComponentInParent()` with 3-level hierarchy, null parent, no match, depth > 100
- Compile-verify all inspectors after `popOverrideStyle()` signature change (drops `fieldName` param)
- Manual test: edit prefab instance fields, click reset buttons, verify no ImGui assertion / visual glitch
- Existing editor manual tests pass

---

## Phase 1: Centralized Undo Tracking — `FieldUndoTracker`

**Goal:** Extract the per-widget undo pattern (94 occurrences across 17 files) into a one-line call.

### Problem

Every field editor method has a 10-line undo tracking block. The pattern is identical:

```java
if (ImGui.isItemActivated()) {
    undoStartValues.put(key, currentValue);
}
if (ImGui.isItemDeactivatedAfterEdit()) {
    float start = undoStartValues.remove(key);
    UndoManager.getInstance().push(new SomeUndoCommand(setter, start, current, "Change X"));
}
```

This is duplicated across 4 separate static maps:
- `PrimitiveEditors.undoStartValues` (line 27)
- `VectorEditors.undoStartValues` (line 27)
- `EditorFields.undoStartValues` (line 37)
- `UITransformInspector.percentUndoStart` (per-instance, but never cleared)

**Existing bug:** `EditorFields.undoStartValues` uses plain string keys like `"uiTransform.offset.x"`.
These collide across entities — start a drag on entity A's offset.x, switch to entity B, release
drag → stale undo command pushed for wrong entity. The other maps use `identityHashCode`-based keys
which avoid this by accident (different component instance = different key).

### Solution: `FieldUndoTracker`

> **v3 change:** This is now a **standalone utility** with no `InspectorField` dependency.
> The primary API uses string keys + setter, matching how inspectors already work.

```java
// editor/ui/fields/FieldUndoTracker.java

public final class FieldUndoTracker {

    private static final Map<String, Object> startValues = new HashMap<>();

    /**
     * Track undo for the last ImGui widget. Call IMMEDIATELY after the widget.
     *
     * ORDERING CONTRACT:
     * - Must be called BEFORE any ImGui call that changes "last item"
     *   (text, button, checkbox, etc.)
     * - Safe calls between widget and track(): setTooltip(), sameLine() (no widget after),
     *   popStyleColor(), popID()
     * - Unsafe calls: text(), smallButton(), checkbox(), dragFloat(), any widget render
     *
     * @param key         Unique key — MUST include component identity to avoid cross-entity collisions.
     *                    Use pattern: System.identityHashCode(component) + "@" + fieldId
     * @param current     Current value (pre-edit on activation frame, post-edit on deactivation frame)
     * @param setter      Consumer to apply value on undo/redo
     * @param description Undo menu description (e.g., "Change Width %")
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
     * Track undo for a reflection field. Convenience overload that creates
     * SetComponentFieldCommand for proper prefab override sync.
     *
     * @param key       Unique key (use undoKey(component, fieldName) pattern)
     * @param current   Current field value
     * @param component The component being edited
     * @param fieldName The reflection field name
     * @param entity    The entity (for prefab override tracking) — pass explicitly,
     *                  do NOT read from FieldEditorContext static state
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
     * Clear all tracking state. Called on:
     * - Inspector unbind (CustomComponentInspector.unbind())
     * - Entity selection change (see: SelectionChangeDetector)
     * - Play mode enter/exit (PlayModeController)
     *
     * Also clears legacy undo maps in PrimitiveEditors, VectorEditors, EditorFields
     * to fix the existing cross-entity key collision bug.
     */
    public static void clear() {
        startValues.clear();
        // Also clear legacy maps (until they are migrated to use FieldUndoTracker)
        PrimitiveEditors.clearUndoState();
        VectorEditors.clearUndoState();
        EditorFields.clearUndoState();
    }

    /**
     * Debug: returns the number of pending undo entries.
     * If this grows > 50, something is leaking (abandoned drags).
     */
    public static int pendingCount() {
        return startValues.size();
    }
}
```

### Key design decisions (addressing v2 review findings)

**1. No `InspectorField` dependency (Architect #10, Product #2)**

The v2 design coupled `FieldUndoTracker.track()` to `InspectorField`, which:
- Made Phase 3 depend on Phase 1 (Architect #7 — "cannot be parallel")
- Required building a sealed interface that 17 inspectors don't need (Product #1)
- Introduced `identityHashCode(setter)` instability for per-frame lambdas (Architect #8, QA #5)

The v3 tracker uses plain string keys. Callers build keys using `undoKey(component, fieldId)`.
No lambda identity hashing.

**2. `trackReflection()` takes entity explicitly (Architect #9)**

The v2 design had `ReflectionInspectorField.createUndoCommand()` reading entity from
`FieldEditorContext.getEntity()` — a static field that could be null or stale if called outside
`begin()/end()` scope. The v3 version takes `entity` as an explicit parameter. No silent failures.

**3. String keys, not identity hash keys (Architect #8, QA #5)**

The v2 design used `identityHashCode(setter)` for getter/setter undo keys. Java lambdas created
per-frame (`t::setRotation`) produce a **new object each frame**, so `identityHashCode` changes
every frame. On the activation frame, the tracker stores key X. On the deactivation frame, it
looks up key Y. **Undo silently broke.**

v3 uses `undoKey(component, fieldId)` which is stable because `component` is the same object
across frames and `fieldId` is a string constant.

**4. Ordering contract documented explicitly (QA #8)**

The v2 design said "call IMMEDIATELY after the widget" but didn't specify which ImGui calls are
safe between widget and `track()`. v3 documents the contract:
- **Safe:** `setTooltip()`, `sameLine()` (without widget after), `popStyleColor()`, `popID()`
- **Unsafe:** `text()`, `smallButton()`, `checkbox()`, `dragFloat()`, any widget

**5. `clear()` also clears legacy maps (QA #4)**

The 4 existing undo maps (`PrimitiveEditors`, `VectorEditors`, `EditorFields`, `UITransformInspector`)
are never cleared today. This is a latent bug: `EditorFields.undoStartValues` uses plain string keys
that collide across entities. `clear()` calls `clearUndoState()` on each class, fixing the bug
retroactively without migrating those classes to `FieldUndoTracker`.

### Selection-change detection for `clear()`

> **v3 addition (QA #4):** `InspectorPanel` has no selection-change callback. It re-renders every
> frame based on `selectionManager` state. We need to add change detection.

**Option A (recommended):** Add a `lastSelectedEntityId` field to `EntityInspector` (or
`InspectorPanel`). On each frame, compare against current selection. If changed, call
`FieldUndoTracker.clear()`.

```java
// In EntityInspector or InspectorPanel draw loop:
private int lastSelectedEntityId = -1;

// At top of draw():
int currentId = (selectedEntity != null) ? selectedEntity.getId() : -1;
if (currentId != lastSelectedEntityId) {
    FieldUndoTracker.clear();
    lastSelectedEntityId = currentId;
}
```

This is simple, robust, and requires no event system changes.

### Required `clear()` call sites

| Location | When | Mechanism |
|----------|------|-----------|
| `EntityInspector` / `InspectorPanel` | Different entity selected | Selection-change detection (see above) |
| `CustomComponentInspector.unbind()` | Inspector loses focus | Direct call in existing method |
| `PlayModeController.enterPlayMode()` | Editor → play transition | Direct call |
| `PlayModeController.exitPlayMode()` | Play → editor transition | Direct call |

### Impact on existing code

`FieldUndoTracker` does NOT replace the existing undo tracking in `PrimitiveEditors`, `VectorEditors`,
or `EditorFields`. Those continue to work as-is. The tracker is **opt-in** for:

1. **Custom inspectors** that currently duplicate the 10-line undo pattern (UITextInspector,
   UIButtonInspector, UIPanelInspector, AlphaGroupInspector, DoorInspector, SpawnPointInspector,
   StaticOccupantInspector, WarpZoneInspector — 8 inspectors with manual undo blocks)
2. **UITransformInspector** — replaces `handlePercentUndo()` and the `percentUndoStart` HashMap
3. **Future inspectors** — use `FieldUndoTracker.track()` instead of copying the pattern

Existing `PrimitiveEditors.drawFloat(label, component, fieldName, speed)` is untouched. It continues
to use its own internal `undoStartValues`. Migration of the internal implementations to
`FieldUndoTracker` is optional and can happen incrementally.

### Files to Change

| File | Change |
|------|--------|
| `editor/ui/fields/FieldUndoTracker.java` | **NEW** — Centralized undo tracking |
| `editor/ui/fields/PrimitiveEditors.java` | Add `public static void clearUndoState()` |
| `editor/ui/fields/VectorEditors.java` | Add `public static void clearUndoState()` |
| `editor/ui/layout/EditorFields.java` | Add `public static void clearUndoState()` |
| `editor/ui/inspectors/CustomComponentInspector.java` | Add `FieldUndoTracker.clear()` to `unbind()` |
| `editor/panels/inspector/EntityInspector.java` | Add selection-change detection + `clear()` |
| `editor/ui/inspectors/UITransformInspector.java` | Replace `handlePercentUndo()` (optional, can defer to Phase 2) |

### Phase 1 Testing

- Unit test `track()`: activation → change → deactivation pushes correct command
- Unit test `track()`: activation → no change → deactivation pushes nothing
- Unit test `track()`: two different fields interleaved don't collide
- Unit test `trackReflection()`: creates `SetComponentFieldCommand` with correct entity
- Unit test `clear()`: removes all pending state, including legacy maps
- Unit test `undoKey()`: same component + different fields → different keys; different components + same field → different keys
- Integration test: edit field → undo → verify value restored
- Regression test: selection change mid-drag doesn't push stale undo command
- Regression test: `EditorFields` string-key cross-entity collision is fixed by `clear()` on selection change
- **Document and test which ImGui calls between widget and `track()` are safe vs unsafe** (QA #8)

---

## Phase 2: UITransformInspector Targeted Refactor

**Goal:** Refactor UITransformInspector using Phase 0+1 tools, plus private layout helpers.

### Realistic Target (addressing Product review)

> **v3 correction:** The v2 design claimed 700–800 lines. The savings math was wrong.
> Explicit savings add up to ~265 lines. Domain logic alone is ~390 lines.

| Savings | Lines | How |
|---------|-------|-----|
| Undo boilerplate | ~60 | `FieldUndoTracker` for percent fields, manual undo blocks |
| Style push/pop | ~40 | `accentButton()` helper (Phase 0) |
| Parent chain lookups | ~20 | `findComponentInParent()` (Phase 0) |
| Component casts | ~10 | Already-typed `getComponent<T>()` cleanup |
| Manual width math | ~80 | Private `RowLayout` helper (see below) |
| Method extraction | ~50 | Extracting repeated sub-patterns into private helpers |

**What stays the same:**
- Cascading resize logic + undo (~200 lines) — inherently multi-entity, multi-field
- Anchor/pivot preset grids (~125 lines) — domain-specific UI
- Match-parent toggle logic (~88 lines) — domain-specific behavior
- Rotation/scale section (~143 lines) — domain-specific
- Size section core (~100 lines after helpers extracted) — domain-specific

**Realistic target: 950–1,000 lines** (from 1,230). This is a ~20% reduction, entirely from
eliminating boilerplate and mechanical duplication. The domain complexity is inherent and stays.

### Private `RowLayout` helper (not a public class)

> **v3 change (Product #2, Architect #10):** `InspectorRow` from v2 was proposed as a public class.
> But it has exactly one consumer (UITransformInspector). Making it public creates a maintenance burden
> for an abstraction with one user. Instead, add private helper methods inside UITransformInspector.

```java
// Private inner class or static helper methods inside UITransformInspector

/**
 * Calculates flex width for a row with label + fixed-width content + N flex slots.
 * Does NOT render anything — just returns the width. Caller does layout manually.
 */
private float calculateFlexWidth(float fixedContentWidth, int flexSlots) {
    float available = ImGui.getContentRegionAvailX();
    if (FieldEditorContext.isActive()) {
        available -= FieldEditorUtils.RESET_BUTTON_WIDTH;
    }
    float remaining = available - FieldEditorUtils.LABEL_WIDTH - fixedContentWidth;
    return flexSlots > 0 ? Math.max(0, remaining / flexSlots) : 0;
}
```

If, in the future, a second inspector needs this pattern, extract it into a public utility then.
Not before.

> **v3 fixes for v2's InspectorRow bugs (for reference if extracted later):**
>
> - **Constructor parameter mismatch (QA #7):** The v2 `withLabel(label, fixedWidth, flexSlots)`
>   passed `(flex, fixedWidth)` to constructor `(float remaining, int flexSlots)` — wouldn't compile.
>
> - **`nextFlex(String)` hardcodes 4f instead of `ItemSpacing.x` (ImGui #5):** `sameLine()` inserts
>   `ItemSpacing.x` gap (default ~8f). Using `4f` causes ~4px overflow per slot. Must use
>   `ImGui.getStyle().getItemSpacingX()`.
>
> - **`withLabel("##hidden")` deducts LABEL_WIDTH unnecessarily (ImGui #8):** When label starts
>   with `##`, `drawLabel()` returns without drawing, but `remaining = available - LABEL_WIDTH` still
>   deducts 120f. Current `inspectorRow()` handles this correctly by skipping the label block entirely.
>
> - **Nested rows produce silent visual corruption (ImGui #4):** Inner row calls
>   `getContentRegionAvailX()` which returns full remaining panel width, not the outer slot width.
>   Must add nesting guard assertion if ever extracted as public API.
>
> - **`setNextMiddleContent`/`setNextFieldWidth` silently ignored (QA #16):** These static "next-call"
>   overrides are consumed by `inspectorRow()` but not by a standalone row helper. Must consume/clear
>   them in the factory method if extracted as public API.

### Key Refactorings

**Size axis field — before (UITransformInspector:533-656, ~120 lines):**

Manual `EditorLayout.beforeWidget()`, `ImGui.text()`, `sameLine()`, style push x3, `smallButton()`,
style pop x3, `sameLine()`, `calcTextSize()`, `calculateWidgetWidth()`, `setNextItemWidth()`,
`dragFloat()`, `handlePercentUndo()`.

**After (~40 lines):**

```java
private boolean drawAxisField(String label, String id, boolean isWidth,
                               float currentWidth, float currentHeight,
                               boolean hasParentUITransform) {
    boolean changed = false;
    UITransform.SizeMode mode = isWidth ? component.getWidthMode() : component.getHeightMode();
    boolean isPercent = mode == UITransform.SizeMode.PERCENT;

    // Label
    FieldEditorUtils.inspectorRow(label, () -> {});  // just the label positioning
    // Actually: use manual label + width calculation
    float buttonW = ImGui.calcTextSize(isPercent ? "%%" : "px").x + 8f;
    float flexW = calculateFlexWidth(buttonW, 1);

    // Mode toggle button
    ImGui.sameLine();
    if (FieldEditorUtils.accentButton(isPercent, (isPercent ? "%%" : "px") + "##" + id)) {
        if (hasParentUITransform) changed |= toggleSizeMode(isWidth);
    }
    if (ImGui.isItemHovered()) {
        ImGui.setTooltip(isPercent ? "Switch to pixel size" : "Switch to percentage of parent");
    }

    // Value drag
    ImGui.sameLine();
    ImGui.setNextItemWidth(flexW);
    if (isPercent) {
        changed |= drawPercentDrag(id, isWidth);
    } else {
        changed |= drawPixelDrag(id, isWidth, currentWidth, currentHeight);
    }
    return changed;
}
```

**Percent undo — before (handlePercentUndo, ~20 lines + HashMap):**

```java
// Current: manual undo with instance HashMap
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

**After (1 line):**

```java
// Inside drawPercentDrag(), immediately after ImGui.dragFloat():
FieldUndoTracker.track(
    FieldUndoTracker.undoKey(component, "percent_" + id),
    isWidth ? component.getWidthPercent() : component.getHeightPercent(),
    isWidth ? component::setWidthPercent : component::setHeightPercent,
    "Change " + (isWidth ? "Width" : "Height") + " %"
);
```

The `percentUndoStart` HashMap and `handlePercentUndo()` method are deleted.

**Note:** Complex cascading resize undo (`startSizeEdit()`/`commitSizeEdit()`) stays as-is — it's
multi-entity and multi-field. `FieldUndoTracker` handles the common single-field case.

### `EditorLayout` / `EditorFields` Cleanup

After Phase 2, UITransformInspector no longer uses `EditorLayout` or `EditorFields`. Since no other
inspector uses them either, they can be deleted. This is a cleanup step within Phase 2, not a
separate migration phase.

| File | Action |
|------|--------|
| `editor/ui/layout/EditorLayout.java` | **DELETE** — zero remaining consumers |
| `editor/ui/layout/EditorFields.java` | **DELETE** — zero remaining consumers |
| `editor/ui/layout/LayoutContext.java` | **DELETE** (if exists, internal to EditorLayout) |

### Files to Change

| File | Change |
|------|--------|
| `editor/ui/inspectors/UITransformInspector.java` | Refactor using Phase 0+1 APIs + private helpers |
| `editor/ui/layout/EditorLayout.java` | **DELETE** |
| `editor/ui/layout/EditorFields.java` | **DELETE** |

### Phase 2 Testing

Full manual test checklist for UITransformInspector:
- [ ] Anchor preset grid (all 9 positions + custom value)
- [ ] Pivot preset grid (all 9 positions + custom value)
- [ ] Offset drag with undo/redo
- [ ] Size drag (fixed mode) with cascading children + undo
- [ ] Size drag (percent mode) with undo
- [ ] px / % mode toggle with undo
- [ ] Lock aspect ratio (both modes)
- [ ] Match parent toggles (master + per-property)
- [ ] Layout group disabled state
- [ ] Prefab instance: override styling + reset buttons
- [ ] Play mode: inspector renders without NPE
- [ ] Side-by-side visual comparison with current layout (widths, spacing, alignment)
- [ ] Keyboard entry into drag field → undo works correctly (not just mouse drag)
- [ ] Start drag → switch entity selection mid-drag → verify no stale undo command

---

## Phase Summary

| Phase | Deliverable | New Files | Modified Files | Deleted Files | Value |
|-------|------------|-----------|----------------|---------------|-------|
| 0 | Quick wins + critical override style fix | 0 | 4 | 0 | Immediate — bug fix + helpers for all |
| 1 | `FieldUndoTracker` (standalone) | 1 | 6 | 0 | Eliminates boilerplate in 10+ inspectors |
| 2 | UITransformInspector refactor | 0 | 1 | 2–3 | Targeted — validates Phase 0+1 |

**Recommended order:** 0 → 1 → 2

Phase 0 ships standalone value immediately. Phase 1 is independent of Phase 0 (can be parallel).
Phase 2 depends on both.

**Total: 3 phases, 1 new file, ~11 files modified, 2–3 files deleted.**

---

## What This Does NOT Build (and Why)

### `InspectorField` sealed interface — DEFERRED

The v2 design proposed a sealed interface unifying reflection and getter/setter field access.
Four reviewers identified fundamental problems:

| Problem | Reviewer | Severity |
|---------|----------|----------|
| `sealed` blocks future implementations (TransformEditors, ListEditor, AssetEditor) | Architect #2a | High |
| `instanceof ReflectionInspectorField` in `drawFloat` violates the "callers never check type" principle | Architect #2b | High |
| `identityHashCode(setter)` breaks for per-frame lambdas — undo silently stops working | Architect #8 | Critical |
| `drawResetButton()` on a field descriptor couples data to rendering; 6 no-op methods = two interfaces crammed into one | Architect #2c | Medium |
| `createUndoCommand()` reads static `FieldEditorContext.getEntity()` — fails silently out of scope | Architect #9 | High |
| Only works for scalars; vectors, enums, assets, lists are unaddressed | Architect #6a | High |
| 17 inspectors gain nothing from it; forced migration makes their code more verbose | Product #2 | High |

**When to reconsider:** When a second inspector of UITransformInspector's complexity exists —
one that genuinely mixes reflection and getter/setter APIs and would benefit from a unified
field abstraction. At that point, revisit the interface design with these fixes:
- Regular interface (not sealed) with package-private constructors
- Default typed accessors on the interface (no `instanceof` downcasting)
- Entity passed at construction, not read from static state
- String-based undo keys only (no `identityHashCode(setter)`)
- Override styling and reset buttons stay in draw methods, not on the descriptor

### `InspectorRow` public class — DEFERRED

Exactly one consumer (UITransformInspector). Private helper methods suffice. Extract when a second
consumer exists. See v2 bugs to fix if extracted:
- Constructor parameter type mismatch
- `4f` hardcode instead of `ItemSpacing.x`
- `##hidden` label regression
- Nested row silent corruption
- `setNextMiddleContent` state leak

### Phase 5 full migration — CANCELLED

Forcing 17 working inspectors through a new API:
- `TransformInspector` (28 lines): 3-line draws become 6-line constructions. Net negative.
- `GridMovementInspector` (187 lines): `FieldEditors.drawFloat("Speed", component, "speed", 0.1f)`
  becomes `FieldEditors.drawFloat(new ReflectionInspectorField("Speed", component, "speed"), 0.1f)`.
  Strictly more verbose, same behavior.
- Most inspectors use `FieldEditors` one-liners that already handle undo. No benefit from migration.
- Estimated 4 hours coding + 4–8 hours QA with zero automated test coverage.

---

## Testing Strategy

### Phase 0
- Unit test `findComponentInParent()`: 3-level hierarchy, null parent, no match, depth > 100
- Unit test `popOverrideStyle()` push/pop pairing: push with override → state changes → pop still pops
- Compile-verify all inspectors after `popOverrideStyle()` signature change
- Manual test: prefab instance field editing, reset buttons, override styling
- Existing editor manual tests pass

### Phase 1
- Unit test `track()`: activation → change → deactivation pushes correct command
- Unit test `track()`: activation → no change → deactivation pushes nothing
- Unit test `track()`: two different fields interleaved don't collide
- Unit test `trackReflection()`: creates `SetComponentFieldCommand` with explicit entity
- Unit test `clear()`: removes all pending state including legacy maps
- Unit test `undoKey()`: collision resistance across components and fields
- Integration test: edit field → undo → verify value restored
- Regression test: selection change mid-drag doesn't push stale undo command
- Regression test: `EditorFields` string-key cross-entity collision fixed by `clear()` on selection change
- Test which ImGui calls between widget and `track()` are safe vs. unsafe
- Debug assertion: log warning if `startValues.size() > 50` (leak detection)

### Phase 2
- Full manual test checklist (see Phase 2 section above)
- Side-by-side visual comparison with pre-refactor layout
- Verify `EditorLayout` / `EditorFields` have zero imports remaining in codebase before deletion

---

## Risk Assessment

| Risk | Severity | Mitigation |
|------|----------|-----------|
| `FieldUndoTracker.clear()` not called at all required sites | High | Selection-change detection in EntityInspector; clear legacy maps; debug size assertion |
| Selection change mid-drag pushes stale undo | High | `clear()` on selection change; undo key includes `identityHashCode(component)` |
| `pushOverrideStyle` fix changes behavior | Low | Fix is strictly more correct — push/pop always paired; no behavioral change when override state is stable |
| UITransformInspector refactor introduces visual regression | Medium | Side-by-side comparison; detailed manual test checklist |
| `EditorLayout`/`EditorFields` deletion misses a consumer | Low | Grep for imports before deletion; compiler catches any remaining references |
| Phase 2 doesn't hit 950–1,000 lines | Medium | Domain complexity is inherent; even 1,050 lines is a meaningful improvement with cleaner structure |
| Abandoned drags leak entries in `startValues` | Medium | `clear()` on selection change handles most cases; debug assertion catches the rest |

---

## What This Does NOT Change

- **Serialization** — No changes to component serialization/deserialization
- **Prefab system** — Override tracking works identically
- **EditorCommand interface** — `execute()` / `undo()` / `canMergeWith()` unchanged
- **Complex multi-entity undo** — `UITransformDragCommand`, `CompoundCommand` stay as-is
- **Custom inspector registration** — `@InspectorFor`, `CustomComponentEditorRegistry` untouched
- **ReflectionFieldEditor** — Auto-discovery of component fields unchanged
- **Existing inspector APIs** — `FieldEditors.drawFloat(label, component, fieldName, speed)` and all
  other existing signatures stay exactly as they are. No migration required.

---

## Appendix A: Expert Review Summary (v2 → v3)

This design was reviewed by four expert perspectives. The v3 restructuring addresses their findings:

### Cross-Reviewer Consensus (all four agreed)

| Finding | Resolution in v3 |
|---------|-----------------|
| Ship Phase 0 immediately — zero risk, immediate value | Phase 0 preserved, expanded with critical bug fix |
| `FieldUndoTracker` should be standalone, not coupled to `InspectorField` | Phase 1 redesigned with string-key primary API |
| Fix `pushOverrideStyle`/`popOverrideStyle` — this is a bug today | Added as Phase 0d |
| Refactor UITransformInspector locally, not via system-wide abstraction | Phase 2 uses private helpers, not public classes |
| Do not migrate 17 working inspectors | Phase 5 cancelled; existing APIs preserved |

### Critical / High Findings Addressed

| # | Finding | Reviewer | Resolution |
|---|---------|----------|------------|
| 1 | `pushOverrideStyle`/`popOverrideStyle` re-query state → ImGui stack corruption | QA, ImGui | Fixed in Phase 0d — store push result |
| 2 | `identityHashCode(setter)` unstable for per-frame lambdas → undo breaks | Architect, QA | Eliminated — v3 uses string keys only |
| 3 | Phases 1+3 cannot be parallel (`FieldUndoTracker` depended on `InspectorField`) | Architect | Eliminated — `FieldUndoTracker` is standalone |
| 4 | No selection-change hook for `clear()` | QA | Added selection-change detection in EntityInspector |
| 5 | `instanceof ReflectionInspectorField` in `drawFloat` violates design principle | Architect, QA | Eliminated — `InspectorField` deferred |
| 6 | `sealed` prevents future implementations | Architect | Eliminated — `InspectorField` deferred |
| 7 | 6-phase, 5-class design is disproportionate to one outlier file | Product | Reduced to 3 phases, 1 new file |
| 8 | 700–800 line target unreachable | Product | Corrected to 950–1,000 |
| 9 | `createUndoCommand()` reads static `FieldEditorContext.getEntity()` | Architect | `trackReflection()` takes entity explicitly |

### Medium Findings Addressed

| # | Finding | Reviewer | Resolution |
|---|---------|----------|------------|
| 10 | `EditorFields` string-key collision across entities | QA | `FieldUndoTracker.clear()` fixes retroactively |
| 11 | `InspectorRow` constructor type mismatch | QA | Documented in "v2 bugs" section; row is private helper now |
| 12 | `nextFlex(String)` hardcodes 4f vs `ItemSpacing.x` | ImGui | Documented in "v2 bugs" section |
| 13 | `withLabel("##hidden")` regression | ImGui | Documented in "v2 bugs" section |
| 14 | Nested rows → silent visual corruption | ImGui | Documented in "v2 bugs" section |
| 15 | `setNextMiddleContent` state leak with `InspectorRow` | QA | Documented in "v2 bugs" section |
| 16 | `findComponentInParent` infinite loop on hierarchy cycles | QA | Added depth guard |
| 17 | Facade grows to ~70 methods during transition | Architect | No transition — existing APIs preserved |

### Findings Acknowledged but Deferred

| Finding | Rationale |
|---------|-----------|
| `InspectorField` doesn't handle vectors, enums, assets, lists | Interface deferred entirely; revisit when needed |
| No multi-entity editing story | Explicitly a non-goal of this refactor |
| Frame-level snapshot undo (Unity's `ApplyModifiedProperties()`) | Fundamental architecture change; out of scope |

---

## Appendix B: `FieldUndoTracker.track()` Activation-Frame Value Correctness

> **v3 addition (QA #3).** The v2 design had conflicting code samples with different `track()` call
> semantics. This section clarifies the correct pattern.

The `track()` method captures `current` as the start value on the activation frame. This requires
that `current` holds the **pre-edit** value at that point. This is correct because:

1. `ImGui.isItemActivated()` fires on the frame the user clicks/focuses the widget.
2. On that frame, `ImGui.dragFloat()` typically returns `false` (no drag has occurred yet), so
   the buffer still contains the pre-click value.
3. The `current` parameter passed to `track()` is the field's value read **before** the widget
   call, or the buffer value which hasn't been modified yet.

**Edge case — keyboard text entry:** If `dragFloat` returns `true` on the same frame as
`isItemActivated()` (which can happen when tabbing into a field with keyboard), the captured start
value may be wrong. In practice, ImGui fires `isItemActivated()` on focus, and the first edit
arrives on the next frame. But this should be verified empirically and documented in tests.

**Correct calling pattern:**
```java
float preEditValue = component.getWidthPercent();  // read BEFORE widget
float[] buf = {preEditValue};
if (ImGui.dragFloat("##w", buf, 0.5f)) {
    component.setWidthPercent(buf[0]);
}
// Pass preEditValue (or buf[0] which equals preEditValue on activation frame)
FieldUndoTracker.track(key, preEditValue, component::setWidthPercent, "Change Width %");
```

On the activation frame, `preEditValue` is correct. On the deactivation frame, `preEditValue` is
still the pre-edit value read this frame... wait, that's wrong. On the deactivation frame, we need
the **current** (post-edit) value to compare against the start value.

**Correction:** The `current` parameter serves dual purpose:
- On activation frame: captured as start value (should be pre-edit)
- On deactivation frame: compared against start value (should be post-edit)

Since the same parameter is used for both, and these happen on different frames, the parameter
should always reflect the **current state of the field**:
- Activation frame: field hasn't been edited yet → current = pre-edit ✓
- Deactivation frame: field has been edited → current = post-edit ✓

This works naturally when `current` is read from the component (`component.getWidthPercent()`),
because the component always reflects the latest state.
