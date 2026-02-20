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

import java.util.HashMap;
import java.util.Map;

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

    /** Tracks start values for dragInt2 undo. */
    private static final Map<String, Object> undoStartValues = new HashMap<>();

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
        String key = "occupant.offset." + id;
        int startX = component.getOffsetX();
        int startY = component.getOffsetY();
        int[] offset = {startX, startY};

        FieldEditors.inspectorRow("Offset", () -> {
            ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
            ImGui.dragInt2("##offset", offset, 0.1f);
        });

        if (ImGui.isItemActivated()) {
            undoStartValues.put(key, new int[]{startX, startY});
        }

        boolean changed = false;
        if (offset[0] != startX || offset[1] != startY) {
            component.setOffsetX(offset[0]);
            component.setOffsetY(offset[1]);
            changed = true;
        }

        if (ImGui.isItemDeactivatedAfterEdit() && undoStartValues.containsKey(key)) {
            int[] oldValues = (int[]) undoStartValues.remove(key);
            if (oldValues[0] != offset[0] || oldValues[1] != offset[1]) {
                UndoManager.getInstance().push(new SetterUndoCommand<>(
                        vals -> { component.setOffsetX(vals[0]); component.setOffsetY(vals[1]); },
                        oldValues, new int[]{offset[0], offset[1]}, "Change Offset"
                ));
            }
        }

        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Offset from entity position (in tiles)");
        }

        return changed;
    }

    private boolean drawSize(String id) {
        String key = "occupant.size." + id;
        int startW = component.getWidth();
        int startH = component.getHeight();
        int[] size = {startW, startH};

        FieldEditors.inspectorRow("Size", () -> {
            ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
            ImGui.dragInt2("##size", size, 0.1f, 1, 100);
        });

        if (ImGui.isItemActivated()) {
            undoStartValues.put(key, new int[]{startW, startH});
        }

        boolean changed = false;
        if (size[0] != startW || size[1] != startH) {
            component.setWidth(size[0]);
            component.setHeight(size[1]);
            changed = true;
        }

        if (ImGui.isItemDeactivatedAfterEdit() && undoStartValues.containsKey(key)) {
            int[] oldValues = (int[]) undoStartValues.remove(key);
            if (oldValues[0] != size[0] || oldValues[1] != size[1]) {
                UndoManager.getInstance().push(new SetterUndoCommand<>(
                        vals -> { component.setWidth(vals[0]); component.setHeight(vals[1]); },
                        oldValues, new int[]{size[0], size[1]}, "Change Size"
                ));
            }
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
