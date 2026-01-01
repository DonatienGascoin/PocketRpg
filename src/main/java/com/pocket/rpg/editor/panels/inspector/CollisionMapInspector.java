package com.pocket.rpg.editor.panels.inspector;

import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.utils.IconUtils;
import imgui.ImGui;
import lombok.Setter;

/**
 * Renders collision map inspector.
 */
public class CollisionMapInspector {

    @Setter
    private EditorScene scene;

    public void render() {
        ImGui.text(IconUtils.getCollisionsIcon() + " Collision Map");
        ImGui.separator();

        boolean collisionVisible = scene.isCollisionVisible();
        if (ImGui.checkbox("Show Collision Overlay", collisionVisible)) {
            scene.setCollisionVisible(!collisionVisible);
        }

        ImGui.separator();
        ImGui.text("Tools");
        ImGui.bulletText("Brush - Paint collision");
        ImGui.bulletText("Eraser - Remove collision");
        ImGui.bulletText("Fill - Fill area");
        ImGui.bulletText("Rectangle - Draw rectangle");

        ImGui.separator();
        ImGui.textDisabled("Collision data stored in scene file");
    }
}
