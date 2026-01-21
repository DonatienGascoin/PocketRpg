package com.pocket.rpg.editor.panels.inspector;

import com.pocket.rpg.editor.core.MaterialIcons;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.scene.SceneCameraSettings;
import com.pocket.rpg.editor.utils.IconUtils;
import imgui.ImGui;
import imgui.type.ImString;
import lombok.Setter;

/**
 * Renders camera settings inspector.
 */
public class CameraInspector {

    @Setter
    private EditorScene scene;

    private final float[] floatBuffer = new float[4];
    private final ImString stringBuffer = new ImString(256);

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

        floatBuffer[0] = cam.getOrthographicSize();
        if (ImGui.dragFloat("Ortho Size", floatBuffer, 0.5f, 1f, 50f)) {
            cam.setOrthographicSize(floatBuffer[0]);
            scene.markDirty();
        }

        ImGui.separator();
        ImGui.text("Follow Target");

        boolean followPlayer = cam.isFollowPlayer();
        if (ImGui.checkbox("Follow Player", followPlayer)) {
            cam.setFollowPlayer(!followPlayer);
            scene.markDirty();
        }

        if (cam.isFollowPlayer()) {
            stringBuffer.set(cam.getFollowTargetName());
            if (ImGui.inputText("Target Name", stringBuffer)) {
                cam.setFollowTargetName(stringBuffer.get());
                scene.markDirty();
            }
        }

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
        
        // Reset to defaults (matching SceneCameraSettings constructor defaults)
        cam.setPosition(0, 0);
        cam.setOrthographicSize(10f);
        cam.setFollowPlayer(false);
        cam.setFollowTargetName("");
        cam.setUseBounds(false);
        cam.setBounds(0, 0, 100, 100);
        
        scene.markDirty();
    }
}
