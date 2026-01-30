# Sprite Editor Guide

> **Summary:** The Sprite Editor lets you configure pivot points and 9-slice borders for sprites and sprite sheets. Use the Pivot tab to set the origin point for positioning/rotation, and the 9-Slice tab to define scalable borders for UI elements. For rendering options (stretch vs tile, fill modes), see UIImage component settings.

---

## Table of Contents

1. [Quick Reference](#quick-reference)
2. [Overview](#overview)
3. [Opening the Panel](#opening-the-panel)
4. [Interface Overview](#interface-overview)
5. [Pivot Tab](#pivot-tab)
6. [9-Slice Tab](#9-slice-tab)
7. [Workflows](#workflows)
8. [Tips & Best Practices](#tips--best-practices)
9. [Troubleshooting](#troubleshooting)
10. [Code Integration](#code-integration)
11. [Related](#related)

---

## Quick Reference

| Task | How |
|------|-----|
| Edit sprite | Right-click sprite in Asset Browser > "Sprite Editor..." |
| Edit sprite (quick) | Double-click sprite in Asset Browser |
| Set pivot preset | Pivot tab > Click one of the 9-grid buttons |
| Enable pixel snap | Pivot tab > Click "Pixel Snap" button |
| Set 9-slice borders | 9-Slice tab > Drag border lines or enter values |
| Use border presets | 9-Slice tab > Click "4px", "8px", "16px", or "Clear" |
| Save changes | Click green "Save" button |

---

## Overview

The **Sprite Editor** combines two powerful editing features in a tabbed interface:

### Pivot Tab
The **pivot point** (origin/anchor) is a normalized coordinate (0-1) that defines where a sprite is anchored relative to its transform position. It affects:
- Positioning (which part of the sprite sits at the transform's position)
- Rotation (sprites rotate around their pivot point)
- Scaling (sprites scale from their pivot point)

Common pivot values:
- **(0.5, 0.5)** - Center (default)
- **(0.5, 0.0)** - Bottom-center (recommended for characters)
- **(0.0, 0.0)** - Bottom-left corner

### 9-Slice Tab
**9-slice** (also called 9-patch) defines borders that remain fixed when scaling a sprite. This is essential for UI elements like buttons, panels, and dialog boxes that need to scale without distorting their corners or borders.

A 9-slice sprite is divided into 9 regions:
- **4 corners** - Fixed size, never stretched
- **4 edges** - Stretch along one axis
- **1 center** - Stretches in both directions

---

## Opening the Panel

### Double-Click (Fastest)
**Double-click** a sprite or sprite sheet in the Asset Browser.

### Context Menu
**Right-click** sprite or sprite sheet > **"Sprite Editor..."**

### Menu Bar
**Edit > Sprite Editor...**

Opens without a pre-selected asset. Use Browse to select one.

---

## Interface Overview

```
+----------------------------------------------------------------------+
|  Sprite Editor                                                    [X] |
+----------------------------------------------------------------------+
|  Asset: [sprites/button.png                              ] [Browse]   |
+----------------------------------------------------------------------+
|  [Pivot] [9-Slice]                                                    |
+----------------------------------------------------------------------+
|                                                                       |
|   (Tab-specific content - see sections below)                         |
|                                                                       |
+----------------------------------------------------------------------+
|  (Sprite Sheet Mode - only for spritesheets)                          |
|  Apply To: (o) All Sprites  ( ) Selected Only                         |
|  [#0] [#1] [#2] [#3] [#4] [#5] [#6] [#7] ...                         |
+----------------------------------------------------------------------+
|  Zoom: [------O------] 2.0x  [1x] [2x] [4x]          [Cancel] [Save]  |
+----------------------------------------------------------------------+
```

| Section | Description |
|---------|-------------|
| **Asset Selector** | Shows current asset. Click "Browse" to change. |
| **Tabs** | Switch between Pivot and 9-Slice editing modes. |
| **Tab Content** | Editor controls specific to the selected tab. |
| **Sprite Sheet Mode** | Appears for sprite sheets. Select sprites and apply mode. |
| **Footer** | Zoom controls and Save/Cancel buttons. |

---

## Pivot Tab

### Interface

```
+--------------------------------+-------------------------------------+
|                                |                                     |
|     +--------------------+     |  Pivot                              |
|     |                    |     |  X: [====O====] 0.500               |
|     |        [+]         |     |  Y: [====O====] 0.500               |
|     |     (pivot)        |     |                                     |
|     |                    |     |  Presets                            |
|     +--------------------+     |  [TL] [TC] [TR]                     |
|                                |  [ML] [C ] [MR]                     |
|                                |  [BL] [BC] [BR]                     |
|                                |                                     |
|                                |  Options                            |
|                                |  [Pixel Snap] [Grid] [Crosshair]    |
+--------------------------------+-------------------------------------+
```

### Controls

| Control | Description |
|---------|-------------|
| **Preview Canvas** | Click and drag to move pivot. Shows sprite with grid overlay. |
| **X/Y Fields** | Drag or type to set precise pivot (0.0-1.0). |
| **Preset Buttons** | 9-grid for common positions (TL=Top-Left, BC=Bottom-Center, etc.) |
| **Pixel Snap** | Snaps pivot to pixel boundaries (green when enabled). |
| **Grid** | Toggles grid overlay on preview. |
| **Crosshair** | Toggles crosshair lines through pivot point. |

### Preset Reference

| Button | Coordinates | Best For |
|--------|-------------|----------|
| TL | (0.0, 1.0) | Top-left anchored UI |
| TC | (0.5, 1.0) | Hanging objects |
| TR | (1.0, 1.0) | Top-right anchored UI |
| ML | (0.0, 0.5) | Left-side attached |
| C | (0.5, 0.5) | Rotating/scaling sprites |
| MR | (1.0, 0.5) | Right-side attached |
| BL | (0.0, 0.0) | Tile alignment |
| BC | (0.5, 0.0) | Characters, NPCs |
| BR | (1.0, 0.0) | Tile alignment |

---

## 9-Slice Tab

### Interface

```
+--------------------------------+------------------------------+
|                                |                              |
|     +--------------------+     |  Borders (pixels)            |
|     |    |         |     |     |  Left:   [   4   ]  [4px ]   |
|     |----+---------+----|     |  Right:  [   4   ]  [8px ]   |
|     |    |         |    |     |  Top:    [   4   ]  [16px]   |
|     |    |         |    |     |  Bottom: [   4   ]  [Clear]  |
|     |----+---------+----|     |                              |
|     |    |         |     |     |                              |
|     +--------------------+     |                              |
|        ^  border lines         |                              |
|                                |                              |
+--------------------------------+------------------------------+
```

### Controls

| Control | Description |
|---------|-------------|
| **Preview Canvas** | Shows sprite with draggable border lines. Blue=vertical, Red=horizontal. Supports zoom (scroll) and pan (middle-click). |
| **Border Fields** | Left, Right, Top, Bottom border sizes in pixels. |
| **4px / 8px / 16px** | Quick presets for uniform borders. |
| **Clear** | Reset all borders to zero. |

### Border Colors

| Color | Lines |
|-------|-------|
| Blue | Left and Right borders (vertical lines) |
| Red | Top and Bottom borders (horizontal lines) |
| Yellow | Corner region highlights |

### Dragging Borders

- Hover over a border line until cursor changes
- Click and drag to adjust
- Values automatically clamp to valid ranges (can't overlap)

---

## Workflows

### Setting a Character Pivot

1. Double-click the character sprite in Asset Browser
2. On the **Pivot** tab, click **BC** (Bottom-Center)
3. Click **Save**

### Creating a Scalable Button

1. Double-click your button sprite in Asset Browser
2. Switch to the **9-Slice** tab
3. Click **8px** preset (or drag borders to match your design)
4. Click **Save**
5. In your UIImage component, set **Image Type** to "Sliced"

### Setting Up a Dialog Panel

1. Open your panel sprite in the Sprite Editor
2. Go to **9-Slice** tab
3. Set borders to match the decorative frame (e.g., Left=12, Right=12, Top=16, Bottom=12)
4. Click **Save**
5. In your UIImage component:
   - Set **Image Type** to "Sliced" for stretched center
   - Or set **Image Type** to "Tiled" for repeating patterns

### Editing Sprite Sheet Pivots

1. Double-click a `.spritesheet` file
2. Select "Apply to All Sprites" to set a default pivot
3. Set the pivot, click **Save**
4. For specific overrides: select "Apply to Selected Only", click a sprite in the grid, adjust, and save

---

## Tips & Best Practices

### Pivot Tips
- **Use BC for characters** - Transform Y coordinate = ground level
- **Use C for projectiles** - Better rotation behavior
- **Enable Pixel Snap for pixel art** - Prevents sub-pixel artifacts

### 9-Slice Tips
- **Match borders to artwork** - Borders should align with where your sprite's "stretchable" region begins
- **Test in-game** - Create a UIImage with Image Type "Sliced" at different sizes to verify
- **Keep corners small** - Smaller corners = more flexible scaling range
- **Use UIImage for rendering** - 9-slice borders are defined in Sprite Editor, but rendering mode (Sliced/Tiled) is set on UIImage component

---

## Troubleshooting

| Problem | Solution |
|---------|----------|
| Context menu missing "Sprite Editor..." | Ensure you're clicking a .png or .spritesheet file |
| Changes not saving | Click the green **Save** button before closing |
| 9-slice not rendering in game | Set UIImage component's **Image Type** to "Sliced" |
| Can't drag border lines | Hover near the line until cursor changes, then drag |
| Sprite sheet shows wrong sprite | Click the correct sprite in the bottom grid |
| Warning "Sprite has no 9-slice data" | Configure borders in Sprite Editor's 9-Slice tab first |

---

## Code Integration

### Using Pivot (Default Behavior)

```java
// Pivot is automatically used by SpriteRenderer
SpriteRenderer renderer = gameObject.getComponent(SpriteRenderer.class);
renderer.setSprite(sprite); // Uses sprite's stored pivot

// Override pivot per-component (doesn't change sprite)
renderer.setUseSpritePivot(false);
renderer.setOrigin(0.5f, 0.0f);
```

### Reading Sprite Pivot

```java
Sprite sprite = Assets.load("sprites/player.png", Sprite.class);
float pivotX = sprite.getPivotX();
float pivotY = sprite.getPivotY();
```

### Checking for 9-Slice Data

```java
Sprite sprite = Assets.load("sprites/button.png", Sprite.class);

if (sprite.hasNineSlice()) {
    NineSlice nineSlice = sprite.createNineSlice();
    // Use nineSlice.getRegionUV(NineSlice.TOP_LEFT), etc.
}
```

### Reading 9-Slice Borders

```java
NineSliceData data = sprite.getNineSliceData();
int left = data.left;
int right = data.right;
int top = data.top;
int bottom = data.bottom;
```

### Using 9-Slice with UIImage

```java
// In code, set up a UIImage for 9-slice rendering
UIImage image = gameObject.addComponent(UIImage.class);
image.setSprite(sprite);
image.setImageType(UIImage.ImageType.SLICED);
image.setFillCenter(true);  // false to render only the border (frame)
```

### UIImage Image Types

| Type | Description |
|------|-------------|
| `SIMPLE` | Regular sprite rendering (default) |
| `SLICED` | 9-slice rendering - corners fixed, edges/center stretch |
| `TILED` | Repeats sprite to fill area |
| `FILLED` | Partial rendering for progress bars (horizontal, vertical, radial) |

---

## Data Storage

| Asset Type | Storage Location | Contents |
|------------|------------------|----------|
| Sprite | `gameData/.metadata/{path}.meta` | `pivotX`, `pivotY`, `nineSlice` |
| Sprite Sheet | `.spritesheet` file | `pivotX`, `pivotY`, `spritePivots` |

### Example: Sprite Metadata

```json
// gameData/.metadata/sprites/button.png.meta
{
  "pivotX": 0.5,
  "pivotY": 0.5,
  "nineSlice": {
    "left": 8,
    "right": 8,
    "top": 8,
    "bottom": 8
  }
}
```

---

## Related

- [Animation Editor Guide](animation-editor-guide.md) - Animations use sprite pivots
- `Sprite.java` - Core sprite class
- `NineSliceData.java` - 9-slice border data
- `NineSlice.java` - Runtime 9-slice with UV computation
- `UIImage.java` - UI component with Image Type settings (Simple, Sliced, Tiled, Filled)
- `UIImageInspector.java` - Inspector for UIImage with fill mode controls
