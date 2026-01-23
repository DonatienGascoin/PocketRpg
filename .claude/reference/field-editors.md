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
| `editor/ui/fields/FieldEditorUtils.java` | Layout helpers, shared utilities |

## Before Creating Custom ImGui Field Rendering

1. Check if `FieldEditors` facade already supports the type
2. If not, add support to the appropriate specialized editor class
3. Use `FieldEditorUtils.inspectorRow()` for consistent layout
4. Follow the undo pattern: capture on activation, push on deactivation
5. Support both reflection-based and getter/setter variants

## Undo Pattern for Fields

```java
// On widget activation (e.g., drag start, input focus)
UndoManager.getInstance().capture();

// On widget deactivation (e.g., drag end, input blur)
UndoManager.getInstance().push(command);
```
