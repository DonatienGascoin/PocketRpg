# Asset Rename — Design Document

## 1. Overview

### Problem

There is no way to rename an asset from within the editor. Users must manually rename files on disk, then hand-edit every scene, animation, animator controller, prefab, and config file that references the old path. This is error-prone and tedious.

### Goal

Add a "Rename" option to the AssetBrowserPanel right-click context menu. When triggered, the editor renames the file on disk and **automatically updates all references** across the project — scene files, animation files, animator controllers, prefabs, config files, metadata files, and in-memory caches.

### High-Level Approach

1. User right-clicks an asset in the AssetBrowserPanel, selects "Rename"
2. An inline text input appears over the filename (or a modal dialog)
3. Before renaming, the system scans the project to find all files referencing the old path
4. A confirmation dialog shows what will be affected
5. The rename executes: disk rename, metadata rename, file patching, cache invalidation
6. The editor refreshes its state

---

## 2. Current System Analysis

### 2.1 Asset Identification: Path-Based (No UUIDs)

Every asset is identified by its **relative path** from `gameData/assets/` (e.g., `sprites/player.png`). This path is used as the key everywhere:
- `ResourceCache` keys
- `AssetManager.resourcePaths` (Object → path)
- `AssetManager.cachedFullPaths` (normalized path → full path)
- `AssetManager.cachedTypes` (path → Class)
- JSON serialization in all file types
- `AssetMetadata` file path derivation

There is no UUID or stable ID system. **The path IS the identity.**

### 2.2 Hot-Reload Support by Loader

| Loader | Hot-Reload | Asset Types |
|--------|-----------|-------------|
| TextureLoader | YES | `.png`, `.jpg`, `.jpeg`, `.bmp`, `.tga` |
| SpriteLoader | YES | `.png`, `.jpg`, `.jpeg`, `.bmp`, `.tga` |
| ShaderLoader | YES | `.glsl`, `.shader`, `.vs`, `.fs` |
| AnimationLoader | YES | `.anim`, `.anim.json` |
| AnimatorControllerLoader | YES | `.animator.json` |
| FontLoader | YES | `.font`, `.font.json`, `.ttf`, `.otf` |
| **AudioClipLoader** | **NO** | `.wav`, `.ogg`, `.mp3` |
| **SceneDataLoader** | **NO** | `.scene`, `.scene.json` |
| **JsonPrefabLoader** | **NO** | `.prefab.json`, `.prefab` |

### 2.3 Cache Invalidation Limitations

The current API has **no public method to invalidate a single cache entry**. Relevant internals:

- `ResourceCache.remove(String path)` exists but is **not exposed** in `Assets` or `AssetContext`
- `AssetManager.unregisterResource(Object)` only removes path tracking, not the cache entry
- `Assets.load()` returns the **cached version** without checking if the file still exists on disk
- There is no `Assets.invalidate(String path)` method

**A new cache invalidation API is required** to support rename properly.

### 2.4 Where Asset Paths Appear

#### On Disk — File Types Containing Asset Path References

| File Type | Location | Path Format | Notes |
|-----------|----------|-------------|-------|
| Scene files (`.scene`) | `gameData/scenes/` | `FullClassName:path#index` or `path` | Component properties + binary tilemap data |
| Animation files (`.anim.json`) | `gameData/assets/animations/` | `path#index` | `frames[].sprite` |
| Animator controllers (`.animator.json`) | `gameData/assets/animators/` | `path` | `states[].animation`, `states[].animations.{DIR}` |
| Prefab files (`.prefab.json`) | `gameData/assets/prefabs/` | `FullClassName:path#index` | Component properties + `previewSpritePath` |
| Metadata files (`.meta`) | `gameData/.metadata/` | Derived from asset path | Filename mirrors asset path |
| `rendering.json` | `gameData/config/` | `path` | `transitions[].lumaSprite` (8 entries) |
| `editor.json` | `editor/config/` | Full path | `defaultUiFont` |

#### In Memory — Runtime Structures

| Structure | Key/Value | Location |
|-----------|-----------|----------|
| `ResourceCache` | `path → Object` | `AssetManager` field |
| `resourcePaths` | `Object → path` | `AssetManager` field |
| `cachedFullPaths` | `normalizedPath → fullPath` | `AssetManager` field |
| `cachedTypes` | `path → Class` | `AssetManager` field |
| `ThumbnailCache` | `path → OpenGL texture ID` | `AssetBrowserPanel` |
| `SpriteLoader.metadataCache` | `path → SpriteMetadata` | `SpriteLoader` field |
| `SpriteLoader.gridCache` | `path → SpriteGrid` | `SpriteLoader` field |
| `TilesetRegistry` | `path → TilesetEntry` | Editor tileset system |
| `AssetBrowserPanel.selectedAsset` | `AssetEntry.path` | Panel state |
| `AssetBrowserPanel.expandedSpritesheets` | `Set<String>` of paths | Panel state |
| `AssetBrowserPanel.multipleModeCache` | `Map<String, Boolean>` | Panel state |

---

## 3. The Non-Hot-Reloadable Asset Problem

This is the central design challenge. Three asset types **do not support hot-reload**: AudioClip, SceneData, and JsonPrefab. When a rename operation changes the path of one of these assets, or changes the path of an asset *referenced by* one of these, the system cannot simply mutate the cached object in place.

### 3.1 Scenarios and Their Consequences

#### Scenario A: Renaming a hot-reloadable asset (e.g., a sprite PNG)

**Direct rename of `sprites/player.png` → `sprites/hero.png`:**
1. The `Sprite` and `Texture` objects are cached under the old path
2. SpriteLoader supports hot-reload, so we *could* reload — but the old path no longer exists on disk
3. Hot-reload won't help here because it reloads from the *same* path. We need a **re-key** operation, not a reload.

**Impact on non-hot-reloadable assets that reference it:**
- A `.prefab.json` may contain `"sprite": "...Sprite:sprites/player.png#0"` — the file on disk gets patched, but the **in-memory `JsonPrefab` object** still holds a `Sprite` instance whose `resourcePaths` entry points to the old path.
- However, since the actual `Sprite` object reference is shared (same instance from cache), and we re-key it in the cache, the `JsonPrefab`'s component fields still hold valid object references. **Serialization is the concern**: when saving the prefab, `AssetReferenceTypeAdapterFactory` looks up `resourcePaths` to get the path string. If we update `resourcePaths` to point to the new path, serialization produces the correct new path.

**Conclusion:** For hot-reloadable assets, re-keying the cache + updating `resourcePaths` is sufficient. The object reference remains valid; only the path mapping changes.

#### Scenario B: Renaming a non-hot-reloadable asset (e.g., an audio clip)

**Direct rename of `audio/sfx/jump.wav` → `audio/sfx/player_jump.wav`:**
1. The `AudioClip` object is cached under the old path
2. AudioClipLoader does NOT support hot-reload
3. The AudioClip holds an OpenAL buffer — the actual audio data in GPU/driver memory is fine, it doesn't depend on the file path
4. We just need to re-key the cache and update `resourcePaths`

**Same conclusion as Scenario A.** The audio data is already loaded into memory. The file path is only used for:
- Cache lookup (re-key solves this)
- Serialization (updating `resourcePaths` solves this)
- Future reloads (the new path on disk is correct)

**Hot-reload is irrelevant to the rename operation.** We are not reloading the asset's data — we are remapping its identity.

#### Scenario C: Renaming an asset referenced by an open scene

**Rename `sprites/player.png` while a scene using it is open in the editor:**
1. The scene's `SpriteRenderer` components hold live `Sprite` object references
2. These references remain valid (same Java object in memory)
3. The `resourcePaths` map is updated: `spriteObject → "sprites/hero.png"`
4. When the scene is saved, the serializer looks up `resourcePaths` and writes the new path
5. The scene file on disk was already patched during the rename operation

**Risk:** If the scene was already dirty (unsaved changes), the user might save and overwrite our disk patches, or they might not save and lose the patched paths. **We must update both**: the on-disk file AND the in-memory path mappings.

#### Scenario D: Renaming a `.prefab.json` file itself

**Rename `prefabs/chest.prefab.json` → `prefabs/treasure_chest.prefab.json`:**
1. The `JsonPrefab` object is cached under the old path
2. `PrefabRegistry` indexes prefabs by their `id` field, NOT by file path
3. Scene files reference prefabs by `prefabId`, not by file path
4. The prefab's `sourcePath` (transient field) needs updating
5. Re-key the cache, update `resourcePaths`, update `PrefabRegistry` internal path tracking

**Lower risk** — prefab instances in scenes use `prefabId` (e.g., `"poke_player"`), not the file path. No scene patching needed for prefab file renames (unless the user also wants to change the prefab ID to match the new filename).

#### Scenario E: Renaming a `.scene` file

**Rename `DemoScene.scene` → `ForestVillage.scene`:**
1. `WarpZone.targetScene` in other scenes stores scene names (e.g., `"DemoScene"`)
2. `EditorConfig.recentScenes` stores scene names
3. `GameConfig.startScene` stores scene name
4. `SaveData.currentScene` in save files stores scene name
5. The currently open scene's `EditorScene.filePath` must update

**High risk** — scene names are derived from filenames and used across many systems. This is the most complex rename type.

#### Scenario F: Renaming an `.anim.json` file

**Rename `animations/player_idle_down.anim.json` → `animations/player_idle_south.anim.json`:**
1. `.animator.json` files reference it in `states[].animation` or `states[].animations.{DIR}`
2. Scene/prefab `AnimationComponent.animation` fields reference it
3. The `Animation` object is hot-reloadable but same logic applies: re-key, don't reload

**Medium risk** — must patch animator controllers and component references.

### 3.2 Key Insight

**Hot-reload capability is NOT a factor in the rename operation.** Renaming does not change the asset's data — it changes its identity (path). The required operation is:

1. **Rename file on disk** (the physical file)
2. **Re-key all caches** (ResourceCache, cachedTypes, cachedFullPaths, resourcePaths)
3. **Patch all referencing files** on disk (scenes, anims, animators, prefabs, configs)
4. **Update in-memory path mappings** so serialization produces correct paths
5. **Rename metadata file** if one exists

The live objects in memory remain perfectly valid — their data hasn't changed, only their name.

---

## 4. Reference Scanning Strategy

### 4.1 What to Scan

Before renaming, we must find every file that contains a reference to the old path. The search patterns depend on the asset type:

**For any asset path `old/path/asset.ext`:**

1. **Scene files** (`gameData/scenes/*.scene`):
   - Text search for `old/path/asset.ext` in JSON content
   - Binary-encoded tilemap data: decode Base64, search for path string, re-encode

2. **Animation files** (`gameData/assets/**/*.anim.json`):
   - Search `frames[].sprite` for paths starting with `old/path/asset.ext`

3. **Animator controllers** (`gameData/assets/**/*.animator.json`):
   - Search `states[].animation` and `states[].animations.*` values

4. **Prefab files** (`gameData/assets/**/*.prefab.json`):
   - Search component properties and `previewSpritePath`

5. **Config files**:
   - `gameData/config/rendering.json` — transition sprite paths
   - `editor/config/editor.json` — defaultUiFont

6. **Metadata files** (`gameData/.metadata/`):
   - Check if `old/path/asset.ext.meta` exists

### 4.2 Sub-Asset Path Awareness

Sprites from spritesheets use the format `path.png#index`. When renaming `sprites/sheet.png`, we must also match:
- `sprites/sheet.png#0`
- `sprites/sheet.png#1`
- `sprites/sheet.png#42`
- etc.

The search must use prefix matching: any reference starting with `old/path/asset.ext` (including `#` suffixed variants).

### 4.3 Typed Reference Format

In component serialization, asset references use the format `FullClassName:path`. For example:
```
com.pocket.rpg.rendering.resources.Sprite:sprites/player.png#0
```

The scanner must handle both bare paths and type-prefixed paths.

### 4.4 TilemapRenderer Binary Data

Tilemaps are serialized as Base64-encoded binary. Sprite paths are embedded as UTF-8 strings within this binary data. Two options:

**Option A: Decode, patch, re-encode** — fully correct but complex. Requires understanding the binary format.

**Option B: String-replace within the raw Base64-decoded bytes** — risky, could corrupt data if the path string appears in non-path contexts.

**Option C: Force a scene re-save** — load the scene via the standard deserializer, let the patched `resourcePaths` map produce correct paths during serialization, and re-save. This is the safest approach because it uses the existing serialization pipeline.

**Recommendation: Option C** for scenes that contain tilemap data referencing the renamed asset. For scenes without tilemaps, simple text replacement in the JSON is sufficient.

---

## 5. The Rename Operation — Step by Step

### 5.1 Pre-Rename Phase

1. **Validate new name**: no illegal characters, no name collision with existing file
2. **Build old path and new path**: compute the full relative paths
3. **Scan project for references**: find all files containing the old path
4. **Show confirmation dialog**: list affected files, let user confirm or cancel

### 5.2 Execution Phase

Order matters. We must ensure consistency at every step.

```
1. Rename the physical asset file on disk
2. Rename the metadata file (if exists)
3. Patch all referencing files on disk:
   a. Scene files (text replace in JSON, or re-save for tilemaps)
   b. Animation files (.anim.json)
   c. Animator controller files (.animator.json)
   d. Prefab files (.prefab.json)
   e. Config files (rendering.json, editor.json)
4. Update in-memory state:
   a. Re-key ResourceCache (remove old, put new)
   b. Update resourcePaths map (all objects pointing to old path → new path)
   c. Update cachedFullPaths and cachedTypes
   d. Invalidate ThumbnailCache for old path
   e. Invalidate SpriteLoader metadata/grid caches
   f. Update TilesetRegistry if applicable
5. If current scene references the renamed asset:
   a. Mark scene dirty (so user knows to save)
   b. Or: silently update the scene's serialized form
6. Refresh AssetBrowserPanel
7. Clear undo stack (stale path references in commands)
```

### 5.3 Post-Rename Phase

1. **Refresh the AssetBrowserPanel** — call `refresh()` to rescan
2. **Update selection** — if the renamed asset was selected, update `selectedAsset`
3. **Show status bar message** — "Renamed X references in Y files"
4. **Publish event** — `AssetRenamedEvent` for any listeners

---

## 6. Edge Cases

### 6.1 Renaming While the Affected Scene Is Open and Dirty

If the user has unsaved changes to a scene that references the renamed asset:
- We cannot just patch the `.scene` file on disk (user would lose their changes on next save)
- We cannot just update in-memory paths (the disk file would have stale paths)
- **Solution**: Update BOTH. Patch the disk file (for the path references only), AND update the in-memory `resourcePaths` so the next save produces correct paths. Mark the scene as dirty.

### 6.2 Renaming a Spritesheet Used in Tilemaps

Tilemap binary data contains embedded sprite paths. The safest approach is to:
1. If the scene is currently open: the in-memory `TilemapRenderer` holds `Sprite` objects (not paths). Updating `resourcePaths` means the next save writes correct paths.
2. If the scene is NOT open: load the scene, let the patched resource system resolve paths correctly, re-save.

### 6.3 Name Collision

If `sprites/hero.png` already exists and the user tries to rename `sprites/player.png` to `sprites/hero.png`:
- Block the rename with an error message
- Do not offer to overwrite

### 6.4 Cross-Reference Chains

Renaming `sprites/player.png` doesn't change the paths of `.anim.json` files that reference it — it only changes the sprite path *inside* those files. No cascading rename is needed.

### 6.5 Scene File Rename (Special Case)

Renaming a `.scene` file requires additional work:
- Patch `WarpZone.targetScene` in all other scenes
- Update `EditorConfig.recentScenes`
- Update `GameConfig.startScene` if it matches
- Update `EditorScene.filePath` if the scene is currently open
- Save files referencing old scene name would break (warn user)

### 6.6 Extension Change

The user should NOT be able to change the file extension (e.g., `.png` → `.jpg`). The rename UI should either:
- Only allow editing the filename stem (not the extension)
- Or validate that the extension hasn't changed

---

## 7. New Infrastructure Required

### 7.1 Cache Invalidation API

Add to `AssetContext` / `Assets` facade:
```java
// Remap a cached asset from one path to another without reloading
void remapAssetPath(String oldPath, String newPath);
```

This method would:
1. Remove from `ResourceCache` under old key, insert under new key
2. Update all entries in `resourcePaths` pointing to old path
3. Update `cachedFullPaths` and `cachedTypes`
4. Handle sub-asset paths (e.g., `old.png#0` through `old.png#N`)

### 7.2 Project-Wide Reference Scanner

New utility class: `AssetReferenceScanner`
```java
// Scans all project files for references to the given asset path
// Returns a list of (filePath, lineNumber, matchContext) for preview
List<AssetReference> findReferences(String assetPath);
```

### 7.3 Project-Wide Reference Patcher

New utility class: `AssetReferencePatcher`
```java
// Replaces all occurrences of oldPath with newPath in the given file
// Handles typed references (ClassName:path), sub-assets (path#N), and plain paths
int patchFile(Path file, String oldPath, String newPath);
```

### 7.4 Confirmation Dialog

New editor dialog showing:
- Old name → New name
- List of affected files with reference counts
- "Rename" / "Cancel" buttons

### 7.5 AssetRenamedEvent

New event for `EditorEventBus`:
```java
public record AssetRenamedEvent(String oldPath, String newPath, int filesPatched)
    implements EditorEvent {}
```

---

## 8. Undo Considerations

Asset rename is a **multi-file, disk-level operation**. Making it fully undoable would require:
- Storing the old file name
- Storing a map of every patched file's old content (or old path → new path for re-patching)
- Reversing the disk rename
- Reversing all file patches
- Re-keying all caches back

**Recommendation: Do not make rename undoable.** Instead:
- Require explicit confirmation before executing
- Clear the undo stack after rename (existing commands may reference stale paths)
- The user can use git to revert if needed

---

## 9. UI Design

### 9.1 Context Menu Addition

In `AssetBrowserPanel`, add to the existing right-click menu (both list and grid views):
```
[Sprite Editor...]     (existing, conditional)
─────────────────
[Rename]               (new — always available)
[Copy Path]            (existing)
```

Keyboard shortcut: `F2` when an asset is selected (standard rename shortcut).

### 9.2 Inline Rename

When "Rename" is activated:
1. Replace the filename label with an `ImGui.inputText()` pre-filled with the current name (without extension)
2. Auto-select the text
3. On Enter: validate and proceed to confirmation
4. On Escape: cancel rename
5. On click-away: cancel rename

### 9.3 Confirmation Dialog

```
┌─ Rename Asset ──────────────────────────────┐
│                                              │
│  sprites/player.png  →  sprites/hero.png     │
│                                              │
│  The following files will be updated:        │
│                                              │
│  Scenes:                                     │
│    ● Test.scene (3 references)               │
│    ● DemoScene.scene (1 reference)           │
│                                              │
│  Animations:                                 │
│    ● player_idle_down.anim.json (1 ref)      │
│    ● player_walk_left.anim.json (1 ref)      │
│                                              │
│  Prefabs:                                    │
│    ● poke_player.prefab.json (2 references)  │
│                                              │
│  Metadata:                                   │
│    ● player.png.meta (will be renamed)       │
│                                              │
│  Total: 7 references in 4 files              │
│                                              │
│                     [ Cancel ]  [ Rename ]   │
└──────────────────────────────────────────────┘
```

---

## 10. Files to Modify / Create

| File | Change |
|------|--------|
| `editor/panels/AssetBrowserPanel.java` | Add "Rename" context menu item, inline rename input, F2 shortcut |
| `resources/AssetContext.java` | Add `remapAssetPath()` to interface |
| `resources/AssetManager.java` | Implement `remapAssetPath()` — re-key cache + path maps |
| `resources/Assets.java` | Expose `remapAssetPath()` in static facade |
| `resources/AssetMetadata.java` | Add `rename(oldPath, newPath)` utility |
| `editor/assets/AssetReferenceScanner.java` | **NEW** — scans project for path references |
| `editor/assets/AssetReferencePatcher.java` | **NEW** — patches files to update path references |
| `editor/assets/AssetRenameOperation.java` | **NEW** — orchestrates the full rename flow |
| `editor/ui/dialogs/RenameAssetDialog.java` | **NEW** — confirmation dialog |
| `editor/events/AssetRenamedEvent.java` | **NEW** — event for post-rename notification |
| `editor/assets/ThumbnailCache.java` | Add `rekey(oldPath, newPath)` or just invalidate |
| `resources/loaders/SpriteLoader.java` | Add cache re-key for `metadataCache` and `gridCache` |
| `editor/tileset/TilesetRegistry.java` | Handle path updates in registry maps |

---

## 11. Testing Strategy

### Unit Tests
- `AssetReferenceScannerTest`: verify detection in all file formats (scene JSON, anim JSON, animator JSON, prefab JSON, typed references, sub-asset references)
- `AssetReferencePatcherTest`: verify correct patching (plain path, typed path, sub-asset paths, no false positives)
- `AssetManager.remapAssetPath` test: verify cache re-keying and path map updates

### Integration Tests
- Rename a sprite → verify scene files updated correctly
- Rename a sprite used in tilemaps → verify tilemap binary data updated
- Rename an animation → verify animator controllers updated
- Rename with open scene → verify in-memory state consistent

### Manual Testing
- Rename asset, save scene, close and reopen → no broken references
- Rename asset used in multiple scenes → all scenes updated
- Try to rename to existing name → blocked with error
- Cancel rename → nothing changes
- Rename spritesheet → all `#index` references updated

---

## 12. Phases

### Phase 1: Core Infrastructure
- [ ] Add `remapAssetPath()` to AssetContext/AssetManager/Assets
- [ ] Implement cache re-keying (ResourceCache, resourcePaths, cachedFullPaths, cachedTypes)
- [ ] Add sub-asset path awareness (handle `#index` variants)
- [ ] Add SpriteLoader / TilesetRegistry cache invalidation helpers
- [ ] Unit tests for cache remapping

### Phase 2: Reference Scanner & Patcher
- [ ] Implement `AssetReferenceScanner` — scan scenes, anims, animators, prefabs, configs
- [ ] Implement `AssetReferencePatcher` — text replacement with typed/sub-asset awareness
- [ ] Handle tilemap binary data in scenes (Option C: re-save approach)
- [ ] Unit tests for scanner and patcher

### Phase 3: Rename Operation & UI
- [ ] Implement `AssetRenameOperation` — orchestration class
- [ ] Add "Rename" to context menu in `AssetBrowserPanel` (both list and grid views)
- [ ] Implement inline rename text input with Enter/Escape handling
- [ ] Add F2 keyboard shortcut
- [ ] Implement `RenameAssetDialog` — confirmation with affected file list
- [ ] Implement `AssetRenamedEvent` and publish it
- [ ] Clear undo stack after rename
- [ ] Refresh panel state after rename

### Phase 4: Edge Cases & Polish
- [ ] Handle scene-file rename (WarpZone.targetScene, recentScenes, GameConfig.startScene)
- [ ] Handle currently-open-and-dirty scene
- [ ] Metadata file rename
- [ ] Extension change prevention
- [ ] Name collision detection
- [ ] Status bar feedback message
- [ ] Error handling (partial failure rollback?)

### Phase 5: Code Review
- [ ] Review all changes
- [ ] Verify no regressions in scene save/load
- [ ] Verify no regressions in asset browser
- [ ] Update `.claude/reference/` docs if needed
