# Custom Inspector Guide

> **Summary:** Create custom inspector UIs for your components instead of the default reflection-based editor. Use `@InspectorFor` to automatically register your inspector.

---

## Table of Contents

1. [Quick Reference](#quick-reference)
2. [Overview](#overview)
3. [Creating a Custom Inspector](#creating-a-custom-inspector)
4. [Field Editors](#field-editors)
5. [Advanced Patterns](#advanced-patterns)
6. [Tips & Best Practices](#tips--best-practices)
7. [Troubleshooting](#troubleshooting)
8. [Related](#related)

---

## Quick Reference

| Task | How |
|------|-----|
| Create custom inspector | Extend `CustomComponentInspector<T>`, add `@InspectorFor(T.class)` |
| Draw a float field | `FieldEditors.drawFloat("Label", component, "fieldName", speed)` |
| Draw a color picker | `FieldEditors.drawColor("Label", component, "fieldName")` |
| Draw an asset field | `FieldEditors.drawAsset("Label", component, "fieldName", Sprite.class, entity)` |
| Draw an enum dropdown | `EnumEditor.drawEnum("Label", component, "fieldName", MyEnum.class)` |

---

## Overview

By default, the editor uses reflection to automatically generate inspector UI for component fields. While this works for most cases, you may want to create a custom inspector when you need:

- **Conditional fields** - Show/hide fields based on other field values
- **Custom layout** - Group related fields, add sections, or use specialized widgets
- **Validation** - Add warnings, constraints, or computed displays
- **Specialized editors** - Custom pickers, preset buttons, or visual editors

Custom inspectors are automatically discovered at startup via the `@InspectorFor` annotation. You can define them in any package under `com.pocket.rpg`.

---

## Creating a Custom Inspector

### Step 1: Create the Class

Create a class that extends `CustomComponentInspector<T>` where `T` is your component type:

```java
package com.pocket.rpg.editor.ui.inspectors;

import com.pocket.rpg.components.MyComponent;
import com.pocket.rpg.editor.ui.fields.FieldEditors;

@InspectorFor(MyComponent.class)
public class MyComponentInspector extends CustomComponentInspector<MyComponent> {

    @Override
    public boolean draw() {
        boolean changed = false;

        // Draw your custom UI here
        // 'component' is already typed as MyComponent
        // 'entity' is the EditorGameObject owning this component

        changed |= FieldEditors.drawFloat("Speed", component, "speed", 0.1f);
        changed |= FieldEditors.drawBoolean("Enabled", component, "enabled");

        return changed;
    }
}
```

### Step 2: Requirements

Your inspector class must:

1. **Extend `CustomComponentInspector<T>`** - Where T is your component class
2. **Add `@InspectorFor(T.class)`** - For automatic registration
3. **Have a no-arg constructor** - Required for reflection-based instantiation
4. **Override `draw()`** - Return `true` if any field changed

### Step 3: Available Context

Inside `draw()`, you have access to:

| Field/Method | Type | Description |
|--------------|------|-------------|
| `component` | `T` | The component being edited (already cast) |
| `entity` | `HierarchyItem` | The entity owning this component (always non-null) |
| `editorEntity()` | `EditorGameObject` or `null` | Returns entity as EditorGameObject, or null in play mode |

**Play Mode Support:** Custom inspectors work in both editor and play mode. Use `entity` for scene graph queries (`getComponent()`, `getHierarchyParent()`, `getHierarchyChildren()`). Use `editorEntity()` for editor-only operations (undo commands, position access, prefab overrides).

---

## Field Editors

The `FieldEditors` class provides pre-built editors with undo support and consistent styling.

### Primitive Types

```java
// Boolean checkbox
changed |= FieldEditors.drawBoolean("Enabled", component, "enabled");

// Integer field
changed |= FieldEditors.drawInt("Count", component, "count");

// Float field with drag speed
changed |= FieldEditors.drawFloat("Speed", component, "speed", 0.1f);

// Float field with range
changed |= FieldEditors.drawFloat("Speed", component, "speed", 0.1f, 0f, 100f);

// Float slider
changed |= PrimitiveEditors.drawFloatSlider("Amount", component, "amount", 0f, 1f);

// String field
changed |= FieldEditors.drawString("Name", component, "name");
```

### Vector Types

```java
// Vector2f (X, Y)
changed |= FieldEditors.drawVector2f("Offset", component, "offset", 1.0f);

// Vector3f (X, Y, Z)
changed |= FieldEditors.drawVector3f("Position", component, "position", 1.0f);

// Color picker (Vector4f as RGBA)
changed |= FieldEditors.drawColor("Tint", component, "color");
```

### Enum Types

```java
// Enum dropdown
changed |= EnumEditor.drawEnum("Direction", component, "direction", Direction.class);
```

### Asset References

```java
// Sprite picker with drag-drop support
changed |= FieldEditors.drawAsset("Sprite", component, "sprite", Sprite.class, entity);

// Animation picker
changed |= FieldEditors.drawAsset("Animation", component, "animation", Animation.class, entity);

// Any asset type
changed |= FieldEditors.drawAsset("Font", component, "font", Font.class, entity);
```

### Enum Set (Flags-Style Checkboxes)

Renders all values of an enum as inline checkboxes, backed by a `List<E>` field:

```java
// Reflection-based (undo via ListItemCommand)
changed |= FieldEditors.drawEnumSet("Interact From", component, "interactFrom",
        Direction.class, editorEntity());
```

### String Combo (Dynamic List)

Dropdown for selecting from a runtime-populated list of strings:

```java
// Basic
changed |= FieldEditors.drawStringCombo("Camera Bounds", "key",
        component::getCameraBoundsId, component::setCameraBoundsId, boundsIds);

// Nullable (adds "None" option to clear the field)
changed |= FieldEditors.drawStringCombo("Camera Bounds", "key",
        component::getCameraBoundsId, component::setCameraBoundsId, boundsIds, true);
```

### Map Fields

`Map<String, V>` fields are rendered automatically by the reflection editor when `@FieldMeta` provides key/value types. Supports String, int, float, double, boolean values with undo.

### Transform Fields (for entities)

```java
// Position with XYZ coloring and reset button
changed |= FieldEditors.drawPosition("Position", entity);

// Scale with XYZ coloring and reset button
changed |= FieldEditors.drawScale("Scale", entity);

// Rotation with reset button
changed |= FieldEditors.drawRotation("Rotation", entity);
```

---

## Advanced Patterns

### Conditional Fields

Show/hide fields based on component state:

```java
@Override
public boolean draw() {
    boolean changed = false;

    // Always show mode selector
    changed |= EnumEditor.drawEnum("Mode", component, "mode", Mode.class);

    // Conditional fields based on mode
    if (component.getMode() == Mode.CUSTOM) {
        ImGui.indent();
        changed |= FieldEditors.drawFloat("Custom Value", component, "customValue", 0.1f);
        ImGui.unindent();
    }

    return changed;
}
```

### Sections with Headers

Organize fields into logical groups:

```java
@Override
public boolean draw() {
    boolean changed = false;

    // Section: Appearance
    ImGui.text(MaterialIcons.Palette + " Appearance");
    ImGui.separator();
    ImGui.spacing();

    changed |= FieldEditors.drawAsset("Sprite", component, "sprite", Sprite.class, entity);
    changed |= FieldEditors.drawColor("Color", component, "color");

    // Section: Behavior
    ImGui.spacing();
    ImGui.spacing();
    ImGui.text(MaterialIcons.Settings + " Behavior");
    ImGui.separator();
    ImGui.spacing();

    changed |= FieldEditors.drawFloat("Speed", component, "speed", 0.1f);
    changed |= FieldEditors.drawBoolean("Loop", component, "loop");

    return changed;
}
```

### Preset Buttons

Add quick preset options:

```java
ImGui.text("Presets:");
ImGui.sameLine();

if (ImGui.smallButton("Slow")) {
    component.setSpeed(1.0f);
    changed = true;
}
ImGui.sameLine();
if (ImGui.smallButton("Normal")) {
    component.setSpeed(5.0f);
    changed = true;
}
ImGui.sameLine();
if (ImGui.smallButton("Fast")) {
    component.setSpeed(10.0f);
    changed = true;
}
```

### Warning Messages

Display warnings or info text:

```java
// Warning when sprite is missing
if (component.getSprite() == null) {
    ImGui.spacing();
    ImGui.textColored(1.0f, 0.8f, 0.2f, 1.0f,
        MaterialIcons.Warning + " No sprite assigned");
}

// Info text
ImGui.textDisabled("Callbacks are set via code at runtime.");
```

### Custom Undo Support

For changes not handled by FieldEditors, use `SetterUndoCommand`:

```java
// Simple setter-based undo
if (ImGui.selectable(option, isSelected)) {
    String oldValue = component.getValue();
    component.setValue(option);
    UndoManager.getInstance().push(new SetterUndoCommand<>(
        component::setValue, oldValue, option, "Change Value"
    ));
}
```

For compound widgets like `dragInt2` that need activation/deactivation tracking:

```java
private static final Map<String, Object> undoStartValues = new HashMap<>();

// In draw method:
if (ImGui.isItemActivated()) {
    undoStartValues.put(key, new int[]{startX, startY});
}
// ... apply live changes ...
if (ImGui.isItemDeactivatedAfterEdit() && undoStartValues.containsKey(key)) {
    int[] old = (int[]) undoStartValues.remove(key);
    UndoManager.getInstance().push(new SetterUndoCommand<>(
        v -> { component.setX(v[0]); component.setY(v[1]); },
        old, new int[]{vals[0], vals[1]}, "Change Offset"
    ));
}
```

For list/map mutations on component fields, use `ListItemCommand` and `MapItemCommand`:

```java
// Add to list
UndoManager.getInstance().execute(new ListItemCommand(
    component, "fieldName", ListItemCommand.Operation.ADD,
    list.size(), null, newItem, editorEntity()
));

// Put to map
UndoManager.getInstance().execute(new MapItemCommand(
    component, "fieldName", MapItemCommand.Operation.PUT,
    key, oldValue, newValue, editorEntity()
));
```

### Play Mode Compatibility

Custom inspectors run in both editor and play mode. In play mode, `editorEntity()` returns `null` — changes are temporary and won't have undo support.

```java
@Override
public boolean draw() {
    boolean changed = false;

    // Scene graph queries work in both modes
    HierarchyItem parent = entity.getHierarchyParent();
    SpriteRenderer sr = entity.getComponent(SpriteRenderer.class);

    // Always draw fields (changes work in both modes)
    changed |= FieldEditors.drawFloat("Speed", component, "speed", 0.1f);

    // Guard editor-only operations
    if (editorEntity() != null) {
        // Position access (EditorGameObject-specific)
        Vector3f pos = editorEntity().getPosition();

        // Undo commands
        UndoManager.getInstance().push(new SetComponentFieldCommand(..., editorEntity()));
    }

    return changed;
}
```

---

## Tips & Best Practices

- **Return `changed` correctly** - Always `|=` when calling field editors so changes propagate
- **Use `FieldEditors` for undo support** - Manual field changes won't have undo unless you add it
- **Group related fields** - Use sections, indentation, and spacing for clarity
- **Add tooltips** - Use `ImGui.isItemHovered()` and `ImGui.setTooltip()` for help text
- **Use icons** - Import `MaterialIcons` for consistent iconography
- **Keep it focused** - Only show fields relevant to the component's purpose

---

## Troubleshooting

| Problem | Solution |
|---------|----------|
| Inspector not appearing | Ensure `@InspectorFor` annotation is present and class has no-arg constructor |
| Changes not persisting | Make sure `draw()` returns `true` when fields change |
| No undo support | Use `FieldEditors` methods or manually push `UndoManager` commands |
| Field not updating | Check that field name matches exactly (case-sensitive) |
| Icon not showing | Import `com.pocket.rpg.editor.core.MaterialIcons` |
| NPE in play mode | Use `editorEntity()` instead of `entity` for undo/position operations |
| Undo command type error | Pass `editorEntity()` (not `entity`) to `SetComponentFieldCommand` |

---

## Related

- [Inspector Panel Guide](inspector-panel-guide.md) - How the inspector panel works
- [Components Guide](components-guide.md) - Creating custom components
