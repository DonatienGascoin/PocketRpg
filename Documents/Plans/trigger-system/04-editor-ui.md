# Editor UI

## Problems to Solve

1. **Horizontal layout needs 3 columns** - Avoid scrolling, organize content logically
2. **Trigger list** - See all triggers at a glance, select from list
3. **Trigger inspector** - Edit trigger properties in Inspector panel (reuse existing)
4. **Visual feedback** - Show which triggers are missing configuration

---

## Three-Column CollisionPanel Layout

### Current Layout (Horizontal, 2 columns)

```
┌─────────────────────────────────────────────────────────────────────────┐
│ Collision Panel                                                          │
├─────────────────────────────────────────────────────────────────────────┤
│ LEFT COLUMN          │ RIGHT COLUMN                                      │
│ ─────────────        │ ──────────────────────────────────────────────── │
│ Tool Size            │ Z Level: [0 ▼]                                   │
│ Visibility           │ ─────────────                                     │
│ ─────────────        │ Collision Types                                   │
│ Selected: Warp       │ [scrollable area with all types]                 │
│ Description...       │                                                   │
│ Stats: 150 tiles     │                                                   │
└──────────────────────┴──────────────────────────────────────────────────┘
```

### New 3-Column Layout (No Tabs, No Scrolling)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│ Tool Size: [3]  │ Show: ☑  │ Elev: [0 ▼]                        │ 150 tiles │
├─────────────────────┬─────────────────────┬─────────────────────────────────┤
│ COLUMN 1            │ COLUMN 2            │ COLUMN 3                        │
│ Basic Types         │ Metadata Types      │ Trigger List                    │
├─────────────────────┼─────────────────────┼─────────────────────────────────┤
│ Movement            │ Elevation           │ ⚠ Triggers (3)                  │
│ [None] [Solid]      │ [Stairs↑][Stairs↓]  │                                 │
│                     │                     │ ● Warp (5,10) → cave            │
│ Ledges              │ Triggers            │ ⚠ Warp (5,11) [!]               │
│ [↓][↑][←][→]        │ [Warp][Door]        │ ● Door (3,3) → house            │
│                     │                     │ ● Stairs↑ (8,2) → elev=1        │
│ Terrain             ├─────────────────────┤                                 │
│ [Water][Grass]      │ Selected: Warp      │                                 │
│ [Ice][Sand]         │ "Teleports to       │                                 │
│                     │  another scene"     │                                 │
└─────────────────────┴─────────────────────┴─────────────────────────────────┘
```

**Column breakdown:**
- **Column 1**: Basic collision types (Movement, Ledges, Terrain) - no metadata needed
- **Column 2**: Metadata types (Elevation, Triggers) + Selected type info at bottom
- **Column 3**: Trigger list - clicking selects trigger, shows in Inspector panel

---

## CollisionPanel Changes

**File**: `src/main/java/com/pocket/rpg/editor/panels/CollisionPanel.java`

```java
public class CollisionPanel {

    private final CollisionTypeSelector typeSelector;
    private final TriggerListSection triggerListSection;

    // ... existing fields ...

    public CollisionPanel() {
        this.typeSelector = new CollisionTypeSelector();
        this.triggerListSection = new TriggerListSection();
        // ...
    }

    private void renderHorizontal() {
        // Top bar: Tool size, visibility, elevation, stats (all on one line)
        renderTopBar();

        ImGui.separator();

        // 3-column layout
        float availableWidth = ImGui.getContentRegionAvailX();
        float col1Width = availableWidth * 0.30f;  // Basic types
        float col2Width = availableWidth * 0.30f;  // Metadata types + selected info
        float col3Width = availableWidth * 0.40f;  // Trigger list

        // Column 1: Basic collision types
        ImGui.beginChild("Col1_BasicTypes", col1Width, 0, false);
        typeSelector.renderColumn1();  // Movement, Ledges, Terrain
        ImGui.endChild();

        ImGui.sameLine();

        // Column 2: Metadata types + selected info
        ImGui.beginChild("Col2_MetadataTypes", col2Width, 0, false);
        typeSelector.renderColumn2();  // Elevation, Triggers + selected info
        ImGui.endChild();

        ImGui.sameLine();

        // Column 3: Trigger list
        ImGui.beginChild("Col3_TriggerList", col3Width, 0, false);
        triggerListSection.render();
        ImGui.endChild();
    }

    private void renderTopBar() {
        // Tool size (only for brush/eraser)
        if (brushTool != null && isToolActive(brushTool)) {
            ImGui.setNextItemWidth(60);
            int[] size = {brushTool.getBrushSize()};
            if (ImGui.sliderInt("##size", size, 1, 5)) {
                brushTool.setBrushSize(size[0]);
            }
            ImGui.sameLine();
        }

        // Visibility toggle
        boolean visible = toolConfigView.isOverlayVisible();
        if (ImGui.checkbox("Show##vis", visible)) {
            toolConfigView.setOverlayVisible(!visible);
        }
        ImGui.sameLine();

        // Elevation
        ImGui.text("Elev:");
        ImGui.sameLine();
        ImGui.setNextItemWidth(50);
        int[] elev = {toolConfigView.getElevation()};
        if (ImGui.inputInt("##elevation", elev)) {
            toolConfigView.setElevation(elev[0]);
        }
        ImGui.sameLine();

        // Stats
        ImGui.textDisabled("| " + getTileCount() + " tiles");
    }
}
```

---

## CollisionTypeSelector Column Methods

**File**: `src/main/java/com/pocket/rpg/editor/panels/collisions/CollisionTypeSelector.java`

New methods for 3-column layout:

```java
/**
 * Renders Column 1: Basic types (Movement, Ledges, Terrain).
 * These types don't require metadata configuration.
 */
public void renderColumn1() {
    List<CollisionCategory> col1Categories = List.of(
        CollisionCategory.MOVEMENT,
        CollisionCategory.LEDGE,
        CollisionCategory.TERRAIN
    );

    for (CollisionCategory category : col1Categories) {
        List<CollisionType> types = CollisionType.getByCategory(category);
        if (types.isEmpty()) continue;

        // Category label
        ImGui.textDisabled(category.getDisplayName());

        // All buttons on same line
        boolean first = true;
        for (CollisionType type : types) {
            if (!first) ImGui.sameLine();
            first = false;
            renderCompactButton(type);
        }

        ImGui.spacing();
    }
}

/**
 * Renders Column 2: Metadata types (Elevation, Triggers) + selected type info.
 * These types require metadata configuration.
 */
public void renderColumn2() {
    List<CollisionCategory> col2Categories = List.of(
        CollisionCategory.ELEVATION,
        CollisionCategory.TRIGGER
    );

    for (CollisionCategory category : col2Categories) {
        List<CollisionType> types = CollisionType.getByCategory(category);
        if (types.isEmpty()) continue;

        // Category label
        ImGui.textDisabled(category.getDisplayName());

        // All buttons on same line
        boolean first = true;
        for (CollisionType type : types) {
            if (!first) ImGui.sameLine();
            first = false;
            renderCompactButton(type);
        }

        ImGui.spacing();
    }

    // Selected type info at bottom
    ImGui.separator();
    ImGui.text("Selected: " + selectedType.getDisplayName());
    ImGui.textWrapped(selectedType.getDescription());
}

private void renderCompactButton(CollisionType type) {
    boolean isSelected = (type == selectedType);
    float[] color = type.getOverlayColor();

    // Smaller buttons
    float buttonWidth = type.isLedge() ? 35 : 60;

    // Style based on selection
    if (isSelected) {
        ImGui.pushStyleColor(ImGuiCol.Button, color[0], color[1], color[2], 0.9f);
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, color[0], color[1], color[2], 1.0f);
        ImGui.pushStyleColor(ImGuiCol.ButtonActive, color[0] * 0.8f, color[1] * 0.8f, color[2] * 0.8f, 1.0f);
    } else {
        ImGui.pushStyleColor(ImGuiCol.Button, color[0], color[1], color[2], 0.4f);
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, color[0], color[1], color[2], 0.6f);
        ImGui.pushStyleColor(ImGuiCol.ButtonActive, color[0], color[1], color[2], 0.8f);
    }

    // Short label (use icon if available, else abbreviation)
    String label = type.hasIcon()
        ? type.getIcon()
        : abbreviate(type.getDisplayName());

    if (ImGui.button(label + "##" + type.name(), buttonWidth, 0)) {
        selectType(type);
    }

    ImGui.popStyleColor(3);

    // Tooltip with full name and description from enum
    if (ImGui.isItemHovered()) {
        ImGui.setTooltip(type.getDisplayName() + "\n" + type.getDescription());
    }
}

private String abbreviate(String name) {
    // "Tall Grass" -> "Grass"
    if (name.length() <= 5) return name;
    if (name.contains(" ")) {
        String[] parts = name.split(" ");
        return parts[parts.length - 1]; // Last word
    }
    return name.substring(0, 4);
}
```

---

## TriggerListSection

**File**: `src/main/java/com/pocket/rpg/editor/panels/collision/TriggerListSection.java`

```java
/**
 * Section showing list of all trigger tiles in the scene.
 * Displayed in Column 3 of CollisionPanel.
 * Clicking a trigger selects it and shows properties in Inspector panel.
 */
public class TriggerListSection {

    @Setter private EditorScene scene;
    @Setter private Consumer<TileCoord> onTriggerSelected;

    private TileCoord selectedTrigger;
    private CollisionType filterType = null; // null = show all
    private final ImString searchFilter = new ImString(64);

    /**
     * Returns total number of trigger tiles.
     */
    public int getTriggerCount() {
        if (scene == null) return 0;
        return countTriggerTiles();
    }

    /**
     * Returns number of trigger tiles without configuration.
     */
    public int getUnconfiguredCount() {
        if (scene == null) return 0;
        return countUnconfiguredTriggers();
    }

    public void render() {
        if (scene == null) {
            ImGui.textDisabled("No scene loaded");
            return;
        }

        renderFilterBar();
        ImGui.separator();
        renderTriggerList();
    }

    private void renderFilterBar() {
        // Type filter dropdown
        ImGui.text("Filter:");
        ImGui.sameLine();

        String currentFilter = filterType == null ? "All" : filterType.getDisplayName();
        ImGui.setNextItemWidth(80);
        if (ImGui.beginCombo("##filter", currentFilter)) {
            if (ImGui.selectable("All", filterType == null)) {
                filterType = null;
            }
            for (CollisionType type : CollisionType.values()) {
                if (type.isTrigger()) {
                    if (ImGui.selectable(type.getDisplayName(), type == filterType)) {
                        filterType = type;
                    }
                }
            }
            ImGui.endCombo();
        }

        // Search box
        ImGui.sameLine();
        ImGui.setNextItemWidth(-1);
        ImGui.inputTextWithHint("##search", "Search...", searchFilter);
    }

    private void renderTriggerList() {
        List<TriggerEntry> triggers = collectTriggers();

        if (triggers.isEmpty()) {
            ImGui.textDisabled("No trigger tiles found");
            ImGui.spacing();
            ImGui.textWrapped("Draw WARP, DOOR, STAIRS, or SCRIPT tiles on the collision map to create triggers.");
            return;
        }

        // Show warning count if any
        long unconfigured = triggers.stream().filter(t -> t.data == null).count();
        if (unconfigured > 0) {
            ImGui.pushStyleColor(ImGuiCol.Text, 1.0f, 0.6f, 0.2f, 1.0f);
            ImGui.text(MaterialIcons.Warning + " " + unconfigured + " unconfigured");
            ImGui.popStyleColor();
            ImGui.spacing();
        }

        // Scrollable list
        ImGui.beginChild("TriggerListScroll", 0, 0, false);

        for (TriggerEntry entry : triggers) {
            renderTriggerEntry(entry);
        }

        ImGui.endChild();
    }

    private void renderTriggerEntry(TriggerEntry entry) {
        boolean isSelected = entry.coord.equals(selectedTrigger);
        boolean isConfigured = entry.data != null;

        // Build label
        String icon = entry.type.hasIcon() ? entry.type.getIcon() : "";
        String coords = "(" + entry.coord.x() + ", " + entry.coord.y() + ")";
        String summary = getSummary(entry);

        // Warning indicator for unconfigured
        String status = isConfigured ? "●" : MaterialIcons.Warning;
        String label = status + " " + icon + " " + entry.type.getDisplayName() + " " + coords;

        // Style unconfigured entries
        if (!isConfigured) {
            ImGui.pushStyleColor(ImGuiCol.Text, 1.0f, 0.6f, 0.2f, 1.0f);
        }

        if (ImGui.selectable(label + "##" + entry.coord.pack(), isSelected)) {
            selectedTrigger = entry.coord;
            if (onTriggerSelected != null) {
                onTriggerSelected.accept(entry.coord);
            }
        }

        if (!isConfigured) {
            ImGui.popStyleColor();
        }

        // Summary on same line (right-aligned would be ideal)
        if (!summary.isEmpty()) {
            ImGui.sameLine();
            ImGui.textDisabled(summary);
        }

        // Context menu
        if (ImGui.beginPopupContextItem()) {
            if (ImGui.menuItem("Edit in Inspector")) {
                selectedTrigger = entry.coord;
                if (onTriggerSelected != null) {
                    onTriggerSelected.accept(entry.coord);
                }
            }
            if (ImGui.menuItem("Go To")) {
                centerCameraOnTile(entry.coord);
            }
            ImGui.separator();
            if (ImGui.menuItem("Delete Collision Tile")) {
                deleteTriggerTile(entry.coord);
            }
            ImGui.endPopup();
        }

        // Hover tooltip
        if (ImGui.isItemHovered()) {
            renderTooltip(entry);
        }
    }

    private String getSummary(TriggerEntry entry) {
        if (entry.data == null) return "[not configured]";

        // Exhaustive switch - compiler ensures all TriggerData types are handled
        return switch (entry.data) {
            case WarpTriggerData warp -> "→ " + warp.targetScene();
            case DoorTriggerData door -> door.locked()
                ? "→ " + door.targetScene() + " [locked]"
                : "→ " + door.targetScene();
            case StairsTriggerData stairs -> "→ elev=" + stairs.targetElevation();
        };
    }

    private void renderTooltip(TriggerEntry entry) {
        ImGui.beginTooltip();

        ImGui.text("Position: " + entry.coord);
        ImGui.text("Type: " + entry.type.getDisplayName());

        if (entry.data != null) {
            ImGui.separator();
            renderDataTooltip(entry.data);
        } else {
            ImGui.separator();
            ImGui.pushStyleColor(ImGuiCol.Text, 1.0f, 0.5f, 0.2f, 1.0f);
            ImGui.text(MaterialIcons.Warning + " Not configured");
            ImGui.text("Select and configure in Inspector");
            ImGui.popStyleColor();
        }

        ImGui.endTooltip();
    }

    private void renderDataTooltip(TriggerData data) {
        // Exhaustive switch - compiler ensures all TriggerData types are handled
        switch (data) {
            case WarpTriggerData warp -> {
                ImGui.text("Target: " + warp.targetScene());
                ImGui.text("Position: (" + warp.targetX() + ", " + warp.targetY() + ")");
                ImGui.text("Transition: " + warp.transition());
            }
            case DoorTriggerData door -> {
                ImGui.text("Locked: " + (door.locked() ? "Yes (" + door.requiredKey() + ")" : "No"));
                ImGui.text("Target: " + door.targetScene());
            }
            case StairsTriggerData stairs -> {
                ImGui.text("Target Elevation: " + stairs.targetElevation());
                if (stairs.targetX() != null) {
                    ImGui.text("Reposition: (" + stairs.targetX() + ", " + stairs.targetY() + ")");
                }
            }
        }
    }

    private record TriggerEntry(TileCoord coord, CollisionType type, TriggerData data) {}

    // ... helper methods for collecting, filtering, counting ...
}
```

---

## TriggerInspector

**File**: `src/main/java/com/pocket/rpg/editor/ui/inspectors/TriggerInspector.java`

```java
/**
 * Inspector panel for editing trigger properties.
 * Renders type-specific editors based on TriggerData type.
 */
public class TriggerInspector {

    @Setter private EditorScene scene;
    @Setter private UndoManager undoManager;

    private TileCoord selectedTile;
    private CollisionType collisionType;
    private TriggerData originalData;  // For undo
    private boolean isDirty = false;

    // Editor state (ImGui widgets need mutable state)
    private final ImString targetScene = new ImString(128);
    private final ImInt targetX = new ImInt();
    private final ImInt targetY = new ImInt();
    private final ImInt targetElevation = new ImInt();
    private final ImInt transitionType = new ImInt();
    private final ImBoolean locked = new ImBoolean();
    private final ImString requiredKey = new ImString(64);
    private final ImBoolean consumeKey = new ImBoolean(true);
    private final ImString lockedMessage = new ImString(256);
    private final ImBoolean oneShot = new ImBoolean();
    private final ImBoolean playerOnly = new ImBoolean(true);

    public void setSelectedTile(TileCoord tile) {
        if (isDirty) {
            // Auto-apply or prompt?
            applyChanges();
        }
        this.selectedTile = tile;
        loadFromTile();
    }

    public void render() {
        if (selectedTile == null) {
            renderNoSelection();
            return;
        }

        if (collisionType == null || !collisionType.requiresMetadata()) {
            renderNotATrigger();
            return;
        }

        renderHeader();
        ImGui.separator();

        // Type-specific editor
        switch (collisionType) {
            case WARP -> renderWarpEditor();
            case DOOR -> renderDoorEditor();
            case STAIRS_UP, STAIRS_DOWN -> renderStairsEditor();
            default -> ImGui.textDisabled("No editor for " + collisionType);
        }

        ImGui.separator();
        renderCommonOptions();
        ImGui.separator();
        renderActions();
    }

    private void renderNoSelection() {
        ImGui.textDisabled("No trigger selected");
        ImGui.spacing();
        ImGui.textWrapped("Select a trigger tile in the Scene View or from the Triggers list.");
    }

    private void renderNotATrigger() {
        ImGui.textDisabled("Selected tile is not a trigger");
        ImGui.text("Position: " + selectedTile);
        if (collisionType != null) {
            ImGui.text("Type: " + collisionType.getDisplayName());
        }
    }

    private void renderHeader() {
        // Icon and type name
        if (collisionType.hasIcon()) {
            ImGui.text(collisionType.getIcon());
            ImGui.sameLine();
        }
        ImGui.text(collisionType.getDisplayName() + " Trigger");

        // Position
        ImGui.textDisabled("at " + selectedTile);

        // Validation warnings
        if (originalData != null) {
            List<String> errors = originalData.validate();
            if (!errors.isEmpty()) {
                ImGui.spacing();
                ImGui.pushStyleColor(ImGuiCol.Text, 1.0f, 0.5f, 0.2f, 1.0f);
                for (String error : errors) {
                    ImGui.text(MaterialIcons.Warning + " " + error);
                }
                ImGui.popStyleColor();
            }
        } else {
            ImGui.spacing();
            ImGui.pushStyleColor(ImGuiCol.Text, 1.0f, 0.5f, 0.2f, 1.0f);
            ImGui.text(MaterialIcons.Warning + " Trigger not configured");
            ImGui.text("Fill in the fields below to configure.");
            ImGui.popStyleColor();
        }
    }

    // ========== WARP EDITOR ==========

    private void renderWarpEditor() {
        ImGui.text("Target Scene");
        if (renderSceneDropdown(targetScene)) isDirty = true;

        ImGui.spacing();
        ImGui.text("Target Position");

        ImGui.setNextItemWidth(80);
        if (ImGui.inputInt("X##warpX", targetX)) isDirty = true;
        ImGui.sameLine();
        ImGui.setNextItemWidth(80);
        if (ImGui.inputInt("Y##warpY", targetY)) isDirty = true;

        ImGui.spacing();
        ImGui.text("Transition");
        String[] transitions = {"None", "Fade", "Slide Left", "Slide Right", "Slide Up", "Slide Down"};
        ImGui.setNextItemWidth(-1);
        if (ImGui.combo("##transition", transitionType, transitions)) isDirty = true;
    }

    // ========== DOOR EDITOR ==========

    private void renderDoorEditor() {
        if (ImGui.checkbox("Locked", locked)) isDirty = true;

        if (locked.get()) {
            ImGui.indent();

            ImGui.text("Required Key");
            ImGui.setNextItemWidth(-1);
            if (ImGui.inputText("##key", requiredKey)) isDirty = true;

            if (ImGui.checkbox("Consume Key", consumeKey)) isDirty = true;

            ImGui.text("Locked Message");
            ImGui.setNextItemWidth(-1);
            if (ImGui.inputText("##lockedMsg", lockedMessage)) isDirty = true;

            ImGui.unindent();
        }

        ImGui.spacing();
        ImGui.separator();
        ImGui.spacing();

        ImGui.text("Destination");

        ImGui.text("Scene");
        if (renderSceneDropdown(targetScene)) isDirty = true;

        ImGui.text("Position");
        ImGui.setNextItemWidth(80);
        if (ImGui.inputInt("X##doorX", targetX)) isDirty = true;
        ImGui.sameLine();
        ImGui.setNextItemWidth(80);
        if (ImGui.inputInt("Y##doorY", targetY)) isDirty = true;

        ImGui.spacing();
        ImGui.text("Transition");
        String[] transitions = {"None", "Fade", "Slide Left", "Slide Right", "Slide Up", "Slide Down"};
        ImGui.setNextItemWidth(-1);
        if (ImGui.combo("##doorTransition", transitionType, transitions)) isDirty = true;
    }

    // ========== STAIRS EDITOR ==========

    private void renderStairsEditor() {
        ImGui.text("Target Elevation");
        ImGui.setNextItemWidth(80);
        if (ImGui.inputInt("##targetElevation", targetElevation)) isDirty = true;

        ImGui.spacing();
        ImGui.text("Reposition (optional)");
        ImGui.textDisabled("Leave at 0 to keep same X/Y");

        ImGui.setNextItemWidth(80);
        if (ImGui.inputInt("X##stairsX", targetX)) isDirty = true;
        ImGui.sameLine();
        ImGui.setNextItemWidth(80);
        if (ImGui.inputInt("Y##stairsY", targetY)) isDirty = true;
    }

    // ========== COMMON OPTIONS ==========

    private void renderCommonOptions() {
        ImGui.text("Options");
        ImGui.spacing();

        if (ImGui.checkbox("One Shot", oneShot)) isDirty = true;
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Trigger fires only once per game session");
        }

        if (ImGui.checkbox("Player Only", playerOnly)) isDirty = true;
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Only the player can activate this trigger");
        }
    }

    // ========== ACTIONS ==========

    private void renderActions() {
        boolean wasDisabled = !isDirty;
        if (wasDisabled) ImGui.beginDisabled();

        if (ImGui.button("Apply", 80, 0)) {
            applyChanges();
        }
        ImGui.sameLine();
        if (ImGui.button("Revert", 80, 0)) {
            loadFromTile();
        }

        if (wasDisabled) ImGui.endDisabled();

        ImGui.sameLine();
        ImGui.spacing();
        ImGui.sameLine();

        ImGui.pushStyleColor(ImGuiCol.Button, 0.6f, 0.2f, 0.2f, 1.0f);
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.7f, 0.3f, 0.3f, 1.0f);
        ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0.5f, 0.15f, 0.15f, 1.0f);
        if (ImGui.button("Delete", 80, 0)) {
            ImGui.openPopup("ConfirmDeleteTrigger");
        }
        ImGui.popStyleColor(3);

        // Confirmation popup
        if (ImGui.beginPopupModal("ConfirmDeleteTrigger")) {
            ImGui.text("Delete trigger data at " + selectedTile + "?");
            ImGui.text("The collision tile will remain.");
            ImGui.spacing();
            if (ImGui.button("Delete", 120, 0)) {
                deleteTriggerData();
                ImGui.closeCurrentPopup();
            }
            ImGui.sameLine();
            if (ImGui.button("Cancel", 120, 0)) {
                ImGui.closeCurrentPopup();
            }
            ImGui.endPopup();
        }
    }

    // ... helper methods for load/save/apply ...
}
```

---

## InspectorPanel Integration

The `InspectorPanel` needs to detect when a trigger tile is selected and show `TriggerInspector`:

```java
// In InspectorPanel.java

private void render() {
    // Check if trigger tile is selected
    TileCoord selectedTrigger = getSelectedTriggerTile();
    if (selectedTrigger != null) {
        triggerInspector.setSelectedTile(selectedTrigger);
        triggerInspector.render();
        return;
    }

    // Otherwise, render normal entity inspector
    // ... existing code ...
}
```

---

## Summary of Files

| File | Type | Description |
|------|------|-------------|
| `editor/panels/CollisionPanel.java` | MODIFY | 3-column layout, integrate trigger list |
| `editor/panels/collisions/CollisionTypeSelector.java` | MODIFY | Add column methods, auto-generate from enum |
| `editor/panels/collision/TriggerListSection.java` | NEW | Trigger list UI (column 3) |
| `editor/ui/inspectors/TriggerInspector.java` | NEW | Trigger property editor |
| `editor/panels/InspectorPanel.java` | MODIFY | Integrate TriggerInspector |
