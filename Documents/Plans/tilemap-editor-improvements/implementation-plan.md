# Tilemap Editor Improvements Plan

## Overview

This plan addresses two issues with the tilemap editor:
1. **Workflow friction**: Too many clicks required to start painting tiles
2. **Tile ordering bug**: Tiles display in wrong order compared to spritesheet layout

---

## Problem Analysis

### Issue 1: Workflow Friction

**Current workflow to paint tiles:**
1. Open the editor
2. Click "Tilemap Layers" in Hierarchy panel
3. Click the desired layer in Inspector panel
4. Open TilesetPalettePanel (from menu)
5. Select a tile
6. Paint in viewport

**Pain points:**
- Layer selection is hidden in Inspector, requiring navigation away from palette
- TilesetPalettePanel doesn't auto-open when entering tilemap mode
- No visual feedback showing which layer is active without looking at Inspector
- No keyboard shortcuts for layer switching

### Issue 2: Tile Layout Ordering Bug

**Symptom:** Tiles in TilesetPalettePanel display vertically inverted compared to the source spritesheet.

**Root cause:** UV coordinate double-inversion.

1. `SpriteSheet.java:145` inverts Y coordinates for OpenGL's bottom-origin system:
   ```java
   int py = texture.getHeight() - (pyTop + spriteHeight);
   ```

2. `TileGridRenderer.java:91` swaps v0/v1 when calling ImGui:
   ```java
   ImGui.imageButton("##tile", textureId, size, size, u0, v1, u1, v0);
   //                                                     ↑       ↑
   //                                              SWAPPED: causes double-inversion
   ```

3. ImGui expects top-origin coordinates, but receives bottom-origin coordinates that are then flipped again, resulting in inverted display.

**Visual example:**
```
Spritesheet:          Current display:     Expected display:
[0] [1] [2] [3]       [12][13][14][15]     [0] [1] [2] [3]
[4] [5] [6] [7]       [8] [9] [10][11]     [4] [5] [6] [7]
[8] [9] [10][11]  →   [4] [5] [6] [7]  vs  [8] [9] [10][11]
[12][13][14][15]      [0] [1] [2] [3]      [12][13][14][15]
```

---

## Proposed Solution

### Approach: Hybrid Improvements (Phased)

Combine targeted fixes with incremental UX improvements:

| Phase | Description | Complexity |
|-------|-------------|------------|
| 1 | Fix tile ordering bug | Low |
| 2 | Add layer selector to TilesetPalettePanel | Medium |
| 3 | Auto-open palette on tilemap mode | Low |
| 4 | Add keyboard shortcuts for layers | Medium |
| 5 | Status bar feedback | Low |

---

## Implementation Plan

### Phase 1: Fix Tile Ordering Bug

**Files to modify:**
- `src/main/java/com/pocket/rpg/editor/panels/tilesets/TileGridRenderer.java`

**Changes:**

1. **Line 91** - Remove v0/v1 swap in `renderTileButton()`:
   ```java
   // Before:
   ImGui.imageButton("##tile", textureId, tileDisplaySize, tileDisplaySize, u0, v1, u1, v0);

   // After:
   ImGui.imageButton("##tile", textureId, tileDisplaySize, tileDisplaySize, u0, v0, u1, v1);
   ```

2. **Line 110** - Remove v0/v1 swap in tooltip preview:
   ```java
   // Before:
   ImGui.image(textureId, 64, 64, u0, v1, u1, v0);

   // After:
   ImGui.image(textureId, 64, 64, u0, v0, u1, v1);
   ```

3. **Verify** that game rendering still works correctly (SpriteSheet inversion is only for OpenGL context, ImGui should use native coords).

**Testing:**
- Open TilesetPalettePanel with various tilesets
- Verify tile order matches source spritesheet
- Verify tooltip preview is correct
- Verify painting still works correctly

---

### Phase 2: Add Layer Selector to TilesetPalettePanel

**Files to modify:**
- `src/main/java/com/pocket/rpg/editor/panels/TilesetPalettePanel.java`

**Changes:**

1. **Add EditorScene reference** to TilesetPalettePanel constructor:
   ```java
   private final EditorScene scene;

   public TilesetPalettePanel(EditorContext context, ...) {
       this.scene = context.getScene();
       // ...
   }
   ```

2. **Create `renderLayerSelector()` method** to render a compact layer dropdown/tabs:
   ```java
   private void renderLayerSelector() {
       if (scene == null) return;

       List<TilemapLayer> layers = scene.getLayers();
       if (layers.isEmpty()) {
           ImGui.textDisabled("No layers");
           return;
       }

       TilemapLayer activeLayer = scene.getActiveLayer();
       String activeLayerName = activeLayer != null ? activeLayer.getName() : "None";

       ImGui.setNextItemWidth(150);
       if (ImGui.beginCombo("##layer", activeLayerName)) {
           for (int i = 0; i < layers.size(); i++) {
               TilemapLayer layer = layers.get(i);
               boolean isSelected = (activeLayer == layer);

               // Show lock icon if locked
               String label = layer.isLocked() ? "[L] " + layer.getName() : layer.getName();

               if (ImGui.selectable(label, isSelected)) {
                   scene.setActiveLayer(i);
               }
           }
           ImGui.endCombo();
       }

       // Inline visibility toggle for active layer
       if (activeLayer != null) {
           ImGui.sameLine();
           boolean visible = activeLayer.isVisible();
           if (ImGui.checkbox("##vis", visible)) {
               activeLayer.setVisible(!visible);
           }
           if (ImGui.isItemHovered()) {
               ImGui.setTooltip("Toggle layer visibility");
           }
       }
   }
   ```

3. **Call `renderLayerSelector()`** at the top of `renderContent()`, after tileset selector:
   ```java
   @Override
   protected void renderContent() {
       renderTilesetSelector();

       ImGui.separator();
       renderLayerSelector();  // NEW
       ImGui.separator();

       renderTileGrid();
       // ...
   }
   ```

**Testing:**
- Open TilesetPalettePanel
- Verify layer dropdown shows all layers
- Verify selecting a layer changes the active layer
- Verify painting goes to the selected layer
- Verify lock/visibility indicators work

---

### Phase 3: Auto-Open Palette on Tilemap Mode

**Files to modify:**
- `src/main/java/com/pocket/rpg/editor/panels/hierarchy/HierarchySelectionHandler.java`
- `src/main/java/com/pocket/rpg/editor/EditorUIController.java`

**Changes:**

1. **Add method to EditorUIController** to open/focus TilesetPalettePanel:
   ```java
   public void openTilesetPalette() {
       if (tilesetPalettePanel != null) {
           tilesetPalettePanel.setVisible(true);
           tilesetPalettePanel.focus();
       }
   }
   ```

2. **Modify HierarchySelectionHandler.selectTilemapLayers()** to trigger panel open:
   ```java
   public void selectTilemapLayers() {
       selectionManager.selectTilemapLayer(0);
       if (toolManager != null && brushTool != null) {
           toolManager.setActiveTool(brushTool);
       }

       // NEW: Auto-open palette
       if (uiController != null) {
           uiController.openTilesetPalette();
       }
   }
   ```

3. **Wire up UIController reference** in HierarchySelectionHandler constructor or via setter.

**Testing:**
- Click "Tilemap Layers" in Hierarchy
- Verify TilesetPalettePanel opens automatically
- Verify it doesn't open duplicate panels if already open

---

### Phase 4: Keyboard Shortcuts for Layers

**Files to modify:**
- `editor/config/editorShortcuts.json`
- `src/main/java/com/pocket/rpg/editor/shortcut/EditorShortcutHandlers.java`
- `src/main/java/com/pocket/rpg/editor/shortcut/EditorShortcutHandlersImpl.java`

**Changes:**

1. **Add shortcut definitions** to `editorShortcuts.json`:
   ```json
   {
     "LAYER_NEXT": { "key": "]", "modifiers": [] },
     "LAYER_PREV": { "key": "[", "modifiers": [] },
     "LAYER_1": { "key": "1", "modifiers": ["CTRL"] },
     "LAYER_2": { "key": "2", "modifiers": ["CTRL"] },
     "LAYER_3": { "key": "3", "modifiers": ["CTRL"] },
     "LAYER_4": { "key": "4", "modifiers": ["CTRL"] },
     "LAYER_5": { "key": "5", "modifiers": ["CTRL"] },
     "TOGGLE_LAYER_VISIBILITY": { "key": "H", "modifiers": ["CTRL"] }
   }
   ```

2. **Add handler interface methods** to `EditorShortcutHandlers.java`:
   ```java
   void nextLayer();
   void prevLayer();
   void selectLayer(int index);
   void toggleActiveLayerVisibility();
   ```

3. **Implement handlers** in `EditorShortcutHandlersImpl.java`:
   ```java
   @Override
   public void nextLayer() {
       EditorScene scene = context.getScene();
       if (scene == null) return;

       int current = scene.getActiveLayerIndex();
       int count = scene.getLayers().size();
       if (count > 0) {
           scene.setActiveLayer((current + 1) % count);
       }
   }

   @Override
   public void prevLayer() {
       EditorScene scene = context.getScene();
       if (scene == null) return;

       int current = scene.getActiveLayerIndex();
       int count = scene.getLayers().size();
       if (count > 0) {
           scene.setActiveLayer((current - 1 + count) % count);
       }
   }

   @Override
   public void selectLayer(int index) {
       EditorScene scene = context.getScene();
       if (scene != null && index < scene.getLayers().size()) {
           scene.setActiveLayer(index);
       }
   }

   @Override
   public void toggleActiveLayerVisibility() {
       EditorScene scene = context.getScene();
       if (scene == null) return;

       TilemapLayer layer = scene.getActiveLayer();
       if (layer != null) {
           layer.setVisible(!layer.isVisible());
       }
   }
   ```

4. **Register shortcuts** in EditorShortcuts.java initialization.

**Testing:**
- Press `]` to cycle to next layer
- Press `[` to cycle to previous layer
- Press `Ctrl+1` through `Ctrl+5` to select layers directly
- Press `Ctrl+H` to toggle visibility
- Verify shortcuts only work when in tilemap editing context

---

### Phase 5: Status Bar Feedback

**Files to modify:**
- `src/main/java/com/pocket/rpg/editor/ui/StatusBar.java`
- `src/main/java/com/pocket/rpg/editor/EditorUIController.java`

**Changes:**

1. **Add persistent layer indicator** to StatusBar:
   ```java
   private void renderLayerIndicator() {
       EditorScene scene = context.getScene();
       if (scene == null) return;

       TilemapLayer layer = scene.getActiveLayer();
       if (layer != null) {
           ImGui.sameLine();
           ImGui.separator();
           ImGui.sameLine();

           // Color indicator based on layer state
           if (layer.isLocked()) {
               ImGui.textColored(0.7f, 0.7f, 0.7f, 1.0f, "Layer: " + layer.getName() + " [Locked]");
           } else {
               ImGui.text("Layer: " + layer.getName());
           }
       }
   }
   ```

2. **Call in StatusBar render loop** when tilemap mode is active.

**Testing:**
- Enter tilemap editing mode
- Verify status bar shows current layer name
- Verify it updates when switching layers
- Verify locked indicator appears for locked layers

---

## File Change Summary

| File | Changes |
|------|---------|
| `TileGridRenderer.java` | Fix UV coordinate swap (lines 91, 110) |
| `TilesetPalettePanel.java` | Add layer selector dropdown |
| `HierarchySelectionHandler.java` | Add auto-open palette call |
| `EditorUIController.java` | Add `openTilesetPalette()` method |
| `editorShortcuts.json` | Add layer shortcut definitions |
| `EditorShortcutHandlers.java` | Add layer handler interfaces |
| `EditorShortcutHandlersImpl.java` | Implement layer handlers |
| `StatusBar.java` | Add layer indicator |

---

## Testing Checklist

### Phase 1 - Tile Ordering
- [ ] Tiles display in correct order matching spritesheet
- [ ] Tooltip preview shows correct tile
- [ ] Painting places correct tile
- [ ] Game rendering still works correctly

### Phase 2 - Layer Selector
- [ ] Dropdown shows all layers
- [ ] Selecting layer changes active layer
- [ ] Painting goes to correct layer
- [ ] Visibility toggle works
- [ ] Lock indicator displays

### Phase 3 - Auto-Open
- [ ] Palette opens when clicking "Tilemap Layers"
- [ ] Doesn't open duplicates
- [ ] Focus goes to palette

### Phase 4 - Shortcuts
- [ ] `[` and `]` cycle layers
- [ ] `Ctrl+1-5` select layers directly
- [ ] `Ctrl+H` toggles visibility
- [ ] Shortcuts only active in tilemap mode

### Phase 5 - Status Bar
- [ ] Shows active layer name
- [ ] Updates on layer change
- [ ] Shows locked state

---

## Code Review

After implementation, request a code review of all changed files. Write review to:
`Documents/Plans/tilemap-editor-improvements/review.md`
