# Entity/Component Serialization Architecture Analysis

## Overview

This document analyzes the current architecture for entity and component serialization in PocketRPG, identifies recurring pain points, and proposes solutions.

---

## Current Class Relationships

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              STORAGE (JSON)                                  │
├─────────────────────────────────────────────────────────────────────────────┤
│  SceneData                                                                   │
│  ├── List<GameObjectData>  ←── Tilemaps (legacy path)                       │
│  │   └── List<Component>   ←── ACTUAL runtime components!                   │
│  │                                                                           │
│  └── List<EntityData>      ←── Placed entities (new path)                   │
│      ├── prefabId + componentOverrides  (prefab instances)                  │
│      └── List<ComponentData>            (scratch entities)                  │
│          └── Map<String, Object> fields ←── Raw JSON types (String, Number) │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                              EDITOR                                          │
├─────────────────────────────────────────────────────────────────────────────┤
│  EditorScene                                                                 │
│  ├── List<TilemapLayer>    ←── Wraps GameObject with TilemapRenderer        │
│  │                                                                           │
│  └── List<EditorEntity>    ←── Editor representation                        │
│      ├── List<ComponentData>           (scratch)                            │
│      │   └── fields: Map<String,Object> ←── Inspector reads THIS directly  │
│      │                                                                       │
│      └── componentOverrides: Map<...>  (prefab instance)                    │
│                                                                              │
│  Inspector (FieldEditors) reads from ComponentData.fields                   │
│  - Expects resolved assets (Font, Sprite objects)                           │
│  - Gets raw strings if not resolved                                         │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                              RUNTIME                                         │
├─────────────────────────────────────────────────────────────────────────────┤
│  RuntimeScene                                                                │
│  └── List<GameObject>                                                        │
│      └── List<Component>   ←── Actual typed component instances             │
│          └── Typed fields (Font font, Sprite sprite, etc.)                  │
│                                                                              │
│  RuntimeSceneLoader converts:                                                │
│  - EntityData → GameObject via ComponentData.toComponent()                  │
│  - GameObjectData → GameObject (already has Component instances)            │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## The Core Problem: Two Serialization Paths

### Path 1: GameObjectData (Tilemaps)
```
TilemapRenderer (Component) 
    → GameObjectData.components (List<Component>)
    → Gson + ComponentTypeAdapterFactory 
    → JSON with custom binary encoding
    → Load: Gson recreates Component directly
```

**How it works:** Actual `Component` instances are stored. Gson uses `ComponentTypeAdapterFactory` which has custom handling for `TilemapRenderer` (binary encoding). Assets are resolved during Gson deserialization.

### Path 2: EntityData/ComponentData (Entities)
```
Component fields 
    → ComponentData.fromComponent() 
    → ComponentData.fields (Map<String, Object>)
    → Gson serializes map as-is
    → JSON with field names and raw values
    → Load: Gson recreates ComponentData with raw types
    → Editor displays fields directly (PROBLEM!)
    → Runtime: ComponentData.toComponent() resolves assets
```

**How it works:** Component fields are extracted to a `Map<String, Object>`. Gson serializes this map. On load, Gson recreates the map with raw JSON types (String, Number, etc.). The editor reads directly from this map, expecting resolved assets but getting strings.

---

## Why These Classes Keep Failing

### 1. Type Information Loss
```java
// Serialization (toSerializable)
if (value instanceof Sprite sprite) {
    return SpriteReference.toPath(sprite);  // Font → "fonts/zelda.font.json"
}

// Storage in JSON
"font": "fonts/zelda.font.json"  // Just a string, no type info

// Deserialization - Gson sees Map<String, Object>
fields.get("font")  // Returns String, not Font!
```

The `Map<String, Object>` loses type information. Gson doesn't know "font" should be a `Font`, so it stays a `String`.

### 2. Resolution Timing Mismatch
```
                    EDITOR PATH                      RUNTIME PATH
                    ───────────                      ────────────
Gson Load           ComponentData.fields             ComponentData.fields
                    (raw strings)                    (raw strings)
                          │                                │
                          ▼                                ▼
                    EditorEntity.fromData()          RuntimeSceneLoader
                          │                                │
                          ▼                                ▼
                    [NO RESOLUTION]                  compData.toComponent()
                          │                                │
                          ▼                                ▼
                    Inspector reads fields           fromSerializable()
                    (sees String)                    (resolves Font)
                          │                                │
                          ▼                                ▼
                    "(unnamed)"                      Actual Font object
```

**Editor never calls `toComponent()`** - it displays `ComponentData.fields` directly.
**Runtime calls `toComponent()`** - which calls `fromSerializable()` to resolve assets.

### 3. Dual Entity Systems
Why do we have BOTH `GameObjectData` and `EntityData`?

| Aspect | GameObjectData | EntityData |
|--------|---------------|------------|
| Components | `List<Component>` (actual instances) | `List<ComponentData>` (field maps) |
| Used For | Tilemaps | Placed entities |
| Serialization | ComponentTypeAdapterFactory | Generic Gson |
| Asset Resolution | During Gson read | Manual (toComponent) |
| Prefab Support | No | Yes (componentOverrides) |

This split creates confusion and inconsistent behavior.

### 4. ComponentData Dual Role
`ComponentData` tries to be two things:
1. **Serialization container** - stores field values for JSON
2. **Editor data model** - inspector reads/writes fields directly

These roles have conflicting requirements:
- Serialization wants strings (portable, JSON-friendly)
- Editor wants resolved objects (for display names, previews)

---

## Recurring Bug Patterns

### Bug Pattern 1: "(unnamed)" Assets
**Cause:** `ComponentData.fields` contains String path, editor expects resolved asset.
**Fix Applied:** `resolveAssetReferences()` in `EditorEntity.fromData()`
**Why It Recurs:** Any new deserialization path that skips resolution.

### Bug Pattern 2: Asset Path Not Found
**Cause:** Asset not registered in `resourcePaths` map.
**Fix Applied:** `SpriteSheet.ensureSpriteRegistered()`
**Why It Recurs:** Any code path that creates assets without `Assets.load()`.

### Bug Pattern 3: Wrong Type After Deserialization
**Cause:** Generic `Map<String, Object>` loses type information.
**Example:** Vector2f becomes `Map` or `List`, Font becomes `String`.
**Why It Recurs:** Fundamental to the Map-based approach.

### Bug Pattern 4: Serialization/Deserialization Asymmetry
**Cause:** `toSerializable()` and `fromSerializable()` have different code paths.
**Example:** Sprite serializes as path, but deserialization expects specific type check.
**Why It Recurs:** No single source of truth for conversions.

---

## Architecture Options

### Option A: Current Architecture (Patched)
Keep current design, add resolution at all entry points.

```java
// Every place that creates ComponentData from JSON must resolve:
EditorEntity.fromData() → resolveAssetReferences()
Prefab.instantiate() → already calls toComponent()
RuntimeSceneLoader → already calls toComponent()
```

**Pros:**
- Minimal changes
- Already partially working

**Cons:**
- Easy to miss new entry points
- Two representations (fields map vs Component)
- Resolution scattered across codebase

### Option B: Unify on Component (Remove ComponentData)

Use actual `Component` instances everywhere, let Gson handle serialization.

```java
// EntityData stores actual components
public class EntityData {
    private List<Component> components;  // Not ComponentData
}

// Editor works with Component directly
public class EditorEntity {
    private List<Component> components;  // Not ComponentData
}
```

**Serialization:** Enhanced `ComponentTypeAdapterFactory` handles ALL components, not just TilemapRenderer.

**Pros:**
- Single representation (Component)
- Strongly typed
- No field maps, no resolution needed
- Inspector reads actual component fields via reflection

**Cons:**
- Major refactor
- Every component needs proper serialization
- Editor modifies live Component instances
- Undo/redo more complex (need to clone components)

### Option C: Smart ComponentData with Lazy Resolution

`ComponentData.fields` automatically resolves assets on access.

```java
public class ComponentData {
    private Map<String, Object> rawFields;      // From JSON
    private Map<String, Object> resolvedFields; // Lazily populated
    private boolean resolved = false;
    
    public Map<String, Object> getFields() {
        if (!resolved) {
            resolveAllFields();
        }
        return resolvedFields;
    }
    
    private void resolveAllFields() {
        ComponentMeta meta = ComponentRegistry.getByClassName(type);
        for (FieldMeta field : meta.fields()) {
            Object raw = rawFields.get(field.name());
            resolvedFields.put(field.name(), fromSerializable(raw, field.type()));
        }
        resolved = true;
    }
}
```

**Pros:**
- Minimal API changes
- Resolution happens automatically
- Single point of resolution logic

**Cons:**
- Still two representations internally
- Must invalidate cache on field changes
- Complex state management

### Option D: Typed Field Storage

Replace `Map<String, Object>` with typed field containers.

```java
public class ComponentData {
    private String type;
    private List<FieldValue> fields;
}

public sealed interface FieldValue {
    String name();
    
    record IntValue(String name, int value) implements FieldValue {}
    record FloatValue(String name, float value) implements FieldValue {}
    record StringValue(String name, String value) implements FieldValue {}
    record AssetRef(String name, String path, Class<?> assetType) implements FieldValue {}
    record VectorValue(String name, float[] components) implements FieldValue {}
}
```

**JSON:**
```json
{
  "type": "UIText",
  "fields": [
    {"_t": "string", "name": "text", "value": "Hello"},
    {"_t": "asset", "name": "font", "path": "fonts/zelda.font.json", "assetType": "Font"}
  ]
}
```

**Pros:**
- Type information preserved
- Clear asset references
- Extensible for new types

**Cons:**
- Breaking change to JSON format
- More verbose JSON
- Migration needed

### Option E: Separate Editor Model

Create dedicated editor data classes that maintain resolved state.

```java
// Editor-only, never serialized
public class EditorComponentData {
    private ComponentData serializable;  // For save/load
    private Component liveComponent;     // For display/preview
    
    public Object getFieldValue(String name) {
        return getFieldFromComponent(liveComponent, name);
    }
    
    public void setFieldValue(String name, Object value) {
        setFieldOnComponent(liveComponent, name, value);
        syncToSerializable();
    }
}
```

**Pros:**
- Clear separation of concerns
- Editor always has resolved data
- Serialization isolated

**Cons:**
- More classes
- Must keep in sync
- Memory overhead (two copies)

---

## Recommendation

### Short Term: Option A (Patched) + Option C (Lazy Resolution)

1. **Keep current architecture** but make `ComponentData.getFields()` resolve lazily
2. **Remove explicit `resolveAssetReferences()` calls** - resolution happens on first access
3. **Add validation** - log warning if raw string found where asset expected

```java
// ComponentData.java
public Map<String, Object> getFields() {
    ensureResolved();
    return fields;
}

private void ensureResolved() {
    if (needsResolution) {
        resolveAssetReferences();
        needsResolution = false;
    }
}

// Mark as needing resolution after Gson deserialization
// (via custom TypeAdapter or @JsonAdapter)
```

### Long Term: Option B (Unify on Component)

1. **Extend ComponentTypeAdapterFactory** to handle all components generically
2. **Replace ComponentData with Component** in EntityData and EditorEntity
3. **Inspector uses reflection** to read/write Component fields directly
4. **Undo/redo** clones components before modification

This eliminates the Map<String, Object> problem entirely.

---

## Migration Path

### Phase 1: Lazy Resolution (Low Risk)
- Modify `ComponentData.getFields()` to resolve lazily
- Remove explicit `resolveAssetReferences()` calls
- Test thoroughly

### Phase 2: Generic Component Serialization (Medium Risk)
- Extend `ComponentTypeAdapterFactory` for all components
- Add asset field detection via reflection
- Handle Sprite, Font, Texture automatically

### Phase 3: Unify Data Model (High Risk)
- Replace `List<ComponentData>` with `List<Component>` in EntityData
- Update EditorEntity to use Component directly
- Update Inspector to use reflection

---

## Key Insights

1. **The Map<String, Object> is the root cause** - it loses type information and requires manual resolution.

2. **GameObjectData works better** because it stores actual Component instances, letting Gson adapters handle serialization properly.

3. **The editor/runtime split is artificial** - both need resolved data, just at different times.

4. **Asset resolution should be automatic** - not a manual step that can be forgotten.

5. **Two entity systems (GameObjectData vs EntityData) should be unified** - the split creates confusion and inconsistent behavior.
