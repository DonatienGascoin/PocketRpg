# Phase 3: Tilemap Painting - Implementation Plan

## Sub-phases

### Phase 3a: Layer Management + Basic Brush ✅
**Goal:** Paint tiles on layers, no palette UI yet (hardcoded test spritesheet)

**Files:**
- `TilemapLayer.java` - Wrapper for tilemap layer (GameObject + TilemapRenderer)
- `LayerPanel.java` - ImGui panel for layer management
- `EditorTool.java` - Tool interface
- `ToolManager.java` - Manages active tool, routes input
- `TileBrushTool.java` - Basic brush (uses first sprite from sheet)
- `TileEraserTool.java` - Erases tiles
- Updated `EditorScene.java` - Layer management methods
- Updated `EditorApplication.java` - Tool system integration

**Features:**
- Create/delete/rename layers
- Layer visibility modes: All / Selected Only / Selected + Dimmed
- Select active layer
- Reorder layers (zIndex)
- Basic brush tool (paints with index 0 sprite)
- Eraser tool
- Tile cursor highlight

### Phase 3b: Tileset Palette Panel
**Goal:** Visual tile selection from spritesheets

**Files:**
- `TilesetPalettePanel.java` - Shows spritesheet grid, click to select
- `TilesetRegistry.java` - Manages available spritesheets
- Updated `TileBrushTool.java` - Uses selected tile from palette

**Features:**
- Load spritesheets from assets folder
- Display as clickable grid in ImGui
- Single tile selection
- Multi-tile selection (drag to select region)
- Preview selected tile

### Phase 3c: Additional Tools + Polish
**Goal:** Complete tilemap editing toolkit

**Files:**
- `TileFillTool.java` - Flood fill
- `TileRectangleTool.java` - Rectangle fill
- `TilePickerTool.java` - Eyedropper

**Features:**
- Flood fill with bounds checking
- Rectangle selection and fill
- Pick tile from canvas to select in palette
- Brush size adjustment
- Tool shortcuts (B, E, F, R, I)

---

## Layer Visibility Modes

```java
public enum LayerVisibilityMode {
    ALL,              // All layers fully visible
    SELECTED_ONLY,    // Only active layer visible
    SELECTED_DIMMED   // Active layer full opacity, others at 50%
}
```

Affects `EditorSceneRenderer`:
- When rendering, check mode and apply alpha multiplier per layer

---

## Data Flow

```
User clicks in viewport
    ↓
SceneViewport detects click, converts to tile coords
    ↓
ToolManager.handleMouseDown(tileX, tileY, button)
    ↓
ActiveTool.onMouseDown(tileX, tileY, button)
    ↓
TileBrushTool modifies:
  1. SceneData (source of truth)
  2. Live Scene TilemapRenderer (for rendering)
    ↓
Scene re-renders with new tile
```

---

## SceneData ↔ Live Scene Sync

For Phase 3a, use **rebuild approach**:
- On any tile edit, mark layer dirty
- Before next render, rebuild affected TilemapRenderer from SceneData

Later optimization: incremental sync (only update changed tiles)

---

## Chunk Serialization (for save/load)

TilemapRenderer stores tiles in chunks. For serialization:

```json
{
  "chunks": {
    "0,0": {
      "tiles": [
        [0, 1, 2, -1, ...],  // Row 0: tile indices (-1 = empty)
        [4, 5, 6, 7, ...],   // Row 1
        ...
      ]
    },
    "1,0": { ... }
  }
}
```

Each layer needs to store:
- Spritesheet path
- Sprite dimensions (for SpriteSheet reconstruction)
- zIndex
- Chunk data (tile indices per position)
