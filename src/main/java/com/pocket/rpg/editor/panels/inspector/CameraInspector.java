package com.pocket.rpg.editor.panels.inspector;

import com.pocket.rpg.components.interaction.CameraBoundsZone;
import com.pocket.rpg.editor.core.MaterialIcons;
import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.scene.SceneCameraSettings;
import com.pocket.rpg.editor.ui.fields.FieldEditors;
import com.pocket.rpg.editor.undo.UndoManager;
import com.pocket.rpg.editor.undo.commands.SetInitialBoundsIdCommand;
import com.pocket.rpg.editor.utils.IconUtils;
import imgui.ImGui;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders camera settings inspector.
 */
public class CameraInspector {

    @Setter
    private EditorScene scene;

    private final float[] floatBuffer = new float[4];

    public void render() {
        ImGui.text(IconUtils.getCameraIcon() + " Scene Camera");

        // FIX: Add reset button
        ImGui.sameLine(ImGui.getContentRegionMaxX() - 80);
        if (ImGui.smallButton(MaterialIcons.Undo + " Reset")) {
            resetToDefaults();
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Reset camera to game config defaults");
        }

        ImGui.separator();

        SceneCameraSettings cam = scene.getCameraSettings();

        floatBuffer[0] = cam.getPosition().x;
        floatBuffer[1] = cam.getPosition().y;
        if (ImGui.dragFloat2("Start Position", floatBuffer, 0.5f)) {
            cam.setPosition(floatBuffer[0], floatBuffer[1]);
            scene.markDirty();
        }

        // Note: Orthographic size is controlled globally via RenderingConfig

        ImGui.separator();
        ImGui.text("Camera Bounds");

        drawInitialBoundsIdDropdown(cam);
    }

    private void drawInitialBoundsIdDropdown(SceneCameraSettings cam) {
        String currentId = cam.getInitialBoundsId();
        String display = (currentId == null || currentId.isEmpty()) ? "(none)" : currentId;

        List<String> boundsIds = getAvailableBoundsIds();

        FieldEditors.inspectorRow("Initial Bounds", () -> {
            if (ImGui.beginCombo("##initialBoundsId", display)) {
                // Option for no bounds (empty value)
                if (ImGui.selectable("(none)", currentId == null || currentId.isEmpty())) {
                    applyInitialBoundsId(cam, currentId, "");
                }

                if (!boundsIds.isEmpty()) {
                    ImGui.separator();
                }

                for (String boundsId : boundsIds) {
                    boolean isSelected = boundsId.equals(currentId);
                    if (ImGui.selectable(boundsId, isSelected)) {
                        applyInitialBoundsId(cam, currentId, boundsId);
                    }
                }

                ImGui.endCombo();
            }
        });
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("CameraBoundsZone to activate on scene load.\nSelect (none) for no bounds.");
        }
    }

    private void applyInitialBoundsId(SceneCameraSettings cam, String oldId, String newId) {
        if (oldId == null) oldId = "";
        if (oldId.equals(newId)) return;

        cam.setInitialBoundsId(newId);
        scene.markDirty();
        UndoManager.getInstance().push(new SetInitialBoundsIdCommand(cam, oldId, newId));
    }

    private List<String> getAvailableBoundsIds() {
        List<String> ids = new ArrayList<>();
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

    /**
     * Resets camera settings to game config defaults.
     */
    private void resetToDefaults() {
        SceneCameraSettings cam = scene.getCameraSettings();

        // Reset to defaults (ortho size is controlled via RenderingConfig)
        cam.setPosition(0, 0);
        cam.setInitialBoundsId("");

        scene.markDirty();
    }
}
