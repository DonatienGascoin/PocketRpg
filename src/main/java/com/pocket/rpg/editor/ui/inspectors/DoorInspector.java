package com.pocket.rpg.editor.ui.inspectors;

import com.pocket.rpg.collision.ElevationLevel;
import com.pocket.rpg.components.interaction.Door;
import com.pocket.rpg.editor.core.EditorColors;
import com.pocket.rpg.editor.ui.fields.FieldEditors;
import com.pocket.rpg.editor.undo.UndoManager;
import com.pocket.rpg.editor.undo.commands.SetterUndoCommand;
import imgui.ImGui;
import org.joml.Vector3f;

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
        boolean changed = FieldEditors.drawDragInt2("Offset", "door.offset." + id,
                component::getOffsetX, component::getOffsetY,
                v -> { component.setOffsetX(v[0]); component.setOffsetY(v[1]); },
                0.1f);
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Offset from entity position (in tiles)");
        }
        return changed;
    }

    private boolean drawSize(String id) {
        boolean changed = FieldEditors.drawDragInt2("Size", "door.size." + id,
                component::getWidth, component::getHeight,
                v -> { component.setWidth(v[0]); component.setHeight(v[1]); },
                0.1f, 1, 10);
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
