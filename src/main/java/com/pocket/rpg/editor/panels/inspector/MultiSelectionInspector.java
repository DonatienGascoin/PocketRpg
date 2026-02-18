package com.pocket.rpg.editor.panels.inspector;

import com.pocket.rpg.editor.core.EditorColors;
import com.pocket.rpg.editor.core.MaterialIcons;
import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.undo.UndoManager;
import com.pocket.rpg.editor.undo.commands.BulkDeleteCommand;
import com.pocket.rpg.editor.undo.commands.BulkMoveCommand;
import com.pocket.rpg.editor.utils.IconUtils;
import imgui.ImGui;
import lombok.Setter;
import org.joml.Vector3f;

import java.util.Set;

/**
 * Renders multi-selection inspector.
 */
public class MultiSelectionInspector {

    @Setter
    private EditorScene scene;

    private final float[] floatBuffer = new float[4];

    public void render(Set<EditorGameObject> selected) {
        ImGui.text(IconUtils.getMultipleEntitiesIcon() + " " + selected.size() + " entities selected");
        ImGui.separator();

        // Bulk position offset
        ImGui.text("Move Offset");
        floatBuffer[0] = 0;
        floatBuffer[1] = 0;
        if (ImGui.dragFloat2("##offset", floatBuffer, 0.1f)) {
            if (floatBuffer[0] != 0 || floatBuffer[1] != 0) {
                Vector3f offset = new Vector3f(floatBuffer[0], floatBuffer[1], 0);
                UndoManager.getInstance().execute(new BulkMoveCommand(scene, selected, offset));
                floatBuffer[0] = 0;
                floatBuffer[1] = 0;
            }
        }

        ImGui.sameLine();
        if (ImGui.smallButton("Snap All")) {
            for (EditorGameObject entity : selected) {
                Vector3f pos = entity.getPosition();
                entity.setPosition(Math.round(pos.x * 2) / 2f, Math.round(pos.y));
            }
            scene.markDirty();
        }

        ImGui.separator();

        // List selected entities
        ImGui.text("Selected:");
        ImGui.beginChild("##selectedList", 0, 100, true);
        for (EditorGameObject entity : selected) {
            String icon = IconUtils.getIconForEntity(entity);
            ImGui.text(icon + " " + entity.getName());
        }
        ImGui.endChild();

        ImGui.separator();

        // Bulk actions
        EditorColors.pushDangerButton();
        if (ImGui.button(MaterialIcons.Delete + " Delete All", -1, 0)) {
            UndoManager.getInstance().execute(new BulkDeleteCommand(scene, selected));
        }
        EditorColors.popButtonColors();

        if (ImGui.button(MaterialIcons.Cancel + " Clear Selection", -1, 0)) {
            scene.clearSelection();
        }

        ImGui.separator();
        ImGui.textDisabled("Component editing disabled for multi-selection");
    }
}
