# Inspector System Comparison: Unity vs PocketRpg

## How Unity Does It

### Target Access
```csharp
// Full access to everything from a custom Editor
MyComponent comp = (MyComponent)target;
comp.gameObject;                           // direct
comp.GetComponent<OtherComponent>();       // siblings
comp.transform.parent.GetComponent<X>();   // parent hierarchy
```
The `target` is the real component instance. No restrictions, no wrappers for read access.

### Drawing Fields
Two approaches, both first-class:

**Automatic** (one line, undo included):
```csharp
EditorGUILayout.PropertyField(serializedObject.FindProperty("myFloat"));
```

**Manual** (full control, still simple):
```csharp
EditorGUILayout.BeginHorizontal();
    EditorGUILayout.PrefixLabel("Size");     // label, auto-width
    if (GUILayout.Button("px")) { ... }      // button, auto-width
    value = EditorGUILayout.FloatField(value); // field, fills remaining
EditorGUILayout.EndHorizontal();
```

Key: `PrefixLabel` handles the label column. Everything after it shares remaining space. No manual width math.

### Undo
```csharp
serializedObject.Update();
// ... draw fields that modify SerializedProperties ...
serializedObject.ApplyModifiedProperties(); // undo recorded automatically via delta
```
Undo is a **2-line wrapper** around the entire `OnInspectorGUI`. Zero per-field boilerplate.

### Layout
`BeginHorizontal/EndHorizontal` with `GUILayout.Width()` options. Nestable. Flexible space fills gaps. No manual pixel math.

---

## How PocketRpg Does It

### Target Access
```java
// CustomComponentInspector<UITransform>
this.component  // typed target (UITransform)
this.entity     // EditorGameObject wrapper

// Sibling access: verbose, returns Component base type
entity.getComponent(UITransform.class);
entity.getParent().getComponent(LayoutGroup.class);
```
`component` is the real instance (good). But `entity.getComponent()` returns `Component` requiring casts, and there's no helper for common patterns like "find in parent chain."

### Drawing Fields

**Reflection-based** (one-liner, undo included):
```java
FieldEditors.drawFloat("Speed", component, "speed", 0.1f);
```
Works for simple cases. But only for fields that exactly match a Java field name.

**Getter/setter** (needed for computed properties):
```java
FieldEditors.drawFloat("##Rotation", "uiTransform.rotation",
    t::getLocalRotation2D,
    v -> t.setRotation2D((float) v),
    0.5f, -360f, 360f, "%.1f");
```
Different signature, different undo path, different behavior than reflection variant.

**With custom buttons** (the pain zone):
```java
// Must manually manage: label text, sameLine, button, sameLine, setCursorPos, setNextItemWidth, drag
ImGui.text("Size");
ImGui.sameLine();
ImGui.smallButton("px");  // manual width accounting
ImGui.sameLine();
ImGui.setCursorPosX(FIELD_POSITION); // hardcoded constant
ImGui.setNextItemWidth(EditorLayout.calculateWidgetWidth(labelWidth)); // manual math
ImGui.dragFloat("##id", buf, ...);
// then manually track undo activation/deactivation
```

### Undo
**Per-field, manual:**
```java
// Must be done for EVERY editable field
if (ImGui.isItemActivated()) {
    undoStartValues.put(key, currentValue);    // capture start
}
if (ImGui.isItemDeactivatedAfterEdit()) {
    float start = undoStartValues.remove(key); // retrieve start
    UndoManager.getInstance().push(
        new SetterUndoCommand<>(setter, start, current, "Change X")
    );
}
```
`PrimitiveEditors` does this automatically for reflection-based fields, but getter/setter fields in custom inspectors must repeat this pattern. The `handlePercentUndo` method in UITransformInspector is a copy of this pattern.

### Layout
```java
EditorLayout.beginHorizontal(2);        // must declare widget count upfront
EditorFields.floatField("X", ...);      // works only with EditorFields, not FieldEditors
EditorFields.floatField("Y", ...);
EditorLayout.endHorizontal();
```
Width calculated by dividing available space by widget count. No flexible space. Adding a button between label and field requires falling out of this system entirely.

---

## Side-by-Side: The Same UI Element

### Unity: Width field with px/% toggle
```csharp
EditorGUILayout.BeginHorizontal();
    EditorGUILayout.PrefixLabel("W");
    if (GUILayout.Button(isPercent ? "%" : "px", GUILayout.Width(30))) {
        Undo.RecordObject(target, "Toggle Size Mode");
        ToggleMode();
    }
    if (isPercent)
        comp.widthPercent = EditorGUILayout.FloatField(comp.widthPercent);
    else
        comp.width = EditorGUILayout.FloatField(comp.width);
EditorGUILayout.EndHorizontal();
// ApplyModifiedProperties() handles undo
```

### PocketRpg: Same thing
```java
EditorLayout.beforeWidget();
ImGui.text(label);                    // "W"
ImGui.sameLine();
// Style push for accent color (3 pushes)
if (wasPercent) ImGui.pushStyleColor(ImGuiCol.Button, ...);
if (wasPercent) ImGui.pushStyleColor(ImGuiCol.ButtonHovered, ...);
if (wasPercent) ImGui.pushStyleColor(ImGuiCol.ButtonActive, ...);
if (ImGui.smallButton(modeLabel + "##" + id)) {
    // Manual: capture old state, apply change, build CompoundCommand, push to UndoManager
    toggleSizeMode(isWidth); // 40-line method
}
if (wasPercent) ImGui.popStyleColor(3);
// Tooltip
ImGui.sameLine();
float usedWidth = ImGui.calcTextSize(label).x + ImGui.calcTextSize(modeLabel).x + 16f;
ImGui.setNextItemWidth(EditorLayout.calculateWidgetWidth(usedWidth));
if (isPercent) {
    float[] buf = {percent};
    if (ImGui.dragFloat("##" + id, buf, 0.5f, 0f, 200f, "%.1f%%%%")) { // escaped %
        // Manual: apply value, handle aspect ratio, markDirty
    }
    handlePercentUndo(id, isWidth); // Manual undo tracking
} else {
    // ... different code path for fixed mode with different undo pattern (startSizeEdit/commitSizeEdit)
}
```

---

## Root Causes of Pain

### 1. No layout abstraction over ImGui
Unity's `EditorGUILayout` handles label width, field width, and remaining-space distribution automatically. PocketRpg has `inspectorRow` for simple cases but nothing for "label + N buttons + field." Custom inspectors fall back to raw ImGui calls with manual `sameLine` / `setCursorPosX` / `setNextItemWidth` chains. Each combination of widgets requires new width math.

### 2. Undo is per-widget, not per-frame
Unity records undo as a diff of the entire serialized state. One call wraps all fields. PocketRpg must track undo activation/deactivation per widget, with different patterns for reflection vs getter/setter vs drag-with-children. A custom inspector with 8 fields has 8 undo tracking blocks.

### 3. Two incompatible field APIs
`FieldEditors` (reflection) and `EditorFields` (layout-aware getter/setter) are separate systems with different undo mechanisms, different width handling, and different call conventions. A custom inspector doing anything non-trivial mixes both, plus raw ImGui.

### 4. Custom inspectors can't easily compose widgets
There's no equivalent to Unity's `PrefixLabel` (reserve label space, return field rect). There's no `GUILayout.FlexibleSpace()`. There's no "put these widgets on one line and let them share space." The result: UITransformInspector is 700+ lines of manual layout code for what Unity would do in ~150.

---

## What Would Help Most

| Problem | Unity Solution | Possible PocketRpg Equivalent |
|---------|---------------|-------------------------------|
| Width math | `PrefixLabel` + auto-fill | `inspectorRow` that accepts multiple child widgets |
| Undo boilerplate | `serializedObject.ApplyModifiedProperties()` | Frame-level snapshot diff on the component |
| Two field APIs | One `PropertyField` | Unify FieldEditors and EditorFields into one API |
| Button-in-row | `BeginHorizontal` + `GUILayout.Width` | `inspectorRow` with slot for middle content (exists but underused) |
| Component access | `target.GetComponent<T>()` | Typed `getComponent<T>()` on entity that returns T directly |
