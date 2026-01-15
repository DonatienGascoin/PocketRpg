# Phase 1: EditorLayout System for UITransformInspector

## Problem

X/Y fields in UITransformInspector overflow container width. The current inline implementation doesn't properly account for label widths when calculating field widths.

## Solution

Create a Unity-style layout system with horizontal/vertical layout groups and layout-aware field widgets.

---

## Files to Create

### New Package: `com.pocket.rpg.editor.ui.layout`

1. `LayoutContext.java` - Layout state tracking
2. `EditorLayout.java` - Static layout management
3. `EditorFields.java` - Layout-aware field widgets

### Modified Files

4. `UITransformInspector.java` - Use new layout system

---

## How Unity Does It

```csharp
EditorGUILayout.BeginHorizontal();
EditorGUILayout.LabelField("Position");
x = EditorGUILayout.FloatField("X", x, GUILayout.Width(60));
y = EditorGUILayout.FloatField("Y", y, GUILayout.Width(60));
EditorGUILayout.EndHorizontal();
```

Key behaviors:
- `GUILayout.ExpandWidth(true)` - Field expands to fill remaining space
- `GUILayout.Width(60)` - Fixed width field
- Fields without explicit width share remaining space equally

**Our approach:** `beginHorizontal(widgetCount)` where `widgetCount` is the number of **widgets** (input fields), not labels.

---

## Implementation

### 1. LayoutContext.java

```java
package com.pocket.rpg.editor.ui.layout;

import imgui.ImGui;
import lombok.Getter;
import lombok.Setter;

public class LayoutContext {

    public enum Direction { HORIZONTAL, VERTICAL }

    @Getter
    private final Direction direction;

    @Getter
    private final float startX;

    @Getter
    private final float availableWidth;

    @Getter
    private int widgetCount = 0;

    @Getter @Setter
    private int totalWidgets = -1;

    public LayoutContext(Direction direction, float startX, float availableWidth) {
        this.direction = direction;
        this.startX = startX;
        this.availableWidth = availableWidth;
    }

    public float getRemainingWidth() {
        return availableWidth - (ImGui.getCursorPosX() - startX);
    }

    public void registerWidget() {
        widgetCount++;
    }

    public boolean isHorizontal() {
        return direction == Direction.HORIZONTAL;
    }

    public boolean isVertical() {
        return direction == Direction.VERTICAL;
    }
}
```

### 2. EditorLayout.java

```java
package com.pocket.rpg.editor.ui.layout;

import imgui.ImGui;
import java.util.ArrayDeque;
import java.util.Deque;

public class EditorLayout {

    private static final Deque<LayoutContext> layoutStack = new ArrayDeque<>();
    private static final float LABEL_PADDING = 4f;
    private static final float WIDGET_SPACING = 8f;

    public static void beginHorizontal() {
        float availableWidth = ImGui.getContentRegionAvailX();
        float startX = ImGui.getCursorPosX();
        layoutStack.push(new LayoutContext(LayoutContext.Direction.HORIZONTAL, startX, availableWidth));
    }

    public static void beginHorizontal(int widgetCount) {
        float availableWidth = ImGui.getContentRegionAvailX();
        float startX = ImGui.getCursorPosX();
        LayoutContext ctx = new LayoutContext(LayoutContext.Direction.HORIZONTAL, startX, availableWidth);
        ctx.setTotalWidgets(widgetCount);
        layoutStack.push(ctx);
    }

    public static void endHorizontal() {
        if (!layoutStack.isEmpty()) {
            layoutStack.pop();
        }
    }

    public static boolean isHorizontal() {
        LayoutContext ctx = layoutStack.peek();
        return ctx != null && ctx.isHorizontal();
    }

    public static float calculateWidgetWidth(float labelWidth) {
        LayoutContext ctx = layoutStack.peek();
        if (ctx == null || ctx.isVertical()) {
            return -1;
        }

        if (ctx.getTotalWidgets() > 0) {
            float estimatedLabelWidth = labelWidth * ctx.getTotalWidgets();
            float spacing = WIDGET_SPACING * (ctx.getTotalWidgets() - 1);
            float labelPadding = LABEL_PADDING * ctx.getTotalWidgets();
            return (ctx.getAvailableWidth() - estimatedLabelWidth - spacing - labelPadding) / ctx.getTotalWidgets();
        } else {
            return (ctx.getRemainingWidth() - labelWidth - LABEL_PADDING - WIDGET_SPACING) / 2;
        }
    }

    public static void beforeWidget() {
        LayoutContext ctx = layoutStack.peek();
        if (ctx != null && ctx.isHorizontal() && ctx.getWidgetCount() > 0) {
            ImGui.sameLine();
        }
        if (ctx != null) {
            ctx.registerWidget();
        }
    }
}
```

### 3. EditorFields.java

```java
package com.pocket.rpg.editor.ui.layout;

import com.pocket.rpg.editor.undo.UndoManager;
import com.pocket.rpg.editor.undo.commands.SetterUndoCommand;
import imgui.ImGui;
import org.joml.Vector2f;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

public class EditorFields {

    private static final float LABEL_WIDTH = 120f;
    private static final float[] floatBuf = new float[1];
    private static final Map<String, Object> undoStartValues = new HashMap<>();

    public static boolean floatField(String label, String undoKey,
                                      DoubleSupplier getter, DoubleConsumer setter,
                                      float speed) {
        EditorLayout.beforeWidget();

        ImGui.text(label);

        if (EditorLayout.isHorizontal()) {
            ImGui.sameLine();
            float labelWidth = ImGui.calcTextSize(label).x;
            ImGui.setNextItemWidth(EditorLayout.calculateWidgetWidth(labelWidth));
        } else {
            ImGui.sameLine(LABEL_WIDTH);
            ImGui.setNextItemWidth(-1);
        }

        floatBuf[0] = (float) getter.getAsDouble();
        boolean changed = ImGui.dragFloat("##" + undoKey, floatBuf, speed);

        if (ImGui.isItemActivated()) {
            undoStartValues.put(undoKey, floatBuf[0]);
        }
        if (ImGui.isItemDeactivatedAfterEdit() && undoStartValues.containsKey(undoKey)) {
            float startValue = (Float) undoStartValues.remove(undoKey);
            if (startValue != floatBuf[0]) {
                UndoManager.getInstance().push(
                    new SetterUndoCommand<>(v -> setter.accept(v), startValue, floatBuf[0], "Change " + label)
                );
            }
        }

        if (changed) {
            setter.accept(floatBuf[0]);
        }

        return changed;
    }

    public static boolean floatField(String label, String undoKey,
                                      DoubleSupplier getter, DoubleConsumer setter,
                                      float speed, float min, float max, String format) {
        EditorLayout.beforeWidget();

        ImGui.text(label);

        if (EditorLayout.isHorizontal()) {
            ImGui.sameLine();
            float labelWidth = ImGui.calcTextSize(label).x;
            ImGui.setNextItemWidth(EditorLayout.calculateWidgetWidth(labelWidth));
        } else {
            ImGui.sameLine(LABEL_WIDTH);
            ImGui.setNextItemWidth(-1);
        }

        floatBuf[0] = (float) getter.getAsDouble();
        boolean changed = ImGui.dragFloat("##" + undoKey, floatBuf, speed, min, max, format);

        if (ImGui.isItemActivated()) {
            undoStartValues.put(undoKey, floatBuf[0]);
        }
        if (ImGui.isItemDeactivatedAfterEdit() && undoStartValues.containsKey(undoKey)) {
            float startValue = (Float) undoStartValues.remove(undoKey);
            if (startValue != floatBuf[0]) {
                UndoManager.getInstance().push(
                    new SetterUndoCommand<>(v -> setter.accept(v), startValue, floatBuf[0], "Change " + label)
                );
            }
        }

        if (changed) {
            setter.accept(floatBuf[0]);
        }

        return changed;
    }

    public static boolean vector2Field(String undoKey,
                                        Supplier<Vector2f> getter,
                                        BiConsumer<Float, Float> setter,
                                        float speed) {
        EditorLayout.beginHorizontal(2);
        boolean changed = false;
        Vector2f current = getter.get();

        if (floatField("X", undoKey + ".x", () -> current.x,
                v -> setter.accept((float) v, current.y), speed)) {
            changed = true;
        }
        if (floatField("Y", undoKey + ".y", () -> current.y,
                v -> setter.accept(current.x, (float) v), speed)) {
            changed = true;
        }

        EditorLayout.endHorizontal();
        return changed;
    }
}
```

### 4. UITransformInspector Usage

```java
// === OFFSET SECTION - All on one line ===
EditorLayout.beginHorizontal(2);

ImGui.text(FontAwesomeIcons.ArrowsAlt + " Offset");
ImGui.sameLine();
if (ImGui.smallButton("Reset##offset")) {
    t.setOffset(0, 0);
}
ImGui.sameLine();

changed |= EditorFields.floatField("X", "uiTransform.offset.x",
    () -> t.getOffset().x,
    v -> t.setOffset((float)v, t.getOffset().y), 1f);
changed |= EditorFields.floatField("Y", "uiTransform.offset.y",
    () -> t.getOffset().y,
    v -> t.setOffset(t.getOffset().x, (float)v), 1f);

EditorLayout.endHorizontal();

// === SCALE SECTION - All on one line ===
EditorLayout.beginHorizontal(2);

ImGui.text(FontAwesomeIcons.Expand + " Scale");
ImGui.sameLine();
drawMatchParentToggle("scale", ...);
ImGui.sameLine();
if (ImGui.smallButton("R##scale")) {
    t.setScale2D(1, 1);
}
ImGui.sameLine();

changed |= EditorFields.floatField("X", "uiTransform.scale.x",
    () -> t.getLocalScale2D().x,
    v -> t.setScale2D((float)v, t.getLocalScale2D().y), 0.01f);
changed |= EditorFields.floatField("Y", "uiTransform.scale.y",
    () -> t.getLocalScale2D().y,
    v -> t.setScale2D(t.getLocalScale2D().x, (float)v), 0.01f);

EditorLayout.endHorizontal();
```

**Visual Result:**
```
Offset [Reset] X [  0.00  ] Y [  0.00  ]
Scale  [M] [R] X [  1.00  ] Y [  1.00  ]
```

---

## Status: Fully Implemented ✓

Files created:
- `src/main/java/com/pocket/rpg/editor/ui/layout/LayoutContext.java`
- `src/main/java/com/pocket/rpg/editor/ui/layout/EditorLayout.java`
- `src/main/java/com/pocket/rpg/editor/ui/layout/EditorFields.java`

Changes made:
- EditorLayout now includes `RIGHT_MARGIN = 8f` for consistent right edge alignment
- UITransformInspector updated:
  - Offset X/Y fields use EditorLayout
  - Size W/H fields use EditorLayout with labels on left, equal widths
  - Scale X/Y fields use EditorLayout
  - Match parent behavior now consistent: all sections show disabled text with parent value
  - Added `getParentUITransform()` helper method

---

## Verification

1. Run: `mvn exec:java -Dexec.mainClass="com.pocket.rpg.editor.EditorApplication"`
2. Open UITransformInspector for a UI element
3. Verify fields fit on one line without overflow
4. Resize inspector panel - fields should adjust width proportionally
5. Test undo/redo on X/Y fields
6. Verify all fields end at same X position with consistent right margin
7. Enable "Match Parent" and verify disabled text shows parent value
