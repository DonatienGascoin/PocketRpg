# Pivot Editor Guide

> **Summary:** The Pivot Editor lets you set the pivot point (origin) for sprites and sprite sheets. The pivot determines the point around which a sprite rotates and scales, and its position relative to the transform.

---

## Table of Contents

1. [Quick Reference](#quick-reference)
2. [Overview](#overview)
3. [Opening the Panel](#opening-the-panel)
4. [Interface Overview](#interface-overview)
5. [Workflows](#workflows)
6. [Tips & Best Practices](#tips--best-practices)
7. [Troubleshooting](#troubleshooting)
8. [Code Integration](#code-integration)
9. [Related](#related)

---

## Quick Reference

| Task | How |
|------|-----|
| Edit sprite pivot | Right-click sprite in Asset Browser > "Edit Pivot..." |
| Use preset pivot | Click one of the 9-grid buttons (TL, TC, TR, etc.) |
| Snap to pixel | Enable "Pixel Snap" button, then drag pivot |
| Edit sprite sheet | Open editor > select sprite in grid > edit pivot |
| Apply to all sprites | Select "Apply to All Sprites" radio button before saving |
| Save changes | Click green "Save" button |

---

## Overview

The **pivot point** (also called origin or anchor) is a normalized coordinate (0-1) that defines where a sprite is anchored relative to its transform position:

- **(0.5, 0.5)** - Center (default)
- **(0.5, 0.0)** - Bottom-center (recommended for characters)
- **(0.0, 0.0)** - Bottom-left corner
- **(1.0, 1.0)** - Top-right corner

When you position a sprite at world coordinates (5, 10), the pivot determines which part of the sprite sits at that point. A bottom-center pivot means the sprite's feet are at (5, 10), while a center pivot means the sprite's middle is at (5, 10).

The pivot also affects rotation and scaling - sprites rotate around their pivot point.

---

## Opening the Panel

There are two ways to open the Pivot Editor:

### From the Menu Bar
**Edit > Pivot Editor...**

Opens the editor without a pre-selected asset. Use the Browse button to select an asset.

### From the Asset Browser (Recommended)
**Right-click on sprite or sprite sheet > "Edit Pivot..."**

Opens the editor with the selected asset already loaded.

---

## Interface Overview

```
+----------------------------------------------------------------------+
|  Pivot Editor                                                    [X] |
+----------------------------------------------------------------------+
|                                                                      |
|  Asset: [sprites/player.png                              ] [Browse]  |
|                                                                      |
+--------------------------------+-------------------------------------+
|                                |                                     |
|     +--------------------+     |  Pivot                              |
|     |                    |     |  --------------------------------   |
|     |                    |     |  X: [====O====] 0.500               |
|     |        [+]         |     |  Y: [====O====] 0.500               |
|     |     (pivot)        |     |                                     |
|     |                    |     |  Presets                            |
|     +--------------------+     |  --------------------------------   |
|                                |  [TL] [TC] [TR]                     |
|                                |  [ML] [C ] [MR]                     |
|                                |  [BL] [BC] [BR]                     |
|                                |                                     |
|                                |  Options                            |
|                                |  --------------------------------   |
|                                |  [Pixel Snap] [Grid] [Crosshair]    |
|                                |                                     |
+--------------------------------+-------------------------------------+
|  (Sprite Sheet Mode - only for spritesheets)                         |
|  Apply To: (o) All Sprites  ( ) Selected Only                        |
|  [#0] [#1] [#2] [#3] [#4] [#5] [#6] [#7] ...                        |
+----------------------------------------------------------------------+
|  Zoom: [------O------] 2.0x  [1x] [2x] [4x]          [Cancel] [Save]  |
+----------------------------------------------------------------------+
```

### UI Sections

| Section | Description |
|---------|-------------|
| **Asset Selector** | Shows current asset path. Click "Browse" to open asset picker. |
| **Preview Canvas** | Interactive preview. Click and drag to move the pivot point. |
| **Pivot Fields** | X and Y drag fields for precise positioning (0.0 to 1.0). |
| **Presets** | 9-grid quick preset buttons for common pivot positions. |
| **Options** | Toggle buttons for Pixel Snap, Grid overlay, and Crosshair. |
| **Sprite Sheet Mode** | Appears when editing a sprite sheet. Select individual sprites and choose apply mode. |
| **Footer** | Zoom controls and action buttons (Cancel, Save). |

### Preset Button Reference

| Button | Position | Coordinates | Common Use |
|--------|----------|-------------|------------|
| TL | Top-Left | (0.0, 1.0) | UI elements anchored top-left |
| TC | Top-Center | (0.5, 1.0) | Hanging objects |
| TR | Top-Right | (1.0, 1.0) | UI elements anchored top-right |
| ML | Middle-Left | (0.0, 0.5) | Side-mounted objects |
| C | Center | (0.5, 0.5) | Default, rotation-focused sprites |
| MR | Middle-Right | (1.0, 0.5) | Side-mounted objects |
| BL | Bottom-Left | (0.0, 0.0) | Tile-like placement |
| BC | Bottom-Center | (0.5, 0.0) | Characters, NPCs (recommended) |
| BR | Bottom-Right | (1.0, 0.0) | Tile-like placement |

---

## Workflows

### Workflow 1: Setting a Character Sprite Pivot

1. Right-click the character sprite in the Asset Browser
2. Select "Edit Pivot..."
3. Click the **BC** (Bottom-Center) preset button
4. The pivot moves to the bottom-center of the sprite
5. Click **Save** to persist the change

### Workflow 2: Fine-Tuning with Pixel Snap

1. Open the Pivot Editor for your sprite
2. Click **Pixel Snap** to enable it (button turns green)
3. Drag the pivot in the preview canvas
4. The pivot snaps to pixel boundaries for precise alignment
5. Click **Save**

### Workflow 3: Editing Sprite Sheet Pivots

1. Right-click a `.spritesheet` file in the Asset Browser
2. Select "Edit Pivot..."
3. The sprite grid appears at the bottom
4. **To set the same pivot for all sprites:**
   - Select "Apply to All Sprites" (default)
   - Set the pivot position
   - Click **Save**
5. **To set different pivots per sprite:**
   - Select "Apply to Selected Only"
   - Click a sprite in the grid
   - Set its pivot
   - Click **Save**
   - Repeat for other sprites

---

## Tips & Best Practices

- **Use Bottom-Center (BC) for characters** - This makes positioning easier since the transform Y coordinate corresponds to the ground level.

- **Use Center (C) for projectiles and effects** - Objects that rotate frequently should pivot from their center.

- **Enable Pixel Snap for pixel art** - Ensures pivots align to pixel boundaries, preventing sub-pixel rendering artifacts.

- **Use "Apply to All" first** - When editing sprite sheets, set a default pivot for all sprites first, then override specific sprites that need different pivots.

- **Check the crosshair** - Enable the crosshair toggle to see alignment lines through the pivot point.

---

## Troubleshooting

| Problem | Solution |
|---------|----------|
| "Edit Pivot..." not appearing in context menu | Ensure you're right-clicking a sprite (.png) or sprite sheet (.spritesheet) file |
| Pivot changes not persisting | Click **Save** to persist changes to disk |
| Sprite sheet shows wrong sprite | Click the correct sprite in the grid at the bottom of the panel |
| Pivot seems off by a small amount | Enable **Pixel Snap** to align to pixel boundaries |
| Can't find metadata file | Metadata is stored in `gameData/.metadata/{assetPath}.meta` for sprites |

---

## Code Integration

Most pivot usage is automatic - the `SpriteRenderer` component uses the sprite's pivot by default. However, you can override the pivot in code if needed.

### Using Sprite Pivot (Default)

When `useSpritePivot` is true (default), the renderer uses the pivot stored in the sprite:

```java
SpriteRenderer renderer = gameObject.getComponent(SpriteRenderer.class);
renderer.setUseSpritePivot(true); // Default behavior
```

### Overriding Pivot in Component

You can override the pivot per-component without changing the sprite's stored pivot:

```java
SpriteRenderer renderer = gameObject.getComponent(SpriteRenderer.class);
renderer.setUseSpritePivot(false);
renderer.setOrigin(0.5f, 0.0f); // Bottom-center override
```

### Reading Sprite Pivot

```java
Sprite sprite = Assets.load("sprites/player.png", Sprite.class);
float pivotX = sprite.getPivotX(); // 0.0 to 1.0
float pivotY = sprite.getPivotY(); // 0.0 to 1.0
```

### Programmatic Pivot Setting

```java
Sprite sprite = Assets.load("sprites/player.png", Sprite.class);
sprite.setPivot(0.5f, 0.0f); // Bottom-center

// Or use helpers
sprite.setPivotBottomCenter();
sprite.setPivotCenter();
```

---

## Data Storage

| Asset Type | Storage Location | Format |
|------------|------------------|--------|
| Standalone Sprite | `gameData/.metadata/{path}.meta` | JSON with `pivotX`, `pivotY` |
| Sprite Sheet | Same `.spritesheet` file | JSON fields `pivotX`, `pivotY`, `spritePivots` |

### Example: Sprite Metadata File

```json
// gameData/.metadata/sprites/player.png.meta
{
  "pivotX": 0.5,
  "pivotY": 0.0
}
```

### Example: Sprite Sheet with Pivots

```json
// gameData/player.spritesheet
{
  "texture": "sprites/player.png",
  "spriteWidth": 32,
  "spriteHeight": 32,
  "pivotX": 0.5,
  "pivotY": 0.0,
  "spritePivots": {
    "3": { "pivotX": 0.5, "pivotY": 0.5 },
    "7": { "pivotX": 0.3, "pivotY": 0.2 }
  }
}
```

---

## Related

- [Animation Editor Guide](animation-editor-guide.md) - Animations use sprite pivots for frame positioning
- `SpriteRenderer` component - Renders sprites with pivot support
- `Sprite.java` - Core sprite class with pivot fields
