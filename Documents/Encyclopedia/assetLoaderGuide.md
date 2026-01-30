# Asset Loader Guide

> **Summary:** Create custom asset loaders to handle new file types. Loaders are automatically discovered via reflection - just implement `AssetLoader<T>` and provide a no-arg constructor.

---

## Table of Contents

1. [Quick Reference](#quick-reference)
2. [Overview](#overview)
3. [Creating an Asset Loader](#creating-an-asset-loader)
4. [Required Methods](#required-methods)
5. [Optional Methods](#optional-methods)
6. [Editor Integration](#editor-integration)
7. [Sub-Asset Support](#sub-asset-support)
8. [Tips & Best Practices](#tips--best-practices)
9. [Troubleshooting](#troubleshooting)
10. [Related](#related)

---

## Quick Reference

| Task | How |
|------|-----|
| Create a loader | Implement `AssetLoader<MyAsset>` with no-arg constructor |
| Load an asset | `MyAsset asset = Assets.load("path/to/file.ext", MyAsset.class)` |
| Load by extension | `MyAsset asset = Assets.load("path/to/file.ext")` (auto-detects type) |
| Save an asset | `Assets.persist(asset, "path/to/file.ext")` |
| Enable drag-drop | Override `canInstantiate()` and `instantiate()` |
| Add editor panel | Override `getEditorPanelType()` |

---

## Overview

Asset loaders handle the loading, saving, and editor integration for specific file types. The `AssetManager` automatically discovers all loaders at startup by scanning for classes that implement `AssetLoader<T>`.

**Key features:**
- **Automatic registration** - No manual registration needed; just implement the interface
- **Type inference** - The asset type `T` is extracted from the generic parameter via reflection
- **Extension mapping** - File extensions are automatically mapped to asset types
- **Caching** - Loaded assets are cached to prevent duplicate loading
- **Editor integration** - Loaders can provide thumbnails, icons, and drag-drop support

**Built-in loaders:**

| Loader | Asset Type | Extensions |
|--------|------------|------------|
| `TextureLoader` | `Texture` | `.png`, `.jpg`, `.jpeg`, `.bmp`, `.tga` |
| `SpriteLoader` | `Sprite` | `.png`, `.jpg`, `.jpeg`, `.bmp`, `.tga` |
| `SpriteSheetLoader` | `SpriteSheet` | `.spritesheet`, `.spritesheet.json` |
| `ShaderLoader` | `Shader` | `.glsl`, `.shader`, `.vs`, `.fs` |
| `FontLoader` | `Font` | `.font`, `.font.json`, `.ttf`, `.otf` |
| `AnimationLoader` | `Animation` | `.anim`, `.anim.json` |
| `SceneDataLoader` | `SceneData` | `.scene`, `.scene.json` |
| `JsonPrefabLoader` | `JsonPrefab` | `.prefab`, `.prefab.json` |

---

## Creating an Asset Loader

### Step 1: Create the Asset Class

First, create the class that represents your asset:

```java
package com.pocket.rpg.resources;

public class MyAsset {
    private String name;
    private int value;

    // Getters, setters, etc.
}
```

### Step 2: Create the Loader

Implement `AssetLoader<T>` where `T` is your asset type:

```java
package com.pocket.rpg.resources.loaders;

import com.pocket.rpg.resources.AssetLoader;
import java.io.IOException;

public class MyAssetLoader implements AssetLoader<MyAsset> {

    @Override
    public MyAsset load(String path) throws IOException {
        // Load and parse the file
        String content = Files.readString(Paths.get(path));
        return parseMyAsset(content);
    }

    @Override
    public void save(MyAsset asset, String path) throws IOException {
        // Serialize and write to disk
        String content = serializeMyAsset(asset);
        Files.writeString(Paths.get(path), content);
    }

    @Override
    public MyAsset getPlaceholder() {
        // Return a default asset for error cases
        return new MyAsset("placeholder", 0);
    }

    @Override
    public String[] getSupportedExtensions() {
        return new String[]{".myasset", ".myasset.json"};
    }
}
```

### Step 3: Requirements

Your loader class must:

1. **Implement `AssetLoader<T>`** - Where `T` is your asset class
2. **Have a no-arg constructor** - Required for reflection-based instantiation
3. **Be in `com.pocket.rpg` package** - Or any sub-package (for scanning)

That's it! The loader is automatically discovered and registered at startup.

---

## Required Methods

### `load(String path)`

Loads an asset from the file system.

```java
@Override
public MyAsset load(String path) throws IOException {
    try {
        String json = Files.readString(Paths.get(path));
        return gson.fromJson(json, MyAsset.class);
    } catch (Exception e) {
        throw new IOException("Failed to load: " + path, e);
    }
}
```

**Notes:**
- The `path` is already fully resolved (absolute path)
- Throw `IOException` on failure - the system will use the placeholder

### `save(T resource, String path)`

Saves an asset to disk.

```java
@Override
public void save(MyAsset asset, String path) throws IOException {
    String json = gson.toJson(asset);

    // Create parent directories if needed
    Path filePath = Paths.get(path);
    Path parent = filePath.getParent();
    if (parent != null && !Files.exists(parent)) {
        Files.createDirectories(parent);
    }

    Files.writeString(filePath, json);
}
```

**Notes:**
- Throw `UnsupportedOperationException` if saving isn't supported
- Always create parent directories if they don't exist

### `getPlaceholder()`

Returns a fallback asset when loading fails.

```java
@Override
public MyAsset getPlaceholder() {
    // Return a valid but obviously "wrong" asset
    return new MyAsset("MISSING", -1);
}
```

**Notes:**
- Return `null` if no meaningful placeholder exists
- Placeholders prevent crashes and make missing assets visible

### `getSupportedExtensions()`

Returns file extensions this loader handles.

```java
@Override
public String[] getSupportedExtensions() {
    return new String[]{".myasset", ".myasset.json"};
}
```

**Notes:**
- Include the leading dot (`.png`, not `png`)
- Use lowercase
- List compound extensions first (`.myasset.json` before `.json`)

---

## Optional Methods

### `supportsHotReload()`

Enable runtime reloading when files change.

```java
@Override
public boolean supportsHotReload() {
    return true;
}

@Override
public MyAsset reload(MyAsset existing, String path) throws IOException {
    // Clean up old resource if needed
    existing.dispose();
    // Load fresh
    return load(path);
}
```

### `getIconCodepoint()`

Provide an icon for the asset browser.

```java
@Override
public String getIconCodepoint() {
    return MaterialIcons.DataObject;  // Or any MaterialIcons constant
}
```

---

## Editor Integration

### Drag-Drop to Scene

Allow assets to be dragged into the scene viewport:

```java
@Override
public boolean canInstantiate() {
    return true;
}

@Override
public EditorGameObject instantiate(MyAsset asset, String assetPath, Vector3f position) {
    // Create entity with appropriate components
    EditorGameObject entity = new EditorGameObject(asset.getName(), position, false);

    // Add components based on asset type
    MyComponent component = new MyComponent();
    component.setAsset(asset);
    entity.addComponent(component);

    return entity;
}
```

### Asset Preview Thumbnail

Provide a sprite preview for the asset browser:

```java
@Override
public Sprite getPreviewSprite(MyAsset asset) {
    // Return a sprite representing this asset
    if (asset != null && asset.hasPreview()) {
        return asset.getPreviewSprite();
    }
    return null;  // Falls back to icon
}
```

### Double-Click Editor Panel

Open a dedicated editor when the asset is double-clicked:

```java
@Override
public EditorPanelType getEditorPanelType() {
    return EditorPanelType.MY_ASSET_EDITOR;
}
```

**Note:** You'll also need to register the panel type in `EditorPanelType` enum.

### Editor Capabilities

Declare special editor features this asset supports:

```java
@Override
public Set<EditorCapability> getEditorCapabilities() {
    return Set.of(
        EditorCapability.PIVOT_EDITING,
        EditorCapability.NINE_SLICE_EDITING
    );
}
```

---

## Sub-Asset Support

Some assets contain addressable sub-assets (e.g., sprites within a sprite sheet).

### Loading Sub-Assets

Sub-assets use the `#` separator in paths:

```java
// Load sprite index 3 from a sprite sheet
Sprite sprite = Assets.load("sheets/player.spritesheet#3", Sprite.class);
```

### Implementing Sub-Asset Support

Override `getSubAsset()` in your loader:

```java
@Override
@SuppressWarnings("unchecked")
public <S> S getSubAsset(MyAsset parent, String subId, Class<S> subType) {
    if (subType != ChildAsset.class) {
        throw new IllegalArgumentException("Only supports ChildAsset sub-assets");
    }

    int index = Integer.parseInt(subId);
    return (S) parent.getChild(index);
}
```

---

## Tips & Best Practices

- **Use Gson for JSON assets** - Consistent with other loaders, handles most cases well
- **Validate on load** - Check required fields and throw descriptive `IOException` messages
- **Support compound extensions** - Use `.myasset.json` for JSON-based formats (easier debugging)
- **Create meaningful placeholders** - Make missing assets visually obvious (magenta textures, etc.)
- **Handle hot reload carefully** - Clean up OpenGL resources, reset caches, preserve references
- **Keep loaders stateless** - Don't store instance data; use the asset class for state

---

## Troubleshooting

| Problem | Solution |
|---------|----------|
| Loader not registered | Ensure class is in `com.pocket.rpg` package and has no-arg constructor |
| Wrong loader used | Check extension order - more specific extensions should be first |
| Asset not caching | Ensure path normalization is consistent (forward slashes) |
| Hot reload not working | Override both `supportsHotReload()` and `reload()` |
| Sub-asset not loading | Implement `getSubAsset()` and use `#` separator in path |
| Drag-drop not working | Override both `canInstantiate()` and `instantiate()` |

---

## Related

- [Custom Inspector Guide](custom-inspector-guide.md) - Creating custom component inspectors
- [Sprite Editor Guide](sprite-editor-guide.md) - Editing sprite properties
- [Animation Editor Guide](animation-editor-guide.md) - Creating animations
