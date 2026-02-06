package com.pocket.rpg.editor.ui.inspectors;

import com.pocket.rpg.collision.ElevationLevel;
import com.pocket.rpg.components.interaction.Door;
import com.pocket.rpg.editor.ui.fields.FieldEditors;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.type.ImBoolean;
import imgui.type.ImString;
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

    private final ImBoolean openState = new ImBoolean();
    private final ImBoolean lockedState = new ImBoolean();
    private final ImBoolean stayOpenState = new ImBoolean();
    private final ImBoolean consumeKeyState = new ImBoolean();
    private final ImString keyIdBuffer = new ImString(256);

    @Override
    public boolean draw() {
        boolean changed = false;

        // State section
        ImGui.text("Door State");
        ImGui.separator();

        changed |= drawStateToggles();

        ImGui.spacing();
        ImGui.spacing();

        // Lock section
        ImGui.text("Lock Settings");
        ImGui.separator();

        changed |= drawLockSettings();

        ImGui.spacing();
        ImGui.spacing();

        // Position section
        ImGui.text("Position & Size");
        ImGui.separator();

        changed |= drawOffset();
        ImGui.spacing();
        changed |= drawSize();
        ImGui.spacing();
        changed |= drawElevationDropdown();

        // Preview
        ImGui.spacing();
        ImGui.separator();
        ImGui.spacing();
        drawTilePreview();

        return changed;
    }

    private boolean drawStateToggles() {
        boolean changed = false;

        // Open toggle
        openState.set(component.isOpen());
        FieldEditors.inspectorRow("Open", () -> {
            if (ImGui.checkbox("##open", openState)) {
                component.setOpen(openState.get());
            }
        });
        if (ImGui.isItemDeactivatedAfterEdit()) changed = true;

        // Stay open toggle
        stayOpenState.set(component.isStayOpen());
        FieldEditors.inspectorRow("Stay Open", () -> {
            if (ImGui.checkbox("##stayOpen", stayOpenState)) {
                component.setStayOpen(stayOpenState.get());
            }
        });
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("If true, door cannot be closed after opening");
        }
        if (ImGui.isItemDeactivatedAfterEdit()) changed = true;

        return changed;
    }

    private boolean drawLockSettings() {
        boolean changed = false;

        // Locked toggle
        lockedState.set(component.isLocked());
        FieldEditors.inspectorRow("Locked", () -> {
            if (ImGui.checkbox("##locked", lockedState)) {
                component.setLocked(lockedState.get());
            }
        });
        if (ImGui.isItemDeactivatedAfterEdit()) changed = true;

        // Only show key settings if locked
        if (component.isLocked()) {
            // Key ID
            keyIdBuffer.set(component.getRequiredKeyId() != null ? component.getRequiredKeyId() : "");
            FieldEditors.inspectorRow("Required Key", () -> {
                ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
                if (ImGui.inputText("##keyId", keyIdBuffer)) {
                    component.setRequiredKeyId(keyIdBuffer.get());
                }
            });
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("Item ID of the key required to unlock");
            }
            if (ImGui.isItemDeactivatedAfterEdit()) changed = true;

            // Consume key toggle
            consumeKeyState.set(component.isConsumeKey());
            FieldEditors.inspectorRow("Consume Key", () -> {
                if (ImGui.checkbox("##consumeKey", consumeKeyState)) {
                    component.setConsumeKey(consumeKeyState.get());
                }
            });
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("If true, key is removed from inventory when used");
            }
            if (ImGui.isItemDeactivatedAfterEdit()) changed = true;
        }

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
        if (ImGui.isItemDeactivatedAfterEdit()) changed = true;
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
            if (ImGui.dragInt2("##size", size, 0.1f, 1, 10)) {
                component.setWidth(size[0]);
                component.setHeight(size[1]);
            }
        });
        if (ImGui.isItemDeactivatedAfterEdit()) changed = true;
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
                        component.setElevation(level.getLevel());
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
            ImGui.pushStyleColor(ImGuiCol.Text, 0.4f, 0.9f, 0.4f, 1.0f); // Green
            ImGui.text("  OPEN at (" + x + ", " + y + ") elev=" + elev);
        } else if (component.isLocked()) {
            ImGui.pushStyleColor(ImGuiCol.Text, 0.9f, 0.4f, 0.4f, 1.0f); // Red
            ImGui.text("  LOCKED at (" + x + ", " + y + ") elev=" + elev);
        } else {
            ImGui.pushStyleColor(ImGuiCol.Text, 0.9f, 0.7f, 0.3f, 1.0f); // Yellow
            ImGui.text("  CLOSED at (" + x + ", " + y + ") elev=" + elev);
        }
        ImGui.popStyleColor();
    }
}
