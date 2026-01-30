package com.pocket.rpg.editor.undo.commands;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.editor.undo.EditorCommand;
import com.pocket.rpg.serialization.ComponentReflectionUtils;

/**
 * Command for changing a component field value.
 */
public class SetComponentFieldCommand implements EditorCommand {

    private final Component component;
    private final String fieldName;
    private final Object oldValue;
    private final EditorGameObject entity;
    private Object newValue;
    private Runnable afterApply;

    public SetComponentFieldCommand(Component component, String fieldName,
                                    Object oldValue, Object newValue,
                                    EditorGameObject entity) {
        this.component = component;
        this.fieldName = fieldName;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.entity = entity;
    }

    /**
     * Sets a callback to run after both execute() and undo().
     * Useful for triggering side effects like layout recalculation.
     */
    public SetComponentFieldCommand withAfterApply(Runnable afterApply) {
        this.afterApply = afterApply;
        return this;
    }

    @Override
    public void execute() {
        ComponentReflectionUtils.setFieldValue(component, fieldName, newValue);
        syncOverride(newValue);
        if (afterApply != null) afterApply.run();
    }

    @Override
    public void undo() {
        ComponentReflectionUtils.setFieldValue(component, fieldName, oldValue);
        syncOverride(oldValue);
        if (afterApply != null) afterApply.run();
    }

    @Override
    public String getDescription() {
        return "Change " + fieldName;
    }

    @Override
    public boolean canMergeWith(EditorCommand other) {
        if (!(other instanceof SetComponentFieldCommand cmd)) {
            return false;
        }

        return cmd.component == this.component &&
                cmd.fieldName.equals(this.fieldName);
    }

    @Override
    public void mergeWith(EditorCommand other) {
        if (other instanceof SetComponentFieldCommand cmd) {
            this.newValue = cmd.newValue;
        }
    }

    private void syncOverride(Object value) {
        if (entity == null || !entity.isPrefabInstance()) return;

        String componentType = component.getClass().getName();
        Object defaultValue = entity.getFieldDefault(componentType, fieldName);

        if (valuesEqual(value, defaultValue)) {
            entity.resetFieldToDefault(componentType, fieldName);
        } else {
            entity.setFieldValue(componentType, fieldName, value);
        }
    }

    private boolean valuesEqual(Object a, Object b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.equals(b);
    }
}
