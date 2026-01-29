# Scene View Guide

> **Summary:** The main visual editing viewport where you see and interact with your scene. Pan, zoom, select entities, paint tiles, and use transform tools.

---

## Table of Contents

1. [Quick Reference](#quick-reference)
2. [Overview](#overview)
3. [Interface Overview](#interface-overview)
4. [Camera Controls](#camera-controls)
5. [Toolbar](#toolbar)
6. [Editing Modes](#editing-modes)
7. [Grid Overlay](#grid-overlay)
8. [Gizmos](#gizmos)
9. [Keyboard Shortcuts](#keyboard-shortcuts)
10. [Tips & Best Practices](#tips--best-practices)
11. [Troubleshooting](#troubleshooting)
12. [Related](#related)

---

## Quick Reference

| Task | How |
|------|-----|
| Pan camera | Middle-mouse drag, or WASD / Arrow keys |
| Zoom | Scroll wheel (zooms toward cursor) |
| Reset zoom | Ctrl+0 |
| Select entity | Click with Selection tool (V) |
| Move entity | W tool, then drag |
| Rotate entity | E tool, then drag |
| Scale entity | R tool, then drag |
| Paint tiles | Select tilemap layer, choose tile, left-click |
| Drop asset | Drag from Asset Browser, drop in viewport |

---

## Overview

The Scene View is the central viewport where you visually edit your scene. It renders:
- Tilemap layers (the painted tile grid)
- Entity sprites (positioned in the world)
- Gizmos (selection handles, bounds, component visualizations)
- Grid overlay (tile grid lines)
- Coordinate display (cursor position)
- Debug overlays (collision visualization)

The Scene View is part of the tabbed workspace alongside the Game View, Animation Editor, Animator, and UI Designer.

---

## Interface Overview

```
┌──────────────────────────────────────────────────────────┐
│ [Scene] [Game] [Animation] [Animator] [UI Designer]      │
├──────────────────────────────────────────────────────────┤
│ [🔲Select][✋Move][🔄Rotate][📐Scale] | [Grid][Gizmos]    │
├──────────────────────────────────────────────────────────┤
│                                                          │
│    ┌─────────┐                                           │
│    │ Player  │  ← Entity with selection gizmo            │
│    └─────────┘                                           │
│         ·                                                │
│    ┌──┬──┬──┬──┬──┬──┐                                   │
│    │░░│░░│░░│░░│░░│░░│  ← Painted tiles                  │
│    ├──┼──┼──┼──┼──┼──┤                                   │
│    │░░│░░│░░│░░│░░│░░│                                   │
│    └──┴──┴──┴──┴──┴──┘                                   │
│                                                          │
│                                          Tile: (3, 2)    │
└──────────────────────────────────────────────────────────┘
```

| Element | Description |
|---------|-------------|
| **Tab bar** | Switch between Scene, Game, and editor panels |
| **Toolbar** | Tool selection and view options |
| **Viewport** | Rendered scene with entities, tiles, gizmos |
| **Coordinate display** | Current cursor tile position (bottom-right) |

---

## Camera Controls

### Mouse

| Input | Action |
|-------|--------|
| **Middle-mouse drag** | Pan the camera |
| **Scroll wheel** | Zoom toward cursor position |

### Keyboard (when Scene View is focused)

| Key | Action |
|-----|--------|
| W / Up Arrow | Pan up |
| A / Left Arrow | Pan left |
| S / Down Arrow | Pan down |
| D / Right Arrow | Pan right |

### Zoom Controls

| Shortcut | Action |
|----------|--------|
| Scroll wheel | Smooth zoom toward cursor |
| Ctrl+= | Zoom in (step) |
| Ctrl+- | Zoom out (step) |
| Ctrl+0 | Reset zoom to 1.0x |

The camera zooms toward the cursor position, so you can zoom into a specific area by pointing at it and scrolling.

---

## Toolbar

The Scene View toolbar sits above the viewport:

### Tool Buttons

| Button | Tool | Key | Description |
|--------|------|-----|-------------|
| Select | Selection | V | Click entities to select them |
| Move | Move | W | Drag to move selected entity |
| Rotate | Rotate | E | Drag to rotate selected entity |
| Scale | Scale | R | Drag to scale selected entity |

### View Options

| Button | Description |
|--------|-------------|
| Grid | Toggle tile grid overlay |
| Gizmos | Toggle gizmo rendering |

---

## Editing Modes

The Scene View behaves differently depending on what's selected in the [Hierarchy](hierarchy-panel-guide.md):

### Entity Mode

Active when an entity is selected. Use transform tools (Move, Rotate, Scale) to manipulate entities.

- **Left-click**: Select entity under cursor
- **Drag**: Move/rotate/scale depending on active tool
- **Right-click**: Context menu (if available)

### Tile Painting Mode

Active when **Tilemap Layers** is selected and the [Tileset Palette](tileset-palette-guide.md) is open with a tile selected.

- **Left-click / drag**: Paint with active tool (Brush, Fill, Rectangle)
- **Right-click**: Erase (with Brush tool)
- The grid overlay shows tile boundaries
- A tile cursor preview shows what will be painted

### Collision Painting Mode

Active when **Collision Map** is selected and the [Collision Panel](collision-map-guide.md) is open.

- **Left-click / drag**: Paint collision types
- Similar tools as tile painting (Brush, Eraser, Fill, Rectangle, Picker)

### Camera Mode

Active when **Scene Camera** is selected. The camera bounds gizmo appears, showing the game camera's visible area and boundary limits.

---

## Grid Overlay

The tile grid overlay shows cell boundaries in the viewport. It helps align entities and visualize the tile coordinate system.

- Toggle with the **Grid** button in the toolbar
- Grid lines adapt to zoom level
- Coordinate display in the bottom-right shows the tile position under the cursor

---

## Gizmos

Gizmos are visual helpers drawn over the scene. They include:

- **Selection outline**: Blue rectangle around selected entities
- **Transform handles**: Move arrows, rotation circle, scale handles
- **Component gizmos**: Bounds visualizations, trigger zones, spawn points
- **Camera bounds**: Visible area and boundary limits when camera is selected

Toggle gizmo visibility with the **Gizmos** button in the toolbar or press **G**.

See the [Gizmos Guide](gizmos-guide.md) for details on component-specific gizmos.

---

## Keyboard Shortcuts

| Shortcut | Action |
|----------|--------|
| V | Selection tool |
| W | Move tool / Pan up |
| E | Rotate tool (entity mode) / Eraser (tile mode) |
| R | Scale tool (entity mode) / Rectangle (tile mode) |
| B | Brush tool (tile/collision mode) |
| F | Fill tool (tile/collision mode) |
| I | Picker tool (tile/collision mode) |
| Delete | Delete selected entity |
| Escape | Deselect / Cancel |
| Ctrl+0 | Reset zoom |
| WASD / Arrows | Pan camera (when focused) |
| Middle-mouse | Pan camera (drag) |
| Scroll | Zoom |

> Tools E and R switch between transform tools and painting tools depending on whether a tilemap/collision layer is selected. See [Shortcuts Guide](shortcuts-guide.md).

---

## Tips & Best Practices

- **Zoom to cursor**: The scroll wheel zooms toward your cursor — point at what you want to zoom into
- **Focus matters**: Camera panning (WASD/arrows) only works when the Scene View is focused. Click the viewport first.
- **Middle-mouse for panning**: Works regardless of active tool — you can pan while painting
- **Use the grid**: Enable grid overlay when painting tiles for pixel-perfect alignment
- **Drop assets**: Drag sprites from the Asset Browser directly into the viewport to create entities at specific positions

---

## Troubleshooting

| Problem | Solution |
|---------|----------|
| Can't pan with WASD | Click the Scene View first to focus it |
| Camera panning when typing | A text field is not focused — click the text field |
| Entities not selectable | Make sure you're in entity mode (not tile/collision painting mode) |
| No gizmos visible | Check the Gizmos toggle in the toolbar |
| Scene is blank | Check zoom level (Ctrl+0 to reset), or open a scene (File > Open) |
| Tiles not visible | Check tilemap layer visibility in the palette |
| Can't paint tiles | Select Tilemap Layers in the Hierarchy, select a tile in the palette |

---

## Related

- [Hierarchy Panel Guide](hierarchy-panel-guide.md) — Selecting entities and switching modes
- [Inspector Panel Guide](inspector-panel-guide.md) — Editing selected entity properties
- [Tileset Palette Guide](tileset-palette-guide.md) — Tile painting tools
- [Gizmos Guide](gizmos-guide.md) — Component visualizations
- [Shortcuts Guide](shortcuts-guide.md) — All keyboard shortcuts
- [Play Mode Guide](play-mode-guide.md) — Testing the game in the Game View
