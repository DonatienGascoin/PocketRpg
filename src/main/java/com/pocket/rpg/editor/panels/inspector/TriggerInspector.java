package com.pocket.rpg.editor.panels.inspector;

import com.pocket.rpg.collision.CollisionType;
import com.pocket.rpg.collision.Direction;
import com.pocket.rpg.collision.ElevationLevel;
import com.pocket.rpg.collision.trigger.*;
import com.pocket.rpg.editor.core.MaterialIcons;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.ui.fields.FieldEditors;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import lombok.Setter;

import java.util.List;

/**
 * Inspector panel for editing trigger properties.
 * <p>
 * Currently only supports STAIRS triggers.
 * WARP, DOOR, and SPAWN_POINT are now entity-based components
 * (WarpZone, Door, SpawnPoint).
 */
public class TriggerInspector {

    @Setter
    private EditorScene scene;

    private TileCoord selectedTile;
    private CollisionType collisionType;
    private TriggerData currentData;

    public void setSelectedTile(TileCoord tile) {
        if (tile == null) {
            this.selectedTile = null;
            this.collisionType = null;
            this.currentData = null;
            return;
        }

        this.selectedTile = tile;

        if (scene != null) {
            this.collisionType = scene.getCollisionMap().get(tile.x(), tile.y(), tile.elevation());
            this.currentData = scene.getTriggerDataMap().get(tile.x(), tile.y(), tile.elevation());
        }
    }

    /**
     * Returns true if a trigger tile is currently selected.
     */
    public boolean hasSelection() {
        return selectedTile != null;
    }

    /**
     * Clears the current trigger selection.
     */
    public void clearSelection() {
        setSelectedTile(null);
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

        // Type-specific editor - only STAIRS is collision-based now
        if (collisionType == CollisionType.STAIRS) {
            renderStairsExplanation();
            renderStairsEditor();
        } else {
            ImGui.textDisabled("No editor for " + collisionType);
        }

        ImGui.separator();
        renderDeleteAction();
    }

    private void renderNoSelection() {
        ImGui.textDisabled("No trigger selected");
        ImGui.spacing();
        ImGui.textWrapped("Select a trigger tile in the Scene View or from the Triggers list in the Collision Panel.");
    }

    private void renderNotATrigger() {
        ImGui.textDisabled("Not a trigger tile");
        ImGui.text("Position: (" + selectedTile.x() + ", " + selectedTile.y() + ")");
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
        ImGui.text(collisionType.getDisplayName());

        // Position
        ImGui.textDisabled("at (" + selectedTile.x() + ", " + selectedTile.y() + ", elev=" + selectedTile.elevation() + ")");

        // Validation warnings
        if (currentData != null) {
            List<String> errors = currentData.validate();
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
            ImGui.text(MaterialIcons.Warning + " Not configured");
            ImGui.text("Fill in the fields below.");
            ImGui.popStyleColor();
        }
    }

    // ========== STAIRS ==========

    private void renderStairsExplanation() {
        ImGui.pushStyleColor(ImGuiCol.Text, 0.7f, 0.7f, 0.7f, 1.0f);
        ImGui.textWrapped("Stairs change elevation based on exit direction. " +
                "Configure which direction(s) change elevation and by how much. " +
                "Triggers on tile EXIT, not enter - making stairs naturally bidirectional.");
        ImGui.popStyleColor();
        ImGui.spacing();
    }

    private void renderStairsEditor() {
        StairsData stairs = currentData instanceof StairsData s ? s : getDefaultStairs();
        int currentElevation = selectedTile != null ? selectedTile.elevation() : 0;

        ImGui.text("Mono-Direction Stairs");
        ImGui.textDisabled("Elevation changes when exiting in the specified direction.");
        ImGui.spacing();

        // Show current elevation
        ImGui.text("Current: " + ElevationLevel.getDisplayName(currentElevation));
        ImGui.spacing();

        // Exit direction dropdown
        FieldEditors.drawEnum("Exit Direction", "stairs_direction",
                stairs::exitDirection,
                value -> applyStairs(stairs.withExitDirection(value)),
                Direction.class);
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("The direction player must exit to trigger elevation change");
        }

        // Destination elevation dropdown
        int destElevation = currentElevation + stairs.elevationChange();
        String destArrow = stairs.elevationChange() > 0 ? MaterialIcons.ArrowUpward : MaterialIcons.ArrowDownward;
        String destLabel = destArrow + " " + ElevationLevel.getDisplayName(destElevation) + " (level " + destElevation + ")";

        ImGui.text("Destination");
        ImGui.sameLine();
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
        if (ImGui.beginCombo("##stairs_dest", destLabel)) {
            ElevationLevel[] levels = ElevationLevel.getAll();
            for (int i = levels.length - 1; i >= 0; i--) {
                ElevationLevel level = levels[i];

                if (level.getLevel() == currentElevation) {
                    ImGui.beginDisabled();
                    ImGui.selectable("  " + level.getDisplayName() + " (level " + level.getLevel() + ") - current", false);
                    ImGui.endDisabled();
                    continue;
                }

                boolean isSelected = level.getLevel() == destElevation;
                String arrow = level.getLevel() > currentElevation ? MaterialIcons.ArrowUpward : MaterialIcons.ArrowDownward;
                String label = arrow + " " + level.getDisplayName() + " (level " + level.getLevel() + ")";

                if (ImGui.selectable(label, isSelected)) {
                    int change = level.getLevel() - currentElevation;
                    applyStairs(stairs.withElevationChange(change));
                }
            }
            ImGui.endCombo();
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("The elevation level player will be at after using stairs");
        }

        ImGui.spacing();
        ImGui.separator();
        ImGui.spacing();

        // Quick presets
        ImGui.text("Quick Actions");
        ImGui.spacing();

        float buttonWidth = (ImGui.getContentRegionAvailX() - 8) / 2;

        boolean canGoUp = currentElevation < ElevationLevel.getMaxLevel();
        if (!canGoUp) ImGui.beginDisabled();
        if (ImGui.button(MaterialIcons.ArrowUpward + " Go Up", buttonWidth, 0)) {
            applyStairs(StairsData.goingUp(Direction.UP));
        }
        if (!canGoUp) ImGui.endDisabled();
        if (ImGui.isItemHovered()) {
            String destName = ElevationLevel.getDisplayName(currentElevation + 1);
            ImGui.setTooltip("Exit UP -> " + destName);
        }

        ImGui.sameLine();

        boolean canGoDown = currentElevation > 0;
        if (!canGoDown) ImGui.beginDisabled();
        if (ImGui.button(MaterialIcons.ArrowDownward + " Go Down", buttonWidth, 0)) {
            applyStairs(StairsData.goingDown(Direction.DOWN));
        }
        if (!canGoDown) ImGui.endDisabled();
        if (ImGui.isItemHovered()) {
            String destName = ElevationLevel.getDisplayName(currentElevation - 1);
            ImGui.setTooltip("Exit DOWN -> " + destName);
        }

        ImGui.spacing();
        ImGui.textDisabled("For bidirectional stairs, place matching");
        ImGui.textDisabled("stairs at the destination elevation.");
    }

    private void applyStairs(StairsData stairs) {
        saveTriggerData(stairs);
    }

    private StairsData getDefaultStairs() {
        return new StairsData();
    }

    // ========== ACTIONS ==========

    private void renderDeleteAction() {
        if (ImGui.button("Reset", ImGui.getContentRegionAvailX() * 0.5f - 4, 0)) {
            resetTriggerData();
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Clear configuration (keeps collision tile)");
        }

        ImGui.sameLine();

        ImGui.pushStyleColor(ImGuiCol.Button, 0.6f, 0.2f, 0.2f, 1.0f);
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.7f, 0.3f, 0.3f, 1.0f);
        ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0.5f, 0.15f, 0.15f, 1.0f);
        if (ImGui.button("Delete", -1, 0)) {
            ImGui.openPopup("ConfirmDeleteTrigger");
        }
        ImGui.popStyleColor(3);
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Remove tile completely");
        }

        if (ImGui.beginPopupModal("ConfirmDeleteTrigger")) {
            ImGui.text("Delete this trigger tile?");
            ImGui.text("Both the collision tile and configuration will be removed.");
            ImGui.spacing();
            if (ImGui.button("Delete", 120, 0)) {
                deleteTriggerTile();
                ImGui.closeCurrentPopup();
            }
            ImGui.sameLine();
            if (ImGui.button("Cancel", 120, 0)) {
                ImGui.closeCurrentPopup();
            }
            ImGui.endPopup();
        }
    }

    private void resetTriggerData() {
        if (scene == null || selectedTile == null) return;

        scene.getTriggerDataMap().remove(selectedTile.x(), selectedTile.y(), selectedTile.elevation());
        currentData = null;
        scene.markDirty();
    }

    private void deleteTriggerTile() {
        if (scene == null || selectedTile == null) return;

        scene.getTriggerDataMap().remove(selectedTile.x(), selectedTile.y(), selectedTile.elevation());
        scene.getCollisionMap().clear(selectedTile.x(), selectedTile.y(), selectedTile.elevation());

        currentData = null;
        collisionType = null;
        selectedTile = null;

        scene.markDirty();
    }

    private void saveTriggerData(TriggerData data) {
        if (scene == null || selectedTile == null) return;

        scene.getTriggerDataMap().set(selectedTile.x(), selectedTile.y(), selectedTile.elevation(), data);
        currentData = data;
        scene.markDirty();
    }
}
