package com.pocket.rpg.editor.ui.inspectors;

import com.pocket.rpg.collision.ElevationLevel;
import com.pocket.rpg.collision.trigger.TileCoord;
import com.pocket.rpg.components.interaction.StaticOccupant;
import com.pocket.rpg.editor.core.MaterialIcons;
import com.pocket.rpg.editor.ui.fields.FieldEditors;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import org.joml.Vector3f;

/**
 * Custom editor for StaticOccupant component.
 * <p>
 * Features:
 * - Offset (X, Y) for positioning relative to entity
 * - Size (Width, Height) for the blocking area
 * - Elevation dropdown using ElevationLevel names
 * - Preview of absolute tile positions
 */
@InspectorFor(StaticOccupant.class)
public class StaticOccupantInspector extends CustomComponentInspector<StaticOccupant> {

    @Override
    public boolean draw() {
        boolean changed = false;

        // Offset
        changed |= drawOffset();

        ImGui.spacing();

        // Size
        changed |= drawSize();

        ImGui.spacing();
        ImGui.separator();
        ImGui.spacing();

        // Elevation dropdown
        changed |= drawElevationDropdown();

        // Show computed absolute positions
        ImGui.spacing();
        ImGui.separator();
        ImGui.spacing();
        drawAbsoluteTilesPreview();

        return changed;
    }

    private boolean drawOffset() {
        boolean changed = false;

        int[] offset = {component.getOffsetX(), component.getOffsetY()};
        FieldEditors.inspectorRow("Offset", () -> {
            ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
            if (ImGui.dragInt2("##offset", offset, 0.1f)) {
                component.setOffsetX(offset[0]);
                component.setOffsetY(offset[1]);
            }
        });

        if (ImGui.isItemDeactivatedAfterEdit()) {
            changed = true;
        }

        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Offset from entity position (in tiles)");
        }

        return changed;
    }

    private boolean drawSize() {
        boolean changed = false;

        int[] size = {component.getWidth(), component.getHeight()};
        FieldEditors.inspectorRow("Size", () -> {
            ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
            if (ImGui.dragInt2("##size", size, 0.1f, 1, 100)) {
                component.setWidth(size[0]);
                component.setHeight(size[1]);
            }
        });

        if (ImGui.isItemDeactivatedAfterEdit()) {
            changed = true;
        }

        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Width x Height of blocking area (in tiles, minimum 1x1)");
        }

        return changed;
    }

    private boolean drawElevationDropdown() {
        int currentElevation = component.getElevation();
        String currentLabel = ElevationLevel.getDisplayName(currentElevation) + " (level " + currentElevation + ")";

        final boolean[] changed = {false};
        FieldEditors.inspectorRow("Elevation", () -> {
            ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
            if (ImGui.beginCombo("##elevation", currentLabel)) {
                ElevationLevel[] levels = ElevationLevel.getAll();
                for (ElevationLevel level : levels) {
                    boolean isSelected = level.getLevel() == currentElevation;
                    String label = level.getDisplayName() + " (level " + level.getLevel() + ")";

                    if (ImGui.selectable(label, isSelected)) {
                        component.setElevation(level.getLevel());
                        changed[0] = true;
                    }
                }
                ImGui.endCombo();
            }
        });

        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Elevation level for collision blocking");
        }

        return changed[0];
    }

    private void drawAbsoluteTilesPreview() {
        ImGui.textDisabled("Blocked Tiles (computed):");

        if (entity == null) {
            ImGui.textDisabled("  (no entity)");
            return;
        }

        Vector3f pos = entity.getPosition();
        int baseX = (int) Math.floor(pos.x) + component.getOffsetX();
        int baseY = (int) Math.floor(pos.y) + component.getOffsetY();
        int w = component.getWidth();
        int h = component.getHeight();
        int elev = component.getElevation();

        ImGui.pushStyleColor(ImGuiCol.Text, 0.6f, 0.8f, 1.0f, 1.0f);

        if (w == 1 && h == 1) {
            // Single tile - simple display
            ImGui.text("  " + MaterialIcons.Block + " (" + baseX + ", " + baseY + ") elev=" + elev);
        } else {
            // Multi-tile - show range
            int endX = baseX + w - 1;
            int endY = baseY + h - 1;
            ImGui.text("  " + MaterialIcons.Block + " (" + baseX + ", " + baseY + ") to (" + endX + ", " + endY + ")");
            ImGui.text("  " + w * h + " tiles at elev=" + elev);
        }

        ImGui.popStyleColor();
    }
}
