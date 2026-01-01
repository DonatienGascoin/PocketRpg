package com.pocket.rpg.editor.panels.inspector;

import com.pocket.rpg.editor.core.FontAwesomeIcons;
import com.pocket.rpg.editor.scene.EditorEntity;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.utils.ReflectionFieldEditor;
import com.pocket.rpg.serialization.ComponentData;
import com.pocket.rpg.serialization.ComponentMeta;
import com.pocket.rpg.serialization.ComponentRegistry;
import com.pocket.rpg.serialization.FieldMeta;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import lombok.Setter;

/**
 * Handles component field editing with prefab override support.
 */
public class ComponentFieldEditor {

    @Setter
    private EditorScene scene;

    public boolean renderComponentFields(EditorEntity entity, ComponentData comp, boolean isPrefabInstance) {
        if (isPrefabInstance) {
            return renderWithOverrides(entity, comp);
        } else {
            return ReflectionFieldEditor.drawComponent(comp, entity);
        }
    }

    private boolean renderWithOverrides(EditorEntity entity, ComponentData comp) {
        ComponentMeta meta = ComponentRegistry.getByClassName(comp.getType());
        if (meta == null) {
            ImGui.textDisabled("Unknown component type");
            return false;
        }

        boolean changed = false;

        for (FieldMeta fieldMeta : meta.fields()) {
            String fieldName = fieldMeta.name();
            String componentType = comp.getType();
            boolean isOverridden = entity.isFieldOverridden(componentType, fieldName);

            ImGui.pushID(fieldName);
            if (isOverridden) ImGui.pushStyleColor(ImGuiCol.Text, 0.4f, 0.8f, 1.0f, 1.0f);

            boolean fieldChanged = ReflectionFieldEditor.drawField(comp, fieldMeta);

            if (isOverridden) ImGui.popStyleColor();

            if (isOverridden) {
                ImGui.sameLine();
                if (ImGui.smallButton(FontAwesomeIcons.Undo + "##reset")) {
                    entity.resetFieldToDefault(componentType, fieldName);
                    comp.getFields().put(fieldName, entity.getFieldDefault(componentType, fieldName));
                    if (scene != null) scene.markDirty();
                    changed = true;
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("Reset to default: " + entity.getFieldDefault(componentType, fieldName));
                }
            }

            if (fieldChanged) {
                entity.setFieldValue(componentType, fieldName, comp.getFields().get(fieldName));
                if (scene != null) scene.markDirty();
                changed = true;
            }

            ImGui.popID();
        }

        ReflectionFieldEditor.drawComponentReferences(meta.references(), entity);

        return changed;
    }
}
