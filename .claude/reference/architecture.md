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
- **Hot-reload**: `Assets.reload(path)` / `Assets.reloadAll()` mutate cached assets in place
  - Loaders implement `supportsHotReload()` and `reload(existing, path)`
  - Contract: `reload()` must mutate existing instance, not return new reference
  - Supported: Texture, Sprite, Shader

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

### Gizmos System (`editor/gizmos/`)

Visual helpers drawn in Scene View to visualize component properties (bounds, pivots, radii).

**Core classes:**
- `GizmoRenderer` - Orchestrates gizmo rendering for all entities
- `GizmoContext` - Drawing API passed to components
- `GizmoColors` - Standard color constants

**Component integration:**
- `Component` implements `GizmoDrawable` and `GizmoDrawableSelected` with empty defaults
- Override `onDrawGizmos(ctx)` for always-visible gizmos
- Override `onDrawGizmosSelected(ctx)` for selection-only gizmos

**Sizing strategies:**
- World-space: `ctx.drawRect()`, `ctx.drawCircle()` - scales with zoom
- Handle-size: `ctx.getHandleSize(pixels)` - constant screen appearance (like Unity's `HandleUtility.GetHandleSize`)

**Important:** In gizmo methods, use `ctx.getTransform()` not `getTransform()` - the component's `gameObject` field is null in editor context.

### Shortcut System (`editor/shortcut/`)

Centralized, rebindable keyboard shortcuts with scope-aware dispatch.

**Core classes:**
- `ShortcutRegistry` (singleton) - Registers actions, processes input, manages bindings, persists config
- `ShortcutAction` - Defines a shortcut: ID, display name, default binding, scope, handler. Built via `ShortcutAction.builder()`
- `ShortcutBinding` - Key + modifiers (Ctrl/Shift/Alt). Supports `isPressed()` (single frame) and `isHeld()` (continuous)
- `ShortcutContext` - Per-frame UI state snapshot (focused panels, text input active, popup open)
- `ShortcutScope` - `GLOBAL`, `PANEL_FOCUSED`, `PANEL_VISIBLE`, `POPUP` (higher specificity wins conflicts)
- `KeyboardLayout` - `QWERTY` / `AZERTY` (affects default bindings for undo/redo)

**Registration flow (in `EditorApplication`):**
1. `ShortcutRegistry.loadConfigAndGetLayout()` → determines keyboard layout
2. `EditorShortcuts.registerDefaults()` → registers global/tool shortcuts
3. `EditorPanel.getAllPanels()` → each panel's `provideShortcuts()` is registered
4. `applyConfigBindings()` → applies user overrides from config file
5. `EditorShortcuts.bindHandlers()` → connects handlers to global actions

**Panel-owned shortcuts:**
- Panels override `provideShortcuts(KeyboardLayout)` to declare their own shortcuts
- Use `panelShortcut()` helper for pre-configured `PANEL_FOCUSED` scope
- Auto-registered during initialization, auto-included in config generation

**Processing:**
- `ShortcutRegistry.processShortcuts(context)` called once per frame
- Bindings sorted by modifier count (Ctrl+S checked before S)
- Consumed keys tracked to prevent modifier release triggering plain key
- `isActionHeld()` for continuous actions (camera panning)
- Suppressed during play mode and popup windows

**Config persistence:**
- `gameData/config/shortcuts.json` - Per-layout bindings, keyboard layout selection
- `generateCompleteConfig()` generates defaults for both layouts, merges with existing

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
