package com.pocket.rpg.editor.undo.commands;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.editor.undo.EditorCommand;
import com.pocket.rpg.serialization.ComponentReflectionUtils;

/**
 * Command for changing a component field value.
 * Stores the component type string for field access via findComponentByType,
 * preventing stale references after re-cloning (e.g. refreshFromTemplate).
 */
public class SetComponentFieldCommand implements EditorCommand {

    private final String componentType;
    private final String fieldName;
    private final Object oldValue;
    private final EditorGameObject entity;
    private final Component component; // retained for merge identity only
    private Object newValue;
    private Runnable afterApply;

    public SetComponentFieldCommand(Component component, String fieldName,
                                    Object oldValue, Object newValue,
                                    EditorGameObject entity) {
        this.component = component;
        this.componentType = component.getClass().getName();
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
        Component comp = findComponent();
        if (comp != null) {
            ComponentReflectionUtils.setFieldValue(comp, fieldName, newValue);
        }
        syncOverride(newValue);
        if (afterApply != null) afterApply.run();
    }

    @Override
    public void undo() {
        Component comp = findComponent();
        if (comp != null) {
            ComponentReflectionUtils.setFieldValue(comp, fieldName, oldValue);
        }
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

    private Component findComponent() {
        return entity != null ? entity.findComponentByType(componentType) : null;
    }

    private void syncOverride(Object value) {
        if (entity == null) return;
        entity.syncFieldOverride(componentType, fieldName, value);
    }
}
