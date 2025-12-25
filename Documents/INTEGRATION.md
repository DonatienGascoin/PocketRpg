# Phase 1 - Editor Integration Notes

## Import Updates Required

### Files that import ComponentData

Replace all occurrences of:

```java

```

With:
```java
import com.pocket.rpg.serialization.ComponentData;
```

### Files that import ComponentMeta, FieldMeta, ComponentRegistry

Replace:

```java


```

With:
```java
import com.pocket.rpg.serialization.ComponentMeta;
import com.pocket.rpg.serialization.FieldMeta;
import com.pocket.rpg.serialization.ComponentRegistry;
import com.pocket.rpg.serialization.ComponentRefMeta;  // NEW - for reference display
```

---

## InspectorPanel Update

Add this method to render component reference fields as read-only:

```java
/**
 * Renders @ComponentRef fields as read-only labels.
 * These are resolved at runtime, not editable.
 */
private void renderComponentReferences(ComponentMeta meta) {
    List<ComponentRefMeta> references = meta.references();
    if (references.isEmpty()) {
        return;
    }

    ImGui.separator();
    ImGui.textDisabled("Component References (resolved at runtime)");
    ImGui.indent();

    for (ComponentRefMeta ref : references) {
        // Show as: "Transform (self)" or "Collider[] (children)"
        String label = ref.getDisplayName();
        String value = ref.getEditorDescription();

        if (!ref.required()) {
            value += " [optional]";
        }

        // Render as disabled/read-only
        ImGui.textDisabled(label + ":");
        ImGui.sameLine();
        ImGui.text(value);
    }

    ImGui.unindent();
}
```

Call it after rendering regular fields:

```java
private void renderComponentEditor(ComponentData componentData) {
    ComponentMeta meta = ComponentRegistry.getByClassName(componentData.getType());
    if (meta == null) {
        ImGui.textDisabled("Unknown component type");
        return;
    }

    // Render editable fields
    for (FieldMeta fieldMeta : meta.fields()) {
        Object value = componentData.getFields().get(fieldMeta.name());
        Object newValue = ReflectionFieldEditor.drawField(fieldMeta, value);
        if (newValue != value) {
            componentData.getFields().put(fieldMeta.name(), newValue);
        }
    }

    // Render read-only component references
    renderComponentReferences(meta);
}
```

---

## Scene Loading Update

After loading a scene or instantiating prefabs, resolve all component references:

```java
// In SceneLoader or similar:
public void loadScene(String path) {
    SceneData data = loadSceneData(path);
    
    // First pass: Create all GameObjects and Components
    List<GameObject> allObjects = new ArrayList<>();
    for (EntityData entityData : data.getEntities()) {
        GameObject go = createGameObject(entityData);
        allObjects.add(go);
    }
    
    // Second pass: Set up parent-child relationships
    setupHierarchy(allObjects, data);
    
    // Third pass: Resolve all @ComponentRef fields
    for (GameObject go : allObjects) {
        ComponentRefResolver.resolveReferences(go);
    }
}
```

---

## Files to Delete

After migration is complete and tested:
- `com/pocket/rpg/editor/serialization/ComponentData.java`
- `com/pocket/rpg/editor/components/ComponentMeta.java`
- `com/pocket/rpg/editor/components/FieldMeta.java`
- `com/pocket/rpg/editor/components/ComponentRegistry.java`

---

## Summary of New Files

| File | Package | Purpose |
|------|---------|---------|
| `ComponentRef.java` | `com.pocket.rpg.components` | Annotation for component references |
| `ComponentData.java` | `com.pocket.rpg.serialization` | Moved from editor, unchanged API |
| `ComponentMeta.java` | `com.pocket.rpg.serialization` | Moved from editor, added references list |
| `FieldMeta.java` | `com.pocket.rpg.serialization` | Moved from editor, unchanged |
| `ComponentRefMeta.java` | `com.pocket.rpg.serialization` | NEW - metadata for @ComponentRef fields |
| `ComponentRegistry.java` | `com.pocket.rpg.serialization` | Moved from editor, handles @ComponentRef |
| `ComponentRefResolver.java` | `com.pocket.rpg.serialization` | NEW - runtime resolution of references |
