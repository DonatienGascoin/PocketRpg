package com.pocket.rpg.editor.undo.commands;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.editor.undo.EditorCommand;
import com.pocket.rpg.serialization.ComponentReflectionUtils;

/**
 * Command for resetting a single prefab field override to its default value.
 * Captures the field's current value before resetting so it can be restored on undo.
 */
public class ResetFieldOverrideCommand implements EditorCommand {

    private final EditorGameObject entity;
    private final String componentType;
    private final String fieldName;
    private Object savedFieldValue;

    public ResetFieldOverrideCommand(EditorGameObject entity,
                                     String componentType, String fieldName) {
        this.entity = entity;
        this.componentType = componentType;
        this.fieldName = fieldName;
    }

    @Override
    public void execute() {
        // Save current field value before resetting
        Component comp = entity.findComponentByType(componentType);
        if (comp != null) {
            savedFieldValue = ComponentReflectionUtils.deepCopyValue(
                    ComponentReflectionUtils.getFieldValue(comp, fieldName));
        }

        // Reset: applies default value to component and removes from overriddenFields
        entity.resetFieldToDefault(componentType, fieldName);
    }

    @Override
    public void undo() {
        // Restore the saved value on the component
        Component comp = entity.findComponentByType(componentType);
        if (comp != null && savedFieldValue != null) {
            ComponentReflectionUtils.setFieldValue(comp, fieldName, savedFieldValue);
        }
        // Re-mark the field as overridden
        entity.markFieldOverridden(componentType, fieldName);
    }

    @Override
    public String getDescription() {
        return "Reset " + fieldName;
    }
}
