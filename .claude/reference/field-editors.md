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
