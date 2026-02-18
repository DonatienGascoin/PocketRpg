package com.pocket.rpg.editor.ui.inspectors;

import com.pocket.rpg.collision.Direction;
import com.pocket.rpg.components.interaction.CameraBoundsZone;
import com.pocket.rpg.components.interaction.SpawnPoint;
import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.core.EditorColors;
import com.pocket.rpg.editor.ui.fields.FieldEditorContext;
import com.pocket.rpg.editor.ui.fields.FieldEditors;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.type.ImString;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Custom editor for SpawnPoint component.
 * <p>
 * Features:
 * - Spawn ID input field (required, with red background if missing)
 * - Facing direction dropdown
 * - Camera bounds ID dropdown (references CameraBoundsZone entities)
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

        ImGui.spacing();

        // Camera bounds section
        ImGui.text("Camera");
        ImGui.separator();

        changed |= drawCameraBoundsIdDropdown();

        ImGui.spacing();

        // Audio section
        ImGui.text("Audio");
        ImGui.separator();

        changed |= FieldEditors.drawAudioClip("Arrival Sound", component, "arrivalSound", editorEntity());

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
            EditorColors.textColored(EditorColors.WARNING, "Spawn ID required");
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

    private boolean drawCameraBoundsIdDropdown() {
        String currentId = component.getCameraBoundsId();
        String display = (currentId == null || currentId.isEmpty()) ? "(none)" : currentId;

        List<String> boundsIds = getAvailableBoundsIds();

        // Check if reference is broken
        boolean isBroken = currentId != null && !currentId.isEmpty() && !boundsIds.contains(currentId);

        // Begin row highlight if broken
        float rowStartY = 0;
        if (isBroken) {
            ImVec2 cursorPos = ImGui.getCursorScreenPos();
            rowStartY = cursorPos.y;
            ImGui.getWindowDrawList().channelsSplit(2);
            ImGui.getWindowDrawList().channelsSetCurrent(1);
        }

        final boolean[] changed = {false};
        FieldEditors.inspectorRow("Camera Bounds", () -> {
            ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
            if (ImGui.beginCombo("##cameraBoundsId", display)) {
                // Option for no bounds (empty value)
                if (ImGui.selectable("(none)", currentId == null || currentId.isEmpty())) {
                    component.setCameraBoundsId("");
                    changed[0] = true;
                }

                if (!boundsIds.isEmpty()) {
                    ImGui.separator();
                }

                for (String boundsId : boundsIds) {
                    boolean isSelected = boundsId.equals(currentId);
                    if (ImGui.selectable(boundsId, isSelected)) {
                        component.setCameraBoundsId(boundsId);
                        changed[0] = true;
                    }
                }

                ImGui.endCombo();
            }
        });
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("CameraBoundsZone to activate when arriving at this spawn point.\nSelect (none) for no bounds change.");
        }

        // End row highlight
        if (isBroken) {
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

        // Warning text for broken reference
        if (isBroken) {
            EditorColors.textColored(EditorColors.WARNING, "Bounds zone '" + currentId + "' not found");
        }

        return changed[0];
    }

    private List<String> getAvailableBoundsIds() {
        List<String> ids = new ArrayList<>();

        EditorScene scene = FieldEditorContext.getCurrentScene();
        if (scene == null) return ids;

        for (EditorGameObject obj : scene.getEntities()) {
            CameraBoundsZone zone = obj.getComponent(CameraBoundsZone.class);
            if (zone != null) {
                String id = zone.getBoundsId();
                if (id != null && !id.isBlank()) {
                    ids.add(id);
                }
            }
        }
        return ids;
    }

    private void drawPositionPreview() {
        ImGui.textDisabled("Spawn Position:");

        if (editorEntity() == null) {
            ImGui.textDisabled("  (no entity)");
            return;
        }

        Vector3f pos = editorEntity().getPosition();
        int x = (int) Math.floor(pos.x);
        int y = (int) Math.floor(pos.y);

        ImGui.text("  Tile (" + x + ", " + y + ")");

        Direction facing = component.getFacingDirection();
        if (facing != null) {
            ImGui.textDisabled("  Facing: " + facing.name());
        }
    }
}
