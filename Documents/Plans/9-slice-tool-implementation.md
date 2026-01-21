# 9-Slice Tool Implementation Plan

## Overview

A 9-slice (nine-patch) editing tool integrated as a tab in the existing `PivotEditorPanel`, renamed to `SpriteEditorPanel`. This consolidates sprite metadata editing (pivot and 9-slice) into a single modal with shared infrastructure.

---

## Design Decision: Tabbed Panel

Instead of creating a separate `NineSliceEditorPanel`, we will:

1. **Rename** `PivotEditorPanel` → `SpriteEditorPanel`
2. **Add tabs** at the top: "Pivot" and "9-Slice"
3. **Share common infrastructure:**
   - Asset selector (browse button, asset picker popup)
   - Sprite preview canvas with zoom controls
   - Sprite sheet mode (apply to all vs selected sprite)
   - Save/Cancel/Apply footer buttons
   - Undo support patterns
   - Status callbacks

### Benefits
- Reduces code duplication (~300 lines shared)
- Single entry point for sprite metadata editing
- Consistent UX for both features
- Simpler wiring in `EditorUIController`

---

## Combined Modal Layout

```
+------------------------------------------------------------------+
|                    Sprite Editor                              [X] |
+------------------------------------------------------------------+
| Asset: [path/to/sprite.png                    ] [Browse]          |
+------------------------------------------------------------------+
|  [ Pivot ]  [ 9-Slice ]                                           |
+------------------------------------------------------------------+
|                             |                                     |
|    Visual Editor            |   [Tab-specific controls]           |
|    (shared canvas)          |                                     |
|                             |   PIVOT TAB:                        |
|  +---------------------+    |     Pivot X: [____]                 |
|  |                     |    |     Pivot Y: [____]                 |
|  |   [sprite preview   |    |     [3x3 preset grid]               |
|  |    with overlays]   |    |     [Pixel Snap] [Grid] [Crosshair] |
|  |                     |    |                                     |
|  +---------------------+    |   9-SLICE TAB:                      |
|                             |     Left:   [____] px               |
|                             |     Right:  [____] px               |
|                             |     Top:    [____] px               |
|                             |     Bottom: [____] px               |
|                             |     Center: (o)Stretch ( )Tile      |
|                             |     [Scaled Preview]                |
+-----------------------------+-------------------------------------+
|  [Sprite Sheet Selector - if applicable]                          |
+------------------------------------------------------------------+
| Zoom: [=====] [1x][2x][4x]              [Cancel] [Apply] [Save]   |
+------------------------------------------------------------------+
```

**Size:** 800x700 pixels (slightly larger to accommodate both tabs)

---

## Data Structures

### NineSliceData.java

**Location:** `src/main/java/com/pocket/rpg/rendering/resources/NineSliceData.java`

```java
package com.pocket.rpg.rendering.resources;

/**
 * Defines 9-slice border insets for a sprite.
 * All values are in pixels, measured inward from the sprite edges.
 */
public class NineSliceData {

    /** Left border inset in pixels */
    public int left = 0;

    /** Right border inset in pixels */
    public int right = 0;

    /** Top border inset in pixels */
    public int top = 0;

    /** Bottom border inset in pixels */
    public int bottom = 0;

    /** How to render the center and edge regions */
    public CenterMode centerMode = CenterMode.STRETCH;

    public enum CenterMode { STRETCH, TILE }

    public boolean isValid() {
        return left >= 0 && right >= 0 && top >= 0 && bottom >= 0;
    }

    public boolean hasSlicing() {
        return left > 0 || right > 0 || top > 0 || bottom > 0;
    }

    public boolean isEmpty() {
        return left == 0 && right == 0 && top == 0 && bottom == 0;
    }
}
```

### NineSlice.java (Runtime wrapper)

**Location:** `src/main/java/com/pocket/rpg/rendering/resources/NineSlice.java`

```java
package com.pocket.rpg.rendering.resources;

/**
 * A renderable 9-slice sprite that combines a source Sprite with NineSliceData.
 * Provides pre-computed UV coordinates for all 9 regions.
 */
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

    public NineSlice(Sprite sourceSprite, NineSliceData sliceData) { ... }
    private void computeRegionUVs() { ... }
    public float[] getRegionUV(int region) { ... }
    public Texture getTexture() { ... }
    public NineSliceData getSliceData() { ... }
}
```

---

## Metadata Storage

9-slice data is stored alongside pivot data in `.meta` files:

```json
// gameData/.metadata/sprites/button.png.meta
{
  "pivotX": 0.5,
  "pivotY": 0.5,
  "nineSlice": {
    "left": 8,
    "right": 8,
    "top": 6,
    "bottom": 10,
    "centerMode": "STRETCH"
  }
}
```

For sprite sheets, stored in the `.spritesheet` file:

```json
{
  "texture": "sprites/ui/ui_elements.png",
  "spriteWidth": 32,
  "spriteHeight": 32,
  "spriteNineSlices": {
    "0": { "left": 4, "right": 4, "top": 4, "bottom": 4, "centerMode": "STRETCH" }
  }
}
```

---

## Files to Create

| File | Purpose |
|------|---------|
| `rendering/resources/NineSliceData.java` | Data class for slice borders and mode |
| `rendering/resources/NineSlice.java` | Renderable 9-slice with UV computation |

---

## Files to Modify

| File | Change |
|------|--------|
| `editor/panels/PivotEditorPanel.java` | **Rename** to `SpriteEditorPanel.java`, add tabs, refactor shared code |
| `resources/SpriteMetadata.java` | Add `NineSliceData nineSlice` field |
| `rendering/resources/Sprite.java` | Add optional `NineSliceData` field |
| `resources/loaders/SpriteLoader.java` | Load 9-slice from metadata |
| `resources/loaders/SpriteSheetLoader.java` | Parse/save `spriteNineSlices` |
| `rendering/resources/SpriteSheet.java` | Add `spriteNineSlices` map |
| `editor/panels/AssetBrowserPanel.java` | Update panel reference name, add context menu entry |
| `editor/core/EditorUIController.java` | Rename panel reference |
| `editor/ui/EditorMenuBar.java` | Add Tools menu or rename Edit menu item |

---

## Implementation Plan

### Phase 1: Refactor PivotEditorPanel → SpriteEditorPanel

**Goal:** Rename and add tab infrastructure without changing pivot functionality

**Changes:**

1. **Rename file and class:**
   - `PivotEditorPanel.java` → `SpriteEditorPanel.java`
   - Class name: `PivotEditorPanel` → `SpriteEditorPanel`
   - Popup ID: `"Pivot Editor"` → `"Sprite Editor"`

2. **Add tab state:**
   ```java
   private enum EditorTab { PIVOT, NINE_SLICE }
   private EditorTab activeTab = EditorTab.PIVOT;
   ```

3. **Refactor renderContent():**
   ```java
   private void renderContent() {
       renderAssetSelector();  // Shared
       ImGui.separator();

       if (sprite == null && spriteSheet == null) {
           ImGui.textDisabled("Select an asset to edit.");
           renderFooter();
           return;
       }

       // Tab bar
       if (ImGui.beginTabBar("SpriteEditorTabs")) {
           if (ImGui.beginTabItem("Pivot")) {
               activeTab = EditorTab.PIVOT;
               ImGui.endTabItem();
           }
           if (ImGui.beginTabItem("9-Slice")) {
               activeTab = EditorTab.NINE_SLICE;
               ImGui.endTabItem();
           }
           ImGui.endTabBar();
       }

       // Main content based on active tab
       renderMainContent();

       // Sprite sheet selector (shared)
       if (isSpriteSheet && spriteSheet != null) {
           ImGui.separator();
           renderSpriteSheetSelector();
       }

       ImGui.separator();
       renderFooter();  // Shared
   }

   private void renderMainContent() {
       // Left: Preview area (shared canvas, different overlays)
       // Right: Tab-specific controls

       if (activeTab == EditorTab.PIVOT) {
           renderPivotPreview();
           renderPivotControls();
       } else {
           renderNineSlicePreview();
           renderNineSliceControls();
       }
   }
   ```

4. **Extract pivot-specific methods:**
   - `renderPivotPreview()` - canvas with pivot point
   - `renderPivotControls()` - X/Y fields, presets, options
   - `handlePivotDrag()` - drag logic

5. **Update references:**
   - `EditorUIController`: rename field and instantiation
   - `AssetBrowserPanel`: update panel reference
   - `EditorMenuBar`: rename menu item to "Sprite Editor..."

**Verification:** Open panel, pivot editing works exactly as before, tab bar shows but 9-Slice tab is empty.

---

### Phase 2: Add 9-Slice Data Structures

**Goal:** Create data classes and integrate with metadata system

**Files to Create:**

1. **NineSliceData.java** - Border values and center mode enum
2. **NineSlice.java** - Runtime wrapper with UV computation

**Files to Modify:**

1. **SpriteMetadata.java** - Add `nineSlice` field:
   ```java
   public NineSliceData nineSlice;

   public boolean hasNineSlice() {
       return nineSlice != null && nineSlice.hasSlicing();
   }
   ```

2. **Sprite.java** - Add 9-slice support:
   ```java
   private NineSliceData nineSliceData;

   public NineSliceData getNineSliceData() { return nineSliceData; }
   public void setNineSliceData(NineSliceData data) { this.nineSliceData = data; }
   public boolean hasNineSlice() { return nineSliceData != null && nineSliceData.hasSlicing(); }
   ```

3. **SpriteLoader.java** - Load 9-slice from metadata:
   ```java
   // In load() method, after loading pivot:
   if (meta.nineSlice != null) {
       sprite.setNineSliceData(meta.nineSlice);
   }
   ```

**Verification:** Create test `.meta` file with 9-slice data, verify `sprite.getNineSliceData()` returns values.

---

### Phase 3: 9-Slice Editor UI

**Goal:** Implement 9-slice tab functionality in SpriteEditorPanel

**Add to SpriteEditorPanel:**

1. **9-slice state fields:**
   ```java
   // 9-slice working values
   private int sliceLeft = 0;
   private int sliceRight = 0;
   private int sliceTop = 0;
   private int sliceBottom = 0;
   private NineSliceData.CenterMode sliceCenterMode = NineSliceData.CenterMode.STRETCH;

   // Original values for cancel
   private int originalSliceLeft, originalSliceRight, originalSliceTop, originalSliceBottom;
   private NineSliceData.CenterMode originalSliceCenterMode;

   // Drag state for border lines
   private enum DragLine { NONE, LEFT, RIGHT, TOP, BOTTOM }
   private DragLine draggingLine = DragLine.NONE;

   // Preview settings
   private int previewWidth = 100;
   private int previewHeight = 100;
   ```

2. **renderNineSlicePreview():**
   - Draw sprite with zoom
   - Draw 4 draggable border lines (dashed, colored: red=horizontal, blue=vertical)
   - Draw semi-transparent region overlays
   - Handle line dragging with pixel-snapped values
   - Show pixel coordinates on hover

3. **renderNineSliceControls():**
   ```java
   private void renderNineSliceControls() {
       ImGui.text("Borders");
       ImGui.separator();

       // Int inputs with undo support
       sliceLeft = renderIntField("Left", sliceLeft, maxLeft);
       sliceRight = renderIntField("Right", sliceRight, maxRight);
       sliceTop = renderIntField("Top", sliceTop, maxTop);
       sliceBottom = renderIntField("Bottom", sliceBottom, maxBottom);

       ImGui.spacing();
       ImGui.text("Center Mode");
       ImGui.separator();

       if (ImGui.radioButton("Stretch", sliceCenterMode == CenterMode.STRETCH)) {
           sliceCenterMode = CenterMode.STRETCH;
       }
       if (ImGui.radioButton("Tile", sliceCenterMode == CenterMode.TILE)) {
           sliceCenterMode = CenterMode.TILE;
       }

       ImGui.spacing();
       ImGui.text("Preview");
       ImGui.separator();

       // Preview size controls
       ImGui.text("Size:");
       ImGui.setNextItemWidth(60);
       int[] w = {previewWidth};
       if (ImGui.dragInt("##pw", w, 1, 16, 512)) { previewWidth = w[0]; }
       ImGui.sameLine();
       ImGui.text("x");
       ImGui.sameLine();
       ImGui.setNextItemWidth(60);
       int[] h = {previewHeight};
       if (ImGui.dragInt("##ph", h, 1, 16, 512)) { previewHeight = h[0]; }

       // Quick size buttons
       if (ImGui.smallButton("1x")) { previewWidth = previewHeight = spriteSize; }
       ImGui.sameLine();
       if (ImGui.smallButton("2x")) { previewWidth = previewHeight = spriteSize * 2; }
       ImGui.sameLine();
       if (ImGui.smallButton("3x")) { previewWidth = previewHeight = spriteSize * 3; }

       // Render scaled 9-slice preview
       renderScaledNineSlicePreview();
   }
   ```

4. **renderScaledNineSlicePreview():**
   - Show what the 9-slice looks like at the specified preview size
   - Draw 9 quads with correct UVs
   - Corners: fixed size, Edges: stretch one axis, Center: stretch both

5. **Border line dragging:**
   ```java
   private void handleBorderLineDrag(float drawX, float drawY, float displayWidth, float displayHeight) {
       // Hit test for each line (5px tolerance)
       // On drag start: capture original value for undo
       // On drag: update value, clamp to valid range
       // On drag end: push undo command if changed
   }
   ```

**Verification:** Switch to 9-Slice tab, drag border lines, verify values update, preview shows correct slicing.

---

### Phase 4: Save/Load Integration

**Goal:** Persist 9-slice data to metadata files

**Modify applyChanges() / save logic:**

```java
private void apply9Slice(boolean saveToFile) {
    if (assetPath == null) return;

    try {
        NineSliceData data = new NineSliceData();
        data.left = sliceLeft;
        data.right = sliceRight;
        data.top = sliceTop;
        data.bottom = sliceBottom;
        data.centerMode = sliceCenterMode;

        if (isSpriteSheet && spriteSheet != null) {
            // Apply to spritesheet
            if (applyToAllSprites) {
                spriteSheet.setDefaultNineSlice(data);
            } else {
                spriteSheet.setSpriteNineSlice(selectedSpriteIndex, data);
            }

            if (saveToFile) {
                SpriteSheetLoader loader = new SpriteSheetLoader();
                loader.save(spriteSheet, fullPath);
            }
        } else if (sprite != null) {
            // Apply to sprite
            sprite.setNineSliceData(data);

            if (saveToFile) {
                SpriteMetadata meta = AssetMetadata.loadOrDefault(assetPath, SpriteMetadata.class, SpriteMetadata::new);
                meta.nineSlice = data.hasSlicing() ? data : null;  // null if all zeros
                AssetMetadata.saveOrDelete(assetPath, meta);
            }
        }

        showStatus(saveToFile ? "Saved 9-slice" : "Applied 9-slice");

    } catch (IOException e) {
        showStatus("Failed to save: " + e.getMessage());
    }
}
```

**Modify SpriteSheetLoader:**

```java
// Parsing
JsonArray spriteNineSlicesJson = root.getAsJsonArray("spriteNineSlices");
if (spriteNineSlicesJson != null) {
    for (JsonElement entry : spriteNineSlicesJson) {
        JsonObject obj = entry.getAsJsonObject();
        int index = obj.get("index").getAsInt();
        NineSliceData data = gson.fromJson(obj, NineSliceData.class);
        spriteSheet.setSpriteNineSlice(index, data);
    }
}

// Saving
if (!spriteSheet.getSpriteNineSlices().isEmpty()) {
    JsonArray arr = new JsonArray();
    for (Map.Entry<Integer, NineSliceData> e : spriteSheet.getSpriteNineSlices().entrySet()) {
        JsonObject obj = gson.toJsonTree(e.getValue()).getAsJsonObject();
        obj.addProperty("index", e.getKey());
        arr.add(obj);
    }
    root.add("spriteNineSlices", arr);
}
```

**Modify SpriteSheet.java:**

```java
private NineSliceData defaultNineSlice;
private final Map<Integer, NineSliceData> spriteNineSlices = new HashMap<>();

public void setDefaultNineSlice(NineSliceData data) { ... }
public void setSpriteNineSlice(int index, NineSliceData data) { ... }
public NineSliceData getEffectiveNineSlice(int index) { ... }
public Map<Integer, NineSliceData> getSpriteNineSlices() { ... }
```

**Verification:** Edit 9-slice, save, close editor, reopen - values persist.

---

### Phase 5: Context Menu, Double-Click & Menu Bar

**Goal:** Wire up all access points with unified "Sprite Editor" naming

**AssetBrowserPanel.java - Context Menu:**
```java
// Single context menu option for sprite/spritesheet assets (replaces "Edit Pivot...")
if (ImGui.menuItem(FontAwesomeIcons.Edit + " Sprite Editor...")) {
    spriteEditorPanel.open(entry.path);
}
```

**AssetBrowserPanel.java - Double-Click Handler:**
```java
// Double-click on sprite or spritesheet opens the Sprite Editor
if (isDoubleClick && (isSprite || isSpriteSheet)) {
    spriteEditorPanel.open(entry.path);
}
```

**EditorMenuBar.java:**
```java
// Single menu item (replaces "Pivot Editor...")
if (ImGui.menuItem(FontAwesomeIcons.Edit + " Sprite Editor...")) {
    onOpenSpriteEditor.run();
}
```

**Add to SpriteEditorPanel:**
```java
public void setActiveTab(EditorTab tab) {
    this.activeTab = tab;
}
```

**Verification:** Right-click shows "Sprite Editor...", double-click opens panel, menu bar item renamed.

---

### Phase 6: Encyclopedia Documentation

**Goal:** Create/update user documentation for the Sprite Editor

**Check existing documentation:**
- Look for `Documents/Encyclopedia/pivot-editor-guide.md` or similar
- If exists: rename to `sprite-editor-guide.md` and update content
- If not: create new `sprite-editor-guide.md`

**Documentation should cover:**
1. Opening the Sprite Editor (menu, context menu, double-click)
2. Pivot tab functionality (existing features)
3. 9-Slice tab functionality:
   - What 9-slice is and when to use it
   - Setting border values (drag or input fields)
   - Center mode (Stretch vs Tile)
   - Scaled preview usage
4. Sprite sheet workflows (per-sprite vs apply-to-all)
5. Save/Apply/Cancel behavior

**Verification:** Documentation accurately describes all editor features.

---

### Phase 7: Runtime Rendering (Optional/Future)

**Goal:** Enable 9-slice rendering in game UI

**Files:**
- `UIRendererBackend.java` - Add `drawNineSlice()` method
- `UIRenderer.java` - Implement 9-slice rendering
- `UINineSlice.java` - Optional UI component

This phase is deferred until UI components need 9-slice support.

---

## Critical Reference Files

| File | Why It's Relevant |
|------|-------------------|
| `editor/panels/PivotEditorPanel.java` | **Primary file to refactor** - contains all shared infrastructure |
| `editor/tileset/CreateSpritesheetDialog.java` | Grid overlay drawing patterns |
| `resources/SpriteMetadata.java` | Metadata class to extend |
| `resources/AssetMetadata.java` | Metadata loading/saving utility |
| `resources/loaders/SpriteSheetLoader.java` | Loader pattern with JSON parsing |

---

## Verification Checklist

- [ ] Phase 1: Panel renamed, tabs visible, pivot editing unchanged
- [ ] Phase 2: NineSliceData class works, metadata loads/saves
- [ ] Phase 3: 9-Slice tab functional, border lines draggable, preview works
- [ ] Phase 4: Values persist across editor sessions
- [ ] Phase 5: Right-click "Sprite Editor...", double-click opens panel, menu bar renamed
- [ ] Phase 6: Encyclopedia documentation created/updated
- [ ] Phase 7: (Optional) 9-slice renders correctly at runtime

---

## Code Review

After implementation, review all changed/added files and write findings to `Documents/Reviews/sprite-editor-refactor-review.md`.
