package com.pocket.rpg.editor.panels.inspector;

import com.pocket.rpg.editor.core.MaterialIcons;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.scene.SceneCameraSettings;
import com.pocket.rpg.editor.utils.IconUtils;
import imgui.ImGui;
import lombok.Setter;

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

        boolean useBounds = cam.isUseBounds();
        if (ImGui.checkbox("Use Bounds", useBounds)) {
            cam.setUseBounds(!useBounds);
            scene.markDirty();
        }

        if (cam.isUseBounds()) {
            floatBuffer[0] = cam.getBounds().x;
            floatBuffer[1] = cam.getBounds().y;
            ImGui.text("Min (X, Y)");
            if (ImGui.dragFloat2("##boundsMin", floatBuffer, 0.5f)) {
                cam.setBounds(floatBuffer[0], floatBuffer[1], cam.getBounds().z, cam.getBounds().w);
                scene.markDirty();
            }

            floatBuffer[0] = cam.getBounds().z;
            floatBuffer[1] = cam.getBounds().w;
            ImGui.text("Max (X, Y)");
            if (ImGui.dragFloat2("##boundsMax", floatBuffer, 0.5f)) {
                cam.setBounds(cam.getBounds().x, cam.getBounds().y, floatBuffer[0], floatBuffer[1]);
                scene.markDirty();
            }
        }
    }

    /**
     * Resets camera settings to game config defaults.
     */
    private void resetToDefaults() {
        SceneCameraSettings cam = scene.getCameraSettings();

        // Reset to defaults (ortho size is controlled via RenderingConfig)
        cam.setPosition(0, 0);
        cam.setUseBounds(false);
        cam.setBounds(0, 0, 100, 100);

        scene.markDirty();
    }
}
