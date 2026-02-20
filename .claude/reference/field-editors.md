# Inspector Field Editors

When implementing new inspector UI or field types, **prioritize using and extending existing field editors**.

## Available Editors

| File | Purpose |
|------|---------|
| `editor/ui/fields/FieldEditors.java` | **Facade** - Entry point for all field editors |
| `editor/ui/fields/PrimitiveEditors.java` | int, float, boolean, String with undo support |
| `editor/ui/fields/VectorEditors.java` | Vector2f, Vector3f, Vector4f, color pickers |
| `editor/ui/fields/EnumEditor.java` | Enum dropdown fields |
| `editor/ui/fields/AssetEditor.java` | Asset reference fields with picker integration |
| `editor/ui/fields/ListEditor.java` | List&lt;T&gt; fields (String, primitives, enums, assets) |
| `editor/ui/fields/EnumSetEditor.java` | Enum flags-style checkboxes backed by `List<E>` |
| `editor/ui/fields/StringComboEditor.java` | String combo from dynamic list (scene names, spawn IDs, etc.) |
| `editor/ui/fields/MapEditor.java` | `Map<String, V>` fields (String, int, float, double, boolean values) |
| `editor/ui/fields/TransformEditors.java` | Position/Rotation/Scale with axis coloring |
| `editor/ui/fields/ReflectionFieldEditor.java` | Auto-discovers and renders component fields |
| `editor/ui/fields/FieldEditorContext.java` | Override detection, required field highlighting |
| `editor/ui/fields/FieldEditorUtils.java` | Layout helpers, shared utilities, `accentButton()`, `inlineField()` |
| `editor/ui/fields/FieldUndoTracker.java` | Centralized undo tracking — replaces per-class boilerplate |

**Deleted (zero consumers after UITransformInspector refactor):**
- `editor/ui/layout/EditorLayout.java` — layout stack management
- `editor/ui/layout/EditorFields.java` — layout-aware field widgets
- `editor/ui/layout/LayoutContext.java` — internal to EditorLayout

## Before Creating Custom ImGui Field Rendering

1. Check if `FieldEditors` facade already supports the type
2. If not, add support to the appropriate specialized editor class
3. Use `FieldEditorUtils.inspectorRow()` for consistent layout
4. Use `FieldUndoTracker.track()` or `trackReflection()` for undo — avoids manual capture/push boilerplate
5. Support both reflection-based and getter/setter variants

## Undo Pattern for Fields

**Preferred — use FieldUndoTracker** (call immediately after the ImGui widget):
```java
// Setter-based undo (no reflection)
FieldUndoTracker.track(
    FieldUndoTracker.undoKey(component, "fieldName"),
    currentValue, setter, "Description"
);

// Reflection-based undo (syncs prefab overrides)
FieldUndoTracker.trackReflection(
    FieldUndoTracker.undoKey(component, "fieldName"),
    currentValue, component, "fieldName", entity
);
```

**Manual pattern** (for complex multi-field changes like CompoundCommand):
```java
// On widget activation (e.g., drag start, input focus)
UndoManager.getInstance().capture();

// On widget deactivation (e.g., drag end, input blur)
UndoManager.getInstance().push(command);
```

**SetterUndoCommand** (for custom combo/dropdown selections without reflection):
```java
if (ImGui.selectable(option, isSelected)) {
    String oldValue = component.getValue();
    component.setValue(option);
    UndoManager.getInstance().push(new SetterUndoCommand<>(
        component::setValue, oldValue, option, "Change Value"
    ));
}
```

**dragInt2/dragFloat2** (compound 2-value widget with undo):
```java
// Int pair (offset, size, etc.)
FieldEditors.drawDragInt2("Offset", "door.offset." + id,
    component::getOffsetX, component::getOffsetY,
    v -> { component.setOffsetX(v[0]); component.setOffsetY(v[1]); },
    0.1f);

// Int pair with min/max
FieldEditors.drawDragInt2("Size", "door.size." + id,
    component::getWidth, component::getHeight,
    v -> { component.setWidth(v[0]); component.setHeight(v[1]); },
    0.1f, 1, 10);

// Float pair
FieldEditors.drawDragFloat2("Offset", "effect.offset." + id,
    component::getOffsetX, component::getOffsetY,
    v -> { component.setOffsetX(v[0]); component.setOffsetY(v[1]); },
    0.01f);
```

## New Editor Types

### EnumSetEditor — Enum flags checkboxes

Renders all values of an enum as inline checkboxes, backed by a `List<E>` field. Two variants:

```java
// Reflection-based (uses ListItemCommand for undo)
FieldEditors.drawEnumSet("Interact From", component, "interactFrom", Direction.class, entity);

// Getter/setter-based (uses SetterUndoCommand with list snapshots)
EnumSetEditor.draw("Interact From", "key", getter, setter, Direction.class);
```

### StringComboEditor — Dynamic string combo

Dropdown for selecting a string from a runtime-populated list. Undo via `SetterUndoCommand`.

```java
// Basic
FieldEditors.drawStringCombo("Camera Bounds", "key", getter, setter, boundsIds);

// Nullable (adds "None" option at top)
FieldEditors.drawStringCombo("Camera Bounds", "key", getter, setter, boundsIds, true);
```

### MapEditor — Map field editor

Edits `Map<String, V>` fields with add/remove/edit. Values: String, int, float, double, boolean. Undo via `MapItemCommand`.

```java
// Used by ReflectionFieldEditor automatically for @FieldMeta(keyType=..., valueType=...)
// Or manually:
MapEditor.drawMap("Variables", component, meta, entity);
```

## Undo Commands

| Command | Use case |
|---------|----------|
| `SetComponentFieldCommand` | Reflection-based single field change (syncs prefab overrides) |
| `SetterUndoCommand<T>` | Setter-based change — `Consumer<T>` + old/new values |
| `ListItemCommand` | ADD/REMOVE/SET on component `List` fields via reflection |
| `MapItemCommand` | PUT/REMOVE on component `Map` fields via reflection |
| `CompoundCommand` | Batch multiple commands into one undo entry |

## ImGui NextItemData Pitfall

`ImGui.setNextItemWidth()` is consumed by the next `ItemAdd()` call — which includes `text()`. When using `inlineField()` patterns (label text followed by input widget), set the width **after** the text label:

```java
// WRONG: width consumed by text()
ImGui.setNextItemWidth(width);
ImGui.text("X");
ImGui.sameLine();
ImGui.dragFloat("##x", buf);  // gets default width

// CORRECT: use inlineField with fieldWidth
FieldEditorUtils.inlineField("X", width, () -> ImGui.dragFloat("##x", buf));
```
