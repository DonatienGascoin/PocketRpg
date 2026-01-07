package com.pocket.rpg.editor.utils;

import com.pocket.rpg.editor.core.FontAwesomeIcons;
import com.pocket.rpg.editor.scene.EditorEntity;
import com.pocket.rpg.serialization.ComponentData;
import imgui.ImGui;

import java.util.Map;

/**
 * Custom editor for UIPanel component.
 */
public class UIPanelEditor implements CustomComponentEditor {

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

    @Override
    public boolean draw(ComponentData data, EditorEntity entity) {
        Map<String, Object> fields = data.getFields();
        boolean changed = false;

        // Background Color
        ImGui.text(FontAwesomeIcons.FillDrip + " Background Color");
        changed |= FieldEditors.drawColor("Color", fields, "color");

        // Quick alpha slider
        ImGui.spacing();
        float alpha = getAlpha(fields);
        float[] alphaBuf = {alpha};
        ImGui.setNextItemWidth(-1);

        final boolean[] alphaChanged = {false};
        FieldEditors.inspectorRow("Alpha", () -> {
            alphaChanged[0] = ImGui.sliderFloat("##alpha", alphaBuf, 0f, 1f);
        });
        if (alphaChanged[0]) {
            setAlpha(fields, alphaBuf[0]);
            changed = true;
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
                fields.put("color", new org.joml.Vector4f(preset[0], preset[1], preset[2], preset[3]));
                changed = true;
            }
            
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip(PRESET_NAMES[i]);
            }
            
            ImGui.popID();
        }

        return changed;
    }

    private float getAlpha(Map<String, Object> fields) {
        return FieldEditors.getVector4f(fields, "color").w;
    }

    private void setAlpha(Map<String, Object> fields, float alpha) {
        var color = FieldEditors.getVector4f(fields, "color");
        color.w = alpha;
        fields.put("color", color);
    }
}
