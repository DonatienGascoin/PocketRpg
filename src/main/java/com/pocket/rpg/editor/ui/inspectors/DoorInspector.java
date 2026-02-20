package com.pocket.rpg.editor.ui.inspectors;

import com.pocket.rpg.collision.ElevationLevel;
import com.pocket.rpg.components.interaction.Door;
import com.pocket.rpg.editor.core.EditorColors;
import com.pocket.rpg.editor.ui.fields.FieldEditors;
import com.pocket.rpg.editor.undo.UndoManager;
import com.pocket.rpg.editor.undo.commands.SetterUndoCommand;
import imgui.ImGui;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

/**
 * Custom editor for Door component.
 * <p>
 * Features:
 * - State toggles (open, locked, stayOpen, consumeKey)
 * - Key ID input field
 * - Offset and size controls
 * - Elevation dropdown
 * - Preview of door tile position
 */
@InspectorFor(Door.class)
public class DoorInspector extends CustomComponentInspector<Door> {

    /** Tracks start values for dragInt2 undo. */
    private static final Map<String, Object> undoStartValues = new HashMap<>();

    @Override
    public boolean draw() {
        boolean changed = false;
        String id = String.valueOf(System.identityHashCode(component));

        // State section
        ImGui.text("Door State");
        ImGui.separator();

        changed |= FieldEditors.drawBoolean("Open", "door.open." + id,
                component::isOpen, component::setOpen);

        changed |= FieldEditors.drawBoolean("Stay Open", "door.stayOpen." + id,
                component::isStayOpen, component::setStayOpen);
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("If true, door cannot be closed after opening");
        }

        ImGui.spacing();
        ImGui.spacing();

        // Lock section
        ImGui.text("Lock Settings");
        ImGui.separator();

        changed |= drawLockSettings(id);

        ImGui.spacing();
        ImGui.spacing();

        // Position section
        ImGui.text("Position & Size");
        ImGui.separator();

        changed |= drawOffset(id);
        ImGui.spacing();
        changed |= drawSize(id);
        ImGui.spacing();
        changed |= drawElevationDropdown();

        // Preview
        ImGui.spacing();
        ImGui.separator();
        ImGui.spacing();
        drawTilePreview();

        return changed;
    }

    private boolean drawLockSettings(String id) {
        boolean changed = false;

        changed |= FieldEditors.drawBoolean("Locked", "door.locked." + id,
                component::isLocked, component::setLocked);

        // Only show key settings if locked
        if (component.isLocked()) {
            changed |= FieldEditors.drawString("Required Key", "door.keyId." + id,
                    component::getRequiredKeyId, component::setRequiredKeyId);
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("Item ID of the key required to unlock");
            }

            changed |= FieldEditors.drawBoolean("Consume Key", "door.consumeKey." + id,
                    component::isConsumeKey, component::setConsumeKey);
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("If true, key is removed from inventory when used");
            }
        }

        return changed;
    }

    private boolean drawOffset(String id) {
        String key = "door.offset." + id;
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
        String key = "door.size." + id;
        int startW = component.getWidth();
        int startH = component.getHeight();
        int[] size = {startW, startH};

        FieldEditors.inspectorRow("Size", () -> {
            ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
            ImGui.dragInt2("##size", size, 0.1f, 1, 10);
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
            ImGui.setTooltip("Width x Height of the door (in tiles)");
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
            ImGui.setTooltip("Elevation level for collision");
        }

        return changed[0];
    }

    private void drawTilePreview() {
        ImGui.textDisabled("Door Tile (computed):");

        if (editorEntity() == null) {
            ImGui.textDisabled("  (no entity)");
            return;
        }

        Vector3f pos = editorEntity().getPosition();
        int x = (int) Math.floor(pos.x) + component.getOffsetX();
        int y = (int) Math.floor(pos.y) + component.getOffsetY();
        int elev = component.getElevation();

        // Show state with color
        if (component.isOpen()) {
            EditorColors.textColored(EditorColors.SUCCESS, "  OPEN at (" + x + ", " + y + ") elev=" + elev);
        } else if (component.isLocked()) {
            EditorColors.textColored(EditorColors.DANGER, "  LOCKED at (" + x + ", " + y + ") elev=" + elev);
        } else {
            EditorColors.textColored(EditorColors.WARNING, "  CLOSED at (" + x + ", " + y + ") elev=" + elev);
        }
    }
}
