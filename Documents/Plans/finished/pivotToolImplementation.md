# Pivot Tool Implementation Plan

## Overview

A modal panel for editing pivot points on sprites and sprite sheets. Accessible from:
1. Main menu: `Edit > Pivot Editor...`
2. Asset Browser: Right-click on sprite/spritesheet → "Edit Pivot..."

## Current State Analysis

### What Already Exists
- **Sprite.java** (lines 74-81): `pivotX` and `pivotY` fields (normalized 0-1, default 0.5)
- **Sprite.java** (lines 194-236): Helper methods (`setPivotCenter()`, `setPivotBottomCenter()`, etc.)
- **SpriteTypeAdapter.java** (lines 70-71, 124-125): Full serialization support for pivots
- **SpriteRenderer.java** (lines 41-59): `useSpritePivot` flag and `originX/originY` override

### What's Missing
- **No pivot persistence for sprites** - Pivots are only in memory, not saved to asset files
- **No pivot UI** - No editor panel to visually edit pivots
- **SpriteSheet JSON format** - Doesn't include pivot fields
- **Per-sprite pivots in spritesheets** - All sprites from a sheet share the same pivot

---

## Panel Layout Design

```
┌──────────────────────────────────────────────────────────────────────┐
│  Pivot Editor                                                    [X] │
├──────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  Asset: [sprites/player.png                              ] [Browse]  │
│                                                                      │
├────────────────────────────────┬─────────────────────────────────────┤
│                                │                                     │
│     ┌────────────────────┐     │  Pivot Position                     │
│     │                    │     │  ─────────────────────────────────  │
│     │                    │     │                                     │
│     │        [+]         │     │  X: [═══════●═══════] 0.50          │
│     │     (pivot)        │     │  Y: [═══════●═══════] 0.50          │
│     │                    │     │                                     │
│     │                    │     │  ─────────────────────────────────  │
│     └────────────────────┘     │                                     │
│                                │  Quick Presets                      │
│  Zoom: [─────●─────] 2.0x      │  ┌─────┬─────┬─────┐               │
│                                │  │ TL  │ TC  │ TR  │               │
│  [ ] Show Grid                 │  ├─────┼─────┼─────┤               │
│  [ ] Show Origin Crosshair     │  │ ML  │ CTR │ MR  │               │
│                                │  ├─────┼─────┼─────┤               │
│                                │  │ BL  │ BC  │ BR  │               │
│                                │  └─────┴─────┴─────┘               │
│                                │                                     │
│                                │  [Bottom Center] (Recommended)      │
│                                │                                     │
├────────────────────────────────┴─────────────────────────────────────┤
│  Sprite Sheet Mode (when editing a spritesheet)                      │
├──────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  Apply To: (●) All Sprites  ( ) Selected Sprite Only                 │
│                                                                      │
│  Sprite Grid: [#0] [#1] [#2] [#3] [#4] [#5] [#6] [#7] ...           │
│               (click to select individual sprite for preview)        │
│                                                                      │
├──────────────────────────────────────────────────────────────────────┤
│                                          [Cancel]  [Apply]  [Save]   │
└──────────────────────────────────────────────────────────────────────┘
```

### UI Components Breakdown

| Area | Description |
|------|-------------|
| **Asset Selector** | Text field showing current asset path + browse button |
| **Preview Canvas** | Interactive sprite preview with draggable pivot point |
| **Pivot Fields** | X/Y drag float fields for precise pivot positioning (0.0 to 1.0) |
| **Preset Buttons** | 9-grid quick presets (TL, TC, TR, ML, CTR, MR, BL, BC, BR) |
| **Options** | Toggle buttons for Pixel Snap, Grid overlay, and Crosshair visibility |
| **Sprite Sheet Grid** | Thumbnail strip for selecting individual sprites (spritesheet only) |
| **Apply Mode** | Radio buttons: apply to all sprites or selected only (spritesheet only) |
| **Zoom Control** | Slider + quick buttons (1x, 2x, 4x) in footer |
| **Action Buttons** | Cancel (discard), Apply (apply in-memory), Save (persist to file) |

---

## Data Model Changes

### Metadata Storage Location

Store all asset metadata in a **separate folder** that is NOT scanned by the AssetBrowserPanel:

```
gameData/
├── assets/              ← Scanned by AssetManager (assetRoot)
│   ├── sprites/
│   │   └── player.png
│   └── spritesheets/
│       └── player.spritesheet
└── .metadata/           ← NOT scanned, invisible to Asset Browser
    └── sprites/
        └── player.png.meta
```

**Why separate folder?**
- `AssetManager.scanAll()` only scans `assetRoot` (`gameData/assets/`)
- Files in `gameData/.metadata/` are never discovered by the Asset Browser
- Clean separation between source assets and editor metadata
- Hidden folder convention (`.metadata`) makes intent clear

### Standalone Sprites: Metadata Files

Metadata is organized **per asset type**. Each asset type has one metadata class that can grow with new features:

```json
// gameData/.metadata/sprites/player.png.meta
{
  // Pivot editing (Phase 1)
  "pivotX": 0.5,
  "pivotY": 0.0,
  "pixelsPerUnitOverride": null,

  // 9-slice (future)
  "nineSlice": {
    "left": 4,
    "right": 4,
    "top": 4,
    "bottom": 4
  },

  // Physics shape (future)
  "physicsShape": {
    "type": "box",
    "offsetX": 0,
    "offsetY": 0,
    "width": 16,
    "height": 16
  }
}
```

**Path mapping:** Asset path → Metadata path
- `sprites/player.png` → `.metadata/sprites/player.png.meta`
- `sprites/characters/hero.png` → `.metadata/sprites/characters/hero.png.meta`

### Sprite Sheets: Extend Existing JSON Format

Add pivot fields directly to the spritesheet JSON (no separate metadata needed):

```json
// gameData/assets/spritesheets/player.spritesheet
{
  "texture": "sprites/characters/Char1_32x32.png",
  "spriteWidth": 32,
  "spriteHeight": 32,
  "offsetY": 16,
  "pivotX": 0.5,          // NEW: Default pivot for all sprites
  "pivotY": 0.0,          // NEW: Default pivot for all sprites
  "spritePivots": {       // NEW: Per-sprite overrides (optional)
    "3": { "pivotX": 0.5, "pivotY": 0.5 },
    "7": { "pivotX": 0.3, "pivotY": 0.2 }
  }
}
```

### Summary

| Asset Type | Metadata Location | Reason |
|------------|------------------|--------|
| Standalone Sprite | `gameData/.metadata/{path}.meta` | Separate from binary image files |
| Sprite Sheet | Same `.spritesheet` file | Already a JSON file, keep data together |

---

## Implementation Phases

### Phase 1: Core Infrastructure ✅ COMPLETED
**Goal:** Data model, metadata system, and persistence layer

**Files Created:**
1. ✅ `EditorCapability.java` - Enum for loader capabilities (PIVOT_EDITING, etc.)
2. ✅ `AssetMetadata.java` - Centralized metadata file access utility
3. ✅ `SpriteMetadata.java` - Data class for sprite metadata (pivot, ppu override, future fields)

**Files Modified:**
1. ✅ `AssetLoader.java` - Add `getEditorCapabilities()` default method
2. ✅ `SpriteLoader.java` - Override `getEditorCapabilities()`, load metadata on sprite load
3. ✅ `SpriteSheet.java` - Add `defaultPivotX/Y` and `spritePivots` map (completed in Phase 4)
4. ✅ `SpriteSheetLoader.java` - Override `getEditorCapabilities()`, load/save pivots (completed in Phase 4)

**Deliverables:**
- ✅ Generic capability system for loaders
- ✅ Centralized metadata access utility
- ✅ Pivots persist across editor/game restarts (for standalone sprites)
- ✅ Existing assets continue to work (backward compatible)

---

### Phase 2: Pivot Editor Panel & Integration ✅ COMPLETED
**Goal:** Functional modal with sprite preview, pivot editing, and editor integration

**Files Created:**
1. ✅ `PivotEditorPanel.java` - Main modal panel class with:
   - Asset path display
   - Sprite preview rendering with ImGui
   - X/Y sliders for pivot position
   - 9-grid preset buttons
   - Draggable pivot point on preview canvas
   - Zoom control for preview
   - Grid overlay option
   - Origin crosshair toggle
   - Apply and Cancel buttons

**Files Modified:**
1. ✅ `EditorUIController.java` - Register and render panel
2. ✅ `EditorMenuBar.java` - Add "Pivot Editor..." to Edit menu
3. ✅ `AssetBrowserPanel.java` - Add context menu with capability checks

**Integration:**
- ✅ Menu: Edit > Pivot Editor...
- ✅ Asset Browser: Right-click → "Edit Pivot..."

---

### Phase 3: Interactive Preview Enhancements ✅ COMPLETED
**Goal:** Enhanced visual pivot editing

**Enhancements to PivotEditorPanel:**
- ✅ Pixel-snap option for pivot positioning
- ✅ Drag float input fields for X/Y pivot values (labels on left, fields on right)
- ✅ Zoom controls moved to footer (slider + 1x/2x/4x quick buttons)
- ✅ Toggle buttons for Pixel Snap, Grid, and Crosshair options (green when active)
- ✅ Undo/redo support for pivot field editing (using SetterUndoCommand)
- ✅ Asset picker dialog with tabs (Sprites/Spritesheets) and preview panel
- ✅ Sprite sheet sprite selector shows thumbnail previews instead of numbered buttons
- ✅ Dynamic height calculation for sprite sheet mode to prevent scrolling

---

### Phase 4: Sprite Sheet Support ✅ COMPLETED
**Goal:** Edit pivots for sprite sheets with per-sprite options

**Enhancements to PivotEditorPanel:**
- ✅ Sprite grid/strip selector with visual previews
- ✅ "Apply to all" vs "Apply to selected" radio buttons
- ✅ Click navigation between sprites (loads pivot for selected sprite)
- ✅ Apply to all sets default pivot and clears per-sprite overrides

**Files Modified:**
1. ✅ `SpriteSheet.java` - Added per-sprite pivot storage:
   - `defaultPivotX`, `defaultPivotY` fields
   - `spritePivots` map for per-sprite overrides
   - `setDefaultPivot()`, `setSpritePivot()`, `getEffectivePivot()`
   - `hasSpritePivotOverride()`, `getSpritePivots()`, `clearSpritePivots()`
   - `getSprite()` now applies pivot when creating sprites

2. ✅ `SpriteSheetLoader.java` - Load/save pivot data:
   - Load: reads `pivotX`, `pivotY` and `spritePivots` from JSON
   - Save: writes pivot fields only if non-default

3. ✅ `PivotEditorPanel.java` - Per-sprite pivot editing:
   - `loadAsset()` loads pivot from sprite sheet
   - `loadPivotForSelectedSprite()` called when sprite selection changes
   - `applyPivot()` saves via SpriteSheetLoader when saving to file

---

### Phase 5: Polish & UX ✅ PARTIALLY COMPLETED
**Goal:** Production-quality UX

**Polish:**
- ✅ Undo/redo support for pivot changes (via SetterUndoCommand pattern)
- ⬜ Keyboard navigation (removed - decided against for simplicity)
- ✅ Tooltip hints (on all buttons)
- ✅ Error handling for missing assets
- ✅ Browse button functionality (asset picker with tabs and preview)
- ⬜ Optional keyboard shortcut for menu (not implemented)

---

## File Structure Summary

### New Files
```
src/main/java/com/pocket/rpg/
├── editor/
│   └── panels/
│       └── PivotEditorPanel.java          # Main pivot editor modal
└── resources/
    ├── EditorCapability.java              # Enum: PIVOT_EDITING, NINE_SLICE, etc.
    ├── AssetMetadata.java                 # Centralized metadata access utility
    └── SpriteMetadata.java                # Sprite metadata (pivot, ppu, future fields)

gameData/
└── .metadata/                             # Metadata storage (not scanned by Asset Browser)
    └── sprites/
        └── *.meta                         # Per-sprite metadata files
```

### Modified Files
```
src/main/java/com/pocket/rpg/
├── editor/
│   ├── EditorUIController.java            # Register pivot panel
│   ├── panels/
│   │   └── AssetBrowserPanel.java         # Add context menu with capability checks
│   └── ui/
│       └── EditorMenuBar.java             # Add menu item
├── rendering/
│   └── resources/
│       └── SpriteSheet.java               # Add pivot storage (default + per-sprite) [Phase 4]
└── resources/
    ├── AssetLoader.java                   # Add getEditorCapabilities() method
    └── loaders/
        ├── SpriteLoader.java              # Add capability, load metadata
        └── SpriteSheetLoader.java         # Add capability, load/save pivot fields [Phase 4]
```

---

## Open Questions

1. **Default pivot for new sprites:** Keep 0.5, 0.5 (center) or change to 0.5, 0.0 (bottom-center) which is more common for game sprites?

2. **Animation frame pivots:** Should animation frames be able to override sprite pivots? (Out of scope for initial implementation)

3. **PPU override in pivot editor:** Include pixels-per-unit override editing in the same panel, or keep it separate?

## Resolved Decisions

1. **Metadata storage:** `gameData/.metadata/` folder (separate from scanned assets)
2. **Metadata file naming:** `{assetPath}.meta` (e.g., `sprites/player.png.meta`)
3. **Context menu integration:** Use `EditorCapability` enum via `AssetLoader.getEditorCapabilities()`
4. **Metadata organization:** One metadata class per asset type (e.g., `SpriteMetadata` contains all sprite-related metadata)

---

## Design Decisions (Implementation Notes)

### UI Layout Decisions

1. **Panel Split Ratio (70/30):** Left preview area gets 70% width, right buttons panel gets 30%. This provides enough space for the pivot X/Y fields while maintaining a large preview area.

2. **Pivot Field Labels:** Labels ("X:", "Y:") are placed on the **left** of the drag fields, following the pattern used elsewhere in the editor (e.g., inspector fields). This was achieved using:
   ```java
   ImGui.text("X:");
   ImGui.sameLine();
   ImGui.setNextItemWidth(...);
   ImGui.dragFloat("##PivotX", ...);
   ```

3. **Dynamic Height Calculation:** The main content area height is calculated dynamically based on whether sprite sheet mode is active:
   ```java
   float footerHeight = 40;
   float spriteSheetHeight = (isSpriteSheet) ? 120 : 0;
   float reservedHeight = footerHeight + spriteSheetHeight + 20;
   float availableHeight = ImGui.getContentRegionAvailY() - reservedHeight;
   ```
   This prevents scrolling in sprite sheet mode.

4. **Modal Size:** Fixed at 750x680 to accommodate all content including sprite sheet selector without scrolling.

### Undo/Redo Implementation

Undo support for pivot fields follows the pattern from `VectorEditors.java`:
1. Capture start value on `ImGui.isItemActivated()`
2. Push `SetterUndoCommand` on `ImGui.isItemDeactivatedAfterEdit()`
3. Each axis (X/Y) has separate undo tracking for the drag fields

**Preview Canvas Dragging:**
- Captures both X and Y start values when drag begins (`isMouseClicked`)
- Pushes a combined undo command when drag ends (`!isMouseDown`)
- Uses `float[]` array to store both values in a single undo command

### Asset Picker Design

1. **Two-Tab Layout:** Instead of collapsible headers, uses tabs for "Sprites" and "Spritesheets" on the left panel with a preview panel on the right.
2. **Interaction:**
   - Single click: Shows preview in right panel
   - Double click: Loads asset to pivot editor and closes dialog
   - "Load" button: Same as double click
3. **Preview:** Uses `AssetPreviewRegistry.render()` for consistent preview rendering.

### Options Buttons

Toggle buttons (Pixel Snap, Grid, Crosshair) use **green color** when active instead of blue, matching the "Save" button styling. The push/pop color pattern captures state **before** the button click to avoid mismatches:
```java
boolean wasActive = activeState;
if (wasActive) ImGui.pushStyleColor(...);
if (ImGui.button(...)) activeState = !activeState;
if (wasActive) ImGui.popStyleColor(2);  // Uses captured state
```

### Removed Features

1. **Keyboard shortcuts:** Removed all keyboard shortcuts (number keys for presets, arrow keys for adjustment, etc.) to simplify the UI and reduce complexity.
2. **Transform preview (flip/rotate):** Removed as it wasn't actually rendering rotations and added unnecessary complexity.

### Sprite Sheet Pivot Storage (Phase 4)

1. **Dual-level pivots:** Sprite sheets have a default pivot (`defaultPivotX/Y`) and per-sprite overrides (`spritePivots` map). The `getEffectivePivot(frameIndex)` method returns the override if present, otherwise the default.

2. **Apply to All behavior:** When "Apply to All" is used:
   - Sets the default pivot
   - Clears all per-sprite overrides via `clearSpritePivots()`
   - Updates all cached sprites

3. **Apply to Selected behavior:** When "Apply to Selected Only" is used:
   - Sets a per-sprite override via `setSpritePivot(index, x, y)`
   - Only updates that specific cached sprite

4. **JSON format:**
   ```json
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
   - Default pivot only saved if non-center (0.5, 0.5)
   - Per-sprite overrides only saved if map is non-empty

5. **Pivot selection on sprite change:** When clicking a sprite in the grid selector, `loadPivotForSelectedSprite()` is called to load that sprite's effective pivot into the editor fields.
