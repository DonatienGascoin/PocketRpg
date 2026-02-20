package com.pocket.rpg.editor.ui.inspectors;

import com.pocket.rpg.collision.ElevationLevel;
import com.pocket.rpg.components.interaction.StaticOccupant;
import com.pocket.rpg.editor.core.EditorColors;
import com.pocket.rpg.editor.core.MaterialIcons;
import com.pocket.rpg.editor.ui.fields.FieldEditors;
import com.pocket.rpg.editor.undo.UndoManager;
import com.pocket.rpg.editor.undo.commands.SetterUndoCommand;
import imgui.ImGui;
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
        String id = String.valueOf(System.identityHashCode(component));

        // Offset
        changed |= drawOffset(id);

        ImGui.spacing();

        // Size
        changed |= drawSize(id);

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

    private boolean drawOffset(String id) {
        boolean changed = FieldEditors.drawDragInt2("Offset", "occupant.offset." + id,
                component::getOffsetX, component::getOffsetY,
                v -> { component.setOffsetX(v[0]); component.setOffsetY(v[1]); },
                0.1f);
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Offset from entity position (in tiles)");
        }
        return changed;
    }

    private boolean drawSize(String id) {
        boolean changed = FieldEditors.drawDragInt2("Size", "occupant.size." + id,
                component::getWidth, component::getHeight,
                v -> { component.setWidth(v[0]); component.setHeight(v[1]); },
                0.1f, 1, 100);
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
                        int oldValue = currentElevation;
                        int newValue = level.getLevel();
                        component.setElevation(newValue);
                        UndoManager.getInstance().push(new SetterUndoCommand<>(
                                v -> component.setElevation(v), oldValue, newValue, "Change Elevation"
                        ));
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

        if (editorEntity() == null) {
            ImGui.textDisabled("  (no entity)");
            return;
        }

        Vector3f pos = editorEntity().getPosition();
        int baseX = (int) Math.floor(pos.x) + component.getOffsetX();
        int baseY = (int) Math.floor(pos.y) + component.getOffsetY();
        int w = component.getWidth();
        int h = component.getHeight();
        int elev = component.getElevation();

        if (w == 1 && h == 1) {
            // Single tile - simple display
            EditorColors.textColored(EditorColors.INFO, "  " + MaterialIcons.Block + " (" + baseX + ", " + baseY + ") elev=" + elev);
        } else {
            // Multi-tile - show range
            int endX = baseX + w - 1;
            int endY = baseY + h - 1;
            EditorColors.textColored(EditorColors.INFO, "  " + MaterialIcons.Block + " (" + baseX + ", " + baseY + ") to (" + endX + ", " + endY + ")");
            EditorColors.textColored(EditorColors.INFO, "  " + w * h + " tiles at elev=" + elev);
        }
    }
}
