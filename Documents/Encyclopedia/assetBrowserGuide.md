# Asset Browser Guide

> **Summary:** Browse, search, and manage game assets. Drag assets into the scene, hierarchy, or inspector fields to use them.

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
| Navigate folders | Click folders in the tree or breadcrumb path |
| Search assets | Type in the search bar at the top |
| Use an asset | Drag it into the Scene View, Hierarchy, or an Inspector field |
| Edit an asset | Double-click to open in the appropriate editor |
| Inspect an asset | Single-click to show metadata in the Inspector |
| Expand spritesheet | Click the expand arrow on a spritesheet to see individual sprites |
| Zoom thumbnails | Use the zoom slider at the bottom |

---

## Overview

The Asset Browser shows all files in the `gameData/assets/` directory. It supports sprites, spritesheets, animations, animator controllers, audio clips, fonts, prefabs, and scene files.

The browser has two panels:
- **Left**: Folder tree for navigation
- **Right**: Asset grid with thumbnails

Assets are used by dragging them into the editor — onto the Scene View to create entities, onto the Hierarchy to add to the scene, or onto Inspector fields to assign references.

---

## Opening the Panel

The Asset Browser is open by default. If closed, reopen via **View > Asset Browser** in the menu bar.

---

## Interface Overview

```
┌────────────────┬─────────────────────────────────────────┐
│ FOLDER TREE    │ 🔍 [Search________________]              │
│                │ assets / sprites / characters            │
│ ▾ assets       ├─────────────────────────────────────────┤
│   ▸ animations │ ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐    │
│   ▸ animators  │ │ 🖼   │ │ 🖼   │ │ 🖼   │ │ 🖼   │    │
│   ▸ audio      │ │      │ │      │ │      │ │      │    │
│   ▸ fonts      │ │player│ │enemy │ │npc_1 │ │npc_2 │    │
│   ▾ sprites    │ └──────┘ └──────┘ └──────┘ └──────┘    │
│     ▸ chars    │ ┌──────┐                                │
│     ▸ tiles    │ │ 📋   │                                │
│     ▸ ui       │ │sheet │ (spritesheet, expandable)      │
│   ▸ prefabs    │ └──────┘                                │
│                ├─────────────────────────────────────────┤
│                │ Zoom: [========●===]                     │
└────────────────┴─────────────────────────────────────────┘
```

| Element | Description |
|---------|-------------|
| **Folder tree** | Navigate `gameData/assets/` directory structure |
| **Search bar** | Filter assets by name across all folders |
| **Breadcrumb** | Shows current path — click segments to navigate up |
| **Asset grid** | Thumbnails of assets in the current folder |
| **Zoom slider** | Adjust thumbnail size |

---

## Workflows

### Browsing Assets

- Click folders in the **tree** to navigate
- Click **breadcrumb segments** to go up
- Use the **search bar** to filter by name (searches all folders)
- Press **Escape** to clear the search

### Creating Entities from Assets

**Drag to Scene View:**
1. Drag a sprite or prefab from the grid
2. Drop it in the Scene View
3. An entity is created at the drop position with the appropriate components (SpriteRenderer for sprites)

**Drag to Hierarchy:**
1. Drag an asset from the grid
2. Drop it onto the Hierarchy panel
3. An entity is created and added to the scene

### Assigning Assets to Fields

1. Drag an asset from the grid
2. Drop it onto an asset field in the [Inspector](inspector-panel-guide.md)
3. The field updates to reference that asset

### Double-Click to Edit

| Asset type | Opens |
|-----------|-------|
| `.anim.json` | [Animation Editor](animation-editor-guide.md) |
| `.animator.json` | [Animator Editor](animator-guide.md) |
| `.spritesheet` | [Sprite Editor](sprite-editor-guide.md) |
| Audio clips | Audio preview |

### Inspecting Assets

1. Single-click an asset in the grid
2. The [Inspector](inspector-panel-guide.md) shows asset metadata:
   - File path and type
   - Import settings (pivot, 9-slice borders for sprites)
   - Metadata editor

### Expanding Spritesheets

Spritesheets show as a single thumbnail with an expand arrow:
1. Click the expand arrow on a spritesheet
2. Individual sprites appear as sub-items (e.g., `sheet#0`, `sheet#1`)
3. Drag individual sprites to use specific frames
4. Click the expand arrow again to collapse

### Refreshing

The Asset Browser automatically detects new files. If files were added externally, the browser refreshes after a short cooldown.

---

## Keyboard Shortcuts

| Shortcut | Action |
|----------|--------|
| Escape | Clear search filter |

> See the [Shortcuts Guide](shortcuts-guide.md) for all editor shortcuts.

---

## Tips & Best Practices

- **Organize by type**: Keep sprites in `sprites/`, animations in `animations/`, etc.
- **Use sub-folders**: Group related assets (e.g., `sprites/characters/`, `sprites/tiles/`)
- **Drag individual sprites**: Expand spritesheets and drag specific frames for precise control
- **Search is global**: The search bar searches all folders, not just the current one
- **Zoom for detail**: Increase thumbnail zoom to better see small sprites
- **Sprite sub-asset syntax**: Individual sprites use `#` notation (e.g., `sheets/player.spritesheet#3`)

---

## Troubleshooting

| Problem | Solution |
|---------|----------|
| Asset not showing | Check that the file is in `gameData/assets/` with a supported extension |
| Thumbnail is blank | The file may be corrupted or an unsupported format |
| Drag not working | Make sure you're dragging from the thumbnail, not the label |
| Search finds nothing | Check spelling — search matches file names only |
| New file not appearing | Wait for auto-refresh or navigate away and back to the folder |

---

## Related

- [Inspector Panel Guide](inspector-panel-guide.md) — Viewing asset metadata
- [Hierarchy Panel Guide](hierarchy-panel-guide.md) — Creating entities from assets
- [Scene View Guide](scene-view-guide.md) — Dropping assets into the scene
- [Sprite Editor Guide](sprite-editor-guide.md) — Editing spritesheets
- [Asset Loader Guide](asset-loader-guide.md) — Creating custom asset loaders
