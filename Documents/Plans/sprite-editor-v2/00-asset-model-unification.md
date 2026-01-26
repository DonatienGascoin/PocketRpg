# Sprite Asset Model Unification Plan

**Status: COMPLETE**

## Overview

Unify `Sprite` and `SpriteSheet` into a single asset concept where a texture can be either **Single** (one sprite) or **Multiple** (grid of sprites), similar to Unity's Sprite import settings.

This plan should be completed **BEFORE** implementing Sprite Editor V2.

---

## Current Model vs Unified Model

### Current Model

```
assets/
├── sprites/
│   └── icon.png                    ← Single sprite (Sprite class)
├── spritesheets/
│   ├── player.spritesheet          ← Grid definition (SpriteSheet class)
│   └── player.png                  ← Source texture
```

- **Two asset types**: `Sprite` and `SpriteSheet`
- **Two loaders**: `SpriteLoader` and `SpriteSheetLoader`
- **Reference format**: `"player.spritesheet#3"` for sheet sprites

### Unified Model (Unity-style)

```
assets/
├── sprites/
│   ├── icon.png                    ← Texture file
│   └── icon.png.meta               ← Mode: Single, pivot, 9-slice
├── spritesheets/
│   ├── player.png                  ← Texture file
│   └── player.png.meta             ← Mode: Multiple, grid settings, per-sprite data
```

- **One concept**: Sprite (with mode: Single or Multiple)
- **One loader**: `SpriteLoader` handles both modes
- **Reference format**: `"player.png#3"` for multiple-mode sprites
- **No `.spritesheet` files** - metadata in `.meta` files

---

## New Metadata Format

### Single Mode (`.png.meta`)

```json
{
  "spriteMode": "single",
  "pivot": { "x": 0.5, "y": 0.0 },
  "nineSlice": { "left": 0, "right": 0, "top": 0, "bottom": 0 },
  "pixelsPerUnit": 16
}
```

### Multiple Mode (`.png.meta`)

```json
{
  "spriteMode": "multiple",
  "grid": {
    "spriteWidth": 16,
    "spriteHeight": 16,
    "spacingX": 0,
    "spacingY": 0,
    "offsetX": 0,
    "offsetY": 0
  },
  "defaultPivot": { "x": 0.5, "y": 0.0 },
  "defaultNineSlice": { "left": 0, "right": 0, "top": 0, "bottom": 0 },
  "sprites": {
    "0": { "pivot": { "x": 0.5, "y": 0.0 } },
    "5": { "pivot": { "x": 0.3, "y": 0.7 }, "nineSlice": { "left": 4, "right": 4, "top": 4, "bottom": 4 } }
  },
  "pixelsPerUnit": 16
}
```

---

## Reference Format Changes

| Current | Unified | Notes |
|---------|---------|-------|
| `"sprites/icon.png"` | `"sprites/icon.png"` | No change for single sprites |
| `"spritesheets/player.spritesheet#3"` | `"spritesheets/player.png#3"` | Remove `.spritesheet`, reference texture directly |
| `Assets.load(path, SpriteSheet.class)` | `Assets.loadSpriteSheet(path)` | New helper method |

---

## Migration Strategy

### Automatic Migration

When loading a `.spritesheet` file:
1. Read the old format
2. Create corresponding `.png.meta` file
3. Delete the `.spritesheet` file (or mark as migrated)
4. Update references in scene/prefab files

### Migration Tool

```
[Migrate Spritesheets]
Found 8 .spritesheet files to migrate:
  ✓ player.spritesheet → player.png.meta
  ✓ outdoor.spritesheet → outdoor.png.meta
  ✓ trees.spritesheet → trees.png.meta
  ...

Updating references in 12 files:
  ✓ scenes/demo.scene.json
  ✓ prefabs/player.prefab.json
  ...

Migration complete!
```

---

## Implementation Phases

### Phase 1: Extend SpriteMetadata

Add grid/multiple-mode support to existing `SpriteMetadata` class.

**Files:**

| File | Change |
|------|--------|
| `resources/SpriteMetadata.java` | Add `spriteMode`, `grid`, `sprites` map fields |

**Tasks:**
- [x] Add `SpriteMode` enum: `SINGLE`, `MULTIPLE`
- [x] Add `GridSettings` inner class (width, height, spacing, offset)
- [x] Add `sprites` map for per-sprite overrides
- [x] Add `defaultPivot` and `defaultNineSlice` fields
- [x] Add helper methods: `isSingle()`, `isMultiple()`, `getSpriteCount()`
- [x] Ensure backwards compatibility with existing `.meta` files

**New SpriteMetadata structure:**

```java
public class SpriteMetadata {
    // Mode
    public SpriteMode spriteMode = SpriteMode.SINGLE;

    // Single mode fields (existing)
    public Float pivotX;
    public Float pivotY;
    public NineSliceData nineSlice;
    public Float pixelsPerUnitOverride;

    // Multiple mode fields (new)
    public GridSettings grid;
    public PivotData defaultPivot;
    public NineSliceData defaultNineSlice;
    public Map<Integer, SpriteOverride> sprites; // per-sprite overrides

    public enum SpriteMode { SINGLE, MULTIPLE }

    public static class GridSettings {
        public int spriteWidth = 16;
        public int spriteHeight = 16;
        public int spacingX = 0;
        public int spacingY = 0;
        public int offsetX = 0;
        public int offsetY = 0;
    }

    public static class SpriteOverride {
        public PivotData pivot;
        public NineSliceData nineSlice;
    }
}
```

---

### Phase 2: Update SpriteLoader

Modify `SpriteLoader` to handle both Single and Multiple modes.

**Files:**

| File | Change |
|------|--------|
| `resources/loaders/SpriteLoader.java` | Add multiple-mode support, sub-asset loading |

**Tasks:**
- [x] Load `.meta` file alongside texture
- [x] If `spriteMode == MULTIPLE`, create sprite grid (like SpriteSheet does)
- [x] Implement `getSubAsset(parent, index, Sprite.class)` for indexed access
- [x] Cache generated sprites per texture
- [x] Handle `path#index` format for loading specific sprites

**Loading logic:**

```java
public Sprite load(String path) {
    // Check for sub-asset format: "player.png#3"
    if (path.contains("#")) {
        return loadSubSprite(path);
    }

    // Load texture
    Texture texture = Assets.load(texturePath, Texture.class);

    // Load metadata
    SpriteMetadata meta = AssetMetadata.load(path, SpriteMetadata.class);

    if (meta != null && meta.spriteMode == SpriteMode.MULTIPLE) {
        // For multiple mode without index, return first sprite or throw error
        throw new IllegalArgumentException("Multiple-mode sprite requires index: " + path + "#0");
    }

    // Single mode - return full texture as sprite
    return createSingleSprite(texture, meta);
}

private Sprite loadSubSprite(String path) {
    String[] parts = path.split("#");
    String texturePath = parts[0];
    int index = Integer.parseInt(parts[1]);

    // Load or get cached sprite grid
    SpriteGrid grid = getOrCreateSpriteGrid(texturePath);
    return grid.getSprite(index);
}
```

---

### Phase 3: Create SpriteGrid Helper

Extract grid management from `SpriteSheet` into a reusable helper.

**Files:**

| File | Change |
|------|--------|
| `rendering/resources/SpriteGrid.java` | **NEW** - Grid calculation and sprite extraction |

**Tasks:**
- [x] Extract grid calculation logic from `SpriteSheet`
- [x] Support lazy sprite generation
- [x] Support pivot/9-slice per sprite
- [x] Cache sprites by index

**SpriteGrid class:**

```java
public class SpriteGrid {
    private final Texture texture;
    private final SpriteMetadata.GridSettings grid;
    private final SpriteMetadata metadata;
    private final Map<Integer, Sprite> spriteCache = new HashMap<>();

    private int columns;
    private int rows;
    private int totalSprites;

    public SpriteGrid(Texture texture, SpriteMetadata metadata) {
        this.texture = texture;
        this.grid = metadata.grid;
        this.metadata = metadata;
        calculateGrid();
    }

    public Sprite getSprite(int index) {
        return spriteCache.computeIfAbsent(index, this::createSprite);
    }

    public int getTotalSprites() { return totalSprites; }
    public int getColumns() { return columns; }
    public int getRows() { return rows; }

    // ... grid calculation and sprite creation logic from SpriteSheet
}
```

---

### Phase 4: Update Asset Loading Infrastructure

Update `Assets` class and related infrastructure.

**Files:**

| File | Change |
|------|--------|
| `resources/Assets.java` | Add helper methods for sprite loading |
| `resources/SpriteReference.java` | Update path handling |
| `serialization/custom/SpriteTypeAdapter.java` | Update for new format |

**Tasks:**
- [x] Add `Assets.loadSpriteSheet(path)` → returns `SpriteGrid`
- [x] Update `SpriteReference.toPath()` to use new format
- [x] Update `SpriteReference.fromPath()` to handle both old and new formats
- [x] Update `SpriteTypeAdapter` for backwards compatibility during migration

**Backwards compatibility:**

```java
// SpriteReference.fromPath() handles both formats:
"spritesheets/player.spritesheet#3"  → loads player.png, gets sprite 3
"spritesheets/player.png#3"          → loads player.png, gets sprite 3
"sprites/icon.png"                   → loads icon.png as single sprite
```

---

### Phase 5: Update TilesetRegistry

Replace `SpriteSheet` usage with `SpriteGrid`.

**Files:**

| File | Change |
|------|--------|
| `editor/tileset/TilesetRegistry.java` | Use SpriteGrid instead of SpriteSheet |
| `editor/tileset/TilesetEntry.java` | Update to use SpriteGrid |
| `editor/tileset/TilesetSelector.java` | Update for new model |

**Tasks:**
- [x] Change `TilesetEntry` to wrap `SpriteGrid` instead of `SpriteSheet`
- [x] Update `scanAndLoad()` to find `.png.meta` files with `spriteMode: multiple`
- [x] Update `getSprites()` to use `SpriteGrid.getSprite()`
- [x] Maintain backwards compatibility during migration

**Updated TilesetEntry:**

```java
public class TilesetEntry {
    private final String displayName;
    private final String path;  // Now points to .png, not .spritesheet
    private final SpriteGrid spriteGrid;
    private List<Sprite> cachedSprites;

    public List<Sprite> getSprites() {
        if (cachedSprites == null) {
            cachedSprites = new ArrayList<>();
            for (int i = 0; i < spriteGrid.getTotalSprites(); i++) {
                cachedSprites.add(spriteGrid.getSprite(i));
            }
        }
        return cachedSprites;
    }
}
```

---

### Phase 6: Update Editor Panels

Update editor panels to work with the unified model.

**Files:**

| File | Change |
|------|--------|
| `editor/panels/SpriteEditorPanel.java` | Use SpriteMetadata/SpriteGrid |
| `editor/tileset/CreateSpritesheetDialog.java` | Create .meta instead of .spritesheet |
| `editor/panels/AssetBrowserPanel.java` | Update asset type detection |

**Tasks:**
- [x] Update `SpriteEditorPanel` to load/save `SpriteMetadata`
- [x] Update `CreateSpritesheetDialog` to create `.meta` file with `spriteMode: multiple`
- [x] Update asset browser to show sprite mode (Single/Multiple) instead of type
- [x] Remove `SpriteSheet.class` from asset picker options

---

### Phase 7: Migration Tool

Create a tool to migrate existing `.spritesheet` files.

**Files:**

| File | Change |
|------|--------|
| `editor/tools/SpritesheetMigrationTool.java` | **NEW** - Migration utility |
| `editor/EditorMenuBar.java` | Add migration menu item |

**Tasks:**
- [x] Scan for all `.spritesheet` files
- [x] Convert each to `.png.meta` format
- [x] Update references in all `.scene.json` and `.prefab.json` files
- [x] Provide dry-run mode to preview changes
- [x] Provide backup before migration
- [x] Show migration report

**Migration process:**

```java
public class SpritesheetMigrationTool {
    public MigrationReport migrate(boolean dryRun) {
        List<String> spritesheetFiles = findSpritesheetFiles();

        for (String ssPath : spritesheetFiles) {
            // 1. Read old .spritesheet file
            SpriteSheetData oldData = readOldFormat(ssPath);

            // 2. Create new .meta file
            String texturePath = oldData.texture;
            SpriteMetadata newMeta = convertToMetadata(oldData);

            if (!dryRun) {
                AssetMetadata.save(texturePath, newMeta);
            }

            // 3. Update references
            String oldRef = ssPath;  // "spritesheets/player.spritesheet"
            String newRef = texturePath;  // "spritesheets/player.png"
            updateReferences(oldRef, newRef, dryRun);

            // 4. Delete old file
            if (!dryRun) {
                Files.delete(Path.of(Assets.getAssetRoot(), ssPath));
            }
        }

        return report;
    }
}
```

---

### Phase 8: Update Remaining Code

Update all remaining code that uses `SpriteSheet`.

**Files:**

| File | Change |
|------|--------|
| `DemoScene.java` | Remove SpriteSheet usage |
| `ExampleScene.java` | Remove SpriteSheet usage |
| `EditorScene.java` | Remove SpriteSheet usage |
| `TileSelection.java` | Use SpriteGrid |
| `TileGridRenderer.java` | Use SpriteGrid |
| `Animation.java` | Verify compatibility (should work) |

**Tasks:**
- [x] Replace `new SpriteSheet(...)` with metadata-based loading
- [x] Update `TileSelection` to get sprites from `SpriteGrid`
- [x] Verify animation system works with new paths
- [x] Build succeeds with no SpriteSheet references

---

### Phase 9: Deprecate and Remove SpriteSheet

Remove the old `SpriteSheet` class and `SpriteSheetLoader`.

**Files:**

| File | Change |
|------|--------|
| `rendering/resources/SpriteSheet.java` | **DELETE** |
| `resources/loaders/SpriteSheetLoader.java` | **DELETE** |
| `resources/Assets.java` | Remove SpriteSheet registration |

**Tasks:**
- [x] Verify no code references `SpriteSheet` class
- [x] Verify no `.spritesheet` files remain
- [x] Delete `SpriteSheet.java`
- [x] Delete `SpriteSheetLoader.java`
- [x] Remove loader registration from `Assets`
- [x] Update SpriteEditorPanel, AssetPickerPopup, TilesetRegistry, AssetBrowserPanel

---

## File Change Summary

### New Files

| File | Purpose |
|------|---------|
| `rendering/resources/SpriteGrid.java` | Grid calculation and sprite extraction |
| `editor/tools/SpritesheetMigrationTool.java` | Migration utility |

### Modified Files

| File | Change |
|------|--------|
| `resources/SpriteMetadata.java` | Add spriteMode, grid, sprites fields |
| `resources/loaders/SpriteLoader.java` | Handle multiple mode, sub-assets |
| `resources/Assets.java` | Add helper methods |
| `resources/SpriteReference.java` | Update path handling |
| `serialization/custom/SpriteTypeAdapter.java` | Backwards compatibility |
| `editor/tileset/TilesetRegistry.java` | Use SpriteGrid |
| `editor/tileset/TilesetEntry.java` | Use SpriteGrid |
| `editor/tileset/TilesetSelector.java` | Update for new model |
| `editor/panels/SpriteEditorPanel.java` | Use SpriteMetadata |
| `editor/tileset/CreateSpritesheetDialog.java` | Create .meta files |
| `editor/panels/AssetBrowserPanel.java` | Update type detection |
| `DemoScene.java` | Remove SpriteSheet usage |
| `ExampleScene.java` | Remove SpriteSheet usage |
| `EditorScene.java` | Remove SpriteSheet usage |
| `TileSelection.java` | Use SpriteGrid |
| `TileGridRenderer.java` | Use SpriteGrid |

### Deleted Files (Phase 9)

| File | Reason |
|------|--------|
| `rendering/resources/SpriteSheet.java` | Replaced by SpriteGrid + SpriteMetadata |
| `resources/loaders/SpriteSheetLoader.java` | Functionality in SpriteLoader |

### Migrated Data Files

| Old Format | New Format |
|------------|------------|
| `player.spritesheet` | `player.png.meta` |
| `outdoor.spritesheet` | `outdoor.png.meta` |
| `trees.spritesheet` | `trees.png.meta` |
| (all 8 `.spritesheet` files) | (corresponding `.meta` files) |

---

## Testing Strategy

### Phase 1-3 Testing
- [ ] SpriteMetadata loads/saves correctly with new fields
- [ ] SpriteLoader handles single mode as before
- [ ] SpriteLoader handles multiple mode with index
- [ ] SpriteGrid calculates grid correctly
- [ ] Sprites extracted from grid have correct UVs

### Phase 4-5 Testing
- [ ] Old reference format still works (`player.spritesheet#3`)
- [ ] New reference format works (`player.png#3`)
- [ ] TilesetRegistry finds multiple-mode textures
- [ ] Tileset palette shows correct sprites

### Phase 6-7 Testing
- [ ] SpriteEditorPanel edits both modes correctly
- [ ] CreateSpritesheetDialog creates valid .meta files
- [ ] Migration tool converts all files
- [ ] Migration tool updates all references
- [ ] No broken references after migration

### Phase 8-9 Testing
- [ ] All scenes load correctly
- [ ] All prefabs load correctly
- [ ] Animations play correctly
- [ ] Tilemap painting works
- [ ] No compiler errors after SpriteSheet deletion

---

## Rollback Plan

If issues are discovered after migration:

1. **Backup**: Migration tool creates backup of all files before changes
2. **Restore**: Restore from backup if needed
3. **Compatibility**: Keep old loader around (deprecated) until fully tested

---

## Code Review

After each phase, verify:
1. Backwards compatibility maintained
2. No broken references
3. Performance comparable to before
4. Memory usage not increased significantly

Final review after Phase 9:
- Write review to `Documents/Plans/sprite-editor-v2/review-asset-model.md`
