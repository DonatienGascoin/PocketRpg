# Feature: Entities Support

## Overview

Extend the editor to support creating entities from scratch (not just from prefabs), with automatic component discovery and reflection-based property editing. Migrate from Java-based `Prefab` interface to JSON-based prefab files.

## Goals

- Create entities without predefined prefabs
- Add/remove components dynamically via "Add Component" UI
- Edit component fields using reflection (no manual PropertyDefinition)
- Save entities as reusable JSON prefabs
- Load JSON prefabs in editor and at runtime

## Non-Goals

- Visual scripting or behavior graphs
- Component dependencies/requirements validation (future)
- Undo/redo system (separate feature)

---

## Current State

| Aspect | Current | Target |
|--------|---------|--------|
| Entity creation | Prefab-based only (`EditorEntity` holds `prefabId`) | Scratch + Prefab |
| Prefab definition | Java `Prefab` interface | JSON files |
| Property editing | `PropertyDefinition` list from Prefab | Reflection on Component fields |
| Component discovery | Manual registration | Classpath scanning |

### Current EditorEntity

```java
public class EditorEntity {
    private String prefabId;           // Required - links to Prefab
    private Vector3f position;
    private Map<String, Object> properties;  // Overrides only
}
```

### Current Prefab Interface

```java
public interface Prefab {
    String getId();
    GameObject instantiate(Vector3f position, Map<String, Object> overrides);
    List<PropertyDefinition> getEditableProperties();
}
```

---

## Proposed Architecture

### New EditorEntity Structure

```java
public class EditorEntity {
    private String id;                          // Unique instance ID
    private String name;                        // Display name
    private Vector3f position;
    
    // Option A: Scratch entity (components defined inline)
    private List<ComponentData> components;     // Serialized component data
    
    // Option B: Prefab instance (reference + overrides)
    private String prefabPath;                  // e.g., "prefabs/chest.prefab.json"
    private Map<String, Object> overrides;      // Field overrides
    
    public boolean isPrefabInstance() {
        return prefabPath != null;
    }
}
```

### ComponentData (Serialized Component)

```java
public class ComponentData {
    private String type;                        // Fully qualified class name
    private Map<String, Object> fields;         // Field name → value
}
```

### JSON Prefab Format

```json
{
    "id": "chest",
    "displayName": "Treasure Chest",
    "category": "Interactables",
    "previewSprite": "sprites/chest_closed.png",
    "components": [
        {
            "type": "com.pocket.rpg.components.SpriteRenderer",
            "fields": {
                "zIndex": 10
            }
        },
        {
            "type": "com.game.components.LootContainer",
            "fields": {
                "lootTable": "common_loot",
                "locked": false
            }
        }
    ]
}
```

---

## Key Components

### 1. ComponentRegistry

Discovers and registers all `Component` subclasses at startup.

```java
public class ComponentRegistry {
    private static Map<String, Class<? extends Component>> components;
    private static Map<String, ComponentMeta> metadata;
    
    public static void initialize() {
        // Scan classpath for Component subclasses
        // Use Reflections library or custom ClassLoader scanning
    }
    
    public static Collection<ComponentMeta> getAll();
    public static Class<? extends Component> getByName(String className);
    public static Component instantiate(String className);
}

public record ComponentMeta(
    String className,
    String displayName,      // Derived from class name or @DisplayName
    String category,         // From @ComponentCategory or package
    List<FieldMeta> fields
) {}
```

### 2. ReflectionFieldEditor

Renders ImGui controls based on field type (your existing `drawFields` approach).

```java
public class ReflectionFieldEditor {
    
    public static boolean drawComponent(Component component) {
        boolean changed = false;
        
        for (Field field : getEditableFields(component.getClass())) {
            changed |= drawField(component, field);
        }
        
        return changed;
    }
    
    private static List<Field> getEditableFields(Class<?> clazz) {
        // Traverse class hierarchy
        // Filter: !transient, !static, !@HideInInspector
    }
    
    private static boolean drawField(Object obj, Field field) {
        // Switch on field.getType()
        // Handle: int, float, boolean, String, Vector2f, Vector3f, Vector4f, Enum, Asset refs
    }
}
```

### 3. JsonPrefabRegistry

Replaces current `PrefabRegistry`, loads JSON prefabs from disk.

```java
public class JsonPrefabRegistry {
    private Map<String, JsonPrefab> prefabs;
    
    public void scanAndLoad(String prefabDirectory);
    public JsonPrefab get(String id);
    public void save(JsonPrefab prefab, String path);
    public Collection<JsonPrefab> getAll();
}

public class JsonPrefab {
    private String id;
    private String displayName;
    private String category;
    private String previewSpritePath;
    private List<ComponentData> components;
    
    public GameObject instantiate(Vector3f position);
    public EditorEntity createEditorInstance(Vector3f position);
}
```

### 4. Updated InspectorPanel

```java
// In renderEntityInspector():

// 1. Basic properties (name, position)
renderBasicProperties(entity);

// 2. Prefab info (if prefab instance)
if (entity.isPrefabInstance()) {
    ImGui.labelText("Prefab", entity.getPrefabPath());
    if (ImGui.button("Unpack Prefab")) {
        entity.unpackPrefab();  // Convert to scratch entity
    }
}

// 3. Components list
for (ComponentData comp : entity.getComponents()) {
    if (ImGui.collapsingHeader(comp.getDisplayName())) {
        ReflectionFieldEditor.drawComponentData(comp);
    }
}

// 4. Add Component button
if (ImGui.button("Add Component")) {
    openComponentBrowser();
}
```

### 5. ComponentBrowserPopup

Modal popup for selecting components to add.

```java
public class ComponentBrowserPopup {
    private String searchFilter = "";
    private String selectedCategory = "All";
    
    public void render() {
        // Search bar
        // Category tabs or dropdown
        // Scrollable list of components (grouped)
        // Click to add
    }
}
```

---

## Data Structures

### Scene Serialization Changes

```java
public class SceneData {
    // Existing
    private String name;
    private List<LayerData> layers;
    private CollisionMapData collision;
    private CameraSettingsData camera;
    
    // Updated
    private List<EntityData> entities;  // Changed from EditorEntity references
}

public class EntityData {
    private String id;
    private String name;
    private float[] position;           // [x, y, z]
    
    // Scratch entity
    private List<ComponentData> components;
    
    // OR Prefab instance
    private String prefabPath;
    private Map<String, Object> overrides;
}
```

---

## Implementation Phases

### Phase 1: Component Infrastructure
1. Create `ComponentRegistry` with classpath scanning
2. Create `ComponentMeta` and `FieldMeta` structures
3. Implement `ReflectionFieldEditor` (port your existing code)
4. Add `@HideInInspector` annotation

### Phase 2: EditorEntity Refactor
1. Refactor `EditorEntity` to support scratch entities
2. Add `ComponentData` wrapper
3. Update `InspectorPanel` to use reflection
4. Remove dependency on `PropertyDefinition`

### Phase 3: Component Browser
1. Create `ComponentBrowserPopup`
2. Add "Add Component" button to inspector
3. Implement component removal
4. Handle component instantiation

### Phase 4: JSON Prefabs
1. Define JSON prefab schema
2. Create `JsonPrefab` and `JsonPrefabRegistry`
3. Implement prefab loading/saving
4. Update `PrefabBrowserPanel` to use JSON registry
5. Add "Save as Prefab" to entity context menu

### Phase 5: Migration & Cleanup
1. Convert existing Java prefabs to JSON
2. Remove old `Prefab` interface (or deprecate)
3. Update runtime instantiation
4. Documentation

---

## Open Questions

1. **Classpath scanning**: Use [Reflections](https://github.com/ronmamo/reflections) library, or manual scanning via `ClassLoader`? Reflections is cleaner but adds dependency.

2. **Component constructors**: Require no-arg constructors for all components? Current `SpriteRenderer(Sprite)` would need refactoring.

3. **Nested objects**: If a component has a field of type `SomeConfig`, how deep do we recurse? Suggest: one level, with expandable sections.

4. **Asset references**: How to handle `Sprite`, `Texture`, `Font` fields? Need asset picker UI with preview.

5. **Component ordering**: Does order in the list matter? Some systems assume specific ordering.

6. **Prefab overrides**: When editing a prefab instance, which fields show as "overridden" vs "default"? Visual indicator?

---

## Dependencies

- **Reflections library** (optional): `org.reflections:reflections:0.10.2`
- **Gson** (existing): For JSON serialization
- **ImGui** (existing): For UI

---

## Risk Assessment

| Risk | Mitigation |
|------|------------|
| Reflection performance | Cache Field objects, don't reflect every frame |
| Complex field types | Start with primitives, add types incrementally |
| Breaking existing prefabs | Keep Java Prefab interface temporarily, migrate gradually |
| Classpath scanning slow | Scan once at startup, cache results |
