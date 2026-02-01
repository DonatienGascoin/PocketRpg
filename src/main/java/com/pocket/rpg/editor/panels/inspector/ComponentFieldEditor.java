package com.pocket.rpg.editor.panels.inspector;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.editor.core.MaterialIcons;
import com.pocket.rpg.editor.panels.hierarchy.HierarchyItem;
import com.pocket.rpg.editor.scene.DirtyTracker;
import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.ui.fields.FieldEditorContext;
import com.pocket.rpg.editor.ui.fields.FieldEditors;
import com.pocket.rpg.editor.ui.fields.ReflectionFieldEditor;
import com.pocket.rpg.editor.undo.UndoManager;
import com.pocket.rpg.editor.undo.commands.ResetFieldOverrideCommand;
import com.pocket.rpg.serialization.ComponentMeta;
import com.pocket.rpg.serialization.ComponentReflectionUtils;
import com.pocket.rpg.serialization.FieldMeta;
import imgui.ImGui;
import imgui.flag.ImGuiCol;

/**
 * Handles component field editing with prefab override support.
 */
public class ComponentFieldEditor {

    private DirtyTracker dirtyTracker;
    private EditorScene scene;

    /**
     * Sets the dirty tracker and scene for component field editing.
     * For scene editing, pass the EditorScene as both (it implements DirtyTracker).
     * For prefab editing, pass a custom DirtyTracker and null scene.
     */
    public void setContext(DirtyTracker dirtyTracker, EditorScene scene) {
        this.dirtyTracker = dirtyTracker;
        this.scene = scene;
    }

    /**
     * Convenience method for scene editing — sets EditorScene as both dirty tracker and scene.
     */
    public void setScene(EditorScene scene) {
        this.dirtyTracker = scene;
        this.scene = scene;
    }

    /**
     * Renders component fields for a runtime game object during play mode.
     * No undo, no prefab overrides — changes are temporary.
     * Custom editors receive the HierarchyItem for scene graph queries,
     * but undo/override logic is skipped (entity is not an EditorGameObject).
     */
    public boolean renderRuntimeComponentFields(Component component, HierarchyItem entity) {
        return ReflectionFieldEditor.drawComponent(component, entity);
    }

    public boolean renderComponentFields(EditorGameObject entity, Component component, boolean isPrefabInstance) {
        // Ensure scene is available for inspectors that need it
        FieldEditorContext.setCurrentScene(scene);

        if (isPrefabInstance) {
            FieldEditors.beginOverrideContext(entity, component);
        }

        boolean changed = ReflectionFieldEditor.drawComponent(component, entity);

        if (isPrefabInstance) {
            FieldEditors.endOverrideContext();
        }

        return changed;
    }

    private boolean renderWithOverrides(EditorGameObject entity, Component component) {
        ComponentMeta meta = ComponentReflectionUtils.getMeta(component);
        if (meta == null) {
            ImGui.textDisabled("Unknown component type");
            return false;
        }

        boolean changed = false;
        String componentType = component.getClass().getName();

        for (FieldMeta fieldMeta : meta.fields()) {
            String fieldName = fieldMeta.name();
            boolean isOverridden = entity.isFieldOverridden(componentType, fieldName);

            ImGui.pushID(fieldName);
            if (isOverridden) ImGui.pushStyleColor(ImGuiCol.Text, 0.4f, 0.8f, 1.0f, 1.0f);

            boolean fieldChanged = ReflectionFieldEditor.drawField(component, fieldMeta);

            if (isOverridden) ImGui.popStyleColor();

            if (isOverridden) {
                ImGui.sameLine();
                if (ImGui.smallButton(MaterialIcons.Undo + "##reset")) {
                    UndoManager.getInstance().execute(
                            new ResetFieldOverrideCommand(entity, component, componentType, fieldName));
                    if (dirtyTracker != null) dirtyTracker.markDirty();
                    changed = true;
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("Reset to default: " + entity.getFieldDefault(componentType, fieldName));
                }
            }

            if (fieldChanged) {
                Object currentValue = ComponentReflectionUtils.getFieldValue(component, fieldName);
                entity.setFieldValue(componentType, fieldName, currentValue);
                if (dirtyTracker != null) dirtyTracker.markDirty();
                changed = true;
            }

            ImGui.popID();
        }

        ReflectionFieldEditor.drawComponentReferences(meta.references(), entity);

        return changed;
    }
}
