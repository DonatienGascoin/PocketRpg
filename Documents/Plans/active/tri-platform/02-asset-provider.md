# Plan 2: Asset Provider Abstraction

**Goal:** Replace all `java.nio.file` / `java.io.File` usage in game code with `AssetProvider` and `SaveProvider` interfaces.

**Prerequisites:** Plan 0 (go decision). **Parallelizable with Plans 1 and 3.**

**Status:** Not started

---

## Dependency Graph

```
Plan 0 (go/no-go)
   |
   v
Plan 1    Plan 2 (this plan)    Plan 3    ← parallel, different files
   |         |                    |
   +----+----+--------------------+
        |
        v
     Plan 4
```

---

## Deliverables

### 1. `AssetProvider` interface

Read-only access to game assets (textures, shaders, audio, JSON, etc.):

```java
public interface AssetProvider {
    byte[] readBytes(String path);
    String readString(String path);
    boolean exists(String path);
}
```

- Paths are forward-slash relative (e.g., `"textures/player.png"`)
- No directory listing — assets are known at build time
- Throws on missing asset (fail-fast)

### 2. `SaveProvider` interface

Read-write access to persistent save data:

```java
public interface SaveProvider {
    void save(String key, byte[] data);
    byte[] load(String key);
    boolean exists(String key);
    void delete(String key);
}
```

- Key-based, not path-based (abstracts over filesystem vs localStorage)
- Returns `null` from `load()` if key doesn't exist

### 3. `FileSystemAssetProvider` implementation

- Desktop implementation using `java.nio.file`
- Resolves paths relative to a base directory (e.g., `gameData/`)
- All `Files`, `Paths`, `FileInputStream` usage contained here

### 4. `FileSystemSaveProvider` implementation

- Desktop implementation using `java.nio.file`
- Saves to a configurable directory (e.g., `saves/`)
- Handles directory creation, atomic writes

### 5. Game code refactoring (14+ files)

All non-editor game code refactored to use `AssetProvider` / `SaveProvider` instead of direct filesystem access.

### 6. `PlatformFactory` extension

- `createAssetProvider()` method added
- `createSaveProvider()` method added

---

## Key Files to Refactor (~17)

| File | Current Usage | Refactor To |
|------|--------------|-------------|
| `AssetManager` | Orchestrates loading, may use `Files` | Use `AssetProvider` |
| `AssetMetadata` | Reads `.metadata/` JSON files | Use `AssetProvider` |
| `TextureLoader` | Reads image files | Use `AssetProvider.readBytes()` |
| `ShaderLoader` | Reads `.glsl` files | Use `AssetProvider.readString()` |
| `AudioLoader` | Reads audio files | Use `AssetProvider.readBytes()` |
| `SceneLoader` | Reads scene JSON | Use `AssetProvider.readString()` |
| `AnimationLoader` | Reads animation JSON | Use `AssetProvider.readString()` |
| `TilesetLoader` | Reads tileset data | Use `AssetProvider` |
| `MapLoader` | Reads map data | Use `AssetProvider` |
| `ConfigLoader` | Reads config JSON | Use `AssetProvider.readString()` |
| `AudioConfig` | Reads audio config | Use `AssetProvider` |
| `MusicConfig` | Reads music config | Use `AssetProvider` |
| `SaveManager` | Reads/writes save files | Use `SaveProvider` |
| `FileUtils` | Utility file operations | Refactor or remove |
| `JsonPrefabLoader` | Reads prefab JSON | Use `AssetProvider.readString()` |
| `Shader` | Reads shader source files | Use `AssetProvider.readString()` |
| `Font` | Reads font data | Use `AssetProvider` |

---

## Design Decisions

### Path format
- Always forward-slash separated: `"textures/player.png"`
- Relative to asset root — no absolute paths in game code
- `AssetProvider` implementations handle OS path conversion internally

### Error handling
- `AssetProvider.readBytes()` throws `AssetNotFoundException` (unchecked) on missing asset
- `SaveProvider.load()` returns `null` on missing key (saves may not exist yet)
- No silent fallbacks — fail fast on missing assets

### No directory listing
- Game code never needs to enumerate files at runtime
- Asset manifests (future Plan 5) handle web preloading
- Simplifies web implementation (no directory listing via HTTP)

### Editor exemption
- Editor code may continue using `java.nio.file` directly
- Editor is desktop-only and needs filesystem features (file dialogs, watching, etc.)
- Only game/core code is refactored

---

## Success Criteria

- [ ] Zero `java.nio.file` or `java.io.File` imports in non-editor game files (except FS implementations)
- [ ] Desktop game loads and runs identically
- [ ] All existing tests pass
- [ ] `AssetProvider` and `SaveProvider` available via `PlatformFactory`
- [ ] Save/load cycle works through `SaveProvider`
- [ ] All asset loaders use `AssetProvider` for file access

---

## Implementation Order

1. Define `AssetProvider` and `SaveProvider` interfaces
2. Implement `FileSystemAssetProvider` and `FileSystemSaveProvider`
3. Extend `PlatformFactory` with factory methods
4. Refactor `Shader` (reads GLSL source files)
5. Refactor `AssetManager` + all `*Loader` classes
6. Refactor `ConfigLoader`, `AudioConfig`, `MusicConfig`
7. Refactor `SaveManager`
8. Refactor `FileUtils` (remove or reduce to editor-only)
9. Refactor remaining files (`Font`, `JsonPrefabLoader`, etc.)
10. Final sweep: verify zero `java.nio.file` / `java.io.File` in game code
11. Run full game to verify identical behavior
