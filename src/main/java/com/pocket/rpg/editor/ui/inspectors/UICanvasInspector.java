package com.pocket.rpg.editor.ui.inspectors;

import com.pocket.rpg.components.ui.UICanvas;
import com.pocket.rpg.editor.core.MaterialIcons;
import com.pocket.rpg.editor.ui.fields.EnumEditor;
import com.pocket.rpg.editor.ui.fields.FieldEditors;
import imgui.ImGui;

/**
 * Custom editor for UICanvas component.
 * Shows planeDistance only when renderMode is SCREEN_SPACE_CAMERA.
 */
@InspectorFor(UICanvas.class)
public class UICanvasInspector extends CustomComponentInspector<UICanvas> {

    private static final String[] RENDER_MODE_TOOLTIPS = {
            "Renders on top of everything, ignores camera",
            "Renders at a plane distance from camera",
            "Renders in world space (e.g., health bars)"
    };

    @Override
    public boolean draw() {
        boolean changed = false;

        // === UI KEY ===
        changed |= ComponentKeyField.draw(component);

        // Render Mode
        ImGui.text(MaterialIcons.DesktopWindows + " Render Mode");
        changed |= EnumEditor.drawEnum("Render Mode", component, "renderMode", UICanvas.RenderMode.class);

        // Tooltip for current mode
        UICanvas.RenderMode currentMode = component.getRenderMode();
        if (currentMode == null) currentMode = UICanvas.RenderMode.SCREEN_SPACE_OVERLAY;
        int modeIndex = currentMode.ordinal();
        if (ImGui.isItemHovered() && modeIndex >= 0 && modeIndex < RENDER_MODE_TOOLTIPS.length) {
            ImGui.setTooltip(RENDER_MODE_TOOLTIPS[modeIndex]);
        }

        ImGui.spacing();

        // Sort Order
        ImGui.text(MaterialIcons.Layers + " Sort Order");
        ImGui.sameLine(120);
        ImGui.textDisabled("Higher = on top");

        changed |= FieldEditors.drawInt("##sortOrder", component, "sortOrder");

        // Plane Distance - only for SCREEN_SPACE_CAMERA
        if (currentMode == UICanvas.RenderMode.SCREEN_SPACE_CAMERA) {
            ImGui.spacing();
            ImGui.separator();
            ImGui.spacing();

            ImGui.text(MaterialIcons.Straighten + " Plane Distance");
            changed |= FieldEditors.drawFloat("##planeDistance", component, "planeDistance", 1.0f, 0.1f, 10000f);
        }

        return changed;
    }
}
