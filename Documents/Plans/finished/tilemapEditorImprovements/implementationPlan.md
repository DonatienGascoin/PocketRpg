# Tilemap Editor Improvements Plan

**Status: IMPLEMENTED**

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
- Inspector doesn't switch back to entity when clicking a GameObject in Hierarchy
- Interacting with palette doesn't sync Hierarchy selection (confusing state)
- No visual feedback when no layer is selected (can click tiles but nothing happens)

---

## Current Panel Layouts

### Hierarchy Panel (Current)
```
┌─ Hierarchy ─────────────────────────┐
│ ▼ Scene                             │
│   ├─ Player                         │
│   ├─ Camera                         │
│   └─ ...                            │
│                                     │
│ ▶ Tilemap Layers  ← Click here      │
│                     (always selects │
│                      layer 0)       │
│                                     │
│ ▶ Collision                         │
└─────────────────────────────────────┘
```

### Inspector Panel (Current - when Tilemap Layers selected)
```
┌─ Inspector ─────────────────────────┐
│ Tilemap Layers                      │
│ ─────────────────────────────────── │
│ [+ Add Layer]                       │
│                                     │
│ ┌─────────────────────────────────┐ │
│ │ ● Ground         z:0   [👁] [🔒]│ │  ← Must click here
│ │   Objects        z:1   [👁] [🔒]│ │    to change layer
│ │   Overlay        z:2   [👁] [🔒]│ │
│ └─────────────────────────────────┘ │
│                                     │
│ Selected Layer: Ground              │
│ [Tileset: ▼ outdoor_tiles    ]      │
│ [Rename] [Delete]                   │
└─────────────────────────────────────┘
```

### TilesetPalettePanel (Current - Horizontal 2-Column Layout)
```
┌─ Tileset Palette ─────────────────────────────────────────────────────────┐
│                           │                                               │
│  [Tileset: ▼ outdoor   ]  │  ┌──┬──┬──┬──┬──┬──┬──┬──┬──┬──┐            │
│  ──────────────────────── │  │  │  │  │  │  │  │  │  │  │  │            │
│  Selection: Tile 5        │  ├──┼──┼──┼──┼──┼──┼──┼──┼──┼──┤            │
│  [Tool Size: ===O===  3]  │  │  │  │  │  │  │  │  │  │  │  │            │
│  ──────────────────────── │  ├──┼──┼──┼──┼──┼──┼──┼──┼──┼──┤            │
│  [Tile Size: ===O===  32] │  │  │  │  │  │  │  │  │  │  │  │            │
│                           │  ├──┼──┼──┼──┼──┼──┼──┼──┼──┼──┤            │
│   ↑                       │  │  │  │  │  │  │  │  │  │  │  │            │
│   No layer info!          │  └──┴──┴──┴──┴──┴──┴──┴──┴──┴──┘            │
│   User doesn't know       │                                               │
│   which layer they're     │         (scrollable tile grid)                │
│   painting to             │                                               │
│                           │                                               │
└───────────────────────────┴───────────────────────────────────────────────┘
        Left Column (33%)                    Right Column (67%)
```

---

## Proposed Panel Layouts

### TilesetPalettePanel (New - Phase 2)

**With layer selected (normal state):**
```
┌─ Tileset Palette ─────────────────────────────────────────────────────────┐
│                           │                                               │
│  [Tileset: ▼ outdoor   ]  │  ┌──┬──┬──┬──┬──┬──┬──┬──┬──┬──┐            │
│  ──────────────────────── │  │  │  │  │  │  │  │  │  │  │  │            │
│  Layer: [▼ Ground ] [👁]  │  ├──┼──┼──┼──┼──┼──┼──┼──┼──┼──┤  ← NEW     │
│  ──────────────────────── │  │  │  │  │  │  │  │  │  │  │  │            │
│  Selection: Tile 5        │  ├──┼──┼──┼──┼──┼──┼──┼──┼──┼──┤            │
│  [Tool Size: ===O===  3]  │  │  │  │  │  │  │  │  │  │  │  │            │
│  ──────────────────────── │  ├──┼──┼──┼──┼──┼──┼──┼──┼──┼──┤            │
│  [Tile Size: ===O===  32] │  │  │  │  │  │  │  │  │  │  │  │            │
│                           │  └──┴──┴──┴──┴──┴──┴──┴──┴──┴──┘            │
│                           │                                               │
└───────────────────────────┴───────────────────────────────────────────────┘
```

**No layer selected (disabled state):**
```
┌─ Tileset Palette ─────────────────────────────────────────────────────────┐
│                           │                                               │
│  [Tileset: ▼ outdoor   ]  │  ┌──┬──┬──┬──┬──┬──┬──┬──┬──┬──┐            │
│  ──────────────────────── │  │░░│░░│░░│░░│░░│░░│░░│░░│░░│░░│ ← DISABLED │
│  Layer: [▼ None     ]     │  ├──┼──┼──┼──┼──┼──┼──┼──┼──┼──┤   (grayed  │
│  ──────────────────────── │  │░░│░░│░░│░░│░░│░░│░░│░░│░░│░░│    out,    │
│  ⚠ Select a layer to      │  ├──┼──┼──┼──┼──┼──┼──┼──┼──┼──┤    non-    │
│    start painting         │  │░░│░░│░░│░░│░░│░░│░░│░░│░░│░░│  clickable)│
│  ──────────────────────── │  ├──┼──┼──┼──┼──┼──┼──┼──┼──┼──┤            │
│  [Tile Size: ===O===  32] │  │░░│░░│░░│░░│░░│░░│░░│░░│░░│░░│            │
│                           │  └──┴──┴──┴──┴──┴──┴──┴──┴──┴──┘            │
│                           │                                               │
└───────────────────────────┴───────────────────────────────────────────────┘
```

**Layer dropdown expanded:**
```
┌─ Tileset Palette ─────────────────────────────────────────────────────────┐
│                           │                                               │
│  [Tileset: ▼ outdoor   ]  │  ┌──┬──┬──┬──┬──┬──┬──┬──┬──┬──┐            │
│  ──────────────────────── │  │  │  │  │  │  │  │  │  │  │  │            │
│  Layer: [▼ Ground ] [👁]  │  ├──┼──┼──┼──┼──┼──┼──┼──┼──┼──┤            │
│         ┌──────────────┐  │  │  │  │  │  │  │  │  │  │  │  │            │
│         │ ● Ground     │  │  ├──┼──┼──┼──┼──┼──┼──┼──┼──┼──┤            │
│         │   Objects    │  │  │  │  │  │  │  │  │  │  │  │  │            │
│         │   Overlay    │  │  ├──┼──┼──┼──┼──┼──┼──┼──┼──┼──┤            │
│         │ 🔒 Locked    │  │  │  │  │  │  │  │  │  │  │  │  │            │
│         └──────────────┘  │  └──┴──┴──┴──┴──┴──┴──┴──┴──┴──┘            │
│                           │                                               │
└───────────────────────────┴───────────────────────────────────────────────┘
```

### Status Bar (New - Phase 4)
```
┌─────────────────────────────────────────────────────────────────────────┐
│ Ready │ outdoor.scene │ Brush Tool │ Layer: Ground                      │
└─────────────────────────────────────────────────────────────────────────┘
                                       ↑
                                       NEW: Always visible layer indicator
```

### Full Editor Layout (After Changes)
```
┌─────────────────────────────────────────────────────────────────────────────┐
│ File  Edit  View  Tools  Window  Help                                       │
├────────────────┬────────────────────────────────────┬───────────────────────┤
│ Hierarchy      │         Scene Viewport             │ Inspector             │
│ ───────────    │  ┌──────────────────────────────┐  │ ───────────           │
│ ▼ Scene        │  │                              │  │ Shows context:       │
│   Player  ←────│──│── Click entity = Inspector   │  │ - Entity properties  │
│   Camera       │  │      shows entity properties │  │ - OR Tilemap Layers  │
│                │  │                              │  │ - OR Collision       │
│ ▶ Tilemap Lyr ←│──│── Click = Opens palette,     │  │                       │
│                │  │      focuses it              │  │ Switches back when   │
│ ▶ Collision  ←─│──│── Click = Opens collision    │  │ clicking entities!   │
│                │  │      panel, focuses it       │  │                       │
│                │  └──────────────────────────────┘  │                       │
│                │                                    │                       │
│                ├────────────────────────────────────┤                       │
│                │ Tileset Palette (auto-opened)      │                       │
│                │ ─────────────────────────────────  │                       │
│                │ [Tileset: ▼]  │ ┌──┬──┬──┬──┐     │                       │
│                │ Layer: [▼]    │ │  │  │  │  │     │                       │
│                │ Selection:    │ └──┴──┴──┴──┘     │                       │
├────────────────┴────────────────────────────────────┴───────────────────────┤
│ Ready │ outdoor.scene │ Brush │ Layer: Ground                               │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Workflow Comparison

### Before (6 steps)
```
1. Open editor
2. Click "Tilemap Layers" in Hierarchy
3. Look at Inspector to see layers
4. Click desired layer in Inspector
5. Open Tileset Palette from menu
6. Select tile and paint

Problem: Click entity → Inspector stays on Tilemap Layers (broken!)
```

### After (2-3 steps)
```
1. Click "Tilemap Layers" in Hierarchy
   → Palette auto-opens and focuses (Phase 3)
   → Layer selector visible in palette (Phase 2)
2. Select tile and paint
3. Click entity in Hierarchy
   → Inspector switches back to entity (Phase 3)

Same for Collision:
1. Click "Collision" in Hierarchy → Collision panel opens and focuses
2. Click entity → Inspector switches back to entity
```

---

### Issue 2: Tile Layout Ordering Bug

**Symptom:** Tiles in TilesetPalettePanel display with wrong order for the **first ~6 rows only** - after that, the order is correct. This started after pivot point and 9-slice tools were implemented.

**Root cause:** HashMap iteration order corruption during metadata save/load.

In `SpriteSheet.java` (lines 43, 48):
```java
private final Map<Integer, float[]> spritePivots = new HashMap<>();      // Line 43
private final Map<Integer, NineSliceData> spriteNineSlices = new HashMap<>();  // Line 48
```

Both use `HashMap` which does **not** preserve insertion order. This causes:

1. **Save operation** (`SpriteSheetLoader.save()`):
   - Iterates `spriteSheet.getSpritePivots().entrySet()` to serialize
   - HashMap iteration order is unpredictable
   - JSON keys get written in scrambled order: `{"5": {...}, "2": {...}, "0": {...}}`

2. **Load operation** (`SpriteSheetLoader.load()`):
   - Parses JSON with scrambled key order
   - Metadata gets associated with wrong sprite indices

3. **Display**: Sprites with edited metadata show in wrong positions; sprites without metadata display correctly.

**Why only first ~6 rows affected:**
- User only edited pivot/9-slice for sprites in first 6 rows
- Those entries got scrambled during save/reload cycle
- Later sprites without metadata are unaffected

---

## Proposed Solution

### Approach: Hybrid Improvements (Phased)

Combine targeted fixes with incremental UX improvements:

| Phase | Description | Complexity |
|-------|-------------|------------|
| 1 | Fix tile ordering bug | Low |
| 2 | Add layer selector to TilesetPalettePanel | Medium |
| 3 | Auto-open/focus panels + fix Inspector switching | Medium |
| 4 | Status bar feedback | Low |

---

## Implementation Plan

### Phase 1: Fix Tile Ordering Bug

**Files to modify:**
- `src/main/java/com/pocket/rpg/rendering/resources/SpriteSheet.java`

**Changes:**

1. **Line 43** - Change `spritePivots` from HashMap to LinkedHashMap:
   ```java
   // Before:
   private final Map<Integer, float[]> spritePivots = new HashMap<>();

   // After:
   private final Map<Integer, float[]> spritePivots = new LinkedHashMap<>();
   ```

2. **Line 48** - Change `spriteNineSlices` from HashMap to LinkedHashMap:
   ```java
   // Before:
   private final Map<Integer, NineSliceData> spriteNineSlices = new HashMap<>();

   // After:
   private final Map<Integer, NineSliceData> spriteNineSlices = new LinkedHashMap<>();
   ```

3. **Re-save affected spritesheets** - After the fix, open and re-save any spritesheets that have corrupted metadata to restore correct ordering.

**Why LinkedHashMap:**
- `LinkedHashMap` preserves insertion order
- When metadata is loaded from JSON (keys parsed in order), entries stay in correct frame index order
- When saving, entries serialize in the same order they were inserted
- Metadata stays aligned with correct sprite indices across save/load cycles

**Testing:**
- Open a spritesheet with pivot data (e.g., building_splitted)
- Verify tile order matches source spritesheet in TilesetPalettePanel
- Edit a pivot point, save, reload
- Verify order remains correct after reload
- Verify game rendering still works correctly

---

### Phase 2: Add Layer Selector to TilesetPalettePanel

**Files to modify:**
- `src/main/java/com/pocket/rpg/editor/panels/TilesetPalettePanel.java`
- `src/main/java/com/pocket/rpg/editor/panels/tilesets/TileGridRenderer.java`

**Changes:**

1. **Add dependencies** to TilesetPalettePanel:
   ```java
   private final EditorScene scene;
   private final EditorSelectionManager selectionManager;

   public TilesetPalettePanel(EditorContext context, ...) {
       this.scene = context.getScene();
       this.selectionManager = context.getSelectionManager();
       // ...
   }
   ```

2. **Create `renderLayerSelector()` method** in left column (after tileset selector):
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

       ImGui.text("Layer:");
       ImGui.setNextItemWidth(-1);  // Fill available width
       if (ImGui.beginCombo("##layer", activeLayerName)) {
           for (int i = 0; i < layers.size(); i++) {
               TilemapLayer layer = layers.get(i);
               boolean isSelected = (activeLayer == layer);

               // Show lock icon if locked
               String label = layer.isLocked() ? "[L] " + layer.getName() : layer.getName();

               if (ImGui.selectable(label, isSelected)) {
                   scene.setActiveLayer(i);
                   // SYNC: Force Hierarchy to select Tilemap Layers
                   selectionManager.selectTilemapLayer(i);
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

3. **Show warning when no layer selected** (replace selection info area):
   ```java
   private void renderSelectionInfo() {
       TilemapLayer activeLayer = scene != null ? scene.getActiveLayer() : null;

       if (activeLayer == null) {
           ImGui.textColored(1.0f, 0.8f, 0.2f, 1.0f, "Select a layer to");
           ImGui.textColored(1.0f, 0.8f, 0.2f, 1.0f, "start painting");
           return;
       }

       // ... existing selection info code ...
   }
   ```

4. **Sync Hierarchy when clicking tile or selecting brush** - in `onSelectionCreated()`:
   ```java
   private void onSelectionCreated(TileSelection selection) {
       // ... existing code to set selection on tools ...

       // SYNC: Force Hierarchy to select Tilemap Layers when interacting with palette
       if (selectionManager != null && scene != null) {
           int layerIndex = scene.getActiveLayerIndex();
           if (layerIndex >= 0) {
               selectionManager.selectTilemapLayer(layerIndex);
           }
       }
   }
   ```

5. **Disable tile grid when no layer selected** - in `TileGridRenderer.java`:
   ```java
   public void render(Tileset tileset, boolean enabled) {
       if (!enabled) {
           // Gray out the entire grid
           ImGui.pushStyleVar(ImGuiStyleVar.Alpha, 0.4f);
           ImGui.beginDisabled(true);
       }

       // ... existing render code ...

       if (!enabled) {
           ImGui.endDisabled();
           ImGui.popStyleVar();
       }
   }
   ```

6. **Pass enabled state from TilesetPalettePanel**:
   ```java
   // In renderHorizontal() right column:
   boolean hasActiveLayer = scene != null && scene.getActiveLayer() != null;
   gridRenderer.render(tilesetSelector.getSelectedTileset(), hasActiveLayer);
   ```

**Bidirectional Selection Pattern:**
```
Hierarchy click "Tilemap Layers"  ──►  Opens/focuses TilesetPalettePanel
                                       Selects layer 0

TilesetPalettePanel interactions  ──►  Forces Hierarchy to select "Tilemap Layers"
  - Select layer from dropdown         (so Inspector shows tilemap layers)
  - Click a tile
  - Select brush tool
```

**Testing:**
- Open TilesetPalettePanel
- Verify layer dropdown shows all layers
- Select a layer → Hierarchy highlights "Tilemap Layers", Inspector shows layers
- Click a tile → Hierarchy highlights "Tilemap Layers"
- No layer selected → Grid is grayed out and non-clickable
- Warning message shows "Select a layer to start painting"
- Verify lock/visibility indicators work

---

### Phase 3: Auto-Open/Focus Panels + Fix Inspector Switching

**Files to modify:**
- `src/main/java/com/pocket/rpg/editor/panels/hierarchy/HierarchySelectionHandler.java`
- `src/main/java/com/pocket/rpg/editor/EditorUIController.java`
- `src/main/java/com/pocket/rpg/editor/panels/HierarchyPanel.java`

**Changes:**

1. **Add methods to EditorUIController** to open/focus panels:
   ```java
   public void openTilesetPalette() {
       if (tilesetPalettePanel != null) {
           tilesetPalettePanel.setVisible(true);
           tilesetPalettePanel.focus();
       }
   }

   public void openCollisionPanel() {
       if (collisionPanel != null) {
           collisionPanel.setVisible(true);
           collisionPanel.focus();
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

       // NEW: Auto-open and focus palette
       if (uiController != null) {
           uiController.openTilesetPalette();
       }
   }
   ```

3. **Modify HierarchySelectionHandler.selectCollision()** (or add if missing) to open collision panel:
   ```java
   public void selectCollision() {
       selectionManager.selectCollision();
       // Activate collision tool if available

       // NEW: Auto-open and focus collision panel
       if (uiController != null) {
           uiController.openCollisionPanel();
       }
   }
   ```

4. **Fix Inspector switching back to entity** - When selecting a GameObject in HierarchyPanel:
   ```java
   // In HierarchyPanel or HierarchySelectionHandler
   public void selectGameObject(GameObject gameObject) {
       selectionManager.select(gameObject);  // This should clear tilemap/collision selection
       // Inspector will automatically show entity properties
   }
   ```

   The key is ensuring `EditorSelectionManager.select(GameObject)` clears other selection types:
   ```java
   public void select(GameObject gameObject) {
       this.selectedObject = gameObject;
       this.selectedLayerIndex = -1;      // Clear tilemap layer selection
       this.collisionSelected = false;     // Clear collision selection
       notifyListeners();
   }
   ```

5. **Wire up UIController reference** in HierarchySelectionHandler constructor or via setter.

**Testing:**
- Click "Tilemap Layers" in Hierarchy → TilesetPalettePanel opens and focuses
- Click "Collision" in Hierarchy → CollisionPanel opens and focuses
- Click an entity in Hierarchy → Inspector switches back to entity properties
- Verify panels don't open duplicates if already open

---

### Phase 4: Status Bar Feedback

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
| `SpriteSheet.java` | Change HashMap to LinkedHashMap for spritePivots and spriteNineSlices (lines 43, 48) |
| `TilesetPalettePanel.java` | Add layer selector dropdown, sync Hierarchy on interactions, pass enabled state to grid |
| `TileGridRenderer.java` | Add `enabled` parameter, disable/gray out grid when no layer selected |
| `HierarchySelectionHandler.java` | Add auto-open palette/collision panel calls |
| `EditorUIController.java` | Add `openTilesetPalette()` and `openCollisionPanel()` methods |
| `EditorSelectionManager.java` | Ensure `select(GameObject)` clears tilemap/collision selection |
| `HierarchyPanel.java` | Wire up proper selection clearing when clicking entities |
| `StatusBar.java` | Add layer indicator |

---

## Testing Checklist

### Phase 1 - Tile Ordering ✓
- [x] Tiles display in correct order matching spritesheet
- [x] Tooltip preview shows correct tile
- [x] Painting places correct tile
- [x] Edit pivot, save, reload - order remains correct
- [x] Edit 9-slice, save, reload - order remains correct
- [x] Game rendering still works correctly

### Phase 2 - Layer Selector + Bidirectional Sync ✓
- [x] Dropdown shows all layers in TilesetPalettePanel left column
- [x] Selecting layer changes active layer
- [x] Selecting layer → Hierarchy selects "Tilemap Layers"
- [x] Clicking tile → Hierarchy selects "Tilemap Layers"
- [x] No layer selected → Grid is grayed out/disabled
- [x] No layer selected → Warning message displayed
- [x] Painting goes to correct layer
- [x] Visibility toggle works
- [x] Lock indicator displays

### Phase 3 - Auto-Open/Focus + Inspector Switching ✓
- [x] Click "Tilemap Layers" → Palette opens and focuses
- [x] Click "Collision" → CollisionPanel opens and focuses
- [x] Click entity → Inspector switches back to entity properties
- [x] Doesn't open duplicate panels if already open

### Phase 4 - Status Bar ✓
- [x] Shows active layer name
- [x] Updates on layer change
- [x] Shows locked state

---

## Code Review

After implementation, request a code review of all changed files. Write review to:
`Documents/Plans/tilemap-editor-improvements/review.md`
