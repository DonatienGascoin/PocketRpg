# Scene Editor - Development Phases

## Overview

Standalone editor application sharing engine code with the game. Outputs `.scene` JSON files that the game loads at runtime.

**Core Features:**
- Tilemap painting (multi-layer)
- Collision layer editing
- Entity/prefab placement
- Scene transitions setup

**Architecture:**
```
EditorApplication (separate main)
    ├── EditorWindow (GLFW + ImGui context)
    ├── EditorScene (renders world, no game logic)
    ├── ToolSystem (brush, eraser, selection, entity placer)
    └── Panels (scene hierarchy, inspector, tileset palette, asset browser)

Output: .scene JSON files
Game: SceneLoader reads .scene files → builds Scene with GameObjects
```

**Important Note:** Tilemaps are currently implemented as `TilemapRenderer` components attached to GameObjects, not standalone objects. The editor and serialization must respect this architecture - each tilemap layer is a GameObject with a TilemapRenderer component.

---

## Terminology

To ensure clarity throughout the codebase and documentation:

| Term | Definition |
|------|------------|
| **Sprite** | A single image region from a texture, with UV coordinates and optional pivot |
| **SpriteSheet** | A texture divided into a grid of sprites (rows, cols, spacing, offset) |
| **Tileset** | A SpriteSheet used specifically for tilemap painting (same class, different context) |
| **Tile** | A single cell in a tilemap, references a Sprite |
| **Tilemap** | A grid data structure holding Tiles, managed by TilemapRenderer |
| **TilemapLayer** | Editor wrapper around a GameObject with TilemapRenderer component |
| **Layer** | In editor context, synonymous with TilemapLayer |
| **CollisionMap** | Separate grid storing collision types (not visual, not part of TilemapRenderer) |

---



## Implementation Order Summary

| Phase | Status | Effort | Description |
|-------|--------|--------|-------------|
| 1. Foundation | ✅ Done | Medium | Editor shell, ImGui, camera |
| 2. Serialization | ✅ Done | Medium | Scene file format, save/load infrastructure |
| 3. Tilemap Painting | ✅ Done | Large | Brush tools, layers, palette |
| 3.5. Consolidation | Pending | Medium | Complete save/load, opacity, batch fixes, tool polish |
| 4. Collision Editing | Pending | Large | Full collision system with behaviors |
| 5. Entity Placement | Pending | Medium | Prefab browser, placement, inspector, edit modes |
| 6. UX Polish | Pending | Medium | Undo/redo, logging, shortcuts |
| 7. Advanced | Pending | Large | Pivot editor, triggers, auto-tile |

---

## Complete File Structure

```
src/main/java/com/pocket/rpg/
├── collision/
│   ├── CollisionType.java
│   ├── CollisionMap.java
│   ├── CollisionLayer.java
│   ├── CollisionChunk.java
│   ├── CollisionSystem.java
│   ├── GridCollisionSystem.java
│   ├── EntityOccupancyMap.java
│   ├── MoveResult.java
│   ├── MovementModifier.java
│   ├── Direction.java
│   └── behaviors/
│       ├── TileBehavior.java
│       ├── CollisionBehaviorRegistry.java
│       ├── PassableBehavior.java
│       ├── SolidBehavior.java
│       ├── WaterBehavior.java
│       ├── IceBehavior.java
│       ├── LedgeBehavior.java
│       ├── ZLevelChangeBehavior.java
│       └── TriggerBehavior.java
├── components/
│   ├── ZLevelComponent.java
│   └── ... (existing)
├── editor/
│   ├── EditorApplication.java
│   ├── EditMode.java
│   ├── core/
│   │   ├── EditorConfig.java
│   │   ├── EditorWindow.java
│   │   ├── ImGuiLayer.java
│   │   └── FileDialogs.java
│   ├── camera/
│   │   └── EditorCamera.java
│   ├── tools/
│   │   ├── EditorTool.java
│   │   ├── ToolManager.java
│   │   ├── TileBrushTool.java
│   │   ├── TileEraserTool.java
│   │   ├── TileFillTool.java
│   │   ├── TileRectangleTool.java
│   │   ├── TilePickerTool.java
│   │   ├── CollisionBrushTool.java
│   │   ├── EntityPlacerTool.java
│   │   └── SelectionTool.java
│   ├── panels/
│   │   ├── TilesetPalettePanel.java
│   │   ├── LayerPanel.java
│   │   ├── CollisionPanel.java
│   │   ├── PrefabBrowserPanel.java
│   │   ├── EntityInspectorPanel.java
│   │   └── LogPanel.java
│   ├── commands/
│   │   ├── EditorCommand.java
│   │   ├── CommandHistory.java
│   │   └── ... (command implementations)
│   ├── scene/
│   │   ├── EditorScene.java
│   │   ├── TilemapLayer.java
│   │   ├── EditorEntity.java
│   │   └── LayerVisibilityMode.java
│   ├── serialization/
│   │   ├── EditorSceneSerializer.java
│   │   ├── TilemapComponentData.java
│   │   └── ChunkData.java
│   ├── rendering/
│   │   ├── EditorFramebuffer.java
│   │   └── EditorSceneRenderer.java
│   ├── logging/
│   │   ├── EditorLogger.java
│   │   ├── LogEntry.java
│   │   └── LogLevel.java
│   ├── ui/
│   │   ├── EditorMenuBar.java
│   │   ├── SceneViewport.java
│   │   └── StatusBar.java
│   └── tileset/
│       ├── TileSelection.java
│       ├── TilesetRegistry.java
│       └── CreateSpritesheetDialog.java
├── rendering/
│   ├── SpriteBatch.java             # Updated with vertex colors
│   └── renderers/BatchRenderer.java # Updated with opacity
└── serialization/
    ├── SceneData.java
    └── ... (existing)
```

---

## Key Integration Points

### TilemapRenderer (Existing)
- Editor uses TilemapRenderer directly for painting
- Each layer is a GameObject with TilemapRenderer component
- Chunk structure used for efficient storage and serialization

### GridMovement (Updated)
- Uses CollisionSystem instead of TilemapRenderer for collision
- Handles MovementModifier (SLIDE, JUMP, etc.)
- Integrates with ZLevelComponent for multi-level support

### Scene (Existing)
- RuntimeScene extends Scene for loaded scenes
- Uses existing addGameObject, getRenderers(), etc.

### AssetManager (Existing)
- Editor uses AssetManager for loading tilesets, sprites
- Prefab previews loaded via AssetManager

### Serializer (Existing)
- SceneSerializer uses existing Gson setup
- EditorSceneSerializer handles EditorScene ↔ SceneData conversion