# Plan 2: Inspector Authoring Improvements

## Overview

**Problem:** Creating new inspectors that go beyond simple `FieldEditors.drawFloat()` calls is messy. Inspectors needing "label + button + field" rows require ~15 lines of manual ImGui layout math. Optional/nullable color fields with toggles cost ~70 lines (see UIButtonInspector:146-216). There's no layout helper, no optional field helper, and the encyclopedia guide doesn't cover the new tools from Plan 1.

**Approach:** Three things — a public `InspectorRow` layout utility (fixing the 5 bugs identified in the v2 review), a convenience method for the common "optional color field with toggle" pattern, and an updated encyclopedia guide covering all new tools.

**Depends on:** Plan 1 (FieldUndoTracker, accentButton, findComponentInParent must exist first).

---

## Critical Reference Files

| File | Why |
|------|-----|
| `designDoc.md` | v2 InspectorRow bugs to fix (documented in "What This Does NOT Build" section) |
| `editor/ui/fields/FieldEditorUtils.java` | `inspectorRow()`, `LABEL_WIDTH`, `RESET_BUTTON_WIDTH`, `setNextMiddleContent` |
| `editor/ui/fields/FieldEditorContext.java` | Override context, `isActive()` |
| `editor/ui/inspectors/UIButtonInspector.java` | `drawStateColorOverride()` (lines 146-216) — the pattern to extract |
| `Documents/Encyclopedia/customInspectorGuide.md` | Existing guide to update |
| `.claude/reference/common-pitfalls.md` | ImGui push/pop rules |

---

## Phase 1: `InspectorRow` Public Utility

**Goal:** A layout helper for inspector rows that need more than "label + one field." Fixes all 5 bugs from the v2 expert review.

### Tasks

- [ ] Create `InspectorRow.java` in `editor/ui/fields/`
- [ ] Fix v2 bug: constructor parameter type mismatch
- [ ] Fix v2 bug: `nextFlex(String)` hardcodes `4f` instead of `ItemSpacing.x`
- [ ] Fix v2 bug: `withLabel("##hidden")` deducts LABEL_WIDTH unnecessarily
- [ ] Fix v2 bug: nested rows — add nesting guard assertion
- [ ] Fix v2 bug: consume `setNextMiddleContent`/`setNextFieldWidth`/`setNextTooltip` in factory
- [ ] Extract `drawLabel()` from `FieldEditorUtils.inspectorRow()`

### Files to Create

| File | Description |
|------|-------------|
| `editor/ui/fields/InspectorRow.java` | Immediate-mode row layout utility |

### Files to Modify

| File | Change |
|------|--------|
| `editor/ui/fields/FieldEditorUtils.java` | Extract `drawLabel()` as public method |

### Implementation Detail

#### 1a. `InspectorRow`

```java
// editor/ui/fields/InspectorRow.java

public final class InspectorRow {

    private static boolean nestingActive = false;  // nesting guard

    private final float flexWidth;
    private boolean first = true;

    // --- Factories ---

    /** Row with label + N flex slots. */
    public static InspectorRow withLabel(String label, int flexSlots) {
        assertNotNested();
        nestingActive = true;
        consumeNextOverrides();  // clear stale setNextMiddleContent/setNextFieldWidth/setNextTooltip

        float available = ImGui.getContentRegionAvailX();
        if (FieldEditorContext.isActive()) {
            available -= FieldEditorUtils.RESET_BUTTON_WIDTH;
        }

        boolean hasLabel = !label.startsWith("##");
        if (hasLabel) {
            FieldEditorUtils.drawLabel(label);
        }

        float labelW = hasLabel ? FieldEditorUtils.LABEL_WIDTH : 0;
        float remaining = available - labelW;
        return new InspectorRow(remaining, flexSlots);
    }

    /** Row with label + fixed-width content + N flex slots. */
    public static InspectorRow withLabel(String label, float fixedWidth, int flexSlots) {
        assertNotNested();
        nestingActive = true;
        consumeNextOverrides();

        float available = ImGui.getContentRegionAvailX();
        if (FieldEditorContext.isActive()) {
            available -= FieldEditorUtils.RESET_BUTTON_WIDTH;
        }

        boolean hasLabel = !label.startsWith("##");
        if (hasLabel) {
            FieldEditorUtils.drawLabel(label);
        }

        float labelW = hasLabel ? FieldEditorUtils.LABEL_WIDTH : 0;
        float remaining = available - labelW - fixedWidth;
        return new InspectorRow(remaining, flexSlots);
    }

    /** Row with no label, N flex slots (for XY side-by-side). */
    public static InspectorRow noLabel(int flexSlots) {
        assertNotNested();
        nestingActive = true;
        consumeNextOverrides();

        float available = ImGui.getContentRegionAvailX();
        return new InspectorRow(available, flexSlots);
    }

    /** Call when done with the row. Resets nesting guard. */
    public void end() {
        nestingActive = false;
    }

    private InspectorRow(float remaining, int flexSlots) {
        this.flexWidth = flexSlots > 0 ? Math.max(0, remaining / flexSlots) : 0;
    }

    // --- Slot Helpers ---

    /** Call before each flex widget. Inserts sameLine + sets width. */
    public void nextFlex() {
        if (!first) ImGui.sameLine();
        first = false;
        ImGui.setNextItemWidth(flexWidth);
    }

    /** Call before a flex widget with an inline label ("X", "Y"). */
    public void nextFlex(String inlineLabel) {
        if (!first) ImGui.sameLine();
        first = false;
        float spacing = ImGui.getStyle().getItemSpacingX();  // v3 fix: use actual spacing, not hardcoded 4f
        float labelW = ImGui.calcTextSize(inlineLabel).x + spacing;
        ImGui.text(inlineLabel);
        ImGui.sameLine();
        ImGui.setNextItemWidth(Math.max(0, flexWidth - labelW));
    }

    /** Call before a fixed-width widget (button, toggle). Widget determines own width. */
    public void nextFixed() {
        if (!first) ImGui.sameLine();
        first = false;
    }

    /** Call before a fixed-width widget with explicit width. */
    public void nextFixed(float width) {
        if (!first) ImGui.sameLine();
        first = false;
        ImGui.setNextItemWidth(width);
    }

    /** Get the calculated flex width (for manual use). */
    public float getFlexWidth() { return flexWidth; }

    // --- Guards ---

    private static void assertNotNested() {
        if (nestingActive) {
            throw new IllegalStateException(
                "InspectorRow: nested rows are not supported. " +
                "Call end() on the outer row before creating an inner row."
            );
        }
    }

    private static void consumeNextOverrides() {
        // Prevent stale setNextMiddleContent/setNextFieldWidth/setNextTooltip
        // from leaking into this row. These are consumed by inspectorRow() but
        // not by InspectorRow. Clear them so they don't corrupt the next inspectorRow() call.
        FieldEditorUtils.consumeNextFieldWidth();
        FieldEditorUtils.consumeNextMiddleContent();
        FieldEditorUtils.consumeNextTooltip();
    }
}
```

> **Design choice: `end()` instead of `AutoCloseable`.**
> The v2 review noted that `close()` as a no-op is misleading. v3 uses explicit `end()` because
> it does real work (resets the nesting guard). Callers see `row.end()` and know something happens.
> If forgotten, the nesting guard throws on the next `InspectorRow` creation, catching the bug
> immediately.

> **Note:** `FieldEditorUtils` needs public `consumeNextFieldWidth()`, `consumeNextMiddleContent()`,
> `consumeNextTooltip()` methods. These currently exist as private consumers inside `inspectorRow()`.
> Extract them as package-private or public static methods.

#### 1b. Extract `drawLabel()` from FieldEditorUtils

```java
// In FieldEditorUtils.java — extract from inspectorRow()

public static void drawLabel(String label) {
    if (label.startsWith("##")) return;

    float currentPos = ImGui.getCursorPosX();
    float textWidth = ImGui.calcTextSize(label).x;
    boolean truncated = textWidth > LABEL_WIDTH;

    if (truncated) {
        ImVec2 cursorScreen = ImGui.getCursorScreenPos();
        float lineHeight = ImGui.getTextLineHeight();
        ImGui.pushClipRect(cursorScreen.x, cursorScreen.y,
            cursorScreen.x + LABEL_WIDTH, cursorScreen.y + lineHeight, true);
        ImGui.text(label);
        ImGui.popClipRect();
    } else {
        ImGui.text(label);
    }

    if (ImGui.isItemHovered() && truncated) {
        ImGui.setTooltip(label);
    }

    ImGui.sameLine(currentPos + LABEL_WIDTH);
}
```

Refactor `inspectorRow()` to call `drawLabel()` internally (dedup, not a behavioral change).

### Verification

- [ ] Unit test width calculation: `withLabel("Test", 1)` → flexWidth = available - LABEL_WIDTH - RESET (when active)
- [ ] Unit test width calculation: `withLabel("Test", 30f, 1)` → flexWidth = available - LABEL_WIDTH - 30 - RESET
- [ ] Unit test width calculation: `noLabel(2)` → flexWidth = available / 2
- [ ] Unit test `withLabel("##hidden", 1)` → does NOT deduct LABEL_WIDTH
- [ ] Unit test nested row assertion: creating row inside row throws `IllegalStateException`
- [ ] Unit test `end()` resets nesting guard: create → end → create works
- [ ] Visual test: side-by-side with current `inspectorRow()` for a simple "label + field" row
- [ ] Visual test: "label + button + field" row — button and field share space correctly
- [ ] Visual test: "X" / "Y" inline labels — text and fields aligned, no overflow
- [ ] Visual test: very narrow inspector panel — flex widths clamp to 0, no negative widths

---

## Phase 2: Optional Color Field Helper

**Goal:** Extract the 70-line optional color pattern from UIButtonInspector into a reusable method.

### The Problem

UIButtonInspector's `drawStateColorOverride()` (lines 146-216) handles a nullable Vector4f color
field with a checkbox toggle, disabled display, color picker, and undo. This exact pattern is
needed for any "optional override color" (hover color, pressed color, highlight color, etc.).

**Current cost:** 70 lines per optional color field.
**Target cost:** 1 line.

### Tasks

- [ ] Add `drawOptionalColor()` to `FieldEditors`
- [ ] Refactor `UIButtonInspector.drawStateColorOverride()` to use it
- [ ] Verify UIButtonInspector behavior is identical after refactor

### Files to Modify

| File | Change |
|------|--------|
| `editor/ui/fields/FieldEditors.java` | Add `drawOptionalColor()` |
| `editor/ui/inspectors/UIButtonInspector.java` | Replace `drawStateColorOverride()` with new method |

### Implementation Detail

```java
// In FieldEditors.java

/**
 * Draws a nullable color field with a checkbox toggle.
 * When enabled: shows color picker with undo.
 * When disabled: shows greyed-out color picker.
 * Checkbox toggle creates undo command (null ↔ default color).
 *
 * @param label      Field label
 * @param component  The component
 * @param fieldName  The nullable Vector4f field name
 * @param defaultColor Color to use when enabling (typically the component's base color)
 * @param entity     EditorGameObject for undo commands (null in play mode)
 * @return true if any change occurred
 */
public static boolean drawOptionalColor(String label, Component component, String fieldName,
                                         Vector4f defaultColor, EditorGameObject entity) {
    boolean changed = false;
    Object colorObj = ComponentReflectionUtils.getFieldValue(component, fieldName);
    boolean enabled = colorObj != null;

    // Checkbox between label and field
    FieldEditorUtils.setNextMiddleContent(() -> {
        boolean[] enabledRef = { enabled };
        if (ImGui.checkbox("##enable_" + fieldName, enabledRef[0])) {
            Vector4f oldValue = enabled
                ? new Vector4f((Vector4f) ComponentReflectionUtils.getFieldValue(component, fieldName))
                : null;
            Vector4f newValue = enabled ? null : new Vector4f(defaultColor);

            ComponentReflectionUtils.setFieldValue(component, fieldName, newValue);
            if (entity != null) {
                UndoManager.getInstance().push(
                    new SetComponentFieldCommand(component, fieldName, oldValue, newValue, entity)
                );
            }
        }
    });

    if (enabled && colorObj instanceof Vector4f colorVal) {
        // Active color picker — delegates to existing drawColor which handles undo
        changed = FieldEditors.drawColor(label, component, fieldName);
    } else {
        // Disabled placeholder
        FieldEditorUtils.inspectorRow(label, () -> {
            ImGui.beginDisabled();
            float[] dummy = {0, 0, 0, 0};
            ImGui.colorEdit4("##" + fieldName, dummy);
            ImGui.endDisabled();
        });
    }

    return changed;
}
```

> **Note:** Check if `FieldEditors.drawColor()` already handles the undo tracking for the color
> picker correctly (activation/deactivation). If so, this method just wraps it with the
> checkbox toggle logic. If not, use `FieldUndoTracker.trackReflection()` for the color value changes.

**Usage in UIButtonInspector — before (70 lines):**

```java
changed |= drawStateColorOverride("Hover Color", "hoveredColor");
changed |= drawStateColorOverride("Press Color", "pressedColor");
```

**After (2 lines, delete drawStateColorOverride + start value fields):**

```java
changed |= FieldEditors.drawOptionalColor("Hover Color", component, "hoveredColor",
    component.getColor(), editorEntity());
changed |= FieldEditors.drawOptionalColor("Press Color", component, "pressedColor",
    component.getColor(), editorEntity());
```

Delete from UIButtonInspector:
- `drawStateColorOverride()` method (~70 lines)
- `hoveredColorEditStart` field
- `pressedColorEditStart` field

### Verification

- [ ] Manual test: UIButtonInspector — toggle hover color on → color picker appears
- [ ] Manual test: UIButtonInspector — toggle hover color off → greyed-out picker, field is null
- [ ] Manual test: UIButtonInspector — edit hover color → undo restores previous color
- [ ] Manual test: UIButtonInspector — toggle off → undo → toggles back on with previous color
- [ ] Manual test: prefab instance — override indicator on optional color field
- [ ] Manual test: play mode — no NPE (editorEntity is null)

---

## Phase 3: Update Encyclopedia Guide

**Goal:** Update `Documents/Encyclopedia/customInspectorGuide.md` with the new tools from Plan 1 and Plan 2.

### Tasks

- [ ] Add `FieldUndoTracker` section (from Plan 1) — replacing the manual undo pattern
- [ ] Add `InspectorRow` section — layout for label+button+field rows, XY side-by-side
- [ ] Add `accentButton()` section — toggle-style buttons with accent highlighting
- [ ] Add `findComponentInParent<T>()` section — traversing parent hierarchy
- [ ] Add `drawOptionalColor()` section — nullable color fields with toggles
- [ ] Update "Custom Undo Support" section to recommend `FieldUndoTracker` first
- [ ] Add "Common Patterns" section with copy-paste examples for frequent use cases

### Files to Modify

| File | Change |
|------|--------|
| `Documents/Encyclopedia/customInspectorGuide.md` | Add new tool sections, update undo section |

### New Sections to Add

#### FieldUndoTracker (after "Custom Undo Support")

```markdown
### Undo with FieldUndoTracker (Recommended)

For getter/setter fields or raw ImGui widgets, use `FieldUndoTracker` instead of manual tracking:

\```java
// After a raw ImGui widget:
float[] buf = { component.getAlpha() };
FieldEditorUtils.inspectorRow("Alpha", () -> {
    ImGui.sliderFloat("##alpha", buf, 0f, 1f);
});
// One line replaces 10 lines of manual undo tracking:
FieldUndoTracker.track(
    FieldUndoTracker.undoKey(component, "alpha"),
    component.getAlpha(),
    component::setAlpha,
    "Change Alpha"
);
if (buf[0] != component.getAlpha()) {
    component.setAlpha(buf[0]);
    changed = true;
}
\```
```

#### InspectorRow (after "Sections with Headers")

```markdown
### Complex Row Layouts with InspectorRow

When you need more than "label + one field" (e.g., a toggle button next to a drag field):

\```java
// Label + toggle button + drag field
var row = InspectorRow.withLabel("Width", 30f, 1);
row.nextFixed();
if (FieldEditorUtils.accentButton(isPercent, isPercent ? "%%" : "px")) {
    toggleMode();
}
row.nextFlex();
ImGui.dragFloat("##width", buf, 1f);
row.end();

// Two fields side by side (no label)
var row = InspectorRow.noLabel(2);
row.nextFlex("X");
ImGui.dragFloat("##x", xBuf, 1f);
row.nextFlex("Y");
ImGui.dragFloat("##y", yBuf, 1f);
row.end();
\```

**Important:** Always call `row.end()`. Nesting rows is not supported.
```

#### accentButton (in "Preset Buttons" or new section)

```markdown
### Toggle Buttons with Accent Style

\```java
// Button that highlights when active
if (FieldEditorUtils.accentButton(isEnabled, "Enable##myToggle")) {
    isEnabled = !isEnabled;
    changed = true;
}
\```
```

---

## Phase 4: Code Review

- [ ] Review in `Documents/Reviews/inspector-authoring-review.md`
- [ ] Verify all phase verification checklists are complete
- [ ] Verify UIButtonInspector works identically after refactor
- [ ] Check `InspectorRow` handles edge cases (narrow panel, no label, 3+ flex slots)

---

## File Changes Summary

### New Files

| File | Phase | Description |
|------|-------|-------------|
| `editor/ui/fields/InspectorRow.java` | 1 | Row layout utility for complex inspector rows |

### Modified Files

| File | Phase | Changes |
|------|-------|---------|
| `editor/ui/fields/FieldEditorUtils.java` | 1 | Extract `drawLabel()`, expose consume methods |
| `editor/ui/fields/FieldEditors.java` | 2 | Add `drawOptionalColor()` |
| `editor/ui/inspectors/UIButtonInspector.java` | 2 | Replace `drawStateColorOverride()` with `drawOptionalColor()` |
| `Documents/Encyclopedia/customInspectorGuide.md` | 3 | Add new tool sections |

---

## What This Enables for New Inspectors

After Plan 1 + Plan 2, creating a new inspector with complex UI goes from this:

```java
// BEFORE: label + toggle + drag field (15+ lines)
EditorLayout.beforeWidget();
ImGui.text("Width");
ImGui.sameLine();
ImGui.pushStyleColor(ImGuiCol.Button, ...);
ImGui.pushStyleColor(ImGuiCol.ButtonHovered, ...);
ImGui.pushStyleColor(ImGuiCol.ButtonActive, ...);
if (ImGui.smallButton(isPercent ? "%%" : "px")) { toggleMode(); }
ImGui.popStyleColor(3);
ImGui.sameLine();
float usedWidth = ImGui.calcTextSize("Width").x + ImGui.calcTextSize("%%").x + 16f;
ImGui.setNextItemWidth(EditorLayout.calculateWidgetWidth(usedWidth));
if (ImGui.dragFloat("##width", buf, 1f)) { /* apply */ }
if (ImGui.isItemActivated()) { undoStartValues.put(key, currentValue); }
if (ImGui.isItemDeactivatedAfterEdit()) {
    float start = undoStartValues.remove(key);
    if (start != buf[0]) { UndoManager.getInstance().push(...); }
}
```

To this:

```java
// AFTER: same thing (6 lines)
var row = InspectorRow.withLabel("Width", 30f, 1);
row.nextFixed();
if (FieldEditorUtils.accentButton(isPercent, (isPercent ? "%%" : "px") + "##w")) { toggleMode(); }
row.nextFlex();
if (ImGui.dragFloat("##width", buf, 1f)) { component.setWidth(buf[0]); changed = true; }
FieldUndoTracker.track(FieldUndoTracker.undoKey(component, "width"), component.getWidth(), component::setWidth, "Change Width");
row.end();
```
