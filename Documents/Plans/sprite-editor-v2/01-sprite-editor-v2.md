# Sprite Editor V2 Implementation Plan

**Status: COMPLETE**

**Prerequisite: Complete [00-asset-model-unification.md](./00-asset-model-unification.md) first**

## Overview

Build a Unity-style Sprite Editor that:
1. Shows **full texture** in the preview with grid overlay (for multiple mode)
2. Allows **click-to-select** sprites directly in the preview
3. Provides **Slicing**, **Pivot**, and **9-Slice** tabs with context-aware overlays
4. Supports **creating** new multiple-mode sprites (setting up grid on a texture)
5. Supports **deleting** sprite metadata (reverting to single mode)

The V2 editor coexists with V1 until the final phase.

---

## Asset Model (Post-Unification)

After completing the asset model unification:

| Texture Mode | Preview | Tabs Available |
|--------------|---------|----------------|
| **Single** | Single sprite, centered | Pivot, 9-Slice |
| **Multiple** | Full texture with grid | Slicing, Pivot, 9-Slice |
| **No metadata** | Full texture (treated as single) | Mode selector + Pivot, 9-Slice |

---

## UI Layouts

### Layout 1: No Asset Selected

```
┌─────────────────────────────────────────────────────────────────────────────┐
│ Asset: [None selected_________] [Browse...]  [+ New Multiple...]            │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│                                                                             │
│                         Select an asset to edit                             │
│                                                                             │
│                         [Browse...] to select a texture                     │
│                                                                             │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Layout 2: Single Mode Sprite

```
┌─────────────────────────────────────────────────────────────────────────────┐
│ Asset: [icon.png______________] [Browse...]  [+ New Multiple...]            │
│ Mode:  ● Single  ○ Multiple                                                 │
├─────────────────────────────────────────────────────────────────────────────┤
│ [Pivot]  [9-Slice]                         (No Slicing tab for single mode) │
├─────────────────────────────────────────────┬───────────────────────────────┤
│                                             │ Sprite Info                   │
│                                             │ Size: 32x32 px                │
│         ┌─────────────────┐                 ├───────────────────────────────┤
│         │                 │                 │ Pivot                         │
│         │                 │                 │ X: [0.500]  Y: [0.000]        │
│         │        ⊕        │                 │                               │
│         │                 │                 │ [TL] [TC] [TR]                │
│         │                 │                 │ [ML] [CC] [MR]                │
│         └─────────────────┘                 │ [BL] [BC] [BR]                │
│                                             │                               │
│                                             │ [Pixel Snap] [Grid]           │
├─────────────────────────────────────────────┴───────────────────────────────┤
│ Zoom: [====●=====]  [Reset] [Fit]                     [Cancel]  [💾 Save]   │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Layout 3: Multiple Mode - Slicing Tab

```
┌─────────────────────────────────────────────────────────────────────────────┐
│ Asset: [player.png____________] [Browse...]  [🗑 Clear Metadata]            │
│ Mode:  ○ Single  ● Multiple                                                 │
├─────────────────────────────────────────────────────────────────────────────┤
│ [Slicing]  [Pivot]  [9-Slice]                                               │
├─────────────────────────────────────────────┬───────────────────────────────┤
│                                             │ Grid Settings                 │
│   ┌────┬────┬────┬────┐                     ├───────────────────────────────┤
│   │ 0  │ 1  │ 2  │ 3  │                     │ Sprite Size:                  │
│   ├────┼────┼────┼────┤                     │ W: [16]  H: [16]  [8][16][32] │
│   │ 4  │ 5  │ 6  │ 7  │                     │                               │
│   ├────┼────┼────┼────┤                     │ Spacing:                      │
│   │ 8  │ 9  │ 10 │ 11 │                     │ X: [0]   Y: [0]               │
│   └────┴────┴────┴────┘                     │                               │
│                                             │ Offset:                       │
│   Green = sprite boundaries                 │ X: [0]   Y: [0]               │
│   Numbers = sprite indices                  ├───────────────────────────────┤
│                                             │ Grid: 4x3 = 12 sprites        │
├─────────────────────────────────────────────┴───────────────────────────────┤
│ Zoom: [====●=====]  [Reset] [Fit]                     [Cancel]  [💾 Save]   │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Layout 4: Multiple Mode - Pivot Tab

```
┌─────────────────────────────────────────────────────────────────────────────┐
│ Asset: [player.png____________] [Browse...]  [🗑 Clear Metadata]            │
│ Mode:  ○ Single  ● Multiple                                                 │
├─────────────────────────────────────────────────────────────────────────────┤
│ [Slicing]  [Pivot]  [9-Slice]                                               │
├─────────────────────────────────────────────┬───────────────────────────────┤
│                                             │ Selected: Sprite #5           │
│   ┌────┬────┬────┬────┐                     │ Size: 16x16 px                │
│   │ ·  │ ·  │ ·  │ ·  │                     ├───────────────────────────────┤
│   ├────┼────┼────┼────┤                     │ Pivot                         │
│   │ ·  │ ⊕  │ ·  │ ·  │ ← #5 selected      │ X: [0.500]  Y: [0.000]        │
│   ├────┼────┼────┼────┤   (highlighted)     │                               │
│   │ ·  │ ·  │ ·  │ ·  │                     │ [TL] [TC] [TR]                │
│   └────┴────┴────┴────┘                     │ [ML] [CC] [MR]                │
│                                             │ [BL] [BC] [BR]                │
│   · = pivot markers (all visible)           │                               │
│   Click cell to select                      │ ☑ Apply to All Sprites        │
│   Drag ⊕ to move pivot                      │ [Pixel Snap] [Grid] [Cross]   │
├─────────────────────────────────────────────┴───────────────────────────────┤
│ Zoom: [====●=====]  [Reset] [Fit]                     [Cancel]  [💾 Save]   │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Layout 5: Multiple Mode - 9-Slice Tab

```
┌─────────────────────────────────────────────────────────────────────────────┐
│ Asset: [player.png____________] [Browse...]  [🗑 Clear Metadata]            │
│ Mode:  ○ Single  ● Multiple                                                 │
├─────────────────────────────────────────────────────────────────────────────┤
│ [Slicing]  [Pivot]  [9-Slice]                                               │
├─────────────────────────────────────────────┬───────────────────────────────┤
│                                             │ Selected: Sprite #5           │
│   ┌────┬────┬────┬────┐                     │ Size: 16x16 px                │
│   │    │    │    │    │                     ├───────────────────────────────┤
│   ├────┼────┼────┼────┤                     │ 9-Slice Borders               │
│   │    │╔══╗│    │    │ ← borders shown    │ L: [4]  R: [4]                │
│   ├────┼╚══╝┼────┼────┤   on selected      │ T: [4]  B: [4]                │
│   │    │    │    │    │                     │                               │
│   └────┴────┴────┴────┘                     │ [4px] [8px] [16px] [Clear]    │
│                                             │                               │
│   Click cell to select                      │ ☑ Apply to All Sprites        │
│   Drag lines to adjust borders              │                               │
├─────────────────────────────────────────────┴───────────────────────────────┤
│ Zoom: [====●=====]  [Reset] [Fit]                     [Cancel]  [💾 Save]   │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Asset Browser Dialog

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           Select Texture                                    │
├─────────────────────────────────────────────────────────────────────────────┤
│ [Search: _________________________]                                         │
├─────────────────────────────────────────────┬───────────────────────────────┤
│                                             │                               │
│   sprites/                                  │        Preview                │
│     icon.png .............. [Single]        │                               │
│     button.png ............ [Single] 9S    │    ┌─────────────┐            │
│   spritesheets/                             │    │             │            │
│     player.png ............ [Multiple] 16   │    │   player    │            │
│     outdoor.png ........... [Multiple] 64   │    │             │            │
│     trees.png ............. [Multiple] 12   │    └─────────────┘            │
│   textures/                                 │                               │
│     background.png ........ [No meta]       │    Mode: Multiple             │
│     tileset_new.png ....... [No meta]       │    Grid: 4x4 = 16 sprites    │
│                                             │                               │
├─────────────────────────────────────────────┴───────────────────────────────┤
│                                                    [Cancel]  [Select]       │
└─────────────────────────────────────────────────────────────────────────────┘

Legend:
  [Single]   = spriteMode: single
  [Multiple] = spriteMode: multiple (shows sprite count)
  [No meta]  = no .meta file (can be converted)
  9S         = has 9-slice data
```

### Clear Metadata Confirmation

```
┌───────────────────────────────────────────┐
│         Clear Sprite Metadata?            │
├───────────────────────────────────────────┤
│                                           │
│  This will delete the metadata for:       │
│  "player.png"                             │
│                                           │
│  The texture will revert to single mode   │
│  with default settings.                   │
│                                           │
│  ⚠ Scenes using sprites from this texture │
│    may have broken references             │
│                                           │
├───────────────────────────────────────────┤
│              [Cancel]  [🗑 Clear]          │
└───────────────────────────────────────────┘
```

---

## Implementation Phases

### Phase 1: Texture Preview Renderer

Create a preview renderer that shows full texture with optional grid overlay.

**Files:**

| File | Change |
|------|--------|
| `spriteeditor/TexturePreviewRenderer.java` | **NEW** - Full texture preview with grid, selection, overlays |

**Tasks:**
- [x] Create `TexturePreviewRenderer` class
- [x] Implement full texture rendering (not just one sprite)
- [x] Implement grid overlay drawing (for multiple mode)
- [x] Implement `hitTestCell(mouseX, mouseY)` for click-to-select
- [x] Implement pivot marker drawing (all sprites, highlight selected)
- [x] Implement 9-slice border drawing (on selected sprite)
- [x] Implement zoom/pan (reuse logic from `SpritePreviewRenderer`)
- [x] Add coordinate conversion: screen ↔ texture pixel ↔ cell index ↔ normalized

**Key methods:**

```java
public class TexturePreviewRenderer {
    // Preview lifecycle
    public boolean beginPreview(Texture texture, SpriteMetadata metadata, float width, float height);
    public void endPreview();

    // Grid overlay
    public void drawGridOverlay(GridSettings grid);
    public void drawCellNumbers(GridSettings grid);

    // Selection
    public int hitTestCell(float mouseX, float mouseY);
    public void setSelectedCell(int index);
    public void drawSelectionHighlight(int cellIndex);

    // Pivot overlay
    public void drawAllPivotMarkers(float[] defaultPivot, Map<Integer, float[]> overrides);
    public void drawPivotMarker(int cellIndex, float pivotX, float pivotY, boolean isSelected);

    // 9-slice overlay
    public void drawNineSliceBorders(int cellIndex, NineSliceData data);

    // Coordinate conversion
    public int screenToCellIndex(float screenX, float screenY);
    public float[] cellToScreenRect(int cellIndex);  // returns [x, y, w, h]
    public float[] screenToNormalized(float screenX, float screenY, int cellIndex);
}
```

---

### Phase 2: V2 Panel Shell

Create the new panel with mode selector and tab structure.

**Files:**

| File | Change |
|------|--------|
| `panels/SpriteEditorPanelV2.java` | **NEW** - Main V2 panel |
| `EditorUIController.java` | Register V2 panel, add menu entry |

**Tasks:**
- [x] Create `SpriteEditorPanelV2` as modal popup
- [x] Implement header: asset path, Browse button, mode selector
- [x] Implement mode switching (Single ↔ Multiple)
- [x] Implement tab bar (Slicing only visible in Multiple mode)
- [x] Implement footer: zoom slider, Reset/Fit buttons, Cancel/Save
- [x] Add "Sprite Editor V2" menu item under Edit menu
- [x] Wire up asset loading via `SpriteMetadata`

**Panel state:**

```java
public class SpriteEditorPanelV2 {
    // Current asset
    private String texturePath;
    private Texture texture;
    private SpriteMetadata metadata;
    private boolean hasUnsavedChanges;

    // Multiple mode state
    private SpriteGrid spriteGrid;
    private int selectedSpriteIndex = 0;

    // UI state
    private EditorTab activeTab = EditorTab.PIVOT;
    private TexturePreviewRenderer previewRenderer;

    public enum EditorTab { SLICING, PIVOT, NINE_SLICE }
}
```

---

### Phase 3: Asset Browser Integration

Implement the asset browser dialog showing textures with their mode.

**Files:**

| File | Change |
|------|--------|
| `spriteeditor/TextureBrowserDialog.java` | **NEW** - Browse textures with mode info |
| `panels/SpriteEditorPanelV2.java` | Wire up Browse button |

**Tasks:**
- [x] Create `TextureBrowserDialog` class
- [x] Scan for all texture files (`.png`, `.jpg`, etc.)
- [x] Load metadata for each to determine mode
- [x] Display mode badge: [Single], [Multiple], [No meta]
- [x] Show sprite count for multiple mode
- [x] Show 9S indicator if has 9-slice data
- [x] Implement search/filter
- [x] Implement preview panel on right side
- [x] Wire up to SpriteEditorPanelV2

---

### Phase 4: Slicing Tab

Implement the slicing tab for configuring grid parameters.

**Files:**

| File | Change |
|------|--------|
| `spriteeditor/SlicingEditorTab.java` | **NEW** - Grid configuration UI |

**Tasks:**
- [x] Create `SlicingEditorTab` class
- [x] Implement grid parameter inputs (width, height, spacing, offset)
- [x] Implement preset buttons (8, 16, 32)
- [x] Show grid info (columns × rows = total)
- [x] Wire preview to show grid overlay
- [x] Show warning if 0 sprites fit
- [x] Live preview as parameters change
- [x] Track changes for save

**Tab layout:**

```java
public class SlicingEditorTab {
    private final TexturePreviewRenderer previewRenderer;

    // Grid parameters (bound to metadata.grid)
    private int spriteWidth = 16;
    private int spriteHeight = 16;
    private int spacingX = 0;
    private int spacingY = 0;
    private int offsetX = 0;
    private int offsetY = 0;

    public void render(Texture texture, SpriteMetadata metadata, float previewWidth, float previewHeight);
    public void applyToMetadata(SpriteMetadata metadata);
}
```

---

### Phase 5: Pivot Tab V2

Implement the pivot tab with full texture view and visible pivots.

**Files:**

| File | Change |
|------|--------|
| `spriteeditor/PivotEditorTabV2.java` | **NEW** - Pivot editing with texture view |

**Tasks:**
- [x] Create `PivotEditorTabV2` class
- [x] Use `TexturePreviewRenderer` for preview
- [x] Implement click-to-select sprite cells (multiple mode)
- [x] Draw pivot markers on all sprites (dimmed for non-selected)
- [x] Implement pivot dragging for selected sprite
- [x] Implement "Apply to All" checkbox
- [x] Port preset buttons from V1
- [x] Port pixel snap option from V1
- [x] Implement undo/redo
- [x] Handle single mode (full texture, one pivot)

---

### Phase 6: 9-Slice Tab V2

Implement the 9-slice tab with full texture view.

**Files:**

| File | Change |
|------|--------|
| `spriteeditor/NineSliceEditorTabV2.java` | **NEW** - 9-slice editing with texture view |

**Tasks:**
- [x] Create `NineSliceEditorTabV2` class
- [x] Use `TexturePreviewRenderer` for preview
- [x] Implement click-to-select sprite cells (multiple mode)
- [x] Draw 9-slice borders on selected sprite
- [x] Implement border line dragging
- [x] Implement "Apply to All" checkbox
- [x] Port preset buttons from V1
- [x] ~~Implement scaled preview showing 9-slice result~~ (cancelled - not needed)
- [x] Implement undo/redo
- [x] Handle single mode (full texture, one set of borders)

---

### Phase 7: Mode Switching & New Multiple

Implement mode switching and creating new multiple-mode metadata.

**Files:**

| File | Change |
|------|--------|
| `panels/SpriteEditorPanelV2.java` | Mode switching logic |

**Tasks:**
- [x] Implement Single → Multiple mode switch:
  - Show slicing configuration
  - Create default grid settings
  - Preserve existing pivot/9-slice as defaults
  - Auto-focus Slicing tab
- [x] Implement Multiple → Single mode switch:
  - Confirm dialog (will lose per-sprite data)
  - Keep sprite 0's pivot/9-slice, or defaults
- [x] ~~Implement "New Multiple..." button~~ (cancelled - not needed)
- [x] Handle textures with no metadata (treat as single, allow conversion)

---

### Phase 8: Clear Metadata / Delete

~~Implement clearing sprite metadata.~~ **SKIPPED** - Edge case, can delete `.meta` files manually if needed.

**Tasks:**
- [x] ~~All tasks~~ (skipped)

---

### Phase 9: Migration & Cleanup

Replace V1 with V2 and clean up.

**Files:**

| File | Change |
|------|--------|
| `panels/SpriteEditorPanel.java` | **DELETE** |
| `spriteeditor/PivotEditorTab.java` | **DELETE** |
| `spriteeditor/NineSliceEditorTab.java` | **DELETE** |
| `spriteeditor/SpritePreviewRenderer.java` | **DELETE** (replaced by TexturePreviewRenderer) |
| `panels/SpriteEditorPanelV2.java` | Rename to `SpriteEditorPanel.java` |
| `spriteeditor/PivotEditorTabV2.java` | Rename to `PivotEditorTab.java` |
| `spriteeditor/NineSliceEditorTabV2.java` | Rename to `NineSliceEditorTab.java` |
| `EditorUIController.java` | Update menu entries |

**Tasks:**
- [x] Verify V2 is feature-complete
- [x] Test all workflows
- [x] Add "Edit in Sprite Editor" context menu option in AssetBrowserPanel (now uses new editor)
- [x] Update double-click on sprites to open new editor
- [x] Remove V1 files (SpriteEditorPanel.java, SpritePreviewRenderer.java, PivotEditorTab.java, NineSliceEditorTab.java)
- [x] Rename V2 files (removed "V2" suffix)
- [x] Update menu to use new panel (removed "Sprite Editor V2..." menu item)
- [x] Update any documentation

---

## File Change Summary

### New Files

| File | Purpose |
|------|---------|
| `spriteeditor/TexturePreviewRenderer.java` | Full texture preview with overlays |
| `panels/SpriteEditorPanelV2.java` | Main V2 panel |
| `spriteeditor/TextureBrowserDialog.java` | Asset browser with mode info |
| `spriteeditor/SlicingEditorTab.java` | Grid configuration tab |
| `spriteeditor/PivotEditorTabV2.java` | Pivot tab with texture view |
| `spriteeditor/NineSliceEditorTabV2.java` | 9-slice tab with texture view |

### Modified Files

| File | Change |
|------|--------|
| `EditorUIController.java` | Register V2 panel, menu entries |

### Deleted Files (Phase 9)

| File | Reason |
|------|--------|
| `panels/SpriteEditorPanel.java` | Replaced by V2 |
| `spriteeditor/PivotEditorTab.java` | Replaced by V2 |
| `spriteeditor/NineSliceEditorTab.java` | Replaced by V2 |
| `spriteeditor/SpritePreviewRenderer.java` | Replaced by TexturePreviewRenderer |

---

## Testing Strategy

### Phase 1-2 Testing
- [ ] Texture renders correctly in preview
- [ ] Grid overlay appears for multiple mode
- [ ] Zoom/pan works smoothly
- [ ] Mode selector works

### Phase 3 Testing
- [ ] Browser shows all textures
- [ ] Mode badges display correctly
- [ ] Search/filter works
- [ ] Preview panel shows selected texture

### Phase 4 Testing (Slicing)
- [ ] Grid parameters update preview live
- [ ] Presets apply correct values
- [ ] Validation warns on 0 sprites
- [ ] Changes tracked for save

### Phase 5 Testing (Pivot)
- [ ] All pivot markers visible
- [ ] Click selects correct sprite
- [ ] Pivot dragging works
- [ ] "Apply to All" works
- [ ] Single mode works

### Phase 6 Testing (9-Slice)
- [ ] Border lines visible
- [ ] Border dragging works
- [ ] "Apply to All" works
- [ ] Scaled preview works
- [ ] Single mode works

### Phase 7 Testing (Mode Switching)
- [ ] Single → Multiple preserves data
- [ ] Multiple → Single shows warning
- [ ] "New Multiple..." opens correctly

### Phase 8 Testing (Clear)
- [ ] Confirmation dialog appears
- [ ] Metadata file deleted
- [ ] Editor state cleared
- [ ] Registry updated

### Phase 9 Testing (Migration)
- [ ] All V1 features work in V2
- [ ] No broken references
- [ ] Menu entries work

---

## Dependencies

**This plan depends on:**
- [00-asset-model-unification.md](./00-asset-model-unification.md) - Must be completed first

**Uses these systems:**
- `SpriteMetadata` - For loading/saving sprite data
- `SpriteGrid` - For grid calculations (from unification plan)
- `AssetMetadata` - For metadata file operations
- `UndoManager` - For undo/redo support
- `TilesetRegistry` - For updating after mode changes

---

## Code Review

After implementation:
- Write review to `Documents/Plans/sprite-editor-v2/review-editor-v2.md`
