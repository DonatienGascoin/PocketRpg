package com.pocket.rpg.editor.undo.commands;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.editor.undo.EditorCommand;
import com.pocket.rpg.serialization.ComponentReflectionUtils;

/**
 * Command for resetting a single prefab field override to its default value.
 * Captures the override value and the component's field value before resetting
 * so they can be restored on undo.
 */
public class ResetFieldOverrideCommand implements EditorCommand {

    private final EditorGameObject entity;
    private final Component component;
    private final String componentType;
    private final String fieldName;
    private Object savedOverrideValue;
    private Object savedFieldValue;

    public ResetFieldOverrideCommand(EditorGameObject entity, Component component,
                                     String componentType, String fieldName) {
        this.entity = entity;
        this.component = component;
        this.componentType = componentType;
        this.fieldName = fieldName;
    }

    @Override
    public void execute() {
        // Save current override value and field value before resetting
        var overrides = entity.getComponentOverrides().get(componentType);
        if (overrides != null) {
            savedOverrideValue = overrides.get(fieldName);
        }
        savedFieldValue = ComponentReflectionUtils.getFieldValue(component, fieldName);

        // Reset the override and apply the default value to the component
        entity.resetFieldToDefault(componentType, fieldName);
        Object defaultValue = entity.getFieldDefault(componentType, fieldName);
        ComponentReflectionUtils.setFieldValue(component, fieldName, defaultValue);
    }

    @Override
    public void undo() {
        // Restore the override
        entity.getComponentOverrides()
                .computeIfAbsent(componentType, k -> new java.util.HashMap<>())
                .put(fieldName, savedOverrideValue);
        entity.invalidateComponentCache();

        // Restore the component field value
        ComponentReflectionUtils.setFieldValue(component, fieldName, savedFieldValue);
    }

    @Override
    public String getDescription() {
        return "Reset " + fieldName;
    }
}
