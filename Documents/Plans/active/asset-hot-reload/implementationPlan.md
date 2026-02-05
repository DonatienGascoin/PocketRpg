# Asset Hot-Reload Implementation Plan

## Overview

Enable the editor's Refresh button to reload modified assets (PNG, shader files) from disk while keeping all references valid.

**Scope:** Texture, Sprite, Shader only (no Animation, Font, AudioClip for now)

---

## Architecture

### Core Principle: Mutate Existing Objects

Instead of creating new objects on reload, mutate existing instances in place. This keeps all external references valid.

```
Before: reload() → new Texture@B (old Texture@A orphaned, references broken)
After:  reload() → Texture@A mutates internally (all references still valid)
```

### Key Findings

- **Texture sharing:** Each Sprite creates its own Texture instance (`new Texture(path)`), not shared via cache
- **Sub-assets:** Grid sprites share parent's Texture; reloading parent auto-updates sub-assets
- **Sub-asset loading:** `Assets.load("path#5")` loads parent first, then extracts - reload parent only
- **No load order needed:** Each Sprite has its own Texture

---

## Implementation

### 1. Update AssetLoader Interface

**File:** `src/main/java/com/pocket/rpg/resources/AssetLoader.java`

Update existing `reload()` method contract and default implementation:

```java
/**
 * Reloads an asset in place by mutating the existing instance.
 * <p>
 * <b>Contract:</b> Implementations MUST mutate the existing instance rather than
 * creating a new one. This ensures all external references remain valid after reload.
 * <p>
 * <b>Failure handling:</b> If reload fails, the existing instance should remain
 * unchanged (load new data before destroying old).
 *
 * @param existing The existing asset instance to mutate
 * @param path Full path to reload from
 * @return The same instance passed in (mutated), or existing unchanged if not supported
 * @throws IOException if reload fails
 */
default T reload(T existing, String path) throws IOException {
    Log.warn("Hot-reload not implemented for " + getClass().getSimpleName() +
             ", skipping: " + path);
    return existing;
}
```

### 2. Texture.reloadFromDisk()

**File:** `src/main/java/com/pocket/rpg/rendering/resources/Texture.java`

```java
/**
 * Reloads this texture from disk, replacing GPU data while keeping the same instance.
 * Creates new GL texture before destroying old to avoid render gaps.
 *
 * @param path Path to image file
 * @throws IOException if loading fails (this texture unchanged on failure)
 */
public void reloadFromDisk(String path) throws IOException {
    // 1. Load new image data FIRST (fail-fast, don't destroy old yet)
    int[] newWidth = new int[1];
    int[] newHeight = new int[1];
    ByteBuffer newImageData = loadImageData(path, newWidth, newHeight);

    // 2. Create new GL texture
    int newTextureId = createGLTexture(newImageData, newWidth[0], newHeight[0]);

    // 3. Only NOW destroy old texture (no gap - new one ready)
    if (this.textureId != 0) {
        glDeleteTextures(this.textureId);
    }

    // 4. Update internal state
    this.textureId = newTextureId;
    this.width = newWidth[0];
    this.height = newHeight[0];

    // 5. Free CPU-side image data
    STBImage.stbi_image_free(newImageData);
}
```

### 3. Sprite.reloadMetadata()

**File:** `src/main/java/com/pocket/rpg/rendering/resources/Sprite.java`

```java
/**
 * Reloads metadata (pivot, 9-slice) in place.
 * Called after texture reload to pick up any metadata changes.
 *
 * @param metadata The new metadata (may be null for defaults)
 */
public void reloadMetadata(SpriteMetadata metadata) {
    if (metadata == null) {
        // Reset to defaults
        this.pivotX = 0.5f;
        this.pivotY = 0.5f;
        this.nineSliceData = null;
        return;
    }

    if (metadata.isSingle()) {
        if (metadata.hasPivot()) {
            this.pivotX = metadata.pivotX;
            this.pivotY = metadata.pivotY;
        }
        this.nineSliceData = metadata.nineSlice != null ? metadata.nineSlice.copy() : null;
    }
    // Multiple mode: pivot/9-slice handled per grid sprite
}
```

### 4. Shader.reloadFromDisk()

**File:** `src/main/java/com/pocket/rpg/rendering/resources/Shader.java`

```java
/**
 * Reloads and recompiles this shader from disk.
 *
 * @param path Path to shader file
 * @throws IOException if loading or compilation fails (old shader unchanged on failure)
 */
public void reloadFromDisk(String path) throws IOException {
    // 1. Load and parse new shader source
    String source = Files.readString(Path.of(path));
    String vertexSource = extractVertexShader(source);
    String fragmentSource = extractFragmentShader(source);

    // 2. Compile new program (fail-fast)
    int newProgramId = compileProgram(vertexSource, fragmentSource);

    // 3. Only NOW delete old program
    if (this.programId != 0) {
        glDeleteProgram(this.programId);
    }

    // 4. Update internal state
    this.programId = newProgramId;
    // Re-cache uniform locations
    this.uniformLocations.clear();
}
```

### 5. Update Loader Implementations

**TextureLoader.java:**
```java
@Override
public boolean supportsHotReload() {
    return true;
}

@Override
public Texture reload(Texture existing, String path) throws IOException {
    existing.reloadFromDisk(path);
    return existing;
}
```

**SpriteLoader.java:**
```java
@Override
public boolean supportsHotReload() {
    return true;
}

@Override
public Sprite reload(Sprite existing, String path) throws IOException {
    // Reload underlying texture
    Texture texture = existing.getTexture();
    if (texture != null) {
        texture.reloadFromDisk(path);
    }

    // Reload metadata
    String relativePath = Assets.getRelativePath(path);
    SpriteMetadata meta = null;
    if (relativePath != null) {
        meta = AssetMetadata.load(relativePath, SpriteMetadata.class);
    }
    existing.reloadMetadata(meta);

    // Clear grid cache for multiple-mode sprites
    SpriteGrid grid = gridCache.get(existing);
    if (grid != null) {
        grid.clearCache();
    }

    return existing;
}
```

**ShaderLoader.java:**
```java
@Override
public boolean supportsHotReload() {
    return true;
}

@Override
public Shader reload(Shader existing, String path) throws IOException {
    existing.reloadFromDisk(path);
    return existing;
}
```

### 6. Assets.reload() and reloadAll()

**File:** `src/main/java/com/pocket/rpg/resources/Assets.java`

```java
/**
 * Reloads an asset from disk, mutating the cached instance in place.
 * All existing references remain valid.
 *
 * @param path Asset path (e.g., "sprites/player.png")
 * @return true if reloaded, false if not cached or reload not supported
 */
public static boolean reload(String path) {
    AssetContext ctx = getContext();
    if (!(ctx instanceof AssetManager manager)) {
        return false;
    }

    // Skip sub-asset paths - reload parent instead
    if (path.contains("#")) {
        path = path.substring(0, path.indexOf('#'));
    }

    Object cached = manager.getCache().get(path);
    if (cached == null) {
        return false; // Not loaded, nothing to reload
    }

    Class<?> type = manager.getCachedType(path);
    if (type == null) {
        return false;
    }

    AssetLoader<?> loader = manager.getLoader(type);
    if (loader == null || !loader.supportsHotReload()) {
        return false;
    }

    try {
        String fullPath = manager.resolvePath(path);
        reloadWithLoader(loader, cached, fullPath);
        Log.info("Reloaded: " + path);
        return true;
    } catch (IOException e) {
        Log.error("Failed to reload " + path + ": " + e.getMessage());
        return false;
    }
}

@SuppressWarnings("unchecked")
private static <T> void reloadWithLoader(AssetLoader<T> loader, Object asset, String path)
        throws IOException {
    T existing = (T) asset;
    T result = loader.reload(existing, path);

    // Guard: ensure loader mutated in place rather than creating new reference
    if (result != existing) {
        Log.error("Hot-reload contract violation for " + existing.getClass().getSimpleName() +
                  ": " + loader.getClass().getSimpleName() + ".reload() returned new reference " +
                  "instead of mutating existing. Path: " + path + ". " +
                  "Fix: Update the loader's reload() method to mutate the existing instance in place " +
                  "and return it, rather than creating a new object. Keeping old asset to prevent broken references.");
        // Don't use the new reference - keep existing to avoid breaking external references
    }
}

/**
 * Reloads all cached assets that support hot-reload.
 *
 * @return Number of assets reloaded
 */
public static int reloadAll() {
    AssetContext ctx = getContext();
    if (!(ctx instanceof AssetManager manager)) {
        return 0;
    }

    int count = 0;
    for (String path : new ArrayList<>(manager.getCache().getPaths())) {
        // Skip sub-assets (they share parent's texture)
        if (path.contains("#")) {
            continue;
        }
        if (reload(path)) {
            count++;
        }
    }
    return count;
}
```

### 7. AssetManager Changes

**File:** `src/main/java/com/pocket/rpg/resources/AssetManager.java`

Add type tracking when caching assets:

```java
// Track asset types for reload
private final Map<String, Class<?>> cachedTypes = new ConcurrentHashMap<>();

// In loadWithType(), after caching:
cachedTypes.put(normalizedPath, type);

// Add getter:
public Class<?> getCachedType(String path) {
    return cachedTypes.get(path);
}

// Add loader getter:
public AssetLoader<?> getLoader(Class<?> type) {
    return loaders.get(type);
}
```

### 8. AssetBrowserPanel.refresh()

**File:** `src/main/java/com/pocket/rpg/editor/panels/AssetBrowserPanel.java`

```java
public void refresh() {
    long now = System.currentTimeMillis();
    if (now - lastRefreshTime < REFRESH_COOLDOWN_MS) {
        return;
    }
    lastRefreshTime = now;

    // Reload all changed assets from disk
    int reloaded = Assets.reloadAll();
    if (reloaded > 0) {
        Log.info("[AssetBrowser] Reloaded " + reloaded + " assets");
    }

    // Clear caches
    multipleModeCache.clear();
    thumbnailCache.clear();

    // ... rest of existing refresh logic ...
}
```

---

## Files to Change

| File | Changes |
|------|---------|
| `AssetLoader.java` | Update `reload()` javadoc and default (log warning, return existing) |
| `Texture.java` | Add `reloadFromDisk(path)` |
| `Sprite.java` | Add `reloadMetadata(metadata)` |
| `Shader.java` | Add `reloadFromDisk(path)` |
| `TextureLoader.java` | Implement mutating `reload()` |
| `SpriteLoader.java` | Implement mutating `reload()` |
| `ShaderLoader.java` | Implement mutating `reload()` |
| `Assets.java` | Add `reload(path)`, `reloadAll()` |
| `AssetManager.java` | Add `cachedTypes` map, `getCachedType()`, `getLoader()` |
| `AssetBrowserPanel.java` | Call `Assets.reloadAll()` in `refresh()` |

---

## Out of Scope

- **Timestamp tracking:** No `reloadChanged()` - just `reloadAll()` for simplicity
- **Animation/Font/AudioClip:** Not needed for now
- **Sub-asset special handling:** Reload parent; sub-assets share texture automatically
- **File watcher:** Manual refresh button only

---

## Documentation

After implementation, update `Documents/Encyclopedia/`:
- Add page on asset hot-reload
- Document which asset types support it
- Explain that Refresh button reloads from disk
- Note: sub-assets (spritesheet#N) auto-update when parent reloads

---

## Testing

Manual testing:
1. Load a scene with sprites
2. Modify PNG file externally (e.g., in GIMP)
3. Click Refresh in Asset Browser
4. Verify sprite updates in both Asset Browser thumbnails and Scene View
5. Verify no crashes or visual glitches
