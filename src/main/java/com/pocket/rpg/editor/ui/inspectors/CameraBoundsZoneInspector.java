package com.pocket.rpg.editor.ui.inspectors;

import com.pocket.rpg.components.interaction.CameraBoundsZone;
import com.pocket.rpg.editor.core.EditorColors;
import com.pocket.rpg.editor.core.MaterialIcons;
import com.pocket.rpg.editor.events.EditorEventBus;
import com.pocket.rpg.editor.events.ToggleBoundsZoneToolEvent;
import com.pocket.rpg.editor.tools.BoundsZoneTool;
import com.pocket.rpg.editor.ui.fields.ReflectionFieldEditor;
import com.pocket.rpg.serialization.ComponentMeta;
import com.pocket.rpg.serialization.ComponentReflectionUtils;
import com.pocket.rpg.serialization.FieldMeta;
import imgui.ImGui;

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
                changed |= ReflectionFieldEditor.drawField(component, fieldMeta, editorEntity());
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
                changed |= ReflectionFieldEditor.drawField(component, fieldMeta, editorEntity());
            } catch (Exception e) {
                EditorColors.textColored(EditorColors.DANGER, fieldMeta.name() + ": Error");
            }
        }

        return changed;
    }

    private void drawEditBoundsButton() {
        boolean isActive = BoundsZoneTool.isToolActive();

        if (isActive) {
            EditorColors.pushDangerButton();
        }

        String label = isActive
                ? MaterialIcons.Close + " Stop Editing"
                : MaterialIcons.OpenWith + " Edit Bounds";

        if (ImGui.button(label, 120, 0)) {
            EditorEventBus.get().publish(new ToggleBoundsZoneToolEvent(!isActive));
        }

        if (isActive) {
            EditorColors.popButtonColors();
        }

        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(isActive
                    ? "Deactivate bounds handle editing in the viewport."
                    : "Activate drag handles to resize bounds in the viewport.");
        }
    }
}
