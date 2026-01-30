package com.pocket.rpg.editor.ui.inspectors;

import com.pocket.rpg.components.interaction.CameraBoundsZone;
import com.pocket.rpg.editor.core.MaterialIcons;
import com.pocket.rpg.editor.events.EditorEventBus;
import com.pocket.rpg.editor.events.ToggleBoundsZoneToolEvent;
import com.pocket.rpg.editor.tools.BoundsZoneTool;
import com.pocket.rpg.editor.ui.fields.ReflectionFieldEditor;
import com.pocket.rpg.serialization.ComponentMeta;
import com.pocket.rpg.serialization.ComponentReflectionUtils;
import com.pocket.rpg.serialization.FieldMeta;
import imgui.ImGui;
import imgui.flag.ImGuiCol;

import java.util.List;

/**
 * Custom editor for CameraBoundsZone component.
 * <p>
 * Renders boundsId field, then an "Edit Bounds" toggle button,
 * then the min/max fields. The button activates the BoundsZoneTool
 * for interactive handle-based bounds editing in the viewport.
 */
@InspectorFor(CameraBoundsZone.class)
public class CameraBoundsZoneInspector extends CustomComponentInspector<CameraBoundsZone> {

    @Override
    public boolean draw() {
        boolean changed = false;

        ComponentMeta meta = ComponentReflectionUtils.getMeta(component);
        if (meta == null) return false;

        List<FieldMeta> fields = meta.fields();

        // Note: FieldEditorContext.begin/end is managed by CustomComponentEditorRegistry
        // before/after calling draw(). Do NOT call begin/end here.

        // Draw boundsId field first
        for (FieldMeta fieldMeta : fields) {
            if ("boundsId".equals(fieldMeta.name())) {
                changed |= ReflectionFieldEditor.drawField(component, fieldMeta, entity);
                break;
            }
        }

        // Edit Bounds toggle button
        ImGui.spacing();
        drawEditBoundsButton();
        ImGui.spacing();

        // Draw remaining fields (minX, minY, maxX, maxY)
        for (FieldMeta fieldMeta : fields) {
            if ("boundsId".equals(fieldMeta.name())) continue;
            try {
                changed |= ReflectionFieldEditor.drawField(component, fieldMeta, entity);
            } catch (Exception e) {
                ImGui.textColored(1f, 0.3f, 0.3f, 1f, fieldMeta.name() + ": Error");
            }
        }

        return changed;
    }

    private void drawEditBoundsButton() {
        boolean isActive = BoundsZoneTool.isToolActive();

        if (isActive) {
            ImGui.pushStyleColor(ImGuiCol.Button, 0.8f, 0.3f, 0.3f, 1.0f);
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.9f, 0.4f, 0.4f, 1.0f);
            ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0.7f, 0.2f, 0.2f, 1.0f);
        }

        String label = isActive
                ? MaterialIcons.Close + " Stop Editing"
                : MaterialIcons.OpenWith + " Edit Bounds";

        if (ImGui.button(label, 120, 0)) {
            EditorEventBus.get().publish(new ToggleBoundsZoneToolEvent(!isActive));
        }

        if (isActive) {
            ImGui.popStyleColor(3);
        }

        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(isActive
                    ? "Deactivate bounds handle editing in the viewport."
                    : "Activate drag handles to resize bounds in the viewport.");
        }
    }
}
