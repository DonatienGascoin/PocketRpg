package com.pocket.rpg.editor.ui.inspectors;

import com.pocket.rpg.collision.Direction;
import com.pocket.rpg.components.interaction.SpawnPoint;
import com.pocket.rpg.editor.ui.fields.FieldEditors;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.type.ImString;
import org.joml.Vector3f;

/**
 * Custom editor for SpawnPoint component.
 * <p>
 * Features:
 * - Spawn ID input field (required, with red background if missing)
 * - Facing direction dropdown
 * - Preview of spawn position
 */
@InspectorFor(SpawnPoint.class)
public class SpawnPointInspector extends CustomComponentInspector<SpawnPoint> {

    private final ImString spawnIdBuffer = new ImString(256);

    private static final int ERROR_ROW_BG_COLOR = ImGui.colorConvertFloat4ToU32(1f, 0.1f, 0.1f, 0.7f);

    @Override
    public boolean draw() {
        boolean changed = false;

        // Spawn ID with required styling
        changed |= drawSpawnIdField();

        ImGui.spacing();

        // Facing direction dropdown
        changed |= drawFacingDirectionDropdown();

        // Preview
        ImGui.spacing();
        ImGui.separator();
        ImGui.spacing();
        drawPositionPreview();

        return changed;
    }

    private boolean drawSpawnIdField() {
        boolean changed = false;

        String currentId = component.getSpawnId();
        boolean isMissing = currentId == null || currentId.isBlank();

        // Begin row highlight if missing
        float rowStartY = 0;
        if (isMissing) {
            ImVec2 cursorPos = ImGui.getCursorScreenPos();
            rowStartY = cursorPos.y;
            ImGui.getWindowDrawList().channelsSplit(2);
            ImGui.getWindowDrawList().channelsSetCurrent(1);
        }

        spawnIdBuffer.set(currentId != null ? currentId : "");
        FieldEditors.inspectorRow("Spawn ID", () -> {
            ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
            if (ImGui.inputText("##spawnId", spawnIdBuffer)) {
                component.setSpawnId(spawnIdBuffer.get());
            }
        });
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Unique identifier for this spawn point.\nUsed by WarpZone to specify destination.");
        }
        if (ImGui.isItemDeactivatedAfterEdit()) changed = true;

        // End row highlight
        if (isMissing) {
            ImDrawList drawList = ImGui.getWindowDrawList();
            drawList.channelsSetCurrent(0);
            float padding = 2f;
            float startX = ImGui.getWindowPos().x + ImGui.getWindowContentRegionMin().x - padding;
            float endX = ImGui.getWindowPos().x + ImGui.getWindowContentRegionMax().x + padding;
            float startY = rowStartY - padding;
            float endY = ImGui.getCursorScreenPos().y;
            drawList.addRectFilled(startX, startY, endX, endY, ERROR_ROW_BG_COLOR);
            drawList.channelsMerge();
        }

        // Warning text
        if (isMissing) {
            ImGui.textColored(1.0f, 0.8f, 0.2f, 1.0f, "Spawn ID required");
        }

        return changed;
    }

    private boolean drawFacingDirectionDropdown() {
        Direction current = component.getFacingDirection();
        String currentName = current != null ? current.name() : "DOWN";

        final boolean[] changed = {false};
        FieldEditors.inspectorRow("Facing Direction", () -> {
            ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
            if (ImGui.beginCombo("##facingDir", currentName)) {
                for (Direction dir : Direction.values()) {
                    boolean isSelected = dir == current;
                    if (ImGui.selectable(dir.name(), isSelected)) {
                        component.setFacingDirection(dir);
                        changed[0] = true;
                    }
                }
                ImGui.endCombo();
            }
        });
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Direction the player should face after spawning.");
        }

        return changed[0];
    }

    private void drawPositionPreview() {
        ImGui.textDisabled("Spawn Position:");

        if (entity == null) {
            ImGui.textDisabled("  (no entity)");
            return;
        }

        Vector3f pos = entity.getPosition();
        int x = (int) Math.floor(pos.x);
        int y = (int) Math.floor(pos.y);

        ImGui.text("  Tile (" + x + ", " + y + ")");

        Direction facing = component.getFacingDirection();
        if (facing != null) {
            ImGui.textDisabled("  Facing: " + facing.name());
        }
    }
}
