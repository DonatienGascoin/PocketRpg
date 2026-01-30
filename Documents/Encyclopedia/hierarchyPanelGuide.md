# Hierarchy Panel Guide

> **Summary:** View and organize all entities in the current scene as a tree. Create, select, reparent, reorder, and delete entities.

---

## Table of Contents

1. [Quick Reference](#quick-reference)
2. [Overview](#overview)
3. [Opening the Panel](#opening-the-panel)
4. [Interface Overview](#interface-overview)
5. [Workflows](#workflows)
6. [Keyboard Shortcuts](#keyboard-shortcuts)
7. [Tips & Best Practices](#tips--best-practices)
8. [Troubleshooting](#troubleshooting)
9. [Related](#related)

---

## Quick Reference

| Task | How |
|------|-----|
| Create entity | Click **+** button or right-click → **New Entity** |
| Select entity | Click its name in the tree |
| Multi-select | Ctrl+Click or Shift+Click |
| Reparent entity | Drag and drop onto another entity |
| Reorder entity | Drag between entities (drop zones appear) |
| Delete entity | Select → press **Delete** or right-click → **Delete Selected** |
| Select camera | Click **Scene Camera** |
| Select tilemap layers | Click **Tilemap Layers** |
| Select collision map | Click **Collision Map** |

---

## Overview

The Hierarchy panel shows everything in the current scene as a tree structure. The top section has three fixed items — Scene Camera, Tilemap Layers, and Collision Map — followed by all entities organized in a parent-child tree.

Selecting an item in the Hierarchy determines what appears in the [Inspector](inspector-panel-guide.md):
- **Scene Camera** → Camera settings (position, bounds, zoom)
- **Tilemap Layers** → Layer management (add, remove, reorder, rename)
- **Collision Map** → Collision map properties
- **Entity** → Entity name, components, and their properties
- **Multiple entities** → Bulk operations (move, delete)

The Hierarchy also determines which editor mode is active:
- Selecting **Tilemap Layers** activates tile painting tools
- Selecting **Collision Map** activates collision painting tools
- Selecting an **entity** activates transform/selection tools

---

## Opening the Panel

The Hierarchy panel is open by default. If closed, reopen via **View > Hierarchy** in the menu bar.

---

## Interface Overview

```
┌──────────────────────────────────┐
│ Hierarchy                    [+] │
├──────────────────────────────────┤
│ 🎬 SceneName                     │
├──────────────────────────────────┤
│ 📷 Scene Camera                  │
│ 🗂 Tilemap Layers (3)            │
│ 🧱 Collision Map                 │
├──────────────────────────────────┤
│ ▸ 🎮 Player                      │
│   └─ 🎮 Shadow                   │
│ 🎮 NPC_Merchant                  │
│ ▸ 🎮 Trees                       │
│   ├─ 🎮 Oak_1                    │
│   └─ 🎮 Oak_2                    │
│ 📐 UICanvas                      │
│   └─ 📐 HealthBar                │
│                                  │
│ (empty space — click to deselect)│
└──────────────────────────────────┘
```

| Element | Description |
|---------|-------------|
| **Scene name** | Current scene file, shown at the top |
| **+ button** | Opens the entity creation menu |
| **Scene Camera** | Fixed item — click to edit camera settings in Inspector |
| **Tilemap Layers (N)** | Fixed item — click to manage tilemap layers and activate painting |
| **Collision Map** | Fixed item — click to edit collision properties and activate collision tools |
| **Entity tree** | All entities with expand/collapse for parent-child relationships |
| **Entity icons** | 🎮 for game entities, 📐 for UI entities |

---

## Workflows

### Creating Entities

**Method 1: + Button**
1. Click the **+** button in the top-right corner
2. Choose from the menu:
   - **New Entity** — Empty entity with just a Transform
   - **Create UI** submenu:
     - **Canvas** — Root UI container
     - **Panel** — UI panel/container
     - **Image** — UI image element
     - **Button** — UI button element
     - **Text** — UI text element

**Method 2: Right-click**
1. Right-click empty space in the Hierarchy
2. Same menu as the + button

**Method 3: Drag from Asset Browser**
1. Drag a sprite or prefab from the [Asset Browser](asset-browser-guide.md)
2. Drop it into the Hierarchy or Scene View
3. An entity is created with the appropriate components

### Selecting Entities

- **Single select**: Click an entity name
- **Multi-select**: Ctrl+Click to toggle individual entities
- **Range select**: Shift+Click to select a range
- **Deselect all**: Click empty space below the entity list

### Reparenting Entities

1. Click and drag an entity
2. Drop it onto another entity to make it a child
3. The child entity inherits the parent's transform

### Reordering Entities

1. Click and drag an entity
2. Drop zones appear between entities (thin horizontal lines)
3. Drop on a zone to reorder without changing the parent

### Deleting Entities

- **Single**: Select → press **Delete**
- **Multiple**: Multi-select → right-click → **Delete Selected**
- All deletions are undoable with **Ctrl+Z**

### Switching Editor Modes

Click one of the fixed items to switch modes:

| Click | Mode activated | Tools available |
|-------|---------------|-----------------|
| **Scene Camera** | Camera editing | Camera bounds gizmo |
| **Tilemap Layers** | Tile painting | Brush, Eraser, Fill, Rectangle, Picker |
| **Collision Map** | Collision painting | Collision Brush, Eraser, Fill, Rectangle, Picker |
| **Any entity** | Entity editing | Selection, Move, Rotate, Scale |

---

## Keyboard Shortcuts

| Shortcut | Action |
|----------|--------|
| Delete | Delete selected entity/entities |
| Escape | Deselect all |
| Ctrl+Z | Undo |
| Ctrl+Shift+Z | Redo |
| Ctrl+D | Duplicate |

> See the [Shortcuts Guide](shortcuts-guide.md) for all editor shortcuts.

---

## Tips & Best Practices

- **Use parent entities for grouping**: Create an empty entity named "Trees" or "NPCs" and parent related entities under it
- **Naming convention**: Use descriptive names — "NPC_Merchant" is better than "Entity_42"
- **Order matters for rendering**: Entities lower in the list render on top (higher z-order)
- **Click empty space to deselect**: Useful when you want to stop editing an entity
- **UI entities need a Canvas parent**: Panel, Image, Button, and Text must be children of a Canvas entity

---

## Troubleshooting

| Problem | Solution |
|---------|----------|
| Entity not visible in scene | Check z-order (Transform z position) and that SpriteRenderer has a sprite assigned |
| Can't drag entity | Make sure you're dragging from the entity name, not the expand arrow |
| Entity disappeared after reparenting | Expand the new parent — the entity is now a child |
| Tilemap tools not working | Click **Tilemap Layers** in the Hierarchy first to activate painting mode |
| Collision tools not working | Click **Collision Map** in the Hierarchy first |

---

## Related

- [Inspector Panel Guide](inspector-panel-guide.md) — Editing selected entity properties
- [Asset Browser Guide](asset-browser-guide.md) — Dragging assets into the hierarchy
- [Scene View Guide](scene-view-guide.md) — Visual editing in the viewport
- [Tileset Palette Guide](tileset-palette-guide.md) — Tile painting tools
- [Collision Map Guide](collision-map-guide.md) — Collision editing
