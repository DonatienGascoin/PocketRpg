# Tileset Palette Guide

> **Summary:** Select tiles from tilesets and paint them onto tilemap layers using brush, fill, rectangle, eraser, and picker tools.

---

## Table of Contents

1. [Quick Reference](#quick-reference)
2. [Overview](#overview)
3. [Opening the Panel](#opening-the-panel)
4. [Interface Overview](#interface-overview)
5. [Workflows](#workflows)
6. [Painting Tools](#painting-tools)
7. [Keyboard Shortcuts](#keyboard-shortcuts)
8. [Tips & Best Practices](#tips--best-practices)
9. [Troubleshooting](#troubleshooting)
10. [Related](#related)

---

## Quick Reference

| Task | How |
|------|-----|
| Open palette | Press **F1** or **View > Tileset Palette** |
| Select a tile | Click a tile in the grid |
| Select a pattern | Click and drag across multiple tiles |
| Paint tiles | Select Brush tool (**B**), left-click in scene |
| Erase tiles | Select Eraser tool (**E**), left-click in scene |
| Fill area | Select Fill tool (**F**), click a tile in scene |
| Draw rectangle | Select Rectangle tool (**R**), click-drag in scene |
| Pick from scene | Select Picker tool (**I**), click a tile in scene |
| Change brush size | Press **+** / **-** or use the slider |
| Switch layer | Use the layer dropdown in the palette |

---

## Overview

The Tileset Palette is the tile painting workspace. When open and a tilemap layer is selected, you can paint tiles onto the scene using various tools.

**The painting workflow:**
1. Select **Tilemap Layers** in the [Hierarchy](hierarchy-panel-guide.md) to enter painting mode
2. Open the Tileset Palette (**F1**) if not already open
3. Choose a layer from the dropdown
4. Select a tileset
5. Click tiles in the grid to select them
6. Paint in the [Scene View](scene-view-guide.md) using the active tool

The palette has two layout modes — vertical (default) and horizontal — switchable for different screen setups.

---

## Opening the Panel

- Press **F1** to toggle
- Or use **View > Tileset Palette** from the menu bar

---

## Interface Overview

```
┌──────────────────────────────────┐
│ Tileset Palette                  │
├──────────────────────────────────┤
│ ⚠ Select a layer to start       │
│                                  │
│ Layer: [Ground ▼] [✓]           │
├──────────────────────────────────┤
│ Tileset: [overworld ▼]          │
├──────────────────────────────────┤
│ ┌──┬──┬──┬──┬──┬──┬──┬──┐      │
│ │  │  │▓▓│  │  │  │  │  │      │
│ ├──┼──┼──┼──┼──┼──┼──┼──┤      │
│ │  │  │  │  │  │  │  │  │      │
│ ├──┼──┼──┼──┼──┼──┼──┼──┤      │
│ │  │  │  │  │  │  │  │  │      │
│ └──┴──┴──┴──┴──┴──┴──┴──┘      │
├──────────────────────────────────┤
│ Selection: Tile 14               │
│ Tool Size [===●======] 3         │
├──────────────────────────────────┤
│ Tile Size [===●======]           │
└──────────────────────────────────┘
```

| Element | Description |
|---------|-------------|
| **Warning banner** | Guides you if no layer is selected or not in painting mode |
| **Layer dropdown** | Select which tilemap layer to paint on. Visibility checkbox next to it. |
| **Tileset dropdown** | Choose which tileset to browse tiles from |
| **Tile grid** | Visual grid of all tiles in the selected tileset. Click to select. |
| **Selection info** | Shows current selection (single tile or pattern dimensions) |
| **Tool Size slider** | Brush/eraser size (1-10 tiles) |
| **Tile Size slider** | Zoom level of the tile grid display |

---

## Workflows

### Setting Up for Painting

1. Click **Tilemap Layers** in the Hierarchy (or select a layer from the palette dropdown)
2. Open the Tileset Palette (**F1**)
3. Select a tileset from the dropdown
4. Click a tile to select it — the Brush tool activates automatically

### Selecting Tiles

**Single tile:**
- Click any tile in the grid

**Pattern selection (multi-tile):**
- Click and drag across multiple tiles
- The selection becomes a "stamp" pattern
- Paint with it to stamp the entire pattern at once

**Clear selection:**
- Press **Escape** when the palette is focused

### Switching Layers

1. Use the **Layer dropdown** at the top of the palette
2. Select a layer name, or choose **None** to deselect
3. The visibility checkbox toggles the selected layer's visibility

### Picking Tiles from the Scene

1. Press **I** to activate the Tile Picker tool
2. Click any painted tile in the Scene View
3. The tile (and its tileset) is selected in the palette
4. Switch back to Brush (**B**) to continue painting

---

## Painting Tools

### Brush (B)

Paints the selected tile or pattern at the cursor position. Hold left-click and drag for continuous painting.

- **Size**: Adjustable from 1-10 tiles (for single tile selections)
- **Pattern mode**: When a multi-tile pattern is selected, stamps the entire pattern regardless of brush size

### Eraser (E)

Removes tiles at the cursor position. Hold left-click and drag for continuous erasing.

- **Size**: Same as brush size (synced)

### Fill (F)

Flood-fills a contiguous region of matching tiles with the selected tile.

1. Select a tile in the palette
2. Press **F** for Fill tool
3. Click a tile in the scene — all connected tiles of the same type are replaced

### Rectangle (R)

Draws a filled rectangle of the selected tile.

1. Select a tile in the palette
2. Press **R** for Rectangle tool
3. Click and drag in the scene to define the rectangle
4. Release to fill the area

### Picker (I)

Samples a tile from the scene and selects it in the palette.

1. Press **I** for Picker tool
2. Click any painted tile in the scene
3. The tile is selected in the palette and the tileset switches if needed

---

## Keyboard Shortcuts

| Shortcut | Action |
|----------|--------|
| F1 | Toggle Tileset Palette |
| B | Brush tool |
| E | Eraser tool |
| F | Fill tool |
| R | Rectangle tool |
| I | Picker tool |
| + | Increase brush size |
| - | Decrease brush size |
| Escape | Clear tile selection (when palette focused) |

> All shortcuts are rebindable via **Edit > Shortcuts**. Tool shortcuts require a tilemap layer to be selected. See [Shortcuts Guide](shortcuts-guide.md).

---

## Tips & Best Practices

- **Use patterns for terrain**: Select a 3x3 grass pattern and stamp it for natural-looking terrain
- **Layer organization**: Use separate layers for ground, decorations, and overlays
- **Pick before painting**: Use the Picker (I) to quickly match existing tiles
- **Adjust brush size**: Large brush sizes speed up filling open areas
- **Lock layers**: Lock layers you're not editing to prevent accidental changes
- **Hide layers**: Toggle visibility to see what's on each layer

---

## Troubleshooting

| Problem | Solution |
|---------|----------|
| "Select a layer to start painting" | Click **Tilemap Layers** in the Hierarchy, then select a layer |
| "Select a tile or brush to resume painting" | Click a tile in the palette grid or press B for brush |
| Can't see tile grid | Select a tileset from the dropdown — it may show "None" |
| Painting on wrong layer | Check the layer dropdown — make sure the correct layer is selected |
| Tiles not appearing | Check layer visibility (checkbox next to dropdown) and z-order |
| Brush tool not working | Make sure you're clicking in the Scene View, not another panel |

---

## Related

- [Hierarchy Panel Guide](hierarchy-panel-guide.md) — Selecting tilemap layers to enter painting mode
- [Scene View Guide](scene-view-guide.md) — The viewport where painting happens
- [Collision Map Guide](collision-map-guide.md) — Painting collision types (similar tools)
- [Shortcuts Guide](shortcuts-guide.md) — All painting tool shortcuts
