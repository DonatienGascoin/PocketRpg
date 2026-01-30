# Asset System Harmonization - Design Document

## Problem Statement

Asset naming and path resolution is fragmented across multiple places with type-specific logic:

1. **`FieldEditors.getAssetDisplayName()`** - if/else for Sprite, Texture, String
2. **`AssetDragPayload`** - carries path separately, assets created from it aren't tracked
3. **`ComponentTypeAdapterFactory.serializeSpriteRef()`** - duplicates spritesheet#index logic
4. **`SpriteTypeAdapter`** - similar duplication
5. **`AssetPickerPopup`** - type-specific scanning, no spritesheet expansion

**Symptoms:**
- Inconsistent display names between drag-drop, asset picker, and after scene reload
- Adding a new asset type requires changes in multiple places

---

## Design Decision: Single Source of Truth

**`AssetManager.resourcePaths`** (`Map<Object, String>`) is the single source of truth for asset paths.

**Key principle:** Assets don't store their own path. The map handles everything.

---

## Path Format

Standard assets:
```
sprites/player.png
fonts/arial.font.json
```

Sub-assets (spritesheet sprites):
```
sheets/player.spritesheet#3
```

The `#` separator is parsed by `AssetManager` to handle sub-asset loading.

---

## Implementation Changes

### 1. AssetLoader Interface

Add new method for sub-asset extraction:

```java
public interface AssetLoader<T> {
    // ... existing methods ...

    /**
     * Extracts a sub-asset from a parent asset.
     * Only override for assets that contain addressable sub-assets (e.g., SpriteSheet).
     *
     * @param parent  The parent asset
     * @param subId   The sub-asset identifier (e.g., "3" for sprite index)
     * @param subType The expected sub-asset type
     * @return The sub-asset
     */
    default <S> S getSubAsset(T parent, String subId, Class<S> subType) {
        throw new UnsupportedOperationException(
            "Asset type " + parent.getClass().getSimpleName() + " does not support sub-assets"
        );
    }
}
```

### 2. SpriteSheetLoader

Override `getSubAsset()`:

```java
@Override
public <S> S getSubAsset(SpriteSheet parent, String subId, Class<S> subType) {
    if (subType == Sprite.class) {
        int index = Integer.parseInt(subId);
        if (index < 0 || index >= parent.getTotalFrames()) {
            throw new IllegalArgumentException("Sprite index out of range: " + index);
        }
        return subType.cast(parent.getSprite(index));
    }
    throw new IllegalArgumentException("SpriteSheet only provides Sprite sub-assets");
}
```

### 3. AssetManager

Modify `load()` to handle sub-asset references:

```java
@Override
public <T> T load(String path, Class<T> type) {
    String normalizedPath = normalizePath(path);

    // Check cache first (including sub-assets)
    T cached = cache.get(normalizedPath);
    if (cached != null) {
        return cached;
    }

    // Check for sub-asset reference
    int hashIndex = normalizedPath.indexOf('#');
    if (hashIndex != -1) {
        return loadSubAsset(normalizedPath, hashIndex, type);
    }

    // ... existing load logic ...
}

@SuppressWarnings("unchecked")
private <T> T loadSubAsset(String fullPath, int hashIndex, Class<T> type) {
    String basePath = fullPath.substring(0, hashIndex);
    String subId = fullPath.substring(hashIndex + 1);

    // Load parent asset
    Class<?> parentType = getTypeFromExtension(basePath);
    if (parentType == null) {
        throw new IllegalArgumentException("Unknown parent asset type: " + basePath);
    }
    Object parent = load(basePath, parentType);

    // Get loader and extract sub-asset
    AssetLoader<?> loader = loaders.get(parentType);
    T subAsset = ((AssetLoader<Object>) loader).getSubAsset(parent, subId, type);

    // Cache and register with full reference path
    cache.put(fullPath, subAsset);
    resourcePaths.put(subAsset, fullPath);

    if (statisticsEnabled) {
        cache.getStats().recordLoad();
    }

    return subAsset;
}
```

### 4. AssetContext Interface

Add method if not already present:

```java
/**
 * Gets the path for a loaded resource.
 * Returns the full reference path including sub-asset identifiers (e.g., "sheet.spritesheet#3").
 *
 * @param resource The resource object
 * @return The path, or null if not tracked
 */
String getPathForResource(Object resource);
```

### 5. Sprite Class

**Remove:**
- `sourcePath` field
- `spriteIndex` field
- `getSourcePath()` method
- `getSpriteIndex()` method
- Any setters for these fields

**Keep:**
- `name` field (for display purposes unrelated to asset path)

### 6. ComponentTypeAdapterFactory

**Keep** for component polymorphism and TilemapRenderer binary format.

Simplify `serializeSpriteRef()`:

```java
private String serializeSpriteRef(Sprite sprite) {
    if (sprite == null) return "";
    String path = Assets.getPathForResource(sprite);
    return path != null ? path : "";
}
```

Simplify `resolveSpriteRef()`:

```java
private Sprite resolveSpriteRef(String ref) {
    if (ref == null || ref.isEmpty()) return null;
    return Assets.load(ref, Sprite.class);  // Handles #index automatically
}
```

### 7. TypeAdapter Changes

#### TextureTypeAdapter - REMOVE

**Reason:** Redundant. `AssetReferenceTypeAdapterFactory` already handles Texture (not in `SKIP_TYPES`). Both do equivalent work:
- `TextureTypeAdapter`: `context.getRelativePath(texture.getFilePath())`
- `AssetReferenceTypeAdapterFactory`: `assetContext.getPathForResource(value)`

After harmonization, `resourcePaths` is the source of truth, so the factory handles it.

**In Serializer.java:**
```java
// REMOVE this line:
.registerTypeAdapter(Texture.class, new TextureTypeAdapter(context))
```

#### SpriteTypeAdapter - SIMPLIFY (Keep)

**Reason:** Still needed for **object fallback** - sprites created programmatically that have no asset path.

**Simplified write:**
```java
@Override
public void write(JsonWriter out, Sprite sprite) throws IOException {
    if (sprite == null) {
        out.nullValue();
        return;
    }

    // Single source of truth - resourcePaths map
    String path = Assets.getPathForResource(sprite);

    if (path != null) {
        // Path already includes #index if applicable
        out.value(path);
        return;
    }

    // Fallback: Serialize as full object (programmatic sprites)
    out.beginObject();
    // ... existing object serialization ...
    out.endObject();
}
```

**Simplified read:**
```java
@Override
public Sprite read(JsonReader in) throws IOException {
    JsonToken token = in.peek();

    if (token == JsonToken.NULL) {
        in.nextNull();
        return null;
    }

    // String reference - Assets.load() handles #index parsing
    if (token == JsonToken.STRING) {
        String value = in.nextString();
        return Assets.load(value, Sprite.class);
    }

    // Object definition - unchanged
    if (token == JsonToken.BEGIN_OBJECT) {
        return deserializeObject(in);
    }

    throw new IOException("Expected String or Object for Sprite, but found " + token);
}
```

#### AssetReferenceTypeAdapterFactory - Keep as-is

No changes needed. It already:
- Skips `Sprite.class` (handled by SpriteTypeAdapter)
- Handles `Texture.class` and other assets via `resourcePaths`

### 8. FieldEditors

Replace `getAssetDisplayName()`:

```java
private static String getAssetDisplayName(Object value, Class<?> type) {
    if (value == null) return "(none)";
    
    String path = Assets.getPathForResource(value);
    if (path != null) {
        return getFileName(path);
    }
    
    return "(unnamed)";
}

private static String getFileName(String path) {
    if (path == null || path.isEmpty()) return "(none)";
    int lastSlash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
    return lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
}
```

### 9. AssetPickerPopup

Expand spritesheets inline with indented sprites:

```
player.spritesheet
  ├─ player.spritesheet#0
  ├─ player.spritesheet#1
  └─ player.spritesheet#2
enemy.png
```

**Changes:**
- When scanning for `Sprite.class`, also scan for `SpriteSheet.class`
- For each spritesheet, add entries for individual sprites with `path#index` format
- Indent sprite entries visually
- Selection returns the full path including `#index`

### 10. AssetDragPayload

Update to use unified path format:

```java
public record AssetDragPayload(
    String path,      // Full path including #index if applicable
    Class<?> type,
    Object asset      // Can be removed if we always load via Assets.load(path)
) {
    // ...
    
    public static AssetDragPayload ofSpriteSheet(String sheetPath, int spriteIndex) {
        String fullPath = sheetPath + "#" + spriteIndex;
        return new AssetDragPayload(fullPath, Sprite.class, null);
    }
}
```

**Drop handling:**
```java
// Target (SceneViewport/HierarchyPanel)
AssetDragPayload payload = AssetDragPayload.deserialize(data);
Object asset = Assets.load(payload.path(), payload.type());
// Asset is now registered in resourcePaths automatically
```

---

## TypeAdapter Architecture (After Harmonization)

```
Gson Serialization
    │
    ├─► AssetReferenceTypeAdapterFactory
    │       Handles: Texture, SpriteSheet, Font, JsonPrefab, etc.
    │       Skips: Sprite (SKIP_TYPES), primitives, collections
    │       Method: resourcePaths.get(asset) → path string
    │
    ├─► SpriteTypeAdapter
    │       Handles: Sprite only
    │       Write: Assets.getPathForResource() → path, or full object fallback
    │       Read: Assets.load(path) for strings, deserializeObject() for objects
    │
    └─► ComponentTypeAdapterFactory
            Handles: Component subclasses
            Uses: Assets.getPathForResource() / Assets.load() for sprite refs in TilemapRenderer
```

**Removed:** `TextureTypeAdapter` (redundant with AssetReferenceTypeAdapterFactory)

---

## Data Flow Summary

### Loading
```
Assets.load("sheets/player.spritesheet#3", Sprite.class)
    ▼
AssetManager.load() detects "#"
    │
    ▼
loadSubAsset() called
    │
    ├─► Loads parent: load("sheets/player.spritesheet", SpriteSheet.class)
    │
    ├─► Calls SpriteSheetLoader.getSubAsset(sheet, "3", Sprite.class)
    │
    ├─► Caches sprite with key "sheets/player.spritesheet#3"
    │
    └─► Registers: resourcePaths.put(sprite, "sheets/player.spritesheet#3")
```

### Serialization
```
Sprite sprite = ...;
String path = Assets.getPathForResource(sprite);
// Returns: "sheets/player.spritesheet#3"
// Write path to JSON
```

### Deserialization
```
String path = "sheets/player.spritesheet#3";
Sprite sprite = Assets.load(path, Sprite.class);
// Sprite is loaded and registered automatically
```

### Display Name
```
Sprite sprite = ...;
String path = Assets.getPathForResource(sprite);
String displayName = getFileName(path);
// Returns: "player.spritesheet#3"
```

---

## Edge Cases

### Programmatically Created Assets
Assets not loaded through `Assets.load()` return `null` from `getPathForResource()`.

Serializers handle this gracefully:
```java
String path = Assets.getPathForResource(sprite);
if (path == null) {
    // Write null or skip field
}
```

### Manual Registration (Optional)
If needed, add method to register programmatic assets:

```java
// In AssetContext/AssetManager
void registerResource(Object resource, String path);
```

---

## Migration Checklist

- [ ] Add `getSubAsset()` to `AssetLoader` interface
- [ ] Implement `getSubAsset()` in `SpriteSheetLoader`
- [ ] Modify `AssetManager.load()` to parse `#` and call `loadSubAsset()`
- [ ] Remove `sourcePath` and `spriteIndex` from `Sprite`
- [ ] Simplify `ComponentTypeAdapterFactory.serializeSpriteRef()` and `resolveSpriteRef()`
- [ ] Simplify `SpriteTypeAdapter` (remove sourcePath/spriteIndex logic, keep object fallback)
- [ ] **Remove `TextureTypeAdapter`** from `Serializer.java`
- [ ] Replace `FieldEditors.getAssetDisplayName()` with `Assets.getPathForResource()` lookup
- [ ] Update `AssetDragPayload` to use unified path format
- [ ] Update drag-drop handlers to use `Assets.load(path, type)`
- [ ] Expand spritesheets in `AssetPickerPopup`
- [ ] Test: drag-drop sprite from spritesheet
- [ ] Test: select sprite via asset picker
- [ ] Test: save/load scene with spritesheet sprites
- [ ] Test: display name consistency across all paths
- [ ] Test: Texture serialization still works (via AssetReferenceTypeAdapterFactory)

---

## Benefits

1. **Single source of truth** - `resourcePaths` map
2. **No type-specific code** - adding new asset types doesn't require changes elsewhere
3. **Consistent naming** - same path format everywhere
4. **Simplified serialization** - no manual `#index` assembly
5. **Extensible sub-asset pattern** - any loader can support sub-assets by overriding `getSubAsset()`
