package com.pocket.rpg.editor.utils;

import com.pocket.rpg.editor.core.FontAwesomeIcons;
import com.pocket.rpg.editor.scene.EditorEntity;
import com.pocket.rpg.serialization.ComponentData;
import imgui.ImGui;
import imgui.type.ImInt;

import java.util.Map;

/**
 * Custom editor for UICanvas component.
 * Shows planeDistance only when renderMode is SCREEN_SPACE_CAMERA.
 */
public class UICanvasEditor implements CustomComponentEditor {

    private static final String[] RENDER_MODES = {
            "SCREEN_SPACE_OVERLAY",
            "SCREEN_SPACE_CAMERA",
            "WORLD_SPACE"
    };

    private static final String[] RENDER_MODE_TOOLTIPS = {
            "Renders on top of everything, ignores camera",
            "Renders at a plane distance from camera",
            "Renders in world space (e.g., health bars)"
    };

    @Override
    public boolean draw(ComponentData data, EditorEntity entity) {
        Map<String, Object> fields = data.getFields();
        boolean changed = false;

        // Render Mode
        ImGui.text(FontAwesomeIcons.Desktop + " Render Mode");
        
        String currentMode = getEnumValue(fields, "renderMode", "SCREEN_SPACE_OVERLAY");
        int currentIndex = indexOf(RENDER_MODES, currentMode);
        
        ImGui.setNextItemWidth(-1);
        ImInt selected = new ImInt(currentIndex);
        if (ImGui.combo("##renderMode", selected, RENDER_MODES)) {
            fields.put("renderMode", RENDER_MODES[selected.get()]);
            changed = true;
        }
        
        // Tooltip for current mode
        if (ImGui.isItemHovered() && currentIndex >= 0 && currentIndex < RENDER_MODE_TOOLTIPS.length) {
            ImGui.setTooltip(RENDER_MODE_TOOLTIPS[currentIndex]);
        }

        ImGui.spacing();

        // Sort Order
        ImGui.text(FontAwesomeIcons.LayerGroup + " Sort Order");
        ImGui.sameLine(120);
        ImGui.textDisabled("Higher = on top");
        
        changed |= FieldEditors.drawInt("##sortOrder", fields, "sortOrder");

        // Plane Distance - only for SCREEN_SPACE_CAMERA
        String mode = getEnumValue(fields, "renderMode", "SCREEN_SPACE_OVERLAY");
        if ("SCREEN_SPACE_CAMERA".equals(mode)) {
            ImGui.spacing();
            ImGui.separator();
            ImGui.spacing();
            
            ImGui.text(FontAwesomeIcons.RulerHorizontal + " Plane Distance");
            changed |= FieldEditors.drawFloat("##planeDistance", fields, "planeDistance", 1.0f, 0.1f, 10000f);
        }

        return changed;
    }

    private String getEnumValue(Map<String, Object> fields, String key, String defaultValue) {
        Object value = fields.get(key);
        if (value == null) return defaultValue;
        if (value instanceof Enum<?> e) return e.name();
        return value.toString();
    }

    private int indexOf(String[] array, String value) {
        for (int i = 0; i < array.length; i++) {
            if (array[i].equals(value)) return i;
        }
        return 0;
    }
}
