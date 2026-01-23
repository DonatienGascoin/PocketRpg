# Architecture Reference

## Entry Points

- `Main.java` → `GameApplication` - Game runtime
- `EditorApplication` - Scene editor with play mode

## Core Systems

### Component-Based Entity System
- `GameObject` - Entity container with parent-child hierarchy
- `Component` - Base class with lifecycle hooks (`onStart`, `update`, `lateUpdate`, `onDestroy`)
- `Transform` - Built-in component for position/rotation/scale (auto-added, cannot be removed)

### Rendering Pipeline (`rendering/pipeline/`)
- `RenderPipeline` - Unified rendering: scene → post-fx → UI → overlays
- `SceneRenderer` - Handles tilemaps and sprite batching with culling
- `PostProcessor` - Post-processing effects chain

### Scene Management (`scenes/`)
- `Scene` - Contains GameObjects and manages component caches for renderers/UI
- `SceneManager` - Loading/unloading with `RuntimeSceneLoader`
- `TransitionManager` - Fade/wipe transitions between scenes

### Input System (`input/`)
- Action-based (`Input.isActionPressed("JUMP")`) and raw key/mouse access
- Configured via `gameData/config/input.json`

### Animation System (`animation/`, `components/AnimationComponent`)
- Frame-based sprite animations with per-frame durations
- `AnimationComponent` requires `SpriteRenderer`, manages playback states: `STOPPED`, `PLAYING`, `PAUSED`, `FINISHED`
- Animation files: `gameData/assets/animations/*.anim.json`
- Sprite paths use `#` syntax: `"spritesheets/player.spritesheet#0"`

### Collision System (`collision/`)
- Custom tile-based collision for grid movement (not JBox2D)
- `CollisionSystem` - Query API for movement validation
- `CollisionType` enum: `SOLID`, `LEDGE_*`, `WATER`, `ICE`, `SAND`, `WARP`, etc.
- `TileBehavior` interface - Extensible behaviors per collision type
- `GridMovement` component - Smooth interpolation between tiles

### Asset Pipeline (`resources/`)
- `Assets` - Static facade for loading assets
- `AssetManager` - Loader registry, caching, path normalization
- `AssetLoader<T>` - Interface for custom asset types
- Metadata stored in `gameData/.metadata/` (pivots, 9-slice borders)
- Sub-asset syntax: `"spritesheets/player.spritesheet#3"` loads sprite index 3

## Serialization

Components use reflection-based serialization via `ComponentRegistry`:
- `@ComponentRef` - Auto-resolved component references (not serialized). Supports `source` parameter: `SELF`, `PARENT`, `CHILDREN`, `CHILDREN_RECURSIVE`
- `@HideInInspector` - Excludes fields from editor inspector and serialization
- `@Required` - Shows red highlight in inspector when field is null/empty
- Scene files are JSON in `gameData/scenes/`

## Editor Architecture (`editor/`)

Controller pattern with shared `EditorContext`:
- `EditorSceneController` - Scene file operations
- `EditorToolController` - Selection, brush, and other tools
- `EditorUIController` - ImGui panels (Inspector, Hierarchy, TilesetPalette)
- `PlayModeController` - Test scenes in-editor

## Key Directories

| Path | Purpose |
|------|---------|
| `src/main/java/com/pocket/rpg/core/` | GameObject, GameLoop, Camera |
| `src/main/java/com/pocket/rpg/components/` | Built-in components (SpriteRenderer, TilemapRenderer, etc.) |
| `src/main/java/com/pocket/rpg/serialization/` | Component metadata, registry, scene serialization |
| `src/main/java/com/pocket/rpg/editor/` | Scene editor |
| `gameData/` | Assets, scenes, and config files |

## Configuration Files

- `gameData/config/game.json` - Window size, scaling mode, default transitions
- `gameData/config/input.json` - Input action/axis bindings
- `gameData/config/rendering.json` - Render settings

## Testing

Tests use JUnit 5 + Mockito. Mock utilities in `src/test/java/com/pocket/rpg/testing/`:
- `HeadlessPlatformFactory` - Tests without graphics context
- `MockTimeContext` - Time simulation
- `MockInputTesting` - Input event simulation
