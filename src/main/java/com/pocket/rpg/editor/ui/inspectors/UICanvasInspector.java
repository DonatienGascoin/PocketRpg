package com.pocket.rpg.editor.ui.inspectors;

import com.pocket.rpg.components.ui.UICanvas;
import com.pocket.rpg.editor.core.MaterialIcons;
import com.pocket.rpg.editor.ui.fields.FieldEditors;
import com.pocket.rpg.serialization.ComponentReflectionUtils;
import imgui.ImGui;
import imgui.type.ImInt;

/**
 * Custom editor for UICanvas component.
 * Shows planeDistance only when renderMode is SCREEN_SPACE_CAMERA.
 */
@InspectorFor(UICanvas.class)
public class UICanvasInspector extends CustomComponentInspector<UICanvas> {

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
    public boolean draw() {
        boolean changed = false;

        // === UI KEY ===
        changed |= FieldEditors.drawString("UI Key", component, "uiKey");

        ImGui.spacing();

        // Render Mode
        ImGui.text(MaterialIcons.DesktopWindows + " Render Mode");

        String currentMode = getEnumValue("renderMode", "SCREEN_SPACE_OVERLAY");
        int currentIndex = indexOf(RENDER_MODES, currentMode);

        ImGui.setNextItemWidth(-1);
        ImInt selected = new ImInt(currentIndex);
        // TODO: Refactor to use FieldEditors for @Required support and undo
        if (ImGui.combo("##renderMode", selected, RENDER_MODES)) {
            ComponentReflectionUtils.setFieldValue(component, "renderMode",
                    UICanvas.RenderMode.valueOf(RENDER_MODES[selected.get()]));
            changed = true;
        }

        // Tooltip for current mode
        if (ImGui.isItemHovered() && currentIndex >= 0 && currentIndex < RENDER_MODE_TOOLTIPS.length) {
            ImGui.setTooltip(RENDER_MODE_TOOLTIPS[currentIndex]);
        }

        ImGui.spacing();

        // Sort Order
        ImGui.text(MaterialIcons.Layers + " Sort Order");
        ImGui.sameLine(120);
        ImGui.textDisabled("Higher = on top");

        changed |= FieldEditors.drawInt("##sortOrder", component, "sortOrder");

        // Plane Distance - only for SCREEN_SPACE_CAMERA
        String mode = getEnumValue("renderMode", "SCREEN_SPACE_OVERLAY");
        if ("SCREEN_SPACE_CAMERA".equals(mode)) {
            ImGui.spacing();
            ImGui.separator();
            ImGui.spacing();

            ImGui.text(MaterialIcons.Straighten + " Plane Distance");
            changed |= FieldEditors.drawFloat("##planeDistance", component, "planeDistance", 1.0f, 0.1f, 10000f);
        }

        return changed;
    }

    private String getEnumValue(String fieldName, String defaultValue) {
        Object value = ComponentReflectionUtils.getFieldValue(component, fieldName);
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
