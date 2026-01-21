# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run Commands

```bash
# Build
mvn compile

# Run tests
mvn test
mvn test -Dtest=ClassName              # Single test class
mvn test -Dtest=ClassName#methodName   # Single test method

# Run game
mvn exec:java -Dexec.mainClass="com.pocket.rpg.Main"

# Run scene editor
mvn exec:java -Dexec.mainClass="com.pocket.rpg.editor.EditorApplication"
```

## Project Overview

PocketRpg is a 2D game engine with a scene editor, built in Java 25. It uses LWJGL for OpenGL/GLFW, ImGui for the editor UI, and JBox2D for physics.

## Architecture

### Entry Points
- `Main.java` → `GameApplication` - Game runtime
- `EditorApplication` - Scene editor with play mode

### Core Systems

**Component-Based Entity System**
- `GameObject` - Entity container with parent-child hierarchy
- `Component` - Base class with lifecycle hooks (`onStart`, `update`, `lateUpdate`, `onDestroy`)
- `Transform` - Built-in component for position/rotation/scale (auto-added, cannot be removed)

**Rendering Pipeline** (`rendering/pipeline/`)
- `RenderPipeline` - Unified rendering: scene → post-fx → UI → overlays
- `SceneRenderer` - Handles tilemaps and sprite batching with culling
- `PostProcessor` - Post-processing effects chain

**Scene Management** (`scenes/`)
- `Scene` - Contains GameObjects and manages component caches for renderers/UI
- `SceneManager` - Loading/unloading with `RuntimeSceneLoader`
- `TransitionManager` - Fade/wipe transitions between scenes

**Input System** (`input/`)
- Action-based (`Input.isActionPressed("JUMP")`) and raw key/mouse access
- Configured via `gameData/config/input.json`

### Serialization

Components use reflection-based serialization via `ComponentRegistry`:
- `@ComponentRef` - Marks fields for automatic component lookup at runtime (self/parent/children)
- `@HideInInspector` - Excludes fields from editor inspector
- Scene files are JSON in `gameData/scenes/`

### Editor Architecture (`editor/`)

Controller pattern with shared `EditorContext`:
- `EditorSceneController` - Scene file operations
- `EditorToolController` - Selection, brush, and other tools
- `EditorUIController` - ImGui panels (Inspector, Hierarchy, TilesetPalette)
- `PlayModeController` - Test scenes in-editor

### Key Directories

| Path | Purpose |
|------|---------|
| `src/main/java/com/pocket/rpg/core/` | GameObject, GameLoop, Camera |
| `src/main/java/com/pocket/rpg/components/` | Built-in components (SpriteRenderer, TilemapRenderer, etc.) |
| `src/main/java/com/pocket/rpg/serialization/` | Component metadata, registry, scene serialization |
| `src/main/java/com/pocket/rpg/editor/` | Scene editor (142 files) |
| `gameData/` | Assets, scenes, and config files |

## Testing

Tests use JUnit 5 + Mockito. Mock utilities in `src/test/java/com/pocket/rpg/testing/`:
- `HeadlessPlatformFactory` - Tests without graphics context
- `MockTimeContext` - Time simulation
- `MockInputTesting` - Input event simulation

## Adding Components

1. Extend `Component` in `com.pocket.rpg.components`
2. Add a no-arg constructor (required for serialization)
3. Override lifecycle methods as needed: `onStart()`, `update(float deltaTime)`, `onDestroy()`
4. Use `@ComponentRef` for automatic component references resolved at scene load

## Configuration Files

- `gameData/config/game.json` - Window size, scaling mode, default transitions
- `gameData/config/input.json` - Input action/axis bindings
- `gameData/config/rendering.json` - Render settings

## Analysis and Planning Documentation

- `Documents/` - Analysis and design documents
- `Documents/Plans/` - Implementation plans for features and fixes
- `Documents/Encyclopedia/` - User-facing guides for editor features

## Encyclopedia Documentation

The `Documents/Encyclopedia/` folder contains user guides explaining how to use editor features.

**After completing work on a feature:**
1. Check if a guide already exists for the feature in `Documents/Encyclopedia/`
2. If it exists, determine if it needs updates based on your changes
3. If it doesn't exist, consider if the feature warrants a guide
4. **ALWAYS ask the user before creating or updating any encyclopedia file**

Example prompt:
> "The Animation Editor guide exists at `Documents/Encyclopedia/animation-editor-guide.md`. Should I update it to reflect the new sprite picker changes?"

See `Documents/Encyclopedia/_TEMPLATE.md` for the guide structure.

## Feature Planning Guidelines

When planning or implementing new features, **integrate with existing systems** rather than reinventing functionality.

**Existing systems to leverage:**

| System | Location | Use For |
|--------|----------|---------|
| **Undo/Redo** | `editor/undo/` | Any editor action that modifies state |
| **Assets** | `resources/Assets.java` | Loading, caching, path resolution for any asset type |
| **AssetLoader** | `resources/AssetLoader.java` | New asset types (implement interface, register in AssetManager) |
| **EditorPanel** | `editor/EditorPanel.java` | Opening panels on asset double-click |
| **Shortcuts** | `editor/shortcut/` | Keyboard shortcuts for editor actions |
| **ComponentRef** | `serialization/ComponentRef.java` | Auto-resolving component dependencies |
| **Inspector** | `editor/panels/InspectorPanel.java` | Property editing uses reflection-based rendering |
| **ThumbnailCache** | `editor/assets/ThumbnailCache.java` | Asset preview images |
| **StatusBar** | `editor/ui/StatusBar.java` | User feedback messages |

**Before creating new infrastructure, ask:**
1. Does a similar system already exist?
2. Can an existing system be extended?
3. Does this follow the patterns used elsewhere in the codebase?

## Planning Mode Instructions

When exiting plan mode, always provide a bullet-point summary in the console before calling ExitPlanMode:

```
Plan updated in "<plan file path>"

**Things to change:**

1. **FileName.java**
   Short explanation of what changes

2. **AnotherFile.java**
   Short explanation of what changes
```

This gives the user a quick overview of what will be implemented without needing to read the full plan file.

**Every plan must include a code review step at the end.** After implementing a feature, perform a code review of all changed/added files and write the review to `Documents/Reviews/`.

## Inspector Field Editors

When implementing new inspector UI or field types, **prioritize using and extending existing field editors**:

| File | Purpose |
|------|---------|
| `editor/ui/fields/FieldEditors.java` | **Facade** - Entry point for all field editors |
| `editor/ui/fields/PrimitiveEditors.java` | int, float, boolean, String with undo support |
| `editor/ui/fields/VectorEditors.java` | Vector2f, Vector3f, Vector4f, color pickers |
| `editor/ui/fields/EnumEditor.java` | Enum dropdown fields |
| `editor/ui/fields/AssetEditor.java` | Asset reference fields with picker integration |
| `editor/ui/fields/TransformEditors.java` | Position/Rotation/Scale with axis coloring |
| `editor/ui/fields/ReflectionFieldEditor.java` | Auto-discovers and renders component fields |
| `editor/ui/fields/FieldEditorContext.java` | Override detection, required field highlighting |
| `editor/ui/fields/FieldEditorUtils.java` | Layout helpers, shared utilities |

**Before creating custom ImGui field rendering:**
1. Check if `FieldEditors` facade already supports the type
2. If not, add support to the appropriate specialized editor class
3. Use `FieldEditorUtils.inspectorRow()` for consistent layout
4. Follow the undo pattern: capture on activation, push on deactivation
5. Support both reflection-based and getter/setter variants

## ImGui Push/Pop Style Rules

**CRITICAL: Always use the same condition for push and pop.**

When using `pushStyleColor`/`popStyleColor` or `pushStyleVar`/`popStyleVar`, the push and pop **must** use the exact same condition value. If state changes between push and pop (e.g., a button click toggles a boolean), you get assertion errors.

**WRONG - state changes between push and pop:**
```java
if (isEnabled) {
    ImGui.pushStyleColor(ImGuiCol.Button, ...);
}
if (ImGui.button("Toggle")) {
    isEnabled = !isEnabled;  // State changes here!
}
if (isEnabled) {  // Different value than push!
    ImGui.popStyleColor();
}
```

**CORRECT - store state before the widget:**
```java
boolean wasEnabled = isEnabled;  // Store state
if (wasEnabled) {
    ImGui.pushStyleColor(ImGuiCol.Button, ...);
}
if (ImGui.button("Toggle")) {
    isEnabled = !isEnabled;  // State can change safely
}
if (wasEnabled) {  // Same value as push
    ImGui.popStyleColor();
}
```

This applies to any conditional push/pop pattern where the condition might change.