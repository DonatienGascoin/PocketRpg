# Phase 4: Migrate Sprite Editor → SpriteEditorContent

## Context

Phases 1-3 of the unified AssetEditorPanel plan are complete. The sprite editor is currently a **modal popup** (`SpriteEditorPanel`, 1093 lines) that opens on top of everything else. Phase 4 converts it into a dockable `AssetEditorContent` implementation that runs inside the unified `AssetEditorPanel` shell, like all other asset types.

The user specifically requested: "merge the 2 panels, the modal one with pivot and 9 slice, etc. But also add the toggle for tilemap."

**Keep old `SpriteEditorPanel` for comparison** (same pattern as Phase 3 — both old and new coexist until user confirms everything works).

---

## What Changes

### New file: `editor/panels/content/SpriteEditorContent.java`

Migrates from `SpriteEditorPanel` into the `AssetEditorContent` interface.

**Layout (content area only, shell owns toolbar + hamburger sidebar):**

```
┌──────────────────────────────────────────────────────────────────────────────┐
│ [≡] player.png *  [Save] [Undo] [Redo] | Mode: ○Single ●Multiple [☑Tilemap]│
├──────────────────────────────────────────────────────────────────────────────┤
│  [Slicing] [Pivot] [9-Slice]  ← tab bar                                     │
│  ┌─────────────────────────────────────┬──────────────────┐                  │
│  │                                     │                  │                  │
│  │         Texture Preview             │    Sidebar       │                  │
│  │    (TexturePreviewRenderer)         │  (tab-specific)  │                  │
│  │         + grid overlay              │                  │                  │
│  │         + pivot/9slice overlays     │                  │                  │
│  │                                     │                  │                  │
│  ├─────────────────────────────────────┤                  │                  │
│  │  Zoom: [===] [Reset] [Fit]         │                  │                  │
│  └─────────────────────────────────────┴──────────────────┘                  │
└──────────────────────────────────────────────────────────────────────────────┘
```

**Toolbar extras (`renderToolbarExtras()`):**
- Mode radio buttons: Single / Multiple
- Tilemap checkbox (only visible in Multiple mode) — edits `metadata.usableAsTileset`

**Content (`render()`):**
- Tab bar: Slicing (multiple only) | Pivot | 9-Slice
- Preview area (left): `TexturePreviewRenderer` with grid/pivot/9-slice overlays
- Sidebar (right): Tab-specific controls (same as current `renderSidebar()`)
- Zoom controls: slider + Reset + Fit buttons (moved from footer to below preview)

**What's handled by the shell:**
- Save button (Ctrl+S) → calls `customSave()`
- Undo/Redo (Ctrl+Z/Y) → `UndoManager` target stacks
- Asset selection → hamburger sidebar (replaces `TextureBrowserDialog`)
- Dirty indicator in toolbar

**What's removed:**
- `TextureBrowserDialog` — hamburger sidebar replaces it
- Cancel/Save footer — shell handles save, undo replaces cancel
- Modal popup behavior (`beginPopupModal` / `openPopup`)
- `shouldOpen` / `isOpen` state — shell manages open state

### Key implementation details

**Asset class:** `Sprite.class` — SpriteLoader maps `.png` files to Sprite.class

**onAssetLoaded(path, asset, shell):**
- Cast asset to `Sprite`, get `texture = sprite.getTexture()`
- Load metadata: `AssetMetadata.load(path, SpriteMetadata.class)` (create new if null)
- Store `originalMetadata = copyMetadata(metadata)` for potential revert
- Initialize tabs: `slicingTab.loadFromMetadata()`, `pivotTab.loadFromMetadata()`, `nineSliceTab.loadFromMetadata()`
- Auto-fit preview: `previewRenderer.fit(texture, ...)`

**hasCustomSave() → true / customSave(path):**
1. `saveCurrentEditsToMetadata()` — write tab values into metadata object
2. `AssetMetadata.save(path, metadata)` — persist to `.metadata/` file
3. `applySavedMetadataToSprite()` — update in-memory Sprite so other components see changes
4. Publish `AssetChangedEvent(path, MODIFIED)`

**Undo pattern — SnapshotCommand with SpriteMetadata:**
- Use deferred snapshot like other contents: capture `beforeSnapshot` on change, flush as `SnapshotCommand` to UndoManager at next render start
- Debounce: same 300ms debounce for drag operations (pivot dragging, 9-slice handle dragging)
- Restorer: `copyMetadata()` + `reloadTabs()` to restore full state
- This replaces the local `undoStack`/`redoStack` Deques from the old panel

**copyMetadata() bug fix:**
The old `copyMetadata()` does NOT copy `usableAsTileset`. The new implementation must include it:
```java
copy.usableAsTileset = source.usableAsTileset;
```

**Tilemap toggle:**
- In `renderToolbarExtras()`, after mode radio buttons:
  ```java
  if (isMultipleMode) {
      ImGui.sameLine();
      boolean tilemap = metadata.usableAsTileset != null && metadata.usableAsTileset;
      if (ImGui.checkbox("Tilemap", tilemap)) {
          captureUndoState();
          metadata.usableAsTileset = !tilemap ? true : null;
          shell.markDirty();
      }
  }
  ```
- Setting `usableAsTileset = null` (rather than `false`) keeps the metadata file minimal

**Tab components — reuse as-is:**
- `SlicingEditorTab` — grid settings sidebar (implements `OnChangeListener`)
- `PivotEditorTab` — pivot editing sidebar + preview overlay (implements `Listener`)
- `NineSliceEditorTab` — 9-slice sidebar + preview overlay (implements `Listener`)
- `TexturePreviewRenderer` — zoom/pan canvas, grid overlay, coordinate conversion

**Mode switching:**
- Single→Multiple: `switchToMultipleMode()` with undo snapshot before conversion
- Multiple→Single: confirmation popup (`renderSingleModeConfirmation()`) then `switchToSingleMode()` with undo snapshot

### Modify: `EditorUIController.java`

Add Sprite content registration alongside existing registrations:
```java
assetEditorPanel.getContentRegistry().register(
    com.pocket.rpg.rendering.resources.Sprite.class,
    com.pocket.rpg.editor.panels.content.SpriteEditorContent::new);
```

**Keep old SpriteEditorPanel for comparison** — don't remove any existing sprite panel wiring yet. Both old (popup) and new (docked) will coexist. Double-click still opens old popup; hamburger sidebar opens new.

### Do NOT modify yet (deferred to Phase 5):

- `SpriteLoader.getEditorPanelType()` — keeps returning `SPRITE_EDITOR`
- `AssetBrowserPanel.spriteEditorPanel` reference — stays
- `EditorPanelType.SPRITE_EDITOR` — stays
- `OpenSpriteEditorEvent` handler — stays pointing to old panel

---

## Critical Files

| File | Action |
|------|--------|
| `editor/panels/content/SpriteEditorContent.java` | **NEW** — ~700 lines |
| `editor/EditorUIController.java` | Add 3-line registration |
| `editor/panels/SpriteEditorPanel.java` | **Keep** for comparison |
| `editor/panels/spriteeditor/SlicingEditorTab.java` | Reuse as-is |
| `editor/panels/spriteeditor/PivotEditorTab.java` | Reuse as-is |
| `editor/panels/spriteeditor/NineSliceEditorTab.java` | Reuse as-is |
| `editor/panels/spriteeditor/TexturePreviewRenderer.java` | Reuse as-is |
| `resources/SpriteMetadata.java` | Read-only reference |
| `resources/AssetMetadata.java` | Used for load/save |
| `editor/panels/AssetEditorContent.java` | Interface to implement |
| `editor/panels/AssetEditorShell.java` | Shell API reference |
| `editor/undo/commands/SnapshotCommand.java` | Used for undo |

---

## Verification

1. `mvn compile` — must succeed
2. Run editor
3. Test matrix:
   - Open `.png` via Asset Editor sidebar → content renders correctly
   - Mode toggle: Single ↔ Multiple works, confirmation popup for Multiple→Single
   - Slicing tab: grid settings change preview grid
   - Pivot tab: drag pivot in preview, sidebar values update
   - 9-Slice tab: drag handles in preview, sidebar values update
   - Tilemap checkbox: appears only in Multiple mode, saves correctly
   - Zoom: slider, Reset, Fit all work
   - Save (Ctrl+S): writes `.metadata/` file, in-memory Sprite updates
   - Undo/Redo (Ctrl+Z/Y): reverts metadata changes
   - Cell selection: click sprite in preview, sidebar shows per-sprite values
   - Apply to All: pivot/9-slice apply-to-all toggle works
   - Old SpriteEditorPanel: still opens via double-click, context menu in asset browser
