package com.pocket.rpg.editor.assets;

import com.pocket.rpg.dialogue.DialogueVariable;
import com.pocket.rpg.dialogue.DialogueVariables;
import com.pocket.rpg.editor.core.EditorColors;
import com.pocket.rpg.editor.core.MaterialIcons;
import imgui.ImGui;

import java.util.List;

/**
 * Preview renderer for DialogueVariables assets.
 * Shows variable count and a compact list of variable names with types.
 */
public class DialogueVariablesPreviewRenderer implements AssetPreviewRenderer<DialogueVariables> {

    @Override
    public void renderPreview(DialogueVariables variables, float maxSize) {
        if (variables == null) {
            ImGui.textDisabled("No variables asset");
            return;
        }

        EditorColors.textColored(EditorColors.INFO, MaterialIcons.DataObject + " Dialogue Variables");

        ImGui.spacing();
        ImGui.textWrapped("Named placeholders replaced in dialogue text. Write [VAR_NAME] in a line.");

        ImGui.spacing();
        ImGui.separator();
        ImGui.spacing();

        List<DialogueVariable> varList = variables.getVariables();
        int count = varList != null ? varList.size() : 0;
        ImGui.text(count + (count == 1 ? " variable" : " variables") + " defined");

        ImGui.spacing();

        if (varList != null && !varList.isEmpty()) {
            for (DialogueVariable var : varList) {
                String name = var.getName() != null ? var.getName() : "?";
                String type = var.getType() != null ? var.getType().name() : "?";
                ImGui.bulletText(name + "  (" + type + ")");
            }
        } else {
            ImGui.textDisabled("(none)");
        }

        ImGui.spacing();
        ImGui.separator();
        ImGui.spacing();
        ImGui.textDisabled("Open in editor to add/remove.");
    }

    @Override
    public Class<DialogueVariables> getAssetType() {
        return DialogueVariables.class;
    }
}
