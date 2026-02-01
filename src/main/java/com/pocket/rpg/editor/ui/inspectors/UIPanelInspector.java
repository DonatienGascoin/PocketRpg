package com.pocket.rpg.editor.ui.inspectors;

import com.pocket.rpg.components.ui.UIPanel;
import com.pocket.rpg.editor.core.MaterialIcons;
import com.pocket.rpg.editor.ui.fields.FieldEditorContext;
import com.pocket.rpg.editor.ui.fields.FieldEditors;
import com.pocket.rpg.editor.undo.UndoManager;
import com.pocket.rpg.editor.undo.commands.SetComponentFieldCommand;
import com.pocket.rpg.serialization.ComponentReflectionUtils;
import imgui.ImGui;
import org.joml.Vector4f;

/**
 * Custom editor for UIPanel component.
 */
@InspectorFor(UIPanel.class)
public class UIPanelInspector extends CustomComponentInspector<UIPanel> {

    // Common color presets
    private static final float[][] COLOR_PRESETS = {
            {0.2f, 0.2f, 0.2f, 1f},    // Dark gray
            {0.1f, 0.1f, 0.1f, 0.9f},  // Near black
            {0.3f, 0.3f, 0.4f, 0.8f},  // Blue-gray
            {0.15f, 0.15f, 0.15f, 1f}, // Darker gray
            {1f, 1f, 1f, 1f},          // White
            {0f, 0f, 0f, 0.5f},        // Semi-transparent black
    };

    private static final String[] PRESET_NAMES = {
            "Dark", "Black", "Blue", "Darker", "White", "Overlay"
    };

    // Undo tracking for continuous alpha slider edits
    private Vector4f alphaEditStartColor;

    @Override
    public boolean draw() {
        boolean changed = false;

        // === UI KEY ===
        changed |= UIKeyField.draw(component);

        // Background Color
        ImGui.text(MaterialIcons.FormatColorFill + " Background Color");
        changed |= FieldEditors.drawColor("Color", component, "color");

        // Quick alpha slider
        ImGui.spacing();
        Vector4f color = FieldEditors.getVector4f(component, "color");
        float[] alphaBuf = {color.w};
        ImGui.setNextItemWidth(-1);

        final boolean[] alphaChanged = {false};
        FieldEditors.inspectorRow("Alpha", () -> {
            alphaChanged[0] = ImGui.sliderFloat("##alpha", alphaBuf, 0f, 1f);
        });
        if (ImGui.isItemActivated()) {
            alphaEditStartColor = new Vector4f(color);
        }
        if (alphaChanged[0]) {
            color.w = alphaBuf[0];
            ComponentReflectionUtils.setFieldValue(component, "color", color);
            changed = true;
        }
        if (ImGui.isItemDeactivatedAfterEdit() && alphaEditStartColor != null) {
            Vector4f newColor = new Vector4f(FieldEditors.getVector4f(component, "color"));
            if (editorEntity() != null) {
                UndoManager.getInstance().push(
                        new SetComponentFieldCommand(component, "color", alphaEditStartColor, newColor, editorEntity())
                );
            }
            alphaEditStartColor = null;
        }

        // Color presets
        ImGui.spacing();
        ImGui.text("Presets:");
        ImGui.sameLine();

        for (int i = 0; i < COLOR_PRESETS.length; i++) {
            if (i > 0) ImGui.sameLine();

            float[] preset = COLOR_PRESETS[i];
            ImGui.pushID(i);

            // Color button
            if (ImGui.colorButton("##preset", preset)) {
                Vector4f oldColor = new Vector4f(FieldEditors.getVector4f(component, "color"));
                Vector4f newColor = new Vector4f(preset[0], preset[1], preset[2], preset[3]);
                ComponentReflectionUtils.setFieldValue(component, "color", newColor);
                if (editorEntity() != null) {
                    UndoManager.getInstance().push(
                            new SetComponentFieldCommand(component, "color", oldColor, newColor, editorEntity())
                    );
                }
                changed = true;
            }

            if (ImGui.isItemHovered()) {
                ImGui.setTooltip(PRESET_NAMES[i]);
            }

            ImGui.popID();
        }

        return changed;
    }
}
