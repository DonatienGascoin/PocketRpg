# Asset Deserialization Investigation

## Problem Summary

After loading a scene from disk, asset fields (Font, Sprite, Texture) in component inspector display as `"(unnamed)"` instead of their proper path names. The assets ARE loaded and work at runtime, but the editor's `FieldEditors.drawAsset()` receives **String paths** instead of **resolved asset objects**.

---

## Symptoms

1. **Font field in UIText**: Shows `"(unnamed)"` after scene reload
2. **Sprite field in SpriteRenderer**: Shows `"(unnamed)"` after scene reload
3. **Debug output confirms the issue**:
```
[DEBUG] Registered: Font path=fonts/zelda30.font.json, identity=1954406292
[DEBUG] getAssetDisplayName: String -> path=null, identity=131635550
```

The Font IS loaded and registered, but `getAssetDisplayName()` receives a String, not a Font object.

---

## Scene JSON Structure

```json
{
  "type": "com.pocket.rpg.components.ui.UIText",
  "fields": {
    "font": "fonts/zelda30.font.json",   // <-- String path
    "text": "New Text"
  }
}
```

```json
{
  "type": "com.pocket.rpg.components.SpriteRenderer",
  "fields": {
    "sprite": "sheet_spacing.png"   // <-- String path
  }
}
```

Assets are serialized as **String paths** (correct), but during deserialization they stay as Strings instead of being resolved to actual asset objects.

---

## Architecture Overview

### Serialization Chain (Write)
```
Component → ComponentTypeAdapterFactory.write()
         → For non-TilemapRenderer: delegates to Gson
         → AssetReferenceTypeAdapterFactory.write()
         → Checks assetContext.getPathForResource(value)
         → If tracked: writes String path
         → If not tracked: delegates to default serialization
```

### Deserialization Chain (Read)
```
JSON → ComponentTypeAdapterFactory.read()
    → Parses "type" and "properties" fields
    → For non-TilemapRenderer: gson.fromJson(properties, componentClass)
    → Gson deserializes into component fields
    → AssetReferenceTypeAdapterFactory.read() should intercept asset types
```

### Editor Display Chain
```
EditorEntity → ComponentData (wraps component)
            → ComponentData.fields: Map<String, Object>
            → Populated via reflection from component instance
            → FieldEditors.drawAsset() reads from this map
            → Calls Assets.getPathForResource(value) to get display name
```

---

## Root Cause Analysis

### The Problem Location

When `gson.fromJson(properties, componentClass)` deserializes a component:

1. Gson sees `"font": "fonts/zelda30.font.json"` 
2. Gson needs to set `UIText.font` field (type `Font`)
3. `AssetReferenceTypeAdapterFactory` SHOULD intercept this and call `assetContext.load(path, Font.class)`

**BUT** something is preventing proper resolution. Possible causes:

### Hypothesis 1: AssetReferenceTypeAdapterFactory Not Intercepting

The factory has this skip logic:
```java
// SKIP_TYPES includes many types but NOT Font
private static final Set<Class<?>> SKIP_TYPES = Set.of(
    Boolean.class, Byte.class, ..., Sprite.class  // Note: Sprite IS skipped
);
```

Font is NOT in SKIP_TYPES, so it should be intercepted. But Sprite IS skipped - this explains why SpriteRenderer sprites also fail.

### Hypothesis 2: ComponentData Reflection Issue

`ComponentData` extracts fields via reflection:
```java
// In ComponentData constructor or refresh
for (Field field : component.getClass().getDeclaredFields()) {
    fields.put(field.getName(), field.get(component));
}
```

If the component's actual field contains the resolved Font object, ComponentData should get it. If ComponentData shows a String, then the component itself has a String.

### Hypothesis 3: Order of TypeAdapterFactory Registration

```java
// In Serializer.java or wherever Gson is built
GsonBuilder builder = new GsonBuilder();
builder.registerTypeAdapterFactory(new ComponentTypeAdapterFactory(context));
builder.registerTypeAdapterFactory(new AssetReferenceTypeAdapterFactory(context));
```

**Order matters.** If ComponentTypeAdapterFactory handles the entire component before AssetReferenceTypeAdapterFactory can intercept individual fields, the asset resolution never happens.

### Hypothesis 4: Delegate Adapter Chain

In `ComponentTypeAdapterFactory.read()`:
```java
return (T) gson.fromJson(properties, (java.lang.reflect.Type) clazz);
```

This creates a NEW deserialization context. The `AssetReferenceTypeAdapterFactory` should still be in the chain, but needs investigation.

---

## Key Files

| File | Role |
|------|------|
| `ComponentTypeAdapterFactory.java` | Handles Component serialization/deserialization |
| `AssetReferenceTypeAdapterFactory.java` | Should intercept asset types and load via Assets |
| `SpriteTypeAdapter.java` | Dedicated adapter for Sprite (handles spritesheet#index) |
| `Serializer.java` | Builds Gson instance, registers adapters |
| `ComponentData.java` | Editor wrapper, extracts fields via reflection |
| `FieldEditors.java` | Renders inspector fields, calls Assets.getPathForResource() |

---

## Current Quick Fix

Added lazy-loading in `FieldEditors.drawAsset()`:
```java
// Lazy-load: if value is a String path but we expect an asset, resolve it
if (value instanceof String path && !path.isEmpty() && assetType != String.class) {
    try {
        Object loadedAsset = Assets.load(path, assetType);
        if (loadedAsset != null) {
            fields.put(key, loadedAsset);  // Update the map with resolved asset
            value = loadedAsset;
        }
    } catch (Exception e) {
        System.err.println("Failed to lazy-load asset: " + path);
    }
}
```

**This is a workaround**, not a proper fix. The proper fix should happen during deserialization.

---

## Investigation Steps

### Step 1: Verify Gson Registration Order
Check `Serializer.java` for TypeAdapterFactory registration order:
```java
// Should AssetReferenceTypeAdapterFactory be FIRST or LAST?
// Comment in AssetReferenceTypeAdapterFactory says:
// "IMPORTANT: Must be registered FIRST in Gson builder"
```

### Step 2: Add Debug Logging in AssetReferenceTypeAdapterFactory.read()
```java
@Override
public T read(JsonReader in) throws IOException {
    System.out.println("[ARTAF] read() called for type: " + rawType.getSimpleName());
    // ... rest of method
}
```

Check if it's ever called for Font or Sprite types during scene loading.

### Step 3: Check Sprite Handling
Sprite is in SKIP_TYPES:
```java
private static final Set<Class<?>> SKIP_TYPES = Set.of(
    ...,
    Sprite.class  // <-- This causes Sprite fields to be skipped!
);
```

This means AssetReferenceTypeAdapterFactory NEVER handles Sprite. There's a separate `SpriteTypeAdapter`, but is it registered?

### Step 4: Trace Full Deserialization Path
Add logging in:
1. `ComponentTypeAdapterFactory.read()` - before `gson.fromJson()`
2. `AssetReferenceTypeAdapterFactory.read()` - entry point
3. `SpriteTypeAdapter.read()` - entry point
4. `FontLoader.load()` - when actually loading

### Step 5: Check SpriteTypeAdapter Registration
Is `SpriteTypeAdapter` registered in the Gson builder?
```java
builder.registerTypeAdapter(Sprite.class, new SpriteTypeAdapter());
```

If not, Sprites will never be properly deserialized.

---

## Potential Proper Solutions

### Solution A: Fix AssetReferenceTypeAdapterFactory Skip Logic
Remove Sprite from SKIP_TYPES and let it handle Sprite OR ensure SpriteTypeAdapter is properly registered.

### Solution B: Register Asset TypeAdapters for Each Type
```java
builder.registerTypeAdapter(Font.class, new FontTypeAdapter(context));
builder.registerTypeAdapter(Sprite.class, new SpriteTypeAdapter(context));
builder.registerTypeAdapter(Texture.class, new TextureTypeAdapter(context));
```

### Solution C: Post-Deserialization Asset Resolution
After `gson.fromJson()`, walk the component's fields and resolve any String paths:
```java
Component component = gson.fromJson(properties, clazz);
resolveAssetFields(component);  // Reflection-based field resolution
return component;
```

### Solution D: Custom Deserializer per Component
Less generic but guaranteed to work - each component that has asset fields gets custom deserialization logic.

---

## Related Context

### Asset Path Format
- Direct assets: `"sprites/player.png"`
- Sub-assets (spritesheet sprites): `"sheets/player.spritesheet#3"`

### Asset Registration
Assets loaded via `Assets.load()` are registered in `AssetManager.resourcePaths`:
```java
resourcePaths.put(resource, normalizedPath);
```

`Assets.getPathForResource(object)` looks up this map. Returns null if object wasn't loaded through Assets.

### SpriteReference Utility
For sprites specifically, there's a `SpriteReference` utility:
```java
SpriteReference.toPath(sprite)   // Sprite → path string
SpriteReference.fromPath(path)   // path string → Sprite
```

---

## Test Cases

1. **New scene, add UIText, set Font, save, reload** → Font should show path name
2. **New scene, add SpriteRenderer, set Sprite from image, save, reload** → Sprite should show path name  
3. **New scene, add SpriteRenderer, set Sprite from spritesheet, save, reload** → Should show `sheet.spritesheet#N`
4. **TilemapRenderer** → Already has custom binary serialization, tiles should work (they do now)

---

## Files to Share in New Chat

1. `Serializer.java` - Gson builder and adapter registration
2. `AssetReferenceTypeAdapterFactory.java` - Generic asset handling
3. `SpriteTypeAdapter.java` - Sprite-specific handling  
4. `ComponentTypeAdapterFactory.java` - Component wrapper
5. `ComponentData.java` - How editor extracts field values
6. `UIText.java` - Example component with Font field
7. Sample scene JSON showing the serialized format
