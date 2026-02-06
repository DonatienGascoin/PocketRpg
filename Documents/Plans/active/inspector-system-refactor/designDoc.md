# Inspector System Refactor — Design Document (v2)

> **v2 changes:** Redesigned based on expert review (QA, architecture, product, ImGui). Major changes:
> InspectorRow uses immediate rendering (not deferred lambdas). InspectorField is now an interface
> hiding reflection vs getter/setter. FieldUndoTracker uses component-scoped keys. Added Phase 0
> (quick wins) and Phase 5 (old API removal). Realistic scope: UITransformInspector is the primary
> beneficiary; most other inspectors are fine as-is.

---

## Scope & Motivation

### Who actually hurts?

The comparison doc identifies three structural problems. But an audit of all 18 custom inspectors shows the pain is **heavily concentrated**:

| Inspector | Lines | Mixes APIs? | Manual layout? | Manual undo? | Needs refactor? |
|-----------|-------|-------------|----------------|--------------|-----------------|
| UITransformInspector | 1,230 | YES (heavy) | 38 calls | 4 blocks | **YES** |
| UITextInspector | 536 | Some | 6 calls | 2 blocks | Moderate |
| UIButtonInspector | 389 | Some | 9 calls | 6 blocks | Moderate |
| UIImageInspector | 281 | Minimal | 3 calls | 1 block | Low |
| Other 14 inspectors | 22–250 | No | 0–2 calls | 0–1 blocks | **No** |

**UITransformInspector is the outlier.** The other 17 inspectors work fine with the current APIs. This refactor is primarily motivated by making complex UI inspectors viable — UITransformInspector today, and similar inspectors as more UI components are added.

### Design principles

1. **Immediate rendering only** — no deferred lambdas, no builder-then-execute pattern. ImGui's `isItemActivated()` / `isItemDeactivatedAfterEdit()` must work at their call site.
2. **Callers don't check `hasReflection()`** — the abstraction handles reflection vs getter/setter internally.
3. **Component-scoped undo keys** — no global key collisions between inspectors.
4. **Incremental value** — Phase 0 ships standalone improvements that help all inspectors immediately.

---

## Phase 0: Quick Wins (No New Abstractions)

**Goal:** Ship high-value, low-risk improvements that help all 18 inspectors. No new classes, no API changes.

### 0a: Typed `getComponent<T>()` on HierarchyItem

Every custom inspector that accesses sibling components does this:

```java
// Current: returns Component, requires cast
Component transformComp = entity.getComponent(UITransform.class);
if (!(transformComp instanceof UITransform uiTransform)) return false;
```

Add a typed overload:

```java
// In HierarchyItem / IGameObject interface
default <T extends Component> T getComponent(Class<T> type) {
    // Existing implementation already finds by class — just cast the return
}
```

This already returns the right type internally — we just need the signature to reflect it. **Wait** — check if the interface already declares this. If `IGameObject.getComponent()` returns `Component`, change its return type to `<T extends Component> T`. If that's a breaking change on the interface, add a typed overload alongside.

**Impact:** Eliminates 15+ casts across 10 inspectors. Zero risk.

### 0b: `findComponentInParent<T>()` helper

5 inspectors check parent chains for `LayoutGroup`, `UITransform`, etc.:

```java
// Current: manual parent walk
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
    while (parent != null) {
        T comp = parent.getComponent(type);
        if (comp != null) return comp;
        parent = parent.getHierarchyParent();
    }
    return null;
}
```

**Impact:** Reduces parent-walk boilerplate from 5–8 lines to 1 line. Used in UITransformInspector, LayoutGroupInspectorBase, UIImageInspector.

### 0c: `accentButton()` helper

UITransformInspector pushes 3 style colors for every accent-colored button (px/% toggle, match-parent). Extract:

```java
// In FieldEditorUtils or a new EditorWidgets utility

public static boolean accentButton(boolean active, String label) {
    boolean wasActive = active;
    if (wasActive) {
        ImGui.pushStyleColor(ImGuiCol.Button, ACCENT_COLOR);
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, ACCENT_HOVER);
        ImGui.pushStyleColor(ImGuiCol.ButtonActive, ACCENT_ACTIVE);
    }
    boolean clicked = ImGui.smallButton(label);
    if (wasActive) ImGui.popStyleColor(3);
    return clicked;
}
```

**Impact:** Eliminates ~40 lines from UITransformInspector. Reusable for any future toggle-style buttons.

### Files to Change

| File | Change |
|------|--------|
| `editor/panels/hierarchy/HierarchyItem.java` | Add typed `getComponent<T>()`, `findComponentInParent<T>()` |
| `editor/ui/fields/FieldEditorUtils.java` | Add `accentButton()` |
| `editor/ui/inspectors/UITransformInspector.java` | Use new helpers (optional, can defer) |

### Phase 0 Testing

- Unit test `findComponentInParent()` with 3-level hierarchy, null parent, no match
- Compile-verify all existing inspectors (signature change)
- Existing editor manual tests pass

---

## Phase 1: Unified Field API — `InspectorField`

**Goal:** One field descriptor that works for both reflection and getter/setter fields, hiding the distinction from callers.

### Problem

Reflection fields get prefab override styling + reset buttons + `SetComponentFieldCommand` undo.
Getter/setter fields get none of that — just `SetterUndoCommand`.

Custom inspectors must choose per-field, mixing two APIs with different signatures, different undo patterns, and different behavior.

### Solution: `InspectorField` Interface

An interface (not a final class — per architecture review) with two implementations that hide the reflection vs getter/setter distinction:

```java
// editor/ui/fields/InspectorField.java

public sealed interface InspectorField permits ReflectionInspectorField, GetterSetterInspectorField {

    String label();
    String undoKey();

    // Value access (Object to avoid boxing overhead on hot path —
    // typed accessors on subtypes for primitives)
    Object getValue();
    void setValue(Object value);

    // Undo — creates the right command type transparently
    EditorCommand createUndoCommand(Object oldValue, Object newValue);

    // Override support — no-ops for getter/setter fields
    boolean isOverridden();
    void markOverridden(Object value);
    boolean canReset();
    void drawResetButton();

    // Override styling — no-ops for getter/setter fields
    void pushOverrideStyle();
    void popOverrideStyle();
}
```

### ReflectionInspectorField

```java
// editor/ui/fields/ReflectionInspectorField.java

public final class ReflectionInspectorField implements InspectorField {

    private final String label;
    private final Component component;
    private final String fieldName;

    public ReflectionInspectorField(String label, Component component, String fieldName) {
        this.label = label;
        this.component = component;
        this.fieldName = fieldName;
    }

    @Override public String undoKey() {
        return System.identityHashCode(component) + "@" + fieldName;
    }

    @Override public Object getValue() {
        return ComponentReflectionUtils.getFieldValue(component, fieldName);
    }

    @Override public void setValue(Object value) {
        ComponentReflectionUtils.setFieldValue(component, fieldName, value);
    }

    // Typed accessors for hot-path primitives (avoid boxing)
    public float getFloat(float defaultValue) {
        return ComponentReflectionUtils.getFloat(component, fieldName, defaultValue);
    }
    public void setFloat(float value) {
        ComponentReflectionUtils.setFieldValue(component, fieldName, value);
    }

    @Override public EditorCommand createUndoCommand(Object oldValue, Object newValue) {
        return new SetComponentFieldCommand(
            component, fieldName, oldValue, newValue,
            FieldEditorContext.getEntity()
        );
    }

    @Override public boolean isOverridden() {
        return FieldEditorContext.isActive()
            && FieldEditorContext.isFieldOverridden(fieldName);
    }

    @Override public void markOverridden(Object value) {
        if (FieldEditorContext.isActive()) {
            FieldEditorContext.markFieldOverridden(fieldName, value);
        }
    }

    @Override public boolean canReset() {
        return isOverridden();
    }

    @Override public void drawResetButton() {
        FieldEditorUtils.drawResetButtonIfNeeded(component, fieldName);
    }

    @Override public void pushOverrideStyle() {
        FieldEditorContext.pushOverrideStyle(fieldName);
    }

    @Override public void popOverrideStyle() {
        FieldEditorContext.popOverrideStyle(fieldName);
    }
}
```

### GetterSetterInspectorField

```java
// editor/ui/fields/GetterSetterInspectorField.java

public final class GetterSetterInspectorField implements InspectorField {

    private final String label;
    private final String key;
    private final Supplier<Object> getter;
    private final Consumer<Object> setter;

    // Constructor + typed factory methods:

    public static GetterSetterInspectorField ofFloat(
            String label, String key,
            DoubleSupplier getter, DoubleConsumer setter) {
        return new GetterSetterInspectorField(label, key,
            () -> (float) getter.getAsDouble(),
            v -> setter.accept((float) v));
    }

    @Override public String undoKey() {
        // Include hash to avoid collisions between inspectors
        return System.identityHashCode(setter) + "@" + key;
    }

    @Override public Object getValue() { return getter.get(); }
    @Override public void setValue(Object value) { setter.accept(value); }

    @Override public EditorCommand createUndoCommand(Object oldValue, Object newValue) {
        return new SetterUndoCommand<>(setter, oldValue, newValue, "Change " + label);
    }

    // Override support — no-ops
    @Override public boolean isOverridden() { return false; }
    @Override public void markOverridden(Object value) { /* no-op */ }
    @Override public boolean canReset() { return false; }
    @Override public void drawResetButton() { /* no-op */ }
    @Override public void pushOverrideStyle() { /* no-op */ }
    @Override public void popOverrideStyle() { /* no-op */ }
}
```

### Key design decisions (addressing review feedback)

1. **Interface, not final class** — Callers program to `InspectorField`, never check `hasReflection()`. Override support methods are no-ops on getter/setter fields, so callers always call them.

2. **`undoKey()` includes identity hash** — Reflection fields use `identityHashCode(component) + "@" + fieldName`. Getter/setter fields use `identityHashCode(setter) + "@" + key`. No collisions between inspectors editing different components.

3. **No generic type parameter** — Uses `Object` internally. Typed accessors (`getFloat()`) on the reflection subtype for the hot path. This avoids boxing overhead and signature bloat per the architecture review.

4. **`drawResetButton()` on the field itself** — Callers don't need to know about `FieldEditorContext` or `FieldEditorUtils`. They just call `field.drawResetButton()` after the field widget.

### Unified PrimitiveEditors Implementation

One method handles both reflection and getter/setter fields:

```java
// PrimitiveEditors.java — single implementation

public static boolean drawFloat(InspectorField field, float speed) {
    Object raw = field.getValue();
    float value = (raw instanceof Number n) ? n.floatValue() : 0f;
    floatBuffer[0] = value;
    String key = field.undoKey();

    ImGui.pushID(key);
    field.pushOverrideStyle();    // No-op for getter/setter

    final boolean[] changed = {false};
    FieldEditorUtils.inspectorRow(field.label(), () -> {
        changed[0] = ImGui.dragFloat("##" + key, floatBuffer, speed);
    });

    // Undo tracking — immediate after widget (inspectorRow executes lambda synchronously)
    if (ImGui.isItemActivated()) {
        undoStartValues.put(key, value);
    }
    boolean deactivated = ImGui.isItemDeactivatedAfterEdit();

    field.popOverrideStyle();     // No-op for getter/setter

    if (changed[0]) {
        field.setValue(floatBuffer[0]);
        field.markOverridden(floatBuffer[0]);  // No-op for getter/setter
    }

    if (deactivated && undoStartValues.containsKey(key)) {
        float startValue = (Float) undoStartValues.remove(key);
        if (startValue != floatBuffer[0]) {
            UndoManager.getInstance().push(
                field.createUndoCommand(startValue, floatBuffer[0])
            );
        }
    }

    field.drawResetButton();      // No-op for getter/setter
    ImGui.popID();
    return changed[0];
}
```

**Note:** `inspectorRow()` executes its lambda synchronously — this is critical. `isItemActivated()` on line after the `inspectorRow()` call works because the dragFloat has already executed.

### Facade: Convenience Factories

`FieldEditors` gets shorthand factory + draw methods:

```java
// FieldEditors.java — factories + unified draw

// Factories
public static InspectorField field(String label, Component component, String fieldName) {
    return new ReflectionInspectorField(label, component, fieldName);
}

public static InspectorField field(String label, String key,
                                    DoubleSupplier getter, DoubleConsumer setter) {
    return GetterSetterInspectorField.ofFloat(label, key, getter, setter);
}

// Draw methods
public static boolean drawFloat(InspectorField field, float speed) {
    return PrimitiveEditors.drawFloat(field, speed);
}
```

### Files to Change

| File | Change |
|------|--------|
| `editor/ui/fields/InspectorField.java` | **NEW** — Sealed interface |
| `editor/ui/fields/ReflectionInspectorField.java` | **NEW** — Reflection implementation |
| `editor/ui/fields/GetterSetterInspectorField.java` | **NEW** — Getter/setter implementation |
| `editor/ui/fields/PrimitiveEditors.java` | Add `drawFloat(InspectorField, speed)` etc. alongside old methods |
| `editor/ui/fields/VectorEditors.java` | Same |
| `editor/ui/fields/EnumEditor.java` | Same |
| `editor/ui/fields/FieldEditors.java` | Add factory methods + InspectorField draw overloads |

Old methods stay (not deprecated yet) — they coexist until Phase 5.

---

## Phase 2: Row Layout — `InspectorRow` (Immediate Mode)

**Goal:** Replace manual `sameLine` / `setCursorPosX` / `setNextItemWidth` chains with a scope-based row helper that calculates widths upfront but renders widgets immediately.

### Problem (unchanged from v1)

`inspectorRow(label, field)` handles "label + field" only. Anything more complex requires manual ImGui layout math.

### Why NOT a Deferred Builder

The v1 design proposed collecting `Runnable` lambdas and executing them in `end()`. The ImGui expert review identified fatal flaws:

1. **`isItemActivated()` / `isItemDeactivatedAfterEdit()` check the last rendered widget** — if widgets render inside `end()`, these checks can't be called at the widget's call site.
2. **Widget return values** require `boolean[]` capture arrays — error-prone and noisy.
3. **Push/pop style safety** is harder to verify when rendering is deferred.
4. **Error stack traces** point to `end()`, not the actual widget.

### Solution: Immediate Scope-Based API

Instead of collecting lambdas, `InspectorRow` calculates widths upfront and exposes them. Widgets render inline at their call site:

```java
// editor/ui/fields/InspectorRow.java

public final class InspectorRow implements AutoCloseable {

    private final float available;
    private final float flexWidth;
    private boolean first = true;

    // --- Construction ---

    /** Row with label + N flex slots. */
    public static InspectorRow withLabel(String label, int flexSlots) {
        float available = ImGui.getContentRegionAvailX();
        if (FieldEditorContext.isActive()) {
            available -= FieldEditorUtils.RESET_BUTTON_WIDTH;
        }

        // Draw label immediately
        FieldEditorUtils.drawLabel(label);

        float remaining = available - FieldEditorUtils.LABEL_WIDTH;
        return new InspectorRow(remaining, flexSlots);
    }

    /** Row with label + fixed-width widget + N flex slots. */
    public static InspectorRow withLabel(String label, float fixedWidth, int flexSlots) {
        float available = ImGui.getContentRegionAvailX();
        if (FieldEditorContext.isActive()) {
            available -= FieldEditorUtils.RESET_BUTTON_WIDTH;
        }

        FieldEditorUtils.drawLabel(label);

        float remaining = available - FieldEditorUtils.LABEL_WIDTH - fixedWidth;
        float flex = flexSlots > 0 ? remaining / flexSlots : 0;
        return new InspectorRow(flex, fixedWidth);
    }

    /** Row with no label, N flex slots (for XY side-by-side). */
    public static InspectorRow noLabel(int flexSlots) {
        float available = ImGui.getContentRegionAvailX();
        float flex = flexSlots > 0 ? available / flexSlots : available;
        return new InspectorRow(available, flexSlots);
    }

    private InspectorRow(float remaining, int flexSlots) {
        this.available = remaining;
        this.flexWidth = flexSlots > 0 ? Math.max(0, remaining / flexSlots) : 0;
    }

    // --- Slot Helpers (called between widgets) ---

    /** Call before each widget to insert sameLine + set width. */
    public void nextFlex() {
        if (!first) ImGui.sameLine();
        first = false;
        ImGui.setNextItemWidth(flexWidth);
    }

    /** Call before each flex widget with an inline label ("X", "Y"). */
    public void nextFlex(String inlineLabel) {
        if (!first) ImGui.sameLine();
        first = false;
        float labelW = ImGui.calcTextSize(inlineLabel).x + 4f;
        ImGui.text(inlineLabel);
        ImGui.sameLine();
        ImGui.setNextItemWidth(Math.max(0, flexWidth - labelW));
    }

    /** Call before a fixed-width widget (button, toggle). */
    public void nextFixed() {
        if (!first) ImGui.sameLine();
        first = false;
        // Natural width — widget determines its own size
    }

    /** Call before a fixed-width widget with explicit width. */
    public void nextFixed(float width) {
        if (!first) ImGui.sameLine();
        first = false;
        ImGui.setNextItemWidth(width);
    }

    /** Get the calculated flex width (for manual use). */
    public float getFlexWidth() { return flexWidth; }

    @Override
    public void close() {
        // AutoCloseable — nothing to clean up, but enables try-with-resources
        // for safety against forgotten close in complex control flow
    }
}
```

### Usage Examples

**Simple field (equivalent to `inspectorRow`):**
```java
try (var row = InspectorRow.withLabel("Speed", 1)) {
    row.nextFlex();
    boolean changed = ImGui.dragFloat("##speed", buf, 0.1f);
    FieldUndoTracker.track(field, buf[0]);  // Works — widget just rendered
}
```

**Field with toggle button:**
```java
try (var row = InspectorRow.withLabel("W", 30f, 1)) {
    row.nextFixed();
    if (accentButton(isPercent, "%%" + "##sizeW")) toggleMode();

    row.nextFlex();
    boolean changed = ImGui.dragFloat("##sizeW", buf, 1f);
    // isItemActivated() works here — dragFloat just rendered inline
}
```

**Two fields side by side:**
```java
try (var row = InspectorRow.noLabel(2)) {
    row.nextFlex("X");
    changed |= ImGui.dragFloat("##x", xBuf, 1f);

    row.nextFlex("Y");
    changed |= ImGui.dragFloat("##y", yBuf, 1f);
}
```

### Why This Works with ImGui

- **Widgets render inline** — `dragFloat()` executes at its call site, not in a deferred lambda.
- **`isItemActivated()` works immediately** — the last widget is the one just rendered.
- **Widget return values are direct `boolean`** — no `boolean[]` wrappers needed.
- **Push/pop pairs are visible** — they wrap the widget call, not a Runnable.
- **try-with-resources** guards against forgotten cleanup (though `close()` is a no-op here, the pattern prevents bugs if we add cleanup later).
- **`Math.max(0, ...)` guards against negative widths** — if fixed content exceeds available space, flex widgets get 0 width instead of negative.

### Extracting `drawLabel()` from FieldEditorUtils

The label rendering logic (clipping, truncation tooltip) currently lives inside `inspectorRow()`. Extract it into a standalone method so `InspectorRow` can reuse it:

```java
// In FieldEditorUtils

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

### Nesting

**Nesting is not supported.** `InspectorRow` calculates width based on available space at construction time. If a flex slot starts a new `InspectorRow`, the inner row sees reduced available space (which is correct — it's inside the outer slot). This works naturally because the width calculation uses `ImGui.getContentRegionAvailX()` at the point of construction, and `setNextItemWidth()` constrains the slot. **But** nested rows should be documented as unsupported — the inner row's widths may not add up correctly if the outer row's `sameLine()` shifts the cursor.

### Files to Change

| File | Change |
|------|--------|
| `editor/ui/fields/InspectorRow.java` | **NEW** — Immediate scope-based row layout |
| `editor/ui/fields/FieldEditorUtils.java` | Extract `drawLabel()` from `inspectorRow()` |
| `editor/ui/layout/EditorLayout.java` | Untouched (old API stays until Phase 5) |
| `editor/ui/layout/EditorFields.java` | Untouched (old API stays until Phase 5) |

---

## Phase 3: Centralized Undo Tracking — `FieldUndoTracker`

**Goal:** Extract the per-widget undo pattern into a one-line call.

### Problem (unchanged)

Every field editor method has a 10-line undo tracking block. ~25 copies across the codebase.

### Solution: `FieldUndoTracker`

```java
// editor/ui/fields/FieldUndoTracker.java

public final class FieldUndoTracker {

    // Component-scoped: identityHashCode(component/setter) + "@" + fieldKey → start value
    private static final Map<String, Object> startValues = new HashMap<>();

    /**
     * Track undo for the last ImGui widget. Call IMMEDIATELY after the widget.
     *
     * IMPORTANT: Must be called before any other ImGui call — isItemActivated()
     * and isItemDeactivatedAfterEdit() check the LAST rendered widget.
     *
     * @param field   The InspectorField being edited
     * @param current The current value after potential edit
     * @return true if an undo command was pushed (edit completed)
     */
    public static boolean track(InspectorField field, Object current) {
        String key = field.undoKey();  // Already component-scoped

        if (ImGui.isItemActivated()) {
            startValues.put(key, current);
        }

        if (ImGui.isItemDeactivatedAfterEdit() && startValues.containsKey(key)) {
            Object startValue = startValues.remove(key);
            if (!Objects.equals(startValue, current)) {
                UndoManager.getInstance().push(
                    field.createUndoCommand(startValue, current)
                );
                return true;
            }
        }
        return false;
    }

    /**
     * Track undo for a raw key + setter (for one-off cases in custom inspectors
     * that don't use InspectorField).
     *
     * @param key         Unique key — MUST include component identity to avoid collisions.
     *                    Use pattern: System.identityHashCode(component) + "@" + fieldId
     * @param current     Current value
     * @param setter      Consumer to apply value on undo/redo
     * @param description Undo menu description
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
     * Clear all tracking state. Must be called on:
     * - Inspector unbind (CustomComponentInspector.unbind())
     * - Entity selection change (InspectorPanel)
     * - Play mode enter/exit (PlayModeController)
     */
    public static void clear() {
        startValues.clear();
    }
}
```

### Key collision prevention (addressing QA review)

`InspectorField.undoKey()` returns component-scoped keys:
- `ReflectionInspectorField`: `System.identityHashCode(component) + "@" + fieldName`
- `GetterSetterInspectorField`: `System.identityHashCode(setter) + "@" + key`

Two inspectors editing different components will have different identity hashes. Two fields on the same component will have different field names. No collisions.

### Required `clear()` call sites

| Location | When |
|----------|------|
| `CustomComponentInspector.unbind()` | Inspector loses focus |
| `InspectorPanel` selection change | Different entity selected |
| `PlayModeController.enterPlayMode()` | Editor → play transition |
| `PlayModeController.exitPlayMode()` | Play → editor transition |

### Impact on PrimitiveEditors (with Phase 1)

Combined with `InspectorField`, the float method becomes:

```java
public static boolean drawFloat(InspectorField field, float speed) {
    float value = (field instanceof ReflectionInspectorField rf)
        ? rf.getFloat(0f) : ((Number) field.getValue()).floatValue();
    floatBuffer[0] = value;

    ImGui.pushID(field.undoKey());
    field.pushOverrideStyle();

    final boolean[] changed = {false};
    FieldEditorUtils.inspectorRow(field.label(), () -> {
        changed[0] = ImGui.dragFloat("##f", floatBuffer, speed);
    });

    // One-line undo tracking — replaces 10-line block
    FieldUndoTracker.track(field, floatBuffer[0]);

    field.popOverrideStyle();

    if (changed[0]) {
        field.setValue(floatBuffer[0]);
        field.markOverridden(floatBuffer[0]);
    }

    field.drawResetButton();
    ImGui.popID();
    return changed[0];
}
```

### Impact on UITransformInspector

`handlePercentUndo()` (20 lines) becomes:

```java
// Inside drawAxisField(), immediately after the percent dragFloat:
FieldUndoTracker.track(
    System.identityHashCode(component) + "@percent_" + id,
    isWidth ? t.getWidthPercent() : t.getHeightPercent(),
    isWidth ? t::setWidthPercent : t::setHeightPercent,
    "Change " + (isWidth ? "Width" : "Height") + " %"
);
```

The `percentUndoStart` HashMap and `handlePercentUndo()` method are deleted.

**Note:** Complex cascading resize undo (`startSizeEdit()`/`commitSizeEdit()`) stays as-is — it's multi-entity and multi-field. `FieldUndoTracker` handles the common single-field case.

### Files to Change

| File | Change |
|------|--------|
| `editor/ui/fields/FieldUndoTracker.java` | **NEW** — Centralized undo tracking |
| `editor/ui/fields/PrimitiveEditors.java` | Replace inline undo blocks (in new InspectorField methods) |
| `editor/ui/fields/VectorEditors.java` | Same |
| `editor/ui/inspectors/CustomComponentInspector.java` | Add `FieldUndoTracker.clear()` to `unbind()` |
| `editor/panels/InspectorPanel.java` | Add `FieldUndoTracker.clear()` on selection change |
| `editor/ui/inspectors/UITransformInspector.java` | Replace `handlePercentUndo()` |

---

## Phase 4: UITransformInspector Refactor

**Goal:** Refactor UITransformInspector using Phases 0–3, proving the new APIs work end-to-end.

### Realistic Target (addressing product review)

The v1 design claimed 400–500 lines. That was optimistic. The domain logic (cascading resize, aspect lock, match-parent, preset grids) is inherently complex. **Realistic target: 700–800 lines** (from 1,230), achieved by eliminating:

| Savings | Lines | How |
|---------|-------|-----|
| Manual width math | ~100 | `InspectorRow` calculates widths |
| Undo boilerplate | ~60 | `FieldUndoTracker` for percent fields |
| Style push/pop | ~40 | `accentButton()` helper (Phase 0) |
| Parent chain lookups | ~20 | `findComponentInParent()` (Phase 0) |
| Component casts | ~15 | Typed `getComponent<T>()` (Phase 0) |
| API unification | ~30 | `InspectorField` eliminates mixed API patterns |

**What stays the same:**
- Cascading resize logic + undo (~200 lines) — inherently multi-entity
- Anchor/pivot preset grids (~90 lines) — domain-specific UI
- Match-parent toggle logic (~100 lines) — domain-specific behavior

### Key Refactorings

**Size axis field — before (UITransformInspector:533-656, ~120 lines):**
Manual `EditorLayout.beforeWidget()`, `ImGui.text()`, `sameLine()`, style push x3, `smallButton()`, style pop x3, `sameLine()`, `calcTextSize()`, `calculateWidgetWidth()`, `setNextItemWidth()`, `dragFloat()`, `handlePercentUndo()`.

**After (~30 lines):**
```java
private boolean drawAxisField(String label, String id, boolean isWidth,
                               float currentWidth, float currentHeight,
                               boolean hasParentUITransform) {
    boolean changed = false;
    UITransform.SizeMode mode = isWidth ? component.getWidthMode() : component.getHeightMode();
    boolean isPercent = mode == UITransform.SizeMode.PERCENT;

    try (var row = InspectorRow.withLabel(label, 30f, 1)) {
        // Mode toggle button
        row.nextFixed();
        if (FieldEditorUtils.accentButton(isPercent, (isPercent ? "%%" : "px") + "##" + id)) {
            if (hasParentUITransform) changed |= toggleSizeMode(isWidth);
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(isPercent ? "Switch to pixel size" : "Switch to percentage of parent");
        }

        // Value drag
        row.nextFlex();
        if (isPercent) {
            changed |= drawPercentDrag(id, isWidth);
        } else {
            changed |= drawPixelDrag(id, isWidth, currentWidth, currentHeight);
        }
    }
    return changed;
}
```

**Offset fields — before (uses EditorLayout + EditorFields, ~10 lines):**
```java
EditorLayout.beginHorizontal(2);
changed |= EditorFields.floatField("X", "uiTransform.offset.x", ...);
changed |= EditorFields.floatField("Y", "uiTransform.offset.y", ...);
EditorLayout.endHorizontal();
```

**After (uses InspectorRow, ~8 lines):**
```java
try (var row = InspectorRow.noLabel(2)) {
    row.nextFlex("X");
    changed |= drawOffsetAxis(true);

    row.nextFlex("Y");
    changed |= drawOffsetAxis(false);
}
```

### Files to Change

| File | Change |
|------|--------|
| `editor/ui/inspectors/UITransformInspector.java` | Refactor using Phases 0–3 APIs |

---

## Phase 5: Old API Removal

**Goal:** Remove the old `drawFloat(label, component, fieldName, speed)` and `drawFloat(label, key, getter, setter, speed)` signatures, plus `EditorLayout` / `EditorFields`. Clean cut, no deprecated wrappers.

### Prerequisites

- Phases 0–4 shipped and stable (at least 2 weeks without regressions)
- All 18 custom inspectors migrated to `InspectorField` + `InspectorRow`

### Migration Scope

| Inspector | Complexity | Estimated effort |
|-----------|-----------|-----------------|
| TransformInspector | Trivial (3 lines) | 5 min |
| AlphaGroupInspector | Trivial | 5 min |
| CameraBoundsZoneInspector | Simple | 10 min |
| DoorInspector | Simple | 10 min |
| GridMovementInspector | Moderate | 20 min |
| SpawnPointInspector | Simple | 10 min |
| StaticOccupantInspector | Simple | 10 min |
| UICanvasInspector | Simple | 10 min |
| UIButtonInspector | Moderate | 30 min |
| UIImageInspector | Moderate | 20 min |
| UITextInspector | Moderate | 30 min |
| UIPanelInspector | Simple | 10 min |
| WarpZoneInspector | Simple | 10 min |
| 3x LayoutGroupInspectors | Simple | 15 min each |
| UITransformInspector | Already done in Phase 4 | 0 |

**Total estimated migration: ~4 hours** — straightforward because most inspectors use simple reflection fields.

### What Gets Deleted

| File | Action |
|------|--------|
| `editor/ui/layout/EditorLayout.java` | **DELETE** |
| `editor/ui/layout/EditorFields.java` | **DELETE** |
| `editor/ui/layout/LayoutContext.java` | **DELETE** (if exists, internal to EditorLayout) |
| `editor/ui/fields/PrimitiveEditors.java` | Remove old method signatures (reflection + getter/setter string-key variants) |
| `editor/ui/fields/VectorEditors.java` | Same |
| `editor/ui/fields/EnumEditor.java` | Same |
| `editor/ui/fields/FieldEditors.java` | Remove old method signatures, remove `dragFloat`/`dragVector2f`/etc. raw variants |

### Migration Guide for Inspector Authors

**Reflection field (most common):**
```java
// Before:
FieldEditors.drawFloat("Speed", component, "speed", 0.1f);

// After:
var speed = new ReflectionInspectorField("Speed", component, "speed");
FieldEditors.drawFloat(speed, 0.1f);
// Or create in bind() and reuse:
FieldEditors.drawFloat(this.speedField, 0.1f);
```

**Getter/setter field:**
```java
// Before:
FieldEditors.drawFloat("Rotation", "rot", t::getRotation, t::setRotation, 0.5f);

// After:
var rotation = GetterSetterInspectorField.ofFloat("Rotation", "rot", t::getRotation, t::setRotation);
FieldEditors.drawFloat(rotation, 0.5f);
```

**Horizontal layout:**
```java
// Before:
EditorLayout.beginHorizontal(2);
EditorFields.floatField("X", "key.x", getterX, setterX, 1f);
EditorFields.floatField("Y", "key.y", getterY, setterY, 1f);
EditorLayout.endHorizontal();

// After:
try (var row = InspectorRow.noLabel(2)) {
    row.nextFlex("X");
    ImGui.dragFloat("##key.x", xBuf, 1f);
    FieldUndoTracker.track(xField, xBuf[0]);

    row.nextFlex("Y");
    ImGui.dragFloat("##key.y", yBuf, 1f);
    FieldUndoTracker.track(yField, yBuf[0]);
}
```

---

## Phase Summary

| Phase | Deliverable | New Files | Modified Files | Value |
|-------|------------|-----------|----------------|-------|
| 0 | Quick wins (typed getComponent, findInParent, accentButton) | 0 | 3 | Immediate — helps all inspectors |
| 1 | `InspectorField` interface + implementations | 3 | 4 | Foundation — new API coexists with old |
| 2 | `InspectorRow` (immediate mode) | 1 | 1 | Layout helper — opt-in for complex rows |
| 3 | `FieldUndoTracker` | 1 | 4 | Internal cleanup — eliminates 25 undo blocks |
| 4 | UITransformInspector refactor | 0 | 1 | Proof — validates phases 0–3 together |
| 5 | Old API removal + full migration | 0 (3 deleted) | ~20 | Clean cut — one API, no duplication |

**Recommended order:** 0 → 1 → 3 → 2 → 4 → 5

Phase 0 ships standalone value immediately. Phases 1+3 can be developed in parallel (undo tracker is independent of field descriptor). Phase 2 (layout) builds on 1. Phase 4 validates everything. Phase 5 cleans up after Phase 4 proves stability.

---

## Testing Strategy

### Phase 0
- Unit test `findComponentInParent()`: 3-level hierarchy, null parent, no match, multiple matches (returns first)
- Compile-verify all inspectors with typed `getComponent<T>()`
- Existing editor manual tests pass

### Phase 1
- Unit test `ReflectionInspectorField`: getValue/setValue roundtrip, undoKey uniqueness, createUndoCommand type, isOverridden with/without FieldEditorContext
- Unit test `GetterSetterInspectorField`: same, plus verify override methods are no-ops
- Unit test `undoKey()` collision resistance: two different components with same field name produce different keys
- Integration test: render one inspector with old API, same inspector with new API, verify identical ImGui output

### Phase 2
- Unit test width calculation: 1 flex, 2 flex, 1 fixed + 1 flex, label + fixed + flex
- Edge case: zero flex slots, negative available space (narrow panel), fixedWidth > available
- Visual test: UITransformInspector side-by-side comparison

### Phase 3
- Unit test `track()`: activation → change → deactivation pushes correct command
- Unit test `track()`: activation → no change → deactivation pushes nothing
- Unit test `track()`: two different fields interleaved don't collide
- Unit test `clear()`: removes all pending state
- Integration test: edit field → undo → verify value restored
- Regression test: selection change mid-drag doesn't push stale undo command

### Phase 4
- Full manual test checklist for UITransformInspector:
  - [ ] Anchor preset grid (all 9 positions + custom value)
  - [ ] Pivot preset grid (all 9 positions + custom value)
  - [ ] Offset drag with undo/redo
  - [ ] Size drag (fixed mode) with cascading children + undo
  - [ ] Size drag (percent mode) with undo
  - [ ] px ↔ % mode toggle with undo
  - [ ] Lock aspect ratio (both modes)
  - [ ] Match parent toggles (master + per-property)
  - [ ] Layout group disabled state
  - [ ] Prefab instance: override styling + reset buttons
  - [ ] Play mode: inspector renders without NPE

### Phase 5
- All 18 inspectors compile
- Full editor manual test pass
- No `EditorLayout` or `EditorFields` imports remain in codebase

---

## Risk Assessment

| Risk | Severity | Mitigation |
|------|----------|-----------|
| `InspectorField` interface too heavy for simple inspectors | Medium | Simple inspectors can create fields inline — no bind()-time caching needed |
| `InspectorRow` width math differs from current layout | Medium | Side-by-side visual comparison during Phase 4 |
| `FieldUndoTracker.clear()` not called at all required sites | High | Document required call sites; add debug assertion that warns if startValues grows > 50 entries |
| Phase 5 migration breaks obscure inspector | Medium | Migration is mechanical — each inspector is small and independently testable |
| Selection change mid-drag pushes stale undo | High | `clear()` on selection change; undo key includes component identity hash |
| Two InspectorField implementations diverge in behavior | Medium | Sealed interface prevents third-party implementations; both tested against same contract |

---

## What This Does NOT Change

- **Serialization** — No changes to component serialization/deserialization
- **Prefab system** — Override tracking works identically (just accessed through `InspectorField` interface)
- **EditorCommand interface** — `execute()` / `undo()` / `canMergeWith()` unchanged
- **Complex multi-entity undo** — `UITransformDragCommand`, `CompoundCommand` stay as-is
- **Custom inspector registration** — `@InspectorFor`, `CustomComponentEditorRegistry` untouched
- **ReflectionFieldEditor** — Auto-discovery of component fields unchanged

---

## Appendix: Expert Review Summary

This design was reviewed by four perspectives. Key feedback incorporated:

| Reviewer | Key Concern | Resolution |
|----------|------------|------------|
| **QA Engineer** | FieldUndoTracker static state causes key collisions | Keys now include `identityHashCode(component/setter)` — no collisions |
| **QA Engineer** | InspectorRow.end() silently fails if not called | Switched to AutoCloseable + try-with-resources pattern |
| **QA Engineer** | Reset button behavior changes for getter/setter fields | `InspectorField.drawResetButton()` is a no-op for getter/setter — same as current behavior |
| **Senior Architect** | InspectorField leaks `hasReflection()` | Replaced with sealed interface — callers never check implementation type |
| **Senior Architect** | Over-generalized from one outlier | Added Phase 0 (quick wins for all), acknowledged scope in "Who actually hurts?" section |
| **Senior Architect** | Facade keeps growing | Phase 5 removes old methods; net method count decreases |
| **Product Owner** | All value deferred to Phase 4 | Phase 0 ships standalone value; Phases 1+3 clean up internal code |
| **Product Owner** | Higher-value wins ignored (typed getComponent, findInParent) | Added as Phase 0 |
| **Product Owner** | "No deprecated" forces big-bang migration | Phase 5 is separate; old APIs coexist until stability proven |
| **ImGui Expert** | Deferred rendering breaks isItemActivated() | Switched to immediate scope-based API — no lambdas in InspectorRow |
| **ImGui Expert** | Widget return values lost in Runnable | Immediate mode — boolean returns work naturally |
| **ImGui Expert** | Push/pop safety with deferred execution | Immediate mode — push/pop pairs are visible at call site |
