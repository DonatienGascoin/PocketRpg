# Editor Modes Refactor - Implementation Plan

This document describes the implementation plan for removing the mode system and transitioning to a panel-driven, modeless editor design.

## Implementation Status

| Phase | Status | Notes |
|-------|--------|-------|
| Phase 1: Decouple Selection | ✅ Complete | EditorSelectionManager, EditorContext, HierarchySelectionHandler, InspectorPanel updated |
| Phase 2: Panel Visibility | 🟡 In Progress | EditorPanel base class, Window menu, panel persistence added. PrefabBrowserPanel deletion pending |
| Phase 3: Panel-Driven Tools | ⬜ Pending | |
| Phase 4: Shortcuts Require Panel | ⬜ Pending | |
| Phase 5: F1/F2 Shortcuts | ⬜ Pending | |
| Phase 6: Remove Mode System | ⬜ Pending | |
| Phase 7: Polish | ⬜ Pending | |

**Prerequisite completed:** Renamed `EditorPanel` enum to `EditorPanelType` to free up the name for the new abstract class.

---

## Summary of Changes

| Change | Description |
|--------|-------------|
| Remove modes | Delete `EditorModeManager`, replace with panel-driven tool visibility |
| All panels always render | No more mode-based panel hiding |
| Panel-driven tools | Tile tools visible when Tileset Palette open, collision tools when Collision Panel open |
| Shortcuts require visibility | Tool shortcuts only work if their panel is open |
| F1/F2 toggle panels | F1 = Tileset Palette, F2 = Collision Panel |
| Window menu | Open/close panels from menu bar |
| Panel state persistence | Save/restore panel visibility across sessions |
| Remove Place Entity tool | Redundant with AssetBrowserPanel drag-and-drop |
| Remove PrefabBrowserPanel | Redundant with AssetBrowserPanel |
| Decouple selection | Selection persists independently of tools |

---

## Design Decisions

### 1. Shortcuts Only Work When Panel Is Open

**Rationale:** "If I can't see the button, the shortcut shouldn't work." This prevents unexpected behavior.

| Shortcut | Tileset Palette closed | Tileset Palette open |
|----------|------------------------|----------------------|
| B (Brush) | Does nothing | Activates Brush tool |
| E (Eraser) | Does nothing | Activates Eraser tool |
| F, R, I | Does nothing | Activates respective tool |

Same applies to collision tools and Collision Panel.

### 2. F1/F2 Toggle Panels

| Key | Action |
|-----|--------|
| F1 | Toggle Tileset Palette (open/close) |
| F2 | Toggle Collision Panel (open/close) |

**Toggle behavior:**
- If panel is closed → open it and focus it
- If panel is open → close it

### 3. Window Menu for Panel Management

Add a "Window" menu to the main menu bar:

```
┌─────────────────────────────────────────────┐
│ File  Edit  View  Window  Help              │
│                    ├─────────────────────┐  │
│                    │ ✓ Hierarchy         │  │
│                    │ ✓ Inspector         │  │
│                    │ ✓ Asset Browser     │  │
│                    │ ─────────────────── │  │
│                    │   Tileset Palette F1│  │
│                    │   Collision Panel F2│  │
│                    │ ─────────────────── │  │
│                    │   Reset Layout      │  │
│                    └─────────────────────┘  │
└─────────────────────────────────────────────┘
```

**Features:**
- Checkmark indicates panel is open
- Click to toggle panel visibility
- Shortcut keys shown for painting panels
- "Reset Layout" restores default panel state

### 4. Panel State Persistence

Panel open/closed state saved via existing `EditorConfig` with a Map for flexibility.

**Add to `EditorConfig.java`:**

```java
// Panel visibility state - Map allows dynamic panel registration
private Map<String, Boolean> panelVisibility = new HashMap<>();

public boolean isPanelOpen(String panelId) {
    return panelVisibility.getOrDefault(panelId, true); // default open
}

public void setPanelOpen(String panelId, boolean open) {
    panelVisibility.put(panelId, open);
    save(); // or mark dirty for batch save
}
```

**Abstract base class:**

```java
public abstract class EditorPanel {
    private final String panelId;
    private final boolean defaultOpen;
    private boolean isOpen;
    private EditorConfig config;

    protected EditorPanel(String panelId, boolean defaultOpen) {
        this.panelId = panelId;
        this.defaultOpen = defaultOpen;
    }

    /** Called once after construction */
    public void init(EditorConfig config) {
        this.config = config;
        this.isOpen = config.isPanelOpen(panelId, defaultOpen);
    }

    public boolean isOpen() { return isOpen; }

    public void setOpen(boolean open) {
        if (this.isOpen != open) {
            this.isOpen = open;
            config.setPanelOpen(panelId, open);
        }
    }

    public void toggle() { setOpen(!isOpen); }

    public String getPanelId() { return panelId; }

    /** Subclasses implement rendering */
    public abstract void render();
}
```

**Panel implementation - minimal code:**

```java
public class TilesetPalettePanel extends EditorPanel {

    public TilesetPalettePanel() {
        super("tilesetPalette", false); // id, defaultOpen
    }

    @Override
    public void render() {
        if (!isOpen()) return;
        if (ImGui.begin("Tileset Palette")) {
            // render content...
        }
        ImGui.end();
    }
}
```

**Flow:**

1. Panel extends `EditorPanel` with id + defaultOpen in constructor
2. `init(config)` called once after creation
3. `isOpen()`, `setOpen()`, `toggle()` all inherited - no duplication

**Behavior:**
- On startup: each panel restores its state via `initVisibility()`
- On panel open/close: `setOpen()` auto-persists to config
- New panels: just implement `EditorPanel` interface
- Default state: defined per-panel via `getDefaultOpen()`

### 5. Remove Place Entity Tool

**Rationale:** AssetBrowserPanel provides drag-and-drop entity placement.

**Changes:**
- Delete `EntityPlacerTool.java`
- Remove from tool registration in `EditorToolController`
- Remove from toolbar
- Remove shortcut (P)

### 6. Remove PrefabBrowserPanel

**Rationale:** AssetBrowserPanel already shows prefabs.

**Changes:**
- Delete `PrefabBrowserPanel.java`
- Remove from `EditorUIController` render calls
- Remove from Window menu

### 7. Decouple Selection from Tools

Selection and active tool are independent:

| Concept | What it controls |
|---------|------------------|
| Selection (entity, layer, etc.) | What Inspector shows |
| Active tool | What viewport clicks do |

Selection persists when:
- Switching tools
- Opening/closing panels
- Clicking in hierarchy (updates selection, tool unchanged)

---

## Implementation Phases

### Phase 1: Decouple Selection from Mode

**Goal:** Selection persists independently, Inspector always shows current selection.

**Tasks:**

1. **Create `EditorSelectionManager.java`**
   ```java
   public class EditorSelectionManager {
       public enum SelectionType { NONE, ENTITY, TILEMAP_LAYER, COLLISION_LAYER, CAMERA }

       private SelectionType type = SelectionType.NONE;
       private Set<EditorGameObject> selectedEntities = new HashSet<>();
       private int selectedLayerIndex = -1;

       private List<Consumer<SelectionType>> listeners = new ArrayList<>();

       public void selectEntity(EditorGameObject entity) { ... }
       public void selectEntities(Set<EditorGameObject> entities) { ... }
       public void selectTilemapLayer(int layerIndex) { ... }
       public void selectCollisionLayer() { ... }
       public void selectCamera() { ... }
       public void clearSelection() { ... }

       public void addListener(Consumer<SelectionType> listener) { ... }
   }
   ```

2. **Update `EditorContext.java`**
   - Add `EditorSelectionManager selectionManager` field
   - Initialize in `init()`
   - Add getter

3. **Update `HierarchySelectionHandler.java`**
   - Remove internal selection state (`cameraSelected`, `tilemapLayersSelected`, etc.)
   - Delegate all selection to `EditorSelectionManager`
   - Remove `onModeChanged` listener that clears selection

4. **Update `InspectorPanel.java`**
   - Remove mode checks
   - Query `selectionManager` for what to display
   - Listen to selection changes

**Test:** Switch modes (while they still exist), verify entity stays selected in Inspector.

---

### Phase 2: Panel Visibility and Persistence

**Goal:** All panels render based on saved state, with Window menu control.

**Tasks:**

1. **Add panel state to each panel class**

   For `TilesetPalettePanel.java`, `CollisionPanel.java`, `HierarchyPanel.java`, `InspectorPanel.java`, `AssetBrowserPanel.java`:
   ```java
   private boolean isOpen = true;  // default varies by panel

   public boolean isOpen() { return isOpen; }
   public void setOpen(boolean open) { this.isOpen = open; }
   public void toggle() { isOpen = !isOpen; }
   public void focus() { ImGui.setWindowFocus(WINDOW_NAME); }

   public void render() {
       if (!isOpen) return;
       // existing render code...
   }
   ```

2. **Update `EditorUIController.java`**
   - Remove `renderModePanels()` mode checks
   - Call render on all panels unconditionally
   - Add `renderWindowMenu()` method for Window menu
   - Track panel references for menu toggles

3. **Add Window menu rendering**
   ```java
   private void renderWindowMenu() {
       if (ImGui.beginMenu("Window")) {
           if (ImGui.menuItem("Hierarchy", "", hierarchyPanel.isOpen())) {
               hierarchyPanel.toggle();
           }
           if (ImGui.menuItem("Inspector", "", inspectorPanel.isOpen())) {
               inspectorPanel.toggle();
           }
           // ... etc
           ImGui.separator();
           if (ImGui.menuItem("Tileset Palette", "F1", tilesetPalette.isOpen())) {
               tilesetPalette.toggle();
           }
           if (ImGui.menuItem("Collision Panel", "F2", collisionPanel.isOpen())) {
               collisionPanel.toggle();
           }
           ImGui.separator();
           if (ImGui.menuItem("Reset Layout")) {
               resetPanelLayout();
           }
           ImGui.endMenu();
       }
   }
   ```

4. **Add persistence to `EditorConfig` or layout file**
   - Load panel states on startup
   - Save panel states on change
   - Add `resetPanelLayout()` method

5. **Delete `PrefabBrowserPanel.java`**
   - Remove class file
   - Remove from EditorUIController
   - Remove references

**Test:**
- Close a panel, restart editor → panel still closed
- Window menu shows correct checkmarks
- Reset Layout restores defaults

---

### Phase 3: Panel-Driven Tool Visibility

**Goal:** Tools appear in toolbar based on which panels are open.

**Tasks:**

1. **Update `SceneViewToolbar.java`**
   - Add panel references via constructor/setter
   - Update tool rendering:
     ```java
     private void renderToolButtons() {
         // Always show entity tools (Select, Move - no Place Entity)
         renderEntityTools();

         // Show tile tools only if palette is open
         if (tilesetPalette.isOpen()) {
             ImGui.sameLine();
             ImGui.text("|");
             ImGui.sameLine();
             renderTileTools();
         }

         // Show collision tools only if panel is open
         if (collisionPanel.isOpen()) {
             ImGui.sameLine();
             ImGui.text("|");
             ImGui.sameLine();
             renderCollisionTools();
         }
     }
     ```
   - Remove mode dropdown entirely

2. **Update `ToolManager.java`**
   - Add method to check if tool is currently available
   - Add `onToolBecameUnavailable` callback
   - When panel closes, if active tool was from that panel, switch to Select

3. **Delete `EntityPlacerTool.java`**
   - Delete class file
   - Remove from `EditorToolController.createTools()`
   - Update `ENTITY_TOOLS` array in `SceneViewToolbar`

4. **Update `EditorToolController.java`**
   - Remove EntityPlacerTool creation
   - Remove mode-based tool filtering
   - Connect panel close events to tool availability

**Test:**
- Open Tileset Palette → tile tools appear
- Close Tileset Palette while Brush active → switches to Select
- Place Entity tool no longer exists

---

### Phase 4: Shortcuts Require Panel Open

**Goal:** Tool shortcuts only work if the tool is visible (panel open).

**Tasks:**

1. **Update `EditorShortcutHandlersImpl.java`**
   - Add panel references
   - Wrap tool shortcuts in panel checks:
     ```java
     // Tile Brush
     registry.register(EditorShortcuts.TOOL_TILE_BRUSH, () -> {
         if (tilesetPalette.isOpen()) {
             toolManager.setActiveTool("Brush");
         }
     });

     // Collision Brush
     registry.register(EditorShortcuts.TOOL_COLLISION_BRUSH, () -> {
         if (collisionPanel.isOpen()) {
             toolManager.setActiveTool("Collision Brush");
         }
     });
     ```

2. **Apply to all tile and collision tool shortcuts**

**Test:**
- Close Tileset Palette, press B → nothing happens
- Open Tileset Palette, press B → Brush activates

---

### Phase 5: F1/F2 Panel Shortcuts

**Goal:** F1 toggles Tileset Palette, F2 toggles Collision Panel.

**Tasks:**

1. **Update `EditorShortcuts.java`**
   - Replace mode shortcuts:
     ```java
     public static final String PANEL_TILESET = "panel.tileset";      // F1
     public static final String PANEL_COLLISION = "panel.collision";  // F2
     // Remove MODE_ENTITY, MODE_TILEMAP, MODE_COLLISION
     ```

2. **Update `EditorShortcutHandlersImpl.java`**
   ```java
   // F1 - Toggle Tileset Palette
   registry.register(EditorShortcuts.PANEL_TILESET, () -> {
       tilesetPalette.toggle();
       savePanelState();
   });

   // F2 - Toggle Collision Panel
   registry.register(EditorShortcuts.PANEL_COLLISION, () -> {
       collisionPanel.toggle();
       savePanelState();
   });
   ```

3. **Update Hierarchy click behavior**
   - Click "Tilemap Layers" → open Tileset Palette (if closed) + select layer
   - Click "Collision Map" → open Collision Panel (if closed) + select collision

**Test:**
- F1 opens Tileset Palette when closed
- F1 closes Tileset Palette when open
- Same for F2 and Collision Panel

---

### Phase 6: Remove Mode System

**Goal:** Delete `EditorModeManager` and all references.

**Tasks:**

1. **Delete `EditorModeManager.java`**

2. **Update `EditorContext.java`**
   - Remove `EditorModeManager modeManager` field
   - Remove `switchMode()`, `switchToTilemapMode()`, etc.
   - Remove mode change listeners
   - Remove `onModeChanged()` method

3. **Update `SceneViewToolbar.java`**
   - Remove any remaining mode references

4. **Update `EditorToolController.java`**
   - Remove mode-based logic in `renderToolPanel()`
   - Remove mode checks in `renderToolSettings()`

5. **Update `HierarchySelectionHandler.java`**
   - Remove `modeManager` reference
   - Remove `onModeChanged()` method
   - Update `selectTilemapLayers()` to open panel instead of switching mode
   - Update `selectCollisionMap()` to open panel instead of switching mode

6. **Search codebase for remaining references**
   - `modeManager`
   - `EditorModeManager`
   - `switchToTilemap`
   - `switchToCollision`
   - `switchToEntity`
   - `isTilemapMode`
   - `isCollisionMode`
   - `isEntityMode`

**Test:**
- Editor compiles without errors
- All workflows function without mode concept

---

### Phase 7: Polish

**Goal:** Final refinements and edge cases.

**Tasks:**

1. **Alt+Click for quick select**
   - In `ViewportInputHandler`, detect Alt+Click
   - Temporarily use Select tool behavior for that click
   - Select entity under cursor
   - Don't change active tool

2. **ESC behavior**
   ```java
   if (tilesetPalette.isOpen() && tilesetPalette.isFocused()) {
       tilesetPalette.setOpen(false);
   } else if (collisionPanel.isOpen() && collisionPanel.isFocused()) {
       collisionPanel.setOpen(false);
   } else if (selectionManager.hasSelection()) {
       selectionManager.clearSelection();
   }
   ```

3. **Visual feedback**
   - Active tilemap layer name in viewport or status bar
   - Clear indication when painting panels are open

4. **Update documentation**
   - Update keyboard shortcut help text
   - Update any user-facing guides

---

## Files Summary

| File | Action | Phase |
|------|--------|-------|
| **NEW: `EditorSelectionManager.java`** | CREATE | 1 |
| **NEW: `EditorPanel.java`** (abstract class) | CREATE | 2 |
| `EditorContext.java` | MODIFY | 1, 6 |
| `HierarchySelectionHandler.java` | MODIFY | 1, 5, 6 |
| `InspectorPanel.java` | MODIFY | 1 |
| `TilesetPalettePanel.java` | MODIFY | 2 |
| `CollisionPanel.java` | MODIFY | 2 |
| `HierarchyPanel.java` | MODIFY | 2 |
| `AssetBrowserPanel.java` | MODIFY | 2 |
| `EditorUIController.java` | MODIFY | 2 |
| `EditorConfig.java` | MODIFY | 2 |
| **DELETE: `PrefabBrowserPanel.java`** | DELETE | 2 |
| `SceneViewToolbar.java` | MODIFY | 3, 6 |
| `ToolManager.java` | MODIFY | 3 |
| **DELETE: `EntityPlacerTool.java`** | DELETE | 3 |
| `EditorToolController.java` | MODIFY | 3, 6 |
| `EditorShortcutHandlersImpl.java` | MODIFY | 4, 5 |
| `EditorShortcuts.java` | MODIFY | 5 |
| `HierarchyPanel.java` | MODIFY | 5 |
| **DELETE: `EditorModeManager.java`** | DELETE | 6 |
| `ViewportInputHandler.java` | MODIFY | 7 |

---

## Testing Checklist

### Phase 1: Selection
- [ ] Select entity, change tool → entity stays selected in Inspector
- [ ] Select tilemap layer → Inspector shows layer properties
- [ ] Select collision → Inspector shows collision properties
- [ ] Selection persists across panel open/close

### Phase 2: Panels
- [ ] All panels visible based on saved state at startup
- [ ] Can close each panel via X button
- [ ] Can toggle via Window menu
- [ ] Checkmarks update correctly in menu
- [ ] Panel state persists across restart
- [ ] Reset Layout works
- [ ] PrefabBrowserPanel is gone

### Phase 3: Tools
- [ ] Only entity tools visible when no painting panels open
- [ ] Tile tools appear when Tileset Palette opens
- [ ] Tile tools disappear when Tileset Palette closes
- [ ] Collision tools appear when Collision Panel opens
- [ ] Closing panel while using its tool → switches to Select
- [ ] Place Entity tool is gone
- [ ] Mode dropdown is gone

### Phase 4: Shortcuts
- [ ] B does nothing when Tileset Palette closed
- [ ] B activates Brush when Tileset Palette open
- [ ] All tile shortcuts require Tileset Palette open
- [ ] All collision shortcuts require Collision Panel open

### Phase 5: F1/F2
- [ ] F1 opens Tileset Palette when closed
- [ ] F1 closes Tileset Palette when open
- [ ] F2 opens Collision Panel when closed
- [ ] F2 closes Collision Panel when open
- [ ] Click "Tilemap Layers" → opens Tileset Palette
- [ ] Click "Collision Map" → opens Collision Panel

### Phase 6: Mode Removal
- [ ] No compile errors
- [ ] No runtime errors
- [ ] No "mode" text in UI
- [ ] All workflows function

### Phase 7: Polish
- [ ] Alt+Click selects entity from any tool
- [ ] ESC closes focused painting panel
- [ ] ESC clears selection if no panel focused
- [ ] Active layer visible somewhere

---

## Code Review

After implementation, request a code review in `Documents/Plans/editor-modes-refactor/review.md` covering:

1. All new/modified files listed above
2. Complete removal of mode system references
3. Panel state persistence correctness
4. Tool availability logic
5. Shortcut conditional behavior
6. Edge cases (rapid open/close, multiple panels, etc.)
