# 9-Slice Tool Implementation Plan

## Overview

A 9-slice (nine-patch) editing tool for creating scalable UI sprites that preserve corners while stretching edges and center regions. The tool will be accessible as a modal dialog from both the main menu and AssetBrowserPanel context menu.

**Prerequisite:** Pivot tool Phase 1 must be complete (provides `EditorCapability`, `AssetMetadata`, and `SpriteMetadata` infrastructure).

---

## Modal Layout Design

```
+----------------------------------------------------------+
|                   9-Slice Editor                      [X] |
+---------------------------+------------------------------+
|                           |  Sprite:                     |
|    Visual Editor          |  [dropdown or path display]  |
|    (450x400)              |------------------------------|
|                           |  Border Values:              |
|  +---------------------+  |    Left:   [____] px         |
|  | TL |    TOP     | TR|  |    Right:  [____] px         |
|  |----|------------|---|  |    Top:    [____] px         |
|  |    |            |   |  |    Bottom: [____] px         |
|  | L  |   CENTER   | R |  |------------------------------|
|  |    |            |   |  |  Center Mode:                |
|  |----|------------|---|  |    (o) Stretch   ( ) Tile    |
|  | BL |   BOTTOM   | BR|  |------------------------------|
|  +---------------------+  |  Preview Size:               |
|                           |    W: [____]  H: [____]      |
|  [draggable border lines] |  [1x] [2x] [3x] [4x]         |
|                           |------------------------------|
|                           |  9-Slice Preview:            |
|                           |  +----------------------+    |
|                           |  |    (scaled result)   |    |
|                           |  +----------------------+    |
+---------------------------+------------------------------+
|                                         [Cancel]  [Save] |
+----------------------------------------------------------+
```

**Size:** 800x600 pixels

**Visual Editor Features:**
- Sprite displayed scaled to fit with aspect ratio preserved
- 4 draggable border lines (left, right, top, bottom)
- Lines shown as colored dashed lines (red=horizontal, blue=vertical)
- Semi-transparent overlay showing the 9 regions
- Pixel position tooltip on hover

---

## Data Structures

### NineSliceData.java

**Location:** `src/main/java/com/pocket/rpg/rendering/resources/NineSliceData.java`

```java
package com.pocket.rpg.rendering.resources;

import lombok.Getter;
import lombok.Setter;

/**
 * Defines 9-slice border insets for a sprite.
 * All values are in pixels, measured inward from the sprite edges.
 * Stored as part of SpriteMetadata in .meta files.
 */
@Getter @Setter
public class NineSliceData {

    /** Left border inset in pixels */
    private int left = 0;

    /** Right border inset in pixels */
    private int right = 0;

    /** Top border inset in pixels */
    private int top = 0;

    /** Bottom border inset in pixels */
    private int bottom = 0;

    /** How to render the center and edge regions */
    public enum CenterMode { STRETCH, TILE }

    private CenterMode centerMode = CenterMode.STRETCH;

    public boolean isValid() {
        return left >= 0 && right >= 0 && top >= 0 && bottom >= 0;
    }

    public boolean hasSlicing() {
        return left > 0 || right > 0 || top > 0 || bottom > 0;
    }

    public int getHorizontalBorders() { return left + right; }
    public int getVerticalBorders() { return top + bottom; }
}
```

### NineSlice.java

**Location:** `src/main/java/com/pocket/rpg/rendering/resources/NineSlice.java`

```java
package com.pocket.rpg.rendering.resources;

import lombok.Getter;

/**
 * A renderable 9-slice sprite that combines a source Sprite with NineSliceData.
 * Provides pre-computed UV coordinates for all 9 regions.
 */
@Getter
public class NineSlice {

    private final Sprite sourceSprite;
    private final NineSliceData sliceData;

    // Pre-computed UV regions (9 sets of u0,v0,u1,v1)
    private final float[][] regionUVs = new float[9][4];

    // Region indices
    public static final int TOP_LEFT = 0;
    public static final int TOP_CENTER = 1;
    public static final int TOP_RIGHT = 2;
    public static final int MIDDLE_LEFT = 3;
    public static final int MIDDLE_CENTER = 4;
    public static final int MIDDLE_RIGHT = 5;
    public static final int BOTTOM_LEFT = 6;
    public static final int BOTTOM_CENTER = 7;
    public static final int BOTTOM_RIGHT = 8;

    public NineSlice(Sprite sourceSprite, NineSliceData sliceData) {
        this.sourceSprite = sourceSprite;
        this.sliceData = sliceData;
        computeRegionUVs();
    }

    private void computeRegionUVs() {
        // Convert pixel insets to UV offsets
        float spriteW = sourceSprite.getWidth();
        float spriteH = sourceSprite.getHeight();

        float leftU = sliceData.getLeft() / spriteW;
        float rightU = sliceData.getRight() / spriteW;
        float topV = sliceData.getTop() / spriteH;
        float bottomV = sliceData.getBottom() / spriteH;

        // Base UVs from sprite
        float u0 = sourceSprite.getU0();
        float v0 = sourceSprite.getV0();
        float u1 = sourceSprite.getU1();
        float v1 = sourceSprite.getV1();

        float uWidth = u1 - u0;
        float vHeight = v1 - v0;

        // Slice boundaries in UV space
        float uLeft = u0 + leftU * uWidth;
        float uRight = u1 - rightU * uWidth;
        float vTop = v0 + topV * vHeight;
        float vBottom = v1 - bottomV * vHeight;

        // Populate all 9 regions
        regionUVs[TOP_LEFT]      = new float[]{u0, v0, uLeft, vTop};
        regionUVs[TOP_CENTER]    = new float[]{uLeft, v0, uRight, vTop};
        regionUVs[TOP_RIGHT]     = new float[]{uRight, v0, u1, vTop};

        regionUVs[MIDDLE_LEFT]   = new float[]{u0, vTop, uLeft, vBottom};
        regionUVs[MIDDLE_CENTER] = new float[]{uLeft, vTop, uRight, vBottom};
        regionUVs[MIDDLE_RIGHT]  = new float[]{uRight, vTop, u1, vBottom};

        regionUVs[BOTTOM_LEFT]   = new float[]{u0, vBottom, uLeft, v1};
        regionUVs[BOTTOM_CENTER] = new float[]{uLeft, vBottom, uRight, v1};
        regionUVs[BOTTOM_RIGHT]  = new float[]{uRight, vBottom, u1, v1};
    }

    public float[] getRegionUV(int region) {
        return regionUVs[region];
    }

    public Texture getTexture() {
        return sourceSprite.getTexture();
    }
}
```

---

## Metadata Storage (Integrated with Pivot Tool)

9-slice data is stored in the same `.meta` files as pivot data, using the `SpriteMetadata` class created by the pivot tool.

### SpriteMetadata.java (Created by Pivot Tool, Extended for 9-Slice)

```java
// gameData/.metadata/sprites/button.png.meta
{
  // Pivot data (from pivot tool)
  "pivotX": 0.5,
  "pivotY": 0.5,
  "pixelsPerUnitOverride": null,

  // 9-slice data (added by this feature)
  "nineSlice": {
    "left": 8,
    "right": 8,
    "top": 6,
    "bottom": 10,
    "centerMode": "STRETCH"
  }
}
```

The `SpriteMetadata` class (created in pivot tool Phase 1) already has a placeholder for `nineSlice`:

```java
public class SpriteMetadata {
    public Float pivotX;
    public Float pivotY;
    public Float pixelsPerUnitOverride;

    // 9-slice data (null if no slicing defined)
    public NineSliceData nineSlice;
}
```

### Sprite Sheet 9-Slice Support

For sprites from sprite sheets, 9-slice data is stored in the `.spritesheet` file (same pattern as per-sprite pivots):

```json
// gameData/assets/spritesheets/ui.spritesheet
{
  "texture": "sprites/ui/ui_elements.png",
  "spriteWidth": 32,
  "spriteHeight": 32,
  "pivotX": 0.5,
  "pivotY": 0.5,
  "spriteNineSlices": {
    "0": { "left": 4, "right": 4, "top": 4, "bottom": 4, "centerMode": "STRETCH" },
    "5": { "left": 8, "right": 8, "top": 6, "bottom": 10, "centerMode": "TILE" }
  }
}
```

---

## Files to Create

| File | Purpose |
|------|---------|
| `src/main/java/com/pocket/rpg/rendering/resources/NineSliceData.java` | Data class for slice borders and mode |
| `src/main/java/com/pocket/rpg/rendering/resources/NineSlice.java` | Renderable 9-slice with UV computation |
| `src/main/java/com/pocket/rpg/editor/nineslice/NineSliceEditorPanel.java` | Main modal panel |
| `src/main/java/com/pocket/rpg/editor/nineslice/NineSlicePreviewRenderer.java` | Visual preview with draggable guides |

---

## Files to Modify

| File | Modification |
|------|--------------|
| `SpriteMetadata.java` | Add `NineSliceData nineSlice` field (may already have placeholder from pivot tool) |
| `SpriteLoader.java` | Load 9-slice data from metadata, add `NINE_SLICE` to capabilities |
| `SpriteSheetLoader.java` | Parse/save `spriteNineSlices`, add `NINE_SLICE` to capabilities |
| `SpriteSheet.java` | Add `spriteNineSlices` map for per-sprite 9-slice data |
| `Sprite.java` | Add optional `NineSliceData` field and getter |
| `AssetBrowserPanel.java` | Add to existing capability-based context menu (created by pivot tool) |
| `EditorMenuBar.java` | Add "Tools" menu with "9-Slice Editor..." option |
| `EditorUIController.java` | Instantiate and wire up `NineSliceEditorPanel` |

---

## Integration Points

### 1. EditorCapability (Already Defined by Pivot Tool)

The pivot tool creates `EditorCapability.java` with `NINE_SLICE` already included:

```java
public enum EditorCapability {
    PIVOT_EDITING,
    NINE_SLICE,        // Already defined for future use
    PHYSICS_SHAPE,
    COLLISION_MASK
}
```

### 2. Loader Capability Updates

**SpriteLoader.java** - Add `NINE_SLICE` to existing capabilities:
```java
@Override
public Set<EditorCapability> getEditorCapabilities() {
    return Set.of(EditorCapability.PIVOT_EDITING, EditorCapability.NINE_SLICE);
}
```

**SpriteSheetLoader.java** - Same update:
```java
@Override
public Set<EditorCapability> getEditorCapabilities() {
    return Set.of(EditorCapability.PIVOT_EDITING, EditorCapability.NINE_SLICE);
}
```

### 3. AssetBrowserPanel Context Menu (Extend Existing)

The pivot tool adds capability-based context menus. The 9-slice entry is already shown in the pivot plan:

```java
// In AssetBrowserPanel.java - already added by pivot tool
if (caps.contains(EditorCapability.NINE_SLICE)) {
    if (ImGui.menuItem(FontAwesomeIcons.BorderAll + " Edit 9-Slice...")) {
        nineSliceEditorPanel.open(entry.path);
    }
}
```

### 4. EditorMenuBar Tools Menu

**Location:** `EditorMenuBar.java` - add new menu between `renderViewMenu()` and `renderHelpMenu()`

In `render()` method:
```java
renderFileMenu();
renderEditMenu();
renderViewMenu();
renderToolsMenu();  // NEW
renderHelpMenu();
```

New method:
```java
private void renderToolsMenu() {
    if (ImGui.beginMenu("Tools")) {
        if (ImGui.menuItem(FontAwesomeIcons.BorderAll + " 9-Slice Editor...")) {
            if (onOpenNineSliceEditor != null) {
                onOpenNineSliceEditor.run();
            }
        }
        ImGui.endMenu();
    }
}
```

---

## Phased Implementation

### Phase 1: Core Data Structures

**Goal:** Create foundation classes and integrate with metadata system

**Prerequisite:** Pivot tool Phase 1 complete (EditorCapability, AssetMetadata, SpriteMetadata)

**Files to Create:**
1. `NineSliceData.java` - Data class with borders and mode enum
2. `NineSlice.java` - Combines sprite + data, computes 9 region UVs

**Files to Modify:**
1. `SpriteMetadata.java` - Ensure `NineSliceData nineSlice` field exists
2. `SpriteLoader.java` - Load 9-slice from metadata when loading sprites
3. `Sprite.java` - Add `NineSliceData` field and `getNineSliceData()` / `setNineSliceData()` methods

**Testing:**
- Manually create a `.meta` file with 9-slice data
- Load sprite via `Assets.load()` and verify `sprite.getNineSliceData()` returns correct values

---

### Phase 2: Editor Panel UI

**Goal:** Create the modal panel with visual editing

**Files to Create:**
1. `NineSliceEditorPanel.java` - Main panel class following `PivotEditorPanel` pattern
2. `NineSlicePreviewRenderer.java` - Handles sprite preview and border line rendering

**Features:**
- Modal popup with sprite preview
- Draggable border lines using ImGui draw list
- Numeric input fields for precise border values
- Center mode radio buttons (Stretch/Tile)
- Preview section showing 9-slice at custom sizes
- Save/Cancel buttons

**Pattern Reference:** Follow `PivotEditorPanel.java` and `CreateSpritesheetDialog.java` for:
- State-based open/close pattern
- `ImGui.openPopup()` / `beginPopupModal()` usage
- Draw list for overlay rendering
- Input validation and change detection

---

### Phase 3: Editor Integration

**Goal:** Wire up access points and loader capabilities

**Files to Modify:**
1. `SpriteLoader.java` - Add `EditorCapability.NINE_SLICE` to capabilities
2. `SpriteSheetLoader.java` - Add `EditorCapability.NINE_SLICE` to capabilities, parse/save spriteNineSlices
3. `SpriteSheet.java` - Add `spriteNineSlices` map
4. `EditorMenuBar.java` - Add Tools menu with "9-Slice Editor..." option
5. `EditorUIController.java` - Create and manage panel instance, pass to AssetBrowserPanel

**Note:** Context menu entry in `AssetBrowserPanel.java` is already added by pivot tool (uses capability check).

---

### Phase 4: Runtime Rendering (Optional)

**Goal:** Enable 9-slice rendering in game/UI

**Files to Modify:**
1. `UIRendererBackend.java` - Add `drawNineSlice()` interface method
2. `UIRenderer.java` - Implement 9-slice rendering (9 quad draws)

**New Component (optional):**
- `UINineSlice.java` - UI component that uses 9-slice sprites for panels, buttons, etc.

**Rendering Algorithm:**
```java
void drawNineSlice(float x, float y, float width, float height,
                   NineSlice ns, Vector4f tint) {
    NineSliceData data = ns.getSliceData();

    // Border sizes (don't scale corners)
    float left = data.getLeft();
    float right = data.getRight();
    float top = data.getTop();
    float bottom = data.getBottom();

    // Center dimensions (scaled)
    float centerW = width - left - right;
    float centerH = height - top - bottom;

    // Clamp if target smaller than borders
    if (centerW < 0) {
        float scale = width / (left + right);
        left *= scale; right *= scale; centerW = 0;
    }
    if (centerH < 0) {
        float scale = height / (top + bottom);
        top *= scale; bottom *= scale; centerH = 0;
    }

    // Draw 9 quads: 3 rows x 3 columns
    // Corners: fixed size | Edges: stretch one axis | Center: stretch both
}
```

---

## Critical Reference Files

| File | Why It's Relevant |
|------|-------------------|
| `src/main/java/com/pocket/rpg/editor/panels/PivotEditorPanel.java` | Modal panel pattern with visual preview (created by pivot tool) |
| `src/main/java/com/pocket/rpg/editor/tileset/CreateSpritesheetDialog.java` | Modal dialog pattern with grid overlay drawing |
| `src/main/java/com/pocket/rpg/rendering/resources/Sprite.java` | UV coordinate handling, texture reference |
| `src/main/java/com/pocket/rpg/resources/AssetMetadata.java` | Metadata loading/saving utility (created by pivot tool) |
| `src/main/java/com/pocket/rpg/resources/SpriteMetadata.java` | Metadata class to extend (created by pivot tool) |
| `src/main/java/com/pocket/rpg/resources/loaders/SpriteSheetLoader.java` | Loader pattern with JSON parsing |
| `src/main/java/com/pocket/rpg/editor/panels/AssetBrowserPanel.java` | Context menu integration (capability-based, from pivot tool) |

---

## Verification Plan

1. **Phase 1:** Create a test `.meta` file with 9-slice data, load sprite via `Assets.load()`, verify `sprite.getNineSliceData()` returns correct values
2. **Phase 2:** Open panel from debug button, adjust borders visually, verify numeric values update and vice versa
3. **Phase 3:** Right-click sprite in AssetBrowser, select "Edit 9-Slice...", save, verify `.meta` file updated correctly
4. **Phase 4:** Create `NineSlice` from sprite with data, render at various sizes (50x50, 200x100, etc.), verify corners stay fixed while center scales
