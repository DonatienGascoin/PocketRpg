package com.pocket.rpg.editor.ui.widgets;

import com.pocket.rpg.editor.ui.fields.FieldEditors;
import com.pocket.rpg.editor.utils.SceneUtils;
import imgui.ImGui;

import java.util.List;

/**
 * Reusable ImGui scene selection dropdown.
 * Lists all available .scene files from the scenes directory.
 */
public final class SceneDropdown {

    private SceneDropdown() {
        // Utility class
    }

    /**
     * Draws a scene selection combo box.
     *
     * @param label      ImGui label for the row
     * @param imguiId    unique ImGui ID suffix
     * @param current    current scene name (empty or null = no selection)
     * @param allowEmpty whether to show a selectable empty option
     * @param emptyLabel display text for the empty option (e.g., "(same scene)" or "(none)")
     * @return the new scene name if changed, or {@code null} if unchanged
     */
    public static String draw(String label, String imguiId, String current, boolean allowEmpty, String emptyLabel) {
        List<String> availableScenes = SceneUtils.getAvailableSceneNames();

        boolean isEmpty = current == null || current.isBlank();
        String display = isEmpty ? emptyLabel : current;

        final String[] result = {null};

        FieldEditors.inspectorRow(label, () -> {
            ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
            if (ImGui.beginCombo("##" + imguiId, display)) {
                // Empty option
                if (allowEmpty) {
                    if (ImGui.selectable(emptyLabel, isEmpty)) {
                        if (!isEmpty) {
                            result[0] = "";
                        }
                    }

                    if (!availableScenes.isEmpty()) {
                        ImGui.separator();
                    }
                }

                // Available scenes
                for (String sceneName : availableScenes) {
                    boolean isSelected = sceneName.equals(current);
                    if (ImGui.selectable(sceneName, isSelected)) {
                        if (!sceneName.equals(current)) {
                            result[0] = sceneName;
                        }
                    }
                }

                ImGui.endCombo();
            }
        });

        return result[0];
    }
}
