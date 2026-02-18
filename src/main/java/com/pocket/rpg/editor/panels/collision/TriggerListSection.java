package com.pocket.rpg.editor.panels.collision;

import com.pocket.rpg.collision.CollisionMap;
import com.pocket.rpg.collision.CollisionType;
import com.pocket.rpg.collision.ElevationLevel;
import com.pocket.rpg.collision.trigger.*;
import com.pocket.rpg.editor.core.EditorColors;
import com.pocket.rpg.editor.core.MaterialIcons;
import com.pocket.rpg.editor.scene.EditorScene;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Section showing list of all trigger tiles in the scene.
 * Displayed in Column 3 of CollisionPanel.
 * Clicking a trigger selects it and shows properties in Inspector panel.
 */
public class TriggerListSection {

    @Setter
    private EditorScene scene;

    @Setter
    private Consumer<TileCoord> onTriggerSelected;

    @Setter
    private Consumer<TileCoord> onTriggerFocus;

    /**
     * Supplier for camera world bounds [minX, minY, maxX, maxY].
     * Used to check if trigger is visible before auto-focusing.
     */
    @Setter
    private Supplier<float[]> cameraWorldBoundsSupplier;

    @Getter
    private TileCoord selectedTrigger;

    private CollisionType filterType = null; // null = show all
    private int currentElevation = 0;

    // Double-click tracking for focus button
    private long lastFocusClickTime = 0;
    private long lastFocusClickCoord = 0;
    private static final long DOUBLE_CLICK_TIME_MS = 300;

    public void setCurrentElevation(int elevation) {
        this.currentElevation = elevation;
    }

    /**
     * Returns total number of trigger tiles at current elevation.
     */
    public int getTriggerCount() {
        if (scene == null) return 0;
        return countTriggerTiles();
    }

    /**
     * Returns number of trigger tiles without configuration at current elevation.
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

        renderHeader();
        ImGui.separator();
        renderTriggerList();
    }

    private void renderHeader() {
        int total = getTriggerCount();
        int unconfigured = getUnconfiguredCount();

        // Header with warning if unconfigured
        if (unconfigured > 0) {
            EditorColors.textColored(EditorColors.WARNING, MaterialIcons.Warning + " Triggers (" + total + ")");
        } else {
            ImGui.text("Triggers (" + total + ")");
        }

        // Filter dropdown
        ImGui.sameLine();
        String currentFilter = filterType == null ? "All" : filterType.getDisplayName();
        ImGui.setNextItemWidth(70);
        if (ImGui.beginCombo("##triggerFilter", currentFilter)) {
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

        // Hint about clicking
        if (total > 0) {
            ImGui.textDisabled("Click to edit in Inspector");
        }
    }

    private void renderTriggerList() {
        List<TriggerEntry> triggers = collectTriggers();

        if (triggers.isEmpty()) {
            ImGui.textDisabled("No triggers at elev " + currentElevation);
            ImGui.spacing();
            ImGui.textWrapped("Draw STAIRS tiles to create triggers. Use WarpZone, Door, SpawnPoint entities for other triggers.");
            return;
        }

        // Show warning count if any
        long unconfigured = triggers.stream().filter(t -> t.data == null).count();
        if (unconfigured > 0) {
            EditorColors.textColored(EditorColors.WARNING, unconfigured + " need config");
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

        // Build label with icon, type name, and coordinates
        String icon = entry.type.hasIcon() ? entry.type.getIcon() : "";
        String typeName = entry.type.getDisplayName();
        String coords = "(" + entry.coord.x() + "," + entry.coord.y() + ")";
        String summary = getSummary(entry);

        // Status indicator
        String status = isConfigured ? "" : MaterialIcons.Warning;
        String label = status + icon + " " + typeName + " " + coords;

        // Calculate layout - button at far right with comfortable padding
        float buttonWidth = 22;
        float windowWidth = ImGui.getContentRegionAvailX();
        float selectableWidth = windowWidth - buttonWidth - 12; // 12px gap for breathing room

        // Style entries - highlight selected and unconfigured
        if (isSelected) {
            ImGui.pushStyleColor(ImGuiCol.Header, EditorColors.INFO[0], EditorColors.INFO[1], EditorColors.INFO[2], EditorColors.INFO[3]);
        }
        if (!isConfigured) {
            ImGui.pushStyleColor(ImGuiCol.Text, EditorColors.WARNING[0], EditorColors.WARNING[1], EditorColors.WARNING[2], EditorColors.WARNING[3]);
        }

        // Selectable with fixed width (leaving room for button)
        if (ImGui.selectable(label + "##" + entry.coord.pack(), isSelected, 0, selectableWidth, 0)) {
            selectedTrigger = entry.coord;
            if (onTriggerSelected != null) {
                onTriggerSelected.accept(entry.coord);
            }
        }

        // Show tooltip on hover
        if (ImGui.isItemHovered()) {
            renderTooltip(entry);
        }

        if (!isConfigured) {
            ImGui.popStyleColor();
        }
        if (isSelected) {
            ImGui.popStyleColor();
        }

        // Focus button on same line
        ImGui.sameLine();
        ImGui.pushID("focus_" + entry.coord.pack());
        if (ImGui.smallButton(MaterialIcons.CenterFocusStrong)) {
            // Select the trigger first
            selectedTrigger = entry.coord;
            if (onTriggerSelected != null) {
                onTriggerSelected.accept(entry.coord);
            }

            // Check if double-click (force focus) using manual tracking
            long now = System.currentTimeMillis();
            long coordPacked = entry.coord.pack();
            boolean isDoubleClick = (coordPacked == lastFocusClickCoord)
                    && (now - lastFocusClickTime < DOUBLE_CLICK_TIME_MS);

            lastFocusClickTime = now;
            lastFocusClickCoord = coordPacked;

            // Double-click: force focus, Single-click: focus only if not visible
            boolean shouldFocus = isDoubleClick || !isTriggerVisible(entry.coord);

            if (shouldFocus && onTriggerFocus != null) {
                onTriggerFocus.accept(entry.coord);
            }
        }
        ImGui.popID();
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Click: focus if off-screen\nDouble-click: force focus");
        }
    }

    private String getSummary(TriggerEntry entry) {
        if (entry.data == null) return "";

        // Only STAIRS triggers use collision-based trigger data now
        // WARP, DOOR, SPAWN_POINT are entity-based components
        if (entry.data instanceof StairsData stairs) {
            // Show direction and destination elevation name
            String dir = stairs.exitDirection().name().charAt(0) + "";
            int destLevel = entry.coord.elevation() + stairs.elevationChange();
            String arrow = stairs.elevationChange() > 0 ? MaterialIcons.ArrowUpward : MaterialIcons.ArrowDownward;
            return dir + " " + arrow + " " + ElevationLevel.getDisplayName(destLevel);
        }
        return "";
    }

    private String abbreviate(String text) {
        if (text == null) return "";
        if (text.length() <= 8) return text;
        return text.substring(0, 6) + "..";
    }

    private void renderTooltip(TriggerEntry entry) {
        ImGui.beginTooltip();

        ImGui.text("Position: (" + entry.coord.x() + ", " + entry.coord.y() + ", elev=" + entry.coord.elevation() + ")");
        ImGui.text("Type: " + entry.type.getDisplayName());

        if (entry.data != null) {
            ImGui.separator();
            renderDataTooltip(entry);
        } else {
            ImGui.separator();
            ImGui.pushStyleColor(ImGuiCol.Text, EditorColors.WARNING[0], EditorColors.WARNING[1], EditorColors.WARNING[2], EditorColors.WARNING[3]);
            ImGui.text(MaterialIcons.Warning + " Not configured");
            ImGui.text("Select and configure in Inspector");
            ImGui.popStyleColor();
        }

        ImGui.endTooltip();
    }

    private void renderDataTooltip(TriggerEntry entry) {
        // Only STAIRS triggers use collision-based trigger data now
        // WARP, DOOR, SPAWN_POINT are entity-based components
        if (entry.data instanceof StairsData stairs) {
            ImGui.text("Exit Direction: " + stairs.exitDirection().name());
            // Show elevation change with names
            int fromLevel = entry.coord.elevation();
            int toLevel = fromLevel + stairs.elevationChange();
            ImGui.text("From: " + ElevationLevel.getDisplayName(fromLevel));
            ImGui.text("To: " + ElevationLevel.getDisplayName(toLevel));
        }
    }

    private List<TriggerEntry> collectTriggers() {
        List<TriggerEntry> triggers = new ArrayList<>();

        CollisionMap collisionMap = scene.getCollisionMap();
        TriggerDataMap triggerDataMap = scene.getTriggerDataMap();

        if (collisionMap == null) return triggers;

        // Iterate through all chunks at current elevation
        for (CollisionMap.CollisionChunk chunk : collisionMap.getChunksForLevel(currentElevation)) {
            int chunkBaseX = chunk.getChunkX() * CollisionMap.CollisionChunk.CHUNK_SIZE;
            int chunkBaseY = chunk.getChunkY() * CollisionMap.CollisionChunk.CHUNK_SIZE;

            for (int tx = 0; tx < CollisionMap.CollisionChunk.CHUNK_SIZE; tx++) {
                for (int ty = 0; ty < CollisionMap.CollisionChunk.CHUNK_SIZE; ty++) {
                    CollisionType type = chunk.get(tx, ty);
                    if (type == null || !type.isTrigger()) continue;
                    if (filterType != null && type != filterType) continue;

                    int worldX = chunkBaseX + tx;
                    int worldY = chunkBaseY + ty;

                    TileCoord coord = new TileCoord(worldX, worldY, currentElevation);
                    TriggerData data = triggerDataMap != null ? triggerDataMap.get(worldX, worldY, currentElevation) : null;

                    triggers.add(new TriggerEntry(coord, type, data));
                }
            }
        }

        // Sort by position for consistent display
        triggers.sort(Comparator.comparingInt((TriggerEntry e) -> e.coord.y())
                .thenComparingInt(e -> e.coord.x()));

        return triggers;
    }

    private int countTriggerTiles() {
        CollisionMap collisionMap = scene.getCollisionMap();
        if (collisionMap == null) return 0;

        int count = 0;
        for (CollisionMap.CollisionChunk chunk : collisionMap.getChunksForLevel(currentElevation)) {
            for (int tx = 0; tx < CollisionMap.CollisionChunk.CHUNK_SIZE; tx++) {
                for (int ty = 0; ty < CollisionMap.CollisionChunk.CHUNK_SIZE; ty++) {
                    CollisionType type = chunk.get(tx, ty);
                    if (type != null && type.isTrigger()) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    private int countUnconfiguredTriggers() {
        CollisionMap collisionMap = scene.getCollisionMap();
        TriggerDataMap triggerDataMap = scene.getTriggerDataMap();
        if (collisionMap == null) return 0;

        int count = 0;
        for (CollisionMap.CollisionChunk chunk : collisionMap.getChunksForLevel(currentElevation)) {
            int chunkBaseX = chunk.getChunkX() * CollisionMap.CollisionChunk.CHUNK_SIZE;
            int chunkBaseY = chunk.getChunkY() * CollisionMap.CollisionChunk.CHUNK_SIZE;

            for (int tx = 0; tx < CollisionMap.CollisionChunk.CHUNK_SIZE; tx++) {
                for (int ty = 0; ty < CollisionMap.CollisionChunk.CHUNK_SIZE; ty++) {
                    CollisionType type = chunk.get(tx, ty);
                    if (type == null || !type.isTrigger()) continue;

                    int worldX = chunkBaseX + tx;
                    int worldY = chunkBaseY + ty;

                    TriggerData data = triggerDataMap != null ? triggerDataMap.get(worldX, worldY, currentElevation) : null;
                    if (data == null) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    public void clearSelection() {
        selectedTrigger = null;
    }

    /**
     * Sets the selected trigger (e.g., when clicking in scene view).
     */
    public void setSelectedTrigger(TileCoord coord) {
        this.selectedTrigger = coord;
    }

    /**
     * Checks if a trigger tile is visible in the current camera view.
     */
    private boolean isTriggerVisible(TileCoord coord) {
        if (cameraWorldBoundsSupplier == null) {
            return false; // If we can't check, assume not visible (will focus)
        }

        float[] bounds = cameraWorldBoundsSupplier.get();
        if (bounds == null || bounds.length < 4) {
            return false;
        }

        float minX = bounds[0];
        float minY = bounds[1];
        float maxX = bounds[2];
        float maxY = bounds[3];

        // Check if trigger tile center is within visible bounds
        float tileX = coord.x() + 0.5f;
        float tileY = coord.y() + 0.5f;

        return tileX >= minX && tileX <= maxX && tileY >= minY && tileY <= maxY;
    }

    private record TriggerEntry(TileCoord coord, CollisionType type, TriggerData data) {
    }
}
