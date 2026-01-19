# Editor UI: Trigger Configuration

## Overview

The editor needs two ways to configure trigger metadata:

1. **Scene View Selection** - Click a trigger tile to select and edit
2. **Trigger List Panel** - See all triggers, select from list

Both methods edit the same underlying `TriggerDataMap`.

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────────────┐
│                            EditorScene                                   │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│   CollisionMap                    TriggerDataMap                         │
│   ┌──────────────┐               ┌──────────────────────────┐           │
│   │ (5,10) WARP  │               │ (5,10) → TriggerData     │           │
│   │ (5,11) WARP  │               │   type: WARP             │           │
│   │ (3,3)  DOOR  │               │   targetScene: "cave"    │           │
│   └──────────────┘               │   targetX: 3, targetY: 5 │           │
│         │                        └──────────────────────────┘           │
│         │                                    ▲                           │
│         │         ┌──────────────────────────┤                           │
│         ▼         ▼                          │                           │
│   CollisionOverlayRenderer              TriggerInspector                 │
│   (draws trigger tiles +                (edits selected trigger)         │
│    selection highlight)                                                  │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## Panel Layout

```
┌─────────────────────────────────────────────────────────────────────────────┐
│ Menu Bar                                                                     │
├────────────────────┬────────────────────────────────────┬───────────────────┤
│                    │                                    │                   │
│  Hierarchy         │         Scene View                 │   Inspector       │
│  ├─ Player         │    ┌─────────────────────────┐    │                   │
│  ├─ NPC            │    │                         │    │   [Entity]        │
│  └─ ...            │    │    (collision overlay   │    │   [Trigger] ◄──── │
│                    │    │     with selection)     │    │                   │
├────────────────────┤    │                         │    │   Trigger Props   │
│                    │    │         ✓ selected      │    │   ─────────────   │
│  Collision Panel   │    │         tile            │    │   Type: WARP      │
│  ─────────────────│    │                         │    │   Target: cave    │
│  [Tools...]        │    └─────────────────────────┘    │   X: 3  Y: 5      │
│                    │                                    │   ☑ Player Only   │
│  ► Trigger List    │────────────────────────────────────│                   │
│    ● WARP (5,10)   │         Game Preview               │   [Apply][Revert] │
│    ● WARP (5,11)   │                                    │   [Delete]        │
│    ● DOOR (3,3)    │                                    │                   │
└────────────────────┴────────────────────────────────────┴───────────────────┘
```

---

## CollisionSelectTool

Allows clicking on trigger tiles in the scene view.

```java
public class CollisionSelectTool extends CollisionTool {
    
    private TileCoord selectedTile = null;
    private Consumer<TileCoord> onSelectionChanged;
    
    @Override
    public String getName() { return "Select"; }
    
    @Override
    public String getIcon() { return FontAwesomeIcons.MousePointer; }
    
    @Override
    public String getTooltip() { return "Select trigger tile to edit properties"; }
    
    @Override
    public void onMouseDown(int tileX, int tileY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return;
        
        CollisionMap collisionMap = editorScene.getCollisionMap();
        CollisionType type = collisionMap.get(tileX, tileY, currentZ);
        
        if (type.isTrigger()) {
            selectedTile = new TileCoord(tileX, tileY, currentZ);
            notifySelectionChanged();
        } else {
            selectedTile = null;
            notifySelectionChanged();
        }
    }
    
    public TileCoord getSelectedTile() { return selectedTile; }
    
    public void setSelectedTile(TileCoord coord) {
        this.selectedTile = coord;
        notifySelectionChanged();
    }
    
    public void clearSelection() {
        this.selectedTile = null;
        notifySelectionChanged();
    }
}

// TileCoord.java
public record TileCoord(int x, int y, int z) {
    @Override
    public String toString() {
        return String.format("(%d, %d, z=%d)", x, y, z);
    }
}
```

---

## Selection Highlight Rendering

```java
// CollisionOverlayRenderer.java
public class CollisionOverlayRenderer {
    
    private TileCoord selectedTile = null;
    private float selectionPulse = 0f;
    
    private static final float[] SELECTION_COLOR = {1.0f, 1.0f, 0.0f, 0.8f};
    private static final float[] SELECTION_BORDER = {1.0f, 1.0f, 1.0f, 1.0f};
    
    public void setSelectedTile(TileCoord tile) {
        this.selectedTile = tile;
    }
    
    public void render(SpriteBatch batch, CollisionMap collisionMap, 
                       Camera camera, int tileSize) {
        // ... existing collision overlay rendering ...
        
        if (selectedTile != null) {
            renderSelectionHighlight(batch, tileSize);
        }
    }
    
    private void renderSelectionHighlight(SpriteBatch batch, int tileSize) {
        selectionPulse += 0.05f;
        float pulse = (float) (0.6f + 0.4f * Math.sin(selectionPulse));
        
        float x = selectedTile.x() * tileSize;
        float y = selectedTile.y() * tileSize;
        
        batch.setColor(
            SELECTION_COLOR[0], 
            SELECTION_COLOR[1], 
            SELECTION_COLOR[2], 
            SELECTION_COLOR[3] * pulse
        );
        batch.drawRect(x, y, tileSize, tileSize);
        
        batch.setColor(SELECTION_BORDER);
        batch.drawRectOutline(x, y, tileSize, tileSize, 2f);
        
        batch.setColor(1, 1, 1, 1);
    }
}
```

---

## Trigger List Panel

Shows all trigger tiles with selection capability.

```java
public class TriggerListPanel {
    
    private EditorScene editorScene;
    private TileCoord selectedTile;
    private Consumer<TileCoord> onTileSelected;
    
    // Filters
    private boolean showWarps = true;
    private boolean showDoors = true;
    private boolean showScripts = true;
    private ImString searchFilter = new ImString(64);
    
    public void render() {
        if (editorScene == null) return;
        
        renderFilters();
        ImGui.separator();
        renderTriggerList();
    }
    
    private void renderFilters() {
        ImGui.text("Filter:");
        ImGui.sameLine();
        
        if (ImGui.checkbox("Warp", showWarps)) showWarps = !showWarps;
        ImGui.sameLine();
        if (ImGui.checkbox("Door", showDoors)) showDoors = !showDoors;
        ImGui.sameLine();
        if (ImGui.checkbox("Script", showScripts)) showScripts = !showScripts;
        
        ImGui.setNextItemWidth(-1);
        ImGui.inputTextWithHint("##search", "Search...", searchFilter);
    }
    
    private void renderTriggerList() {
        ImGui.beginChild("TriggerListScroll", 0, 0, true);
        
        List<TriggerEntry> triggers = collectTriggers();
        
        if (triggers.isEmpty()) {
            ImGui.textDisabled("No trigger tiles found");
            ImGui.textDisabled("Draw WARP, DOOR, or SCRIPT_TRIGGER");
            ImGui.textDisabled("on the collision map");
        } else {
            for (TriggerEntry entry : triggers) {
                renderTriggerEntry(entry);
            }
        }
        
        ImGui.endChild();
    }
    
    private void renderTriggerEntry(TriggerEntry entry) {
        boolean isSelected = entry.coord.equals(selectedTile);
        
        String label = String.format("%s %s %s",
            getTypeIcon(entry.type),
            entry.type.getDisplayName(),
            entry.coord.toString()
        );
        
        if (ImGui.selectable(label, isSelected)) {
            selectedTile = entry.coord;
            notifyTileSelected();
        }
        
        // Context menu
        if (ImGui.beginPopupContextItem()) {
            if (ImGui.menuItem("Edit Properties")) {
                selectedTile = entry.coord;
                notifyTileSelected();
            }
            if (ImGui.menuItem("Go To")) {
                centerCameraOnTile(entry.coord);
            }
            ImGui.separator();
            if (ImGui.menuItem("Delete")) {
                deleteTrigger(entry.coord);
            }
            ImGui.endPopup();
        }
        
        // Tooltip preview
        if (ImGui.isItemHovered()) {
            renderTriggerTooltip(entry);
        }
    }
    
    private void renderTriggerTooltip(TriggerEntry entry) {
        ImGui.beginTooltip();
        
        ImGui.text("Position: " + entry.coord);
        ImGui.text("Type: " + entry.type.getDisplayName());
        
        TriggerData data = entry.data;
        if (data != null) {
            ImGui.separator();
            switch (data.type()) {
                case WARP -> {
                    ImGui.text("Target: " + data.properties().get("targetScene"));
                    ImGui.text("Position: (" + 
                        data.properties().get("targetX") + ", " +
                        data.properties().get("targetY") + ")");
                }
                case DOOR -> {
                    boolean locked = (boolean) data.properties().getOrDefault("locked", false);
                    ImGui.text("Locked: " + (locked ? "Yes" : "No"));
                }
            }
        } else {
            ImGui.separator();
            ImGui.textColored(1, 0.5f, 0, 1, "⚠ No data configured");
        }
        
        ImGui.endTooltip();
    }
    
    private record TriggerEntry(TileCoord coord, CollisionType type, TriggerData data) {}
}
```

---

## Trigger Inspector Panel

Detailed property editor for selected trigger.

```java
public class TriggerInspectorPanel {
    
    private EditorScene editorScene;
    private TileCoord selectedTile;
    private boolean isDirty = false;
    
    // ImGui state
    private ImString targetScene = new ImString(128);
    private ImInt targetX = new ImInt();
    private ImInt targetY = new ImInt();
    private ImInt transitionType = new ImInt();
    private ImBoolean locked = new ImBoolean();
    private ImString keyItem = new ImString(64);
    private ImString scriptId = new ImString(128);
    private ImBoolean oneShot = new ImBoolean();
    private ImBoolean playerOnly = new ImBoolean(true);
    
    public void setSelectedTile(TileCoord tile) {
        if (isDirty) applyChanges();
        this.selectedTile = tile;
        loadTriggerData();
    }
    
    public void render() {
        if (selectedTile == null) {
            renderNoSelection();
            return;
        }
        
        CollisionType type = getSelectedCollisionType();
        if (type == null || !type.isTrigger()) {
            renderInvalidSelection();
            return;
        }
        
        renderHeader(type);
        ImGui.separator();
        
        switch (type) {
            case WARP -> renderWarpProperties();
            case DOOR -> renderDoorProperties();
            case SCRIPT_TRIGGER -> renderScriptProperties();
        }
        
        ImGui.separator();
        renderCommonProperties();
        ImGui.separator();
        renderActions();
    }
    
    private void renderNoSelection() {
        ImGui.textDisabled("No trigger selected");
        ImGui.spacing();
        ImGui.textWrapped("Select a trigger tile in the Scene view " +
                          "or from the Trigger List.");
    }
    
    // ========== WARP ==========
    
    private void renderWarpProperties() {
        ImGui.text("Warp Configuration");
        ImGui.spacing();
        
        ImGui.text("Target Scene:");
        if (ImGui.beginCombo("##targetScene", targetScene.get())) {
            for (String scene : availableScenes) {
                if (ImGui.selectable(scene, scene.equals(targetScene.get()))) {
                    targetScene.set(scene);
                    isDirty = true;
                }
            }
            ImGui.endCombo();
        }
        
        ImGui.text("Target Position:");
        ImGui.setNextItemWidth(80);
        if (ImGui.inputInt("X##targetX", targetX)) isDirty = true;
        ImGui.sameLine();
        ImGui.setNextItemWidth(80);
        if (ImGui.inputInt("Y##targetY", targetY)) isDirty = true;
        
        ImGui.text("Transition:");
        String[] transitions = {"None", "Fade", "Slide Left", "Slide Right"};
        if (ImGui.combo("##transition", transitionType, transitions)) isDirty = true;
    }
    
    // ========== DOOR ==========
    
    private void renderDoorProperties() {
        ImGui.text("Door Configuration");
        ImGui.spacing();
        
        if (ImGui.checkbox("Locked", locked)) isDirty = true;
        
        if (locked.get()) {
            ImGui.indent();
            ImGui.text("Required Key:");
            if (ImGui.inputText("##keyItem", keyItem)) isDirty = true;
            ImGui.unindent();
        }
        
        ImGui.spacing();
        ImGui.text("Destination (optional):");
        if (ImGui.inputText("##doorTargetScene", targetScene)) isDirty = true;
        
        if (!targetScene.get().isEmpty()) {
            ImGui.setNextItemWidth(80);
            if (ImGui.inputInt("X", targetX)) isDirty = true;
            ImGui.sameLine();
            ImGui.setNextItemWidth(80);
            if (ImGui.inputInt("Y", targetY)) isDirty = true;
        }
    }
    
    // ========== SCRIPT ==========
    
    private void renderScriptProperties() {
        ImGui.text("Script Trigger Configuration");
        ImGui.spacing();
        
        if (ImGui.beginTabBar("ScriptTypeTab")) {
            if (ImGui.beginTabItem("Dialogue")) {
                renderDialogueTrigger();
                ImGui.endTabItem();
            }
            if (ImGui.beginTabItem("Trap")) {
                renderTrapTrigger();
                ImGui.endTabItem();
            }
            if (ImGui.beginTabItem("Custom")) {
                ImGui.text("Script ID:");
                if (ImGui.inputText("##scriptId", scriptId)) isDirty = true;
                ImGui.endTabItem();
            }
            ImGui.endTabBar();
        }
    }
    
    // ========== COMMON ==========
    
    private void renderCommonProperties() {
        ImGui.text("Trigger Options");
        ImGui.spacing();
        
        if (ImGui.checkbox("One Shot", oneShot)) isDirty = true;
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Trigger fires only once per game");
        }
        
        if (ImGui.checkbox("Player Only", playerOnly)) isDirty = true;
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Only player can activate this trigger");
        }
    }
    
    // ========== ACTIONS ==========
    
    private void renderActions() {
        ImGui.beginDisabled(!isDirty);
        if (ImGui.button("Apply", 80, 0)) applyChanges();
        ImGui.endDisabled();
        
        ImGui.sameLine();
        
        ImGui.beginDisabled(!isDirty);
        if (ImGui.button("Revert", 80, 0)) loadTriggerData();
        ImGui.endDisabled();
        
        ImGui.sameLine();
        ImGui.spacing();
        ImGui.sameLine();
        
        ImGui.pushStyleColor(ImGuiCol.Button, 0.6f, 0.2f, 0.2f, 1.0f);
        if (ImGui.button("Delete", 80, 0)) {
            ImGui.openPopup("ConfirmDelete");
        }
        ImGui.popStyleColor();
        
        // Confirm popup
        if (ImGui.beginPopupModal("ConfirmDelete")) {
            ImGui.text("Delete this trigger?");
            if (ImGui.button("Yes", 120, 0)) {
                deleteTrigger();
                ImGui.closeCurrentPopup();
            }
            ImGui.sameLine();
            if (ImGui.button("Cancel", 120, 0)) {
                ImGui.closeCurrentPopup();
            }
            ImGui.endPopup();
        }
    }
}
```

---

## Component Synchronization

```java
// Wire everything together
public void setupTriggerEditing() {
    // Select tool notifies others
    collisionSelectTool.setOnSelectionChanged(tile -> {
        triggerInspectorPanel.setSelectedTile(tile);
        triggerListPanel.setSelectedTile(tile);
        collisionOverlayRenderer.setSelectedTile(tile);
    });
    
    // List panel notifies others
    triggerListPanel.setOnTileSelected(tile -> {
        collisionSelectTool.setSelectedTile(tile);
        triggerInspectorPanel.setSelectedTile(tile);
        collisionOverlayRenderer.setSelectedTile(tile);
    });
}
```

---

## Keyboard Shortcuts

| Shortcut | Action |
|----------|--------|
| `S` | Switch to Select tool (collision mode) |
| `Delete` | Delete selected trigger |
| `Ctrl+D` | Duplicate trigger to adjacent tile |
| `Enter` | Apply changes |
| `Escape` | Clear selection / Revert |
| `F` | Focus camera on selected trigger |

---

## Undo/Redo Support

```java
public class TriggerEditCommand implements UndoableCommand {
    
    private final TileCoord coord;
    private final TriggerData oldData;
    private final TriggerData newData;
    private final CollisionType oldType;
    private final CollisionType newType;
    
    @Override
    public void execute() {
        editorScene.getCollisionMap().set(coord.x(), coord.y(), coord.z(), newType);
        if (newData != null) {
            editorScene.getTriggerDataMap().set(coord.x(), coord.y(), coord.z(), newData);
        } else {
            editorScene.getTriggerDataMap().remove(coord.x(), coord.y(), coord.z());
        }
    }
    
    @Override
    public void undo() {
        editorScene.getCollisionMap().set(coord.x(), coord.y(), coord.z(), oldType);
        if (oldData != null) {
            editorScene.getTriggerDataMap().set(coord.x(), coord.y(), coord.z(), oldData);
        } else {
            editorScene.getTriggerDataMap().remove(coord.x(), coord.y(), coord.z());
        }
    }
    
    @Override
    public String getDescription() {
        return "Edit Trigger at " + coord;
    }
}
```
